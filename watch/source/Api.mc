using Toybox.Application;
using Toybox.Communications;
using Toybox.Application.Properties;

// Chiamate al backend (il telefono fa da ponte tramite Garmin Connect Mobile)
module Api {
    const MODES = ["walk", "drive", "bike"];

    function get(path, params, cb) {
        params["t"] = Properties.getValue("token");
        Communications.makeWebRequest(
            Properties.getValue("serverUrl") + path,
            params,
            {
                :method => Communications.HTTP_REQUEST_METHOD_GET,
                :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
            },
            cb
        );
    }

    function nearby(lat, lng, type, cb) {
        get("/nearby", {"lat" => lat, "lng" => lng, "type" => type}, cb);
    }

    function search(lat, lng, q, cb) {
        get("/search", {"lat" => lat, "lng" => lng, "q" => q}, cb);
    }

    function route(oLat, oLng, dLat, dLng, cb) {
        var m = Properties.getValue("mode");
        get("/route", {
            "olat" => oLat, "olng" => oLng, "dlat" => dLat, "dlng" => dLng,
            "mode" => MODES[(m != null && m >= 0 && m < 3) ? m : 0]
        }, cb);
    }
}

module MapApi {
    // Lato (px) dell'immagine mappa: schermo intero ma max 240 per non finire la memoria.
    // Se vedi "Out of memory" sull'orologio, abbassa questo valore (es. 200).
    function px(dc) {
        var w = dc.getWidth();
        return (w > 240) ? 240 : w;
    }

    function fetch(params, size, cb) {
        params["t"] = Application.Properties.getValue("token");
        params["w"] = size;
        params["h"] = size;
        Communications.makeImageRequest(
            Application.Properties.getValue("serverUrl") + "/map",
            params,
            {:maxWidth => size, :maxHeight => size},
            cb
        );
    }
}
