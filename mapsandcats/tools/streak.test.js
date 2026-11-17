// Runs every case in docs/streak-vectors.json through streakStep. Usage: node mapsandcats/tools/streak.test.js
const fs = require('fs'), path = require('path');
const { streakAdopt, streakStep, streakLabel, streakFromLegacy, newStreak } = require('../streak.js');
const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/streak-vectors.json'), 'utf8')).cases;
let fail = 0;
const check = (name, what, got, want) => { if (String(got) !== String(want)) { console.log(`FAIL ${name}: ${what} got ${got} want ${want}`); fail++; } };
for (const c of cases) {
  let s = newStreak();
  const days = Array.isArray(c.visits) ? c.visits : Array.from({ length: c.visits.to - c.visits.from + 1 }, (_, i) => c.visits.from + i);
  for (const d of days) s = streakStep(s, d, c.shieldOn);
  const e = c.expect;
  check(c.name, 'count', s.count, e.count); check(c.name, 'best', s.best, e.best); check(c.name, 'last', s.last, e.last);
  check(c.name, 'mask', s.mask, e.mask); check(c.name, 'ready', s.ready, e.ready); check(c.name, 'charge', s.charge, e.charge);
  check(c.name, 'label', streakLabel(s, c.shieldOn), e.label);
}
for (const c of JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/streak-vectors.json'), 'utf8')).adopt) {
  const n = streakAdopt(Object.assign(newStreak(), c.local), c.today, c.sibling);
  check(c.name, 'adopt count', n.count, c.expect.count); check(c.name, 'adopt best', n.best, c.expect.best);
}
check('legacy 0b111', 'count', streakFromLegacy('7').count, 3);
check('legacy all ones', 'count', streakFromLegacy('18446744073709551615').count, 64);
check('legacy empty', 'count', streakFromLegacy('0').count, 0);
if (fail) { console.log(fail + ' failures'); process.exit(1); }
console.log('OK ' + cases.length + ' cases');
