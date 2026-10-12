using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Math;
using Toybox.Time;

// Immagine di mappa Google scaricata dal server. Tra un download e l'altro
// viene fatta scorrere in base al movimento reale, cosi' sembra una mappa continua.
class MapLayer {
    var bmp = null;
    var cLat = 0.0;       // centro dell'immagine
    var cLng = 0.0;
    var z = 16;
    var rid = null;       // percorso da disegnare (null = solo mappa)
    var fit = false;      // true = inquadra tutto il percorso, niente scorrimento
    var busy = false;
    var failed = false;
    var lastT = 0;
    var reqZ = 0;
    var size = 240;

    function initialize(zoom, routeId, fitRoute) {
        z = zoom;
        rid = routeId;
        fit = fitRoute;
    }

    function invalidate() {
        bmp = null;
        lastT = 0;
    }

    function setZoom(nz) {
        if (nz < 13) { nz = 13; }
        if (nz > 19) { nz = 19; }
        z = nz;
        WatchUi.requestUpdate();
    }

    function mpp() {
        return 156543.03392 * Math.cos(cLat * Math.PI / 180.0) / Math.pow(2, reqZ);
    }

    function request(lat, lng) {
        busy = true;
        lastT = Time.now().value();
        reqZ = z;
        var p = {"z" => z};
        if (fit) {
            p["fit"] = 1;
        } else {
            p["lat"] = lat;
            p["lng"] = lng;
        }
        if (rid != null) {
            p["rid"] = rid;
        }
        cLat = lat;
        cLng = lng;
        MapApi.fetch(p, size, method(:onImage));
    }

    function onImage(code, data) {
        busy = false;
        if (code == 200 && data != null) {
            bmp = data;
            failed = false;
        } else {
            failed = true;
        }
        WatchUi.requestUpdate();
    }

    // Decide se scaricare una nuova immagine
    function update(dc, lat, lng) {
        size = MapApi.px(dc);
        if (busy || lat == null) { return; }
        var now = Time.now().value();
        if (bmp == null) {
            if (!failed || now - lastT > 15) { request(lat, lng); }
            return;
        }
        if (fit) { return; }
        var moved = Geo.dist(lat, lng, cLat, cLng);
        var limit = size * 0.3 * mpp();
        if (z != reqZ || moved > limit || (moved > 15 && now - lastT > 30)) {
            request(lat, lng);
        }
    }

    // true se ha disegnato la mappa
    function draw(dc, lat, lng) {
        if (bmp == null) { return false; }
        var w = dc.getWidth();
        var h = dc.getHeight();
        var x = w / 2 - bmp.getWidth() / 2;
        var y = h / 2 - bmp.getHeight() / 2;
        if (!fit && lat != null) {
            var m = mpp();
            x += (cLng - lng) * 111320.0 * Math.cos(lat * Math.PI / 180.0) / m;
            y -= (cLat - lat) * 111320.0 / m;
        }
        dc.drawBitmap(x, y, bmp);
        return true;
    }

    static function dot(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(w / 2, h / 2, 9);
        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(w / 2, h / 2, 6);
    }
}
