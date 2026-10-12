using Toybox.Application;
using Toybox.Position;
using Toybox.WatchUi;
using Toybox.Attention;
using Toybox.Application.Properties;

class MapsApp extends Application.AppBase {
    var lat = null;
    var lng = null;
    var nav = null;          // NavView attiva, se c'e'
    var destLat = null;
    var destLng = null;
    var lastReroute = 0;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, method(:onPosition));
    }

    function onStop(state) {
        Position.enableLocationEvents(Position.LOCATION_DISABLE, method(:onPosition));
    }

    function onPosition(info) {
        if (info.position != null) {
            var d = info.position.toDegrees();
            lat = d[0];
            lng = d[1];
            if (nav != null) {
                nav.tick(lat, lng);
            }
        }
        WatchUi.requestUpdate();
    }

    function getInitialView() {
        var v = new HomeView();
        return [v, new HomeDelegate(v)];
    }

    function onSettingsChanged() {
        WatchUi.requestUpdate();
    }

    // --- flussi ---

    function busy(text) {
        WatchUi.pushView(new WatchUi.ProgressBar(text, null), new WaitDelegate(), WatchUi.SLIDE_DOWN);
    }

    function requirePosition() {
        if (lat == null) {
            Screens.message("Attendo il GPS...");
            return false;
        }
        return true;
    }

    function findNearby(type) {
        if (requirePosition()) {
            busy("Cerco...");
            Api.nearby(lat, lng, type, method(:onPlaces));
        }
    }

    function findText(q) {
        if (q == null || q.equals("")) {
            Screens.message("Preferito vuoto: impostalo da Garmin Connect");
        } else if (requirePosition()) {
            busy("Cerco...");
            Api.search(lat, lng, q, method(:onPlaces));
        }
    }

    function onPlaces(code, data) {
        WatchUi.popView(WatchUi.SLIDE_UP);
        if (code == 200 && data != null && data["r"] != null && data["r"].size() > 0) {
            var places = data["r"];
            var menu = new WatchUi.Menu2({:title => "Scegli"});
            for (var i = 0; i < places.size(); i++) {
                menu.addItem(new WatchUi.MenuItem(places[i][0], Geo.fmt(places[i][3]), i, {}));
            }
            WatchUi.pushView(menu, new PlacesDelegate(places), WatchUi.SLIDE_LEFT);
        } else {
            Screens.message(code == 200 ? "Nessun risultato" : "Errore rete (" + code + ")");
        }
    }

    function startRoute(dLat, dLng) {
        if (requirePosition()) {
            destLat = dLat;
            destLng = dLng;
            busy("Calcolo percorso...");
            Api.route(lat, lng, dLat, dLng, method(:onRoute));
        }
    }

    function onRoute(code, data) {
        WatchUi.popView(WatchUi.SLIDE_UP);
        if (code == 200 && data != null && data["s"] != null) {
            var v = new PreviewView(data);
            WatchUi.pushView(v, new PreviewDelegate(v), WatchUi.SLIDE_LEFT);
        } else {
            Screens.message(code == 404 ? "Nessun percorso" : "Errore rete (" + code + ")");
        }
    }

    // Ricalcolo silenzioso quando ci si e' allontanati
    function reroute() {
        var now = Time.now().value();
        if (lat != null && destLat != null && now - lastReroute > 30) {
            lastReroute = now;
            Api.route(lat, lng, destLat, destLng, method(:onReroute));
        }
    }

    function onReroute(code, data) {
        if (code == 200 && data != null && data["s"] != null && nav != null) {
            nav.setRoute(data);
            if (Attention has :vibrate) {
                Attention.vibrate([new Attention.VibeProfile(100, 150), new Attention.VibeProfile(0, 100), new Attention.VibeProfile(100, 150)]);
            }
        }
    }
}

using Toybox.Time;
