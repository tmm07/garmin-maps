using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Application.Properties;
using Toybox.Application;

module Screens {
    const CATEGORIES = [
        ["Ristoranti", "restaurant"], ["Bar / Caffe", "cafe"], ["Farmacie", "pharmacy"],
        ["Benzina", "gas_station"], ["Parcheggi", "parking"], ["Bancomat", "atm"], ["Supermercati", "supermarket"]
    ];
    const MODE_NAMES = ["A piedi", "Auto", "Bici"];

    function modeIndex() {
        var m = Properties.getValue("mode");
        return (m != null && m >= 0 && m < 3) ? m : 0;
    }

    function mainMenu() {
        var menu = new WatchUi.Menu2({:title => "Maps"});
        menu.addItem(new WatchUi.MenuItem("Modo", MODE_NAMES[modeIndex()], "mode", {}));
        for (var i = 1; i <= 3; i++) {
            var f = Properties.getValue("fav" + i);
            if (f != null && !f.equals("")) {
                menu.addItem(new WatchUi.MenuItem(f, "Preferito", "fav" + i, {}));
            }
        }
        for (var c = 0; c < CATEGORIES.size(); c++) {
            menu.addItem(new WatchUi.MenuItem(CATEGORIES[c][0], "Vicino a me", CATEGORIES[c][1], {}));
        }
        return menu;
    }

    function message(text) {
        WatchUi.pushView(new MsgView(text), new MsgDelegate(), WatchUi.SLIDE_LEFT);
    }
}

class MsgView extends WatchUi.View {
    var text;

    function initialize(t) {
        View.initialize();
        text = t;
    }

    function onUpdate(dc) {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var lines = Geo.wrap(text, 16);
        var h = dc.getFontHeight(Graphics.FONT_SMALL);
        var y = dc.getHeight() / 2 - (lines.size() * h) / 2;
        for (var i = 0; i < lines.size(); i++) {
            dc.drawText(dc.getWidth() / 2, y + i * h, Graphics.FONT_SMALL, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
        }
    }
}

class MsgDelegate extends WatchUi.BehaviorDelegate {
    function initialize() { BehaviorDelegate.initialize(); }
    function onSelect() { WatchUi.popView(WatchUi.SLIDE_RIGHT); return true; }
}

class WaitDelegate extends WatchUi.BehaviorDelegate {
    function initialize() { BehaviorDelegate.initialize(); }
}

class MainMenuDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() { Menu2InputDelegate.initialize(); }

    function onSelect(item) {
        var id = item.getId();
        var app = Application.getApp();
        if (id.equals("mode")) {
            var m = (Screens.modeIndex() + 1) % 3;
            Properties.setValue("mode", m);
            item.setSubLabel(Screens.MODE_NAMES[m]);
        } else if (id.substring(0, 3).equals("fav")) {
            app.findText(Properties.getValue(id));
        } else {
            app.findNearby(id);
        }
    }
}

class PlacesDelegate extends WatchUi.Menu2InputDelegate {
    var places;

    function initialize(p) {
        Menu2InputDelegate.initialize();
        places = p;
    }

    function onSelect(item) {
        var p = places[item.getId()];
        Application.getApp().startRoute(p[1], p[2]);
    }
}
