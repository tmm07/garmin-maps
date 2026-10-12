'use strict';
// Backend gratuito basato su OpenStreetMap: nessuna chiave API, nessuna carta di credito.
//   percorsi  -> OSRM (routing.openstreetmap.de)     luoghi -> Overpass     indirizzi -> Nominatim
//   mappa     -> tile OSM unite e ridisegnate qui con il percorso sopra (PNG per l'orologio)
const http = require('http');
const { PNG } = require('pngjs');

const OSRM = { walk: 'routed-foot', bike: 'routed-bike', drive: 'routed-car' };
const OVERPASS_URL = 'https://overpass-api.de/api/interpreter';
const NOMINATIM_URL = 'https://nominatim.openstreetmap.org/search';
const DEFAULT_TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

const TYPES = {
  restaurant: ['amenity', 'restaurant', 'Ristorante'], cafe: ['amenity', 'cafe', 'Bar'],
  pharmacy: ['amenity', 'pharmacy', 'Farmacia'], gas_station: ['amenity', 'fuel', 'Distributore'],
  parking: ['amenity', 'parking', 'Parcheggio'], atm: ['amenity', 'atm', 'Bancomat'],
  supermarket: ['shop', 'supermarket', 'Supermercato'],
};
const ARRIVE = 9;

// ---------- utilita' ----------
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

function haversine(aLat, aLng, bLat, bLng) {
  const R = 6371000, rad = Math.PI / 180;
  const dLat = (bLat - aLat) * rad, dLng = (bLng - aLng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

const r5 = (n) => Math.round(n * 1e5) / 1e5;

function downsample(pts, n) {
  if (pts.length <= n) return pts;
  const out = [];
  for (let k = 0; k < n - 1; k++) out.push(pts[Math.floor((k * (pts.length - 1)) / (n - 1))]);
  out.push(pts[pts.length - 1]);
  return out;
}

const httpError = (status, msg) => Object.assign(new Error(msg), { status });

// ---------- percorsi (OSRM -> formato compatto per l'orologio) ----------
// Codici manovra condivisi con l'app: 0 dritto, 1 sx, 2 dx, 3 slight sx, 4 slight dx,
// 5 sharp sx, 6 sharp dx, 7 inversione, 8 rotonda, 9 arrivo
const MOD_CODE = { straight: 0, left: 1, right: 2, 'slight left': 3, 'slight right': 4, 'sharp left': 5, 'sharp right': 6, uturn: 7 };
const MOD_TEXT = {
  straight: 'Prosegui dritto', left: 'Svolta a sinistra', right: 'Svolta a destra',
  'slight left': 'Gira leggermente a sinistra', 'slight right': 'Gira leggermente a destra',
  'sharp left': 'Svolta secca a sinistra', 'sharp right': 'Svolta secca a destra', uturn: 'Inversione a U',
};

function maneuverOf(step) {
  const m = step.maneuver || {};
  const name = step.name ? ` su ${step.name}` : '';
  if (m.type === 'arrive') return [ARRIVE, 'Sei arrivato'];
  if (m.type === 'roundabout' || m.type === 'rotary' || m.type === 'roundabout turn') {
    return [8, m.exit ? `Alla rotonda prendi l'uscita ${m.exit}${name}` : `Entra in rotonda${name}`];
  }
  const mod = MOD_CODE[m.modifier] !== undefined ? m.modifier : 'straight';
  return [MOD_CODE[mod], (m.type === 'continue' || m.type === 'new name' ? 'Prosegui' : MOD_TEXT[mod]) + name];
}

function shapeRoute(raw) {
  const route = raw && raw.routes && raw.routes[0];
  if (!route) return null;
  const steps = (route.legs && route.legs[0] && route.legs[0].steps) || [];
  const geom = decodePolyline(route.geometry);
  const s = [];
  for (let i = 0; i + 1 < steps.length && s.length < 60; i++) {
    const next = steps[i + 1];
    const [code, text] = maneuverOf(next);
    const [lng, lat] = next.maneuver.location;   // fine del tratto i = punto della manovra successiva
    s.push([code, text.slice(0, 70), r5(lat), r5(lng)]);
  }
  if (!s.length) { const e = geom[geom.length - 1]; s.push([ARRIVE, 'Sei arrivato', r5(e[0]), r5(e[1])]); }
  const first = steps[0];
  const p = [];
  for (const [la, ln] of downsample(geom, 80)) p.push(r5(la), r5(ln));
  return {
    d: Math.round(route.distance || 0), t: Math.round(route.duration || 0),
    h: first ? (first.name ? `Dirigiti su ${first.name}` : 'Parti').slice(0, 70) : 'Parti',
    s, p,
  };
}

// ---------- luoghi ----------
function shapeOverpass(raw, lat, lng, fallbackName) {
  const out = [];
  for (const el of (raw && raw.elements) || []) {
    const la = el.lat ?? (el.center && el.center.lat), ln = el.lon ?? (el.center && el.center.lon);
    if (la === undefined || ln === undefined) continue;
    const name = ((el.tags && el.tags.name) || fallbackName).slice(0, 30);
    out.push([name, r5(la), r5(ln), Math.round(haversine(lat, lng, la, ln))]);
  }
  return { r: out.sort((a, b) => a[3] - b[3]).slice(0, 8) };
}

function shapeNominatim(raw, lat, lng) {
  return {
    r: (raw || []).map((p) => {
      const la = Number(p.lat), ln = Number(p.lon);
      const name = String(p.display_name || '?').split(',').slice(0, 2).join(',').trim().slice(0, 30);
      return [name, r5(la), r5(ln), Math.round(haversine(lat, lng, la, ln))];
    }).sort((a, b) => a[3] - b[3]),
  };
}

// ---------- mappa ----------
const TILE = 256;
const worldX = (lng, z) => ((lng + 180) / 360) * TILE * 2 ** z;
const worldY = (lat, z) => {
  const s = Math.sin((lat * Math.PI) / 180);
  return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * TILE * 2 ** z;
};
const worldLng = (x, z) => (x / (TILE * 2 ** z)) * 360 - 180;
const worldLat = (y, z) => {
  const n = Math.PI - (2 * Math.PI * y) / (TILE * 2 ** z);
  return (180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
};

function setPx(img, x, y, rgb) {
  x = Math.round(x); y = Math.round(y);
  if (x < 0 || y < 0 || x >= img.width || y >= img.height) return;
  const i = (y * img.width + x) * 4;
  img.data[i] = rgb[0]; img.data[i + 1] = rgb[1]; img.data[i + 2] = rgb[2]; img.data[i + 3] = 255;
}
function disc(img, cx, cy, r, rgb) {
  for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r) setPx(img, cx + x, cy + y, rgb);
}
function line(img, x0, y0, x1, y1, r, rgb) {
  const n = Math.max(1, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / Math.max(1, r / 2)));
  for (let k = 0; k <= n; k++) disc(img, x0 + ((x1 - x0) * k) / n, y0 + ((y1 - y0) * k) / n, r, rgb);
}

// Copia la tile `t` in `img` con l'angolo in alto a sinistra in (dx, dy), tagliando i bordi
function blit(t, img, dx, dy) {
  const sx = Math.max(0, -dx), sy = Math.max(0, -dy);
  const tx = Math.max(0, dx), ty = Math.max(0, dy);
  const cw = Math.min(t.width - sx, img.width - tx), ch = Math.min(t.height - sy, img.height - ty);
  if (cw > 0 && ch > 0) PNG.bitblt(t, img, sx, sy, cw, ch, tx, ty);
}

function createRenderer({ fetchFn, tileUrl, userAgent }) {
  const tiles = new Map(); // LRU di tile gia' decodificate

  async function getTile(z, x, y) {
    const n = 2 ** z;
    if (y < 0 || y >= n) return null;
    x = ((x % n) + n) % n;
    const key = `${z}/${x}/${y}`;
    if (tiles.has(key)) { const t = tiles.get(key); tiles.delete(key); tiles.set(key, t); return t; }
    const res = await fetchFn(tileUrl.replace('{z}', z).replace('{x}', x).replace('{y}', y), { headers: { 'User-Agent': userAgent } });
    if (!res.ok) return null;
    let png;
    try { png = PNG.sync.read(Buffer.from(await res.arrayBuffer())); } catch { return null; }
    tiles.set(key, png);
    if (tiles.size > 200) tiles.delete(tiles.keys().next().value);
    return png;
  }

  // view: {lat, lng, z} oppure {fit:true} con route.points. Restituisce un Buffer PNG.
  async function render({ w, h, view, route }) {
    let { lat, lng, z } = view;
    if (view.fit) {
      const pts = route.points;
      const lats = pts.map((p) => p[0]), lngs = pts.map((p) => p[1]);
      lat = (Math.min(...lats) + Math.max(...lats)) / 2; lng = (Math.min(...lngs) + Math.max(...lngs)) / 2;
      for (z = 17; z > 3; z--) {
        const spanX = worldX(Math.max(...lngs), z) - worldX(Math.min(...lngs), z);
        const spanY = worldY(Math.min(...lats), z) - worldY(Math.max(...lats), z);
        if (spanX < w * 0.8 && spanY < h * 0.7) break;
      }
    }
    const cx = worldX(lng, z), cy = worldY(lat, z);
    const left = cx - w / 2, top = cy - h / 2;
    const img = new PNG({ width: w, height: h });
    img.data.fill(255);

    const jobs = [];
    for (let ty = Math.floor(top / TILE); ty <= Math.floor((top + h - 1) / TILE); ty++) {
      for (let tx = Math.floor(left / TILE); tx <= Math.floor((left + w - 1) / TILE); tx++) {
        const dx = Math.round(tx * TILE - left), dy = Math.round(ty * TILE - top);
        jobs.push(getTile(z, tx, ty).then((t) => t && blit(t, img, dx, dy)));
      }
    }
    await Promise.all(jobs);

    // 4 livelli per canale = i 64 colori dello schermo del 255 (e PNG molto piu' piccoli)
    for (let i = 0; i < img.data.length; i += 4) for (let c = 0; c < 3; c++) img.data[i + c] = Math.round(img.data[i + c] / 85) * 85;

    if (route) {
      const px = route.points.map(([la, ln]) => [worldX(ln, z) - left, worldY(la, z) - top]);
      for (let i = 0; i + 1 < px.length; i++) line(img, px[i][0], px[i][1], px[i + 1][0], px[i + 1][1], 5, [255, 255, 255]);
      for (let i = 0; i + 1 < px.length; i++) line(img, px[i][0], px[i][1], px[i + 1][0], px[i + 1][1], 3, [0, 85, 255]);
      const end = px[px.length - 1];
      disc(img, end[0], end[1], 7, [255, 255, 255]); disc(img, end[0], end[1], 5, [0, 170, 0]);
      if (view.fit) { disc(img, px[0][0], px[0][1], 7, [255, 255, 255]); disc(img, px[0][0], px[0][1], 5, [0, 85, 255]); }
    }
    return PNG.sync.write(img);
  }
  return { render };
}

// ---------- server ----------
const routeCache = new Map();
function cacheRoute(entry) {
  const id = Math.random().toString(16).slice(2, 10);
  routeCache.set(id, entry);
  if (routeCache.size > 50) routeCache.delete(routeCache.keys().next().value);
  return id;
}

function createApp({ token, fetchFn = fetch, tileUrl = DEFAULT_TILES, contact = 'garmin-maps (set CONTACT)' }) {
  const userAgent = `garmin-maps-watch/1.0 (${contact})`;
  const renderer = createRenderer({ fetchFn, tileUrl, userAgent });
  const get = async (url, opts = {}) => {
    const res = await fetchFn(url, { ...opts, headers: { 'User-Agent': userAgent, ...(opts.headers || {}) }, signal: AbortSignal.timeout(12000) });
    if (!res.ok) throw httpError(502, 'upstream ' + res.status);
    return res.json();
  };
  const num = (v, name) => {
    const n = Number(v);
    if (v === undefined || v === '' || !Number.isFinite(n)) throw httpError(400, 'bad ' + name);
    return n;
  };
  const clamp = (v, lo, hi, def) => { const n = Number(v); return Number.isFinite(n) ? Math.min(hi, Math.max(lo, Math.round(n))) : def; };

  const handlers = {
    async '/nearby'(q) {
      const lat = num(q.lat, 'lat'), lng = num(q.lng, 'lng');
      const t = TYPES[q.type];
      if (!t) throw httpError(400, 'bad type');
      const query = `[out:json][timeout:10];nwr(around:2000,${lat},${lng})["${t[0]}"="${t[1]}"];out center 40;`;
      const raw = await get(OVERPASS_URL, { method: 'POST', body: 'data=' + encodeURIComponent(query), headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
      return shapeOverpass(raw, lat, lng, t[2]);
    },
    async '/search'(q) {
      const lat = num(q.lat, 'lat'), lng = num(q.lng, 'lng');
      if (!q.q) throw httpError(400, 'bad q');
      const u = new URL(NOMINATIM_URL);
      u.search = new URLSearchParams({ q: String(q.q).slice(0, 100), format: 'jsonv2', limit: '5', viewbox: `${lng - 0.3},${lat + 0.3},${lng + 0.3},${lat - 0.3}`, 'accept-language': 'it' });
      return shapeNominatim(await get(u), lat, lng);
    },
    async '/route'(q) {
      const profile = OSRM[q.mode || 'walk'];
      if (!profile) throw httpError(400, 'bad mode');
      const o = [num(q.olng, 'lng'), num(q.olat, 'lat')], d = [num(q.dlng, 'lng'), num(q.dlat, 'lat')];
      const raw = await get(`https://routing.openstreetmap.de/${profile}/route/v1/driving/${o};${d}?steps=true&geometries=polyline&overview=full`);
      const shaped = shapeRoute(raw);
      if (!shaped) throw httpError(404, 'no route');
      const full = decodePolyline(raw.routes[0].geometry);
      shaped.id = cacheRoute({ points: downsample(full, 300) });
      return shaped;
    },
    async '/map'(q) {
      const w = clamp(q.w, 100, 320, 240), h = clamp(q.h, 100, 320, 240);
      const route = q.rid ? routeCache.get(q.rid) : undefined;
      if (q.rid && !route) throw httpError(404, 'unknown rid');
      if (q.fit && !route) throw httpError(400, 'fit needs rid');
      const view = q.fit ? { fit: true } : { lat: num(q.lat, 'lat'), lng: num(q.lng, 'lng'), z: clamp(q.z, 10, 19, 16) };
      return { binary: await renderer.render({ w, h, view, route }), type: 'image/png' };
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

module.exports = { createApp, decodePolyline, encodePolyline, shapeRoute, shapeOverpass, downsample };

if (require.main === module) {
  const token = process.env.APP_TOKEN;
  if (!token) { console.error('Serve APP_TOKEN (una stringa lunga a caso)'); process.exit(1); }
  createApp({ token, tileUrl: process.env.TILE_URL || DEFAULT_TILES, contact: process.env.CONTACT || undefined })
    .listen(process.env.PORT || 8080, () => console.log('in ascolto'));
}
