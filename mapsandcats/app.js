/* Maps and Cats — maps with no sign-up. MapLibre + OpenFreeMap, Photon/Nominatim search, OSRM routing.
   Search is fully open source: it only asks the internet for an address, coordinates and routes. */
(() => {
'use strict';

const APP_VERSION = '2.4.0';
/* The log catcher: lines go to the shell's diagnostic log (shared only when you tap Share in About). No places, no searches. */
const diag = (tag, msg) => { try { const t = window.AndroidTrio; if (t && t.diagLog) t.diagLog(tag, String(msg)); } catch {} };
window.addEventListener('error', e => diag('js-error', (e.message || '') + ' @' + (e.filename || '').split('/').pop() + ':' + e.lineno));
window.addEventListener('unhandledrejection', e => diag('js-reject', e.reason && e.reason.message ? e.reason.message : e.reason));
(() => { const f = window.fetch; if (!f) return; window.fetch = function (u) {
  const host = (() => { try { return new URL(String(u && u.url || u), location.href).host; } catch { return '?'; } })();
  return f.apply(this, arguments).then(r => { if (!r.ok) diag('net', host + ' HTTP ' + r.status); return r; }, e => { diag('net', host + ' ' + e); throw e; });
}; })();
diag('start', 'page ' + APP_VERSION + ' ' + navigator.userAgent.replace(/^.*?\(/, '(').slice(0, 80));
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const KL = [3.139, 101.687]; // [lat, lon] fallback view until we get a fix

/* ================= state ================= */
const KEY = 'mapsandcats.v1', RKEY = 'mapsandcats.routes';
const S = Object.assign({
  theme: 'dark', speedo: true, meowAt: 80, catVoice: true, limitSign: true, weight: 65, sim: false, xp: 0, caught: 0, avoidHw: false,
  voice: { on: true, name: '' },
  servers: { photon: '', nominatim: '', osrm: '' },
  saved: { home: null, work: null, favs: [] }, recents: [], trips: [], country: null, streak: null,
}, (() => { try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch { return {}; } })());
S.voice = Object.assign({ on: true, name: '' }, S.voice);
S.servers = Object.assign({ photon: '', nominatim: '', osrm: '' }, S.servers); delete S.servers.traffic; delete S.traffic; delete S.trafficKey; delete S.trafficProv;
S.saved = Object.assign({ home: null, work: null, favs: [] }, S.saved);
S.streak = Object.assign(newStreak(), S.streak);
const save = () => { try { localStorage.setItem(KEY, JSON.stringify(S)); } catch {} };

let me = null;           // {lat, lon, acc, ts}
let following = false;
const session = { dist: 0, cats: 0, xpRem: 0, lastSlow: 0, anchor: null };

/* ================= helpers ================= */
const R = 6371000, rad = d => d * Math.PI / 180, deg = r => r * 180 / Math.PI;
function dist(a, b) { // haversine, a/b = [lat, lon]
  const dLat = rad(b[0] - a[0]), dLon = rad(b[1] - a[1]);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a[0])) * Math.cos(rad(b[0])) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
function offset(p, meters, bearingRad) {
  const d = meters / R, lat1 = rad(p[0]), lon1 = rad(p[1]);
  const lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(bearingRad));
  const lon2 = lon1 + Math.atan2(Math.sin(bearingRad) * Math.sin(d) * Math.cos(lat1), Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));
  return [deg(lat2), deg(lon2)];
}
// distance from point to segment, in metres (local equirectangular projection — fine at street scale)
function distToSeg(p, a, b) {
  const k = Math.cos(rad(p[0])) * 111320, m = 110540;
  const px = (p[1] - a[1]) * k, py = (p[0] - a[0]) * m, bx = (b[1] - a[1]) * k, by = (b[0] - a[0]) * m;
  const L = bx * bx + by * by, t = L ? Math.max(0, Math.min(1, (px * bx + py * by) / L)) : 0;
  return Math.hypot(px - t * bx, py - t * by);
}
const fmtDist = m => m < 1000 ? `${Math.round(m / 10) * 10 || Math.round(m)} m` : `${(m / 1000).toFixed(m < 10000 ? 1 : 0)} km`;
/* The route engine knows distances and typical speeds, not live traffic. For driving we say so and show a range. */
const fmtEst = (s, mode) => mode === 'foot' ? fmtTime(s) : (() => {
  const lo = Math.max(1, Math.round(s / 60)), hi = Math.max(lo + 1, Math.round(s * 1.3 / 60));
  return hi < 60 ? `${lo}\u2013${hi} min` : `${fmtTime(s)}+`;
})();
const fmtTime = s => { const m = Math.max(1, Math.round(s / 60)); return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60} min`; };
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pick = a => a[Math.floor(Math.random() * a.length)];
const pretty = s => String(s || '').replace(/_/g, ' ').replace(/^./, c => c.toUpperCase());
const getAccent = () => getComputedStyle(document.body).getPropertyValue('--accent').trim() || '#3df5d0';

let toastT;
function toast(msg, ms = 1900) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('show'), ms);
}
// Haptics: Capacitor plugin when wrapped by Capacitor, else the Vibration API; silently no-ops otherwise.
function haptic(ms = 25) {
  try {
    const h = window.Capacitor?.Plugins?.Haptics;
    if (h?.vibrate) h.vibrate({ duration: ms }); else navigator.vibrate?.(ms);
  } catch {}
}

/* ================= map (MapLibre + OpenFreeMap) ================= */
const styleCache = {};
const FALLBACK_STYLE = dark => ({ version: 8, sources: {}, layers: [{ id: 'background', type: 'background', paint: { 'background-color': dark ? '#000' : '#f2f3f0' } }] });
const POI_GROUPS = {
  food: ['restaurant', 'fast_food', 'food_court', 'bar', 'pub', 'ice_cream', 'biergarten'],
  coffee: ['cafe', 'bakery'],
  park: ['park', 'garden', 'playground', 'nature_reserve', 'dog_park'],
  sight: ['attraction', 'museum', 'zoo', 'art_gallery', 'gallery', 'place_of_worship', 'castle', 'monument', 'viewpoint', 'theatre', 'cinema', 'library', 'aquarium', 'theme_park'],
  shop: ['shop', 'grocery', 'supermarket', 'convenience', 'clothing_store', 'department_store', 'mall', 'bookstore', 'florist', 'butcher'],
};
const POI_COLOR = { food: '#ffb84d', coffee: '#d49a6a', park: '#4ade80', sight: '#c084fc', shop: '#38bdf8' };
const catOfClass = c => Object.keys(POI_GROUPS).find(k => POI_GROUPS[k].includes(c)) || null;

/* Map colours. Dark is true AMOLED: land, water and buildings are pure black and only roads, outlines and labels glow.
   Light is a warm paper tone. Both add 3D buildings that rise when the camera is tilted. */
const MAP_THEME = {
  dark: {
    bg: '#000', text: '#b4b4bc', halo: '#000', road: '#8c8c96', water: '#5b8db5', boundary: '#3a3a42',
    layers: {
      water: { 'fill-color': '#000', 'fill-outline-color': 'rgb(18,40,62)' }, waterway: { 'line-color': 'rgb(16,36,56)' },
      landcover_ice_shelf: { 'fill-color': '#000' }, landcover_glacier: { 'fill-color': '#000' }, landuse_residential: { 'fill-color': '#000' },
      landcover_wood: { 'fill-color': 'rgb(1,7,3)', 'fill-outline-color': 'rgb(10,30,16)' }, landuse_park: { 'fill-color': 'rgb(1,7,3)', 'fill-outline-color': 'rgb(10,30,16)' },
      building: { 'fill-color': '#000', 'fill-outline-color': 'rgb(44,44,52)' },
      'aeroway-area': { 'fill-color': '#000' }, 'aeroway-taxiway': { 'line-color': '#2a2a30' }, 'aeroway-runway-casing': { 'line-color': '#000' }, 'aeroway-runway': { 'line-color': '#3a3a42' },
      road_area_pier: { 'fill-color': '#000' }, road_pier: { 'line-color': '#000' },
      highway_path: { 'line-color': '#26262c' }, highway_minor: { 'line-color': '#36363e' }, highway_major_casing: { 'line-color': '#000' },
      highway_major_inner: { 'line-color': '#5a5a66' }, highway_major_subtle: { 'line-color': '#3a3a42' },
      highway_motorway_casing: { 'line-color': '#000' }, highway_motorway_inner: { 'line-color': '#8f8f9e' }, highway_motorway_subtle: { 'line-color': '#3a3a42' },
      railway_transit: { 'line-color': '#2a2a30' }, railway_transit_dashline: { 'line-color': '#000' }, railway_minor: { 'line-color': '#2a2a30' }, railway_minor_dashline: { 'line-color': '#000' },
      railway: { 'line-color': '#2a2a30' }, railway_dashline: { 'line-color': '#000' },
    },
    extrusion: '#2c2c36', extrusionLow: '#1c1c22', extrusionOpacity: .98, light: { anchor: 'viewport', color: '#ffffff', intensity: .6, position: [1.2, 210, 40] },
  },
  light: {
    bg: '#f6efe3', text: '#4a382a', halo: '#f6efe3', road: '#7a6652', water: '#4f7f8a', boundary: '#c9b79a',
    layers: {
      water: { 'fill-color': '#bcd6da' }, waterway: { 'line-color': '#a9c8cd' },
      park: { 'fill-color': '#dde6c4' }, landcover_ice_shelf: { 'fill-color': '#fbf6ec' }, landcover_glacier: { 'fill-color': '#fbf6ec' },
      landuse_residential: { 'fill-color': '#f2e9d9' }, landcover_wood: { 'fill-color': '#d2dfb4' },
      building: { 'fill-color': '#e9dcc6', 'fill-outline-color': '#d6c6aa' },
      'aeroway-area': { 'fill-color': '#fffaf0' }, 'aeroway-taxiway': { 'line-color': '#e6dac4' }, 'aeroway-runway-casing': { 'line-color': '#e6dac4' }, 'aeroway-runway': { 'line-color': '#fffaf0' },
      road_area_pier: { 'fill-color': '#f6efe3' }, road_pier: { 'line-color': '#f6efe3' },
      highway_path: { 'line-color': '#e8dcc6' }, highway_minor: { 'line-color': '#fffaf0' }, highway_major_casing: { 'line-color': '#dccdb2' },
      highway_major_inner: { 'line-color': '#fff7e6' }, highway_major_subtle: { 'line-color': 'rgba(222,205,176,.7)' },
      highway_motorway_casing: { 'line-color': '#e0b56a' }, highway_motorway_inner: { 'line-color': '#f7c978' }, highway_motorway_subtle: { 'line-color': 'rgba(222,205,176,.7)' },
      tunnel_motorway_casing: { 'line-color': '#dccdb2' }, tunnel_motorway_inner: { 'line-color': '#efe3cc' },
      highway_motorway_bridge_casing: { 'line-color': '#e0b56a' }, highway_motorway_bridge_inner: { 'line-color': '#f7c978' },
      railway_transit: { 'line-color': '#d8ccb6' }, railway_transit_dashline: { 'line-color': '#f6efe3' }, railway_service: { 'line-color': '#d8ccb6' }, railway_service_dashline: { 'line-color': '#f6efe3' },
      railway: { 'line-color': '#d8ccb6' }, railway_dashline: { 'line-color': '#f6efe3' },
    },
    extrusion: '#ddcdaf', extrusionLow: '#efe6d4', extrusionOpacity: .86, light: { anchor: 'viewport', color: '#fff4e0', intensity: .35, position: [1.2, 200, 50] },
  },
};
function restyle(s, dark) {
  const T = MAP_THEME[dark ? 'dark' : 'light'];
  for (const l of s.layers) {
    l.paint = l.paint || {};
    if (l.type === 'background') l.paint['background-color'] = T.bg;
    else if (T.layers[l.id]) Object.assign(l.paint, T.layers[l.id]);
    else if (l.type === 'line' && /^boundary/.test(l.id)) l.paint['line-color'] = T.boundary;
    else if (l.type === 'symbol' && 'text-color' in l.paint) {
      const road = /^highway[-_]name|^road/.test(l.id), water = /water/.test(l.id);
      l.paint['text-color'] = water ? T.water : road ? T.road : T.text;
      l.paint['text-halo-color'] = T.halo;
    }
  }
  // flat footprints below z15, real 3D buildings from z15 up
  const flat = s.layers.find(l => l.id === 'building'); if (flat) flat.maxzoom = 15;
  const at = s.layers.findIndex(l => l.type === 'symbol');
  s.layers.splice(at < 0 ? s.layers.length : at, 0, {
    id: 'building-3d', type: 'fill-extrusion', source: 'openmaptiles', 'source-layer': 'building', minzoom: 15,
    paint: {
      'fill-extrusion-color': ['interpolate', ['linear'], ['coalesce', ['get', 'render_height'], 6], 0, T.extrusionLow, 70, T.extrusion], 'fill-extrusion-opacity': T.extrusionOpacity,
      'fill-extrusion-height': ['coalesce', ['get', 'render_height'], 6], 'fill-extrusion-base': ['coalesce', ['get', 'render_min_height'], 0],
    },
  });
  s.light = T.light;
  return s;
}
function addPoiLayers(s, dark) {
  const font = s.layers.find(l => l.layout?.['text-font'])?.layout['text-font'] || ['Noto Sans Regular'];
  const colorExpr = ['match', ['get', 'class']];
  for (const g of Object.keys(POI_GROUPS)) colorExpr.push(POI_GROUPS[g], POI_COLOR[g]);
  colorExpr.push('#9ca3af');
  s.layers.push(
    { id: 'poi-dots', type: 'circle', source: 'openmaptiles', 'source-layer': 'poi', minzoom: 14.5, filter: ['has', 'name'],
      paint: { 'circle-radius': ['interpolate', ['linear'], ['zoom'], 14.5, 2.5, 17, 4.5, 19, 7], 'circle-opacity': .9, 'circle-color': colorExpr, 'circle-stroke-width': 1, 'circle-stroke-color': dark ? '#000' : '#fff' } },
    { id: 'poi-labels', type: 'symbol', source: 'openmaptiles', 'source-layer': 'poi', minzoom: 16, filter: ['has', 'name'],
      layout: { 'text-field': ['get', 'name'], 'text-font': font, 'text-size': 11, 'text-offset': [0, 1], 'text-anchor': 'top', 'text-optional': true, 'text-max-width': 7 },
      paint: { 'text-color': dark ? '#b5b5bb' : '#444', 'text-halo-color': dark ? '#000' : '#fff', 'text-halo-width': 1.2 } });
  return s;
}
async function buildStyle() {
  const dark = S.theme !== 'light', name = dark ? 'dark' : 'positron';
  try {
    if (!styleCache[name]) { const r = await fetch(`https://tiles.openfreemap.org/styles/${name}`); if (!r.ok) throw 0; styleCache[name] = await r.json(); }
    const s = JSON.parse(JSON.stringify(styleCache[name]));
    return addPoiLayers(restyle(s, dark), dark);
  } catch { toast('Map tiles need an internet connection'); return FALLBACK_STYLE(dark); }
}

let map = null, mapStyleKind = null;
const emptyFC = { type: 'FeatureCollection', features: [] };
function onStyleLoad() {
  map.addSource('routes', { type: 'geojson', data: nav.fc || emptyFC });
  const before = map.getLayer('poi-dots') ? 'poi-dots' : undefined, acc = getAccent();
  map.addLayer({ id: 'route-alt', type: 'line', source: 'routes', filter: ['==', ['get', 'sel'], 0], layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#7b8190', 'line-width': 5, 'line-opacity': .85 } }, before);
  map.addLayer({ id: 'route-casing', type: 'line', source: 'routes', filter: ['==', ['get', 'sel'], 1], layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': S.theme === 'light' ? '#ffffff' : '#000000', 'line-width': ['interpolate', ['linear'], ['zoom'], 10, 7, 16, 13, 19, 20] } }, before);
  map.addLayer({ id: 'route-glow', type: 'line', source: 'routes', filter: ['==', ['get', 'sel'], 1], layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': acc, 'line-width': 14, 'line-opacity': .25, 'line-blur': 3 } }, before);
  map.addLayer({ id: 'route-main', type: 'line', source: 'routes', filter: ['==', ['get', 'sel'], 1], layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': acc, 'line-width': ['interpolate', ['linear'], ['zoom'], 10, 4, 16, 8, 19, 13] } }, before);
  // your car-arrow while navigating: a real 3D object in the map (white base plate under a coloured arrow)
  map.addSource('me3d', { type: 'geojson', data: emptyFC });
  map.addLayer({ id: 'me-arrow', type: 'fill-extrusion', source: 'me3d', paint: { 'fill-extrusion-color': ['get', 'c'], 'fill-extrusion-height': ['get', 'h'], 'fill-extrusion-base': ['get', 'b'], 'fill-extrusion-opacity': 1 } });
  update3dArrow();
}
/* Vehicles for navigation: each is a few extruded shapes in a 1-unit-long box (x = right, y = forward, heights in units).
   Colours: a = your accent, w white, k near-black, g glass, r red, y amber, o orange, d dark orange, p pink. */
const rect = (x0, x1, y0, y1) => [[x0, y0], [x1, y0], [x1, y1], [x0, y1]];
const oval = (cx, cy, rx, ry, n = 14) => Array.from({ length: n }, (_, i) => [cx + rx * Math.cos(i / n * 2 * Math.PI), cy + ry * Math.sin(i / n * 2 * Math.PI)]);
const mirror = pts => pts.map(([x, y]) => [-x, y]);
const wheels = (xo, yf, yb, w = .07, l = .16) => [[-xo - w, -xo, yf], [xo, xo + w, yf], [-xo - w, -xo, yb], [xo, xo + w, yb]]
  .map(([x0, x1, y]) => ({ p: rect(x0, x1, y - l / 2, y + l / 2), b: 0, t: .09, c: 'k' }));
const ARROW = [[0, .5], [.36, -.5], [0, -.24], [-.36, -.5], [0, .5]];
const VEHICLES = {
  arrow: { name: 'Arrow', px: 40, parts: [{ p: ARROW.map(([x, y]) => [x * 1.22, y * 1.22]), b: 0, t: .12, c: 'w' }, { p: ARROW, b: .12, t: .34, c: 'a' }] },
  hatch: { name: 'Hatchback', px: 52, parts: [
    { p: [[-.2, -.5], [.2, -.5], [.22, -.42], [.22, .3], [.17, .5], [-.17, .5], [-.22, .3], [-.22, -.42]], b: .03, t: .15, c: 'a' },
    { p: rect(-.17, .17, -.3, .14), b: .15, t: .27, c: 'g' }, { p: rect(-.15, .15, -.22, .08), b: .27, t: .29, c: 'a' },
    { p: rect(-.17, -.09, .47, .5), b: .08, t: .12, c: 'y' }, { p: rect(.09, .17, .47, .5), b: .08, t: .12, c: 'y' },
    { p: rect(-.19, -.1, -.51, -.48), b: .08, t: .12, c: 'r' }, { p: rect(.1, .19, -.51, -.48), b: .08, t: .12, c: 'r' },
    ...wheels(.19, .3, -.3)] },
  sport: { name: 'Sports car', px: 56, parts: [
    { p: [[-.22, -.5], [.22, -.5], [.24, -.1], [.18, .3], [.08, .5], [-.08, .5], [-.18, .3], [-.24, -.1]], b: .02, t: .11, c: 'a' },
    { p: rect(-.14, .14, -.24, .1), b: .11, t: .2, c: 'g' }, { p: rect(-.12, .12, -.18, .02), b: .2, t: .22, c: 'a' },
    { p: rect(-.23, .23, -.5, -.43), b: .11, t: .17, c: 'k' },
    { p: rect(-.14, -.06, .47, .5), b: .06, t: .1, c: 'w' }, { p: rect(.06, .14, .47, .5), b: .06, t: .1, c: 'w' },
    ...wheels(.2, .32, -.3, .07, .17)] },
  plane: { name: 'Plane', px: 62, parts: [
    { p: [[0, .5], [.07, .35], [.07, -.3], [.03, -.5], [-.03, -.5], [-.07, -.3], [-.07, .35]], b: .04, t: .16, c: 'w' },
    { p: [[0, .15], [.5, -.12], [.5, -.2], [0, -.1], [-.5, -.2], [-.5, -.12]], b: .08, t: .1, c: 'a' },
    { p: [[0, -.36], [.2, -.5], [.2, -.54], [0, -.46], [-.2, -.54], [-.2, -.5]], b: .08, t: .1, c: 'a' },
    { p: rect(-.012, .012, -.5, -.28), b: .16, t: .3, c: 'a' },
    { p: oval(.22, -.06, .03, .06, 8), b: .04, t: .1, c: 'k' }, { p: oval(-.22, -.06, .03, .06, 8), b: .04, t: .1, c: 'k' }] },
  cat: { name: 'Cat', px: 48, parts: [
    { p: oval(0, -.12, .2, .3), b: .04, t: .22, c: 'o' }, { p: rect(-.04, .04, -.32, .1), b: .22, t: .235, c: 'd' },
    { p: oval(0, .27, .17, .15), b: .04, t: .28, c: 'o' },
    { p: [[-.16, .3], [-.11, .47], [-.03, .35]], b: .2, t: .38, c: 'o' }, { p: mirror([[-.16, .3], [-.11, .47], [-.03, .35]]), b: .2, t: .38, c: 'o' },
    { p: rect(-.025, .025, .38, .42), b: .22, t: .3, c: 'p' },
    { p: [[.06, -.38], [.26, -.6], [.32, -.55], [.13, -.33]], b: .04, t: .1, c: 'd' }] },
};
const VCOLOR = { w: '#ffffff', k: '#15151a', g: '#24323d', r: '#ff3b4d', y: '#ffd54f', o: '#f4a340', d: '#c9701a', p: '#ff7a9a' };
let vehiclePreviewUntil = 0;
function arrow3dFC() {
  if (!me || !(nav.active || Date.now() < vehiclePreviewUntil)) return emptyFC;
  const v = VEHICLES[S.vehicle] || VEHICLES.arrow;
  const pose = nav.active ? vehiclePose() : null, plat = pose ? pose.lat : me.lat, plon = pose ? pose.lon : me.lon;
  const mpp = 78271.517 * Math.cos(rad(plat)) / 2 ** map.getZoom(), L = v.px * mpp, br = rad(pose ? pose.h : nav.active ? heading : 0), acc = getAccent();
  return { type: 'FeatureCollection', features: v.parts.map(part => ({
    type: 'Feature', properties: { b: part.b * L, h: part.t * L, c: part.c === 'a' ? acc : VCOLOR[part.c] },
    geometry: { type: 'Polygon', coordinates: [[...part.p, part.p[0]].map(([x, y]) => {
      const dx = x * L, dy = y * L, east = dx * Math.cos(br) + dy * Math.sin(br), north = -dx * Math.sin(br) + dy * Math.cos(br);
      return [plon + deg(east / (R * Math.cos(rad(plat)))), plat + deg(north / R)];
    })] },
  })) };
}
let arrowQueued = false;
function update3dArrow() {
  if (!map || arrowQueued) return; arrowQueued = true;
  requestAnimationFrame(() => { arrowQueued = false; try { map.getSource('me3d')?.setData(arrow3dFC()); } catch {} });
}
async function reloadStyle() {
  const kind = S.theme === 'light' ? 'light' : 'dark';
  if (kind === mapStyleKind) { recolorRoute(); return; }
  mapStyleKind = kind; map.setStyle(await buildStyle());
}
function recolorRoute() {
  const a = getAccent();
  for (const id of ['route-glow', 'route-main']) if (map.getLayer(id)) map.setPaintProperty(id, 'line-color', a);
}
async function initMap() {
  mapStyleKind = S.theme === 'light' ? 'light' : 'dark';
  const style = await buildStyle();
  map = new maplibregl.Map({ container: 'map', style, center: [KL[1], KL[0]], zoom: 13, attributionControl: false, dragRotate: true, pitchWithRotate: true, maxPitch: 72, fadeDuration: 100 });
  map.touchZoomRotate.disableRotation();
  map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right');
  map.on('style.load', onStyleLoad);
  map.on('zoom', update3dArrow);
  map.on('rotate', syncCompass); map.on('pitch', syncCompass);
  map.on('error', e => diag('map', (e && e.error && e.error.message) || 'map error'));
  map.on('dragstart', () => { following = false; $('#locateBtn').classList.remove('on'); if (nav.active || quest.walking) scheduleRecenter(); });
  map.on('move', updateScale);
  map.on('moveend', () => { updateClusters(); });
  map.on('idle', () => { updateClusters(); maybeNearbyFromTiles(); });
  map.on('click', onMapClick);
  setupLongPress();
  updateScale();
}

/* --- scale bar: meters-per-pixel from Web Mercator at current zoom/latitude --- */
function updateScale() {
  const z = map.getZoom(), lat = map.getCenter().lat;
  const mpp = 78271.516964 * Math.cos(rad(lat)) / 2 ** z; // MapLibre uses 512px tiles
  const target = 90 * mpp, nice = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1e6, 2e6];
  let len = nice[0]; for (const n of nice) if (n <= target) len = n;
  $('#scale i').style.width = Math.max(10, len / mpp) + 'px';
  $('#scale span').textContent = len >= 1000 ? `${len / 1000} km` : `${len} m`;
}

/* --- the trio: Cat Club numbers, and hand-offs to Purrcast and Zamoled (docs/TRIO.md) --- */
const TRIO = window.AndroidTrio || null;
/* Keep the screen on while navigating or on a Quest walk (native flag in the app, Screen Wake Lock in a browser). */
let wakeLock = null;
async function keepAwake(on) {
  try { if (TRIO && TRIO.keepAwake) TRIO.keepAwake(!!on); } catch {}
  try {
    if ('wakeLock' in navigator) {
      if (on && !wakeLock) { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener('release', () => { wakeLock = null; }); }
      else if (!on && wakeLock) { const w = wakeLock; wakeLock = null; await w.release(); }
    }
  } catch {}
}
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && (nav.active || quest.on)) keepAwake(true); });
function syncCats() { try { TRIO && TRIO.setCats(S.caught | 0); } catch {} }
/* The day streak (same rules as Purrcast and Zamoled, see streak.js). The shield works while all three apps are installed. */
function allThreeInstalled() { try { return !!TRIO && TRIO.installed('app.skypix.weather') && TRIO.installed('com.nikopick.zamoled'); } catch { return false; } }
function streakText() { const on = allThreeInstalled(), sh = streakLabel(S.streak, on); return 'day streak' + (sh ? ' \u00b7 ' + sh : ''); }
function touchStreak() {
  const today = streakDay(Date.now(), -new Date().getTimezoneOffset() * 60000);
  let n = streakStep(S.streak, today, allThreeInstalled());
  /* The trio shares one streak: take the highest from the other two apps. */
  try {
    const sib = TRIO ? JSON.parse(TRIO.siblings()) : {};
    for (const k of ['purrcast', 'zamoled']) if (sib[k] && sib[k].streak) n = streakAdopt(n, today, sib[k].streak);
  } catch {}
  if (n !== S.streak) { S.streak = n; save(); }
  try { TRIO && TRIO.setStreak(S.streak.count, S.streak.best, S.streak.last, streakText()); } catch {}
}
touchStreak();
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') { touchStreak(); refreshTrio(); } });
/* ---- progress: save a code, restore a code (docs/superpowers/specs/2026-11-17-progress-save-code-design.md) ---- */
const progModal = $('#progModal');
function progClose() { progModal.hidden = true; progModal.innerHTML = ''; }
function progOpen(html) { progModal.innerHTML = '<div class="card">' + html + '</div>'; progModal.hidden = false; }
progModal.addEventListener('click', e => { if (e.target === progModal) progClose(); });
const progDay = () => new Date().toISOString().slice(0, 10);
const progBridge = () => { try { return window.AndroidTrio && window.AndroidTrio.shareText ? window.AndroidTrio : null; } catch { return null; } };
function progDownload(name, mime, body) { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([body], { type: mime })); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 4000); }
function progCard(code) {
  const c = document.createElement('canvas'); c.width = 1080; c.height = 1350; const g = c.getContext('2d');
  g.fillStyle = '#000'; g.fillRect(0, 0, 1080, 1350);
  g.fillStyle = '#3df5d0'; g.font = 'bold 64px sans-serif'; g.fillText('Maps and Cats progress', 80, 190);
  g.fillStyle = '#aeaeb8'; g.font = '36px monospace'; g.fillText('Saved ' + progDay(), 80, 250);
  g.fillStyle = '#f4f4f6'; g.font = '58px monospace'; let line = '', y = 520;
  for (const part of code.split('-')) { const next = line ? line + '-' + part : part; if (g.measureText(next).width > 920 && line) { g.fillText(line, 80, y); y += 92; line = part; } else line = next; }
  if (line) g.fillText(line, 80, y);
  g.fillStyle = '#aeaeb8'; g.font = '38px sans-serif'; g.fillText('Keep this safe. Paste it into', 80, 1120); g.fillText('Restore progress to get your streak back.', 80, 1172);
  return c.toDataURL('image/png');
}
function progIcs(code) { const d = progDay().replace(/-/g, ''); return ['BEGIN:VCALENDAR', 'VERSION:2.0', 'BEGIN:VEVENT', 'UID:mac-progress-' + d + '@mapsandcats', 'DTSTAMP:' + d + 'T000000Z', 'DTSTART;VALUE=DATE:' + d, 'SUMMARY:Maps and Cats progress code', 'DESCRIPTION:' + code, 'END:VEVENT', 'END:VCALENDAR'].join('\r\n'); }
function showSave() {
  const code = saveCodeEncode('MAC1', progressPayload(S)), B = progBridge();
  const acts = [
    ['Share to notes or chat', () => B ? B.shareText('Maps and Cats progress code:\n' + code) : navigator.share ? navigator.share({ text: code }).catch(() => {}) : progCopy(code)],
    ['Save a screenshot card', () => { const url = progCard(code); if (B) B.saveImage('mapsandcats-progress-' + progDay() + '.png', url); else { const a = document.createElement('a'); a.href = url; a.download = 'mapsandcats-progress.png'; a.click(); } }],
    ['Add to calendar', () => B ? B.addCalendar('Maps and Cats progress code', code) : progDownload('mapsandcats-progress.ics', 'text/calendar', progIcs(code))],
    ['Save as a text file', () => B ? B.saveFile('mapsandcats-progress-' + progDay() + '.txt', 'text/plain', 'Maps and Cats progress code\n' + code + '\n') : progDownload('mapsandcats-progress.txt', 'text/plain', code + '\n')],
    ['Full backup file (places and settings)', () => B ? B.saveFile('mapsandcats-backup-' + progDay() + '.json', 'application/json', progressBackup(S)) : progDownload('mapsandcats-backup.json', 'application/json', progressBackup(S))],
    ['Copy', () => progCopy(code)],
  ];
  progOpen('<h3>Keep your streak safe</h3><div class="code" id="progCode"></div>' + acts.map((a, i) => `<button class="btn ghost" data-i="${i}" type="button">${esc(a[0])}</button>`).join('') + '<button class="btn ghost" data-close type="button">Close</button>');
  $('#progCode').textContent = code;
  progModal.querySelectorAll('button[data-i]').forEach(b => b.addEventListener('click', () => { progClose(); try { acts[+b.dataset.i][1](); } catch { toast('No app found for that. Try Copy.'); } }));
  progModal.querySelector('[data-close]').addEventListener('click', progClose);
}
function progCopy(code) { (navigator.clipboard ? navigator.clipboard.writeText(code) : Promise.reject()).then(() => toast('Copied. Paste it somewhere safe.'), () => toast('Copy is blocked here. Select the code and copy it.')); }
function showRestore() {
  const B = progBridge(); let pre = ''; try { const t = B && B.clipboardText ? B.clipboardText() : ''; if (t && t.length < 5000 && saveCodeDecode(t, 'MAC1').kind === 'ok') pre = t; } catch {}
  progOpen('<h3>Restore progress</h3><textarea id="progText" placeholder="MAC1-XXXX-XXXX-..." aria-label="Progress code"></textarea><div class="err" id="progErr" role="alert"></div><label class="chk"><input type="checkbox" id="progSettings"> Also restore my settings (backup files only)</label><button class="btn primary" id="progGo" type="button">Restore</button><button class="btn ghost" id="progFile" type="button">Open file</button><button class="btn ghost" data-close type="button">Cancel</button>');
  $('#progText').value = pre;
  const done = msg => { progClose(); toast(msg, 4000); refreshTrio(); };
  $('#progGo').addEventListener('click', () => { try { const m = progressRestoreText(S, $('#progText').value); save(); done(m); } catch (e) { $('#progErr').textContent = e.message; } });
  $('#progFile').addEventListener('click', () => {
    const withSettings = $('#progSettings').checked, got = text => { try { const m = text.trim().startsWith('{') ? progressRestoreBackup(S, text, withSettings) : progressRestoreText(S, text); save(); done(m); } catch (e) { toast(e.message, 4000); } };
    if (B && B.pickFile) { window.__macFilePicked = got; B.pickFile(); progClose(); }
    else { const i = document.createElement('input'); i.type = 'file'; i.onchange = () => i.files[0] && i.files[0].text().then(got); i.click(); progClose(); }
  });
  progModal.querySelector('[data-close]').addEventListener('click', progClose);
}
$('#progSave').addEventListener('click', showSave);
$('#progRestore').addEventListener('click', showRestore);
const REPO_URL = 'https://github.com/VK-VfX/nikezverapps';
/* A violet ring toward the next 10-day reward, with the day count inside. */
function streakRing(count) {
  const r = 22, c = 2 * Math.PI * r, f = count % 10 / 10, shown = count > 0 && f === 0 ? 1 : f;
  return `<svg class="ring" viewBox="0 0 56 56" role="img" aria-label="${count} day streak, ${10 - count % 10} days to the next reward"><circle cx="28" cy="28" r="${r}" fill="none" stroke="var(--line)" stroke-width="5"/><circle cx="28" cy="28" r="${r}" fill="none" stroke="var(--violet)" stroke-width="5" stroke-linecap="round" stroke-dasharray="${(c * shown).toFixed(1)} ${c.toFixed(1)}" transform="rotate(-90 28 28)"/><text x="28" y="33" text-anchor="middle" font-size="16" font-weight="700" fill="var(--text)">${count}</text></svg>`;
}
const HERO_MOODS = ['smile', 'grin', 'smirk', 'confident', 'angry', 'shocked', 'sad', 'scared', 'loaf'];
/* The hero cat: Purrcast's mood when all three apps are installed, otherwise the locked silhouette. */
function renderClubHero(sib) {
  const box = $('#clubHero'); if (!box) return;
  const open = allThreeInstalled() && S.heroOn !== false, mood = open && sib.purrcast && HERO_MOODS.includes(sib.purrcast.mood) ? sib.purrcast.mood : 'smile';
  box.innerHTML = streakRing(S.streak.count) + `<img class="hero-cat" src="assets/hero/hero-${open ? mood + '-' + Math.floor(Math.random() * 5) : 'locked'}.svg" alt="${open ? 'The hero cat feeling ' + mood : 'A hero cat silhouette'}" width="64" height="64">` +
    `<span class="hero-note">${open ? 'Hero cat: ' + mood : allThreeInstalled() ? 'The hero cat is hidden' : 'Install all three apps to meet the hero cat'}</span>`;
  renderParade(open, mood);
  const row = $('#heroRow'); if (row) { row.hidden = !allThreeInstalled(); $('#heroToggle').checked = S.heroOn !== false; }
}
/* A little walking parade of hero cats: the weather's mood leads, the rest come from the calm set (angry and sad belong to bad weather only). */
const CALM_MOODS = ['smile', 'grin', 'smirk', 'confident', 'shocked', 'loaf'];
function renderParade(open, mood) {
  const box = $('#parade'); if (!box) return;
  if (!open) { box.innerHTML = ''; return; }
  const cats = [mood, ...CALM_MOODS.filter(m => m !== mood).sort((a, b) => (a < b ? -1 : 1)).slice(0, 4)];
  box.style.setProperty('--pw', Math.max(160, box.clientWidth || 300) + 'px');
  const off = Math.floor(Math.random() * 5);   // a different mask on every cat, and a different set each time
  box.innerHTML = cats.map((m, i) => `<div class="walker" style="--i:${i}"><img src="assets/hero/hero-${m}-${(i + off) % 5}.svg" alt="" width="44" height="44"></div>`).join('');
}
async function refreshTrio() {
  if (!TRIO) return;
  syncCats();
  let sib = {}; try { sib = JSON.parse(TRIO.siblings()); } catch {}
  let latest = {};
  try {
    const rs = await (await fetch(RELEASES_URL, { headers: { Accept: 'application/vnd.github+json' } })).json();
    const z = rs.find(x => /^zamoled-v/.test(x.tag_name) && !x.draft && !x.prerelease); if (z) latest.zamoled = z.tag_name.replace('zamoled-v', '');
    latest.purrcast = (await (await fetch('https://raw.githubusercontent.com/VK-VfX/nikezverapps/main/purrcast/dist/update.json')).json()).versionName;
  } catch {}
  const line = (k, el) => {
    const s = sib[k] || {}; let t = '';
    if (s.installed) { t = s.version ? 'v' + s.version : ''; if (s.version && latest[k] && vNewer(latest[k], s.version)) t += ` → v${latest[k]} is out, open to update`; }
    $(el).textContent = t ? '· ' + t : '';
  };
  line('purrcast', '#purrStat'); line('zamoled', '#zamStat');
  const parts = [`${S.streak.count} ${streakText()}`, `${S.caught | 0} cats caught`]; if (sib.purrcast && sib.purrcast.stat) parts.push(sib.purrcast.stat); if (sib.zamoled && sib.zamoled.stat) parts.push(sib.zamoled.stat);
  renderClubHero(sib);
  const club = $('#catClub'); club.textContent = 'Cat Club: ';
  parts.forEach((t, i) => { if (i) club.append(' · '); const s = document.createElement('span'); if (i === 0) s.className = 'streak-v'; s.textContent = t; club.append(s); });
}
$('#weatherHere').addEventListener('click', () => {
  const d = nav.dest; if (!d || !TRIO) return;
  TRIO.openUri(`purrcast://weather?lat=${d.lat}&lon=${d.lon}&name=${encodeURIComponent(d.name || '')}`, REPO_URL + '/releases');
});
if (TRIO) $('#weatherHere').hidden = false;
window.__macOpenPlace = (lat, lon, name) => selectPlace({ name: name || 'Shared place', sub: `${(+lat).toFixed(5)}, ${(+lon).toFixed(5)}`, lat: +lat, lon: +lon }, { quiet: true });
window.__macCheckPending = () => { try { const j = TRIO && TRIO.takePlace(); if (j) { const p = JSON.parse(j); window.__macOpenPlace(p.lat, p.lon, p.name); } } catch {} };

/* --- long-press to drop a pin (MapLibre has no native touch long-press) --- */
let lpFired = 0;
function setupLongPress() {
  const el = map.getCanvasContainer(); let t = null, sx = 0, sy = 0; const down = new Set();
  const cancel = () => { clearTimeout(t); t = null; };
  el.addEventListener('pointerdown', e => {
    down.add(e.pointerId); if (down.size > 1) return cancel();
    sx = e.clientX; sy = e.clientY;
    t = setTimeout(() => {
      t = null; lpFired = Date.now(); haptic(35);
      const r = el.getBoundingClientRect(), ll = map.unproject([sx - r.left, sy - r.top]);
      dropPin(ll.lat, ll.lng);
    }, 550);
  });
  el.addEventListener('pointermove', e => { if (t && Math.hypot(e.clientX - sx, e.clientY - sy) > 10) cancel(); });
  for (const ev of ['pointerup', 'pointercancel']) el.addEventListener(ev, e => { down.delete(e.pointerId); cancel(); });
}
async function dropPin(lat, lon) {
  const place = { name: 'Dropped pin', sub: `${lat.toFixed(5)}, ${lon.toFixed(5)}`, lat, lon };
  selectPlace(place, { quiet: true });
  try {
    const r = await fetch(`${SV().nominatim}/reverse?format=jsonv2&lat=${lat}&lon=${lon}&zoom=18`);
    const j = await r.json(); if (j.display_name && nav.dest === place) {
      const parts = j.display_name.split(', '); place.name = j.name || parts[0]; place.sub = parts.slice(1, 4).join(', ');
      $('#destName').textContent = place.name; $('#destSub').textContent = place.sub;
    }
  } catch {}
}

/* --- map taps: POIs, alternative routes --- */
function onMapClick(e) {
  if (Date.now() - lpFired < 600) return;
  $('#results').hidden = true;
  if (map.getLayer('route-alt')) {
    const f = map.queryRenderedFeatures([[e.point.x - 8, e.point.y - 8], [e.point.x + 8, e.point.y + 8]], { layers: ['route-alt'] })[0];
    if (f) return selectAlt(f.properties.i);
  }
  if (map.getLayer('poi-dots')) {
    const f = map.queryRenderedFeatures([[e.point.x - 10, e.point.y - 10], [e.point.x + 10, e.point.y + 10]], { layers: ['poi-dots'] })[0];
    if (f && f.properties.name) { const [lon, lat] = f.geometry.coordinates; return selectPlace({ name: f.properties.name, sub: pretty(f.properties.subclass || f.properties.class), lat, lon }, { quiet: true }); }
  }
  if (!nav.active) $('#sheet').classList.add('collapsed');
}

/* --- POI clustering: below z14.5, bin viewport POIs into count bubbles --- */
let clusterMarkers = [], clusterSig = '';
function updateClusters() {
  const z = map.getZoom();
  const sig = `${z.toFixed(1)}|${map.getCenter().lng.toFixed(3)}|${map.getCenter().lat.toFixed(3)}|${mapStyleKind}`;
  const sig2 = sig + (nav.dest ? '|route' : '');
  if (sig2 === clusterSig) return; clusterSig = sig2;
  clusterMarkers.forEach(m => m.remove()); clusterMarkers = [];
  if (nav.dest) return; // keep the route readable: no bubbles while a route is open
  if (z >= 14.5 || z < 11.5 || !map.getSource('openmaptiles') || !map.isStyleLoaded()) return;
  let feats = []; try { feats = map.querySourceFeatures('openmaptiles', { sourceLayer: 'poi', filter: ['has', 'name'] }); } catch { return; }
  const b = map.getBounds(), seen = new Set(), bins = new Map(), CAP = 4000; // cap the scan: tile cache counts are unreliable and can be huge
  for (let i = 0; i < feats.length && i < CAP; i++) {
    const g = feats[i].geometry; if (g.type !== 'Point') continue;
    const [lon, lat] = g.coordinates; if (!b.contains([lon, lat])) continue; // bound against the real viewport
    const k = `${feats[i].properties.name}|${lon.toFixed(5)}|${lat.toFixed(5)}`; if (seen.has(k)) continue; seen.add(k);
    const p = map.project([lon, lat]), cell = `${Math.floor(p.x / 90)},${Math.floor(p.y / 90)}`, c = bins.get(cell) || { n: 0, lat: 0, lon: 0 };
    c.n++; c.lat += lat; c.lon += lon; bins.set(cell, c);
  }
  for (const c of [...bins.values()].slice(0, 60)) {
    const el = document.createElement('div'); el.className = 'cluster'; el.textContent = c.n > 99 ? '99+' : c.n;
    const ll = [c.lon / c.n, c.lat / c.n];
    el.addEventListener('click', ev => { ev.stopPropagation(); map.easeTo({ center: ll, zoom: Math.min(16, z + 2.5) }); });
    clusterMarkers.push(new maplibregl.Marker({ element: el }).setLngLat(ll).addTo(map));
  }
}

/* ================= servers ================= */
const trim = u => (u || '').trim().replace(/\/+$/, '');
const SV = () => ({
  photon: trim(S.servers.photon) || 'https://photon.komoot.io',
  nominatim: trim(S.servers.nominatim) || 'https://nominatim.openstreetmap.org',
  osrm: trim(S.servers.osrm),
});
function osrmBase(mode) {
  const o = SV().osrm;
  if (o) return `${o}/route/v1/${mode === 'foot' ? 'foot' : 'driving'}`;
  return mode === 'foot' ? 'https://routing.openstreetmap.de/routed-foot/route/v1/driving' : 'https://router.project-osrm.org/route/v1/driving';
}

/* ================= location ================= */
let meMarker = null, simTimer = null, simHeading = Math.random() * 6.28, watchId = null;
const firstFix = { done: false };

/* ================= driving extras: stops along the route, speed limit, cat co-pilot voice, rain at the destination ================= */
const OVERPASS = ['https://overpass-api.de/api/interpreter', 'https://overpass.kumi.systems/api/interpreter'];
async function overpass(q) {
  for (const url of OVERPASS) {
    const ac = new AbortController(), t = setTimeout(() => ac.abort(), 14000);
    try {
      const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'data=' + encodeURIComponent(q), signal: ac.signal });
      if (r.ok) return await r.json();
    } catch {} finally { clearTimeout(t); }
  }
  throw new Error('overpass unavailable');
}

/* Stops along the way: fuel, coffee and rest areas near the part of the route still ahead of you. One tap adds one as a stop. */
const STOP_KINDS = {
  fuel: { icon: ic('fuel'), label: 'Fuel', sel: ['[amenity=fuel]'] },
  coffee: { icon: ic('coffee'), label: 'Coffee', sel: ['[amenity=cafe]'] },
  rest: { icon: ic('rest'), label: 'Rest', sel: ['[highway=rest_area]', '[amenity=toilets]', '[highway=services]'] },
};
let alongHits = [];
function routeAhead(maxPts = 45) {
  const line = nav.routes[nav.sel]?.geometry.coordinates || []; if (line.length < 2) return [];
  let start = 0;
  if (nav.active && me) { let best = Infinity; line.forEach((c, i) => { const d = dist([me.lat, me.lon], [c[1], c[0]]); if (d < best) { best = d; start = i; } }); }
  const rest = line.slice(start), step = Math.max(1, Math.ceil(rest.length / maxPts)), pts = [];
  for (let i = 0; i < rest.length; i += step) pts.push([rest[i][1], rest[i][0]]);
  const last = rest[rest.length - 1]; if (last) pts.push([last[1], last[0]]);
  let acc = 0; return pts.map((p, i) => { if (i) acc += dist(pts[i - 1], p); return { lat: p[0], lon: p[1], at: acc }; });
}
async function findStops(kind) {
  const k = STOP_KINDS[kind], pts = routeAhead(); if (!k || pts.length < 2) return [];
  const poly = pts.map(p => p.lat.toFixed(5) + ',' + p.lon.toFixed(5)).join(',');
  const j = await overpass(`[out:json][timeout:20];(${k.sel.map(x => `nwr(around:600,${poly})${x};`).join('')});out center 80;`);
  const seen = [], out = [];
  for (const e of j.elements || []) {
    const lat = e.lat ?? e.center?.lat, lon = e.lon ?? e.center?.lon; if (lat == null || lon == null) continue;
    let bi = 0, bd = Infinity; pts.forEach((p, i) => { const d = dist([lat, lon], [p.lat, p.lon]); if (d < bd) { bd = d; bi = i; } });
    if (seen.some(s => dist(s, [lat, lon]) < 60)) continue; seen.push([lat, lon]);
    const t = e.tags || {};
    out.push({ name: t.name || t.brand || t.operator || k.label, sub: [t['addr:street'], t.opening_hours ? 'hours listed' : ''].filter(Boolean).join(' · '), lat, lon, ahead: pts[bi].at, off: bd, icon: k.icon });
  }
  return out.sort((a, b) => a.ahead - b.ahead).slice(0, 12);
}
async function showAlong(kind) {
  const list = $('#alongList'); $$('#alongChips .chip').forEach(c => c.classList.toggle('on', c.dataset.along === kind));
  list.innerHTML = '<li class="empty">Looking along your route…</li>';
  try {
    alongHits = await findStops(kind);
    list.innerHTML = alongHits.length ? alongHits.map((p, i) => `<li data-i="${i}"><div class="ico">${p.icon}</div><div class="li-main"><div class="li-title">${esc(p.name)}</div><div class="li-sub">${fmtDist(p.ahead)} ahead · ${fmtDist(p.off)} off the route${p.sub ? ' · ' + esc(p.sub) : ''}</div></div><button class="chip" data-add="${i}">${ic('plus')} Add</button></li>`).join('')
      : '<li class="empty">Nothing found near the route</li>';
  } catch { list.innerHTML = '<li class="empty">Couldn’t search right now — check your connection</li>'; }
}
function addAlongStop(p) {
  nav.wps.push({ name: p.name, sub: p.sub || '', lat: p.lat, lon: p.lon }); renderStops(); drawMarkers(); haptic(20);
  toast(`Added ${p.name} as a stop 😸`, 2200); $('#along').hidden = true;
  if (nav.active) { nav.lastReroute = 0; reroute(); } else fetchRoute();
}
function openAlong(kind) { if (!nav.routes[nav.sel]) return toast('Pick a destination first'); $('#along').hidden = false; showAlong(kind || 'fuel'); }

/* Speed limit on a road sign, read from OpenStreetMap's maxspeed tags (the free routing service has no limits). Not every road is tagged. */
const limit = { v: null, at: 0, lat: 0, lon: 0 };
function parseLimit(s) { const m = /^(\d{2,3})\s*(mph)?$/i.exec(String(s || '').trim()); if (!m) return null; const v = m[2] ? Math.round(+m[1] * 1.609) : +m[1]; return v >= 5 && v <= 140 ? v : null; }
async function refreshLimit() {
  if (S.limitSign === false || !nav.active || !me || nav.mode === 'foot') return;
  if (Date.now() - limit.at < 20000 && dist([me.lat, me.lon], [limit.lat, limit.lon]) < 200) return;
  limit.at = Date.now(); limit.lat = me.lat; limit.lon = me.lon;
  try {
    const j = await overpass(`[out:json][timeout:10];way(around:25,${me.lat},${me.lon})[highway][maxspeed];out tags 6;`);
    limit.v = (j.elements || []).map(e => parseLimit(e.tags?.maxspeed)).find(Boolean) || null;
  } catch { limit.v = null; }
  renderSpeedo();
}

/* Cat co-pilot: the same directions with a little purr. */
let catN = 0;
const CAT_FAR = ['Purr… ', 'Meow! ', 'Mrrp… ', 'Nya! ', ''], CAT_NOW = [' Meow!', ' Purr-fect.', '', ' Mrrp!', ''];
function catSay(kind, text) {
  if (S.catVoice === false) return text; catN++;
  if (kind === 'far') return CAT_FAR[catN % CAT_FAR.length] + text;
  if (kind === 'start') return `Purr… let's go! ${text}`;
  if (kind === 'arrive') return 'Purr-fect! You have arrived. Meow!';
  return text + CAT_NOW[catN % CAT_NOW.length];
}

/* Weather where you are going, for the time you will get there (the same free forecast Purrcast uses). */
const wxCache = {};
async function destWeather() {
  const r = nav.routes[nav.sel], d = nav.dest, el = $('#destWx'); nav.wx = null; el.textContent = ''; el.classList.remove('wet'); $('#tripWx').textContent = '—';
  if (!r || !d) return;
  const key = d.lat.toFixed(2) + ',' + d.lon.toFixed(2);
  try {
    let j = wxCache[key] && Date.now() - wxCache[key].t < 15 * 60000 ? wxCache[key].j : null;
    if (!j) { j = await (await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${d.lat}&longitude=${d.lon}&hourly=temperature_2m,precipitation_probability,weather_code&timezone=auto&forecast_days=2`)).json(); wxCache[key] = { t: Date.now(), j }; }
    const arrive = Date.now() + (r.duration || 0) * 1000, off = (j.utc_offset_seconds || 0) * 1000; let best = 0, bd = Infinity;
    j.hourly.time.forEach((t, i) => { const dd = Math.abs(Date.parse(t + 'Z') - off - arrive); if (dd < bd) { bd = dd; best = i; } });
    const pop = Math.round(j.hourly.precipitation_probability[best] ?? 0), code = j.hourly.weather_code[best], t = Math.round(j.hourly.temperature_2m[best]);
    const rain = pop >= 50 || (code >= 51 && code <= 67) || (code >= 80 && code <= 99), storm = code >= 95;
    nav.wx = { rain, pop, t };
    el.innerHTML = rain ? `${ic('rain')} Rain likely when you arrive · ${pop}% · ${t}°` : `${ic(code <= 1 ? 'sun' : 'cloud')} ${t}° and dry when you arrive${pop >= 20 ? ` (${pop}% chance of rain)` : ''}`;
    el.classList.toggle('wet', rain); $('#tripWx').textContent = `${pop}%`; $('#tripWx').parentElement.classList.toggle('wet', rain);
  } catch { el.textContent = ''; }
}

/* ================= navigation that behaves like Waze / Google Maps =================
   Your position is snapped to the route line (so the arrow rides the road, not the GPS noise), the arrow moves smoothly between fixes,
   the heading follows the road, distances are measured along the route, the travelled part of the line disappears, voice prompts
   come earlier the faster you drive, the camera zooms in for turns, and it re-centres by itself after you look around. */
function prepRoute() {
  const r = nav.routes[nav.sel]; if (!r) return false; if (nav._prep === r && nav.cum) return true;
  const line = r.geometry.coordinates, cum = [0];
  for (let i = 1; i < line.length; i++) cum.push(cum[i - 1] + dist([line[i - 1][1], line[i - 1][0]], [line[i][1], line[i][0]]));
  nav.cum = cum; nav._prep = r; nav.snap = null; nav.routeLen = cum[cum.length - 1] || r.distance || 1;
  const sum = nav.steps.reduce((a, st) => a + (st.distance || 0), 0) || nav.routeLen, k = nav.routeLen / sum; let a = 0;
  nav.stepAt = nav.steps.map(st => { const v = a * k; a += st.distance || 0; return v; }); // metres along the route where each step begins
  return true;
}
function snapToRoute() {
  if (!me || !prepRoute()) return null;
  const line = nav.routes[nav.sel].geometry.coordinates, n = line.length; if (n < 2) return null;
  const from = nav.snap ? Math.max(0, nav.snap.idx - 3) : 0, to = nav.snap ? Math.min(n - 2, nav.snap.idx + 60) : n - 2;
  const k = Math.cos(rad(me.lat)) * 111320, m = 110540; let best = null;
  for (let i = from; i <= to; i++) {
    const a = line[i], b = line[i + 1], px = (me.lon - a[0]) * k, py = (me.lat - a[1]) * m, bx = (b[0] - a[0]) * k, by = (b[1] - a[1]) * m, L = bx * bx + by * by;
    const t = L ? Math.max(0, Math.min(1, (px * bx + py * by) / L)) : 0, d = Math.hypot(px - t * bx, py - t * by);
    if (!best || d < best.d) best = { idx: i, t, d, lat: a[1] + (b[1] - a[1]) * t, lon: a[0] + (b[0] - a[0]) * t, along: nav.cum[i] + (nav.cum[i + 1] - nav.cum[i]) * t };
  }
  if (!best || best.d > Math.max(30, (me.acc || 0) * 1.3)) { nav.snap = null; return null; } // off the road: raw position, rerouting takes over
  if (nav.snap && best.along < nav.snap.along) best.along = nav.snap.along; // never slide backwards along the route
  nav.snap = best; return best;
}
function roadHeading(sn) { // the direction of the road a few metres ahead, so the arrow does not wobble with GPS
  const line = nav.routes[nav.sel].geometry.coordinates; let j = sn.idx + 1;
  while (j < line.length - 1 && nav.cum[j] < sn.along + 14) j++;
  return bearingDeg({ lat: sn.lat, lon: sn.lon }, { lat: line[j][1], lon: line[j][0] });
}
function lerpAngle(a, b, t) { return (a + angDiff(a, b) * t + 360) % 360; }
function vehiclePose() { // the arrow glides from where it was to where it is
  const p = nav.pose; if (!p) return null;
  const t = Math.min(1, (performance.now() - p.t0) / 1000);
  return { lat: p.from.lat + (p.to.lat - p.from.lat) * t, lon: p.from.lon + (p.to.lon - p.from.lon) * t, h: lerpAngle(p.from.h, p.to.h, t) };
}
function setPoseTarget(to) { const cur = vehiclePose(); nav.pose = { from: cur || to, to, t0: performance.now() }; heading = to.h; }
function trimTravelled(sn) { // draw only the route still ahead
  try { const line = nav.routes[nav.sel].geometry.coordinates;
    map.getSource('routes')?.setData({ type: 'FeatureCollection', features: [{ type: 'Feature', properties: { i: nav.sel, sel: 1 }, geometry: { type: 'LineString', coordinates: [[sn.lon, sn.lat], ...line.slice(sn.idx + 1)] } }] }); } catch {}
}
let recenterT = null;
function scheduleRecenter(ms = 8000) {
  clearTimeout(recenterT);
  recenterT = setTimeout(() => { if (!nav.active && !quest.walking) return; following = true; $('#locateBtn').classList.add('on'); nav.active ? navCamera(700) : walkCamera(700); }, ms);
}
function routeOverview() {
  if (!nav.active || !nav.routes[nav.sel]) return; following = false; $('#locateBtn').classList.remove('on');
  const b = new maplibregl.LngLatBounds(); nav.routes[nav.sel].geometry.coordinates.slice(nav.snap?.idx || 0).forEach(c => b.extend(c));
  map.fitBounds(b, { padding: { top: 170, bottom: 150, left: 40, right: 40 }, pitch: 0, bearing: 0, maxZoom: 17, duration: 800 });
  scheduleRecenter(9000);
}
setInterval(() => { if (nav.active && nav.pose) update3dArrow(); }, 70);

/* Speedometer, and a meow when you reach your chosen speed (80 km/h unless you change it in settings). */
let speedMs = 0, meowArmed = true;
function updateSpeed(prev, cur, spd) {
  let v = Number.isFinite(spd) && spd >= 0 ? spd : null;
  if (v === null && prev) { const dt = (cur.ts - prev.ts) / 1000; if (dt > 0.3 && dt < 10) v = dist([prev.lat, prev.lon], [cur.lat, cur.lon]) / dt; }
  if (v === null) return;
  speedMs = v < 0.6 ? 0 : speedMs + (v - speedMs) * 0.6; // stand still = 0, light smoothing otherwise
  renderSpeedo(); meowCheck();
}
function renderSpeedo() {
  const el = $('#speedo'); if (!el) return;
  const k = speedMs * 3.6, at = +S.meowAt;
  el.hidden = !(S.speedo !== false && (nav.active || k >= 3));
  $('#speedoNum').textContent = Math.round(k);
  el.classList.toggle('fast', at > 0 && k >= at);
  const lim = $('#limit'); if (lim) { const show = nav.active && S.limitSign !== false && limit.v; lim.hidden = !show; if (show) { $('#limitNum').textContent = limit.v; el.classList.toggle('over', k > limit.v + 3); } else el.classList.remove('over'); }
}
function meowCheck() {
  const k = speedMs * 3.6, at = +S.meowAt; if (!(at > 0)) return;
  if (k >= at && meowArmed) { meowArmed = false; meow(); } else if (k < at - 5) meowArmed = true;
}
function meow() {
  try {
    const ac = purr.ac || (purr.ac = new (window.AudioContext || window.webkitAudioContext)()); if (ac.state === 'suspended') ac.resume();
    const t = ac.currentTime, o = ac.createOscillator(), f = ac.createBiquadFilter(), g = ac.createGain();
    o.type = 'sawtooth';
    o.frequency.setValueAtTime(420, t); o.frequency.linearRampToValueAtTime(760, t + .16); o.frequency.linearRampToValueAtTime(980, t + .26); o.frequency.exponentialRampToValueAtTime(380, t + .7);
    f.type = 'bandpass'; f.Q.value = 4; f.frequency.setValueAtTime(700, t); f.frequency.linearRampToValueAtTime(1800, t + .22); f.frequency.exponentialRampToValueAtTime(900, t + .7);
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(.35, t + .06); g.gain.setValueAtTime(.35, t + .3); g.gain.exponentialRampToValueAtTime(.001, t + .75);
    o.connect(f).connect(g).connect(ac.destination); o.start(t); o.stop(t + .8);
  } catch {}
  haptic(40); toast(`Meow! ${Math.round(+S.meowAt)} km/h 😼`, 2200);
}
setInterval(() => { if (me && Date.now() - me.ts > 6000 && speedMs) { speedMs = 0; renderSpeedo(); } }, 2000);

/* 3D navigation: the camera tilts, turns to face where you are heading and keeps you in the lower part of the screen. */
let view3d = S.view3d !== false, heading = 0;
const angDiff = (a, b) => ((b - a + 540) % 360) - 180;
function bearingDeg(a, b) {
  const y = Math.sin(rad(b.lon - a.lon)) * Math.cos(rad(b.lat));
  const x = Math.cos(rad(a.lat)) * Math.sin(rad(b.lat)) - Math.sin(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.cos(rad(b.lon - a.lon));
  return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}
let hdAnchor = null;
function updateHeading(prev, cur) { // measured from a point a few metres back, so a slow walk still turns the map
  if (!hdAnchor) { hdAnchor = cur; return; }
  if (dist([hdAnchor.lat, hdAnchor.lon], [cur.lat, cur.lon]) >= 5) { heading = (heading + angDiff(heading, bearingDeg(hdAnchor, cur)) * 0.5 + 360) % 360; hdAnchor = cur; }
}
const NO_PAD = { top: 0, bottom: 0, left: 0, right: 0 };
// the faster you go, the further the camera pulls back so you see the road ahead
const navZoom = () => Math.min(18.2, 17.4 - Math.min(1.6, speedMs * 3.6 / 70) + (nav.dNext < 200 ? (1 - nav.dNext / 200) * 0.8 : 0));
// while navigating, hide the shop dots and names so the road, the route and the next turn stand out
function navDeclutter(on) {
  for (const id of ['poi-dots', 'poi-labels']) { try { if (map.getLayer(id)) map.setLayoutProperty(id, 'visibility', on ? 'none' : 'visible'); } catch {} }
  // buildings step back so the route and the next turn read clearly
  try { if (map.getLayer('building-3d')) map.setPaintProperty('building-3d', 'fill-extrusion-opacity', on ? 0.5 : (S.theme === 'light' ? 0.86 : 0.98)); } catch {}
}
function navCamera(duration = 900) {
  if (!map || !me) return;
  const c = nav.pose ? nav.pose.to : me;
  if (view3d) map.easeTo({ center: [c.lon, c.lat], bearing: heading, pitch: nav.dNext < 140 ? 50 : 60, zoom: navZoom(), padding: { top: Math.round(innerHeight * .38), bottom: 0, left: 0, right: 0 }, duration, easing: t => t });
  else map.easeTo({ center: [c.lon, c.lat], bearing: 0, pitch: 0, zoom: 17, padding: NO_PAD, duration });
}
/* Quest walking mode: as soon as the GPS sees you walking, the map tilts, turns to face where you are heading and follows you. */
function walkCamera(duration = 900) {
  if (!map || !me) return;
  map.easeTo({ center: [me.lon, me.lat], bearing: heading, pitch: 55, zoom: 18, padding: { top: Math.round(innerHeight * .3), bottom: 0, left: 0, right: 0 }, duration, easing: t => t });
}
function setWalking(on) {
  if (quest.walking === on) return; quest.walking = on; document.body.classList.toggle('walking', on);
  if (!meMarker) return; meMarker.getElement().classList.toggle('arrow', on && !nav.active); meMarker.setRotation(on ? heading : 0);
  if (on) { toast('Walking mode 😸', 1800); following = true; $('#locateBtn').classList.add('on'); walkCamera(700); }
  else if (!nav.active) { map.easeTo({ bearing: 0, pitch: 0, padding: NO_PAD, duration: 700 }); }
}
function walkWatch(cur) { // is the phone really walking? ~10 m at a walking pace within 20 s
  const w = quest.wAnchor || (quest.wAnchor = { lat: cur.lat, lon: cur.lon, ts: cur.ts });
  const d = dist([w.lat, w.lon], [cur.lat, cur.lon]), dt = (cur.ts - w.ts) / 1000;
  if (dt > 20) { quest.wAnchor = { lat: cur.lat, lon: cur.lon, ts: cur.ts }; if (d < 4 && quest.walking && Date.now() - (quest.lastMove || 0) > 30000) setWalking(false); return; }
  if (cur.acc <= 50 && d >= 10 && d / Math.max(1, dt) <= 3.2) { quest.lastMove = Date.now(); quest.wAnchor = { lat: cur.lat, lon: cur.lon, ts: cur.ts }; if (!quest.walking && quest.on) setWalking(true); }
  else if (quest.walking && d >= 4) quest.lastMove = Date.now();
}
function resetCamera() { map.easeTo({ pitch: 0, bearing: 0, padding: NO_PAD, duration: 600 }); }
function onPos(lat, lon, acc, ts = Date.now(), spd) {
  const prev = me; me = { lat, lon, acc, ts };
  updateHeading(prev, me); updateSpeed(prev, me, spd);
  if (nav.active) { const sn = snapToRoute(); setPoseTarget(sn ? { lat: sn.lat, lon: sn.lon, h: roadHeading(sn) } : { lat: me.lat, lon: me.lon, h: heading }); }
  if (!map) return;
  if (!meMarker) {
    const el = document.createElement('div'); el.className = 'me';
    el.innerHTML = '<i></i><svg class="navarrow" viewBox="0 0 48 48" width="52" height="52"><path d="M24 4 L40 41 L24 33 L8 41 Z" stroke-linejoin="round"/></svg>';
    meMarker = new maplibregl.Marker({ element: el, rotationAlignment: 'map', pitchAlignment: 'map' }).setLngLat([lon, lat]).addTo(map);
  } else meMarker.setLngLat([lon, lat]);
  // while navigating the dot becomes an arrow that points the way you are heading (it lies flat on the road in 3D)
  const has3d = !!map.getLayer('me-arrow');
  meMarker.getElement().classList.toggle('arrow', (!!nav.active && !has3d) || (!!quest.walking && !nav.active)); // the flat arrow is only the fallback
  meMarker.getElement().classList.toggle('hide', !!nav.active && has3d);
  meMarker.setRotation(nav.active || quest.walking ? heading : 0);
  update3dArrow();

  if (!firstFix.done) {
    firstFix.done = true; following = true; $('#locateBtn').classList.add('on');
    map.jumpTo({ center: [lon, lat], zoom: quest.on ? 17 : 16 });
    $('#locLabel').textContent = 'Around your location';
    resolveCountry(); resolveHere(); renderNearby();
    if (quest.on) topUpCats();
  } else if (following) { if (nav.active) navCamera(900); else if (quest.walking) walkCamera(900); else map.easeTo(quest.on ? { center: [lon, lat], pitch: 0, bearing: 0, padding: NO_PAD, duration: 600 } : { center: [lon, lat], duration: 600 }); }

  if (quest.on) { walkWatch(me); questTick(prev, me); }
  if (nav.active) navTick();
}
function startLocation() {
  if (!('geolocation' in navigator)) { $('#locLabel').textContent = 'Location not supported'; return; }
  if (watchId != null) navigator.geolocation.clearWatch(watchId);
  watchId = navigator.geolocation.watchPosition(
    p => { if (!S.sim) onPos(p.coords.latitude, p.coords.longitude, p.coords.accuracy || 20, p.timestamp, p.coords.speed); },
    err => { diag('geo', 'error code ' + err.code); $('#locLabel').textContent = err.code === 1 ? 'Location is off — tap ◎ to retry' : 'Can’t find you yet…'; if (!me) renderNearby(); },
    { enableHighAccuracy: true, maximumAge: 2000, timeout: 25000 });
}
function setSim(on) {
  S.sim = on; save(); clearInterval(simTimer); simTimer = null; if (!on) return;
  if (!me) onPos(KL[0], KL[1], 8);
  simTimer = setInterval(() => {
    simHeading += (Math.random() - .5) * .6;
    const p = offset([me.lat, me.lon], 3.1, simHeading); // brisk march, just under the walking cap
    onPos(p[0], p[1], 8);
  }, 1000);
}

/* ================= nearby (vector-tile POIs: no extra service) ================= */
const CATS_UI = [{ id: 'all', label: 'All', icon: ic('all') }, { id: 'food', label: 'Food', icon: ic('food') }, { id: 'coffee', label: 'Coffee', icon: ic('coffee') },
  { id: 'park', label: 'Parks', icon: ic('park') }, { id: 'sight', label: 'Sights', icon: ic('sight') }, { id: 'shop', label: 'Shops', icon: ic('shop') }];
const ICON = { coffee: ic('coffee'), food: ic('food'), park: ic('park'), sight: ic('sight'), shop: ic('shop') };
let nearby = [], nearbyFilter = 'all', nearbyAt = null, nearbyTries = 0;

function maybeNearbyFromTiles() {
  if (!me || !map.isStyleLoaded() || !map.getSource('openmaptiles')) return;
  if (nearbyAt && dist([me.lat, me.lon], nearbyAt) < 300 && nearby.length) return;
  if (nearbyTries > 6 && nearby.length) return;
  const feats = map.querySourceFeatures('openmaptiles', { sourceLayer: 'poi', filter: ['has', 'name'] });
  const here = [me.lat, me.lon], seen = new Set(), out = [];
  for (const f of feats.slice(0, 6000)) {
    if (f.geometry.type !== 'Point') continue; const cat = catOfClass(f.properties.class); if (!cat) continue;
    const [lon, lat] = f.geometry.coordinates, d = dist(here, [lat, lon]); if (d > 1500) continue;
    const k = f.properties.name + cat; if (seen.has(k)) continue; seen.add(k);
    out.push({ id: `${lat},${lon}`, name: f.properties.name, lat, lon, cat, kind: pretty(f.properties.subclass || f.properties.class), d });
  }
  nearbyTries++;
  if (!out.length) return;
  nearby = out.sort((a, b) => a.d - b.d); nearbyAt = here; renderNearby();
}
function renderNearby() {
  const savedChips = [S.saved.home && ['home', ic('home') + ' Home'], S.saved.work && ['work', ic('work') + ' Work']].filter(Boolean);
  $('#chips').innerHTML = savedChips.map(([k, l]) => `<button class="chip" data-saved="${k}">${l}</button>`).join('') +
    CATS_UI.map(c => `<button class="chip ${c.id === nearbyFilter ? 'on' : ''}" data-c="${c.id}">${c.icon} ${c.label}</button>`).join('');
  let html;
  if (!me) html = '<li class="empty">Allow location to see places near you.<br>You can still search above.</li>';
  else if (!nearby.length) html = '<li class="empty">Sniffing out places nearby… 😸</li>';
  else {
    const list = nearby.filter(p => nearbyFilter === 'all' || p.cat === nearbyFilter).slice(0, 40);
    html = list.length ? list.map(p => `<li data-id="${esc(p.id)}"><div class="ico">${ICON[p.cat]}</div><div class="li-main"><div class="li-title">${esc(p.name)}</div><div class="li-sub">${esc(p.kind)}</div></div><div class="li-dist">${fmtDist(dist([me.lat, me.lon], [p.lat, p.lon]))}</div></li>`).join('')
      : '<li class="empty">Nothing here. Try another category.</li>';
  }
  $('#places').innerHTML = html;
}
$('#chips').addEventListener('click', e => {
  const b = e.target.closest('.chip'); if (!b) return;
  if (b.dataset.saved) return choose(S.saved[b.dataset.saved]);
  nearbyFilter = b.dataset.c; renderNearby();
});
$('#places').addEventListener('click', e => {
  const li = e.target.closest('li[data-id]'); if (!li) return;
  const p = nearby.find(x => x.id === li.dataset.id); if (p) choose({ name: p.name, lat: p.lat, lon: p.lon, sub: p.kind });
});

/* ================= search: Photon + Nominatim (open source), blended ================= */
const dedupeKey = (lat, lon) => `${Math.round(lat * 1000)},${Math.round(lon * 1000)}`; // ~111 m grid
async function resolveCountry() {
  if (!me) return;
  const c = S.country; if (c && dist([c.lat, c.lon], [me.lat, me.lon]) < 300000) return;
  try {
    const j = await (await fetch(`${SV().nominatim}/reverse?format=jsonv2&lat=${me.lat}&lon=${me.lon}&zoom=3&addressdetails=1`)).json();
    const cc = j.address?.country_code; if (cc) { S.country = { cc, lat: me.lat, lon: me.lon }; save(); }
  } catch {}
}
/* "Near you" names where you are the way Purrcast does: the town (never a council) and the state, e.g. Shah Alam, Selangor. */
async function resolveHere() {
  if (!me) return;
  const h = S.here; if (h && dist([h.lat, h.lon], [me.lat, me.lon]) < 3000) { showHere(h.label); return; }
  try {
    const j = await (await fetch(`${SV().nominatim}/reverse?format=jsonv2&lat=${me.lat}&lon=${me.lon}&zoom=14&addressdetails=1&accept-language=${encodeURIComponent(UI_LANG + ',en')}`)).json();
    const label = resultSub({ address: Object.assign({}, j.address, { cc: j.address?.country_code }), userCc: j.address?.country_code, name: '' }); if (!label) return;
    S.here = { label, lat: me.lat, lon: me.lon }; save(); showHere(label);
  } catch {}
}
function showHere(label) { if (label && !nav.active) $('#locLabel').textContent = 'Near ' + label; }
function searchOrigin() { if (me) return [me.lat, me.lon]; const c = map.getCenter(); return [c.lat, c.lng]; }

const UI_LANG = (navigator.language || 'en').toLowerCase();
const PHOTON_LANG = ['en', 'de', 'fr', 'it'].includes(UI_LANG.slice(0, 2)) ? UI_LANG.slice(0, 2) : 'en'; // Photon only localises these
async function photonSearch(q, o, signal) {
  const r = await fetch(`${SV().photon}/api/?q=${encodeURIComponent(q)}&limit=8&lat=${o[0]}&lon=${o[1]}&lang=${PHOTON_LANG}`, { signal });
  if (!r.ok) throw new Error(r.status);
  return (await r.json()).features.map(f => {
    const p = f.properties, name = p.name || [p.street, p.housenumber].filter(Boolean).join(' ');
    const cc = (p.countrycode || '').toLowerCase();
    // the line under the result points at the town, the city or the state: never a council, never a county on its own
    return { name, sub: resultSub({ name, street: p.name ? p.street : null, address: { city: p.city, suburb: p.locality || p.district, state: p.state, country: p.country, cc }, userCc: S.country?.cc }),
      lat: f.geometry.coordinates[1], lon: f.geometry.coordinates[0], cc: (p.countrycode || '').toLowerCase() || null, src: 'photon', kind: `${p.osm_key}:${p.osm_value}` };
  }).filter(x => x.name);
}
async function nominatimSearch(q, o, signal) {
  const vb = `${o[1] - .5},${o[0] + .5},${o[1] + .5},${o[0] - .5}`;
  const r = await fetch(`${SV().nominatim}/search?format=jsonv2&limit=8&addressdetails=1&accept-language=${encodeURIComponent(UI_LANG + ',en')}&q=${encodeURIComponent(q)}&viewbox=${vb}`, { signal });
  if (!r.ok) throw new Error(r.status);
  return (await r.json()).map(x => { const parts = x.display_name.split(', '), nm = x.name || parts[0];
    return { name: nm, sub: resultSub({ name: nm, address: Object.assign({}, x.address, { cc: x.address?.country_code }), userCc: S.country?.cc }) || parts.slice(1, 4).join(', '), lat: +x.lat, lon: +x.lon, cc: x.address?.country_code || null, src: 'nominatim', kind: `${x.category}:${x.type}`, imp: +x.importance || 0 }; });
}
/* --- ranking: text relevance + distance + provider agreement + prominence (not distance alone) --- */
const norm = t => String(t || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').trim();
function relevance(q, r) {
  const nq = norm(q), nn = norm(r.name); if (!nq || !nn) return 0;
  if (nn === nq) return 1;
  if (nn.startsWith(nq)) return .88;
  const toks = nq.split(' '), words = nn.split(' ');
  if (toks.every(t => words.some(w => w.startsWith(t)))) return .75;
  if (nn.includes(nq)) return .6;
  const hay = nn + ' ' + norm(r.sub);
  return .15 + .35 * toks.filter(t => hay.includes(t)).length / toks.length;
}
const LANDMARK = /^(tourism:(attraction|museum|viewpoint|gallery|zoo|theme_park)|aeroway:|railway:(station|halt)|shop:mall|amenity:(place_of_worship|university|hospital|marketplace)|leisure:(park|stadium)|building:(mosque|church|temple))/;
const MINOR = /^(highway:|place:(house|house_number)|building:(yes|house|residential))/;
function mergeRank(q, o, lists) {
  const keys = new Map(), out = [], hasDigit = /\d/.test(q);
  for (const list of lists) for (const r of list || []) {
    const k = dedupeKey(r.lat, r.lon), ex = keys.get(k);
    if (ex) { ex.agree++; continue; }           // same place from another provider (~111 m grid) → confidence, not a duplicate row
    r.agree = 1; keys.set(k, r); out.push(r);
  }
  for (const r of out) {
    r.d = dist(o, [r.lat, r.lon]); r.rel = relevance(q, r);
    const boost = (LANDMARK.test(r.kind || '') ? .12 : 0) - (!hasDigit && MINOR.test(r.kind || '') ? .08 : 0) + (r.imp || 0) * .15;
    r.score = .55 * r.rel + .25 / (1 + r.d / 8000) + (r.agree > 1 ? .08 : 0) + boost;
  }
  // country scope: if anything matches your country, drop the rest; otherwise keep the global list (Eiffel Tower from Malaysia still works)
  const cc = S.country?.cc; let res = out;
  if (cc && out.some(r => r.cc === cc)) res = out.filter(r => r.cc === cc || (r.cc == null && r.d < 200000));
  return res.sort((a, b) => b.score - a.score).slice(0, 8);
}

/* --- paste coordinates or a map link and it just goes there (no internet needed for this part) --- */
function parseCoords(q) {
  q = q.trim(); let m;
  m = q.match(/(?:@|[?&]q=|[?&]ll=|[?&]query=|geo:|#map=\d+\/|!3d|mlat=)(-?\d{1,2}(?:\.\d+)?)[,/&\s]+(?:mlon=|!4d)?(-?\d{1,3}(?:\.\d+)?)/);
  if (!m) m = q.match(/^\(?\s*(-?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*\)?$/);
  if (!m) return null;
  const lat = +m[1], lon = +m[2]; if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
  return { name: `${lat.toFixed(5)}, ${lon.toFixed(5)}`, sub: 'Coordinates', lat, lon, cc: null, src: 'coords', icon: ic('pin') };
}
async function withRetry(fn) { try { return await fn(); } catch (e) { if (e?.name === 'AbortError') throw e; await new Promise(r => setTimeout(r, 400)); return fn(); } }
/* Typing asks Photon only; Nominatim (whose usage policy forbids search-as-you-type) is asked when the person presses Enter. */
async function blendSearch(q, signal, explicit = true) {
  q = holisticQuery(q);   // a council typed as a place ("Majlis Perbandaran Petaling Jaya Selatan") is searched as the place it governs
  const o = searchOrigin(), pin = parseCoords(q);
  if (pin) return [pin];
  const [ph, nm] = (await Promise.allSettled([withRetry(() => photonSearch(q, o, signal)), explicit ? withRetry(() => nominatimSearch(q, o, signal)) : Promise.resolve(null)])).map(r => r.status === 'fulfilled' ? r.value : null);
  if (!ph && !nm) throw new Error('search providers failed');
  return mergeRank(q, o, [ph, nm]);
}

/* ================= web search: asks Wikipedia for places that have an article, with coordinates ================= */
const webCbs = {}; let webSeq = 0;
window.__webGot = (id, status, url, body) => { const f = webCbs[id]; if (f) { delete webCbs[id]; f({ status, url, body }); } };
function webGet(url, ms = 9000) {
  return new Promise((res, rej) => {
    if (window.AndroidWeb) {
      const id = 'w' + (++webSeq), t = setTimeout(() => { delete webCbs[id]; rej(new Error('timeout')); }, ms);
      webCbs[id] = r => { clearTimeout(t); r.status >= 200 && r.status < 400 ? res(r) : rej(new Error(r.status)); };
      AndroidWeb.get(url, id);
    } else {
      const ac = new AbortController(), t = setTimeout(() => ac.abort(), ms);
      fetch(url, { signal: ac.signal }).then(async r => { clearTimeout(t); r.ok ? res({ status: r.status, url: r.url, body: await r.text() }) : rej(new Error(r.status)); }, e => { clearTimeout(t); rej(e); });
    }
  });
}
const SKIP_HOST = /(^|\.)(facebook|instagram|tiktok|twitter|youtube|pinterest|linkedin|reddit|tripadvisor)\.[a-z.]+$|(^|\.)x\.com$|duckduckgo\.com|bing\.com|google\./;
function hostOf(u) { try { return new URL(u).hostname.replace(/^www\./, ''); } catch { return ''; } }
const okLL = (a, b) => isFinite(a) && isFinite(b) && Math.abs(a) <= 90 && Math.abs(b) <= 180 && !(a === 0 && b === 0);
const cleanTitle = t => String(t || '').split(/\s[-–—|·•:]\s/)[0].trim().slice(0, 80);
function pageCoords(html) { // structured data first, then map links, then meta tags
  const doc = new DOMParser().parseFromString(html, 'text/html'); let addr = null;
  const title = cleanTitle(doc.querySelector('meta[property="og:title"]')?.content || doc.title);
  for (const sc of doc.querySelectorAll('script[type="application/ld+json"]')) {
    let j; try { j = JSON.parse(sc.textContent); } catch { continue; }
    const stack = [j];
    while (stack.length) {
      const o = stack.pop(); if (!o || typeof o !== 'object') continue;
      if (Array.isArray(o)) { stack.push(...o); continue; }
      const g = o.geo && typeof o.geo === 'object' ? o.geo : o, la = +g.latitude, lo = +g.longitude;
      if (okLL(la, lo)) return { lat: la, lon: lo, name: typeof o.name === 'string' && o.name ? o.name.slice(0, 80) : title };
      if (!addr && o.address) {
        const a = o.address; const t = typeof a === 'string' ? a : [a.streetAddress, a.addressLocality, a.postalCode, a.addressRegion, typeof a.addressCountry === 'string' ? a.addressCountry : a.addressCountry?.name].filter(x => typeof x === 'string' && x).join(', ');
        if (t.length > 8) addr = { text: t, name: typeof o.name === 'string' ? o.name : title };
      }
      for (const k in o) if (o[k] && typeof o[k] === 'object') stack.push(o[k]);
    }
  }
  const N = '(-?\\d{1,3}\\.\\d{3,})', RES = [
    /"wgCoordinates":\{"lat":(-?[\d.]+),"lon":(-?[\d.]+)/, new RegExp(`!3d${N}!4d${N}`), new RegExp(`google\\.[a-z.]+/maps[^"'\\s<]*?@${N},${N}`),
    new RegExp(`(?:maps\\.google\\.[a-z.]+|google\\.[a-z.]+/maps)[^"'\\s<]*?[?&;](?:q|ll|center|daddr)=${N}(?:,|%2C)${N}`), new RegExp(`openstreetmap\\.org[^"'\\s<]*?mlat=${N}(?:&amp;|&)mlon=${N}`),
    new RegExp(`openstreetmap\\.org[^"'\\s<]*?#map=\\d+/${N}/${N}`), new RegExp(`geo:${N},${N}`), new RegExp(`maps\\.apple\\.com[^"'\\s<]*?[?&;](?:ll|q|coordinate)=${N},${N}`)];
  for (const re of RES) { const m = html.match(re); if (m && okLL(+m[1], +m[2])) return { lat: +m[1], lon: +m[2], name: title }; }
  const meta = n => doc.querySelector(`meta[name="${n}"],meta[property="${n}"]`)?.content;
  const pos = meta('geo.position') || meta('ICBM');
  if (pos) { const [la, lo] = pos.split(/[;,]/).map(Number); if (okLL(la, lo)) return { lat: la, lon: lo, name: title }; }
  const la = +(meta('place:location:latitude') || meta('og:latitude') || meta('business:contact_data:latitude')), lo = +(meta('place:location:longitude') || meta('og:longitude') || meta('business:contact_data:longitude'));
  if (okLL(la, lo)) return { lat: la, lon: lo, name: title };
  return addr ? { addr } : null;
}
async function geocodeAddress(text, signal) {
  const r = await fetch(`${SV().nominatim}/search?format=jsonv2&limit=1&q=${encodeURIComponent(text)}`, { signal });
  const j = r.ok ? await r.json() : []; return j[0] ? { lat: +j[0].lat, lon: +j[0].lon } : null;
}
async function wikiSearch(q, signal) {
  const lang = UI_LANG.slice(0, 2).replace(/[^a-z]/g, '') || 'en';
  const r = await webGet(`https://${lang}.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrsearch=${encodeURIComponent(q)}&gsrlimit=6&prop=coordinates|description&colimit=6`);
  const pages = Object.values(JSON.parse(r.body).query?.pages || {});
  return pages.filter(p => p.coordinates?.[0]).sort((a, b) => a.index - b.index).map(p => ({ name: p.title, sub: p.description || 'Wikipedia', lat: p.coordinates[0].lat, lon: p.coordinates[0].lon, src: 'web', icon: ic('globe'), host: 'wikipedia.org' }));
}
async function webSearch(q, signal) {
  const o = searchOrigin();
  const wk = await wikiSearch(q, signal).catch(() => null);
  if (!wk) throw new Error('web search failed');
  const pg = null;
  const keys = new Map(), out = [];
  for (const r of [...(pg || []), ...(wk || [])]) {
    const k = dedupeKey(r.lat, r.lon), ex = keys.get(k); if (ex) { ex.agree++; continue; }
    r.agree = 1; r.d = dist(o, [r.lat, r.lon]); r.rel = relevance(q, r); keys.set(k, r); out.push(r);
  }
  for (const r of out) r.score = r.agree * .5 + r.rel + .25 / (1 + r.d / 15000);
  return out.sort((a, b) => b.score - a.score).slice(0, 8);
}

let searchT, searchAbort, addingStop = false;
const qEl = $('#q'), resEl = $('#results');
function kindLabel(r) { // "Fuel · address" — helps tell apart ten places with the same name
  const v = (r.kind || '').split(':')[1]; if (!v || ['yes', 'house', 'house_number', 'residential', 'undefined', 'null'].includes(v)) return '';
  return pretty(v) + ' · ';
}
/* Placeholder rows that fade in and out while a search is running (opacity only). */
function showSkeleton() {
  resEl.hidden = false; resEl._data = [];
  resEl.innerHTML = [0, 1, 2].map(() => '<li class="skel" aria-hidden="true"><div class="ico"></div><div class="li-main"><div class="bar w60"></div><div class="bar w40"></div></div></li>').join('') + '<li class="sr-only" role="status">Searching…</li>';
}
/* A sleepy Noto cat for an empty list. */
function showEmpty(msg) {
  resEl.hidden = false; resEl._data = [];
  resEl.innerHTML = `<li class="empty cat-empty"><img src="assets/cats/black-cat.svg" alt="" width="44" height="44"><span>${esc(msg)}</span></li>`;
}
function showList(items, emptyMsg) {
  resEl.hidden = false; resEl._data = items;
  resEl.innerHTML = items.length ? items.map((r, i) => `<li data-i="${i}"><div class="ico">${r.icon || ic('pin')}</div><div class="li-main"><div class="li-title">${esc(r.name)}</div><div class="li-sub">${esc(kindLabel(r))}${esc(r.sub)}</div></div><div class="li-dist">${r.d != null ? fmtDist(r.d) : ''}</div></li>`).join('')
    : `<li class="empty">${emptyMsg}</li>`;
}
function showQuick() {
  const items = [];
  if (S.saved.home) items.push({ ...S.saved.home, icon: ic('home'), sub: 'Home' });
  if (S.saved.work) items.push({ ...S.saved.work, icon: ic('work'), sub: 'Work' });
  S.saved.favs.forEach(f => items.push({ ...f, icon: ic('star') }));
  S.recents.forEach(r => items.push({ ...r, icon: ic('clock') }));
  if (items.length) showList(items, ''); else resEl.hidden = true;
}
qEl.addEventListener('focus', () => { if (!qEl.value.trim()) showQuick(); });
qEl.addEventListener('input', () => {
  $('#clearQ').hidden = !qEl.value; clearTimeout(searchT); clearResultsMap();
  const v = qEl.value.trim(); if (!v) return showQuick();
  if (v.length < 3) { resEl.hidden = true; return; }
  searchT = setTimeout(doSearch, 450);
});
qEl.addEventListener('keydown', e => { if (e.key === 'Enter') { clearTimeout(searchT); doSearch(true); qEl.blur(); } });
$('#clearQ').addEventListener('click', () => { clearResultsMap(); qEl.value = ''; $('#clearQ').hidden = true; qEl.focus(); showQuick(); });
const webRow = text => ({ name: `Search the web for “${text}”`, sub: 'Wikipedia · finds places that have an article', icon: ic('globe'), webAction: true });
async function doSearch(explicit = false) {
  const text = qEl.value.trim(); if (text.length < 2) return;
  searchAbort?.abort(); const ac = searchAbort = new AbortController();
  clearResultsMap(); showSkeleton();
  let items = null; try { items = await blendSearch(text, ac.signal, explicit); } catch {}
  if (ac.signal.aborted) return;
  const weak = !items || !items.length || (items[0].rel < .5 && items[0].agree < 2);
  if (explicit && !weak) { showOnMap(items, text); return; }          // Enter: show where they all are
  if (!weak) { showList([...items, webRow(text)], ''); return; }      // typing: suggestions
  await runWeb(text, items || [], ac, true, explicit);
}
async function runWeb(text, base, ac, webFirst, toMap = false) {
  showList([...base, { name: 'Searching the web…', sub: 'Reading coordinates from the pages it finds', icon: ic('globe'), busy: true }], '');
  let web = []; try { web = await webSearch(text, ac.signal); } catch {}
  if (ac.signal.aborted) return;
  const seen = new Set(), all = [];
  for (const r of webFirst ? [...web, ...base] : [...base, ...web]) { const k = dedupeKey(r.lat, r.lon); if (seen.has(k)) continue; seen.add(k); all.push(r); }
  if (toMap && all.length) showOnMap(all, text, true); else showList(all.slice(0, 10), 'Nothing found on the web either. Try more of the name, or the area.');
}

/* --- search results as pins on the map: zoom out to see where they all are, tap a pin or card, then Go --- */
let resMarks = [], resSel = -1, resList = [];
function clearResultsMap() { resMarks.forEach(m => m.remove()); resMarks = []; resList = []; resSel = -1; $('#resultStrip').hidden = true; document.body.classList.remove('has-results'); }
function pickResult(r) {
  clearResultsMap(); resEl.hidden = true; qEl.value = ''; $('#clearQ').hidden = true;
  if (r.icon == null || r.src === 'web') { S.recents = [{ name: r.name, sub: r.sub, lat: r.lat, lon: r.lon }, ...S.recents.filter(x => dedupeKey(x.lat, x.lon) !== dedupeKey(r.lat, r.lon))].slice(0, 8); save(); renderSaved(); }
  choose({ name: r.name, sub: r.sub, lat: r.lat, lon: r.lon });
}
function selectResult(i, fly) {
  resSel = i; const strip = $('#resultStrip');
  resMarks.forEach((m, j) => m.getElement().classList.toggle('on', j === i));
  strip.querySelectorAll('.rcard[data-i]').forEach(c => c.classList.toggle('on', +c.dataset.i === i));
  strip.querySelector(`.rcard[data-i="${i}"]`)?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
  if (fly) map.easeTo({ center: [resList[i].lon, resList[i].lat], zoom: Math.max(map.getZoom(), 15.5), duration: 600 });
}
function showOnMap(items, text, fromWeb) {
  clearResultsMap(); resEl.hidden = true;
  resList = items.filter(r => isFinite(r.lat) && isFinite(r.lon)).slice(0, 10);
  if (!resList.length) { showEmpty('No results. Try a different spelling.'); return; }
  const strip = $('#resultStrip'), o = searchOrigin();
  strip.innerHTML = resList.map((r, i) => `<div class="rcard" data-i="${i}"><div class="rtop"><span class="rnum">${i + 1}</span><b>${esc(r.name)}</b></div><small>${esc(kindLabel(r))}${esc(r.sub)}</small><div class="rbot"><span>${fmtDist(r.d != null ? r.d : dist(o, [r.lat, r.lon]))}</span><button class="go" data-i="${i}">Go</button></div></div>`).join('')
    + (fromWeb ? '' : '<div class="rcard web"><div class="rtop"><span class="rnum">'+ic('globe')+'</span><b>Search the web</b></div><small>Not here? Look it up on the internet</small></div>');
  strip._q = text; strip.hidden = false; document.body.classList.add('has-results'); strip.scrollLeft = 0;
  resList.forEach((r, i) => {
    const el = document.createElement('div'); el.className = 'result-pin'; el.textContent = i + 1;
    el.addEventListener('click', ev => { ev.stopPropagation(); selectResult(i, false); });
    resMarks.push(new maplibregl.Marker({ element: el }).setLngLat([r.lon, r.lat]).addTo(map));
  });
  const b = new maplibregl.LngLatBounds(); resList.forEach(r => b.extend([r.lon, r.lat])); if (me) b.extend([me.lon, me.lat]);
  following = false; $('#locateBtn').classList.remove('on'); $('#sheet').classList.add('collapsed');
  setTimeout(() => { if (!resList.length) return; map.fitBounds(b, { padding: { top: 120, bottom: document.body.classList.contains('hud-on') ? 350 : 250, left: 50, right: 50 }, maxZoom: 16, pitch: 0, bearing: 0, duration: 900 }); }, 300);
  selectResult(0, false);
}
$('#resultStrip').addEventListener('click', e => {
  const strip = e.currentTarget, go = e.target.closest('.go'), card = e.target.closest('.rcard');
  if (go) { pickResult(resList[+go.dataset.i]); return; }
  if (!card) return;
  if (card.classList.contains('web')) { const text = strip._q, base = resList.slice(); searchAbort?.abort(); const ac = searchAbort = new AbortController(); clearResultsMap(); runWeb(text, base, ac, false, true); return; }
  selectResult(+card.dataset.i, true);
});
resEl.addEventListener('click', e => {
  const li = e.target.closest('li[data-i]'); if (!li) return;
  const r = resEl._data[+li.dataset.i]; if (!r || r.busy) return;
  if (r.webAction) { const text = qEl.value.trim(); searchAbort?.abort(); const ac = searchAbort = new AbortController(); runWeb(text, resEl._data.filter(x => !x.webAction), ac, false, false); return; }
  pickResult(r);
});
document.addEventListener('pointerdown', e => { if (!e.target.closest('#results, #topbar')) resEl.hidden = true; });

/* ================= routing (OSRM) ================= */
const nav = { dest: null, wps: [], mode: 'car', routes: [], sel: 0, fc: null, active: false, idx: 0, off: 0, abort: null, steps: [], marks: [], said: new Set(), tripAt: 0 };

function choose(place) { if (addingStop && nav.dest) { addingStop = false; $('#addStop').classList.remove('on-toggle'); addWaypoint(place); } else selectPlace(place); }

async function selectPlace(place, opts = {}) {
  if (!me && !firstFix.done) { toast('Waiting for your location…'); startLocation(); }
  nav.dest = place; nav.wps = []; nav.routes = []; nav.sel = 0;
  nav.mode = quest.on ? 'foot' : (nav.mode || 'car');
  $('#destName').textContent = place.name; $('#destSub').textContent = place.sub || '';
  $('#nearbyView').hidden = true; $('#navView').hidden = false; $('#steps').hidden = true; $('#saveMenu').hidden = true;
  $('#sheet').classList.remove('collapsed');
  $('#alts').innerHTML = ''; $('#routeSummary').innerHTML = ''; $('#estNote').textContent = ''; clearRouteChips();
  drawMarkers(); syncModeSeg(); syncPanels(); syncSaveStar();
  if (me) await fetchRoute(); else map.easeTo({ center: [place.lon, place.lat], zoom: 16 });
}
function drawMarkers() {
  nav.marks.forEach(m => m.remove()); nav.marks = [];
  if (!nav.dest) return;
  nav.wps.forEach((w, i) => { const el = document.createElement('div'); el.className = 'stop-pin'; el.textContent = i + 1; nav.marks.push(new maplibregl.Marker({ element: el }).setLngLat([w.lon, w.lat]).addTo(map)); });
  const el = document.createElement('div'); el.className = 'pin'; el.innerHTML = ic('pin-fill');
  nav.marks.push(new maplibregl.Marker({ element: el, anchor: 'bottom' }).setLngLat([nav.dest.lon, nav.dest.lat]).addTo(map));
}
function syncModeSeg() {
  $$('#modeSwitch button').forEach(b => { b.classList.toggle('on', b.dataset.mode === nav.mode); b.disabled = quest.on && b.dataset.mode === 'car'; });
  $('#avoidHw').classList.toggle('on-toggle', S.avoidHw); $('#avoidHw').hidden = nav.mode === 'foot';
}
$('#modeSwitch').addEventListener('click', e => { const b = e.target.closest('button'); if (!b || b.disabled) return; nav.mode = b.dataset.mode; syncModeSeg(); fetchRoute(); });
$('#avoidHw').addEventListener('click', () => { S.avoidHw = !S.avoidHw; save(); syncModeSeg(); fetchRoute(); });

/* waypoints */
$('#addStop').addEventListener('click', () => { addingStop = !addingStop; $('#addStop').classList.toggle('on-toggle', addingStop); if (addingStop) { toast('Search for a stop to add'); qEl.focus(); } });
function addWaypoint(p) { nav.wps.push(p); renderStops(); drawMarkers(); fetchRoute(); }
function renderStops() {
  const ol = $('#stops'); ol.hidden = !nav.wps.length;
  ol.innerHTML = nav.wps.map((w, i) => `<li><div class="stop-pin" style="position:static">${i + 1}</div><div class="li-main"><div class="li-title">${esc(w.name)}</div></div><button class="x" data-rm="${i}" aria-label="Remove stop">${ic('close')}</button></li>`).join('');
  $('#optimizeStops').hidden = nav.wps.length < 1;
}
$('#stops').addEventListener('click', e => { const b = e.target.closest('[data-rm]'); if (!b) return; nav.wps.splice(+b.dataset.rm, 1); renderStops(); drawMarkers(); fetchRoute(); });
// Nearest-neighbour ordering from your location. Not a TSP solver — good enough for a handful of stops.
$('#optimizeStops').addEventListener('click', () => {
  if (!me || !nav.dest) return toast('Need your location first');
  const pool = [...nav.wps, nav.dest]; let cur = [me.lat, me.lon]; const order = [];
  while (pool.length) { let bi = 0, bd = Infinity; pool.forEach((p, i) => { const d = dist(cur, [p.lat, p.lon]); if (d < bd) { bd = d; bi = i; } }); const p = pool.splice(bi, 1)[0]; order.push(p); cur = [p.lat, p.lon]; }
  nav.dest = order.pop(); nav.wps = order;
  $('#destName').textContent = nav.dest.name; $('#destSub').textContent = nav.dest.sub || '';
  renderStops(); drawMarkers(); fetchRoute(); toast('Stops reordered');
});

/* fetch + cache */
const routeKey = () => [nav.mode, S.avoidHw && nav.mode === 'car' ? 'nhw' : '', ...nav.wps, nav.dest].map(p => typeof p === 'string' ? p : `${p.lat.toFixed(4)},${p.lon.toFixed(4)}`).join('|');
const rcache = { get() { try { return JSON.parse(localStorage.getItem(RKEY)) || {}; } catch { return {}; } }, put(k, routes) {
  try { const c = this.get(); c[k] = { t: Date.now(), routes }; const ks = Object.keys(c).sort((a, b) => c[b].t - c[a].t).slice(0, 8); const o = {}; ks.forEach(x => o[x] = c[x]); localStorage.setItem(RKEY, JSON.stringify(o)); } catch {} } };
const slim = r => ({ ...r, geometry: { type: 'LineString', coordinates: r.geometry.coordinates.map(c => [+c[0].toFixed(5), +c[1].toFixed(5)]) } });

async function osrmFetch(mode, coords, { exclude, signal }) {
  const path = coords.map(c => `${c[1]},${c[0]}`).join(';');
  const url = `${osrmBase(mode)}/${path}?overview=full&geometries=geojson&steps=true&alternatives=true${exclude ? '&exclude=' + exclude : ''}`;
  const r = await fetch(url, { signal }); let j = null; try { j = await r.json(); } catch {}
  if (!r.ok || j?.code !== 'Ok') throw Object.assign(new Error(j?.code || r.status), { code: j?.code, status: r.status });
  return j.routes;
}
// Avoid-highways and walking-server fallbacks live here so first-time routing and re-routing behave the same.
async function routesWithFallback(coords, signal) {
  let routes = null, approx = false;
  if (S.avoidHw && nav.mode === 'car') {
    try { routes = await osrmFetch('car', coords, { exclude: 'motorway', signal }); }
    catch (e) { if (signal.aborted) throw e; if (e.code === 'InvalidValue' || e.status === 400) toast('This routing server can’t avoid highways — showing the normal route', 3200); else throw e; }
  }
  if (!routes) {
    try { routes = await osrmFetch(nav.mode, coords, { signal }); }
    catch (e) { // walking server unreachable: approximate from the car network at walking speed rather than leave you stranded
      if (signal.aborted || nav.mode !== 'foot') throw e;
      routes = (await osrmFetch('car', coords, { signal })).map(r => ({ ...r, duration: r.distance / 1.35 })); approx = true;
    }
  }
  return { routes, approx };
}
const routeCoords = () => [[me.lat, me.lon], ...nav.wps.map(w => [w.lat, w.lon]), [nav.dest.lat, nav.dest.lon]];
async function fetchRoute(silent) {
  if (!me || !nav.dest) return;
  nav.abort?.abort(); const ac = nav.abort = new AbortController();
  const coords = routeCoords(), key = routeKey();
  if (!silent) $('#routeSummary').innerHTML = '<small>Finding the best route…</small>';
  let routes = null, approx = false;
  try {
    ({ routes, approx } = await routesWithFallback(coords, ac.signal));
    rcache.put(key, routes.map(slim));
  } catch (e) {
    if (ac.signal.aborted) return;
    const hit = rcache.get()[key];
    if (hit) { routes = hit.routes; toast('Offline — showing your last route to this place', 3200); }
    else { $('#routeSummary').innerHTML = '<small>No route found. Try another mode or place.</small>'; return; }
  }
  if (approx) toast('Walking server unavailable — walking time estimated', 3000);
  nav.routes = routes.slice(0, 3); nav.sel = 0; nav.idx = 0; nav.off = 0; nav.said.clear();
  drawRoutes(true); renderAlts(); selectAlt(0, true);
}
// Re-route while navigating: one request at a time, never cancelled by the next GPS fix, at most one attempt every 20 s.
async function reroute() {
  if (nav.rerouting || !me || !nav.dest || Date.now() - (nav.lastReroute || 0) < 20000) return;
  nav.rerouting = true; nav.lastReroute = Date.now();
  $('#navText').textContent = 'Rerouting…';
  const ac = new AbortController(), t = setTimeout(() => ac.abort(), 20000);
  try {
    const { routes } = await routesWithFallback(routeCoords(), ac.signal);
    if (!nav.active || !routes?.length) return;
    nav.routes = routes.slice(0, 1); nav.sel = 0; nav.idx = 0; nav.off = 0; nav.said.clear(); nav._prep = null; nav.snap = null; nav.t0 = null;
    drawRoutes(false); nav.steps = nav.routes[0].legs.flatMap(l => l.steps); haptic(30); speak('Rerouting.');
  } catch {} finally { clearTimeout(t); nav.rerouting = false; }
}
function ensureRouteLayers() { try { if (map.getStyle() && !map.getLayer('route-main')) { if (map.getSource('routes')) map.removeSource('routes'); onStyleLoad(); } } catch {} }
function clearRouteChips() { (nav.chips || []).forEach(m => m.remove()); nav.chips = []; }
/* A time chip on each route (tap to choose it); only while planning, never while navigating. */
function drawRouteChips() {
  clearRouteChips();
  if (nav.active || nav.routes.length < 2) return;
  nav.routes.forEach((r, i) => {
    const pts = r.geometry.coordinates, at = pts[Math.min(pts.length - 1, Math.floor(pts.length * (.38 + .12 * i)))];
    const el = document.createElement('button'); el.type = 'button'; el.className = 'route-chip' + (i === nav.sel ? ' on' : ''); el.textContent = fmtEst(r.duration, nav.mode);
    el.setAttribute('aria-label', 'Route ' + (i + 1) + ': ' + el.textContent);
    el.addEventListener('click', ev => { ev.stopPropagation(); selectAlt(i); });
    nav.chips.push(new maplibregl.Marker({ element: el }).setLngLat(at).addTo(map));
  });
}
function drawRoutes(fit) {
  ensureRouteLayers();
  nav.fc = { type: 'FeatureCollection', features: nav.routes.map((r, i) => ({ type: 'Feature', properties: { i, sel: i === nav.sel ? 1 : 0 }, geometry: r.geometry })) };
  map.getSource('routes')?.setData(nav.fc);
  drawRouteChips();
  if (fit && !nav.active && nav.routes.length) {
    const b = new maplibregl.LngLatBounds(); nav.routes[0].geometry.coordinates.forEach(c => b.extend(c));
    following = false; $('#locateBtn').classList.remove('on');
    map.fitBounds(b, { padding: { top: 130, bottom: Math.min(380, innerHeight * .5), left: 40, right: 40 }, maxZoom: 17, duration: 700 });
  }
}
function routeVia(r) {
  const s = r.legs.flatMap(l => l.steps).filter(x => x.name).sort((a, b) => b.distance - a.distance)[0]; return s ? `via ${s.name}` : '';
}
function renderAlts() {
  $('#alts').innerHTML = nav.routes.length < 2 ? '' : nav.routes.map((r, i) =>
    `<button class="alt ${i === nav.sel ? 'on' : ''}" data-i="${i}"><div><b>${fmtEst(r.duration, nav.mode)}</b><small>${fmtDist(r.distance)} ${esc(routeVia(r))}</small></div>${i === 0 ? '<span class="tag">Fastest</span>' : ''}</button>`).join('');
}
$('#alts').addEventListener('click', e => { const b = e.target.closest('.alt'); if (b) selectAlt(+b.dataset.i); });
function selectAlt(i, silent) {
  if (!nav.routes[i]) return; nav.sel = i; const r = nav.routes[i];
  if (!silent) { drawRoutes(false); renderAlts(); haptic(10); }
  nav.steps = r.legs.flatMap(l => l.steps);
  $('#routeSummary').innerHTML = `${fmtEst(r.duration, nav.mode)} <small>${fmtDist(r.distance)} · ${nav.mode === 'foot' ? 'walking' : 'driving'}${nav.wps.length ? ` · ${nav.wps.length + 1} stops` : ''}</small>`;
  $('#estNote').textContent = nav.mode === 'foot' ? '' : 'No live traffic in this estimate. Allow extra time in the rush hour.';
  destWeather();
  $('#steps').innerHTML = nav.steps.map(s => `<li><div class="ico">${arrow(s)}</div><div class="li-main"><div class="li-title">${esc(instr(s))}</div><div class="li-sub">${s.distance ? fmtDist(s.distance) : ''}</div></div></li>`).join('');
}

function instr(s) {
  const m = s.maneuver || {}, mod = m.modifier || '', road = s.name ? ` onto ${s.name}` : '';
  switch (m.type) {
    case 'depart': return `Head out${s.name ? ' on ' + s.name : ''}`;
    case 'arrive': return 'Arrive at your destination';
    case 'roundabout': case 'rotary': return `Take the roundabout${m.exit ? ', exit ' + m.exit : ''}${road}`;
    default:
      if (mod === 'straight' || !mod) return `Continue${s.name ? ' on ' + s.name : ' straight'}`;
      if (mod === 'uturn') return `Make a U-turn${road}`;
      if (mod.startsWith('slight')) return `Bear ${mod.replace('slight ', '')}${road}`;
      return `Turn ${mod}${road}`;
  }
}
function arrow(s) {
  const m = s.maneuver || {}, mod = m.modifier || '';
  if (m.type === 'arrive') return '⚑'; if (m.type === 'depart') return '↑'; if (m.type === 'roundabout' || m.type === 'rotary') return '⟳';
  return { left: '←', 'sharp left': '↰', 'slight left': '↖', right: '→', 'sharp right': '↱', 'slight right': '↗', uturn: '↩', straight: '↑' }[mod] || '↑';
}
$('#toggleSteps').addEventListener('click', () => { const s = $('#steps'); s.hidden = !s.hidden; });
$('#closeRoute').addEventListener('click', clearRoute);
function clearRoute() {
  nav.abort?.abort(); stopNav(true); addingStop = false; $('#addStop').classList.remove('on-toggle');
  nav.routes = []; nav.fc = null; map.getSource('routes')?.setData(emptyFC); clearRouteChips();
  nav.marks.forEach(m => m.remove()); nav.marks = []; nav.dest = null; nav.wps = []; renderStops();
  $('#navView').hidden = true; $('#nearbyView').hidden = false; syncPanels(); renderNearby();
}

/* ---- turn-by-turn ---- */
$('#startNav').addEventListener('click', () => {
  if (!nav.routes[nav.sel] || !me) return toast('Waiting for a route…');
  try { (purr.ac || (purr.ac = new (window.AudioContext || window.webkitAudioContext)())).resume(); } catch {}
  nav.active = true; clearRouteChips(); nav.snap = null; nav.pose = null; nav._prep = null; nav.t0 = null; nav.a0 = null; nav.dNext = Infinity; keepAwake(true); renderSpeedo(); navDeclutter(true); update3dArrow(); nav.idx = 0; nav.off = 0; nav.said.clear(); following = true; haptic(40);
  document.body.classList.add('navigating'); $('#navBanner').hidden = false; $('#tripBar').hidden = false; resEl.hidden = true; syncPanels(); syncMute();
  S.trips = [{ name: nav.dest.name, ts: Date.now(), mode: nav.mode, dist: nav.routes[nav.sel].distance }, ...S.trips].slice(0, 20); save(); renderSaved();
  navCamera(800);
  speak(catSay('start', `Starting navigation. ${instr(nav.steps[0])}.`));
  if (nav.wx?.rain) setTimeout(() => speak(catSay('far', 'Heads up, rain is likely when you arrive. Bring an umbrella.')), 6000);
  navTick();
});
// End = leave navigation and return to the map. Hide the UI first so a failure elsewhere can never leave you stuck.
function endNavigation() {
  document.body.classList.remove('navigating'); $('#navBanner').hidden = true; $('#tripBar').hidden = true; nav.active = false; keepAwake(quest.on); renderSpeedo(); navDeclutter(false); update3dArrow(); meMarker?.getElement().classList.remove('hide'); $('#along').hidden = true; limit.v = null;
  try { resetCamera(); } catch {}
  try { stopSpeech(); } catch {}
  try { clearRoute(); } catch (e) { try { syncPanels(); } catch {} }
  toast('Navigation ended');
}
$('#navStop').addEventListener('click', endNavigation);
function stopNav(silent) {
  if (!nav.active && silent) return; nav.active = false; keepAwake(quest.on); renderSpeedo(); navDeclutter(false); update3dArrow(); meMarker?.getElement().classList.remove('hide');
  try { resetCamera(); } catch {}
  document.body.classList.remove('navigating'); $('#navBanner').hidden = true; $('#tripBar').hidden = true;
  try { stopSpeech(); } catch {}
  try { syncPanels(); } catch {}
  if (!silent) toast('Navigation ended');
}
function navTick() {
  const steps = nav.steps; if (!steps.length || !me) return; const here = [me.lat, me.lon];
  const sn = nav.snap && prepRoute() ? nav.snap : null; let dNext, rest;
  if (sn) { // measured along the route itself
    while (nav.idx + 1 < steps.length && sn.along >= nav.stepAt[nav.idx + 1] - 12) nav.idx++;
    dNext = Math.max(0, nav.stepAt[Math.min(nav.idx + 1, steps.length - 1)] - sn.along); rest = Math.max(0, nav.routeLen - sn.along);
  } else {
    while (nav.idx + 1 < steps.length) { const m = steps[nav.idx + 1].maneuver.location; if (dist(here, [m[1], m[0]]) < 25) nav.idx++; else break; }
    const nl0 = steps[Math.min(nav.idx + 1, steps.length - 1)].maneuver.location; dNext = dist(here, [nl0[1], nl0[0]]);
    rest = steps.slice(nav.idx + 2).reduce((a, s) => a + s.distance, 0) + dNext;
  }
  nav.dNext = dNext;
  const next = steps[Math.min(nav.idx + 1, steps.length - 1)], nl = next.maneuver.location;
  if (next.maneuver.type === 'arrive' && dNext < 25) {
    haptic(80); setTimeout(() => haptic(80), 200); speak(catSay('arrive')); toast('😻 You have arrived!', 3500); clearRoute(); return;
  }
  $('#navArrow').textContent = arrow(next); $('#navDist').textContent = fmtDist(dNext);
  $('#navText').textContent = instr(next);
  // "then ..." preview of the turn after this one, when it is close
  const after = steps[nav.idx + 2], dThen = after ? (sn ? nav.stepAt[nav.idx + 2] - nav.stepAt[nav.idx + 1] : dist([nl[1], nl[0]], [after.maneuver.location[1], after.maneuver.location[0]])) : Infinity;
  $('#navThen').textContent = after && after.maneuver.type !== 'arrive' && dThen < 400 ? `Then ${arrow(after)} ${instr(after)}` : '';
  // trip bar: arrival time from the route, nudged by how fast you have really been going
  const r = nav.routes[nav.sel], routeAvg = (r.distance || 1) / Math.max(1, r.duration || 1); let secs = rest / routeAvg;
  if (sn) {
    if (nav.t0 == null) { nav.t0 = Date.now(); nav.a0 = sn.along; }
    const el = (Date.now() - nav.t0) / 1000, done = sn.along - nav.a0;
    if (el > 90 && done > 300) { const ratio = Math.max(0.6, Math.min(1.4, (done / el) / routeAvg)); secs = secs / (0.6 * ratio + 0.4); }
    trimTravelled(sn);
  }
  $('#tripEta').textContent = new Date(Date.now() + secs * 1000).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  $('#tripLeft').textContent = fmtTime(secs); $('#tripDist').textContent = fmtDist(rest);
  refreshLimit();
  // voice: one heads-up, one "now"
  const foot = nav.mode === 'foot', v = speedMs;
  const far = foot ? 80 : Math.max(200, Math.min(1000, v * 20)), near = foot ? 25 : Math.max(50, Math.min(140, v * 5)), early = !foot && v > 20 ? 2000 : 0;
  const kf = `${nav.idx}f`, kn = `${nav.idx}n`, ke = `${nav.idx}e`;
  if (early && dNext < early && dNext > far + 300 && !nav.said.has(ke)) { nav.said.add(ke); speak(catSay('far', `In ${fmtDist(dNext).replace(' m', ' metres').replace(' km', ' kilometres')}, ${instr(next)}`)); }
  if (dNext < near && !nav.said.has(kn)) { nav.said.add(kn); nav.said.add(kf); speak(catSay('now', instr(next))); haptic(30); }
  else if (dNext < far && !nav.said.has(kf)) { nav.said.add(kf); speak(catSay('far', `In ${fmtDist(dNext).replace(' m', ' metres').replace(' km', ' kilometres')}, ${instr(next)}`)); }
  // off-route: farther than max(60 m, 1.5× GPS accuracy) from the line for 3 consecutive good fixes (jitter and bad fixes are ignored)
  const line = nav.routes[nav.sel].geometry.coordinates; let min = Infinity;
  for (let i = 0; i + 1 < line.length; i++) { const d = distToSeg(here, [line[i][1], line[i][0]], [line[i + 1][1], line[i + 1][0]]); if (d < min) min = d; if (min < 15) break; }
  const lim = Math.max(60, (me.acc || 0) * 1.5);
  nav.off = (min > lim && (me.acc || 0) <= 100) ? nav.off + 1 : 0;
  if (nav.off >= 3) { nav.off = 0; reroute(); }
}

/* ================= voice guidance ================= */
const SCRIPTS = [ // per-character script → TTS language. Latin/other = default voice (lang deliberately left unset).
  [/\p{Script=Hiragana}|\p{Script=Katakana}/u, 'ja-JP'], [/\p{Script=Hangul}/u, 'ko-KR'], [/\p{Script=Han}/u, 'zh-CN'],
  [/\p{Script=Devanagari}/u, 'hi-IN'], [/\p{Script=Thai}/u, 'th-TH'], [/\p{Script=Arabic}/u, 'ar-SA'], [/\p{Script=Cyrillic}/u, 'ru-RU'],
  [/\p{Script=Hebrew}/u, 'he-IL'], [/\p{Script=Tamil}/u, 'ta-IN'], [/\p{Script=Greek}/u, 'el-GR'], [/\p{Script=Bengali}/u, 'bn-IN'],
];
function charLang(ch) {
  if (/[\s\p{P}\p{S}\p{N}]/u.test(ch)) return undefined; // neutral: stays with whatever came before
  for (const [re, l] of SCRIPTS) if (re.test(ch)) return l;
  return null; // Latin etc → default voice
}
function segmentByScript(text) {
  const segs = []; let cur = null;
  for (const ch of text) { const l = charLang(ch);
    if (l === undefined && cur) { cur.text += ch; continue; }
    if (!cur || (l !== undefined && cur.lang !== l)) { cur = { lang: l === undefined ? null : l, text: '' }; segs.push(cur); }
    cur.text += ch; }
  return segs.filter(s => s.text.trim());
}
const TTS = ('speechSynthesis' in window) ? window.speechSynthesis : null; // never touch the bare global: it may not exist
let voices = [], ttsUnlocked = false;
const NATIVE_TTS = (typeof window.AndroidTTS === 'object' && window.AndroidTTS) ? window.AndroidTTS : null; // set by the Android app
function stopSpeech() { try { if (NATIVE_TTS) NATIVE_TTS.stop(); else if (TTS && (TTS.speaking || TTS.pending)) TTS.cancel(); } catch {} }
function loadVoices() {
  voices = (!NATIVE_TTS && TTS && TTS.getVoices && TTS.getVoices()) || [];
  const sel = $('#voiceSel'); sel.textContent = '';
  if (NATIVE_TTS) { // voice is chosen in Android settings → Text-to-speech output
    const o = document.createElement('option'); o.value = ''; o.textContent = 'Phone’s text-to-speech voice'; sel.appendChild(o); sel.disabled = true; return;
  }
  const def = document.createElement('option'); def.value = ''; def.textContent = TTS ? 'System default voice' : 'Voice not supported here'; sel.appendChild(def);
  for (const v of voices) { const o = document.createElement('option'); o.value = v.name; o.textContent = `${v.name} (${v.lang})`; sel.appendChild(o); }
  sel.value = voices.some(v => v.name === S.voice.name) ? S.voice.name : ''; sel.selectedIndex = Math.max(0, sel.selectedIndex);
}
// iOS Safari only allows speech after one has been started from a tap: play a silent utterance on the first touch.
function unlockTts() {
  if (!TTS || ttsUnlocked) return; ttsUnlocked = true;
  try { const u = new SpeechSynthesisUtterance(' '); u.volume = 0; TTS.speak(u); } catch {}
}
for (const ev of ['pointerdown', 'touchend', 'click']) document.addEventListener(ev, unlockTts, { passive: true });
function speak(text, force) {
  if (!S.voice.on && !force) return false;
  if (NATIVE_TTS) { // Android app: real TTS, each script segment in its own language
    try { if (!NATIVE_TTS.ready()) { if (force) toast('Phone voice is still starting — try again'); return false; }
      NATIVE_TTS.speak(JSON.stringify(segmentByScript(text).map(g => ({ text: g.text, lang: g.lang || '' })))); return true; } catch { return false; }
  }
  if (!TTS) { if (force) toast('This browser has no speech engine'); return false; }
  try {
    if (!voices.length) loadVoices();
    const busy = TTS.speaking || TTS.pending;
    if (busy) TTS.cancel();                 // cancel() immediately followed by speak() is dropped on iOS → wait a beat after cancelling
    try { TTS.resume(); } catch {}
    const run = () => {
      const chosen = voices.find(v => v.name === S.voice.name);
      for (const seg of segmentByScript(text)) {
        const u = new SpeechSynthesisUtterance(seg.text);
        if (seg.lang) { u.lang = seg.lang; const v = voices.find(x => x.lang.toLowerCase().startsWith(seg.lang.slice(0, 2).toLowerCase())); if (v) u.voice = v; }
        else if (chosen) u.voice = chosen; // default segment: never assert a lang — some engines drop the whole utterance if the locale has no voice
        TTS.speak(u);
      }
    };
    busy ? setTimeout(run, 80) : run();
    return true;
  } catch { return false; }
}
if (TTS) { loadVoices(); TTS.addEventListener?.('voiceschanged', loadVoices); setTimeout(loadVoices, 800); } else loadVoices();
$('#voiceSel').addEventListener('change', e => { S.voice.name = e.target.value; save(); }); // selecting never speaks; use Test voice
$('#testVoice').addEventListener('click', () => {
  unlockTts(); if (speak('In 200 metres, turn left onto Jalan Ampang. 你好 東京 서울 Москва', true)) toast(voices.length ? 'Playing… (check volume and the silent switch)' : 'Playing with the system voice… (check volume and the silent switch)', 3200);
});
$('#voiceToggle').addEventListener('change', e => { S.voice.on = e.target.checked; save(); syncMute(); });
$('#navMute').addEventListener('click', () => { S.voice.on = !S.voice.on; save(); syncMute(); if (!S.voice.on) stopSpeech(); });
function syncMute() { $('#navMute').innerHTML = S.voice.on ? ic('speaker') : ic('mute'); $('#voiceToggle').checked = S.voice.on; }

/* ================= personalization (localStorage only) ================= */
function syncSaveStar() {
  const d = nav.dest; if (!d) return;
  const k = dedupeKey(d.lat, d.lon), is = [S.saved.home, S.saved.work, ...S.saved.favs].some(p => p && dedupeKey(p.lat, p.lon) === k);
  $('#savePlace').innerHTML = is ? ic('star-fill') : ic('star');
}
$('#savePlace').addEventListener('click', () => { $('#saveMenu').hidden = !$('#saveMenu').hidden; });
$('#saveMenu').addEventListener('click', e => {
  const b = e.target.closest('[data-save]'); if (!b || !nav.dest) return;
  const p = { name: nav.dest.name, sub: nav.dest.sub, lat: nav.dest.lat, lon: nav.dest.lon };
  if (b.dataset.save === 'fav') {
    if (S.saved.favs.some(f => dedupeKey(f.lat, f.lon) === dedupeKey(p.lat, p.lon))) return toast('Already in favorites');
    if (S.saved.favs.length >= 5) return toast('Favorites are full (5). Remove one in Settings.', 2600);
    S.saved.favs.push(p);
  } else S.saved[b.dataset.save] = p;
  save(); haptic(20); $('#saveMenu').hidden = true; syncSaveStar(); renderSaved(); renderNearby(); toast('Saved');
});
function renderSaved() {
  const rows = [];
  if (S.saved.home) rows.push(['home', ic('home'), 'Home', S.saved.home.name]); if (S.saved.work) rows.push(['work', ic('work'), 'Work', S.saved.work.name]);
  S.saved.favs.forEach((f, i) => rows.push([`fav${i}`, ic('star'), f.name, f.sub || '']));
  $('#savedList').innerHTML = rows.length ? rows.map(r => `<li data-go="${r[0]}"><div class="ico">${r[1]}</div><div class="li-main"><div class="li-title">${esc(r[2])}</div><div class="li-sub">${esc(r[3])}</div></div><button class="x" data-del="${r[0]}" aria-label="Remove">${ic('close')}</button></li>`).join('') : '<li class="empty">Nothing saved yet. Open a place and tap the star.</li>';
  $('#recentList').innerHTML = S.recents.length ? S.recents.map((r, i) => `<li data-rec="${i}"><div class="ico">${ic('clock')}</div><div class="li-main"><div class="li-title">${esc(r.name)}</div><div class="li-sub">${esc(r.sub)}</div></div></li>`).join('') : '<li class="empty">No recent searches.</li>';
  $('#tripList').innerHTML = S.trips.length ? S.trips.map(t => `<li><div class="ico">${t.mode === 'foot' ? ic('walk') : ic('car')}</div><div class="li-main"><div class="li-title">${esc(t.name)}</div><div class="li-sub">${new Date(t.ts).toLocaleDateString()} · ${fmtDist(t.dist)}</div></div></li>`).join('') : '<li class="empty">No trips yet.</li>';
}
const savedBy = id => id === 'home' ? S.saved.home : id === 'work' ? S.saved.work : S.saved.favs[+id.slice(3)];
$('#savedList').addEventListener('click', e => {
  const del = e.target.closest('[data-del]'), id = del?.dataset.del || e.target.closest('[data-go]')?.dataset.go; if (!id) return;
  if (del) { if (id === 'home' || id === 'work') S.saved[id] = null; else S.saved.favs.splice(+id.slice(3), 1); save(); renderSaved(); renderNearby(); return; }
  openDrawer(false); choose(savedBy(id));
});
$('#recentList').addEventListener('click', e => { const li = e.target.closest('[data-rec]'); if (li) { openDrawer(false); choose(S.recents[+li.dataset.rec]); } });
$('#clearRecents').addEventListener('click', () => { S.recents = []; save(); renderSaved(); });
$('#clearTrips').addEventListener('click', () => { S.trips = []; save(); renderSaved(); });

/* ================= Quest mode ================= */
// Artwork: Google Noto emoji (the Gboard set), bundled in assets/cats so it looks the same on every device.
const CAT_TYPES = [
  { img: 'cat-face', xp: 10, w: 34, r: 0 }, { img: 'cat', xp: 12, w: 20, r: 0 }, { img: 'grin-cat', xp: 18, w: 14, r: 1 },
  { img: 'grin-smile-cat', xp: 20, w: 10, r: 1 }, { img: 'smirk-cat', xp: 25, w: 8, r: 1 }, { img: 'black-cat', xp: 40, w: 8, r: 2 },
  { img: 'kissing-cat', xp: 60, w: 4, r: 2 }, { img: 'heart-eyes-cat', xp: 90, w: 2, r: 3 },
];
const TAP_MSGS = ['Meow! 😸', 'Purr… purr… 😽', 'Let’s catch them all! 🐱', 'Mrrp?', 'Nyaa~ ♥', 'Meeeow!', 'Prrrrr 😽', 'Gotcha, floof! 😸'];
const PASS_MSGS = ['A cat brushed past your leg! 😸', 'Paws crossed — caught one!', 'Sneaky kitty joined you 😼', 'Purrfect timing!'];
const quest = { on: false, cats: [], timer: null, wobble: null };
const levelOf = xp => 1 + Math.floor(Math.sqrt(xp / 60));

function renderHud() {
  const lv = levelOf(S.xp), lo = 60 * (lv - 1) ** 2, hi = 60 * lv ** 2;
  $('#hudLvl').textContent = lv; $('#hudXp').textContent = `${S.xp} XP`;
  $('#hudXpFill').style.width = `${Math.min(100, (S.xp - lo) / (hi - lo) * 100)}%`;
  $('#hudDist').textContent = (session.dist / 1000).toFixed(2);
  $('#hudKcal').textContent = Math.round(session.dist / 1000 * S.weight * 0.53);
  $('#hudCats').textContent = session.cats;
}
function addXp(n) {
  const before = levelOf(S.xp); S.xp += n; save();
  if (levelOf(S.xp) > before) { toast(`😻 Level ${levelOf(S.xp)}! The cats are impressed.`, 2800); haptic(60); }
  renderHud();
}
function purr() {
  try {
    const ac = purr.ac || (purr.ac = new (window.AudioContext || window.webkitAudioContext)());
    const o = ac.createOscillator(), g = ac.createGain(), t = ac.currentTime;
    o.type = 'triangle'; o.frequency.setValueAtTime(520, t); o.frequency.exponentialRampToValueAtTime(880, t + .12); o.frequency.exponentialRampToValueAtTime(420, t + .38);
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(.12, t + .05); g.gain.exponentialRampToValueAtTime(.001, t + .4);
    o.connect(g).connect(ac.destination); o.start(t); o.stop(t + .42);
  } catch {}
}
/* A short teal-and-violet puff where a cat was caught (hidden by the system "remove animations" setting). */
function burst(pos) {
  const el = document.createElement('div'); el.className = 'burst';
  for (let i = 0; i < 8; i++) { const p = document.createElement('i'); const a = i / 8 * Math.PI * 2; p.style.setProperty('--dx', Math.round(Math.cos(a) * 34) + 'px'); p.style.setProperty('--dy', Math.round(Math.sin(a) * 34) + 'px'); if (i % 2) p.className = 'v'; el.append(p); }
  const m = new maplibregl.Marker({ element: el }).setLngLat([pos[1], pos[0]]).addTo(map); setTimeout(() => m.remove(), 700);
}
function popXp(pos, n) {
  const el = document.createElement('div'); el.className = 'xp-pop'; el.innerHTML = `<b>+${n} XP</b>`;
  const m = new maplibregl.Marker({ element: el }).setLngLat([pos[1], pos[0]]).addTo(map); setTimeout(() => m.remove(), 1000);
}
function spawnCat() {
  if (!me) return;
  let roll = Math.random() * CAT_TYPES.reduce((a, c) => a + c.w, 0), type = CAT_TYPES[0];
  for (const c of CAT_TYPES) { if ((roll -= c.w) < 0) { type = c; break; } }
  const pos = offset([me.lat, me.lon], 40 + Math.random() * 260, Math.random() * Math.PI * 2);
  const el = document.createElement('div'); el.className = 'cat'; el.innerHTML = `<span class="r${type.r}"><img src="assets/cats/${type.img}.svg" alt="" draggable="false"></span>`;
  const cat = { type, pos, el, span: el.firstChild, phase: Math.random() * 6.28 };
  el.addEventListener('click', ev => { ev.stopPropagation(); catchCat(cat, true); });
  cat.marker = new maplibregl.Marker({ element: el }).setLngLat([pos[1], pos[0]]).addTo(map);
  quest.cats.push(cat);
}
function topUpCats() {
  if (!quest.on || !me) return;
  quest.cats = quest.cats.filter(c => { if (dist([me.lat, me.lon], c.pos) > 600) { c.marker.remove(); return false; } return true; });
  while (quest.cats.length < 7) spawnCat();
}
function catchCat(cat, tapped) {
  if (cat.gone) return; cat.gone = true; quest.cats = quest.cats.filter(c => c !== cat);
  cat.span.classList.add('caught'); setTimeout(() => cat.marker.remove(), 450);
  session.cats++; S.caught++; syncCats(); addXp(cat.type.xp); popXp(cat.pos, cat.type.xp); burst(cat.pos);
  toast(tapped ? pick(TAP_MSGS) : pick(PASS_MSGS)); purr(); haptic(30); setTimeout(topUpCats, 1500);
}
function questTick(prev, cur) {
  const a = session.anchor || (session.anchor = cur), d = dist([a.lat, a.lon], [cur.lat, cur.lon]);
  if (cur.acc <= 50 && d >= 5) {
    const v = d / Math.max(.5, (cur.ts - a.ts) / 1000); session.anchor = cur;
    if (v <= 3.2) { session.dist += d; session.xpRem += d; if (session.xpRem >= 25) { const n = Math.floor(session.xpRem / 25); session.xpRem -= n * 25; addXp(n); } }
    else if (Date.now() - session.lastSlow > 20000) { session.lastSlow = Date.now(); toast('Whoa, speedy! Quest counts walking only'); }
  }
  for (const c of [...quest.cats]) if (dist([cur.lat, cur.lon], c.pos) < 20) catchCat(c, false);
  renderHud();
}
function setQuest(on) {
  quest.on = on; keepAwake(on || nav.active); document.body.classList.toggle('quest-on', on); clearInterval(quest.timer); clearInterval(quest.wobble);
  if (on) {
    Object.assign(session, { dist: 0, cats: 0, xpRem: 0, anchor: null }); renderHud();
    if (nav.dest && nav.mode === 'car') { nav.mode = 'foot'; syncModeSeg(); fetchRoute(); }
    if (me) { map.easeTo({ center: [me.lon, me.lat], zoom: 17 }); following = true; $('#locateBtn').classList.add('on'); topUpCats(); }
    quest.timer = setInterval(topUpCats, 8000);
    // cats wobble: sine-wave rotation re-applied every 140 ms
    quest.wobble = setInterval(() => { const t = Date.now() / 320; for (const c of quest.cats) if (!c.gone) c.span.style.transform = `rotate(${(Math.sin(t + c.phase) * 11).toFixed(1)}deg)`; }, 140);
    toast('Quest mode on — go find some cats! 😸', 2600);
  } else { quest.cats.forEach(c => c.marker.remove()); quest.cats = []; setWalking(false); quest.wAnchor = null; }
  syncModeSeg(); syncPanels();
}
function syncPanels() {
  const routeOpen = !!nav.dest && !nav.active;
  $('#sheet').hidden = quest.on && !routeOpen; $('#hud').hidden = !quest.on || routeOpen;
  document.body.classList.toggle('hud-on', quest.on && !routeOpen);
}

/* ================= theme & settings ================= */
function applyTheme(t) {
  S.theme = t; save(); document.body.dataset.theme = t;
  $('meta[name=theme-color]').content = t === 'light' ? '#ffffff' : '#000000';
  if (map) reloadStyle();
  if ((t === 'quest') !== quest.on) setQuest(t === 'quest');
  syncUI();
}
function syncUI() {
  $$('#themeSeg button').forEach(b => b.classList.toggle('on', b.dataset.theme === S.theme));
  $('#themeHint').textContent = { dark: 'Pitch-black AMOLED. Easy on the eyes and battery.', light: 'Bright and clean for daylight.', quest: 'Walking only. Cats spawn on the map — tap or stroll past them for XP.' }[S.theme];
  $('#weight').value = S.weight; $('#simToggle').checked = S.sim; $('#speedoToggle').checked = S.speedo !== false; $('#vehicleSel').value = S.vehicle || 'arrow'; $('#catVoiceToggle').checked = S.catVoice !== false; $('#limitToggle').checked = S.limitSign !== false; $('#meowAt').value = S.meowAt; $('#verLabel').textContent = 'v' + APP_VERSION; $('#aboutVer').textContent = 'Version ' + APP_VERSION;
  $('#srvPhoton').value = S.servers.photon; $('#srvNominatim').value = S.servers.nominatim; $('#srvOsrm').value = S.servers.osrm;
  syncMute();
}
function openDrawer(o) { $('#drawer').classList.toggle('open', o); $('#drawer').setAttribute('aria-hidden', String(!o)); $('#scrim').hidden = !o; }
$('#menuBtn').addEventListener('click', () => { renderSaved(); openDrawer(true); });
$('#closeDrawer').addEventListener('click', () => openDrawer(false));
// The trio: open the sibling apps (Android shell only; the website falls back to their pages)
$('#logShare').addEventListener('click', () => { try { window.AndroidTrio.shareLog(APP_VERSION); } catch { toast('The log is only kept in the Android app'); } });
$('#logClear').addEventListener('click', () => { try { window.AndroidTrio.clearLog(); toast('Log cleared'); } catch {} });
for (const b of document.querySelectorAll('[data-pkg]')) {
  b.addEventListener('click', () => {
    if (window.AndroidApps) window.AndroidApps.open(b.dataset.pkg, b.dataset.url);
    else window.open(b.dataset.url, '_blank', 'noopener');
  });
}
$('#scrim').addEventListener('click', () => openDrawer(false));
document.addEventListener('keydown', e => { if (e.key === 'Escape') openDrawer(false); });
$('#themeSeg').addEventListener('click', e => { const b = e.target.closest('button'); if (b) applyTheme(b.dataset.theme); });
$('#srvToggle').addEventListener('click', () => { $('#srvBox').hidden = !$('#srvBox').hidden; });
$('#srvSave').addEventListener('click', () => {
  S.servers = { photon: trim($('#srvPhoton').value), nominatim: trim($('#srvNominatim').value), osrm: trim($('#srvOsrm').value) };
  save(); syncUI(); toast('Servers saved');
});
$('#srvReset').addEventListener('click', () => { S.servers = { photon: '', nominatim: '', osrm: '' }; save(); syncUI(); toast('Using public servers'); });
$('#weight').addEventListener('change', e => { S.weight = Math.min(250, Math.max(30, +e.target.value || 65)); e.target.value = S.weight; save(); renderHud(); });
$('#vehicleSel').addEventListener('change', e => {
  S.vehicle = e.target.value; save(); update3dArrow();
  toast(`${(VEHICLES[S.vehicle] || VEHICLES.arrow).name} chosen. You will see it when you start navigating.`, 2800);
});
$('#overviewBtn').addEventListener('click', routeOverview);
$('#alongBtn').addEventListener('click', () => { const a = $('#along'); if (a.hidden) openAlong('fuel'); else a.hidden = true; });
$('#alongChip').addEventListener('click', () => openAlong('fuel'));
$('#alongClose').addEventListener('click', () => { $('#along').hidden = true; });
$('#alongChips').addEventListener('click', e => { const c = e.target.closest('[data-along]'); if (c) showAlong(c.dataset.along); });
$('#alongList').addEventListener('click', e => { const b = e.target.closest('[data-add]'); if (b && alongHits[+b.dataset.add]) addAlongStop(alongHits[+b.dataset.add]); });
$('#destWx').addEventListener('click', () => { const d = nav.dest; if (d && TRIO) TRIO.openUri(`purrcast://weather?lat=${d.lat}&lon=${d.lon}&name=${encodeURIComponent(d.name || '')}`, REPO_URL + '/releases'); });
$('#catVoiceToggle').addEventListener('change', e => { S.catVoice = e.target.checked; save(); if (e.target.checked) speak(catSay('far', 'In 200 metres, turn left.'), true); });
$('#limitToggle').addEventListener('change', e => { S.limitSign = e.target.checked; save(); limit.at = 0; renderSpeedo(); });
$('#heroToggle').addEventListener('change', e => { S.heroOn = e.target.checked; save(); refreshTrio(); });
$('#speedoToggle').addEventListener('change', e => { S.speedo = e.target.checked; save(); renderSpeedo(); });
$('#meowAt').addEventListener('change', e => { const v = Math.min(300, Math.max(0, Math.round(+e.target.value || 0))); S.meowAt = v; e.target.value = v; meowArmed = true; save(); renderSpeedo(); toast(v ? `Meow at ${v} km/h 😼` : 'Meow off'); });
$('#simToggle').addEventListener('change', e => { setSim(e.target.checked); toast(e.target.checked ? 'Simulating a stroll' : 'Simulation off'); });

function sync3dBtn() { $('#view3dToggle').checked = view3d; }
$('#view3dToggle').addEventListener('change', e => {
  view3d = e.target.checked;
  if (nav.active) navCamera(500);
  else map.easeTo({ pitch: view3d ? 58 : 0, zoom: view3d ? Math.max(map.getZoom(), 16.5) : map.getZoom(), duration: 700 });
  S.view3d = view3d; save(); toast(view3d ? '3D map on' : 'Flat map');
});
sync3dBtn();
/* Compass: shows only when the map is turned or tilted. One tap faces north and flattens it. */
function syncCompass() {
  const b = $('#compassBtn'), off = Math.abs(map.getBearing()) > 2 || map.getPitch() > 4;
  b.hidden = !off; $('#compassNeedle').style.transform = `rotate(${-map.getBearing()}deg)`;
}
$('#compassBtn').addEventListener('click', () => { following = false; $('#locateBtn').classList.remove('on'); resetCamera(); });
$('#locateBtn').addEventListener('click', () => {
  if (!me) { startLocation(); return toast('Looking for you…'); }
  following = true; $('#locateBtn').classList.add('on'); if (nav.active) navCamera(600); else map.easeTo({ center: [me.lon, me.lat], zoom: Math.max(map.getZoom(), 16) });
});
$('#sheetHandle').addEventListener('click', () => $('#sheet').classList.toggle('collapsed'));
new ResizeObserver(() => document.documentElement.style.setProperty('--sheet-h', ($('#sheet').hidden ? 0 : $('#sheet').offsetHeight) + 'px')).observe($('#sheet'));

/* ================= updates =================
   In the Android app: new APKs come from this project's GitHub releases and install in-app (Android asks to confirm).
   In a browser: the service worker + version.json route. */
const NATIVE_UPD = !!window.AndroidUpdater;
const RELEASES_URL = 'https://api.github.com/repos/VK-VfX/nikezverapps/releases?per_page=30';
const updStatus = t => $('#updStatus').textContent = t;
const vNewer = (a, b) => { const x = a.split('.').map(n => +n || 0), y = b.split('.').map(n => +n || 0); for (let i = 0; i < Math.max(x.length, y.length); i++) { if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0); } return false; };
let pendingApk = null, installing = false;
window.__updProgress = t => { updStatus(t); if (/^Update failed|^Install/.test(t)) installing = false; };

async function checkNative(manual) {
  if (manual) updStatus('Checking…');
  try {
    const rs = await (await fetch(RELEASES_URL, { headers: { Accept: 'application/vnd.github+json' } })).json();
    const r = rs.find(x => /^mapsandcats-v/.test(x.tag_name) && !x.draft && !x.prerelease);
    const v = r ? r.tag_name.replace('mapsandcats-v', '') : '';
    const a = r && r.assets.find(x => x.name.endsWith('.apk'));
    if (r && a && vNewer(v, APP_VERSION)) {
      pendingApk = { url: a.browser_download_url, sha: (a.digest || '').replace('sha256:', ''), version: v };
      $('#updateDot').hidden = false; $('#installUpd').hidden = false; $('#installUpd').textContent = 'Install v' + v;
      updStatus(`v${v} is ready. Install updates the app in place; your places stay.`);
    } else if (manual) updStatus(`You’re up to date (v${APP_VERSION}).`);
  } catch { if (manual) updStatus('Couldn’t check — are you offline?'); }
}
function installNative() {
  if (!pendingApk || installing) return;
  if (!window.AndroidUpdater.canInstall()) { updStatus('Allow Maps and Cats to install apps, then tap Install again.'); window.AndroidUpdater.openPermission(); return; }
  installing = true; updStatus('Downloading…');
  window.AndroidUpdater.install(pendingApk.url, pendingApk.sha);
}

let swReg = null, waitingSW = null;
function showUpdateReady() { $('#updateDot').hidden = false; $('#installUpd').hidden = false; updStatus('A new version is ready to install.'); }
async function checkUpdates(manual) {
  if (NATIVE_UPD) return checkNative(manual);
  if (manual) updStatus('Checking…');
  try {
    if (swReg) await swReg.update();
    if (swReg?.waiting) { waitingSW = swReg.waiting; showUpdateReady(); return; }
    const j = await (await fetch('version.json?_=' + Date.now(), { cache: 'no-store' })).json();
    if (j.version !== APP_VERSION) { if (!swReg) { $('#installUpd').hidden = false; $('#updateDot').hidden = false; updStatus(`v${j.version} is available — reload to update.`); } else updStatus('Downloading update…'); }
    else if (manual) updStatus(`You’re up to date (v${APP_VERSION}).`);
  } catch { if (manual) updStatus('Couldn’t check — are you offline?'); }
}
$('#checkUpd').addEventListener('click', () => checkUpdates(true));
$('#installUpd').addEventListener('click', () => { if (NATIVE_UPD) return installNative(); updStatus('Installing…'); if (waitingSW) waitingSW.postMessage('SKIP_WAITING'); else location.reload(); });
if (NATIVE_UPD) {
  checkUpdates(false); setInterval(() => checkUpdates(false), 6 * 60 * 60 * 1000);
} else if ('serviceWorker' in navigator) {
  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => { if (reloading) return; reloading = true; location.reload(); });
  navigator.serviceWorker.register('sw.js').then(reg => {
    swReg = reg;
    const watch = w => w && w.addEventListener('statechange', () => { if (w.state === 'installed' && navigator.serviceWorker.controller) { waitingSW = reg.waiting; showUpdateReady(); } });
    if (reg.waiting && navigator.serviceWorker.controller) { waitingSW = reg.waiting; showUpdateReady(); }
    reg.addEventListener('updatefound', () => watch(reg.installing));
    checkUpdates(false); setInterval(() => checkUpdates(false), 30 * 60 * 1000);
  }).catch(() => {});
} else checkUpdates(false);

/* ================= boot ================= */
// Android back button hook: close drawer / stop nav / close route first, otherwise let the app exit.
window.__macBack = () => {
  if ($('#drawer').classList.contains('open')) { openDrawer(false); return true; }
  if (nav.active) { stopNav(false); return true; }
  if (nav.dest) { clearRoute(); return true; }
  return false;
};
document.body.dataset.theme = S.theme;
syncUI(); renderSaved(); renderNearby(); refreshTrio();
initMap().then(() => {
  if (S.theme === 'quest') setQuest(true);
  startLocation(); if (S.sim) setSim(true);
  window.__macCheckPending();
  window.__mac = { drawRoutes, burst, showSkeleton, showEmpty, map, S, nav, quest, onPos, selectPlace, catSay, limit, parseLimit, startNavTest: () => $('#startNav').click(), spawnCat, catchCat, segmentByScript, blendSearch, webSearch, pageCoords, relevance, mergeRank, getMe: () => me };
});
})();
