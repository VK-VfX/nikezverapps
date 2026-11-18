// Holistic place names for Maps and Cats: the same cases Purrcast's Java version passes (docs/place-names-vectors.json).
// Usage: node mapsandcats/tools/placenames.test.js
const fs = require('fs'), path = require('path');
const { isBody, stripBody, resolvePlace, holisticQuery, resultSub } = require('../placenames.js');
const v = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/place-names-vectors.json'), 'utf8'));
let pass = 0, fail = 0;
const check = (name, got, want) => { const ok = JSON.stringify(got) === JSON.stringify(want); console.log((ok ? 'PASS  ' : 'FAIL  ') + name + (ok ? '' : `   got [${JSON.stringify(got)}] want [${JSON.stringify(want)}]`)); ok ? pass++ : fail++; };
for (const [name, body, stripped] of v.bodies) { check('body: ' + (name || '(empty)'), isBody(name), body); check('strip: ' + (name || '(empty)'), stripBody(name), stripped); }
for (const c of v.resolve) { const r = resolvePlace(c.address); check('resolve: ' + JSON.stringify(c.address), { city: r.city, neighborhood: r.neighborhood, fromBody: r.fromBody }, { city: c.city, neighborhood: c.neighborhood, fromBody: c.fromBody }); }
// a council typed into the search box is searched as the place it governs
check('query: council becomes the place', holisticQuery('Majlis Perbandaran Petaling Jaya Selatan'), 'Petaling Jaya Selatan');
check('query: a normal search is untouched', holisticQuery('nasi lemak Subang Jaya'), 'nasi lemak Subang Jaya');
check('query: a council inside a longer search is kept as typed', holisticQuery('pejabat Majlis Perbandaran Petaling Jaya'), 'pejabat Majlis Perbandaran Petaling Jaya');
check('query: spaces are trimmed', holisticQuery('  Stadt Köln  '), 'Köln');
// the line under a result: street, the town (never a council), the state; the country only when it is not the user's
check('sub: council city becomes the place and the state follows', resultSub({ street: 'Persiaran Sultan', address: { city: 'Majlis Perbandaran Petaling Jaya Selatan', state: 'Selangor', country: 'Malaysia', cc: 'my' }, userCc: 'my', name: 'Taman X' }), 'Persiaran Sultan, Petaling Jaya Selatan, Selangor');
check('sub: a real town and the state', resultSub({ address: { city: 'Shah Alam', state: 'Selangor', country: 'Malaysia', cc: 'my' }, userCc: 'my', name: 'Masjid Sultan Salahuddin' }), 'Shah Alam, Selangor');
check('sub: the country appears when it is another country', resultSub({ address: { village: 'Florence', state: 'Colorado', country: 'United States', cc: 'us' }, userCc: 'my', name: 'Florence Library' }), 'Florence, Colorado, United States');
check('sub: a result named like its own town does not repeat it', resultSub({ address: { city: 'Subang Jaya', state: 'Selangor', cc: 'my' }, userCc: 'my', name: 'Subang Jaya' }), 'Selangor');
check('sub: a suburb is used when no town is known', resultSub({ address: { suburb: 'Bangsar', state: 'Kuala Lumpur', cc: 'my' }, userCc: 'my', name: 'Bangsar Village' }), 'Bangsar, Kuala Lumpur');
console.log('\n' + pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
