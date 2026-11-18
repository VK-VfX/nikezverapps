// Checks savecode.js against docs/save-code-vectors.json. Usage: node mapsandcats/tools/savecode.test.js
const fs = require('fs'), path = require('path');
const { saveCodeEncode, saveCodeDecode } = require('../savecode.js');
const v = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/save-code-vectors.json'), 'utf8'));
const hex = b => Buffer.from(b).toString('hex');
let fail = 0; const check = (n, got, want) => { if (String(got) !== String(want)) { console.log(`FAIL ${n}: got ${got} want ${want}`); fail++; } };
for (const c of v.cases) {
  const want = c.payloadHex.slice(0, -4);
  check(c.app + ' encode', saveCodeEncode(c.prefix, Buffer.from(want, 'hex')), c.code);
  const r = saveCodeDecode(c.code, c.prefix); check(c.app + ' decode', r.kind, 'ok'); if (r.payload) check(c.app + ' payload', hex(r.payload), want);
}
const kind = k => ({ OK: 'ok', WRONG_APP: 'wrongApp', TYPO: 'typo', NOT_FOUND: 'notFound' })[k];
for (const m of v.messy) {
  const r = saveCodeDecode(m.text, m.prefix); check(m.name + ' kind', r.kind, kind(m.expectKind));
  if (m.expectPayloadHex && r.payload) check(m.name + ' payload', hex(r.payload), m.expectPayloadHex);
  if (m.expectOther) check(m.name + ' other', r.other, m.expectOther);
}
let big = ''; for (let i = 0; i < 5e6; i++) big += String.fromCharCode(65 + (i * 7919) % 26);
const t0 = Date.now(); check('5 MB paste kind', saveCodeDecode(big, 'PRC1').kind, 'notFound'); check('5 MB fast', Date.now() - t0 < 2000, true);
if (fail) { console.log(fail + ' failures'); process.exit(1); }
console.log('OK ' + v.cases.length + ' vectors, ' + v.messy.length + ' messy');
