package com.tareas.notif;

import com.mitienda.pos.tareas.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Color;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Widget de la agenda: cabecera con el acento de la app, fondo suavemente
 * tteido de ese mismo color y una fila-tarjeta por tarea (punto del tipo,
 * hora en acento, titulo, y check/tachado si esta hecha).
 * El color de acento lo manda la app en el JSON (campo "acento"), igual que
 * el color de cada tarea (campo "c" de cada linea); los datos llegan por el
 * plugin CdpWidget (SharedPreferences); al tocar el widget se abre la app.
 */
public class WidgetProvider extends AppWidgetProvider {

    private static final int MAX = 12;
    private static final int ACENTO_DEF = 0xFF1E5AE8;
    private static final int COLOR_TXT = 0xFF1B1B1B;
    private static final int COLOR_TXT2 = 0xFF6B7280;
    private static final int COLOR_VENCIDA = 0xFFC0392B;
    private static final int COLOR_CHECK_OFF = 0xFF9AA0A6;
    private static final int COLOR_OK = 0xFF1F8A3C;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        pintar(ctx, mgr, ids);
    }

    public static void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn = new ComponentName(ctx, WidgetProvider.class);
        pintar(ctx, mgr, mgr.getAppWidgetIds(cn));
    }

    /* mezcla el acento con blanco: f = 0 (blanco) .. 1 (acento puro) */
    private static int mezclar(int acento, double f) {
        int r = (int) Math.round(Color.red(acento) * f + 255 * (1 - f));
        int g = (int) Math.round(Color.green(acento) * f + 255 * (1 - f));
        int b = (int) Math.round(Color.blue(acento) * f + 255 * (1 - f));
        return Color.rgb(Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)));
    }

    static void pintar(Context ctx, AppWidgetManager mgr, int[] ids) {
        if (ids == null || ids.length == 0) return;
        SharedPreferences sp = ctx.getSharedPreferences("cdp_widget", Context.MODE_PRIVATE);
        String raw = sp.getString("datos", "");
        String cuando = "";
        String acentoStr = "";
        int acento = ACENTO_DEF;
        JSONArray ls = null;
        try {
            if (raw.length() > 0) {
                JSONObject o = new JSONObject(raw);
                cuando = o.optString("cuando", "");
                acentoStr = o.optString("acento", "");
                ls = o.optJSONArray("lineas");
            }
        } catch (Exception e) { ls = null; }
        if (acentoStr != null && acentoStr.length() > 0) {
            try { acento = Color.parseColor(acentoStr); } catch (Exception e) { acento = ACENTO_DEF; }
        }

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.cdp_widget);
        try { v.setInt(R.id.cdp_widget_root, "setBackgroundColor", mezclar(acento, 0.14)); } catch (Exception e) { }
        v.setTextViewText(R.id.cdp_widget_tit, "Organizador");
        v.setInt(R.id.cdp_widget_tit, "setTextColor", acento);
        if (cuando == null) cuando = "";
        v.setTextViewText(R.id.cdp_widget_cuando, cuando);

        int n = 0;
        try {
            for (int i = 0; ls != null && i < ls.length() && n < MAX; i++) {
                JSONObject l = ls.getJSONObject(i);
                String h = l.optString("h", "");
                String t = l.optString("t", "");
                String c = l.optString("c", "");
                boolean hecha = l.optBoolean("hecha", false);
                int tid = l.optInt("id", 0); /* v1.9.3: para el check del widget */
                if (h == null) h = "";
                if (t == null) t = "";
                if (t.length() == 0) continue;
                boolean vencida = "\u26a0".equals(h);
                int colTipo = acento;
                if (vencida) colTipo = COLOR_VENCIDA;
                else if (c != null && c.length() > 0) {
                    try { colTipo = Color.parseColor(c); } catch (Exception e) { colTipo = acento; }
                }
                RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.cdp_widget_row);
                r.setTextViewText(R.id.cdp_row_dot, "\u25cf");
                r.setInt(R.id.cdp_row_dot, "setTextColor", hecha ? COLOR_CHECK_OFF : colTipo);
                r.setTextViewText(R.id.cdp_row_hora, vencida ? "\u26a0" : h);
                r.setInt(R.id.cdp_row_hora, "setTextColor", vencida ? COLOR_VENCIDA : acento);
                r.setTextViewText(R.id.cdp_row_tit, t);
                r.setInt(R.id.cdp_row_tit, "setTextColor", hecha ? COLOR_TXT2 : COLOR_TXT);
                r.setInt(R.id.cdp_row_tit, "setPaintFlags",
                        (hecha ? Paint.STRIKE_THRU_TEXT_FLAG : 0) | Paint.ANTI_ALIAS_FLAG);
                /* v1.9.3: check PREMIUM y TOCABLE - circulo vacio (marcar) o check verde (desmarcar) */
                r.setTextViewText(R.id.cdp_row_chk, hecha ? "\u2713" : "\u25ef");
                r.setInt(R.id.cdp_row_chk, "setTextColor", hecha ? COLOR_OK : COLOR_CHECK_OFF);
                if (tid > 0) {
                    try {
                        Intent ti = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
                        if (ti != null) {
                            ti.putExtra("cdp_toggle", String.valueOf(tid));
                            PendingIntent pl = PendingIntent.getActivity(ctx, 83000 + tid, ti,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                            r.setOnClickPendingIntent(R.id.cdp_row_chk, pl);
                        }
                    } catch (Exception ignored) { }
                }
                v.addView(R.id.cdp_widget_fila, r);
                n++;
            }
        } catch (Exception e) { n = 0; }

        if (n == 0) {
            v.setViewVisibility(R.id.cdp_widget_fila, android.view.View.GONE);
            v.setViewVisibility(R.id.cdp_widget_vacio, android.view.View.VISIBLE);
        } else {
            v.setViewVisibility(R.id.cdp_widget_fila, android.view.View.VISIBLE);
            v.setViewVisibility(R.id.cdp_widget_vacio, android.view.View.GONE);
        }
        try {
            Intent ai = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (ai != null) {
                PendingIntent pi = PendingIntent.getActivity(ctx, 42, ai,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                v.setOnClickPendingIntent(R.id.cdp_widget_root, pi);
            }
        } catch (Exception e) { /* sin gesto de apertura, el widget sigue pintando */ }
        mgr.updateAppWidget(ids, v);
    }
}
