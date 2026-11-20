package com.tareas.notif;

import com.mitienda.pos.tareas.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Widget simple de agenda: una lista de tareas de hoy en texto plano.
 * La app le pasa los datos por el plugin CdpWidget (SharedPreferences);
 * al tocar el widget se abre la application.
 */
public class WidgetProvider extends AppWidgetProvider {

    private static final int MAX = 8;

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
        StringBuilder sb = new StringBuilder();
        String tit = "Organizador";
        try {
            if (raw.length() > 0) {
                JSONObject o = new JSONObject(raw);
                String c = o.optString("cuando", "");
                if (c.length() > 0) tit = c;
                JSONArray ls = o.optJSONArray("lineas");
                if (ls != null) {
                    for (int i = 0; i < ls.length() && i < MAX; i++) {
                        JSONObject l = ls.getJSONObject(i);
                        sb.append(l.optString("h", "")).append("  ").append(l.optString("t", "")).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.setLength(0);
        }
        if (sb.length() == 0) sb.append("No hay tareas para hoy.\nToca para abrir la agenda.");
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.cdp_widget);
        v.setTextViewText(R.id.cdp_widget_tit, tit);
        v.setTextViewText(R.id.cdp_widget_txt, sb.toString().trim());
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
