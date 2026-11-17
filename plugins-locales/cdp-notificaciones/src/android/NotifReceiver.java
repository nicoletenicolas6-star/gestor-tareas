package com.tareas.notif;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotifReceiver extends BroadcastReceiver {

    private static final String CANAL = "tareas";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int id = intent.getIntExtra("id", (int) System.currentTimeMillis());
        String titulo = intent.getStringExtra("titulo");
        String texto = intent.getStringExtra("texto");
        mostrar(ctx, id, titulo, texto);
    }

    static void mostrar(Context ctx, int id, String titulo, String texto) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Recordatorios de tareas",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos de tareas y recordatorios");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(titulo == null || titulo.length() == 0 ? "Tarea" : titulo)
                .setContentText(texto == null ? "" : texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto == null ? "" : texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        Intent abrir = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (abrir != null) {
            int tarea = (id % 10 == 1 || id % 10 == 2) ? id / 10 : 0;
            abrir.putExtra("tarea", tarea);
            abrir.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, id, abrir,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentIntent(pi);
        }
        try { nm.notify(id, b.build()); } catch (SecurityException ignored) { }
    }
}
