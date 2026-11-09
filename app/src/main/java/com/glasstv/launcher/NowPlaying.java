package com.glasstv.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Displays real Android media sessions; never invents playback metadata. */
public class NowPlaying {
    public static class Access extends NotificationListenerService {}
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaController controller;
    private LinearLayout card;
    private ProgressBar timeline;
    private TextView title, details, progress, play;
    private ImageView cover;
    private Bars bars;
    private final Runnable tick = new Runnable() {
        public void run() { refresh(); handler.postDelayed(this, 1000); }
    };
    public NowPlaying(Activity a) { activity=a; }
    private int dp(int n) { return Math.round(n*activity.getResources().getDisplayMetrics().widthPixels/1920f); }
    public void resume(){handler.removeCallbacks(tick);tick.run();}
    public void pause(){handler.removeCallbacks(tick);if(bars!=null)bars.running=false;}
    public void clearCard(){card=null;cover=null;details=null;progress=null;timeline=null;play=null;}
    public void attach(TextView label, Bars indicator){title=label;bars=indicator;refresh();}
    public Bars indicator(){return new Bars();}
    public void permission(){
        try {
            activity.startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (android.content.ActivityNotFoundException e) {
            new android.app.AlertDialog.Builder(activity)
                .setTitle("Accès aux informations de lecture")
                .setMessage("Ce boîtier ne fournit pas de raccourci vers ce réglage. Cherchez « Accès aux notifications » dans les réglages Android TV.")
                .setPositiveButton("Fermer", null).show();
        }
    }
    private MediaController current(){
        try{
            MediaSessionManager manager=activity.getSystemService(MediaSessionManager.class);
            List<MediaController> sessions=manager.getActiveSessions(new ComponentName(activity,Access.class));
            for(MediaController c:sessions) if(c.getPlaybackState()!=null && c.getPlaybackState().getState()==PlaybackState.STATE_PLAYING)return c;
            for(MediaController c:sessions) if(c.getMetadata()!=null)return c;
        }catch(SecurityException ignored){}
        return null;
    }
    private TextView text(String s,int size){TextView t=new TextView(activity);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(0,dp(size));return t;}
    public void addCard(LinearLayout sheet){
        card=new LinearLayout(activity);card.setOrientation(1);card.setPadding(dp(20),dp(20),dp(20),dp(20));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0x35ffffff);bg.setCornerRadius(dp(26));card.setBackground(bg);
        LinearLayout row=new LinearLayout(activity);cover=new ImageView(activity);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(cover,new LinearLayout.LayoutParams(dp(90),dp(90)));
        details=text("",25);details.setMaxLines(2);details.setEllipsize(android.text.TextUtils.TruncateAt.END);details.setPadding(dp(16),0,0,0);row.addView(details,new LinearLayout.LayoutParams(0,dp(100),1));card.addView(row);
        timeline=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        timeline.setMax(1000);
        card.addView(timeline,new LinearLayout.LayoutParams(-1,dp(20)));
        progress=text("",20);progress.setPadding(0,dp(12),0,dp(14));card.addView(progress);
        sheet.addView(card,new LinearLayout.LayoutParams(-1,-2));refresh();
    }
    public void command(int action){
        if(controller==null)return;
        MediaController.TransportControls t=controller.getTransportControls();
        if(action<0)t.skipToPrevious();else if(action>0)t.skipToNext();
        else if(controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING)t.pause();else t.play();
    }
    public boolean available(){return controller!=null;}
    public void bindPlay(TextView v){play=v;}
    private String clock(long ms){long s=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60);}
    private void refresh(){
        controller=current();MediaMetadata m=controller==null?null:controller.getMetadata();
        PlaybackState state=controller==null?null:controller.getPlaybackState();
        boolean playing=state!=null&&state.getState()==PlaybackState.STATE_PLAYING;
        String name=m==null?"":m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if(name==null||name.isEmpty())name="Lecture en cours";
        if(title!=null){title.setText(controller==null?"":name);title.setVisibility(controller==null?View.GONE:View.VISIBLE);bars.setVisibility(controller==null?View.GONE:View.VISIBLE);bars.running=playing;bars.invalidate();}
        if(card!=null){card.setVisibility(controller==null?View.GONE:View.VISIBLE);
            if(controller!=null){String artist=m==null?null:m.getString(MediaMetadata.METADATA_KEY_ARTIST);details.setText(name+"\n"+(artist==null?"":artist));
                Bitmap art=m==null?null:m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(art==null&&m!=null)art=m.getBitmap(MediaMetadata.METADATA_KEY_ART);cover.setImageBitmap(art);
                long duration=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long position=state==null?0:state.getPosition();if(playing&&state.getLastPositionUpdateTime()>0)position+=(long)((SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());
                boolean hasPosition=state!=null&&state.getPosition()>=0;
                boolean hasTimeline=hasPosition&&duration>0;
                timeline.setVisibility(hasTimeline?View.VISIBLE:View.GONE);
                if(hasTimeline){
                    position=Math.max(0,Math.min(position,duration));
                    timeline.setProgress((int)(1000.0*position/duration));
                }
                progress.setVisibility(hasPosition?View.VISIBLE:View.GONE);
                if(hasPosition)progress.setText(clock(position)+(hasTimeline?" / "+clock(duration):""));
            }}
        if(play!=null)play.setText(playing?"Ⅱ  Pause":"▶  Lecture");
    }
    public class Bars extends View {
        boolean running;
        private final Paint paint=new Paint(3);
        Bars(){super(activity);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){paint.setColor(Color.WHITE);float w=getWidth()/9f;for(int i=0;i<4;i++){float h=getHeight()*(running?(0.25f+0.65f*(float)Math.abs(Math.sin(SystemClock.uptimeMillis()/230.0+i))):.2f);c.drawRoundRect((i*2+1)*w,(getHeight()-h)/2,(i*2+2)*w,(getHeight()+h)/2,w/2,w/2,paint);}if(running&&isShown())postInvalidateDelayed(50);}
    }
}
