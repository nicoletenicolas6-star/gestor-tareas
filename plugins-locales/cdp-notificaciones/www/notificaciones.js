var exec = require('cordova/exec');

// notifs: array de {id, titulo, texto, ts} (ts = epoch ms). El plugin cancela
// los previos, programa los futuros y los persiste (para reprogramar en boot).
function call(name, args, ok, fail) {
    exec(ok, fail, 'Notificaciones', name, args || []);
}
module.exports = {
    sincronizar: function (notifs, ok, fail) { call('sincronizar', [notifs || []], ok, fail); },
    pedirPermiso: function (ok, fail) { call('pedirPermiso', [], ok, fail); },
    pedirMicro: function (ok, fail) { call('pedirMicro', [], ok, fail); },
    estado: function (ok, fail) { call('estado', [], ok, fail); },
    abrirAjustes: function (tipo, ok, fail) { call('abrirAjustes', [tipo || 'notif'], ok, fail); },
    mostrar: function (id, titulo, texto, ok, fail) { call('mostrar', [id, titulo, texto], ok, fail); },
    probar: function (ok, fail) { call('probar', [], ok, fail); },
    alTocar: function (ok, fail) { call('alTocar', [], ok, fail); },
    grabarIniciar: function (ok, fail) { call('grabarIniciar', [], ok, fail); },
    grabarParar: function (ok, fail) { call('grabarParar', [], ok, fail); },
    grabarCancelar: function (ok, fail) { call('grabarCancelar', [], ok, fail); },
    whatsappTexto: function (tel, texto, ok, fail) { call('whatsappTexto', [tel || '', texto || ''], ok, fail); },
    whatsappAudio: function (tel, texto, audio, ok, fail) { call('whatsappAudio', [tel || '', texto || '', audio || ''], ok, fail); },
    /* calendario del telefono: puente entre la app y Muse / otras apps */
    calPermisos: function (ok, fail) { call('calPermisos', [], ok, fail); },
    calInsertar: function (ev, ok, fail) { call('calInsertar', [ev || {}], ok, fail); },
    calBorrar: function (evId, ok, fail) { call('calBorrar', [evId], ok, fail); },
    /* desde/hasta en milisegundos; devuelve [{evId,titulo,desc,fecha,hora,allDay,inicio,mio}] */
    calEventos: function (desde, hasta, ok, fail) { call('calEventos', [desde, hasta], ok, fail); }
};
