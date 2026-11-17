package com.tareas.notif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Las alarmas no sobreviven a un reinicio: se reprograman desde la persistencia.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Notificaciones.reprogramar(ctx);
        }
    }
}
