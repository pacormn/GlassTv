package com.glasstv.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.database.StandaloneDatabaseProvider;
import java.io.File;
import java.util.*;

@androidx.media3.common.util.UnstableApi
final class AerialPlayer {
 private static final long CAPTION_MS = 4800;
 private static final long CAPTION_FADE_MS = 700;
 private static final long FADE_MS=2400;
 private static SimpleCache cache;
 private static class Clip {final String name,url;Clip(String n,String u){name=n;url=u;}}
 private static class Category {final String name;final Clip[] clips;Category(String n,Clip... c){name=n;clips=c;}}
 private final Activity activity; private final FrameLayout host;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayList<Category> categories=new ArrayList<>();
 private final Set<String> failed=new HashSet<>();
 private final Set<Integer> held=new HashSet<>();
 private FrameLayout overlay; private TextureView video; private ImageView cover;
 private TextView status, caption; private ExoPlayer player; private View previous;
 private Bitmap snapshot; private Runnable timeout;
 private int category,clip,playingCategory,playingClip,generation;
 private boolean busy,hasFrame; private float videoRatio=16f/9f,downX,downY;
 private final Runnable hideCaption = () -> {
  if (caption != null) {
   caption.animate().alpha(0f).setDuration(CAPTION_FADE_MS)
       .withEndAction(() -> caption.setVisibility(View.GONE)).start();
  }
 };
 private final Runnable loadSelection=()->{if(visible()&&!busy)play();};
 private static synchronized SimpleCache cache(Context c){if(cache==null)cache=new SimpleCache(new File(c.getCacheDir(),"aerial-media"),new LeastRecentlyUsedCacheEvictor(256L*1024*1024),new StandaloneDatabaseProvider(c.getApplicationContext()));return cache;}
 AerialPlayer(Activity a,FrameLayout h){activity=a;host=h;}
 boolean visible(){return overlay!=null;}
 void start(String source){
  if(visible())return;
  categories.clear();failed.clear();category=clip=0;busy=false;hasFrame=false;
  if(source.startsWith("https://")||source.startsWith("http://")){
   addBuiltInCollections();
  }else categories.add(new Category("Vidéo locale",new Clip("Ma vidéo",source)));
  previous=activity.getCurrentFocus();overlay=new FrameLayout(activity){@Override public boolean onInterceptTouchEvent(MotionEvent event){return true;}};overlay.setBackgroundColor(Color.BLACK);overlay.setKeepScreenOn(true);host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
  video=new TextureView(activity);overlay.addView(video,new FrameLayout.LayoutParams(-1,-1));video.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->crop());
  cover=new ImageView(activity);cover.setScaleType(ImageView.ScaleType.FIT_XY);overlay.addView(cover,new FrameLayout.LayoutParams(-1,-1));
  status=new TextView(activity);status.setTextColor(Color.WHITE);status.setTextSize(20);status.setShadowLayer(6,0,2,Color.BLACK);status.setPadding(40,20,40,32);
  overlay.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));

  caption = new TextView(activity);
  caption.setTextColor(Color.WHITE);
  caption.setTextSize(14);
  caption.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
  caption.setGravity(Gravity.END);
  caption.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
  caption.setVisibility(View.GONE);
  FrameLayout.LayoutParams captionPosition =
      new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
  captionPosition.setMargins(dp(32), 0, dp(32), dp(26));
  overlay.addView(caption, captionPosition);

  play();
 }
 private void addBuiltInCollections() {
  // All 1080p H.264 assets from RobinFrcd/AerialShots, entries.json (2448e54ba8f5).
  categories.add(new Category("Océan",
      new Clip("Cap Méchant",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_CapMechant_1080p_h264.m4v"),
      new Clip("Cap Méchant au coucher du soleil",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_CapMechant2_1080p_h264.m4v"),
      new Clip("Récif de Saint-Leu",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_Lagon_1080p_h264.m4v"),
      new Clip("Épave du Tresta Star",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_TrestaStar_1080p_h264.m4v"),
      new Clip("Henningsvær",
          "https://github.com/RobinFrcd/AerialShots/releases/download/norway-2021-v1/robinfrcd_hennigsvaer_1080p_H264.m4v"),
      new Clip("Reine",
          "https://github.com/RobinFrcd/AerialShots/releases/download/norway-2021-v1/robinfrcd_reine_1080p_H264.m4v")));
  categories.add(new Category("Montagnes",
      new Clip("Mafate",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_Mafate_1080p_h264.m4v"),
      new Clip("Mont Blanc",
          "https://github.com/RobinFrcd/AerialShots/releases/download/french-alps-2022-v1/robinfrcd_french_alps_mont_blanc_2_1080p_h264.m4v"),
      new Clip("Piton des Neiges",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_PitonDesNeiges_1080p_h264.m4v"),
      new Clip("Plaine des Sables",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_PitonFournaise_1080p_h264.m4v"),
      new Clip("Mer de nuages à Megève I",
          "https://github.com/RobinFrcd/AerialShots/releases/download/french-alps-2022-v1/robinfrcd_french_alps_sea_of_clouds_1080p_h264.m4v"),
      new Clip("Mer de nuages à Megève II",
          "https://github.com/RobinFrcd/AerialShots/releases/download/french-alps-2022-v1/robinfrcd_french_alps_sea_of_clouds_2_1080p_h264.m4v")));
  categories.add(new Category("Forêts et vallées",
      new Clip("Grand Étang",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_GrandEtang_1080p_h264.m4v"),
      new Clip("Grand Coude",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_GrandCoude_1080p_h264.m4v"),
      new Clip("Trou de Fer",
          "https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_TrouDeFer_1080p_h264.m4v"),
      new Clip("Fjord d’Aurland",
          "https://github.com/RobinFrcd/AerialShots/releases/download/norway-2021-v1/robinfrcd_aurland_1080p_H264.m4v"),
      new Clip("Nusfjord",
          "https://github.com/RobinFrcd/AerialShots/releases/download/norway-2021-v1/robinfrcd_nusfjord_1080p_H264.m4v"),
      new Clip("Cascade de Vettisfossen",
          "https://github.com/RobinFrcd/AerialShots/releases/download/norway-2021-v1/robinfrcd_vettisfossen_1080p_H264.m4v")));
 }

 private int dp(int value) {
  return Math.round(value * activity.getResources().getDisplayMetrics().density);
 }

 private void clearCaption() {
  handler.removeCallbacks(hideCaption);
  if (caption != null) {
   caption.animate().withEndAction(null).cancel();
   caption.setVisibility(View.GONE);
  }
 }

 private void showCaption(String categoryName, String clipName) {
  status.setVisibility(View.GONE);
  caption.setText(categoryName + " · " + clipName);
  overlay.setContentDescription("Économiseur aérien · " + caption.getText());
  caption.setAlpha(0f);
  caption.setVisibility(View.VISIBLE);
  caption.animate().alpha(0.7f).setDuration(300).start();
  handler.postDelayed(hideCaption, CAPTION_MS - CAPTION_FADE_MS);
 }

 private void showStatus(String message) {
  status.setText(message);
  status.setVisibility(View.VISIBLE);
  overlay.setContentDescription("Économiseur aérien · " + message);
 }
 private void navigate(int horizontal,int vertical){
  if(!visible())return;
  Log.i("GLASSTV_AERIAL","NAV horizontal="+horizontal+" vertical="+vertical);
  if(vertical!=0){category=Math.floorMod(category+vertical,categories.size());clip=0;}else clip=Math.floorMod(clip+horizontal,categories.get(category).clips.length);
  handler.removeCallbacks(loadSelection);
  if(!busy)handler.postDelayed(loadSelection,200);
 }
 private void crop(){if(video==null||video.getWidth()==0||video.getHeight()==0)return;float ratio=(float)video.getWidth()/video.getHeight();Matrix m=new Matrix();m.setScale(Math.max(1,videoRatio/ratio),Math.max(1,ratio/videoRatio),video.getWidth()/2f,video.getHeight()/2f);video.setTransform(m);}
 private void play(){
  if(!visible())return;
  if(hasFrame&&player!=null&&video.isAvailable()){
   Bitmap next=video.getBitmap(1280,720);if(next!=null){cover.setImageBitmap(next);if(snapshot!=null)snapshot.recycle();snapshot=next;cover.setAlpha(1);}
  }
  releasePlayer();busy=true;hasFrame=false;video.setAlpha(0);playingCategory=category;playingClip=clip;
  final int token=generation;final Clip selected=categories.get(category).clips[clip];
  clearCaption();
  if (snapshot == null) showStatus("Chargement…");
  else status.setVisibility(View.GONE);
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("GlassTV/AndroidTV").setConnectTimeoutMs(8000).setReadTimeoutMs(12000);
  CacheDataSource.Factory data=new CacheDataSource.Factory().setCache(cache(activity)).setUpstreamDataSourceFactory(new DefaultDataSource.Factory(activity,http)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
  DefaultMediaSourceFactory media=new DefaultMediaSourceFactory(data).setLoadErrorHandlingPolicy(new androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1));
  player=new ExoPlayer.Builder(activity,new DefaultRenderersFactory(activity).setEnableDecoderFallback(true)).setMediaSourceFactory(media).build();player.setVideoTextureView(video);player.setVolume(0);
  player.addListener(new Player.Listener(){
   @Override public void onVideoSizeChanged(VideoSize size){if(token==generation&&size.height>0){videoRatio=(float)size.width*size.pixelWidthHeightRatio/size.height;crop();}}
   @Override public void onRenderedFirstFrame(){
    if(token!=generation||hasFrame)return;hasFrame=true;cancelTimeout();video.setAlpha(1);
    Log.i("GLASSTV_AERIAL","FIRST_FRAME category="+playingCategory+" clip="+playingClip+" remote="+selected.url.startsWith("https://"));
    showCaption(categories.get(playingCategory).name, selected.name);
    cover.animate().alpha(0).setDuration(FADE_MS).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(()->{
     if(token!=generation)return;cover.setImageDrawable(null);if(snapshot!=null){snapshot.recycle();snapshot=null;}busy=false;
     Log.i("GLASSTV_AERIAL","TRANSITION_DONE category="+playingCategory+" clip="+playingClip);
     if(category!=playingCategory||clip!=playingClip)handler.post(loadSelection);
    }).start();
   }
   @Override public void onPlayerError(PlaybackException error){if(token==generation){Log.w("GLASSTV_AERIAL",error.getErrorCodeName());failure(error.getErrorCodeName());}}
   @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&token==generation&&!busy)navigate(1,0);}
  });
  player.setMediaItem(MediaItem.fromUri(Uri.parse(selected.url)));player.prepare();player.play();
  timeout=()->{if(token==generation)failure("TIMEOUT");};handler.postDelayed(timeout,22000);
 }
 private void failure(String reason){
  failed.add(categories.get(playingCategory).clips[playingClip].url);releasePlayer();busy=false;
  if(category!=playingCategory||clip!=playingClip){handler.post(loadSelection);return;}
  Clip[] choices=categories.get(category).clips;
  for(int n=1;n<=choices.length;n++){int next=(clip+n)%choices.length;if(!failed.contains(choices[next].url)){clip=next;handler.post(loadSelection);return;}}
  clearCaption();
  showStatus("Cette collection est indisponible ("+reason+").\nHaut/bas : autre catégorie · Retour : quitter");
 }
 private void cancelTimeout(){if(timeout!=null){handler.removeCallbacks(timeout);timeout=null;}}
 private void releasePlayer(){cancelTimeout();generation++;if(cover!=null){cover.animate().setListener(null);cover.animate().cancel();}if(player!=null){player.clearVideoSurface();player.release();player=null;}}
 boolean touch(MotionEvent e){
  if(!visible())return false;
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();}
  else if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=e.getX()-downX,dy=e.getY()-downY;float threshold=48*activity.getResources().getDisplayMetrics().density;
   if(Math.max(Math.abs(dx),Math.abs(dy))>threshold){if(Math.abs(dx)>Math.abs(dy))navigate(dx<0?1:-1,0);else navigate(0,dy<0?1:-1);}
  }
  return true;
 }
 boolean key(KeyEvent event){
  int code=event.getKeyCode();
  if(held.contains(code)){if(event.getAction()==KeyEvent.ACTION_UP)held.remove(code);return true;}
  if(!visible())return false;
  if(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN||code==KeyEvent.KEYCODE_VOLUME_MUTE)return false;
  if(event.getAction()==KeyEvent.ACTION_DOWN){held.add(code);if(event.getRepeatCount()!=0)return true;
   switch(code){case KeyEvent.KEYCODE_DPAD_LEFT:navigate(-1,0);break;case KeyEvent.KEYCODE_DPAD_RIGHT:navigate(1,0);break;case KeyEvent.KEYCODE_DPAD_UP:navigate(0,-1);break;case KeyEvent.KEYCODE_DPAD_DOWN:navigate(0,1);break;default:stop();break;}
  }return true;
 }
 void stop(){releasePlayer();handler.removeCallbacks(loadSelection);clearCaption();busy=false;hasFrame=false;
  if(overlay!=null){overlay.setKeepScreenOn(false);host.removeView(overlay);overlay=null;video=null;cover=null;status=null;caption=null;}
  if(snapshot!=null){snapshot.recycle();snapshot=null;}if(previous!=null){previous.requestFocus();previous=null;}
 }
}
