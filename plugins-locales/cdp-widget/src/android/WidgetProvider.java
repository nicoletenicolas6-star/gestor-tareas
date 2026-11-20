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
 * Widget de la agenda: cabecera con fecha y una fila por tarea
 * (hora en acento, titulo, y check/tachado si esta hecha).
 * El color de acento lo manda la app en el JSON (campo "acento");
 * la app le pasa los datos por el plugin CdpWidget (SharedPreferences);
 * al tocar el widget se abre la aplicacion.
 */
public class WidgetProvider extends AppWidgetProvider {

    private static final int MAX = 8;
    private static final int ACENTO_DEF = 0xFF1E5AE8;
    private static final int COLOR_TXT = 0xFF1B1B1B;
    private static final int COLOR_TXT2 = 0xFF6B7280;
    private static final int COLOR_VENCIDA = 0xFFC0392B;
    private static final int COLOR_CHECK_OFF = 0xFF9AA0A6;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        pintar(ctx, mgr, ids);
    }

    public static void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn = new ComponentName(ctx, WidgetProvider.class);
        pintar(ctx, mgr, mgr.getAppWidgetIds(cn));
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
                boolean hecha = l.optBoolean("hecha", false);
                if (h == null) h = "";
                if (t == null) t = "";
                if (t.length() == 0) continue;
                boolean vencida = "\u26a0".equals(h);
                RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.cdp_widget_row);
                r.setTextViewText(R.id.cdp_row_hora, h);
                r.setInt(R.id.cdp_row_hora, "setTextColor", vencida ? COLOR_VENCIDA : acento);
                r.setTextViewText(R.id.cdp_row_tit, t);
                r.setInt(R.id.cdp_row_tit, "setTextColor", COLOR_TXT);
                r.setInt(R.id.cdp_row_tit, "setPaintFlags",
                        (hecha ? Paint.STRIKE_THRU_TEXT_FLAG : 0) | Paint.ANTI_ALIAS_FLAG);
                r.setTextViewText(R.id.cdp_row_chk, hecha ? "\u2713" : "\u25cb");
                r.setInt(R.id.cdp_row_chk, "setTextColor", hecha ? acento : COLOR_CHECK_OFF);
                v.addView(R.id.cdp_widget_fila, r);
                n++;
        }
        } catch (Exception e) { n = 0; }

        if (n == 0) {
            v.setViewVisibility(R.id.cdp_widget_fila, android.view.View.GONE);
            v.setViewVisibility(R.id.cdp_widget_vacio, android.view.View.VISIBLE);
            v.setTextViewText(R.id.cdp_widget_vacio, "No hay tareas para hoy.\nToca para abrir la agenda.");
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
