package com.glasstv.launcher;
import android.app.Activity;import android.content.Context;import android.graphics.Color;import android.net.Uri;import android.os.Handler;import android.os.Looper;import android.util.Log;import android.view.*;import android.widget.*;
import androidx.media3.common.*;import androidx.media3.exoplayer.*;import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;import androidx.media3.datasource.*;import androidx.media3.datasource.cache.*;import androidx.media3.database.StandaloneDatabaseProvider;import androidx.media3.ui.*;import java.io.File;import java.util.*;
@androidx.media3.common.util.UnstableApi
final class AerialPlayer {
 private static SimpleCache cache;
 private final Activity activity;private final FrameLayout host;private FrameLayout overlay;private PlayerView view;private ExoPlayer player;private TextView status;private View previous;private int consumed=-1,index,generation;private final ArrayList<String> sources=new ArrayList<>();private final Handler handler=new Handler(Looper.getMainLooper());private Runnable timeout;private boolean[] failed;
 private static synchronized SimpleCache cache(Context c){if(cache==null)cache=new SimpleCache(new File(c.getCacheDir(),"aerial-media"),new LeastRecentlyUsedCacheEvictor(256L*1024*1024),new StandaloneDatabaseProvider(c.getApplicationContext()));return cache;}
 AerialPlayer(Activity a,FrameLayout root){activity=a;host=root;}
 boolean visible(){return overlay!=null;}
 void start(String source){
  if(visible())return;
  sources.clear();
  if(source.startsWith("https://")||source.startsWith("http://")){sources.addAll(Arrays.asList("https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_CapMechant_1080p_h264.m4v","https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_GrandEtang_1080p_h264.m4v","https://github.com/RobinFrcd/AerialShots/releases/download/reunion-2022/robinfrcd_reunion_Mafate_1080p_h264.m4v","https://github.com/RobinFrcd/AerialShots/releases/download/french-alps-2022-v1/robinfrcd_french_alps_mont_blanc_2_1080p_h264.m4v"));}else sources.add(source);
  failed=new boolean[sources.size()];index=0;previous=activity.getCurrentFocus();overlay=new FrameLayout(activity);overlay.setBackgroundColor(Color.BLACK);overlay.setContentDescription("Économiseur aérien");overlay.setKeepScreenOn(true);host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
  view=new PlayerView(activity);view.setUseController(false);view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);view.setFocusable(false);overlay.addView(view,new FrameLayout.LayoutParams(-1,-1));
  status=new TextView(activity);status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);status.setPadding(32,32,32,32);overlay.addView(status,new FrameLayout.LayoutParams(-1,-1));play();
 }
 private void play(){
  if(!visible())return;releasePlayer();final int token=++generation;
  status.setVisibility(View.VISIBLE);status.setText("Chargement du paysage… "+(index+1)+"/"+sources.size());
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("GlassTV/AndroidTV").setConnectTimeoutMs(8000).setReadTimeoutMs(12000);
  DataSource.Factory upstream=new DefaultDataSource.Factory(activity,http);
  CacheDataSource.Factory data=new CacheDataSource.Factory().setCache(cache(activity)).setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
  DefaultMediaSourceFactory media=new DefaultMediaSourceFactory(data).setLoadErrorHandlingPolicy(new androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1));
  player=new ExoPlayer.Builder(activity,new DefaultRenderersFactory(activity).setEnableDecoderFallback(true)).setMediaSourceFactory(media).build();view.setPlayer(player);player.setVolume(0);
  player.addListener(new Player.Listener(){
   @Override public void onRenderedFirstFrame(){if(token!=generation)return;cancelTimeout();status.setVisibility(View.GONE);Log.i("GLASSTV_AERIAL","FIRST_FRAME source="+index+" remote="+sources.get(index).startsWith("https://"));}
   @Override public void onPlayerError(PlaybackException error){if(token!=generation)return;Log.w("GLASSTV_AERIAL",error.getErrorCodeName(),error);String reason=error.getErrorCodeName();for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof javax.net.ssl.SSLException){reason="CONNEXION_TLS";break;}next(reason);}
   @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&token==generation){int count=0;do{index=(index+1)%sources.size();count++;}while(failed[index]&&count<sources.size());play();}}
  });
  player.setMediaItem(MediaItem.fromUri(Uri.parse(sources.get(index))));player.prepare();player.play();
  timeout=()->{if(token==generation)next("TIMEOUT");};handler.postDelayed(timeout,22000);
 }
 private void next(String reason){cancelTimeout();failed[index]=true;for(int count=0;count<sources.size();count++){index=(index+1)%sources.size();if(!failed[index]){play();return;}}releasePlayer();status.setVisibility(View.VISIBLE);status.setText(reason.equals("CONNEXION_TLS")?"Connexion sécurisée aux vidéos impossible. Code : TLS. Une vidéo locale reste disponible.\nAppuyez sur une touche pour revenir.":"Aucune source lisible ("+reason+").\nEssayez une vidéo locale ou réessayez plus tard.\nAppuyez sur une touche pour revenir.");}
 private void cancelTimeout(){if(timeout!=null){handler.removeCallbacks(timeout);timeout=null;}}
 private void releasePlayer(){cancelTimeout();generation++;if(view!=null)view.setPlayer(null);if(player!=null){player.release();player=null;}}
 boolean key(KeyEvent event){if(consumed==event.getKeyCode()){if(event.getAction()==KeyEvent.ACTION_UP)consumed=-1;return true;}if(!visible())return false;consumed=event.getKeyCode();stop();if(event.getAction()==KeyEvent.ACTION_UP)consumed=-1;return true;}
 void stop(){releasePlayer();if(overlay!=null){overlay.setKeepScreenOn(false);host.removeView(overlay);overlay=null;view=null;}if(previous!=null){previous.requestFocus();previous=null;}}
}