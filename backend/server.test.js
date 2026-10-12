'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { PNG } = require('pngjs');
const { createApp, decodePolyline, encodePolyline, shapeRoute, shapeOverpass } = require('./server');

const POLY = '_p~iF~ps|U_ulLnnqC_mqNvxq`@';

test('polyline: decode (esempio Google) e encode inversi', () => {
  assert.deepStrictEqual(decodePolyline(POLY), [[38.5, -120.2], [40.7, -120.95], [43.252, -126.453]]);
  assert.strictEqual(encodePolyline(decodePolyline(POLY)), POLY);
});

const osrm = {
  routes: [{
    distance: 500.4, duration: 400.2, geometry: POLY,
    legs: [{ steps: [
      { name: 'Via Roma', maneuver: { type: 'depart', modifier: 'straight', location: [9, 45] } },
      { name: 'Via Dante', maneuver: { type: 'turn', modifier: 'right', location: [9.001, 45.001] } },
      { name: 'Piazza', maneuver: { type: 'roundabout', exit: 2, location: [9.002, 45.002] } },
      { name: '', maneuver: { type: 'arrive', modifier: 'left', location: [9.003, 45.003] } },
    ] }],
  }],
};

test('shapeRoute: la manovra e il suo punto sono quelli del tratto successivo', () => {
  const r = shapeRoute(osrm);
  assert.strictEqual(r.h, 'Dirigiti su Via Roma');
  assert.deepStrictEqual(r.s[0], [2, 'Svolta a destra su Via Dante', 45.001, 9.001]);
  assert.deepStrictEqual(r.s[1], [8, "Alla rotonda prendi l'uscita 2 su Piazza", 45.002, 9.002]);
  assert.deepStrictEqual(r.s[2], [9, 'Sei arrivato', 45.003, 9.003]);
  assert.strictEqual(r.d, 500);
  assert.strictEqual(r.p.length, 6);
});

test('shapeOverpass: ordina per distanza, usa il centro dei way e un nome di ripiego', () => {
  const r = shapeOverpass({ elements: [
    { lat: 0.01, lon: 0, tags: { name: 'Lontano' } },
    { center: { lat: 0.001, lon: 0 }, tags: {} },
    { tags: { name: 'senza coordinate' } },
  ] }, 0, 0, 'Bancomat');
  assert.deepStrictEqual(r.r.map((x) => x[0]), ['Bancomat', 'Lontano']);
});

// tile finta: grigio 170, cosi' si distingue dal blu del percorso
function fakeTile() {
  const t = new PNG({ width: 256, height: 256 });
  for (let i = 0; i < t.data.length; i += 4) t.data.set([170, 170, 170, 255], i);
  return PNG.sync.write(t);
}

async function withServer(opts, fn) {
  const server = createApp({ token: 'T', ...opts });
  await new Promise((r) => server.listen(0, r));
  try { return await fn(`http://127.0.0.1:${server.address().port}`); } finally { server.close(); }
}

function mockFetch(log) {
  return async (url) => {
    url = String(url); log.push(url);
    if (url.includes('routing.openstreetmap.de')) return { ok: true, json: async () => osrm };
    if (url.includes('tile.test')) return { ok: true, arrayBuffer: async () => fakeTile() };
    throw new Error('url inattesa ' + url);
  };
}

test('auth, validazione e scelta profilo OSRM', async () => {
  const log = [];
  await withServer({ fetchFn: mockFetch(log) }, async (b) => {
    assert.strictEqual((await fetch(`${b}/route?olat=1&olng=2&dlat=3&dlng=4`)).status, 401);
    assert.strictEqual((await fetch(`${b}/route?t=T&olat=x&olng=2&dlat=3&dlng=4`)).status, 400);
    assert.strictEqual((await fetch(`${b}/nearby?t=T&lat=1&lng=2&type=casino`)).status, 400);
    const ok = await fetch(`${b}/route?t=T&olat=45&olng=9&dlat=45.01&dlng=9.01&mode=bike`);
    assert.strictEqual(ok.status, 200);
    assert.ok((await ok.json()).id);
    assert.ok(log[0].includes('routed-bike') && log[0].includes('9,45;9.01,45.01'));
  });
});

test('/map: PNG della dimensione richiesta con il percorso disegnato (centrato e fit)', async () => {
  const log = [];
  await withServer({ fetchFn: mockFetch(log), tileUrl: 'http://tile.test/{z}/{x}/{y}.png' }, async (b) => {
    const { id } = await (await fetch(`${b}/route?t=T&olat=45&olng=9&dlat=45.01&dlng=9.01`)).json();
    const count = (png) => { let n = 0; for (let i = 0; i < png.data.length; i += 4) if (png.data[i] === 0 && png.data[i + 2] === 255) n++; return n; };

    // il percorso di prova va da (38.5,-120.2) a (43.2,-126.4): lo inquadro con fit
    const fit = await fetch(`${b}/map?t=T&fit=1&rid=${id}&w=240&h=240`);
    assert.strictEqual(fit.headers.get('content-type'), 'image/png');
    const png = PNG.sync.read(Buffer.from(await fit.arrayBuffer()));
    assert.deepStrictEqual([png.width, png.height], [240, 240]);
    assert.ok(count(png) > 100, 'percorso blu visibile');
    const gray = png.data[0];
    assert.strictEqual(gray, 170, 'sfondo = tile');

    const centered = PNG.sync.read(Buffer.from(await (await fetch(`${b}/map?t=T&lat=40&lng=-121&z=8&w=200&h=200&rid=${id}`)).arrayBuffer()));
    assert.deepStrictEqual([centered.width, centered.height], [200, 200]);

    assert.strictEqual((await fetch(`${b}/map?t=T&lat=40&lng=-121&rid=nope`)).status, 404);
    assert.strictEqual((await fetch(`${b}/map?t=T&fit=1`)).status, 400);
    assert.ok(log.some((u) => u.includes('tile.test')));
  });
});
