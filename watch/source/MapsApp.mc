using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.Attention;
using Toybox.Time;

class MapsApp extends Application.AppBase {
    var model = new Model();

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        Communications.registerForPhoneAppMessages(method(:onPhone));
    }

    function onStop(state) {
        Communications.registerForPhoneAppMessages(null);
    }

    function getInitialView() {
        var v = new MapView();
        return [v, new MapDelegate(v)];
    }

    function buzz(long) {
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(100, long ? 700 : 250)]);
        }
    }

    function onPhone(msg) {
        var d = msg.data;
        if (d == null || d["k"] == null) {
            return;
        }
        var m = model;
        m.lastMsg = Time.now().value();
        var k = d["k"];

        if (k.equals("n")) {
            var instr = d["i"];
            if (!m.navOn || !instr.equals(m.instr)) {
                m.buzzedNear = false;
                buzz(false);               // nuova indicazione
            }
            m.navOn = true;
            m.dist = d["d"];
            m.instr = instr;
            m.man = d["m"];
            m.eta = d["e"];
            var meters = m.distMeters();
            if (meters != null && meters <= 60 && !m.buzzedNear) {
                m.buzzedNear = true;       // sei vicino: vibrazione lunga
                buzz(true);
            }
        } else if (k.equals("x")) {
            m.navOn = false;
            buzz(true);
        } else if (k.equals("p")) {
            if (d["r"] == m.refId) {
                m.px = d["x"] / 10.0;
                m.py = d["y"] / 10.0;
                m.hdg = d["h"];
                m.havePos = true;
            }
        } else if (k.equals("m")) {
            m.ways = m.decodeWays(d["w"]);
            m.refId = d["r"];
            m.havePos = false;             // aspetta la prima posizione sul nuovo riferimento
        }
        WatchUi.requestUpdate();
    }
}
