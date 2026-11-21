package com.tareas.notif;

import android.content.Context;
import android.content.Intent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;

/**
 * Guarda los datos de las tareas de hoy (que le envia la app por JS) y
 * refresca el widget de la pantalla principal.
 * v1.9.3: el widget manda el toque en el check de una fila como
 * cdp_toggle=<idTarea> (extra del intent de arranque). La app lo lee con la
 * accion "intent" (arranque en frio: getIntent(); app ya viva: onNewIntent).
 */
public class CdpWidget extends CordovaPlugin {

    private static volatile String togglePendiente = "";

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext cb) throws JSONException {
        if ("actualizar".equals(action)) {
            String json = "";
            try { json = args.optString(0); } catch (Exception ignored) { }
            final Context ctx = cordova.getActivity().getApplicationContext();
            try {
                ctx.getSharedPreferences("cdp_widget", Context.MODE_PRIVATE)
                        .edit().putString("datos", json).apply();
                WidgetProvider.refresh(ctx);
                cb.success(String.valueOf(System.currentTimeMillis()));
            } catch (Exception e) {
                cb.error(e.getMessage() == null ? "widget" : e.getMessage());
            }
            return true;
        }
        if ("intent".equals(action)) {
            String v = togglePendiente;
            togglePendiente = "";
            if (v == null || v.length() == 0) {
                try {
                    Intent it = cordova.getActivity() != null ? cordova.getActivity().getIntent() : null;
                    if (it != null) {
                        String ex = it.getStringExtra("cdp_toggle");
                        if (ex != null && ex.length() > 0) { v = ex; it.removeExtra("cdp_toggle"); }
                    }
                } catch (Exception ignored) { }
            }
            cb.success(v == null ? "" : v);
            return true;
        }
        return false;
    }

    @Override
    public void onNewIntent(Intent intent) {
        try {
            if (intent != null) {
                String ex = intent.getStringExtra("cdp_toggle");
                if (ex != null && ex.length() > 0) {
                    togglePendiente = ex;
                    intent.removeExtra("cdp_toggle");
                }
            }
        } catch (Exception ignored) { }
        super.onNewIntent(intent);
    }
}
