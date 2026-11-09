package com.glasstv.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import io.github.jqssun.airplay.bridge.NativeBridge;

/** Audio-only AirPlay receiver. All lifecycle and JNI operations run on the main thread. */
public final class AirPlayService extends Service {
    private static volatile AirPlayService instance;
    private static volatile String status = "Désactivé";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private final List<NsdManager.RegistrationListener> registrations = new ArrayList<>();
    private NativeBridge bridge;
    private long handle;
    private MediaSession session;
    private NsdManager nsd;
    private WifiManager.MulticastLock multicast;
    private PowerManager.WakeLock wake;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean audioActive, destroyed, playing;
    private int connections, registered;
    private String title = "AirPlay", artist = "", album = "";
    private Bitmap artwork;
    private long position, duration;
    private float clientVolume;
    private InetAddress remoteHost;
    private int remotePort;
    private String remoteToken = "";
    private int remoteGeneration;
    public static String status() { return status; }
    public static MediaController controller() {
        AirPlayService service = instance;
        return service != null && service.audioActive && service.session != null
            ? service.session.getController() : null;
    }
    public static boolean enabled(Context context) {
        return context.getSharedPreferences("appearance",0).getBoolean("airplay_enabled",false);
    }
    public static void toggle(Context context) {
        boolean enable = !enabled(context);
        context.getSharedPreferences("appearance",0).edit().putBoolean("airplay_enabled",enable).apply();
        if (enable) context.startForegroundService(new Intent(context,AirPlayService.class));
        else context.stopService(new Intent(context,AirPlayService.class));
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("airplay","Réception AirPlay",NotificationManager.IMPORTANCE_LOW));
        startForeground(71, notification("Démarrage du récepteur…"));
        main.post(this::startReceiver);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) { return START_NOT_STICKY; }
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"airplay").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("GlassTV · AirPlay").setContentText(text).setContentIntent(open).setOngoing(true).build();
    }
    private void setStatus(String text) {
        status = text;
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager.class).notify(71,notification(text));
        }
        android.util.Log.i("GLASSTV_AIRPLAY", text);
    }
    private void fail(String text) {
        setStatus(text);
        Toast.makeText(this,text,Toast.LENGTH_LONG).show();
        getSharedPreferences("appearance",0).edit().putBoolean("airplay_enabled",false).apply();
        stopSelf();
    }
    private void startReceiver() {
        if (destroyed) return;
        try {
            bridge = new NativeBridge();
            audio = getSystemService(AudioManager.class);
            nsd = getSystemService(NsdManager.class);
            WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            multicast = wifi.createMulticastLock("GlassTV-AirPlay");
            multicast.setReferenceCounted(false);
            multicast.acquire();
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"glasstv:airplay");
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (handle == 0) return;
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        bridge.nativeServerAudioSetClientGain(handle,0f);
                        sendCommand("pause");
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        bridge.nativeServerAudioSetClientGain(handle,(float)Math.pow(10,clientVolume/20));
                    }
                },main).build();
            String identity = getSharedPreferences("airplay",0).getString("identity",null);
            if (identity == null) {
                byte[] bytes = new byte[6]; new java.security.SecureRandom().nextBytes(bytes); bytes[0] = 2;
                identity = android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);
                getSharedPreferences("airplay",0).edit().putString("identity",identity).apply();
            }
            bridge.nativeSetDefaultStreamValues(property(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),property(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
            handle = bridge.nativeInit(this,android.util.Base64.decode(identity,0),"GlassTV",new File(getFilesDir(),"airplay.pem").getAbsolutePath(),false,true);
            if (handle == 0) throw new IOException("initialisation native");
            bridge.nativeServerAudioConfigure(handle,0,95,0,false,false,true,true,false);
            bridge.nativeSetFeature(handle,7,false);
            bridge.nativeSetFeature(handle,9,true);
            bridge.nativeSetHlsEnabled(handle,false);
            bridge.nativeSetH265Enabled(handle,false);
            bridge.nativeSetCodecs(handle,true,true);
            session = new MediaSession(this,"GlassTV AirPlay");
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { sendCommand("play"); }
                @Override public void onPause() { sendCommand("pause"); }
                @Override public void onSkipToNext() { sendCommand("nextitem"); }
                @Override public void onSkipToPrevious() { sendCommand("previtem"); }
            },main);
            int port = bridge.nativeStart(handle,7000);
            if (port <= 0) throw new IOException("port indisponible");
            register(bridge.nativeGetRaopServiceName(handle),"_raop._tcp",port,bridge.nativeGetRaopTxtRecords(handle));
            register("GlassTV","_airplay._tcp",port,bridge.nativeGetAirplayTxtRecords(handle));
        } catch (Exception | LinkageError e) {
            android.util.Log.e("GLASSTV_AIRPLAY","Startup failed",e);
            fail("AirPlay indisponible · " + e.getClass().getSimpleName());
        }
    }
    private int property(String key) {
        try { return Integer.parseInt(audio.getProperty(key)); } catch (NumberFormatException e) { return 0; }
    }
    private void register(String name,String type,int port,Map<String,String> attributes) {
        NsdServiceInfo info = new NsdServiceInfo(); info.setServiceName(name); info.setServiceType(type); info.setPort(port);
        if (attributes != null) for (Map.Entry<String,String> entry : attributes.entrySet()) info.setAttribute(entry.getKey(),entry.getValue());
        NsdManager.RegistrationListener listener = new NsdManager.RegistrationListener() {
            public void onServiceRegistered(NsdServiceInfo result) { post(() -> { if (++registered == 2) setStatus("Prêt · sélectionne GlassTV sur ton iPhone"); }); }
            public void onRegistrationFailed(NsdServiceInfo result,int code) { post(() -> fail("Annonce réseau AirPlay impossible · " + code)); }
            public void onServiceUnregistered(NsdServiceInfo result) {}
            public void onUnregistrationFailed(NsdServiceInfo result,int code) {}
        };
        nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,listener); registrations.add(listener);
    }
    private void post(Runnable work) { main.post(() -> { if (!destroyed) work.run(); }); }
    private void publish() {
        if (session == null) return;
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST,artist).putString(MediaMetadata.METADATA_KEY_ALBUM,album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION,duration).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,artwork).build());
        long actions = remoteHost == null ? 0 : PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,playing?1f:0f).build());
        session.setActive(audioActive);
    }
    public void onAudioFormat(int codec,int frames,boolean screen) { post(() -> {
        if (handle == 0) return;
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { setStatus("Lecture interrompue par une autre application");return; }
        if (!bridge.nativeServerAudioStart(handle)) { fail("La sortie audio AirPlay n’a pas démarré");return; }
        bridge.nativeServerAudioFormat(handle,codec,frames);
        audioActive=true;playing=true;
        if (!wake.isHeld()) wake.acquire();
        setStatus("Lecture AirPlay"); publish();
    }); }
    public void onClientVolume(float db) { post(() -> {
        if (!Float.isFinite(db)) return;
        clientVolume=Math.max(-144f,Math.min(0f,db));
        if (handle!=0) bridge.nativeServerAudioSetClientGain(handle,db<=-144?0f:(float)Math.pow(10,clientVolume/20));
    }); }
    public float onClientVolumeQuery() { return clientVolume; }
    public void onConnectionInit() { post(() -> connections++); }
    public void onConnectionDestroy() { post(() -> { connections=Math.max(0,connections-1);if(connections==0) clearPlayback(); }); }
    private void clearPlayback() {
        if(handle!=0) bridge.nativeServerAudioStop(handle);
        audioActive=false;playing=false;title="AirPlay";artist="";album="";artwork=null;position=0;duration=0;
        remoteGeneration++;remoteHost=null;remoteToken="";
        if(wake!=null&&wake.isHeld())wake.release();
        if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);
        publish();setStatus("Prêt · sélectionne GlassTV sur ton iPhone");
    }
    public void onAudioTeardown() { post(() -> {playing=false;publish();}); }
    public void onDisplayPin(String pin) { post(() -> {
        setStatus("Code AirPlay · " + pin);
        Toast.makeText(this,"Code AirPlay : " + pin,Toast.LENGTH_LONG).show();
    }); }
    public void onProgress(long start,long current,long end) { post(() -> {
        long total=(end-start)&0xffffffffL;
        if(total==0)return;
        duration=total*1000/44100;position=Math.min(duration,((current-start)&0xffffffffL)*1000/44100);playing=true;publish();
    }); }
    public void onMetadata(byte[] data) {
        if(data.length>1024*1024)return;
        Map<String,String> tags=new HashMap<>();parseTags(ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN),tags,0);
        post(() -> { title=tags.getOrDefault("minm","AirPlay");artist=tags.getOrDefault("asar","");album=tags.getOrDefault("asal","");publish(); });
    }
    private static void parseTags(ByteBuffer buffer,Map<String,String> tags,int depth) {
        if(depth>8)return;
        while(buffer.remaining()>=8){
            byte[] tagBytes=new byte[4];buffer.get(tagBytes);String tag=new String(tagBytes,StandardCharsets.US_ASCII);int length=buffer.getInt();
            if(length<0||length>buffer.remaining())return;
            ByteBuffer value=buffer.slice().order(ByteOrder.BIG_ENDIAN);value.limit(length);buffer.position(buffer.position()+length);
            if(tag.equals("mlit")||tag.equals("mcon")||tag.equals("mlcl"))parseTags(value,tags,depth+1);
            else if(tag.equals("minm")||tag.equals("asar")||tag.equals("asal")){byte[] bytes=new byte[Math.min(length,4096)];value.get(bytes);tags.put(tag,new String(bytes,StandardCharsets.UTF_8));}
        }
    }
    public void onCoverArt(byte[] data) {
        if(data.length>5*1024*1024)return;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);
        if(options.outWidth<=0||options.outHeight<=0)return;
        options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
        post(() -> {artwork=bitmap;publish();});
    }
    public void onDacpId(String id,String token) { post(() -> {
        if(!id.matches("[A-Fa-f0-9]{1,32}")||!token.matches("[0-9]{1,32}"))return;
        int generation=++remoteGeneration;remoteHost=null;remoteToken=token;
        NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("iTunes_Ctrl_"+id);info.setServiceType("_dacp._tcp");
        nsd.resolveService(info,new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo result,int code){post(() -> setStatus("Lecture AirPlay · commandes depuis l’iPhone"));}
            public void onServiceResolved(NsdServiceInfo result){post(() -> {
                if(generation!=remoteGeneration)return;
                InetAddress host=result.getHost();
                if(host==null||(!host.isSiteLocalAddress()&&!host.isLinkLocalAddress()))return;
                remoteHost=host;remotePort=result.getPort();publish();
            });}
        });
    }); }
    private void sendCommand(String command) {
        final InetAddress host=remoteHost;final int port=remotePort,generation=remoteGeneration;final String token=remoteToken;
        if(host==null){Toast.makeText(this,"Commande disponible depuis l’iPhone",Toast.LENGTH_SHORT).show();return;}
        commands.execute(() -> {
            try(Socket socket=new Socket()){
                socket.connect(new InetSocketAddress(host,port),2000);socket.setSoTimeout(2000);
                String request="GET /ctrl-int/1/"+command+" HTTP/1.1\r\nHost: "+host.getHostAddress()+":"+port+"\r\nActive-Remote: "+token+"\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                String response=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII)).readLine();
                if(response==null||!response.matches("HTTP/1\\.[01] 2[0-9][0-9].*"))throw new IOException("DACP refused");
                post(() -> {if(generation!=remoteGeneration)return;if(command.equals("play"))playing=true;if(command.equals("pause"))playing=false;publish();});
            }catch(IOException e){post(() -> Toast.makeText(this,"Commande AirPlay refusée · utilise l’iPhone",Toast.LENGTH_SHORT).show());}
        });
    }
    public void onLog(String message) { android.util.Log.d("GLASSTV_AIRPLAY_NATIVE",message); }
    public void onVideoData(byte[] data,long time,boolean h265) {}
    public void onVideoSize(float a,float b,float c,float d) {}
    public void onConnectionReset(int reason) {}
    public void onMirrorRunning(boolean running) {}
    public void onVideoPlay(String location,float start) {}
    public void onVideoScrub(float position) {}
    public void onVideoRate(float rate) {}
    public void onVideoStop() {}
    public void onVideoSessionPoll() {}
    @Override public void onDestroy() {
        destroyed=true;instance=null;main.removeCallbacksAndMessages(null);commands.shutdownNow();
        if(nsd!=null)for(NsdManager.RegistrationListener listener:registrations){try{nsd.unregisterService(listener);}catch(IllegalArgumentException ignored){}}
        if(handle!=0){bridge.nativeStop(handle);bridge.nativeServerAudioStop(handle);bridge.nativeDestroy(handle);handle=0;}
        if(session!=null)session.release();
        if(wake!=null&&wake.isHeld())wake.release();
        if(multicast!=null&&multicast.isHeld())multicast.release();
        if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);
        status="Désactivé";stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
