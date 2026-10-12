using Toybox.Lang;

// Stato ricevuto dal telefono. Coordinate in metri: x = est, y = nord.
class Model {
    var refId = -1;
    var ways = [];          // [classe, [x0, y0, x1, y1, ...]]
    var px = 0.0;           // posizione rispetto al riferimento della mappa
    var py = 0.0;
    var hdg = 0;
    var havePos = false;
    var lastMsg = 0;        // Time.now().value() dell'ultimo messaggio

    var navOn = false;
    var dist = "";
    var instr = "";
    var man = 10;
    var eta = "";
    var buzzedNear = false;

    // Strade: strade separate da ';', ognuna = classe ('1' principale) + 4 caratteri per punto
    // (2 per x, 2 per y; ogni carattere = codice - 63, cioe' 6 bit; 12 bit = passi di 2 m, zero = 2048).
    function decodeWays(s) {
        var out = [];
        var rest = s;
        while (rest.length() > 0) {
            var semi = rest.find(";");
            var part = (semi == null) ? rest : rest.substring(0, semi);
            rest = (semi == null) ? "" : rest.substring(semi + 1, rest.length());
            if (part.length() >= 5) {
                var cls = part.substring(0, 1).equals("1") ? 1 : 0;
                var c = part.substring(1, part.length()).toCharArray();
                var n = c.size() / 4;
                var pts = new [n * 2];
                for (var k = 0; k < n; k++) {
                    var j = k * 4;
                    pts[k * 2] = ((c[j].toNumber() - 63) * 64 + (c[j + 1].toNumber() - 63) - 2048) * 2.0;
                    pts[k * 2 + 1] = ((c[j + 2].toNumber() - 63) * 64 + (c[j + 3].toNumber() - 63) - 2048) * 2.0;
                }
                out.add([cls, pts]);
            }
        }
        return out;
    }

    // Distanza in metri se e' espressa in metri ("350 m"), altrimenti null
    function distMeters() {
        var sp = dist.find(" ");
        if (sp == null || !dist.substring(sp + 1, dist.length()).equals("m")) {
            return null;
        }
        return dist.substring(0, sp).toNumber();
    }
}
