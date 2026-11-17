package com.tareas.notif;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import androidx.core.content.FileProvider;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Notificaciones extends CordovaPlugin {

    private static final String PREFS = "cdp_notificaciones";
    private static final String KEY = "pend";
    private static final int REQ_NOTIF = 1;
    private static final int REQ_MIC = 2;
    private CallbackContext permCb;
    private CallbackContext tocarCb;
    private int avisoTarea = 0;
    private MediaRecorder grabadora;
    private File audioFile;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        try {
            Intent i = cordova.getActivity().getIntent();
            if (i != null) avisoTarea = i.getIntExtra("tarea", 0);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        int t = intent.getIntExtra("tarea", 0);
        if (t > 0) {
            avisoTarea = t;
            intent.removeExtra("tarea");
            entregarAviso();
        }
    }

    private void entregarAviso() {
        if (tocarCb == null || avisoTarea <= 0) return;
        int t = avisoTarea;
        avisoTarea = 0;
        PluginResult r = new PluginResult(PluginResult.Status.OK, t);
        r.setKeepCallback(true);
        tocarCb.sendPluginResult(r);
    }

    @Override
    public boolean execute(String accion, CordovaArgs args, final CallbackContext cb) throws JSONException {
        if ("sincronizar".equals(accion)) {
            final JSONArray arr = args.getJSONArray(0);
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try { sincronizar(cordova.getContext(), arr); cb.success("ok"); }
                    catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("alTocar".equals(accion)) {
            tocarCb = cb;
            entregarAviso();
            return true;
        }
        if ("grabarIniciar".equals(accion)) {
            if (grabadora != null) { cb.error("ya se está grabando"); return true; }
            if (!cordova.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                cb.error("sin permiso de micrófono");
                return true;
            }
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try {
                        Context ctx = cordova.getContext();
                        audioFile = new File(ctx.getCacheDir(), "nota_" + System.currentTimeMillis() + ".m4a");
                        MediaRecorder r = new MediaRecorder();
                        r.setAudioSource(MediaRecorder.AudioSource.MIC);
                        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                        r.setAudioChannels(1);
                        r.setAudioSamplingRate(22050);
                        r.setAudioEncodingBitRate(32000);
                        r.setOutputFile(audioFile.getAbsolutePath());
                        r.prepare();
                        r.start();
                        grabadora = r;
                        cb.success("ok");
                    } catch (Throwable e) {
                        cancelarGrabacion();
                        cb.error(msg(e));
                    }
                }
            });
            return true;
        }
        if ("grabarParar".equals(accion)) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    MediaRecorder r = grabadora;
                    if (r == null) { cb.error("no hay grabación"); return; }
                    grabadora = null;
                    try { r.stop(); } catch (Throwable ignored) { }
                    try { r.release(); } catch (Throwable ignored) { }
                    try {
                        if (audioFile == null || !audioFile.exists() || audioFile.length() < 100) {
                            if (audioFile != null) audioFile.delete();
                            audioFile = null;
                            cb.error("grabación vacía");
                            return;
                        }
                        byte[] data = leer(audioFile);
                        audioFile.delete();
                        audioFile = null;
                        cb.success("data:audio/mp4;base64," + Base64.encodeToString(data, Base64.NO_WRAP));
                    } catch (Throwable e) {
                        cancelarGrabacion();
                        cb.error(msg(e));
                    }
                }
            });
            return true;
        }
        if ("grabarCancelar".equals(accion)) {
            cancelarGrabacion();
            cb.success("ok");
            return true;
        }
        if ("whatsappTexto".equals(accion)) {
            final String tel = args.getString(0);
            final String texto = args.getString(1);
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try { abrirWhatsApp(cordova.getContext(), tel, texto); cb.success("ok"); }
                    catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("whatsappAudio".equals(accion)) {
            final String tel = args.getString(0);
            final String texto = args.getString(1);
            final String audio = args.getString(2);
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try { compartirAudio(cordova.getContext(), tel, texto, audio); cb.success("ok"); }
                    catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("pedirPermiso".equals(accion)) {
            if (Build.VERSION.SDK_INT >= 33) {
                permCb = cb;
                cordova.requestPermission(this, REQ_NOTIF, Manifest.permission.POST_NOTIFICATIONS);
            } else cb.success("otorgado");
            return true;
        }
        if ("pedirMicro".equals(accion)) {
            permCb = cb;
            cordova.requestPermission(this, REQ_MIC, Manifest.permission.RECORD_AUDIO);
            return true;
        }
        if ("estado".equals(accion)) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try {
                        Context ctx = cordova.getContext();
                        boolean permiso = true;
                        if (Build.VERSION.SDK_INT >= 33)
                            permiso = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                        boolean micro = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                        boolean exactas = true;
                        if (Build.VERSION.SDK_INT >= 31) {
                            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                            exactas = am != null && am.canScheduleExactAlarms();
                        }
                        JSONObject o = new JSONObject();
                        o.put("permiso", permiso);
                        o.put("micro", micro);
                        o.put("exactas", exactas);
                        cb.success(o);
                    } catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("abrirAjustes".equals(accion)) {
            try {
                String tipo = args.getString(0);
                Context ctx = cordova.getContext();
                Intent i;
                if ("alarmas".equals(tipo)) {
                    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                    if (Build.VERSION.SDK_INT >= 31 && am != null && !am.canScheduleExactAlarms()) {
                        i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    } else {
                        i = appSettings(ctx);
                    }
                } else if ("mic".equals(tipo)) {
                    i = appSettings(ctx);
                } else {
                    if (Build.VERSION.SDK_INT >= 33) {
                        i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
                    } else i = appSettings(ctx);
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                cb.success("ok");
            } catch (Throwable e) { cb.error(msg(e)); }
            return true;
        }
        if ("mostrar".equals(accion)) {
            final int id = args.getInt(0);
            final String titulo = args.getString(1);
            final String texto = args.getString(2);
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try { NotifReceiver.mostrar(cordova.getContext(), id, titulo, texto); cb.success("ok"); }
                    catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("probar".equals(accion)) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try {
                        NotifReceiver.mostrar(cordova.getContext(), 999999,
                                "Aviso de prueba", "Las notificaciones funcionan en este dispositivo.");
                        cb.success("ok");
                    } catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        cb.error("accion desconocida: " + accion);
        return false;
    }

    private static Intent appSettings(Context ctx) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + ctx.getPackageName()));
        return i;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        CallbackContext cb = permCb;
        permCb = null;
        if (cb == null) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == 0;
        cb.success(ok ? "otorgado" : "denegado");
    }

    private static String msg(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String normalizarTel(String tel) {
        String d = tel == null ? "" : tel.replaceAll("[^0-9]", "");
        if (d.startsWith("00")) d = d.substring(2);
        if (d.startsWith("0")) d = d.substring(1);
        if (d.length() == 8) d = "53" + d;
        return d;
    }

    private static void abrirWhatsApp(Context ctx, String tel, String texto) throws Exception {
        String d = normalizarTel(tel);
        if (d.length() < 6) throw new Exception("Teléfono no válido");
        Intent wa = new Intent(Intent.ACTION_VIEW,
                Uri.parse("whatsapp://send?phone=" + d + "&text=" + Uri.encode(texto)));
        wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(wa);
            return;
        } catch (Throwable ignored) { }
        Intent web = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + d + "?text=" + Uri.encode(texto)));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(web);
    }

    private static void compartirAudio(Context ctx, String tel, String texto, String data) throws Exception {
        String d = normalizarTel(tel);
        int comma = data == null ? -1 : data.indexOf(',');
        String b64 = comma >= 0 ? data.substring(comma + 1) : data;
        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
        File dir = new File(ctx.getCacheDir(), "compartir");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "tarea_" + System.currentTimeMillis() + ".m4a");
        FileOutputStream out = new FileOutputStream(f);
        try { out.write(bytes); } finally { out.close(); }
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".cdpnotif", f);
        String caption = texto + (d.length() > 0 ? "\n\nWhatsApp: +" + d : "");
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("audio/mp4");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_TEXT, caption);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage("com.whatsapp");
        try {
            ctx.startActivity(i);
            return;
        } catch (Throwable ignored) { }
        i.setPackage(null);
        Intent chooser = Intent.createChooser(i, "Compartir nota de voz");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
    }

    private void cancelarGrabacion() {
        MediaRecorder r = grabadora;
        grabadora = null;
        if (r != null) {
            try { r.stop(); } catch (Throwable ignored) { }
            try { r.release(); } catch (Throwable ignored) { }
        }
        if (audioFile != null) {
            audioFile.delete();
            audioFile = null;
        }
    }

    private static byte[] leer(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        cancelarGrabacion();
        super.onPause(multitasking);
    }

    @Override
    public void onDestroy() {
        cancelarGrabacion();
        super.onDestroy();
    }

    static void sincronizar(Context ctx, JSONArray arr) throws JSONException {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray viejo = new JSONArray(sp.getString(KEY, "[]"));
        for (int k = 0; k < viejo.length(); k++) cancelar(ctx, viejo.getJSONObject(k).optInt("id"));
        JSONArray nuevo = new JSONArray();
        long ahora = System.currentTimeMillis();
        for (int k = 0; k < arr.length(); k++) {
            JSONObject o = arr.getJSONObject(k);
            long ts = o.optLong("ts");
            if (ts > ahora) {
                programar(ctx, o.optInt("id"), o.optString("titulo"), o.optString("texto"), ts);
                nuevo.put(o);
            }
        }
        sp.edit().putString(KEY, nuevo.toString()).apply();
    }

    static void reprogramar(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray viejo = new JSONArray(sp.getString(KEY, "[]"));
            long ahora = System.currentTimeMillis();
            for (int k = 0; k < viejo.length(); k++) {
                JSONObject o = viejo.getJSONObject(k);
                long ts = o.optLong("ts");
                if (ts > ahora) programar(ctx, o.optInt("id"), o.optString("titulo"), o.optString("texto"), ts);
            }
        } catch (JSONException ignored) { }
    }

    private static void programar(Context ctx, int id, String titulo, String texto, long ts) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pi(ctx, id, titulo, texto);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi);
            return;
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi);
        }
    }

    static void cancelar(Context ctx, int id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(PendingIntent.getBroadcast(ctx, id, base(ctx, id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static Intent base(Context ctx, int id) {
        Intent i = new Intent(ctx, NotifReceiver.class);
        i.setData(Uri.parse("cdpnotif://" + id));
        i.putExtra("id", id);
        return i;
    }

    private static PendingIntent pi(Context ctx, int id, String titulo, String texto) {
        Intent i = base(ctx, id);
        i.putExtra("titulo", titulo);
        i.putExtra("texto", texto);
        return PendingIntent.getBroadcast(ctx, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
