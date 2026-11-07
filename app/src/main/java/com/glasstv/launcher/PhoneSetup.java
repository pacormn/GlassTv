package com.glasstv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

/** Short-lived, token-protected LAN setup. No key is returned or logged. */
final class PhoneSetup {
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile ServerSocket server;
    private volatile Socket client;
    private volatile boolean closed;
    private AlertDialog dialog;
    private final Runnable timeout = this::close;
    PhoneSetup(Activity activity) { this.activity = activity; }
    void show(Runnable saved) {
        try {
            InetAddress address = null;
            for (NetworkInterface net : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!net.isUp() || net.isLoopback()) continue;
                for (InetAddress a : Collections.list(net.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) { address = a; break; }
                if (address != null) break;
            }
            if (address == null) throw new IOException("No LAN");
            server = new ServerSocket(0, 2, address);
            String path = "/" + UUID.randomUUID().toString();
            String origin = "http://" + address.getHostAddress() + ":" + server.getLocalPort();
            String url = origin + path;
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24, 12, 24, 12);
            TextView description = new TextView(activity);
            description.setText("Scanne avec ton iPhone connecté au même Wi-Fi, puis colle ta clé API TMDB v3.\nLien local valable 5 minutes, uniquement pendant cet écran.\n" + url);
            layout.addView(description);
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320);
            Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
            for (int y=0;y<320;y++) for(int x=0;x<320;x++) bitmap.setPixel(x,y,matrix.get(x,y)?0xff000000:0xffffffff);
            ImageView qr = new ImageView(activity); qr.setImageBitmap(bitmap);
            layout.addView(qr, new LinearLayout.LayoutParams(-1, 320));
            dialog = new AlertDialog.Builder(activity).setTitle("Configurer TMDB depuis le téléphone")
                .setView(layout).setNegativeButton("Fermer", (d,w)->close()).create();
            dialog.setOnDismissListener(d->close()); dialog.show();
            ui.postDelayed(timeout, 300000);
            new Thread(()->serve(path, origin, saved), "GlassTV-local-setup").start();
        } catch (Exception e) {
            close();
            new AlertDialog.Builder(activity).setMessage("Connexion locale indisponible. Connecte la box au Wi-Fi ou à Ethernet, puis réessaie.").setPositiveButton("Compris",null).show();
        }
    }
    private void serve(String path, String origin, Runnable saved) {
        while (!closed) {
            try (Socket socket = server.accept()) {
                client = socket; socket.setSoTimeout(5000);
                InputStream in = socket.getInputStream();
                String request = line(in); if (request == null) continue;
                String[] parts = request.split(" ");
                Map<String,String> headers = new HashMap<>(); int total = request.length();
                String header;
                while ((header=line(in)) != null && !header.isEmpty()) {
                    total += header.length(); if(total>8192) throw new IOException();
                    int colon=header.indexOf(':'); if(colon>0) headers.put(header.substring(0,colon).toLowerCase(Locale.ROOT),header.substring(colon+1).trim());
                }
                if(parts.length!=3 || !parts[1].equals(path) || !origin.substring(7).equals(headers.get("host"))) {
                    reply(socket,404,"Lien invalide ou expiré."); continue;
                }
                if (parts[0].equals("GET")) {
                    reply(socket,200,"<h1>GlassTV</h1><p>Colle ta clé API TMDB v3. Elle sera enregistrée sur ta box.</p><form method='post' action='"+path+"'><input name='key' type='password' autocomplete='off' required pattern='[a-fA-F0-9]{32}' placeholder='Clé TMDB v3' style='width:95%;padding:12px'><p><button style='padding:14px'>Enregistrer sur la TV</button></p></form>");
                } else if(parts[0].equals("POST")) {
                    if(!origin.equals(headers.get("origin")) || !headers.getOrDefault("content-type","").startsWith("application/x-www-form-urlencoded")) {reply(socket,403,"Requête refusée.");continue;}
                    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));
                    if(length<1||length>512) {reply(socket,400,"Clé invalide.");continue;}
                    byte[] body=new byte[length]; int offset=0;
                    while(offset<length) {int n=in.read(body,offset,length-offset);if(n<0)throw new IOException();offset+=n;}
                    String key="";
                    for(String field:new String(body,StandardCharsets.UTF_8).split("&")) if(field.startsWith("key="))key=URLDecoder.decode(field.substring(4),"UTF-8").trim();
                    if(!key.matches("[a-fA-F0-9]{32}")){reply(socket,400,"Format invalide. Retourne à la page précédente et colle la clé API v3 de 32 caractères.");continue;}
                    boolean stored=activity.getSharedPreferences("tmdb_private",0).edit().putString("api_key",key).commit();
                    if(!stored){reply(socket,500,"Enregistrement impossible. Réessaie.");continue;}
                    reply(socket,200,"<h1>Clé enregistrée</h1><p>Tu peux fermer cette page et revenir sur la TV.</p>");
                    ui.post(()->{close();saved.run();}); return;
                } else reply(socket,405,"Méthode refusée.");
            } catch(Exception ignored) { /* Do not log requests or credentials. */ }
            finally {client=null;}
        }
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int i=0;i<4096;i++){int b=in.read();if(b<0)return null;if(b=='\n')return out.toString("US-ASCII").replace("\r","");out.write(b);}
        throw new IOException("Header too large");
    }
    private static void reply(Socket socket,int status,String body) throws IOException {
        byte[] bytes=("<!doctype html><html lang='fr'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>GlassTV</title><body style='background:#161616;color:white;font:18px system-ui;padding:24px'>"+body+"</body></html>").getBytes(StandardCharsets.UTF_8);
        OutputStream out=socket.getOutputStream();
        out.write(("HTTP/1.1 "+status+" Response\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
    void close() {
        closed=true;ui.removeCallbacks(timeout);
        try {if(server!=null)server.close();}catch(IOException ignored){}
        try {if(client!=null)client.close();}catch(IOException ignored){}
        if(dialog!=null && dialog.isShowing())dialog.dismiss();
    }
}
