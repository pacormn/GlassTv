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
 private java.util.concurrent.ExecutorService prefetch;
 private volatile CacheWriter prefetchWriter;
 private ExoPlayer outgoingPlayer;
 private TextureView outgoingVideo;
 private int category,clip,playingCategory,playingClip,generation;
 private boolean collectionFailed;
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
   addAppleCollections();
  }else categories.add(new Category("Vidéo locale",new Clip("Ma vidéo",source)));
  previous=activity.getCurrentFocus();overlay=new FrameLayout(activity){@Override public boolean onInterceptTouchEvent(MotionEvent event){return true;}};overlay.setBackgroundColor(Color.BLACK);overlay.setKeepScreenOn(true);
  // Keep DPAD events routed to the saver after a swipe enters Android touch mode.
  overlay.setFocusableInTouchMode(true);
  overlay.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
  host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
  overlay.requestFocus();
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

  prefetch=java.util.concurrent.Executors.newSingleThreadExecutor();
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

 private void addAppleCollections() {
  categories.add(new Category("Espace",
   new Clip("Korea and Japan Night · GMT026_363A_103NC_E1027_KOREA_JAPAN_NIGHT","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT026_363A_103NC_E1027_KOREA_JAPAN_NIGHT_v18_SDR_PS_20180907_SDR_2K_AVC.mov"),
   new Clip("Antarctica · GMT110_112NC_364D_1054_AURORA_ANTARCTICA","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT110_112NC_364D_1054_AURORA_ANTARTICA__COMP_FINAL_v34_PS_SDR_20181107_SDR_2K_AVC.mov"),
   new Clip("Caribbean · A105_C002","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A105_C002_v06_SDR_FINAL_25062018_SDR_2K_AVC.mov"),
   new Clip("California to Vegas · GMT306_139NC_139J_3066_CALI_TO_VEGAS","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT306_139NC_139J_3066_CALI_TO_VEGAS_v08_SDR_PS_20180824_SDR_2K_AVC.mov"),
   new Clip("Africa and the Middle East · A103_C002_0205DG","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A103_C002_0205DG_v12_SDR_FINAL_20180706_SDR_2K_AVC.mov"),
   new Clip("Nile Delta · A050_C004_1027V8","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A050_C004_1027V8_v16_SDR_FINAL_20180706_SDR_2K_AVC.mov"),
   new Clip("Caribbean Day · GMT308_139K_142NC_CARIBBEAN_DAY","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT308_139K_142NC_CARIBBEAN_DAY_v09_SDR_FINAL_22062018_SDR_2K_AVC.mov"),
   new Clip("China · GMT329_2_113NC_396B_1105","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT329_113NC_396B_1105_CHINA_v04_SDR_FINAL_20180706_F900F2700_SDR_2K_AVC.mov"),
   new Clip("West Africa to the Alps · A001_C004_1207W5","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A001_C004_1207W5_v23_SDR_FINAL_20180706_SDR_2K_AVC.mov"),
   new Clip("Atlantic Ocean to Spain and France · A001_C001_120530","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A001_C001_120530_v04_SDR_FINAL_20180706_SDR_2K_AVC.mov"),
   new Clip("North America Aurora · GMT314_139M_170NC_NORTH_AMERICA_AURORA","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT314_139M_170NC_NORTH_AMERICA_AURORA__COMP_v22_SDR_20181206_v12CC_SDR_2K_AVC.mov"),
   new Clip("Southern California to Baja · A114_C001","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A114_C001_0305OT_v10_SDR_FINAL_22062018_SDR_2K_AVC.mov"),
   new Clip("New Zealand · A105_C003_0212CT","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A105_C003_0212CT_FLARE_v10_SDR_PS_FINAL_20180711_SDR_2K_AVC.mov"),
   new Clip("Ireland to Asia · GMT329_117NC_401C_1037_IRELAND_TO_ASIA","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT329_117NC_401C_1037_IRELAND_TO_ASIA_v48_SDR_PS_FINAL_20180725_F0F6300_SDR_2K_AVC.mov"),
   new Clip("Australia · GMT060_117NC_363D_1034_AUSTRALIA","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT060_117NC_363D_1034_AUSTRALIA_v35_SDR_PS_FINAL_20180731_SDR_2K_AVC.mov"),
   new Clip("South Africa to North Asia · A351_C001_1213SK","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A351_C001_v06_SDR_PS_20180725_SDR_2K_AVC.mov"),
   new Clip("Africa Night · GMT312_162NC_139M_1041_AFRICA_NIGHT","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT312_162NC_139M_1041_AFRICA_NIGHT_v14_SDR_FINAL_20180706_SDR_2K_AVC.mov"),
   new Clip("New York Night · GMT307_136NC_134K_8277_NY_NIGHT","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT307_136NC_134K_8277_NY_NIGHT_01_v25_SDR_PS_20180907_SDR_2K_AVC.mov"),
   new Clip("Caribbean · A108_C001","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A108_C001_v09_SDR_FINAL_22062018_SDR_2K_AVC.mov"),
   new Clip("Sahara and Italy · A009_C001_10181A","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A009_C001_010181A_v09_SDR_PS_FINAL_20180725_SDR_2K_AVC.mov"),
   new Clip("Italy to Asia · GMT329_113NC_396B_1105_ITALY_TO_ASIA","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GMT329_113NC_396B_1105_ITALY_v03_SDR_FINAL_20180706_SDR_2K_AVC.mov"),
   new Clip("Iran and Afghanistan · A083_C002_1130KZ","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A083_C002_1130KZ_v04_SDR_PS_FINAL_20180725_SDR_2K_AVC.mov")));
  categories.add(new Category("Paysages Apple",
   new Clip("Liwa · LW_L001_C006","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LW_L001_C006_PSNK_DENOISE_v02_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("China · C003_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_C003_C003_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Scotland · S003_C020","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/S003_C020_SDR_2K_AVC.mov"),
   new Clip("Hawaii · H007_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_H007_C003_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("China · C004_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_C004_C003_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Hawaii · H004_C009","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_H004_C009_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Patagonia · P006_C002_11106T","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/P006_C002_UHD_SDR_2K_AVC.mov"),
   new Clip("Iceland · I005_C008","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/I005_C008_CROP_SDR_2K_AVC.mov"),
   new Clip("Greenland · GL_G002_C002","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GL_G002_C002_PSNK_v03_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Scotland · S005_C015","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/S005_C015_SDR_2K_AVC.mov"),
   new Clip("Hawaii · H012_C009_0","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_H012_C009_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Yosemite · Y011_C001_0305","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/Y011_C001_SDR_2K_AVC.mov"),
   new Clip("Hawaii · H005_C012","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_H005_C012_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Patagonia · P001_C005_11059D","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/P001_C005_UHD_SDR_2K_AVC.mov"),
   new Clip("Grand Canyon · G010_C026_0107KE","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/G010_C026_UHD_SDR_v02_2K_AVC.mov"),
   new Clip("Yosemite · Y003_C009_027","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/Y003_C009_SDR_2K_AVC.mov"),
   new Clip("Hawaii · H004_C007","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_H004_C007_PS_v02_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Iceland · I003_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/I003_C004_SDR_2K_AVC.mov"),
   new Clip("Iceland · I004_C014","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/I004_C014_SDR_2K_AVC.mov"),
   new Clip("Yosemite · Y011_C008_030584","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/Y011_C008_SDR_2K_AVC.mov"),
   new Clip("China · CH_C002_C005","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_CH_C002_C005_PSNK_v05_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("Grand Canyon · G009_C014_0106B9","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/G009_C014_UHD_SDR_2K_AVC.mov"),
   new Clip("Liwa · LW_L001_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LW_L001_C003__PSNK_DENOISE_v04_SDR_PS_FINAL_20180803_SDR_2K_AVC.mov"),
   new Clip("Patagonia · P005_C002_1109E1","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/P005_C002_UHD_SDR_2K_AVC.mov"),
   new Clip("China · CH_C007_C011","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_CH_C007_C011_PSNK_v02_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("Greenland · GL_G004_C010","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GL_G004_C010_PSNK_v04_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("China · CH_C007_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_CH_C007_C004_PSNK_v02_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("Yosemite · Y009_C015_0304I","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/Y009_C015_SDR_2K_AVC.mov"),
   new Clip("Grand Canyon · G008_C015_0106MB","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/G008_C015_UHD_SDR_2K_AVC.mov"),
   new Clip("Iceland · I003_C008","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/I003_C008_SDR_2K_AVC.mov"),
   new Clip("Scotland · S006_C007","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/S006_C007_SDR_2K_AVC.mov"),
   new Clip("Grand Canyon · G007_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/G007_C004_UHD_SDR_2K_AVC.mov"),
   new Clip("Yosemite · Y005_C003_0228SC","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/Y005_C003_SDR_2K_AVC.mov"),
   new Clip("Yosemite · Y002_C013_0226","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/Y002_C013_SDR_2K_AVC.mov"),
   new Clip("Iceland · I003_C011","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/I003_C011_SDR_2K_AVC.mov"),
   new Clip("Yosemite · Y004_C015_0227PD","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/Y004_C015_SDR_2K_AVC.mov"),
   new Clip("Patagonia · P007_C027","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/P007_C027_UHD_SDR_2K_AVC.mov"),
   new Clip("Greenland · GL_G010_C006","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_GL_G010_C006_PSNK_NOSUN_v12_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("China · C001_C005","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_C001_C005_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("Iceland · I003_C015","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/I003_C015_SDR_2K_AVC.mov"),
   new Clip("Grand Canyon · G009_C003_010678","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/G009_C003_UHD_SDR_2K_AVC.mov"),
   new Clip("Los Angeles · LA_A008_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LA_A008_C004_ALTB_ED_FROM_FLAME_RETIME_v46_SDR_PS_20180917_SDR_2K_AVC.mov"),
   new Clip("California Wildflowers · W014_C018_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/W014_C018_F01_2K_AVC.mov"),
   new Clip("California State Route 58, Carrizo Plain, California · W015_C010_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/W015_C010_F01_2K_AVC_cut2.mov"),
   new Clip("California Wildflowers · W015_C006_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/W015_C006_F01_2K_AVC.mov"),
   new Clip("Del Norte Coast Redwoods State Park, California · R010_C003_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/R010_C003_F02_2K_AVC.mov"),
   new Clip("Redwoods · R013_C039_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/R013_C039_F01_2K_AVC.mov"),
   new Clip("China Beach, Curry County, Oregon · R006_C013_S05","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/R006_C013_S05_2K_AVC.mov"),
   new Clip("Secret Beach, Brookings, Oregon · R004_C012_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/R004_C012_F01_2K_AVC.mov"),
   new Clip("Monument Valley, Utah · M012_C065_S04","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M012_C065_S04_2K_AVC.mov"),
   new Clip("Coal Mine Canyon, Arizona · M013_C012_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M013_C012_F01_2K_AVC.mov"),
   new Clip("Cathedral Canyon, Lake Powell, Utah · M010_C005_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M010_C005_F02_2K_AVC.mov"),
   new Clip("Monument Valley, Utah · M007_C007_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M007_C007_F02_2K_AVC.mov"),
   new Clip("Factory Butte, Wayne County, Utah · M012_C023_S04","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M012_C023_S04_2K_AVC.mov"),
   new Clip("Twilight, Forbidding, and Cascade Canyon, Lake Powell, Utah · M010_C009_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M010_C009_F01_2K_AVC.mov"),
   new Clip("Olympia Bar, Lake Powell, Utah · M005_C017_F01","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M005_C017_F01_2K_AVC.mov"),
   new Clip("Ghansali from Above · ANN0010","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0010_flamecomp_v0007.00735687_edit__SDR_2K_AVC.mov"),
   new Clip("Through Himalayas · ANN0020","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0020_flamecomp_v0003.00776567__SDR_2K_AVC.mov"),
   new Clip("Tea Gardens from Above · ANN0040","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0040_flamecomp_v0006.00789786__SDR_2K_AVC.mov"),
   new Clip("Goa Beaches · ANN0060","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0060_flamecomp_v0003.00815886__SDR_2K_AVC.mov"),
   new Clip("Reservoir Day · ANN0070","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0070_flamecomp_v0008.00289423__SDR_2K_AVC.mov"),
   new Clip("Goa Coast · ANN0080","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0080_flamecomp_v0004.00853686__SDR_2K_AVC.mov"),
   new Clip("Himalayas Day · ANN0090","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0090_flamecomp_v0007.00872132__SDR_2K_AVC.mov"),
   new Clip("Tea Gardens Day · ANN0100","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0100_flamecomp_v0002.00880232__SDR_2K_AVC.mov"),
   new Clip("The Ganges · ANN0110","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0110_flamecomp_v0002.00896432__SDR_2K_AVC.mov"),
   new Clip("Himalayan Peaks · ANN0120","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0120_flamecomp_v0011.00400416__SDR_2K_AVC.mov"),
   new Clip("Tea Gardens Mist · ANN0130","https://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0130_flamecomp_v0002.00193199__SDR_2K_AVC.mov")));
  categories.add(new Category("Villes · Dubai",
   new Clip("Dubai · DB_D002_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_DB_D002_C003_PSNK_v04_SDR_PS_20180914_SDR_2K_AVC.mov"),
   new Clip("Dubai · DB_D001_C005","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_DB_D001_C005_COMP_PSNK_v12_SDR_PS_20180912_SDR_2K_AVC.mov"),
   new Clip("Dubai · DB_D008_C010","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_DB_D008_C010_PSNK_v21_SDR_PS_20180914_F0F16157_SDR_2K_AVC.mov"),
   new Clip("Dubai · DB_D011_C010","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_DB_D011_C010_PSNK_DENOISE_v19_SDR_PS_20180914_SDR_2K_AVC.mov"),
   new Clip("Dubai · DB_D001_C001","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_DB_D001_C001_PSNK_v06_SDR_PS_20180824_SDR_2K_AVC.mov")));
  categories.add(new Category("Villes · Hong Kong",
   new Clip("Hong Kong · HK_H004_C013","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_HK_H004_C013_t9_6M_HB_tag0.mov"),
   new Clip("Hong Kong · HK_H004_C010","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_HK_H004_C010_PSNK_v08_SDR_PS_20181009_SDR_2K_AVC.mov"),
   new Clip("Hong Kong · HK_B005_C011","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_HK_B005_C011_PSNK_v16_SDR_PS_20180914_SDR_2K_AVC.mov"),
   new Clip("Hong Kong · HK_H004_C008","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_HK_H004_C008_PSNK_v19_SDR_PS_20180914_SDR_2K_AVC.mov")));
  categories.add(new Category("Villes · San Francisco",
   new Clip("San Francisco · A007_C017","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A007_C017_01156B_v02_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("San Francisco · A015_C018","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A015_C018_0128ZS_v03_SDR_PS_FINAL_20180709__SDR_2K_AVC.mov"),
   new Clip("San Francisco · A013_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_1223LV_FLARE_v21_SDR_PS_FINAL_20180709_F0F5700_SDR_2K_AVC.mov"),
   new Clip("San Francisco · A013_C012","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A013_C012_0122D6_CC_v01_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("San Francisco · A012_C014","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A012_C014_1223PT_v53_SDR_PS_FINAL_20180709_F0F8700_SDR_2K_AVC.mov"),
   new Clip("San Francisco · A006_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A006_C003_1219EE_CC_v01_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("San Francisco · A008_C007","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_A008_C007_011550_CC_v01_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov")));
  categories.add(new Category("Villes · Los Angeles",
   new Clip("Los Angeles · LA_A005_C009","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LA_A005_C009_PSNK_ALT_v09_SDR_PS_201809134_SDR_2K_AVC.mov"),
   new Clip("Los Angeles · LA_A009_C009","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LA_A009_C009_PSNK_v02_SDR_PS_FINAL_20180709_SDR_2K_AVC.mov"),
   new Clip("Los Angeles · LA_A006_C008","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LA_A006_C008_PSNK_ALL_LOGOS_v10_SDR_PS_FINAL_20180801_SDR_2K_AVC.mov"),
   new Clip("Los Angeles · LA_A006_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LA_A006_C004_v01_SDR_FINAL_PS_20180730_SDR_2K_AVC.mov"),
   new Clip("Los Angeles · LA_A011_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LA_A011_C003_DGRN_LNFIX_STAB_v57_SDR_PS_20181002_SDR_2K_AVC.mov")));
  categories.add(new Category("Villes · New York",
   new Clip("New York · N008_C009","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_N008_C009_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("New York · N013_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_N013_C004_PS_v01_SDR_PS_20180925_F1970F7193_SDR_2K_AVC.mov"),
   new Clip("New York · N008_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_N008_C003_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("New York · N003_C006","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_N003_C006_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov")));
  categories.add(new Category("Villes · London",
   new Clip("London · L010_C006","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_L010_C006_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("London · L004_C011","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_L004_C011_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("London · L007_C007","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_L007_C007_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov"),
   new Clip("London · L012_C002","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_L012_c002_PS_v01_SDR_PS_20180925_SDR_2K_AVC.mov")));
  categories.add(new Category("Océan Apple",
   new Clip("Tahiti Waves · TH_803_A001_8","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/g201_TH_803_A001_8_SDR_20191031_SDR_2K_AVC.mov"),
   new Clip("California Dolphins · B002_C011","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/DL_B002_C011_SDR_20191122_SDR_2K_AVC.mov"),
   new Clip("Barracuda · BO_A018_C029","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/BO_A018_C029_SDR_20190812_SDR_2K_AVC.mov"),
   new Clip("Gray Reef Sharks · U009_C004","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/FK_U009_C004_SDR_20191220_SDR_2K_AVC.mov"),
   new Clip("Jacks · A014_C023","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/BO_A014_C023_SDR_20190717_F240F3709_SDR_2K_AVC.mov"),
   new Clip("Sea Stars · A012_C031","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/BO_A012_C031_SDR_20190726_SDR_2K_AVC.mov"),
   new Clip("Cownose Rays · A006_C008","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/MEX_A006_C008_SDR_20190923_SDR_2K_AVC.mov"),
   new Clip("Kelp · KP_A010_C002","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/KP_A010_C002_SDR_20190717_SDR_2K_AVC.mov"),
   new Clip("Bumpheads · BO_A014_C008","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/BO_A014_C008_SDR_20190719_SDR_2K_AVC.mov"),
   new Clip("Red Sea Coral · A008_C010","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/RS_A008_C010_SDR_20191218_SDR_2K_AVC.mov"),
   new Clip("Seals · SE_A016_C009","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/SE_A016_C009_SDR_20190717_SDR_2K_AVC.mov"),
   new Clip("Tahiti Waves · TH_804_A001_8","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/g201_TH_804_A001_8_SDR_20191031_SDR_2K_AVC.mov"),
   new Clip("Palau Jellies · PA_A001_C007","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/PA_A001_C007_SDR_20190717_SDR_2K_AVC.mov"),
   new Clip("Alaskan Jellies · A004_C012","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/AK_A004_C012_SDR_20191217_SDR_2K_AVC.mov"),
   new Clip("Alaskan Jellies · A003_C014","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/g201_AK_A003_C014_SDR_20191113_SDR_2K_AVC.mov"),
   new Clip("Costa Rica Dolphins · A009_C007","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/CR_A009_C007_SDR_20191113_SDR_2K_AVC.mov"),
   new Clip("Humpback Whale · D004_L014","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/g201_WH_D004_L014_SDR_20191031_SDR_2K_AVC.mov"),
   new Clip("Palau Jellies · PA_A002_C009","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/PA_A002_C009_SDR_20190730_ALT01_SDR_2K_AVC.mov"),
   new Clip("California Kelp Forest · A016_C002","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/g201_CA_A016_C002_SDR_20191114_SDR_2K_AVC.mov"),
   new Clip("Palau Jellies · PA_A010_C007","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/PA_A010_C007_SDR_20190717_SDR_2K_AVC.mov"),
   new Clip("Palau Coral · PA_A004_C003","https://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/PA_A004_C003_SDR_20190719_SDR_2K_AVC.mov")));
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
  collectionFailed=false;
  cancelTimeout();
  if(prefetchWriter!=null)prefetchWriter.cancel();
  generation++;
  outgoingPlayer = player;
  outgoingVideo = video;
  video = new TextureView(activity);
  video.setAlpha(0f);
  overlay.addView(video, 1, new FrameLayout.LayoutParams(-1, -1));
  video.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->crop());
  busy=true;hasFrame=false;playingCategory=category;playingClip=clip;
  final int token=generation;final Clip selected=categories.get(category).clips[clip];
  clearCaption();
  if (outgoingPlayer == null) showStatus("Chargement…");
  else status.setVisibility(View.GONE);
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("GlassTV/AndroidTV").setConnectTimeoutMs(8000).setReadTimeoutMs(12000);
  CacheDataSource.Factory data=new CacheDataSource.Factory().setCache(cache(activity)).setUpstreamDataSourceFactory(new DefaultDataSource.Factory(activity,http)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
  DefaultMediaSourceFactory media=new DefaultMediaSourceFactory(data).setLoadErrorHandlingPolicy(new androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1));
  player=new ExoPlayer.Builder(activity,new DefaultRenderersFactory(activity).setEnableDecoderFallback(true)).setMediaSourceFactory(media).build();player.setVideoTextureView(video);player.setVolume(0);
  player.addListener(new Player.Listener(){
   @Override public void onVideoSizeChanged(VideoSize size){if(token==generation&&size.height>0){videoRatio=(float)size.width*size.pixelWidthHeightRatio/size.height;crop();}}
   @Override public void onRenderedFirstFrame(){
    if(token!=generation||hasFrame)return;hasFrame=true;cancelTimeout();
    Log.i("GLASSTV_AERIAL","FIRST_FRAME category="+playingCategory+" clip="+playingClip+" remote="+selected.url.startsWith("https://"));
    showCaption(categories.get(playingCategory).name, selected.name);
    video.animate().alpha(1).setDuration(FADE_MS).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(()->{
     if(token!=generation)return;
     releaseOutgoing();
     busy=false;
     prefetchNeighbours();
     Log.i("GLASSTV_AERIAL","TRANSITION_DONE category="+playingCategory+" clip="+playingClip);
     if(category!=playingCategory||clip!=playingClip)handler.post(loadSelection);
    }).start();
   }
   @Override public void onPlayerError(PlaybackException error){if(token==generation){Log.w("GLASSTV_AERIAL",error.getErrorCodeName());failure(connectionDiagnostic(error, selected.url));}}
   @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&token==generation&&!busy)navigate(1,0);}
  });
  player.setMediaItem(MediaItem.fromUri(Uri.parse(selected.url)));player.prepare();player.play();
  timeout=()->{if(token==generation)failure("Première image non reçue après 22 s · "+Uri.parse(selected.url).getHost());};handler.postDelayed(timeout,22000);
 }
 private void failure(String reason){
  failed.add(categories.get(playingCategory).clips[playingClip].url);
  cancelTimeout();generation++;
  video.animate().withEndAction(null).cancel();
  if(player!=null){player.clearVideoSurface();player.release();}
  overlay.removeView(video);
  player=outgoingPlayer;video=outgoingVideo;outgoingPlayer=null;outgoingVideo=null;
  busy=false;
  if(category!=playingCategory||clip!=playingClip){handler.post(loadSelection);return;}
  Clip[] choices=categories.get(category).clips;
  for(int n=1;n<=choices.length;n++){int next=(clip+n)%choices.length;if(!failed.contains(choices[next].url)){clip=next;handler.post(loadSelection);return;}}
  clearCaption();
  collectionFailed=true;
  showStatus("Lecture impossible · "+categories.get(playingCategory).name+"\n"+reason+"\nOK : réessayer · Haut/bas : catégorie · Retour : quitter");
 }
 private String connectionDiagnostic(PlaybackException error, String url) {
  String cause = error.getErrorCodeName();
  Throwable current = error;
  Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
  while (current != null && seen.add(current)) {
   if (current instanceof java.security.cert.CertificateException ||
       current instanceof javax.net.ssl.SSLPeerUnverifiedException) {
    cause = "Certificat HTTPS refusé (" + current.getClass().getSimpleName() + ")";
    break;
   }
   if (current instanceof javax.net.ssl.SSLException) cause = "Échec HTTPS (" + current.getClass().getSimpleName() + ")";
   if (current instanceof java.net.UnknownHostException) cause = "DNS : serveur introuvable";
   if (current instanceof java.net.SocketTimeoutException) cause = "Délai de connexion dépassé";
   if (current instanceof java.net.ConnectException) cause = "Connexion au serveur impossible";
   if (current instanceof HttpDataSource.InvalidResponseCodeException)
    cause = "Réponse HTTP " + ((HttpDataSource.InvalidResponseCodeException)current).responseCode;
   current = current.getCause();
  }
  return Uri.parse(url).getHost() + " · " + cause;
 }
 private void retryCollection() {
  for(Clip entry:categories.get(category).clips)failed.remove(entry.url);
  handler.removeCallbacks(loadSelection);
  collectionFailed=false;
  handler.post(loadSelection);
 }
 private void prefetchNeighbours() {
  if(prefetch==null||prefetch.isShutdown())return;
  if(prefetchWriter!=null)prefetchWriter.cancel();
  final ArrayList<String> urls=new ArrayList<>();
  Clip[] row=categories.get(playingCategory).clips;
  urls.add(row[(playingClip+1)%row.length].url);
  urls.add(row[Math.floorMod(playingClip-1,row.length)].url);
  urls.add(categories.get((playingCategory+1)%categories.size()).clips[0].url);
  urls.add(categories.get(Math.floorMod(playingCategory-1,categories.size())).clips[0].url);
  final int token=generation;
  prefetch.execute(()->{
   for(String url:urls){
    if(token!=generation||Thread.currentThread().isInterrupted())return;
    if(!url.startsWith("https://"))continue;
    DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(4000).setReadTimeoutMs(4000);
    CacheDataSource data=new CacheDataSource.Factory().setCache(cache(activity)).setUpstreamDataSourceFactory(http).createDataSource();
    CacheWriter writer=new CacheWriter(data,new DataSpec.Builder().setUri(url).setLength(2L*1024*1024).build(),null,null);
    prefetchWriter=writer;
    if(token!=generation)return;
    try{writer.cache();}catch(java.io.IOException ignored){/* Playback retries normally. */}
   }
  });
 }
 private void cancelTimeout(){if(timeout!=null){handler.removeCallbacks(timeout);timeout=null;}}
 private void releaseOutgoing() {
  if(outgoingPlayer!=null){outgoingPlayer.clearVideoSurface();outgoingPlayer.release();outgoingPlayer=null;}
  if(outgoingVideo!=null){overlay.removeView(outgoingVideo);outgoingVideo=null;}
 }
 private void releasePlayer(){if(video!=null)video.animate().withEndAction(null).cancel();releaseOutgoing();cancelTimeout();generation++;if(cover!=null){cover.animate().setListener(null);cover.animate().cancel();}if(player!=null){player.clearVideoSurface();player.release();player=null;}}
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
   switch(code){case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:if(collectionFailed)retryCollection();else stop();break;case KeyEvent.KEYCODE_DPAD_LEFT:navigate(-1,0);break;case KeyEvent.KEYCODE_DPAD_RIGHT:navigate(1,0);break;case KeyEvent.KEYCODE_DPAD_UP:navigate(0,-1);break;case KeyEvent.KEYCODE_DPAD_DOWN:navigate(0,1);break;default:stop();break;}
  }return true;
 }
 void stop(){if(prefetchWriter!=null)prefetchWriter.cancel();if(prefetch!=null){prefetch.shutdownNow();prefetch=null;}releasePlayer();handler.removeCallbacks(loadSelection);clearCaption();busy=false;hasFrame=false;
  if(overlay!=null){overlay.setKeepScreenOn(false);host.removeView(overlay);overlay=null;video=null;cover=null;status=null;caption=null;}
  if(snapshot!=null){snapshot.recycle();snapshot=null;}if(previous!=null){previous.requestFocus();previous=null;}
 }
}
