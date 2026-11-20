/* puente JS del actualizador: buscar release nueva, descargar con progreso e instalar */
var exec = require('cordova/exec');

var cdpActualizador = {
  /* cb recibe un string JSON {tag, apk, nombre} o '' si no hay/da error */
  buscar: function (cb, fail) {
    exec(function (res) { if (cb) cb(res); }, function (e) { if (fail) fail(e); },
      'CdpActualizador', 'buscar', []);
  },
  /* win se llama varias vezes con '{"p":PCT}' y al final '{"fin":1,"uri":"..."}' */
  descargar: function (url, win, fail) {
    exec(function (res) { if (win) win(res); }, function (e) { if (fail) fail(e); },
      'CdpActualizador', 'descargar', [url || '']);
  },
  instalar: function (uri, cb, fail) {
    exec(function (res) { if (cb) cb(res); }, function (e) { if (fail) fail(e); },
      'CdpActualizador', 'instalar', [uri || '']);
  }
};

module.exports = cdpActualizador;
