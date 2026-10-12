using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Application;
using Toybox.Math;
using Toybox.Time;

// Codici manovra: 0 dritto, 1 sx, 2 dx, 3 slight sx, 4 slight dx, 5 sharp sx, 6 sharp dx,
// 7 inversione, 8 rotonda, 9 arrivo, 10 sconosciuto
class MapView extends WatchUi.View {
    const ANGLES = [0, -90, 90, -45, 45, -135, 135, 180, 0, 0, 0];
    const ARROW = [[0, -34], [24, -6], [9, -6], [9, 34], [-9, 34], [-9, -6], [-24, -6]];
    const RADII = [100.0, 200.0, 400.0];     // metri visibili dal centro al bordo
    var zoom = 1;
    var page = 0;                             // 0 = mappa, 1 = freccia grande

    function initialize() { View.initialize(); }

    function onUpdate(dc) {
        var m = Application.getApp().model;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_WHITE);
        dc.clear();
        if (page == 1 && m.navOn) {
            drawTurnPage(dc, m);
        } else {
            drawMapPage(dc, m);
        }
    }

    function drawArrow(dc, man, cx, cy, k, color) {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        if (man == 9 || man == 8) {
            dc.setPenWidth(5);
            dc.drawCircle(cx, cy, 24 * k);
            dc.setPenWidth(1);
            return;
        }
        var a = ANGLES[man] * Math.PI / 180.0;
        var c = Math.cos(a);
        var s = Math.sin(a);
        var poly = [];
        for (var i = 0; i < ARROW.size(); i++) {
            var x = ARROW[i][0] * k;
            var y = ARROW[i][1] * k;
            poly.add([cx + x * c - y * s, cy + x * s + y * c]);
        }
        dc.fillPolygon(poly);
    }

    // Mappa orientata nel verso di marcia, tu sei il triangolo blu
    function drawMapPage(dc, m) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var ux = w / 2;
        var uy = h * 0.62;
        var scale = (w * 0.5) / RADII[zoom];

        if (m.havePos) {
            var hr = m.hdg * Math.PI / 180.0;
            var ch = Math.cos(hr);
            var sh = Math.sin(hr);
            for (var i = 0; i < m.ways.size(); i++) {
                var way = m.ways[i];
                var pts = way[1];
                if (way[0] == 1) {
                    dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
                    dc.setPenWidth(zoom == 0 ? 6 : 4);
                } else {
                    dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
                    dc.setPenWidth(zoom == 0 ? 3 : 2);
                }
                var lx = 0.0;
                var ly = 0.0;
                for (var k = 0; k + 1 < pts.size(); k += 2) {
                    var dx = pts[k] - m.px;
                    var dy = pts[k + 1] - m.py;
                    var sx = ux + (dx * ch - dy * sh) * scale;     // verso destra
                    var sy = uy - (dx * sh + dy * ch) * scale;     // verso l'alto = direzione di marcia
                    if (k > 0) {
                        dc.drawLine(lx, ly, sx, sy);
                    }
                    lx = sx;
                    ly = sy;
                }
            }
            dc.setPenWidth(1);
            var me = [[ux, uy - 14], [ux + 10, uy + 11], [ux, uy + 5], [ux - 10, uy + 11]];
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(ux, uy, 15);
            dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
            dc.fillPolygon(me);
        } else {
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
            var msg = (m.lastMsg == 0) ? "Avvia l'app sul telefono" : "Carico la mappa...";
            dc.drawText(w / 2, h / 2, Graphics.FONT_SMALL, msg, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        if (m.navOn) {
            drawBanner(dc, m);
        }
        if (m.lastMsg != 0 && Time.now().value() - m.lastMsg > 25) {
            dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.88, Graphics.FONT_XTINY, "Telefono non risponde", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        } else if (m.navOn && m.eta.length() > 0) {
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_WHITE);
            dc.drawText(w / 2, h * 0.90, Graphics.FONT_XTINY, m.eta, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
    }

    // Banner come su Apple Watch: freccia, distanza, via
    function drawBanner(dc, m) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.fillRectangle(0, 0, w, h * 0.30);
        drawArrow(dc, m.man, w * 0.27, h * 0.15, 0.55, Graphics.COLOR_GREEN);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w * 0.42, h * 0.10, Graphics.FONT_MEDIUM, m.dist, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        var t = m.instr;
        if (t.length() > 16) { t = t.substring(0, 15) + "."; }
        dc.drawText(w * 0.42, h * 0.21, Graphics.FONT_XTINY, t, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // Freccia a tutto schermo con il testo completo
    function drawTurnPage(dc, m) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        drawArrow(dc, m.man, w / 2, h * 0.27, 1.0, Graphics.COLOR_BLUE);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.45, Graphics.FONT_NUMBER_MILD, m.dist, Graphics.TEXT_JUSTIFY_CENTER);
        var lines = wrap(m.instr, 18);
        var lh = dc.getFontHeight(Graphics.FONT_TINY);
        for (var i = 0; i < lines.size() && i < 3; i++) {
            dc.drawText(w / 2, h * 0.64 + i * lh, Graphics.FONT_TINY, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
        }
        if (m.eta.length() > 0) {
            dc.drawText(w / 2, h * 0.90, Graphics.FONT_XTINY, m.eta, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
    }

    function wrap(s, n) {
        var lines = [];
        var line = "";
        while (s.length() > 0) {
            var sp = s.find(" ");
            var word = (sp == null) ? s : s.substring(0, sp);
            s = (sp == null) ? "" : s.substring(sp + 1, s.length());
            if (line.length() > 0 && line.length() + 1 + word.length() > n) {
                lines.add(line);
                line = word;
            } else {
                line = (line.length() == 0) ? word : (line + " " + word);
            }
        }
        if (line.length() > 0) {
            lines.add(line);
        }
        return lines;
    }
}

class MapDelegate extends WatchUi.BehaviorDelegate {
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

    function onNextPage() {
        if (view.zoom < 2) { view.zoom++; }
        WatchUi.requestUpdate();
        return true;
    }

    function onPreviousPage() {
        if (view.zoom > 0) { view.zoom--; }
        WatchUi.requestUpdate();
        return true;
    }
}
