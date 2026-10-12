using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Application;

// Schermata iniziale come Apple Maps: mappa attorno a te, START apre il menu
class HomeView extends WatchUi.View {
    var layer = new MapLayer(16, null, false);

    function initialize() { View.initialize(); }

    function onUpdate(dc) {
        var app = Application.getApp();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var w = dc.getWidth();
        var h = dc.getHeight();
        if (app.lat == null) {
            dc.drawText(w / 2, h / 2, Graphics.FONT_SMALL, "Attendo GPS...", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }
        layer.update(dc, app.lat, app.lng);
        if (layer.draw(dc, app.lat, app.lng)) {
            MapLayer.dot(dc);
        } else {
            dc.drawText(w / 2, h / 2, Graphics.FONT_SMALL, layer.failed ? "Mappa non disponibile" : "Carico mappa...", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
        // pillola in basso
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(w * 0.2, h * 0.84, w * 0.6, h * 0.11, 12);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.895, Graphics.FONT_XTINY, "START: cerca", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

class HomeDelegate extends WatchUi.BehaviorDelegate {
    var view;

    function initialize(v) {
        BehaviorDelegate.initialize();
        view = v;
    }

    function onSelect() {
        WatchUi.pushView(Screens.mainMenu(), new MainMenuDelegate(), WatchUi.SLIDE_UP);
        return true;
    }

    function onNextPage() { view.layer.setZoom(view.layer.z - 1); return true; }
    function onPreviousPage() { view.layer.setZoom(view.layer.z + 1); return true; }
}
