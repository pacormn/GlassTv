package com.glasstv.launcher;
import android.app.Activity;import android.graphics.Color;import android.net.Uri;import android.view.*;import android.widget.*;
final class AerialPlayer {
 private final Activity activity;private final FrameLayout host;private FrameLayout overlay;private VideoView video;private View previous;private int consumed=-1;
 AerialPlayer(Activity a,FrameLayout root){activity=a;host=root;}
 boolean visible(){return overlay!=null;}
 void start(String source){
  if(visible())return;previous=activity.getCurrentFocus();overlay=new FrameLayout(activity);overlay.setBackgroundColor(Color.BLACK);overlay.setContentDescription("Économiseur aérien");overlay.setKeepScreenOn(true);host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
  video=new VideoView(activity);overlay.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  TextView status=new TextView(activity);status.setText("Chargement du paysage…");status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);overlay.addView(status,new FrameLayout.LayoutParams(-1,-1));
  video.setOnPreparedListener(player->{player.setVolume(0,0);player.setLooping(true);status.setVisibility(View.GONE);video.start();});
  video.setOnErrorListener((player,what,extra)->{status.setText("Vidéo indisponible. Vérifiez la connexion ou choisissez une vidéo locale.\nAppuyez sur une touche pour revenir.");return true;});
  try{video.setVideoURI(Uri.parse(source));}catch(RuntimeException e){status.setText("Impossible d’ouvrir cette vidéo. Appuyez sur une touche pour revenir.");}
 }
 boolean key(KeyEvent event){if(consumed==event.getKeyCode()){if(event.getAction()==KeyEvent.ACTION_UP)consumed=-1;return true;}if(!visible())return false;consumed=event.getKeyCode();stop();if(event.getAction()==KeyEvent.ACTION_UP)consumed=-1;return true;}
 void stop(){if(video!=null){video.stopPlayback();video=null;}if(overlay!=null){overlay.setKeepScreenOn(false);host.removeView(overlay);overlay=null;}if(previous!=null){previous.requestFocus();previous=null;}}
}