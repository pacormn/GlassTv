package com.glasstv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private HomeView home;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        home = new HomeView(); setContentView(home); home.requestFocus();
    }
    @Override protected void onResume() { super.onResume(); if (home != null) home.refresh(); }
    @Override public void onBackPressed() { /* Keep HOME stable; Back should not close the launcher. */ }

    private static class AppItem {
        String label, pkg; Drawable icon; Intent intent;
        AppItem(String l, String p, Drawable d, Intent i) { label=l; pkg=p; icon=d; intent=i; }
    }

    private class HomeView extends View {
        private final Paint p = new Paint(3);
        private final ArrayList<AppItem> apps = new ArrayList<>();
        private final Random random = new Random(17);
        private final float[] stars = new float[140];
        private int selected=0, scrollRow=0;
        private boolean longPressHandled;
        private long start=SystemClock.uptimeMillis();
        private final int cols=6;
        private final float tileW=160, tileH=126, gapX=21, gapY=30;
        private final RectF r = new RectF();
        private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.FRANCE);
        HomeView() {
            super(MainActivity.this);
            setFocusable(true); setFocusableInTouchMode(true);
            for(int i=0;i<stars.length;i++) stars[i]=random.nextFloat();
            refresh();
        }
        void refresh() {
            PackageManager pm=getPackageManager();
            String old = apps.isEmpty() ? "" : apps.get(Math.min(selected,apps.size()-1)).pkg;
            LinkedHashMap<String,AppItem> found=new LinkedHashMap<>();
            for(String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent q=new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for(ResolveInfo ri:pm.queryIntentActivities(q,0)) {
                    String pkg=ri.activityInfo.packageName;
                    if(pkg.equals(getPackageName()) || found.containsKey(pkg)) continue;
                    Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(cat);
                    launch.setClassName(pkg,ri.activityInfo.name);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { found.put(pkg,new AppItem(ri.loadLabel(pm).toString(),pkg,ri.loadIcon(pm),launch)); }
                    catch(Exception ignored) {}
                }
            }
            apps.clear(); apps.addAll(found.values());
            Collections.sort(apps,(a,b)->a.label.compareToIgnoreCase(b.label));
            selected=0;
            for(int i=0;i<apps.size();i++) if(apps.get(i).pkg.equals(old)) {selected=i;break;}
            scrollRow=Math.max(0,selected/cols-1);
            invalidate();
        }
        @Override protected void onDraw(Canvas real) {
            super.onDraw(real);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            Canvas c=real;
            int save=c.save(); c.scale(w/1920f,h/1080f);
            float t=(SystemClock.uptimeMillis()-start)/1000f;
            drawLandscape(c,t);
            drawTop(c);
            drawGrid(c,t);
            c.restoreToCount(save);
            postInvalidateDelayed(33);
        }
        private void drawLandscape(Canvas c,float t) {
            float shift=(float)Math.sin(t*.045f)*65;
            p.setShader(new LinearGradient(0,0,0,1080,new int[]{0xff061126,0xff1c4563,0xff68839b,0xffd2a4a0},new float[]{0,.40f,.72f,1},Shader.TileMode.CLAMP));
            c.drawRect(0,0,1920,1080,p);p.setShader(null);
            // Aurora-like atmospheric glow and slow parallax.
            p.setShader(new RadialGradient(1380+shift,440,730,new int[]{0x6677b5bd,0x2261a3ad,0x00102244},null,Shader.TileMode.CLAMP));
            c.drawCircle(1380+shift,440,730,p);p.setShader(null);
            p.setColor(0x77ffffff);
            for(int i=0;i<stars.length/2;i++) {
                float x=stars[i*2]*1920, y=stars[i*2+1]*540;
                c.drawCircle(x,y, .6f+(i%3)*.45f,p);
            }
            drawMountain(c, t, 0xff344f6b,650, .08f, 0);
            drawMountain(c, t, 0xff1b344e,760, .13f, 120);
            drawMountain(c, t, 0xff0d233b,870, .22f, 220);
            p.setShader(new LinearGradient(0,750,0,1080,0x00000000,0xb9000717,Shader.TileMode.CLAMP));
            c.drawRect(0,700,1920,1080,p);p.setShader(null);
        }
        private void drawMountain(Canvas c,float t,int color,float baseline,float speed,float phase) {
            float dx=(float)Math.sin(t*.03f+phase)*speed*150;
            Path path=new Path();path.moveTo(-150,1080);path.lineTo(-150,baseline+90);
            for(int i=0;i<=12;i++) {
                float x=-150+i*190+dx;
                float y=baseline+(float)Math.sin(i*1.67+phase)*105+(float)Math.cos(i*.87+phase)*48;
                path.lineTo(x,y);
            }
            path.lineTo(2100,1080);path.close();p.setColor(color);c.drawPath(path,p);
        }
        private void drawTop(Canvas c) {
            p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
            p.setTextSize(45);p.setColor(0xeeffffff);
            c.drawText("GlassTV",112,112,p);
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(33);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(clock.format(new Date()),1808,105,p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(25);p.setColor(0xbbffffff);
            c.drawText("Votre espace. Sans publicité.",113,165,p);
        }
        private void drawGrid(Canvas c,float t) {
            float originX=405, originY=340;
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(46);p.setColor(0xffffffff);
            c.drawText("Applications",originX,278,p);
            if(apps.isEmpty()) {
                p.setTextSize(28);c.drawText("Aucune application détectée",originX,430,p);return;
            }
            int maxRows=3;
            int maxScroll=Math.max(0,(apps.size()-1)/cols-maxRows+1);
            scrollRow=Math.min(scrollRow,maxScroll);
            for(int i=0;i<apps.size();i++) {
                int row=i/cols-scrollRow,col=i%cols;
                if(row<0||row>=maxRows)continue;
                float x=originX+col*(tileW+gapX), y=originY+row*(tileH+gapY);
                boolean focused=i==selected;
                float scale=focused?1.105f:1f;
                c.save();c.scale(scale,scale,x+tileW/2,y+tileH/2);
                r.set(x,y,x+tileW,y+tileH);
                p.setShadowLayer(focused?32:15,0,focused?13:8,focused?0x99000000:0x66000000);
                p.setColor(focused?0x75ffffff:0x3bffffff);
                c.drawRoundRect(r,29,29,p);p.clearShadowLayer();
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(focused?2.4f:1f);
                p.setColor(focused?0xc8ffffff:0x55ffffff);c.drawRoundRect(r,29,29,p);
                p.setStyle(Paint.Style.FILL);
                Drawable icon=apps.get(i).icon;
                if(icon!=null) {
                    int ix=(int)x+43,iy=(int)y+15;
                    icon.setBounds(ix,iy,ix+74,iy+74);icon.draw(c);
                }
                p.setTextSize(18);p.setColor(0xffffffff);
                p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
                p.setTextAlign(Paint.Align.CENTER);
                String name=apps.get(i).label;
                while(p.measureText(name)>tileW-16 && name.length()>3) name=name.substring(0,name.length()-2)+"…";
                c.drawText(name,x+tileW/2,y+112,p);p.setTextAlign(Paint.Align.LEFT);
                c.restore();
            }
            p.setColor(0xbfffffff);p.setTextSize(22);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            c.drawText("◀  ▶  Naviguer     OK  Ouvrir     Maintenir OK  Actualiser",originX,930,p);
            p.setTextSize(20);p.setColor(0x99ffffff);
            c.drawText("Paysage animé • Interface locale • Aucun suivi",originX,976,p);
        }
        @Override public boolean onKeyLongPress(int code, KeyEvent event) {
            if(code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER) {
                longPressHandled=true; refresh(); return true;
            }
            return super.onKeyLongPress(code,event);
        }
        @Override public boolean onKeyUp(int code, KeyEvent event) {
            if(code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER) {
                if(!longPressHandled && !event.isCanceled() && !apps.isEmpty()) {
                    try { startActivity(apps.get(selected).intent); }
                    catch(android.content.ActivityNotFoundException | SecurityException ex) {
                        android.widget.Toast.makeText(MainActivity.this,"Application indisponible",android.widget.Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                }
                longPressHandled=false;
                return true;
            }
            return super.onKeyUp(code,event);
        }
        @Override public boolean onKeyDown(int code,KeyEvent e) {
            if(apps.isEmpty())return super.onKeyDown(code,e);
            int old=selected;
            switch(code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: if(selected%cols>0)selected--;break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: if(selected%cols<cols-1&&selected+1<apps.size())selected++;break;
                case KeyEvent.KEYCODE_DPAD_UP: if(selected>=cols)selected-=cols;break;
                case KeyEvent.KEYCODE_DPAD_DOWN: if(selected/cols<(apps.size()-1)/cols)selected=Math.min(selected+cols,apps.size()-1);break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if(e.getRepeatCount()==0) { longPressHandled=false; e.startTracking(); }
                    return true;
                default:return super.onKeyDown(code,e);
            }
            if(selected/cols<scrollRow)scrollRow=selected/cols;
            if(selected/cols>=scrollRow+3)scrollRow=selected/cols-2;
            if(old!=selected) invalidate();
            return true;
        }
    }
}
