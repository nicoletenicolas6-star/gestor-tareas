package com.tareas.contactos;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class Contactos extends CordovaPlugin {

    private static final int REQ_CONTACTS = 21;
    /* filas por consulta: en agendas grandes una consulta sola revienta el
       CursorWindow ("Row too big to fit into CursorWindow"), asi que se pagina
       por _ID en lotes y no se pierde ni un contacto */
    private static final int LOTE = 400;

    private CallbackContext permCb;

    @Override
    public boolean execute(String accion, CordovaArgs args, final CallbackContext cb) throws JSONException {
        if ("pedirPermiso".equals(accion)) {
            if (cordova.hasPermission(Manifest.permission.READ_CONTACTS)) { cb.success("otorgado"); return true; }
            permCb = cb;
            cordova.requestPermission(this, REQ_CONTACTS, Manifest.permission.READ_CONTACTS);
            return true;
        }
        if ("tienePermiso".equals(accion)) {
            cb.success(cordova.hasPermission(Manifest.permission.READ_CONTACTS) ? "otorgado" : "denegado");
            return true;
        }
        if ("abrirAjustes".equals(accion)) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + cordova.getActivity().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                cordova.getActivity().startActivity(i);
                cb.success("ok");
            } catch (Throwable e) { cb.error(msg(e)); }
            return true;
        }
        if ("listar".equals(accion)) {
            if (!cordova.hasPermission(Manifest.permission.READ_CONTACTS)) { cb.error("sin permiso de contactos"); return true; }
            cordova.getThreadPool().execute(new Runnable() {
                @Override public void run() {
                    try { cb.success(listar()); }
                    catch (Throwable e) { cb.error(msg(e)); }
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode != REQ_CONTACTS) return;
        CallbackContext cb = permCb;
        permCb = null;
        if (cb == null) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        cb.success(ok ? "otorgado" : "denegado");
    }

    /* Una fila por cada telefono: {nombre, tel, tipo}.
       Ademas se anaden los contactos SIN telefono (tel vacio) para que la lista
       de la app sea la agenda COMPLETA. Sin limites de filas. */
    private JSONArray listar() throws JSONException {
        JSONArray out = new JSONArray();
        ContentResolver cr = cordova.getActivity().getContentResolver();
        Set<String> vistos = new HashSet<String>();
        Set<Long> conTel = new HashSet<Long>();

        /* 1) todos los numeros de telefono de la agenda */
        String[] proy = {
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
        };
        long ultimo = -1L;
        while (true) {
            Cursor c = null;
            long ultimoLote = ultimo;
            int filas = 0;
            try {
                c = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proy,
                        ContactsContract.CommonDataKinds.Phone._ID + " > ?",
                        new String[]{String.valueOf(ultimo)},
                        ContactsContract.CommonDataKinds.Phone._ID + " ASC");
                if (c == null) break;
                while (c.moveToNext()) {
                    filas++;
                    long id = c.getLong(0);
                    ultimoLote = id;
                    long cid = c.getLong(1);
                    String nombre = c.getString(2) == null ? "" : c.getString(2).trim();
                    String tel = c.getString(3) == null ? "" : c.getString(3).trim();
                    int tipo = c.getInt(4);
                    if (!tel.isEmpty()) conTel.add(cid);
                    if (tel.isEmpty()) continue;
                    String clave = (nombre + "|" + tel).toLowerCase();
                    if (!vistos.add(clave)) continue;
                    JSONObject o = new JSONObject();
                    o.put("nombre", nombre.isEmpty() ? tel : nombre);
                    o.put("tel", tel);
                    o.put("tipo", tipoTel(tipo));
                    out.put(o);
                }
            } finally {
                if (c != null) c.close();
            }
            if (filas == 0) break;   /* no hay mas lotes */
            ultimo = ultimoLote;
        }

        /* 2) contactos sin ningun telefono: tambien se listan (tel vacio) */
        String[] proyC = {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME
        };
        ultimo = -1L;
        while (true) {
            Cursor c = null;
            long ultimoLote = ultimo;
            int filas = 0;
            try {
                c = cr.query(ContactsContract.Contacts.CONTENT_URI, proyC,
                        ContactsContract.Contacts._ID + " > ?",
                        new String[]{String.valueOf(ultimo)},
                        ContactsContract.Contacts._ID + " ASC");
                if (c == null) break;
                while (c.moveToNext()) {
                    filas++;
                    long id = c.getLong(0);
                    ultimoLote = id;
                    if (conTel.contains(id)) continue;
                    String nombre = c.getString(1) == null ? "" : c.getString(1).trim();
                    if (nombre.isEmpty()) continue;
                    String clave = ("sin|" + nombre).toLowerCase();
                    if (!vistos.add(clave)) continue;
                    JSONObject o = new JSONObject();
                    o.put("nombre", nombre);
                    o.put("tel", "");
                    o.put("tipo", "sin");
                    out.put(o);
                }
            } finally {
                if (c != null) c.close();
            }
            if (filas == 0) break;
            ultimo = ultimoLote;
        }

        return out;
    }

    private static String tipoTel(int tipo) {
        switch (tipo) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE: return "movil";
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
            case ContactsContract.CommonDataKinds.Phone.TYPE_MAIN:
            case ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN: return "fijo";
            default: return "otro";
        }
    }

    private static String msg(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
