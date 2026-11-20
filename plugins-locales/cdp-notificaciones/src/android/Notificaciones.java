package com.tareas.notif;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
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
    private static final int REQ_CAL = 3;
    /* texto recibido por ACTION_SEND (Compartir -> Organizador) */
    public static volatile String textoShare = null;
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
        try {
            Intent is = cordova.getActivity().getIntent();
            if (is != null && Intent.ACTION_SEND.equals(is.getAction())) {
                String s0 = is.getStringExtra(Intent.EXTRA_TEXT);
                if (s0 != null && s0.trim().length() > 0) textoShare = s0.trim();
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        try {
            if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null
                    && intent.getType().contains("text/")) {
                String st = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (st != null && st.trim().length() > 0) textoShare = st.trim();
            }
        } catch (Throwable ignored) { }
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
        if ("calPermisos".equals(accion)) {
            Context ctx = cordova.getContext();
            boolean lee = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
            boolean escribe = ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
            if (!lee || !escribe) {
                permCb = cb;
                cordova.requestPermissions(this, REQ_CAL, new String[]{
                        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR});
            } else cb.success("otorgado");
            return true;
        }
        if ("calInsertar".equals(accion)) {
            final JSONObject ev = args.getJSONObject(0);
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try {
                        Context ctx = cordova.getContext();
                        if (ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
                            throw new Exception("sin permiso de calendario");
                        android.content.ContentResolver cr = ctx.getContentResolver();
                        long calId = calendarioElegido(cr);
                        ContentValues v = new ContentValues();
                        v.put(CalendarContract.Events.CALENDAR_ID, calId);
                        v.put(CalendarContract.Events.TITLE, ev.optString("titulo"));
                        String desc = ev.optString("desc");
                        if (desc.length() > 0) v.put(CalendarContract.Events.DESCRIPTION, desc);
                        String zona = java.util.TimeZone.getDefault().getID();
                        long ini = inicioDia(ev.optString("fecha"));
                        if (ev.optBoolean("allDay", false)) {
                            v.put(CalendarContract.Events.ALL_DAY, 1);
                            v.put(CalendarContract.Events.DTSTART, ini);
                            v.put(CalendarContract.Events.DTEND, ini + 86400000L);
                        } else {
                            ini += horaMs(ev.optString("hora"));
                            long dur = ev.optLong("durMin", 60);
                            if (dur < 5) dur = 5;
                            v.put(CalendarContract.Events.DTSTART, ini);
                            v.put(CalendarContract.Events.DTEND, ini + dur * 60000L);
                        }
                        v.put(CalendarContract.Events.EVENT_TIMEZONE, zona);
                        /* marca: sabemos después qué eventos creó esta app y no los reimportamos */
                        v.put(CalendarContract.Events.CUSTOM_APP_PACKAGE, ctx.getPackageName());
                        Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, v);
                        if (uri == null) throw new Exception("no se pudo crear el evento");
                        cb.success(String.valueOf(ContentUris.parseId(uri)));
                    } catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("calBorrar".equals(accion)) {
            final long evId = args.getLong(0);
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try {
                        Context ctx = cordova.getContext();
                        if (ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
                            throw new Exception("sin permiso de calendario");
                        int n = ctx.getContentResolver().delete(
                                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, evId), null, null);
                        cb.success(n > 0 ? "ok" : "no encontrado");
                    } catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("calEventos".equals(accion)) {
            final long desde = args.getLong(0);
            final long hasta = args.getLong(1);
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try {
                        Context ctx = cordova.getContext();
                        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
                            throw new Exception("sin permiso de calendario");
                        cb.success(calEventos(ctx, desde, hasta));
                    } catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        if ("leerCompartido".equals(accion)) {
            String s1 = textoShare;
            textoShare = null;
            cb.success(s1 == null ? "" : s1.trim());
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
        boolean ok = grantResults.length > 0;
        if (requestCode == REQ_CAL) {
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            }
        } else {
            ok = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        cb.success(ok ? "otorgado" : "denegado");
    }

    private static String msg(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /* calendario */

    /** elige el calendario visible con permiso de escritura, priorizando el primario; -1 si no hay */
    private static long calendarioElegido(android.content.ContentResolver cr) throws Exception {
        if (Build.VERSION.SDK_INT < 14) throw new Exception("calendario no disponible");
        android.database.Cursor c = null;
        try {
            c = cr.query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID,
                            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL},
                    CalendarContract.Calendars.VISIBLE + "=1", null,
                    CalendarContract.Calendars.IS_PRIMARY + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    if (c.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                        long id = c.getLong(0);
                        return id;
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
        throw new Exception("no hay calendario disponible");
    }

    /** milisegundos de la medianoche local de una fecha YYYY-MM-DD */
    private static long inicioDia(String fecha) {
        int y = 2000, m = 1, d = 1;
        try {
            String[] p = fecha.split("-");
            y = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); d = Integer.parseInt(p[2]);
        } catch (Throwable ignored) { }
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    /** milisegundos de una hora HH:MM local (0 si viene vacia o invalida) */
    private static long horaMs(String hora) {
        try {
            String[] p = hora.split(":");
            return (Long.parseLong(p[0]) * 60L + Long.parseLong(p[1])) * 60000L;
        } catch (Throwable e) { return 0; }
    }

    private static JSONArray calEventos(Context ctx, long desde, long hasta) throws Exception {
        android.content.ContentResolver cr = ctx.getContentResolver();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, desde);
        ContentUris.appendId(b, hasta);
        String[] projConMarca = new String[]{CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.CUSTOM_APP_PACKAGE};
        String[] projBase = new String[]{CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.BEGIN};
        String orden = CalendarContract.Instances.BEGIN + " ASC";
        android.database.Cursor c;
        boolean conMarca = true;
        try { c = cr.query(b.build(), projConMarca, null, null, orden); }
        catch (Throwable e) { c = cr.query(b.build(), projBase, null, null, orden); conMarca = false; }
        JSONArray res = new JSONArray();
        String paq = ctx.getPackageName();
        try {
            if (c != null) {
                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("evId", String.valueOf(c.getLong(0)));
                    o.put("titulo", c.getString(1) == null ? "" : c.getString(1));
                    o.put("desc", c.getString(2) == null ? "" : c.getString(2));
                    boolean todo = c.getInt(3) != 0;
                    long ini = c.getLong(4);
                    boolean mio = false;
                    if (conMarca) {
                        String cmp = c.getString(5);
                        mio = cmp != null && cmp.length() > 0 && paq.equals(cmp);
                    }
                    o.put("allDay", todo);
                    o.put("inicio", ini);
                    o.put("mio", mio);
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(ini);
                    o.put("fecha", String.format("%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)));
                    if (!todo) o.put("hora", String.format("%02d:%02d",
                            cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)));
                    res.put(o);
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return res;
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
