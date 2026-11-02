package com.glasstv.launcher;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import org.json.*;
import java.net.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;

final class Updates {
 private final Activity a;
 private final SharedPreferences p;
 private final DownloadManager dm;
 private final Handler h=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile boolean busy,active,closed;
 private boolean prompted;
 Updates(Activity activity){a=activity;p=a.getSharedPreferences("updates",0);dm=(DownloadManager)a.getSystemService(Context.DOWNLOAD_SERVICE);}
 boolean enabled(){return p.getBoolean("auto",true);}
 void toggle(){p.edit().putBoolean("auto",!enabled()).apply();}
 void pause(){active=false;}
 void close(){closed=true;active=false;h.removeCallbacksAndMessages(null);worker.shutdownNow();}
 void resume(){active=true;prompted=false;clearInstalled();ready();check(false);}
 private void message(String text){if(active)Toast.makeText(a,text,Toast.LENGTH_LONG).show();}
 private String current(){try{return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}catch(Exception e){return "0.0.0";}}
 private static int version(String tag){try{String[] n=tag.replaceFirst("^v","").split("\\.");return Integer.parseInt(n[0])*1000000+Integer.parseInt(n[1])*1000+Integer.parseInt(n[2]);}catch(Exception e){return -1;}}
 private void clearInstalled(){if(version(current())>=p.getInt("target",Integer.MAX_VALUE)){long id=p.getLong("download",-1);if(id>=0)dm.remove(id);p.edit().remove("download").remove("target").apply();}}
 private static String get(String address)throws Exception{
  URL url=new URL(address);if(!"https".equals(url.getProtocol()))throw new IOException("HTTPS required");
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","GlassTV");
  try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2000000)throw new IOException("Too large");}return out.toString("UTF-8");}finally{c.disconnect();}
 }
 void check(boolean manual){
  if(closed||busy)return;
  if(p.getLong("download",-1)>=0){ready();if(manual)message("Vérification du téléchargement");return;}
  if(!manual&&(!enabled()||System.currentTimeMillis()-p.getLong("checked",0)<21600000))return;
  busy=true;
  worker.execute(()->{
   try{
    JSONArray releases=new JSONArray(get("https://api.github.com/repos/pacormn/GlassTv/releases?per_page=20"));
    JSONObject chosen=null;int best=version(current());
    for(int i=0;i<releases.length();i++){JSONObject r=releases.getJSONObject(i);int v=version(r.optString("tag_name"));if(!r.optBoolean("draft")&&v>best){chosen=r;best=v;}}
    p.edit().putLong("checked",System.currentTimeMillis()).apply();
    if(chosen==null){if(manual)h.post(()->message("GlassTV est à jour"));return;}
    String apk="",sums="",name="";JSONArray assets=chosen.getJSONArray("assets");
    for(int i=0;i<assets.length();i++){JSONObject item=assets.getJSONObject(i);String n=item.getString("name");if(n.endsWith(".apk")){apk=item.getString("browser_download_url");name=n;}if(n.equals("SHA256SUMS.txt"))sums=item.getString("browser_download_url");}
    String prefix="https://github.com/pacormn/GlassTv/releases/download/";
    if(!apk.startsWith(prefix)||!sums.startsWith(prefix))throw new IOException("Invalid source");
    String hash="";for(String line:get(sums).split("\n")){String[] parts=line.trim().split("\\s+");if(parts.length==2&&parts[1].replaceFirst("^\\*","").equals(name))hash=parts[0];}
    if(!hash.matches("[a-fA-F0-9]{64}"))throw new IOException("Missing checksum");
    if(closed)return;
    long id=dm.enqueue(new DownloadManager.Request(Uri.parse(apk)).setTitle("Mise à jour GlassTV").setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalFilesDir(a,Environment.DIRECTORY_DOWNLOADS,"update-"+System.currentTimeMillis()+".apk"));
    p.edit().putLong("download",id).putInt("target",best).putString("sha",hash).apply();
    h.post(()->message("Mise à jour en téléchargement"));
   }catch(Exception e){if(manual)h.post(()->message("Recherche impossible. Réessayez plus tard."));}
   finally{busy=false;if(!closed)h.postDelayed(this::ready,4000);}
  });
 }
 private void ready(){
  if(!active||closed||busy||prompted)return;
  long id=p.getLong("download",-1);if(id<0)return;
  try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(id))){
   if(c==null||!c.moveToFirst()){p.edit().remove("download").apply();return;}
   int status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
   if(status==DownloadManager.STATUS_FAILED){dm.remove(id);p.edit().remove("download").apply();message("Téléchargement échoué. Relancez la recherche.");return;}
   if(status!=DownloadManager.STATUS_SUCCESSFUL){h.postDelayed(this::ready,4000);return;}
  }catch(RuntimeException e){return;}
  busy=true;
  worker.execute(()->{
   try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(dm.openDownloadedFile(id))){
    MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);
    StringBuilder hex=new StringBuilder();for(byte v:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
    if(!hex.toString().equalsIgnoreCase(p.getString("sha","")))throw new IOException("Invalid checksum");
    h.post(()->{if(!active||closed)return;prompted=true;new AlertDialog.Builder(a).setTitle("Mise à jour prête").setMessage("Confirmer l’installation avec Android ? Une différence de signature de test peut empêcher la mise à jour.").setPositiveButton("Installer",(d,w)->install(id)).setNegativeButton("Plus tard",null).show();});
   }catch(Exception e){dm.remove(id);p.edit().remove("download").apply();h.post(()->message("Fichier invalide : téléchargement supprimé."));}
   finally{busy=false;}
  });
 }
 private void install(long id){
  prompted=false;
  try{
   if(!a.getPackageManager().canRequestPackageInstalls()){a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));return;}
   Uri uri=dm.getUriForDownloadedFile(id);if(uri!=null)a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
  }catch(ActivityNotFoundException|SecurityException e){message("Installateur indisponible. Utilisez la Release GitHub.");}
 }
}
