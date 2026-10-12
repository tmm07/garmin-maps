using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Application;

// Anteprima percorso: tutto il tragitto sulla mappa, tempo e distanza, START per partire
class PreviewView extends WatchUi.View {
    var route;
    var layer;

    function initialize(r) {
        View.initialize();
        route = r;
        layer = new MapLayer(15, r["id"], true);
    }

    function fmtTime(secs) {
        var m = (secs / 60.0).toNumber();
        if (m < 60) { return m + " min"; }
        return (m / 60) + " h " + (m % 60) + " min";
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        layer.update(dc, 1.0, 1.0);      // coordinate irrilevanti: in modalita' fit decide il server
        if (!layer.draw(dc, null, null)) {
            dc.drawText(w / 2, h / 2, Graphics.FONT_SMALL, layer.failed ? "Mappa non disponibile" : "Carico mappa...", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.fillRectangle(0, h * 0.74, w, h * 0.26);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.80, Graphics.FONT_SMALL, fmtTime(route["t"]) + " · " + Geo.fmt(route["d"]), Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.90, Graphics.FONT_SMALL, "START: parti", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

class PreviewDelegate extends WatchUi.BehaviorDelegate {
    var view;

    function initialize(v) {
        BehaviorDelegate.initialize();
        view = v;
    }

    function onSelect() {
        var app = Application.getApp();
        var nav = new NavView(view.route);
        app.nav = nav;
        WatchUi.switchToView(nav, new NavDelegate(nav), WatchUi.SLIDE_LEFT);
        return true;
    }
}
