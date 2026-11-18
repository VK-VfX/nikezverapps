/* Holistic place names for Maps and Cats: the same logic as Purrcast's PlaceNames (Java), checked against the same cases
   (docs/place-names-vectors.json). A council is never the place: results and searches point at the town, the city or the state. */
(function (root) {
  const F = 'iu';
  // A body in front of the name (Malay, Indonesian, French, Spanish, Portuguese, Italian, English).
  const PREFIX = new RegExp('^(majlis\\s+(perbandaran|bandaraya|daerah|bandaran|tempatan)|dewan\\s+bandaraya|pejabat\\s+daerah|pihak\\s+berkuasa\\s+tempatan'
    + '|pemerintah\\s+(kota|kabupaten|daerah)|pemkot|pemkab'
    + "|mairie\\s+(de|du|des|d['\\u2019])|communaut[\\u00e9e]\\s+(de\\s+communes|d['\\u2019]agglom[\\u00e9e]ration|urbaine)|m[\\u00e9e]tropole\\s+(de|du|des|d['\\u2019])"
    + '|ayuntamiento\\s+(de|del)|c[\\u00e2a]mara\\s+municipal\\s+(de|do|da|dos|das)|prefeitura(\\s+municipal)?\\s+(de|do|da|dos|das)'
    + "|comune\\s+(di|del|della|dell['\\u2019]|dei)|citt[\\u00e0a]\\s+metropolitana\\s+di"
    + '|shire\\s+of|regional\\s+(district|municipality|council)\\s+of'
    + '|(municipality|municipal\\s+council|city\\s+council|district\\s+council|town\\s+council)\\s+of)\\b\\s*', F);
  // German and Dutch bodies are a word then the name; the space is required so "Stadtteil Mitte" and "Stadtpark" stay places.
  const WORD_PREFIX = new RegExp('^(stadtverwaltung|gemeindeverwaltung|stadtgemeinde|marktgemeinde|verbandsgemeinde|ortsgemeinde|gemeinde|stadt|landratsamt|bezirksamt|gemeente)\\s+', F);
  // A body after the name ("Subang Jaya City Council", "Çankaya Belediyesi", Chinese, Japanese and Korean city offices).
  const SUFFIX = new RegExp('\\s*(\\b((city|municipal|district|borough|county|town|metropolitan|regional|shire)\\s+council|city\\s+hall|belediyesi|kommune|kommun)'
    + '|(\\u4eba\\u6c11\\u653f\\u5e9c|\\u653f\\u5e9c|\\u5f79\\u6240|\\u5f79\\u5834|\\uc2dc\\uccad|\\uad6c\\uccad|\\uad70\\uccad))$', F);

  const isBody = name => { const s = String(name || '').trim(); return s.length > 0 && (PREFIX.test(s) || WORD_PREFIX.test(s) || SUFFIX.test(s)); };
  /* The place a body is named after ("Majlis Bandaraya Subang Jaya" gives "Subang Jaya"); a normal name stays; '' if nothing is left. */
  function stripBody(name) {
    let s = String(name || '').trim(); if (!isBody(s)) return s;
    return s.replace(PREFIX, '').replace(WORD_PREFIX, '').replace(SUFFIX, '').trim();
  }

  // A county or borough is an administrative area, not a town.
  const CITY_KEYS = ['city', 'town', 'village', 'municipality'];
  const AREA_KEYS = ['suburb', 'quarter', 'neighbourhood', 'city_district', 'borough', 'district', 'hamlet'];
  const val = (a, k) => String((a && a[k]) || '').trim();
  /* The city and the neighbourhood from a Nominatim-style address; fromBody is true when the city was worked out from a council. */
  function resolvePlace(address) {
    const r = { city: '', neighborhood: '', fromBody: false };
    for (const k of CITY_KEYS) { const v = val(address, k); if (v && !isBody(v)) { r.city = v; break; } }
    if (!r.city) for (const k of CITY_KEYS) { const v = val(address, k), s = isBody(v) ? stripBody(v) : ''; if (s) { r.city = s; r.fromBody = true; break; } }
    for (const k of AREA_KEYS) { const v = val(address, k); if (v && !isBody(v)) { r.neighborhood = v; break; } }
    if (r.neighborhood === r.city) r.neighborhood = '';
    return r;
  }

  /* A whole search that is just a council is searched as the place it governs; anything longer is left as typed. */
  function holisticQuery(q) { const s = String(q || '').trim(); return isBody(s) && stripBody(s) ? stripBody(s) : s; }

  /* The line under a result: street, neighbourhood, the town (never a council), the state, and the country only when it is not the user's own. */
  function resultSub(o) {
    const a = o.address || {}, r = resolvePlace(a), name = String(o.name || '').trim().toLowerCase(), seen = new Set(), parts = [];
    const add = t => { t = String(t || '').trim(); const k = t.toLowerCase(); if (!t || k === name || seen.has(k)) return; seen.add(k); parts.push(t); };
    add(o.street); add(r.neighborhood); add(r.city); add(a.state);
    if (a.country && (!o.userCc || !a.cc || String(a.cc).toLowerCase() !== String(o.userCc).toLowerCase())) add(a.country);
    return parts.join(', ');
  }

  const api = { isBody, stripBody, resolvePlace, holisticQuery, resultSub };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else Object.assign(root, api);
})(typeof window !== 'undefined' ? window : globalThis);
