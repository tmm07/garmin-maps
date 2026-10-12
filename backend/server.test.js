'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { createApp, decodePolyline, encodePolyline, shapeRoute } = require('./server');

test('decodePolyline (esempio ufficiale Google)', () => {
  const p = decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@');
  assert.deepStrictEqual(p, [[38.5, -120.2], [40.7, -120.95], [43.252, -126.453]]);
});

const rawRoute = {
  routes: [{
    distanceMeters: 500, duration: '400s',
    polyline: { encodedPolyline: '_p~iF~ps|U_ulLnnqC_mqNvxq`@' },
    legs: [{ steps: [
      { navigationInstruction: { instructions: 'Dirigiti a nord' }, endLocation: { latLng: { latitude: 1, longitude: 2 } } },
      { navigationInstruction: { maneuver: 'TURN_RIGHT', instructions: 'Svolta a destra' }, endLocation: { latLng: { latitude: 3, longitude: 4 } } },
    ] }],
  }],
};

test('shapeRoute sposta la manovra sul punto in cui va eseguita', () => {
  const r = shapeRoute(rawRoute);
  assert.strictEqual(r.h, 'Dirigiti a nord');
  assert.deepStrictEqual(r.s[0], [2, 'Svolta a destra', 1, 2]);
  assert.deepStrictEqual(r.s[1], [9, 'Sei arrivato', 3, 4]);
  assert.strictEqual(r.t, 400);
  assert.strictEqual(r.p.length, 6);
});

async function call(app, path, fetchFn) {
  const server = app; await new Promise((r) => server.listen(0, r));
  try {
    const res = await fetch(`http://127.0.0.1:${server.address().port}${path}`);
    return { status: res.status, body: await res.json() };
  } finally { server.close(); }
}

test('auth, validazione e route end-to-end con Google mockato', async () => {
  let sent;
  const fetchFn = async (url, opts) => { sent = { url, opts }; return { ok: true, json: async () => rawRoute }; };
  const mk = () => createApp({ apiKey: 'K', token: 'T', fetchFn });
  assert.strictEqual((await call(mk(), '/route?olat=1&olng=2&dlat=3&dlng=4')).status, 401);
  assert.strictEqual((await call(mk(), '/route?t=T&olat=x&olng=2&dlat=3&dlng=4')).status, 400);
  assert.strictEqual((await call(mk(), '/nearby?t=T&lat=1&lng=2&type=casino')).status, 400);
  const ok = await call(mk(), '/route?t=T&olat=1&olng=2&dlat=3&dlng=4&mode=bike');
  assert.strictEqual(ok.status, 200);
  assert.strictEqual(JSON.parse(sent.opts.body).travelMode, 'BICYCLE');
  assert.strictEqual(sent.opts.headers['X-Goog-Api-Key'], 'K');
});

test('nearby ordina per distanza', async () => {
  const fetchFn = async () => ({ ok: true, json: async () => ({ places: [
    { displayName: { text: 'Lontano' }, location: { latitude: 0.01, longitude: 0 } },
    { displayName: { text: 'Vicino' }, location: { latitude: 0.001, longitude: 0 } },
  ] }) });
  const r = await call(createApp({ apiKey: 'K', token: 'T', fetchFn }), '/nearby?t=T&lat=0&lng=0&type=cafe');
  assert.strictEqual(r.body.r[0][0], 'Vicino');
});

test('encodePolyline e' + ' inverso di decodePolyline', () => {
  const s = '_p~iF~ps|U_ulLnnqC_mqNvxq`@';
  assert.strictEqual(encodePolyline(decodePolyline(s)), s);
});

test('/map: path del percorso, centro, zoom e PNG restituito', async () => {
  const urls = [];
  const fetchFn = async (url, opts) => {
    urls.push(String(url));
    if (String(url).includes('staticmap')) return { ok: true, arrayBuffer: async () => Uint8Array.from([137, 80, 78, 71]).buffer };
    return { ok: true, json: async () => rawRoute };
  };
  const mk = () => createApp({ apiKey: 'K', token: 'T', fetchFn });
  const route = await call(mk(), '/route?t=T&olat=1&olng=2&dlat=3&dlng=4');
  assert.ok(route.body.id);
  // la cache e' a livello di modulo, quindi l'id vale anche per una nuova istanza
  const server = mk(); await new Promise((r) => server.listen(0, r));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    const res = await fetch(`${base}/map?t=T&lat=45&lng=9&z=17&w=240&h=240&rid=${route.body.id}`);
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.headers.get('content-type'), 'image/png');
    assert.deepStrictEqual([...new Uint8Array(await res.arrayBuffer())], [137, 80, 78, 71]);
    const u = new URL(urls.at(-1));
    assert.strictEqual(u.searchParams.get('center'), '45,9');
    assert.strictEqual(u.searchParams.get('zoom'), '17');
    assert.ok(u.searchParams.getAll('path')[0].includes('enc:'));
    assert.strictEqual((await fetch(`${base}/map?t=T&lat=45&lng=9&rid=nope`)).status, 404);
    assert.strictEqual((await fetch(`${base}/map?t=T&fit=1`)).status, 400);
  } finally { server.close(); }
});
