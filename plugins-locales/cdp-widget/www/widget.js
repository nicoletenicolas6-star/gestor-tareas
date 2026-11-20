var exec = require('cordova/exec');

var cdpWidget = {
    actualizar: function (json, ok, fail) { exec(ok, fail, 'CdpWidget', 'actualizar', [String(json || '')]); }
};

module.exports = cdpWidget;
