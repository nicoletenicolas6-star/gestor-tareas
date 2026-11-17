/*global cordova*/
// Plugin SAF cargado como script directo desde index.html (no por cordova_plugins.js):
// evita el doble envoltorio cordova.define que rompia el bootstrap del arranque.
// Expone los metodos en cordova.plugins.safMediastore (clobber manual).
(() => {
    'use strict';

    function callPromise(name) {
        return function (...params) {
            return new Promise((resolve, reject) => {
                window.cordova.exec(resolve, reject, 'SafMediastore', name, params);
            });
        };
    }

    const api = {};
    [
        'selectFolder',
        'selectFile',
        'openFolder',
        'openFile',
        'readFile',
        'writeFile',
        'overwriteFile',
        'saveFile',
        'deleteFile',
        'getFileName',
        'getUri',
        'findDownloadsFile',
        'tieneAccesoTodosArchivos',
        'solicitarAccesoTodosArchivos',
        'writeLegacyFile',
        'readLegacyFile'
    ].forEach(name => { api[name] = callPromise(name); });

    if (window.cordova) {
        window.cordova.plugins = window.cordova.plugins || {};
        window.cordova.plugins.safMediastore = api;
    }
    window.SafMediastore = api;
})();