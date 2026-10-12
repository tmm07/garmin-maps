'use strict';
// Proxy tra l'app Garmin e le API Google (Routes + Places). Tiene la chiave API lontana dall'orologio
// e restituisce JSON molto compatti (la memoria del 255 e' poca).
const http = require('http');

const ROUTES_URL = 'https://routes.googleapis.com/directions/v2:computeRoutes';
const NEARBY_URL = 'https://places.googleapis.com/v1/places:searchNearby';
const TEXT_URL = 'https://places.googleapis.com/v1/places:searchText';

const TYPES = new Set(['restaurant', 'cafe', 'pharmacy', 'gas_station', 'parking', 'atm', 'supermarket']);
const MODES = { walk: 'WALK', drive: 'DRIVE', bike: 'BICYCLE', transit: 'TRANSIT' };

// Codici manovra condivisi con l'app (vedi watch/source/NavView.mc)
const MAN = {
  TURN_LEFT: 1, TURN_RIGHT: 2, TURN_SLIGHT_LEFT: 3, TURN_SLIGHT_RIGHT: 4,
  TURN_SHARP_LEFT: 5, TURN_SHARP_RIGHT: 6, UTURN_LEFT: 7, UTURN_RIGHT: 7,
  ROUNDABOUT_LEFT: 8, ROUNDABOUT_RIGHT: 8, RAMP_LEFT: 3, RAMP_RIGHT: 4,
  FORK_LEFT: 3, FORK_RIGHT: 4,
};
const ARRIVE = 9;
const STATIC_URL = 'https://maps.googleapis.com/maps/api/staticmap';

// Percorsi recenti, servono per disegnare la linea sulla mappa (/map?rid=...)
const routeCache = new Map();
function cacheRoute(entry) {
  const id = Math.random().toString(16).slice(2, 10);
  routeCache.set(id, entry);
  if (routeCache.size > 50) routeCache.delete(routeCache.keys().next().value);
  return id;
}

function decodePolyline(str) {
  const pts = [];
  let i = 0, lat = 0, lng = 0;
  while (i < str.length) {
    for (const axis of [0, 1]) {
      let shift = 0, result = 0, b;
      do { b = str.charCodeAt(i++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
      const d = result & 1 ? ~(result >> 1) : result >> 1;
      if (axis === 0) lat += d; else lng += d;
    }
    pts.push([lat / 1e5, lng / 1e5]);
  }
  return pts;
}

function haversine(aLat, aLng, bLat, bLng) {
  const R = 6371000, rad = Math.PI / 180;
  const dLat = (bLat - aLat) * rad, dLng = (bLng - aLng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

function encodePolyline(pts) {
  let out = '', pLat = 0, pLng = 0;
  const enc = (v) => {
    v = v < 0 ? ~(v << 1) : v << 1;
    let c = '';
    while (v >= 0x20) { c += String.fromCharCode((0x20 | (v & 0x1f)) + 63); v >>= 5; }
    return c + String.fromCharCode(v + 63);
  };
  for (const [la, ln] of pts) {
    const iLat = Math.round(la * 1e5), iLng = Math.round(ln * 1e5);
    out += enc(iLat - pLat) + enc(iLng - pLng);
    pLat = iLat; pLng = iLng;
  }
  return out;
}

const r5 = (n) => Math.round(n * 1e5) / 1e5;

// Riduce il percorso a max `n` punti (campionamento uniforme, ultimo punto sempre incluso).
function downsample(pts, n) {
  if (pts.length <= n) return pts;
  const out = [];
  for (let k = 0; k < n - 1; k++) out.push(pts[Math.floor((k * (pts.length - 1)) / (n - 1))]);
  out.push(pts[pts.length - 1]);
  return out;
}

// Ogni elemento di `s` e': [manovra, testo, lat, lng] dove lat/lng e' la FINE del tratto corrente e
// manovra/testo sono quelli da fare li' (cioe' l'istruzione del tratto successivo).
function shapeRoute(raw) {
  const route = raw && raw.routes && raw.routes[0];
  if (!route) return null;
  const steps = (route.legs && route.legs[0] && route.legs[0].steps) || [];
  const s = steps.slice(0, 60).map((st, i) => {
    const nxt = steps[i + 1];
    const ni = nxt && nxt.navigationInstruction;
    const e = st.endLocation.latLng;
    return [
      nxt ? (MAN[ni && ni.maneuver] || 0) : ARRIVE,
      nxt ? ((ni && ni.instructions) || 'Continua').slice(0, 70) : 'Sei arrivato',
      r5(e.latitude), r5(e.longitude),
    ];
  });
  const first = steps[0] && steps[0].navigationInstruction;
  const p = [];
  for (const [la, ln] of downsample(decodePolyline(route.polyline.encodedPolyline), 80)) p.push(r5(la), r5(ln));
  return {
    d: route.distanceMeters || 0,
    t: parseInt(route.duration, 10) || 0, // "123s"
    h: (first && first.instructions || 'Parti').slice(0, 70),
    s, p,
  };
}

function shapePlaces(raw, lat, lng) {
  const list = (raw && raw.places) || [];
  return {
    r: list.map((pl) => {
      const la = pl.location.latitude, ln = pl.location.longitude;
      return [((pl.displayName && pl.displayName.text) || '?').slice(0, 30), r5(la), r5(ln), Math.round(haversine(lat, lng, la, ln))];
    }).sort((a, b) => a[3] - b[3]),
  };
}

function createApp({ apiKey, token, fetchFn = fetch }) {
  async function google(url, body, fieldMask) {
    const res = await fetchFn(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Goog-Api-Key': apiKey, 'X-Goog-FieldMask': fieldMask },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw Object.assign(new Error('google ' + res.status), { status: 502 });
    return res.json();
  }

  const num = (v, name) => {
    const n = Number(v);
    if (v === undefined || v === '' || !Number.isFinite(n)) throw Object.assign(new Error('bad ' + name), { status: 400 });
    return n;
  };

  const handlers = {
    async '/nearby'(q) {
      const lat = num(q.lat, 'lat'), lng = num(q.lng, 'lng');
      if (!TYPES.has(q.type)) throw Object.assign(new Error('bad type'), { status: 400 });
      const raw = await google(NEARBY_URL, {
        includedTypes: [q.type], maxResultCount: 8, rankPreference: 'DISTANCE', languageCode: 'it',
        locationRestriction: { circle: { center: { latitude: lat, longitude: lng }, radius: 2000 } },
      }, 'places.displayName,places.location');
      return shapePlaces(raw, lat, lng);
    },
    async '/search'(q) {
      const lat = num(q.lat, 'lat'), lng = num(q.lng, 'lng');
      if (!q.q) throw Object.assign(new Error('bad q'), { status: 400 });
      const raw = await google(TEXT_URL, {
        textQuery: String(q.q).slice(0, 100), pageSize: 5, languageCode: 'it',
        locationBias: { circle: { center: { latitude: lat, longitude: lng }, radius: 20000 } },
      }, 'places.displayName,places.location');
      return shapePlaces(raw, lat, lng);
    },
    async '/route'(q) {
      const mode = MODES[q.mode || 'walk'];
      if (!mode) throw Object.assign(new Error('bad mode'), { status: 400 });
      const pt = (la, ln) => ({ location: { latLng: { latitude: num(la, 'lat'), longitude: num(ln, 'lng') } } });
      const raw = await google(ROUTES_URL, {
        origin: pt(q.olat, q.olng), destination: pt(q.dlat, q.dlng),
        travelMode: mode, languageCode: 'it-IT', units: 'METRIC',
      }, 'routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline,' +
         'routes.legs.steps.navigationInstruction,routes.legs.steps.endLocation');
      const shaped = shapeRoute(raw);
      if (!shaped) throw Object.assign(new Error('no route'), { status: 404 });
      const full = decodePolyline(raw.routes[0].polyline.encodedPolyline);
      shaped.id = cacheRoute({
        poly: encodePolyline(downsample(full, 200)),
        dest: full[full.length - 1], orig: full[0],
      });
      return shaped;
    },
    // Immagine PNG della mappa Google, con il percorso disegnato sopra
    async '/map'(q) {
      const clamp = (v, lo, hi, def) => { const n = Number(v); return Number.isFinite(n) ? Math.min(hi, Math.max(lo, Math.round(n))) : def; };
      const w = clamp(q.w, 100, 320, 240), h = clamp(q.h, 100, 320, 240);
      const p = new URLSearchParams({ size: `${w}x${h}`, scale: '1', format: 'png8', maptype: 'roadmap', language: 'it', key: apiKey });
      p.append('style', 'feature:poi|visibility:off');
      p.append('style', 'feature:transit|visibility:off');
      const r = q.rid && routeCache.get(q.rid);
      if (q.rid && !r) throw Object.assign(new Error('unknown rid'), { status: 404 });
      if (!q.fit) {
        p.set('center', `${num(q.lat, 'lat')},${num(q.lng, 'lng')}`);
        p.set('zoom', String(clamp(q.z, 10, 20, 16)));
      }
      if (r) {
        p.append('path', `color:0x1a73e8ff|weight:6|enc:${r.poly}`);
        p.append('markers', `color:green|size:small|${r.dest[0]},${r.dest[1]}`);
        if (q.fit) p.append('markers', `color:blue|size:small|${r.orig[0]},${r.orig[1]}`);
      } else if (q.fit) {
        throw Object.assign(new Error('fit needs rid'), { status: 400 });
      }
      const res = await fetchFn(`${STATIC_URL}?${p}`);
      if (!res.ok) throw Object.assign(new Error('google ' + res.status), { status: 502 });
      return { binary: Buffer.from(await res.arrayBuffer()), type: 'image/png' };
    },
  };

  return http.createServer(async (req, res) => {
    const send = (code, obj) => {
      if (obj && obj.binary) { res.writeHead(code, { 'Content-Type': obj.type }); return res.end(obj.binary); }
      res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(obj));
    };
    try {
      const u = new URL(req.url, 'http://x');
      const q = Object.fromEntries(u.searchParams);
      if (u.pathname === '/health') return send(200, { ok: true });
      if (!token || q.t !== token) return send(401, { error: 'unauthorized' });
      const h = handlers[u.pathname];
      if (!h) return send(404, { error: 'not found' });
      send(200, await h(q));
    } catch (e) {
      send(e.status || 500, { error: e.message });
    }
  });
}

module.exports = { createApp, decodePolyline, encodePolyline, shapeRoute, shapePlaces, downsample };

if (require.main === module) {
  const apiKey = process.env.GOOGLE_API_KEY, token = process.env.APP_TOKEN;
  if (!apiKey || !token) { console.error('Servono GOOGLE_API_KEY e APP_TOKEN'); process.exit(1); }
  createApp({ apiKey, token }).listen(process.env.PORT || 8080, () => console.log('in ascolto'));
}
