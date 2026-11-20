package com.tareas.notif;

import android.content.Context;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;

/**
 * Guarda los datos de las tareas de hoy (que le envia la app por JS) y
 * refresca el widget de la pantalla principal.
 */
public class CdpWidget extends CordovaPlugin {

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext cb) throws JSONException {
        if ("actualizar".equals(action)) {
            final String json = args.optString(0, "");
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
        return false;
    }
}
