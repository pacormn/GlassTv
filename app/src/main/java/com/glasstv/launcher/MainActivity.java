package com.glasstv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Native TV home: real application banners, remote focus and lightweight glass layers. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<Card> cards = new ArrayList<>();
    private SharedPreferences prefs;
    private FrameLayout root, panel;
    private LinearLayout content;
    private ScrollView scroll;
    private Wallpaper wallpaper;
    private TextView time, heroTitle, heroSubtitle, controls;
    private View lastFocus;
    private boolean resumed, reduced, rebuilding;
    private float unit;
    private int palette;
    private final Runnable clockTick = new Runnable() {
        public void run() {
            if (!resumed) return;
            time.setText(new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date()));
            handler.postDelayed(this, 10000);
        }
    };
    private static class App {
        String pkg, label; Intent launch; Drawable art; boolean banner;
    }
    private int px(float n) { return Math.round(n * unit); }
    private int duration(int ms) { return reduced ? 0 : ms; }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(color);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
        v.setFontFeatureSettings("kern"); v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); return v;
    }
    private GradientDrawable glass(int top, int bottom, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top,bottom});
        d.setCornerRadius(px(radius)); d.setStroke(px(1),stroke); return d;
    }
    private FrameLayout.LayoutParams frame(int w, int h, int gravity, int x, int y) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w<0?w:px(w),h<0?h:px(h),gravity);
        p.leftMargin=px(x); p.topMargin=px(y); return p;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("appearance",MODE_PRIVATE);
        reduced=prefs.getBoolean("reduced",false); palette=prefs.getInt("palette",0);
        unit=getResources().getDisplayMetrics().widthPixels/1920f;
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root=new FrameLayout(this); setContentView(root);
        wallpaper=new Wallpaper(); root.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        buildHeader();
        scroll=new ScrollView(this); scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false); scroll.setSmoothScrollingEnabled(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false); content.setClipToPadding(false); content.setPadding(px(110),px(40),px(110),px(100));
        scroll.addView(content); FrameLayout.LayoutParams sp=frame(-1,-1,Gravity.TOP,0,315);sp.bottomMargin=px(65);root.addView(scroll,sp);
        controls=text("OK  Ouvrir     •     Maintenir OK  Favoris     •     Menu  Centre de contrôle",21,0x99ffffff);
        FrameLayout.LayoutParams cp=frame(-2,30,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,0);cp.bottomMargin=px(27);root.addView(controls,cp);
    }
    private void buildHeader() {
        TextView brand=text("GlassTV",28,0xbbffffff);
        brand.setTypeface(Typeface.create("sans-serif-medium",0));root.addView(brand,frame(260,40,Gravity.TOP,112,63));
        time=text("",30,0xddffffff);time.setGravity(Gravity.RIGHT);
        root.addView(time,frame(145,45,Gravity.TOP,1510,60));
        TextView button=text("◉",35,Color.WHITE);button.setGravity(Gravity.CENTER);button.setContentDescription("Centre de contrôle");
        button.setBackground(glass(0x25ffffff,0x0fffffff,32,0x33ffffff));button.setFocusable(true);button.setId(View.generateViewId());
        button.setOnClickListener(v->showControls());
        button.setOnFocusChangeListener((v,focus)->{v.animate().scaleX(focus?1.12f:1).scaleY(focus?1.12f:1).setDuration(duration(180)).start();});
        root.addView(button,frame(66,66,Gravity.TOP,1735,48));
        heroTitle=text("Bienvenue chez vous.",64,Color.WHITE);heroTitle.setTypeface(Typeface.create("sans-serif-medium",0));
        root.addView(heroTitle,frame(1450,90,Gravity.TOP,112,175));
        heroSubtitle=text("Vos applications. Au premier plan.",27,0xaaffffff);
        root.addView(heroSubtitle,frame(1450,50,Gravity.TOP,115,265));
    }
    @Override protected void onResume() {
        super.onResume(); resumed=true; root.setAlpha(1f);root.setScaleX(1);root.setScaleY(1);
        refreshApps(); handler.removeCallbacks(clockTick);clockTick.run();
    }
    @Override protected void onPause() { super.onPause();resumed=false;handler.removeCallbacks(clockTick);root.animate().cancel(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null);super.onDestroy(); }
    private void refreshApps() {
        String selected=lastFocus instanceof Card?((Card)lastFocus).app.pkg:"";
        LinkedHashMap<String,App> found=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}) {
            for(ResolveInfo ri:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)) {
                String pkg=ri.activityInfo.packageName;
                if(pkg.equals(getPackageName())||found.containsKey(pkg))continue;
                App app=new App();app.pkg=pkg;app.label=ri.loadLabel(getPackageManager()).toString();
                app.launch=new Intent(Intent.ACTION_MAIN).addCategory(category).setClassName(pkg,ri.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { app.art=ri.activityInfo.loadBanner(getPackageManager());app.banner=app.art!=null;
                    if(app.art==null)app.art=ri.loadIcon(getPackageManager());
                    found.put(pkg,app);
                } catch(RuntimeException ignored) { }
            }
        }
        apps.clear();apps.addAll(found.values());
        Set<String> favorites=prefs.getStringSet("favorites",Collections.emptySet());
        apps.sort((a,b)-> {int f=Boolean.compare(favorites.contains(b.pkg),favorites.contains(a.pkg));return f!=0?f:a.label.compareToIgnoreCase(b.label);});
        rebuilding=true;cards.clear();content.removeAllViews();
        if(apps.isEmpty()) { content.addView(text("Aucune application disponible",36,Color.WHITE));rebuilding=false;return; }
        int dockCount=Math.min(5,apps.size());
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(px(28),px(26),px(28),px(15));
        dock.setClipChildren(false);dock.setClipToPadding(false);dock.setBackground(glass(0x38dcd7f5,0x173b345a,40,0x49ffffff));
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,px(255));dp.bottomMargin=px(50);content.addView(dock,dp);
        for(int i=0;i<dockCount;i++)addCard(dock,apps.get(i),i,true);
        if(apps.size()>5) {
            TextView title=text("Applications",29,0xccffffff); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,px(60));tp.leftMargin=px(28);content.addView(title,tp);
            for(int i=5;i<apps.size();i+=5) {
                LinearLayout row=new LinearLayout(this);row.setPadding(px(28),px(12),px(28),px(12));row.setClipChildren(false);row.setClipToPadding(false);
                content.addView(row,new LinearLayout.LayoutParams(-1,px(245)));
                for(int j=i;j<Math.min(i+5,apps.size());j++)addCard(row,apps.get(j),j,false);
            }
        }
        for(int i=0;i<cards.size();i++) {
            Card c=cards.get(i);c.setNextFocusLeftId(cards.get(i%5==0?i:i-1).getId());
            c.setNextFocusRightId(cards.get(i%5==4||i==cards.size()-1?i:i+1).getId());
            if(i>=5)c.setNextFocusUpId(cards.get(i-5).getId());
            c.setNextFocusDownId(cards.get(i/5<(cards.size()-1)/5?Math.min(i+5,cards.size()-1):i).getId());
        }
        rebuilding=false;
        Card restore=cards.get(0);for(Card c:cards)if(c.app.pkg.equals(selected))restore=c;
        final Card focus=restore;scroll.post(()->{if(panel==null)focus.requestFocus();});
    }
    private void addCard(LinearLayout row,App app,int index,boolean dock) {
        Card c=new Card(app,index,dock);cards.add(c);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(292),px(215));p.rightMargin=px(38);row.addView(c,p);
    }
    private class Card extends FrameLayout {
        final App app;final FrameLayout tile;final TextView label;final ImageView art;final Shine shine;
        Card(App a,int index,boolean dock) {
            super(MainActivity.this);app=a;setId(View.generateViewId());setFocusable(true);setClickable(true);setLongClickable(true);
            setClipChildren(false);setClipToPadding(false);setContentDescription(a.label);
            tile=new FrameLayout(MainActivity.this);tile.setBackground(glass(0xff33323e,0xff1b1b24,24,0x22ffffff));tile.setClipToOutline(true);
            addView(tile,new FrameLayout.LayoutParams(-1,px(165)));
            art=new ImageView(MainActivity.this);art.setImageDrawable(a.art);art.setScaleType(a.banner?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
            if(!a.banner)art.setPadding(px(58),px(24),px(58),px(24));tile.addView(art,new FrameLayout.LayoutParams(-1,-1));
            shine=new Shine();tile.addView(shine,new FrameLayout.LayoutParams(-1,-1));shine.setAlpha(.15f);
            label=text(a.label,24,Color.WHITE);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);label.setAlpha(.58f);
            addView(label,frame(-1,40,Gravity.TOP,0,183));
            setOnFocusChangeListener((v,focused)-> {
                if(rebuilding)return;
                tile.animate().cancel();art.animate().cancel();shine.animate().cancel();
                tile.animate().scaleX(focused?1.09f:1).scaleY(focused?1.09f:1).translationZ(px(focused?26:0)).setDuration(duration(260)).setInterpolator(new OvershootInterpolator(.8f)).start();
                art.animate().scaleX(focused?1.035f:1).scaleY(focused?1.035f:1).setDuration(duration(330)).start();
                shine.animate().alpha(focused?1:.15f).setDuration(duration(220)).start();label.animate().alpha(focused?1:.58f).setDuration(duration(180)).start();
                if(focused) {lastFocus=this;heroTitle.setText(a.label);heroSubtitle.setText(prefs.getStringSet("favorites",Collections.emptySet()).contains(a.pkg)?"Dans vos favoris":"Prêt à regarder.");
                    wallpaper.animate().translationX(px((index%5-2)*-7)).translationY(px(index<5?0:-12)).setDuration(duration(600)).setInterpolator(new DecelerateInterpolator()).start();
                }
            });
            setOnClickListener(v->launch(this));setOnLongClickListener(v->{toggleFavorite(app);return true;});
        }
    }
    private class Shine extends View {
        final Paint paint=new Paint(3);Shader shader;
        Shine(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){shader=new LinearGradient(0,0,w,h,new int[]{0x65ffffff,0x08ffffff,0x00ffffff,0x22ffffff},new float[]{0,.38f,.65f,1},Shader.TileMode.CLAMP);}
        @Override protected void onDraw(Canvas c){paint.setShader(shader);c.drawRoundRect(1,1,getWidth()-1,getHeight()-1,px(24),px(24),paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(px(2));paint.setColor(0x85ffffff);c.drawRoundRect(px(1),px(1),getWidth()-px(1),getHeight()-px(1),px(24),px(24),paint);paint.setStyle(Paint.Style.FILL);}
    }
    private void launch(Card card) {
        card.tile.animate().scaleX(1.03f).scaleY(1.03f).setDuration(duration(90)).withEndAction(()->{
            if(!resumed)return;
            try {startActivity(card.app.launch);overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
            catch(android.content.ActivityNotFoundException | SecurityException ex){Toast.makeText(this,"Application indisponible",Toast.LENGTH_SHORT).show();refreshApps();}
        }).start();
    }
    private void toggleFavorite(App app) {
        Set<String> favorites=new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));
        boolean removed=favorites.remove(app.pkg);if(!removed)favorites.add(app.pkg);
        prefs.edit().putStringSet("favorites",favorites).apply();
        Toast.makeText(this,removed?"Retirée des favoris":"Ajoutée aux favoris",Toast.LENGTH_SHORT).show();refreshApps();
    }
    private void showControls() {
        if(panel!=null)return;
        View focused=getCurrentFocus();if(focused!=null)lastFocus=focused;
        panel=new FrameLayout(this);panel.setBackgroundColor(0x88040815);root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(px(40),px(36),px(40),px(30));sheet.setBackground(glass(0xee393749,0xee171925,38,0x55ffffff));
        FrameLayout.LayoutParams p=frame(560,-2,Gravity.RIGHT|Gravity.TOP,0,65);p.rightMargin=px(70);panel.addView(sheet,p);
        TextView heading=text("Centre de contrôle",36,Color.WHITE);sheet.addView(heading,new LinearLayout.LayoutParams(-1,px(75)));
        TextView first=control(sheet,"Réglages Android TV",()->{try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Réglages indisponibles",Toast.LENGTH_SHORT).show();}});
        control(sheet,"Ambiance  ·  "+new String[]{"Crépuscule","Océan","Minuit"}[palette],()->new AlertDialog.Builder(this).setTitle("Ambiance").setSingleChoiceItems(new String[]{"Crépuscule","Océan","Minuit"},palette,(d,which)->{palette=which;prefs.edit().putInt("palette",which).apply();wallpaper.reset();d.dismiss();closeControls();}).show());
        control(sheet,"Animations  ·  "+(reduced?"Réduites":"Fluides"),()->{reduced=!reduced;prefs.edit().putBoolean("reduced",reduced).apply();closeControls();});
        control(sheet,"Actualiser les applications",()->{closeControls();refreshApps();});
        TextView close=control(sheet,"Fermer",this::closeControls);first.setNextFocusUpId(first.getId());close.setNextFocusDownId(close.getId());
        TextView version=text("GlassTV 0.2.0  •  Interface locale",19,0x88ffffff);version.setPadding(0,px(25),0,0);sheet.addView(version);
        sheet.setTranslationX(px(90));sheet.setAlpha(0);sheet.animate().translationX(0).alpha(1).setDuration(duration(280)).setInterpolator(new DecelerateInterpolator()).start();
        first.requestFocus();
    }
    private TextView control(LinearLayout parent,String title,Runnable action) {
        TextView v=text(title,27,Color.WHITE);v.setId(View.generateViewId());v.setNextFocusLeftId(v.getId());v.setNextFocusRightId(v.getId());v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(px(24),0,px(18),0);v.setFocusable(true);v.setClickable(true);
        v.setBackground(glass(0x18ffffff,0x0cffffff,20,0x15ffffff));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(78));p.bottomMargin=px(13);parent.addView(v,p);
        v.setOnFocusChangeListener((view,focus)->{v.setTextColor(focus?0xff151520:Color.WHITE);v.setBackground(glass(focus?0xfff5f2ff:0x18ffffff,focus?0xffd8d9e6:0x0cffffff,20,0x55ffffff));v.animate().scaleX(focus?1.025f:1).scaleY(focus?1.025f:1).setDuration(duration(160)).start();});v.setOnClickListener(view->action.run());return v;
    }
    private void closeControls(){if(panel==null)return;root.removeView(panel);panel=null;if(lastFocus!=null)lastFocus.requestFocus();}
    @Override public void onBackPressed(){if(panel!=null)closeControls();else if(!cards.isEmpty()){scroll.smoothScrollTo(0,0);cards.get(0).requestFocus();}}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(panel==null)showControls();else closeControls();}return true;}return super.dispatchKeyEvent(event);}
    private class Wallpaper extends View {
        final Paint paint=new Paint(3);final Path wave=new Path();Shader base,glow,shade;
        Wallpaper(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void reset(){makeShaders(getWidth(),getHeight());invalidate();}
        void makeShaders(int w,int h){if(w==0||h==0)return;int[][] colors={{0xff14182d,0xff3e365c,0xff90788c},{0xff071d2d,0xff164958,0xff497c87},{0xff030711,0xff121c36,0xff313353}};int[] a=colors[palette];base=new LinearGradient(0,0,w,h,a,null,Shader.TileMode.CLAMP);glow=new RadialGradient(w*.73f,h*.22f,w*.65f,new int[]{palette==1?0x7869c3ce:0x789e8aba,0x00513a78},null,Shader.TileMode.CLAMP);shade=new LinearGradient(0,0,0,h,new int[]{0x11000000,0x00000000,0xaa050914},new float[]{0,.35f,1},Shader.TileMode.CLAMP);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){makeShaders(w,h);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();paint.setShader(base);c.drawRect(0,0,w,h,paint);paint.setShader(glow);c.drawRect(0,0,w,h,paint);paint.setShader(null);
            for(int i=0;i<3;i++){wave.reset();wave.moveTo(-w*.1f,h*(.7f+i*.1f));wave.cubicTo(w*.25f,h*(.2f+i*.12f),w*.65f,h*(1.1f-i*.1f),w*1.1f,h*(.25f+i*.18f));wave.lineTo(w*1.1f,h*1.2f);wave.lineTo(-w*.1f,h*1.2f);wave.close();paint.setColor(new int[]{0x283d315b,0x30463865,0x440a1027}[i]);c.drawPath(wave,paint);}
            paint.setShader(shade);c.drawRect(0,0,w,h,paint);paint.setShader(null);
        }
    }
}
