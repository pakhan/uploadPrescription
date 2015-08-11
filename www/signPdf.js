module.exports = {
    signPdf: function(pk, chain, successCallback) {
        cordova.exec(successCallback,null, "UploadPrescrition", "signPdf", [pk, chain]);
    }
};
