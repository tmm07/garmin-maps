using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Attention;
using Toybox.Math;
using Toybox.Application;

// Codici manovra (stessi del backend): 0 dritto, 1 sx, 2 dx, 3 slight sx, 4 slight dx,
// 5 sharp sx, 6 sharp dx, 7 inversione, 8 rotonda, 9 arrivo
class NavView extends WatchUi.View {
    const ANGLES = [0, -90, 90, -45, 45, -135, 135, 180, 0, 0];
    const ARROW = [[0, -34], [24, -6], [9, -6], [9, 34], [-9, 34], [-9, -6], [-24, -6]];

    var steps;
    var pts;
    var totalDist;
    var hint;
    var idx = 0;
    var distToStep = null;
    var minD = 999999;
    var page = 0;       // 0 = frecce, 1 = mappa
    var curLat = null;
    var curLng = null;
    var arrived = false;
    var layer = null;

    function initialize(route) {
        View.initialize();
        var m = Screens.modeIndex();
        layer = new MapLayer(m == 1 ? 16 : 17, null, false);
        setRoute(route);
    }

    function setRoute(route) {
        steps = route["s"];
        pts = route["p"];
        totalDist = route["d"];
        hint = route["h"];
        layer.rid = route["id"];
        layer.invalidate();
        idx = 0;
        minD = 999999;
        distToStep = null;
        arrived = false;
    }

    function buzz(long) {
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(100, long ? 600 : 250)]);
        }
    }

    // Chiamata a ogni fix GPS
    function tick(la, ln) {
        curLat = la;
        curLng = ln;
        if (arrived) { return; }
        var s = steps[idx];
        var d = Geo.dist(la, ln, s[2], s[3]);
        distToStep = d;
        if (d < minD) { minD = d; }

        var last = (idx == steps.size() - 1);
        if (d < (last ? 20 : 25)) {
            if (last) {
                arrived = true;
                buzz(true);
            } else {
                idx++;
                minD = 999999;
                distToStep = Geo.dist(la, ln, steps[idx][2], steps[idx][3]);
                buzz(false);
            }
        } else if (d > minD + 100) {
            Application.getApp().reroute();
        }
    }

    function onUpdate(dc) {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        if (page == 0) { drawMapPage(dc); } else { drawTurn(dc); }
    }

    function drawArrow(dc, man, cx, cy, k, color) {
        if (man == 9 || man == 8) {
            dc.setColor(color, Graphics.COLOR_TRANSPARENT);
            dc.setPenWidth(5);
            dc.drawCircle(cx, cy, 24 * k);
            dc.setPenWidth(1);
            return;
        }
        var a = ANGLES[man] * Math.PI / 180.0;
        var c = Math.cos(a);
        var sn = Math.sin(a);
        var poly = [];
        for (var i = 0; i < ARROW.size(); i++) {
            var x = ARROW[i][0] * k;
            var y = ARROW[i][1] * k;
            poly.add([cx + x * c - y * sn, cy + x * sn + y * c]);
        }
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        dc.fillPolygon(poly);
    }

    // Mappa Google a schermo intero + banner con la prossima svolta (come Apple Watch)
    function drawMapPage(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        layer.update(dc, curLat, curLng);
        if (curLat != null && layer.draw(dc, curLat, curLng)) {
            MapLayer.dot(dc);
        } else if (curLat != null && layer.failed) {
            drawMap(dc);                      // senza rete: mappa vettoriale semplificata
        } else {
            dc.drawText(w / 2, h / 2, Graphics.FONT_SMALL, curLat == null ? "Attendo GPS..." : "Carico mappa...", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
        var man = arrived ? 9 : steps[idx][0];
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.fillRectangle(0, 0, w, h * 0.30);
        drawArrow(dc, man, w * 0.27, h * 0.15, 0.55, Graphics.COLOR_GREEN);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var dtxt = arrived ? "Arrivo" : ((distToStep == null) ? "--" : Geo.fmt(distToStep));
        dc.drawText(w * 0.42, h * 0.10, Graphics.FONT_MEDIUM, dtxt, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        var t = arrived ? "Sei arrivato" : steps[idx][1];
        if (t.length() > 16) { t = t.substring(0, 15) + "."; }
        dc.drawText(w * 0.42, h * 0.21, Graphics.FONT_XTINY, t, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    function drawTurn(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var s = steps[idx];
        var man = arrived ? 9 : s[0];
        var cx = w / 2;
        var cy = h * 0.27;

        drawArrow(dc, man, cx, cy, 1.0, Graphics.COLOR_BLUE);

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        var dtxt = (distToStep == null) ? "--" : Geo.fmt(distToStep);
        dc.drawText(cx, h * 0.45, Graphics.FONT_NUMBER_MILD, arrived ? "Arrivo" : dtxt, Graphics.TEXT_JUSTIFY_CENTER);

        var text = arrived ? "Sei arrivato" : ((idx == 0 && distToStep == null) ? hint : s[1]);
        var lines = Geo.wrap(text, 18);
        var lh = dc.getFontHeight(Graphics.FONT_TINY);
        for (var i = 0; i < lines.size() && i < 3; i++) {
            dc.drawText(cx, h * 0.65 + i * lh, Graphics.FONT_TINY, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    // Mappa semplice orientata a nord, centrata su di te
    function drawMap(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        if (curLat == null) {
            dc.drawText(w / 2, h / 2, Graphics.FONT_SMALL, "Attendo GPS", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }
        var range = distToStep != null ? distToStep * 1.5 : 300;
        if (range < 200) { range = 200; }
        var scale = (w * 0.45) / range;                      // px per metro
        var kx = 111320.0 * Math.cos(curLat * Math.PI / 180.0);
        var ky = 111320.0;

        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_BLACK);
        dc.setPenWidth(4);
        var px = null;
        var py = null;
        for (var i = 0; i + 1 < pts.size(); i += 2) {
            var x = w / 2 + (pts[i + 1] - curLng) * kx * scale;
            var y = h / 2 - (pts[i] - curLat) * ky * scale;
            if (px != null) { dc.drawLine(px, py, x, y); }
            px = x;
            py = y;
        }
        dc.setPenWidth(1);

        var s = steps[idx];                                   // prossima manovra
        var mx = w / 2 + (s[3] - curLng) * kx * scale;
        var my = h / 2 - (s[2] - curLat) * ky * scale;
        dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_BLACK);
        dc.fillCircle(mx, my, 5);

        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_BLACK);
        dc.fillCircle(w / 2, h / 2, 6);
    }
}

class NavDelegate extends WatchUi.BehaviorDelegate {
    var view;

    function initialize(v) {
        BehaviorDelegate.initialize();
        view = v;
    }

    // START: mappa <-> freccia grande. SU/GIU: zoom
    function onSelect() {
        view.page = 1 - view.page;
        WatchUi.requestUpdate();
        return true;
    }

    function onNextPage() { view.layer.setZoom(view.layer.z - 1); return true; }
    function onPreviousPage() { view.layer.setZoom(view.layer.z + 1); return true; }

    function onBack() {
        Application.getApp().nav = null;
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }
}
