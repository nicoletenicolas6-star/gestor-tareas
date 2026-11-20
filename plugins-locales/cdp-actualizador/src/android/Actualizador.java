package com.tareas.upd;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Actualizacion automatica desde GitHub Releases:
 *  - buscar:    GET releases/latest de "nicoletenicolas6-star/gestor-tareas";
 *               devuelve {"tag","apk","nombre"} o '' si no se pudo.
 *  - descargar: baja la APK a Descargas (MediaStore) y va mandando progreso
 *               '{"p":PCT}'; al terminar '{"fin":1,"uri":"content://..."}'.
 *  - instalar:  abre el paquete con el instalador de Android (ACTION_VIEW).
 * Todo en hilos propios; la app no guarda nada sensible.
 */
public class Actualizador extends CordovaPlugin {

    private static final String REPO = "nicoletenicolas6-star/gestor-tareas";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String MIME_APK = "application/vnd.android.package-archive";

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext cb) {
        if ("buscar".equals(action)) {
            buscar(cb);
            return true;
        }
        if ("descargar".equals(action)) {
            final String url = args.optString(0, "");
            descargar(url, cb);
            return true;
        }
        if ("instalar".equals(action)) {
            final String uri = args.optString(0, "");
            instalar(uri, cb);
            return true;
        }
        return false;
    }

    /* ---------- buscar ---------- */
    private void buscar(final CallbackContext cb) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                String out = "";
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    c.setRequestProperty("User-Agent", "gestor-tareas");
                    int code = c.getResponseCode();
                    if (code == 200) {
                        String body = leer(c.getInputStream());
                        JSONObject o = new JSONObject(body);
                        String tag = o.optString("tag_name", "");
                        String nombre = o.optString("name", "");
                        String apk = "";
                        JSONArray as = o.optJSONArray("assets");
                        for (int i = 0; as != null && i < as.length(); i++) {
                            JSONObject a = as.getJSONObject(i);
                            String n = a.optString("name", "");
                            if (n.endsWith(".apk")) {
                                apk = a.optString("browser_download_url", "");
                                break;
                            }
                        }
                        JSONObject r = new JSONObject();
                        r.put("tag", tag);
                        r.put("apk", apk);
                        r.put("nombre", nombre);
                        out = r.toString();
                    }
                    c.disconnect();
                } catch (Exception e) {
                    out = "";
                }
                PluginResult r = new PluginResult(PluginResult.Status.OK, out);
                cb.sendPluginResult(r);
            }
        });
    }

    /* ---------- descargar con progreso ---------- */
    private void descargar(final String url, final CallbackContext cb) {
        if (url == null || url.length() == 0) {
            cb.error("sin url");
            return;
        }
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String nombre = url.substring(url.lastIndexOf('/') + 1);
                    if (nombre.indexOf('?') >= 0) nombre = nombre.substring(0, nombre.indexOf('?'));
                    if (nombre.length() == 0) nombre = "actualizacion.apk";
                    android.content.Context ctx = cordova.getActivity().getApplicationContext();
                    android.content.ContentResolver cr = ctx.getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                    cv.put(MediaStore.Downloads.MIME_TYPE, MIME_APK);
                    cv.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri dest = cr.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv);
                    if (dest == null) throw new Exception("no se pudo crear el archivo en Descargas");

                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("User-Agent", "gestor-tareas");
                    c.setInstanceFollowRedirects(true);
                    int code = c.getResponseCode();
                    if (code != 200) throw new Exception("descarga HTTP " + code);
                    long total = c.getContentLengthLong();
                    InputStream in = new BufferedInputStream(c.getInputStream());
                    OutputStream out = cr.openOutputStream(dest);

                    byte[] buf = new byte[16384];
                    long he = 0;
                    int n;
                    int ultPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        he += n;
                        if (total > 0) {
                            int pct = (int) (he * 100 / total);
                            if (pct != ultPct && pct % 5 == 0) { /* aviso cada 5% */
                                ultPct = pct;
                                JSONObject pr = new JSONObject();
                                pr.put("p", pct);
                                PluginResult r = new PluginResult(PluginResult.Status.OK, pr.toString());
                                r.setKeepCallback(true);
                                cb.sendPluginResult(r);
                            }
                        }
                    }
                    out.flush();
                    out.close();
                    in.close();
                    c.disconnect();

                    ContentValues cv2 = new ContentValues();
                    cv2.put(MediaStore.Downloads.IS_PENDING, 0);
                    cr.update(dest, cv2, null, null);

                    JSONObject fin = new JSONObject();
                    fin.put("fin", 1);
                    fin.put("uri", dest.toString());
                    PluginResult r = new PluginResult(PluginResult.Status.OK, fin.toString());
                    cb.sendPluginResult(r);
                } catch (Exception e) {
                    cb.error("no se pudo descargar: " + e.getMessage());
                }
            }
        });
    }

    /* ---------- lanzar el instalador ---------- */
    private void instalar(final String uri, final CallbackContext cb) {
        Activity act = cordova.getActivity();
        try {
            if (uri == null || uri.length() == 0) throw new Exception("sin archivo");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(uri), MIME_APK);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
            cb.success("ok");
        } catch (Exception e) {
            /* conserva el archivo en Descargas para instalacion manual */
            cb.error("Android pidio permiso o fallo: " + e.getMessage()
                    + " — instala a mano desde Descargas");
        }
    }

    private static String leer(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
}
