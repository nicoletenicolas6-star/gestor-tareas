package com.tareas.notif;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/* Punto 14.1: recibe ACTION_SEND (text/plain) de cualquier app (Gmail, WhatsApp,
   el navegador...), guarda el texto y abre la app. Sin internet y sin cuenta. */
public class ShareActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent i = getIntent();
            if (i != null && Intent.ACTION_SEND.equals(i.getAction())
                    && i.getType() != null && i.getType().contains("text/")) {
                String txt = i.getStringExtra(Intent.EXTRA_TEXT);
                if (txt != null && txt.trim().length() > 0) {
                    Notificaciones.textoShare = txt.trim();
                }
            }
        } catch (Throwable ignored) { }
        try {
            Intent lap = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (lap != null) {
                lap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(lap);
            }
        } catch (Throwable ignored) { }
        finish();
    }
}
