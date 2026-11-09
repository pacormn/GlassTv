package com.glasstv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
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
@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<Card> cards = new ArrayList<>();
    private SharedPreferences prefs;
    private Updates updates;
    private PhoneSetup phoneSetup;
    private String tmdbKey() { return getSharedPreferences("tmdb_private",0).getString("api_key", ""); }
    private void configureTmdb() {
        if(phoneSetup!=null)phoneSetup.close();
        phoneSetup=new PhoneSetup(this);
        phoneSetup.show(()->{Toast.makeText(this,"Clé TMDB enregistrée",Toast.LENGTH_LONG).show();refreshTmdb();});
    }
    private AerialPlayer aerial;
    private TextView developerBadge;
    private static final String AERIAL_SOURCE="https://sylvan.apple.com/Aerials/2x/Videos/DB_D011_C009_2K_SDR_HEVC.mov";
    private final Runnable idleSaver=()->{if(this.resumed&&this.panel==null&&hasWindowFocus()&&this.prefs.getBoolean("saver_auto",true))startSaver();};
    private void resetIdle(){handler.removeCallbacks(idleSaver);if(resumed&&aerial!=null&&!aerial.visible()&&prefs.getBoolean("saver_auto",true))handler.postDelayed(idleSaver,180000);}
    @Override public void onUserInteraction(){super.onUserInteraction();resetIdle();}
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "?";}}
    private void startSaver(){closeControls();handler.removeCallbacks(idleSaver);if(pageAnimator!=null)pageAnimator.cancel();aerial.start(prefs.getString("saver_video",AERIAL_SOURCE));}
    private void chooseSaverVideo(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),43);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Aucun sélecteur de vidéos installé",Toast.LENGTH_LONG).show();}}

    private FrameLayout root, panel;
    private LinearLayout content;
    private ScrollView scroll;
    private View favoritesBar;
    private Wallpaper wallpaper;
    private TextView time, heroTitle, heroSubtitle, controls;
    private View lastFocus;
    private boolean resumed, reduced, rebuilding;
    private float unit;
    private int palette;
    private boolean appsPage;
    private android.animation.ValueAnimator pageAnimator;
    private int pageTarget=-1;
    private Boolean animatedGrid;
    private Runnable pendingReveal;
    private final ArrayList<Card> dockCards=new ArrayList<>();
    private final ArrayList<Card> gridCards=new ArrayList<>();
    private final ArrayList<TextView> panelButtons=new ArrayList<>();
    private ImageView backdrop, heroLogo;
    private View heroShade;
    private View headerButton;
    private NowPlaying nowPlaying;
    private ScrollView panelScroll;
    private final java.util.concurrent.ExecutorService images=java.util.concurrent.Executors.newSingleThreadExecutor();
    private int imageGeneration;
    private static class TmdbMovie {
        int id, runtimeMinutes;
        String title="", backdropPath="", logoPath="";
        final ArrayList<String> genres = new ArrayList<>();
        Bitmap backdropBitmap, logoBitmap;
        boolean detailsLoaded, artworkLoading, artworkReady, artworkFailed;
        String subtitle() {
            String genreText = android.text.TextUtils.join(" · ", genres);
            String duration = runtimeMinutes > 0 ? (runtimeMinutes / 60 > 0
                ? (runtimeMinutes / 60) + " h " + String.format(Locale.FRANCE, "%02d", runtimeMinutes % 60) + " min"
                : runtimeMinutes + " min") : "Durée indisponible";
            return genreText.isEmpty() ? duration : genreText + "   ·   " + duration;
        }
    }
    private final ArrayList<TmdbMovie> tmdbMovies = new ArrayList<>();
    private final java.util.concurrent.ExecutorService tmdbRequests = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int tmdbIndex, tmdbGeneration, pendingMovieIndex=-1;
    private TmdbMovie activeTmdbMovie;
    private boolean showFilmHero;
    private Bitmap currentTmdbBackdrop;
    private final Runnable filmHeroTask = () -> {
        if (!resumed || appsPage || activeTmdbMovie == null || !activeTmdbMovie.artworkReady
                || !prefs.getString("backdrop", "").isEmpty()) return;
        transitionFilmHero();
    };
    private final Runnable tmdbSlide = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (prefs.getString("backdrop", "").isEmpty() && !tmdbMovies.isEmpty() && !appsPage) showNextTmdbBackdrop(tmdbGeneration);
            handler.postDelayed(this, 15000);
        }
    };
    private boolean hasTmdbKey() { return !tmdbKey().isEmpty(); }
    private void startTmdbSlideshow() {
        if (!hasTmdbKey() || !prefs.getString("backdrop", "").isEmpty()) return;
        final int generation = ++tmdbGeneration;
        handler.removeCallbacks(tmdbSlide);
        if (!tmdbMovies.isEmpty()) {
            showNextTmdbBackdrop(generation);
            handler.postDelayed(tmdbSlide, 15000);
            return;
        }
        tmdbRequests.execute(() -> {
            ArrayList<TmdbMovie> movies = new ArrayList<>();
            for (String category : new String[]{"now_playing", "upcoming"}) {
                java.net.HttpURLConnection connection = null;
                try {
                    String address = "https://api.themoviedb.org/3/movie/" + category
                        + "?api_key=" + java.net.URLEncoder.encode(tmdbKey(), "UTF-8")
                        + "&language=fr-FR&region=FR&page=1";
                    connection = (java.net.HttpURLConnection) new java.net.URL(address).openConnection();
                    connection.setConnectTimeout(8000); connection.setReadTimeout(10000);
                    connection.setRequestProperty("Accept", "application/json");
                    if (connection.getResponseCode() != 200) continue;
                    try (java.io.InputStream input = connection.getInputStream();
                         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192]; int count, total = 0;
                        while ((count = input.read(buffer)) != -1 && total < 1_000_000) {
                            output.write(buffer, 0, count); total += count;
                        }
                        org.json.JSONArray results = new org.json.JSONObject(output.toString("UTF-8")).optJSONArray("results");
                        if (results != null) for (int i = 0; i < results.length() && movies.size() < 8; i++) {
                            org.json.JSONObject item = results.optJSONObject(i);
                            if (item == null) continue;
                            String backdrop = item.optString("backdrop_path", "");
                            if (backdrop.isEmpty()) continue;
                            TmdbMovie movie = new TmdbMovie();
                            movie.id = item.optInt("id");
                            movie.title = item.optString("title", item.optString("original_title", "Film"));
                            movie.backdropPath = backdrop;
                            org.json.JSONArray genreIds = item.optJSONArray("genre_ids");
                            if (genreIds != null) for (int j = 0; j < genreIds.length(); j++) {
                                String genre = frenchGenre(genreIds.optInt(j));
                                if (genre != null && !movie.genres.contains(genre)) movie.genres.add(genre);
                            }
                            if (movie.id > 0 && !movie.title.isEmpty()) movies.add(movie);
                        }
                    }
                } catch (Exception ignored) {
                } finally { if (connection != null) connection.disconnect(); }
                if (movies.size() >= 8) break;
            }
            handler.post(() -> {
                if (isDestroyed() || generation != tmdbGeneration || !prefs.getString("backdrop", "").isEmpty()) return;
                tmdbMovies.clear(); tmdbMovies.addAll(movies); tmdbIndex = 0; pendingMovieIndex = 0;
                if (tmdbMovies.isEmpty()) {
                    Toast.makeText(this, "Aucune bannière TMDB disponible pour le moment", Toast.LENGTH_LONG).show();
                    return;
                }
                showNextTmdbBackdrop(generation);
                handler.removeCallbacks(tmdbSlide);
                handler.postDelayed(tmdbSlide, 15000);
            });
            for (TmdbMovie movie : movies) loadTmdbDetails(movie, generation);
        });
    }
    private String frenchGenre(int id) {
        switch (id) {
            case 28: return "Action"; case 12: return "Aventure"; case 16: return "Animation";
            case 35: return "Comédie"; case 80: return "Policier"; case 99: return "Documentaire";
            case 18: return "Drame"; case 10751: return "Famille"; case 14: return "Fantastique";
            case 36: return "Historique"; case 27: return "Horreur"; case 10402: return "Musique";
            case 9648: return "Mystère"; case 10749: return "Romance"; case 878: return "Science-fiction";
            case 10770: return "Téléfilm"; case 53: return "Thriller"; case 10752: return "Guerre";
            case 37: return "Western"; default: return null;
        }
    }
    private void loadTmdbDetails(TmdbMovie movie, int generation) {
        java.net.HttpURLConnection connection = null;
        try {
            String address = "https://api.themoviedb.org/3/movie/" + movie.id
                + "?api_key=" + java.net.URLEncoder.encode(tmdbKey(), "UTF-8")
                + "&language=fr-FR&append_to_response=images&include_image_language=fr,en,null";
            connection = (java.net.HttpURLConnection) new java.net.URL(address).openConnection();
            connection.setConnectTimeout(8000); connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() == 200) {
                try (java.io.InputStream input = connection.getInputStream();
                     java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count, total = 0;
                    while ((count = input.read(buffer)) != -1 && total < 1_000_000) {
                        output.write(buffer, 0, count); total += count;
                    }
                    org.json.JSONObject details = new org.json.JSONObject(output.toString("UTF-8"));
                    movie.runtimeMinutes = details.optInt("runtime", 0);
                    org.json.JSONArray genres = details.optJSONArray("genres");
                    if (genres != null && genres.length() > 0) {
                        movie.genres.clear();
                        for (int i = 0; i < genres.length(); i++) {
                            String name = genres.optJSONObject(i) == null ? "" : genres.optJSONObject(i).optString("name", "");
                            if (!name.isEmpty()) movie.genres.add(name);
                        }
                    }
                    org.json.JSONObject imageData = details.optJSONObject("images");
                    org.json.JSONArray logos = imageData == null ? null : imageData.optJSONArray("logos");
                    if (logos != null) {
                        org.json.JSONObject fallback = null, english = null, french = null;
                        for (int i = 0; i < logos.length(); i++) {
                            org.json.JSONObject logo = logos.optJSONObject(i);
                            if (logo == null || logo.optString("file_path", "").isEmpty()) continue;
                            String language = logo.optString("iso_639_1", "");
                            if ("fr".equals(language)) { french = logo; break; }
                            if ("en".equals(language)) english = logo;
                            if ("null".equals(language) || language.isEmpty()) fallback = logo;
                        }
                        org.json.JSONObject selected = french != null ? french : english != null ? english : fallback;
                        if (selected != null) movie.logoPath = selected.optString("file_path", "");
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
            handler.post(() -> {
                if (isDestroyed()) return;
                movie.detailsLoaded = true;
                if (tmdbMovies.contains(movie)) preloadTmdbArtwork(movie);
                if (showFilmHero && activeTmdbMovie == movie) applyFilmHero();
            });
        }
    }
    private Bitmap downloadTmdbBitmap(String imagePath, String size) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL("https://image.tmdb.org/t/p/" + size + imagePath).openConnection();
            connection.setConnectTimeout(8000); connection.setReadTimeout(12000);
            try (java.io.InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception ignored) {
            return null;
        } finally { if (connection != null) connection.disconnect(); }
    }
    private void preloadTmdbArtwork(TmdbMovie movie) {
        if (!movie.detailsLoaded || movie.artworkLoading || movie.artworkReady || movie.artworkFailed || !tmdbMovies.contains(movie)) return;
        movie.artworkLoading = true;
        images.execute(() -> {
            Bitmap banner = downloadTmdbBitmap(movie.backdropPath, "w1280");
            Bitmap logo = movie.logoPath.isEmpty() ? null : downloadTmdbBitmap(movie.logoPath, "w500");
            handler.post(() -> {
                if (isDestroyed()) return;
                movie.artworkLoading = false;
                movie.backdropBitmap = banner;
                movie.logoBitmap = logo;
                if (!movie.logoPath.isEmpty() && logo == null) movie.logoPath = "";
                movie.artworkFailed = banner == null;
                movie.artworkReady = banner != null && movie.detailsLoaded;
                int index = tmdbMovies.indexOf(movie);
                if (index >= 0 && pendingMovieIndex == index && !appsPage && generationStillCurrent()) {
                    if (movie.artworkReady) showNextTmdbBackdrop(tmdbGeneration);
                    else { pendingMovieIndex = -1; tmdbIndex = (index + 1) % tmdbMovies.size(); showNextTmdbBackdrop(tmdbGeneration); }
                }
            });
        });
    }
    private boolean generationStillCurrent() { return resumed && prefs.getString("backdrop", "").isEmpty(); }
    private void showGreeting() {
        handler.removeCallbacks(filmHeroTask);
        boolean wasFilm = showFilmHero;
        showFilmHero = false;
        Runnable setGreeting = () -> {
            heroTitle.setText(greeting());
            heroTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            heroTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(82));
            heroTitle.setShadowLayer(px(10), px(2), px(4), 0xaa000000);
            heroTitle.setVisibility(View.VISIBLE);
            heroSubtitle.setVisibility(View.GONE); heroLogo.setVisibility(View.GONE);
            heroTitle.setAlpha(0f);
            heroTitle.animate().alpha(1f).setDuration(duration(1500)).setInterpolator(new DecelerateInterpolator()).start();
        };
        heroTitle.animate().cancel(); heroLogo.animate().cancel(); heroSubtitle.animate().cancel();
        if (wasFilm) {
            heroTitle.animate().alpha(0f).setDuration(duration(1100)).withEndAction(setGreeting).start();
            heroLogo.animate().alpha(0f).setDuration(duration(1100)).start();
            heroSubtitle.animate().alpha(0f).setDuration(duration(1100)).start();
        } else setGreeting.run();
    }
    private void showNextTmdbBackdrop(int generation) {
        if (generation != tmdbGeneration || tmdbMovies.isEmpty() || !prefs.getString("backdrop", "").isEmpty()) return;
        for (int checked = 0; checked < tmdbMovies.size(); checked++) {
            int index = tmdbIndex % tmdbMovies.size();
            TmdbMovie movie = tmdbMovies.get(index);
            if (movie.artworkFailed) {
                tmdbIndex = (index + 1) % tmdbMovies.size();
                continue;
            }
            if (!movie.artworkReady) {
                pendingMovieIndex = index;
                if (movie.detailsLoaded) preloadTmdbArtwork(movie);
                return;
            }
            pendingMovieIndex = -1;
            tmdbIndex = (index + 1) % tmdbMovies.size();
            boolean firstFilm = activeTmdbMovie == null;
            activeTmdbMovie = movie;
            if (firstFilm) showGreeting();
            Drawable oldDrawable = currentTmdbBackdrop == null
                ? new ColorDrawable(Color.TRANSPARENT)
                : new BitmapDrawable(getResources(), currentTmdbBackdrop);
            TransitionDrawable transition = new TransitionDrawable(new Drawable[]{
                oldDrawable, new BitmapDrawable(getResources(), movie.backdropBitmap)
            });
            transition.setCrossFadeEnabled(true);
            backdrop.setImageDrawable(transition);
            backdrop.setAlpha(.8f);
            transition.startTransition(duration(2500));
            currentTmdbBackdrop = movie.backdropBitmap;
            handler.removeCallbacks(filmHeroTask);
            handler.postDelayed(filmHeroTask, firstFilm ? duration(2500) + 3000 : 100);
            return;
        }
        pendingMovieIndex = -1;
    }
    private void transitionFilmHero() {
        if (activeTmdbMovie == null || !activeTmdbMovie.artworkReady) return;
        heroTitle.animate().cancel(); heroLogo.animate().cancel(); heroSubtitle.animate().cancel();
        Runnable showNewTitle = () -> {
            if (!showFilmHero || activeTmdbMovie == null || isDestroyed() || appsPage) return;
            TmdbMovie movie = activeTmdbMovie;
            heroSubtitle.setText(movie.subtitle());
            heroSubtitle.setVisibility(View.VISIBLE); heroSubtitle.setAlpha(0f);
            heroTitle.setText(movie.title);
            heroTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            heroTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(86));
            heroTitle.setShadowLayer(px(12), px(3), px(5), 0xcc000000);
            heroTitle.setVisibility(View.VISIBLE); heroTitle.setAlpha(0f);
            heroLogo.setVisibility(View.GONE); heroLogo.setAlpha(0f);
            if (movie.logoBitmap != null) {
                heroLogo.setImageBitmap(movie.logoBitmap);
                heroLogo.setVisibility(View.VISIBLE);
                heroTitle.setVisibility(View.GONE);
            }
            heroTitle.animate().alpha(1f).setDuration(duration(1600)).setInterpolator(new DecelerateInterpolator()).start();
            heroLogo.animate().alpha(1f).setDuration(duration(1600)).setInterpolator(new DecelerateInterpolator()).start();
            heroSubtitle.animate().alpha(1f).setDuration(duration(1600)).setInterpolator(new DecelerateInterpolator()).start();
        };
        showFilmHero = true;
        heroTitle.animate().alpha(0f).setDuration(duration(1200)).setInterpolator(new DecelerateInterpolator()).withEndAction(showNewTitle).start();
        heroLogo.animate().alpha(0f).setDuration(duration(1200)).setInterpolator(new DecelerateInterpolator()).start();
        heroSubtitle.animate().alpha(0f).setDuration(duration(1200)).setInterpolator(new DecelerateInterpolator()).start();
    }
    private void applyFilmHero() {
        if (!showFilmHero || activeTmdbMovie == null || !activeTmdbMovie.artworkReady) return;
        heroSubtitle.setText(activeTmdbMovie.subtitle());
    }
    private void scheduleFilmHero() {
        handler.removeCallbacks(filmHeroTask);
        if (!showFilmHero && activeTmdbMovie != null && prefs.getString("backdrop", "").isEmpty())
            handler.postDelayed(filmHeroTask, 3000);
    }
    private void refreshTmdb() {
        if (!hasTmdbKey()) {
            configureTmdb();
            return;
        }
        prefs.edit().remove("backdrop").apply();
        tmdbMovies.clear(); tmdbIndex = 0; tmdbGeneration++;
        closeControls();
        startTmdbSlideshow();
    }
    private void reveal(Card card) {
        if(card==null)return;
        if(pendingReveal!=null)scroll.removeCallbacks(pendingReveal);
        pendingReveal=()->{
            if(!card.hasFocus()||panel!=null)return;
            boolean grid=gridCards.contains(card),entering=grid&&!appsPage;
            Rect bounds=new Rect();card.getDrawingRect(bounds);
            content.offsetDescendantRectToMyCoords(card,bounds);
            int margin=px(32),dockSpace=px(32),target=pageAnimator!=null&&pageAnimator.isRunning()?pageTarget:scroll.getScrollY();
            if(!grid)target=0;
            else if(entering||bounds.top-dockSpace<target)target=Math.max(0,bounds.top-dockSpace);
            else if(bounds.bottom+margin>target+scroll.getHeight())target=bounds.bottom+margin-scroll.getHeight();
            appsPage=grid;animatePage(Math.max(0,target),grid);
        };
        scroll.postOnAnimation(pendingReveal);
    }
    private void loadBackdrop(android.net.Uri uri) {
        final int generation=++imageGeneration;
        images.execute(()->{
            try {
                android.graphics.ImageDecoder.Source source=android.graphics.ImageDecoder.createSource(getContentResolver(),uri);
                Bitmap bitmap=android.graphics.ImageDecoder.decodeBitmap(source,(decoder,info,src)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                    float scale=Math.min(1f,1920f/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));
                });
                handler.post(()->{if(!isDestroyed()&&generation==imageGeneration){currentTmdbBackdrop=null;backdrop.setImageBitmap(bitmap);backdrop.setAlpha(.8f);}});
            } catch(Exception ignored) { }
        });
    }
    private void chooseBackdrop() {
        try {startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),42);}
        catch(android.content.ActivityNotFoundException ex){Toast.makeText(this,"Aucun sélecteur d’images installé",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==43&&result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}prefs.edit().putString("saver_video",uri.toString()).apply();return;}
        if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            android.net.Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            prefs.edit().putString("backdrop",uri.toString()).apply();tmdbGeneration++;pendingMovieIndex=-1;handler.removeCallbacks(tmdbSlide);showGreeting();loadBackdrop(uri);
        }
    }
    private boolean backHeld;
    private final Runnable holdBack=()->{backHeld=true;if(panel==null)showControls();};
    private boolean menuHeld;
    private long lastMenuUp;
    private final Runnable singleMenu=()->runRemoteAction(prefs.getInt("menu_single",0));
    private final Runnable holdMenu=()->{menuHeld=true;handler.removeCallbacks(singleMenu);lastMenuUp=0;runRemoteAction(prefs.getInt("menu_hold",1));};
    private final Runnable clockTick = new Runnable() {
        public void run() {
            if (!resumed) return;
            if (!showFilmHero) heroTitle.setText(greeting());
            time.setText(new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date()));
            handler.postDelayed(this, 10000);
        }
    };
    private static class App {
        String pkg, label; Intent launch; Drawable art; boolean banner;
    }
    private String greeting(){int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return (hour>=6&&hour<18?"Bonjour":"Bonsoir")+" Paco";}
    private void animatePage(int target,boolean grid){
        if(favoritesBar!=null)favoritesBar.setTranslationY(0);
        int max=Math.max(0,content.getHeight()-scroll.getHeight());
        target=Math.max(0,Math.min(target,max));
        if(animatedGrid==null||animatedGrid!=grid){
            animatedGrid=grid;heroTitle.animate().cancel();
            heroTitle.animate().alpha(grid?0:1).translationY(grid?-px(14):0).setDuration(duration(220)).start();
            backdrop.animate().cancel();backdrop.animate().alpha(grid?0f:.8f).setDuration(duration(650)).start();
            if (heroLogo != null) heroLogo.animate().alpha(grid?0:1).setDuration(duration(220)).start();
            if (heroSubtitle != null) heroSubtitle.animate().alpha(grid?0:1).setDuration(duration(220)).start();
            if (!grid) scheduleFilmHero();
        }
        if(pageAnimator!=null&&pageAnimator.isRunning()&&target==pageTarget)return;
        if(pageAnimator!=null)pageAnimator.cancel();
        pageTarget=target;
        if(reduced||Math.abs(scroll.getScrollY()-target)<2){scroll.scrollTo(0,target);return;}
        pageAnimator=android.animation.ValueAnimator.ofInt(scroll.getScrollY(),target);
        pageAnimator.setDuration(320);
        pageAnimator.setInterpolator(new android.view.animation.PathInterpolator(.25f,.1f,.25f,1f));
        pageAnimator.addUpdateListener(animation->scroll.scrollTo(0,(Integer)animation.getAnimatedValue()));
        pageAnimator.start();
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
        updates=new Updates(this);
        prefs=getSharedPreferences("appearance",MODE_PRIVATE);
        reduced=prefs.getBoolean("reduced",false); palette=prefs.getInt("palette",0);
        unit=getResources().getDisplayMetrics().widthPixels/1920f;
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root=new FrameLayout(this); setContentView(root);aerial=new AerialPlayer(this,root);
        wallpaper=new Wallpaper(); wallpaper.setScaleX(1.06f);wallpaper.setScaleY(1.06f);root.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        View veil=new View(this);veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x33000000,0x11000000,0xee060914}));root.addView(veil,new FrameLayout.LayoutParams(-1,-1));
        heroShade=new View(this) {
            final Paint shadePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
                if(w==0||h==0)return;
                Shader horizontal=new LinearGradient(0,0,w,0,new int[]{0x00000000,0x50000000,0x35000000,0x00000000},new float[]{0,.08f,.5f,1},Shader.TileMode.CLAMP);
                Shader vertical=new LinearGradient(0,0,0,h,new int[]{0x00ffffff,0xffffffff,0x70ffffff,0x00ffffff},new float[]{0,.12f,.6f,1},Shader.TileMode.CLAMP);
                shadePaint.setShader(new ComposeShader(horizontal,vertical,PorterDuff.Mode.DST_IN));
            }
            @Override protected void onDraw(Canvas canvas) { canvas.drawRect(0,0,getWidth(),getHeight(),shadePaint); }
        };
        root.addView(heroShade,frame(1500,300,Gravity.TOP,40,10));
        String saved=prefs.getString("backdrop","");if(!saved.isEmpty())loadBackdrop(android.net.Uri.parse(saved));
        buildHeader();
        scroll=new ScrollView(this){
            @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(favoritesBar!=null)favoritesBar.setTranslationY(0);}
            @Override public void requestChildFocus(View child,View focused){int y=getScrollY();super.requestChildFocus(child,focused);scrollTo(0,y);}
            @Override public boolean executeKeyEvent(KeyEvent event){return false;}
            @Override public boolean requestChildRectangleOnScreen(View child,Rect rectangle,boolean immediate){return false;}
        }; scroll.setClipChildren(true); scroll.setClipToPadding(true);
        scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false); scroll.setSmoothScrollingEnabled(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false); content.setClipToPadding(false); content.setPadding(px(110),px(575),px(110),px(100));
        scroll.addView(content); FrameLayout.LayoutParams sp=frame(-1,-1,Gravity.TOP,0,145);sp.bottomMargin=px(70);root.addView(scroll,sp);

        developerBadge=text("v"+appVersion(),18,0xbfffffff);developerBadge.setPadding(px(12),px(6),px(12),px(6));
        developerBadge.setBackground(glass(0x66000000,0x44000000,10,0x11ffffff));developerBadge.setVisibility(prefs.getBoolean("developer",false)?View.VISIBLE:View.GONE);
        FrameLayout.LayoutParams badge=frame(-2,-2,Gravity.BOTTOM|Gravity.RIGHT,0,0);badge.rightMargin=px(32);badge.bottomMargin=px(22);root.addView(developerBadge,badge);
    }
    private void buildHeader() {
        TextView brand=text("GlassTV",28,0xbbffffff);
        brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        time=text("",30,0xddffffff);time.setGravity(Gravity.RIGHT);
        root.addView(time,frame(145,45,Gravity.TOP,1510,60));
        nowPlaying=new NowPlaying(this);
        LinearLayout button=new LinearLayout(this);button.setGravity(Gravity.CENTER);button.setPadding(px(24),0,px(24),0);
        TextView icon=text("☷",38,Color.WHITE);button.addView(icon);
        NowPlaying.Bars bars=nowPlaying.indicator();button.addView(bars,new LinearLayout.LayoutParams(px(38),px(32)));
        TextView song=text("",25,Color.WHITE);song.setSingleLine(true);song.setMaxWidth(px(300));song.setEllipsize(android.text.TextUtils.TruncateAt.END);song.setPadding(px(12),0,0,0);button.addView(song);
        button.setContentDescription("Centre de contrôle et musique");button.setBackground(glass(0x88000000,0x66333333,33,0x55ffffff));button.setFocusable(true);button.setId(View.generateViewId());
        button.setOnClickListener(v->showControls());
        button.setOnFocusChangeListener((v,focus)->{v.setPivotX(v.getWidth());v.animate().scaleX(focus?1.08f:1).scaleY(focus?1.12f:1).setDuration(duration(280)).setInterpolator(new OvershootInterpolator(.6f)).start();});
        button.setOnHoverListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_HOVER_ENTER)v.requestFocus();return false;});
        headerButton=button;FrameLayout.LayoutParams hp=frame(-2,66,Gravity.TOP|Gravity.RIGHT,0,48);hp.rightMargin=px(119);root.addView(button,hp);
        time.setVisibility(View.GONE);
        nowPlaying.attach(button,song,bars);
        heroTitle=text(greeting(),82,Color.WHITE);heroTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        heroTitle.setShadowLayer(px(10),px(2),px(4),0xaa000000);
        root.addView(heroTitle,frame(1500,120,Gravity.TOP,112,65));
        heroLogo = new ImageView(this); heroLogo.setScaleType(ImageView.ScaleType.FIT_START); heroLogo.setVisibility(View.GONE);
        root.addView(heroLogo,frame(1300,220,Gravity.TOP,112,28));
        heroSubtitle=text("",27,0xddffffff); heroSubtitle.setShadowLayer(px(8),px(2),px(3),0xaa000000); heroSubtitle.setVisibility(View.GONE);
        root.addView(heroSubtitle,frame(1500,44,Gravity.TOP,112,280));
    }
    @Override protected void onResume() {
        super.onResume(); if(nowPlaying!=null)nowPlaying.resume(); updates.resume();resumed=true; root.setAlpha(1f);root.setScaleX(1);root.setScaleY(1);startTmdbSlideshow();
        if(panel!=null)closeControls();
        refreshApps(); handler.removeCallbacks(clockTick);clockTick.run();resetIdle();
    }
    @Override protected void onPause() { super.onPause();if(nowPlaying!=null)nowPlaying.pause();if(phoneSetup!=null)phoneSetup.close();aerial.stop();handler.removeCallbacks(tmdbSlide);handler.removeCallbacks(idleSaver);if(pageAnimator!=null)pageAnimator.cancel();updates.pause();resumed=false;handler.removeCallbacks(clockTick);handler.removeCallbacks(holdBack);handler.removeCallbacks(singleMenu);handler.removeCallbacks(holdMenu);lastMenuUp=0;root.animate().cancel(); }
    @Override protected void onDestroy() { if(pageAnimator!=null)pageAnimator.cancel();tmdbRequests.shutdownNow();images.shutdownNow();updates.close();handler.removeCallbacksAndMessages(null);super.onDestroy(); }
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
        dockCards.clear();gridCards.clear();
        ArrayList<App> favoritesList=new ArrayList<>();
        for(App app:apps)if(favorites.contains(app.pkg))favoritesList.add(app);
        // Five fixed slots, preserving the previous choices and filling unused slots.
        favoritesList.clear();
        String order=prefs.getString("favorite_slots","");
        for(String pkg:order.split("\\|"))for(App app:apps)if(app.pkg.equals(pkg)&&!favoritesList.contains(app)&&favoritesList.size()<5)favoritesList.add(app);
        for(App app:apps)if(favorites.contains(app.pkg)&&!favoritesList.contains(app)&&favoritesList.size()<5)favoritesList.add(app);
        for(App app:apps)if(!favoritesList.contains(app)&&favoritesList.size()<5)favoritesList.add(app);
        ArrayList<String> slotIds=new ArrayList<>();for(App app:favoritesList)slotIds.add(app.pkg);
        prefs.edit().putString("favorite_slots",android.text.TextUtils.join("|",slotIds)).putStringSet("favorites",new HashSet<>(slotIds)).apply();
        HorizontalScrollView favoritesScroll=new HorizontalScrollView(this);
        favoritesScroll.setHorizontalScrollBarEnabled(false);favoritesScroll.setClipToPadding(false);
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);dock.setPadding(px(30),px(25),px(30),px(15));
        dock.setClipChildren(false);dock.setClipToPadding(false);dock.setBackground(glass(0x32ffffff,0x40151515,40,0x49ffffff));
        favoritesScroll.setFillViewport(true);favoritesScroll.addView(dock);favoritesBar=favoritesScroll;favoritesBar.setElevation(px(18));
        content.addView(favoritesScroll,new LinearLayout.LayoutParams(-1,px(255)));
        for(int i=0;i<favoritesList.size();i++){addCard(dock,favoritesList.get(i),i,true);dockCards.add(cards.get(cards.size()-1));}
        for(int i=favoritesList.size();i<5;i++){
            TextView slot=text("+",44,0x88ffffff);slot.setGravity(Gravity.CENTER);slot.setBackground(glass(0x18ffffff,0x08ffffff,24,0x22ffffff));
            LinearLayout.LayoutParams emptySlot=new LinearLayout.LayoutParams(px(292),px(165));emptySlot.rightMargin=px(32);dock.addView(slot,emptySlot);
        }
        if(favoritesList.isEmpty()) {
            TextView empty=text("Vos favoris apparaîtront ici. ↓ Applications",28,Color.WHITE);
            empty.setFocusable(true);empty.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_DOWN&&e.getAction()==KeyEvent.ACTION_DOWN&&!gridCards.isEmpty()){gridCards.get(0).requestFocus();return true;}return false;});
            dock.addView(empty,new LinearLayout.LayoutParams(px(1500),px(180)));
        }
        TextView hint=text("↓  Toutes les applications",23,0xccffffff);hint.setGravity(Gravity.CENTER);
        content.addView(hint,new LinearLayout.LayoutParams(-1,px(90)));
        TextView title=text("Applications",40,Color.WHITE);content.addView(title,new LinearLayout.LayoutParams(-1,px(85)));
        for(int i=0;i<apps.size();i+=5) {
            LinearLayout row=new LinearLayout(this);row.setPadding(px(28),px(25),px(28),px(15));row.setClipChildren(false);row.setClipToPadding(false);
            content.addView(row,new LinearLayout.LayoutParams(-1,px(265)));
            for(int j=i;j<Math.min(i+5,apps.size());j++){addCard(row,apps.get(j),j,false);gridCards.add(cards.get(cards.size()-1));}
        }
        for(int i=0;i<dockCards.size();i++){
            Card c=dockCards.get(i);c.setNextFocusLeftId(dockCards.get(Math.max(0,i-1)).getId());c.setNextFocusRightId(dockCards.get(Math.min(i+1,dockCards.size()-1)).getId());
            c.setNextFocusUpId(headerButton.getId());c.setNextFocusDownId(gridCards.get(Math.min(i,gridCards.size()-1)).getId());
        }
        for(int i=0;i<gridCards.size();i++){
            Card c=gridCards.get(i);c.setNextFocusLeftId(gridCards.get(i%5==0?i:i-1).getId());
            c.setNextFocusRightId(gridCards.get(i%5==4||i==gridCards.size()-1?i:i+1).getId());
            c.setNextFocusUpId(i>=5?gridCards.get(i-5).getId():!dockCards.isEmpty()?dockCards.get(Math.min(i,dockCards.size()-1)).getId():headerButton.getId());
            c.setNextFocusDownId(gridCards.get(i/5<(gridCards.size()-1)/5?Math.min(i+5,gridCards.size()-1):i).getId());
        }
        rebuilding=false;
        Card restore=cards.get(0);if(appsPage)for(Card c:gridCards)if(c.app.pkg.equals(selected))restore=c;
        final Card focus=restore;scroll.post(()->{if(panel==null)focus.requestFocusFromTouch();});
    }
    private void addCard(LinearLayout row,App app,int index,boolean dock) {
        Card c=new Card(app,index,dock);cards.add(c);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(292),px(215));p.rightMargin=px(32);row.addView(c,p);
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
                tile.animate().scaleX(focused?1.09f:1).scaleY(focused?1.09f:1).translationZ(px(focused?26:0)).setDuration(duration(260)).setInterpolator(new DecelerateInterpolator()).start();
                art.animate().scaleX(focused?1.035f:1).scaleY(focused?1.035f:1).setDuration(duration(330)).start();
                shine.animate().alpha(focused?1:.15f).setDuration(duration(220)).start();label.animate().alpha(focused?1:.58f).setDuration(duration(180)).start();
                if(focused) {reveal(this);
                    if(dock)post(()->{Rect safe=new Rect();getDrawingRect(safe);safe.inset(-px(22),-px(20));requestRectangleOnScreen(safe,false);});
                    lastFocus=this;if(!showFilmHero)heroTitle.setText(greeting());
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
        String[] labels=new String[5];
        for(int i=0;i<5;i++)labels[i]="Emplacement "+(i+1)+(i<dockCards.size()?" · "+dockCards.get(i).app.label:"");
        new AlertDialog.Builder(this).setTitle("Placer "+app.label+" dans les favoris")
            .setItems(labels,(dialog,slot)->{
                ArrayList<String> ids=new ArrayList<>(Arrays.asList(prefs.getString("favorite_slots","").split("\\|")));
                int previous=ids.indexOf(app.pkg);
                if(previous>=0&&slot<ids.size())Collections.swap(ids,previous,slot);
                else if(slot<ids.size())ids.set(slot,app.pkg);
                else ids.add(app.pkg);
                prefs.edit().putString("favorite_slots",android.text.TextUtils.join("|",ids)).putStringSet("favorites",new HashSet<>(ids)).apply();
                refreshApps();
            }).setNegativeButton("Annuler",null).show();
    }
    private void showControls() {
        if(panel!=null)return;
        View focused=getCurrentFocus();if(focused!=null)lastFocus=focused;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);headerButton.setFocusable(false);panelButtons.clear();
        panel=new FrameLayout(this);panel.setBackgroundColor(0x66040815);root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(px(40),px(36),px(40),px(30));sheet.setClipChildren(false);sheet.setClipToPadding(false);
        FrameLayout.LayoutParams p=frame(560,-2,Gravity.RIGHT|Gravity.TOP,0,65);p.rightMargin=px(70);p.height=getResources().getDisplayMetrics().heightPixels-px(130);
        panelScroll=new ScrollView(this);panelScroll.setBackground(glass(0xe3303030,0xf0101010,42,0x88ffffff));panelScroll.setClipToOutline(true);panelScroll.setPadding(px(16),px(16),px(16),px(16));panelScroll.setClipToPadding(false);panelScroll.setVerticalScrollBarEnabled(false);panelScroll.addView(sheet);panel.addView(panelScroll,p);
        TextView heading=text("Centre de contrôle",36,Color.WHITE);sheet.addView(heading,new LinearLayout.LayoutParams(-1,px(75)));
        nowPlaying.addCard(sheet);
        if(nowPlaying.available()){
            control(sheet,"⏮  Précédent",()->nowPlaying.command(-1));
            nowPlaying.bindPlay(control(sheet,"Ⅱ  Pause",()->nowPlaying.command(0)));
            control(sheet,"⏭  Suivant",()->nowPlaying.command(1));
        }
        control(sheet,"Autoriser les infos de lecture",()->nowPlaying.permission());
        TextView first=control(sheet,"Réglages Android TV",()->{try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Réglages indisponibles",Toast.LENGTH_SHORT).show();}});
        control(sheet,"Ambiance  ·  "+new String[]{"Crépuscule","Océan","Minuit"}[palette],()->new AlertDialog.Builder(this).setTitle("Ambiance").setSingleChoiceItems(new String[]{"Crépuscule","Océan","Minuit"},palette,(d,which)->{palette=which;prefs.edit().putInt("palette",which).apply();wallpaper.reset();d.dismiss();closeControls();}).show());
        control(sheet,"Animations  ·  "+(reduced?"Réduites":"Fluides"),()->{reduced=!reduced;prefs.edit().putBoolean("reduced",reduced).apply();closeControls();});
        control(sheet,"Définir comme accueil",this::requestHome);
        control(sheet,"Télécommande",this::remoteSettings);
        control(sheet,"Mises à jour auto · "+(updates.enabled()?"Activées":"Désactivées"),()->{updates.toggle();closeControls();});
        control(sheet,"Rechercher une mise à jour",()->{closeControls();updates.check(true);});
        control(sheet,"Mode développeur · "+(prefs.getBoolean("developer",false)?"Activé":"Désactivé"),()->{boolean enabled=!prefs.getBoolean("developer",false);prefs.edit().putBoolean("developer",enabled).apply();developerBadge.setVisibility(enabled?View.VISIBLE:View.GONE);closeControls();});
        if(prefs.getBoolean("developer",false))control(sheet,"Tester l’économiseur",this::startSaver);
        control(sheet,"Économiseur auto · "+(prefs.getBoolean("saver_auto",true)?"3 minutes":"Désactivé"),()->{prefs.edit().putBoolean("saver_auto",!prefs.getBoolean("saver_auto",true)).apply();closeControls();resetIdle();});
        control(sheet,"Choisir une vidéo locale",this::chooseSaverVideo);
        control(sheet,"Source aérienne par défaut",()->{prefs.edit().remove("saver_video").apply();closeControls();});
        control(sheet,"Configurer TMDB · Téléphone",this::configureTmdb);
        control(sheet,"Bannières cinéma · Actualiser",this::refreshTmdb);
        control(sheet,"À propos des données cinéma",()->new AlertDialog.Builder(this).setTitle("Données cinéma · TMDB").setMessage("This product uses the TMDB API but is not endorsed or certified by TMDB. Les bannières sont fournies par TMDB et s’actualisent depuis les sorties en salle et prochaines sorties en France.").setPositiveButton("Compris",null).show());
        control(sheet,"Choisir la bannière",this::chooseBackdrop);
        control(sheet,"Actualiser les applications",()->{closeControls();refreshApps();});
        TextView close=control(sheet,"Fermer",this::closeControls);first.setNextFocusUpId(first.getId());close.setNextFocusDownId(close.getId());
        TextView version=text("GlassTV "+appVersion()+"  •  Interface locale",19,0x88ffffff);version.setPadding(0,px(25),0,0);sheet.addView(version);
        sheet.setTranslationX(px(90));sheet.setAlpha(0);sheet.animate().translationX(0).alpha(1).setDuration(duration(280)).setInterpolator(new DecelerateInterpolator()).start();
        for(int i=0;i<panelButtons.size();i++){TextView v=panelButtons.get(i);v.setNextFocusUpId(panelButtons.get(Math.max(0,i-1)).getId());v.setNextFocusDownId(panelButtons.get(Math.min(panelButtons.size()-1,i+1)).getId());}
        panelButtons.get(0).requestFocus();
    }
    private TextView control(LinearLayout parent,String title,Runnable action) {
        TextView v=text(title,27,Color.WHITE);v.setId(View.generateViewId());v.setNextFocusLeftId(v.getId());v.setNextFocusRightId(v.getId());v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(px(24),0,px(18),0);v.setFocusable(true);v.setClickable(true);
        v.setBackground(glass(0x30ffffff,0x12ffffff,23,0x3affffff));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(78));p.bottomMargin=px(13);parent.addView(v,p);
        panelButtons.add(v);v.setOnFocusChangeListener((view,focus)->{if(focus&&panelScroll!=null)v.post(()->{Rect r=new Rect();v.getDrawingRect(r);r.inset(-px(12),-px(14));v.requestRectangleOnScreen(r,false);});v.setTextColor(focus?0xff151520:Color.WHITE);v.setBackground(glass(focus?0xfff6faff:0x30ffffff,focus?0xffdddddd:0x12ffffff,23,0x77ffffff));v.animate().scaleX(focus?1.025f:1).scaleY(focus?1.025f:1).setDuration(duration(160)).start();});v.setOnClickListener(view->action.run());return v;
    }
    private void closeControls(){if(panel==null)return;root.removeView(panel);panel=null;scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);headerButton.setFocusable(true);if(lastFocus!=null)lastFocus.requestFocus();}
    @Override public void onBackPressed(){if(panel!=null)closeControls();else if(!cards.isEmpty()){appsPage=false;animatePage(0,false);cards.get(0).requestFocus();}}
    private void requestHome() {
        try {
            if(android.os.Build.VERSION.SDK_INT>=29) {
                android.app.role.RoleManager roles=getSystemService(android.app.role.RoleManager.class);
                if(roles!=null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    if(roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        new AlertDialog.Builder(this).setTitle("Accueil enregistré")
                            .setMessage("Android a bien enregistré GlassTV comme accueil. Si la touche Maison ouvre encore Android TV, le firmware ou le comportement de la télécommande impose ce lancement. Un remappage global de Maison peut être nécessaire.")
                            .setPositiveButton("Compris",null).show();return;
                    }
                    startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),41);return;
                }
            }
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch(android.content.ActivityNotFoundException | SecurityException ex) {
            new AlertDialog.Builder(this).setTitle("Bouton Maison")
                .setMessage("Ce firmware ne propose pas le choix de l’accueil ici. Un remappage du bouton Maison ou une configuration ADB peut être nécessaire. Gardez l’accueil système actif tant que GlassTV n’est pas configuré.")
                .setPositiveButton("Compris",null).show();
        }
    }
    private void remoteSettings() {
        final String[] keys={"menu_single","menu_double","menu_hold"};
        final int[] defaults={0,3,1};
        final String[] actions={"Centre de contrôle","Réglages Android TV","Retour en haut","Changer l’ambiance","Actualiser les applications"};
        new AlertDialog.Builder(this).setTitle("Touche Menu dans GlassTV")
            .setItems(new String[]{"Appui simple","Double appui","Appui long"},(dialog,index)->
                new AlertDialog.Builder(this).setTitle("Choisir une action")
                    .setSingleChoiceItems(actions,prefs.getInt(keys[index],defaults[index]),(picker,action)->{
                        prefs.edit().putInt(keys[index],action).apply();picker.dismiss();
                    }).setNegativeButton("Annuler",null).show())
            .setNegativeButton("Fermer",null).show();
    }
    private void runRemoteAction(int action) {
        if(!resumed)return;
        switch(action) {
            case 0: if(panel==null)showControls();else closeControls();break;
            case 1: try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Réglages indisponibles",Toast.LENGTH_SHORT).show();}break;
            case 2: closeControls();onBackPressed();break;
            case 3: palette=(palette+1)%3;prefs.edit().putInt("palette",palette).apply();wallpaper.reset();break;
            case 4: closeControls();refreshApps();break;
            default: break;
        }
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(aerial!=null && aerial.touch(event))return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(aerial.key(event)){resetIdle();return true;}
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_BACK) {
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){backHeld=false;handler.postDelayed(holdBack,650);}
            else if(event.getAction()==KeyEvent.ACTION_UP){handler.removeCallbacks(holdBack);if(!backHeld&&!event.isCanceled())onBackPressed();}
            return true;
        }
        if(panel!=null&&(key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                int i=panelButtons.indexOf(getCurrentFocus());if(i<0)i=0;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN)i=Math.min(i+1,panelButtons.size()-1);
                if(key==KeyEvent.KEYCODE_DPAD_UP)i=Math.max(i-1,0);
                panelButtons.get(i).requestFocus();
            }return true;
        }
        if(key!=KeyEvent.KEYCODE_MENU)return super.dispatchKeyEvent(event);
        if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
            menuHeld=false;handler.postDelayed(holdMenu,650);
        } else if(event.getAction()==KeyEvent.ACTION_UP) {
            handler.removeCallbacks(holdMenu);
            if(event.isCanceled()){handler.removeCallbacks(singleMenu);lastMenuUp=0;return true;}
            if(!menuHeld) {
                long now=android.os.SystemClock.uptimeMillis();
                if(lastMenuUp!=0 && now-lastMenuUp<320) {
                    handler.removeCallbacks(singleMenu);lastMenuUp=0;runRemoteAction(prefs.getInt("menu_double",3));
                } else {lastMenuUp=now;handler.postDelayed(singleMenu,320);}
            }
        }
        return true;
    }
    private class Wallpaper extends View {
        final Paint paint=new Paint(3);final Path wave=new Path();Shader base,glow,shade;
        Wallpaper(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void reset(){makeShaders(getWidth(),getHeight());invalidate();}
        void makeShaders(int w,int h){if(w==0||h==0)return;int[][] colors={{0xff090909,0xff202020,0xff383838},{0xff111110,0xff292824,0xff48443c},{0xff030303,0xff101010,0xff202020}};int[] a=colors[palette];base=new LinearGradient(0,0,w,h,a,null,Shader.TileMode.CLAMP);glow=new RadialGradient(w*.73f,h*.22f,w*.65f,new int[]{0x304a4a4a,0x00404040},null,Shader.TileMode.CLAMP);shade=new LinearGradient(0,0,0,h,new int[]{0x11000000,0x00000000,0xaa050914},new float[]{0,.35f,1},Shader.TileMode.CLAMP);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){makeShaders(w,h);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();paint.setShader(base);c.drawRect(0,0,w,h,paint);paint.setShader(glow);c.drawRect(0,0,w,h,paint);paint.setShader(null);
            for(int i=0;i<3;i++){wave.reset();wave.moveTo(-w*.1f,h*(.7f+i*.1f));wave.cubicTo(w*.25f,h*(.2f+i*.12f),w*.65f,h*(1.1f-i*.1f),w*1.1f,h*(.25f+i*.18f));wave.lineTo(w*1.1f,h*1.2f);wave.lineTo(-w*.1f,h*1.2f);wave.close();paint.setColor(new int[]{0x18181818,0x20202020,0x44080808}[i]);c.drawPath(wave,paint);}
            paint.setShader(shade);c.drawRect(0,0,w,h,paint);paint.setShader(null);
        }
    }
}


