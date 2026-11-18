// Maps and Cats progress mapping and merge. Usage: node mapsandcats/tools/progress.test.js
const sc = require('../savecode.js'); Object.assign(globalThis, sc);
const { progressPayload, progressFromPayload, progressMerge, progressRestoreText, progressApply } = require('../progress.js');
let fail = 0; const check = (n, got, want) => { if (String(got) !== String(want)) { console.log(`FAIL ${n}: got ${got} want ${want}`); fail++; } };
const hex = b => Buffer.from(b).toString('hex');
const S = { streak: { count: 5, best: 9, last: 20409, mask: '31', ready: true, charge: 0 }, xp: 1200, caught: 37, saved: { home: null, work: null, favs: [] } };
check('payload hex', hex(progressPayload(S)), '0509b99f01000000000000001f01b00925');
check('code', saveCodeEncode('MAC1', progressPayload(S)), 'MAC1-0M4V-K7R1-0000-0000-0001-Y0DG-14JM-P8G');
const back = progressFromPayload(progressPayload(S)); check('round trip', JSON.stringify(back), JSON.stringify({ streak: S.streak, xp: 1200, caught: 37 }));
const m = progressMerge({ streak: { count: 5, best: 5, last: 100, mask: '31', ready: true, charge: 0 }, xp: 10, caught: 2 }, { streak: { count: 9, best: 30, last: 90, mask: '7', ready: false, charge: 3 }, xp: 500, caught: 1 });
check('newer local count', m.streak.count, 5); check('best max', m.streak.best, 30); check('xp max', m.xp, 500); check('caught max', m.caught, 2);
const z = progressMerge(S, { streak: { count: 0, best: 0, last: 0, mask: '0', ready: true, charge: 0 }, xp: 0, caught: 0 });
check('zero count', z.streak.count, 5); check('zero xp', z.xp, 1200);
const T = { streak: { count: 0, best: 0, last: 0, mask: '0', ready: true, charge: 0 }, xp: 0, caught: 0, saved: { home: null, work: null, favs: [] } };
check('restore message', progressRestoreText(T, 'My notes\nMAC1-0M4V-K7R1-0000-0000-0001-Y0DG-14JM-P8G\nbye'), 'Restored: 5 day streak, best 9.');
check('restored xp', T.xp, 1200);
let msg = ''; try { progressRestoreText(T, 'PRC1-1RAB-K7R1-0000-0000-00ZZ-Y081-0VAG'); } catch (e) { msg = e.message; } check('other app', msg, 'That code belongs to Purrcast.');
if (fail) { console.log(fail + ' failures'); process.exit(1); } console.log('OK progress');
