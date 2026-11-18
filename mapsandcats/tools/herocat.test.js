// Runs docs/hero-cat-vectors.json through the supercat rules. Usage: node tools/herocat.test.js
const fs = require('fs'), path = require('path');
const { heroPhase, heroJoy, heroEmotion, heroAnimated } = require('../streak.js');
const j = JSON.parse(fs.readFileSync(path.join(__dirname, '../../docs/hero-cat-vectors.json'), 'utf8'));
let fail = 0, n = 0;
for (const c of j.phases) {
  n++;
  const got = [heroPhase(c.count, c.ready, c.charge), heroAnimated(c.count), heroJoy(c.count)].join('/');
  const want = [c.phase, c.animated, c.joy].join('/');
  if (got !== want) { console.log('FAIL phase', c.count, c.ready, c.charge, got, want); fail++; }
}
for (const c of j.emotions) {
  n++;
  const got = heroEmotion(c.weather, c.count);
  if (got !== c.emotion) { console.log('FAIL emotion', JSON.stringify(c), got); fail++; }
}
console.log(fail ? fail + ' FAILED of ' + n : 'OK ' + n + ' cases');
process.exit(fail ? 1 : 0);
