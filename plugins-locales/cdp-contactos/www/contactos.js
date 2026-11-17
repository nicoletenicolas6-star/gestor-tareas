var exec = require('cordova/exec');

function call(name, args, ok, fail) {
    exec(ok, fail, 'Contactos', name, args || []);
}

// listar -> ok([{nombre:"Ana", tel:"+53 5555 5555"}, ...]) uno por cada telefono del contacto
module.exports = {
    listar: function (ok, fail) { call('listar', [], ok, fail); },
    pedirPermiso: function (ok, fail) { call('pedirPermiso', [], ok, fail); },
    tienePermiso: function (ok, fail) { call('tienePermiso', [], ok, fail); },
    abrirAjustes: function (ok, fail) { call('abrirAjustes', [], ok, fail); }
};
