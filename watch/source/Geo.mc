using Toybox.Math;

module Geo {
    // Distanza in metri (haversine)
    function dist(aLat, aLng, bLat, bLng) {
        var rad = Math.PI / 180.0;
        var dLat = (bLat - aLat) * rad;
        var dLng = (bLng - aLng) * rad;
        var h = Math.pow(Math.sin(dLat / 2), 2) +
                Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * 6371000.0 * Math.asin(Math.sqrt(h));
    }

    function fmt(m) {
        if (m < 1000) {
            return m.toNumber().toString() + " m";
        }
        return (m / 1000.0).format("%.1f") + " km";
    }

    // Spezza il testo in righe da max n caratteri
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
