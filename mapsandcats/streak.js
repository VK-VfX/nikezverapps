/* The day streak and the Streak Shield. Pure rules, no browser: the same rules run in Purrcast (Java) and Zamoled (Kotlin), and
   all three are checked against docs/streak-vectors.json so they cannot drift apart.
   A visit is the app coming to the front; the day is the phone's local calendar day. The shield saves a gap of 1 to 7 missed days,
   once, and recharges after 7 visit days in a row. It only works while all three apps are installed. */
(function (root) {
  const RECHARGE_DAYS = 7, MAX_SAVED_GAP = 8, M64 = (1n << 64n) - 1n;

  /* A fresh state. `mask` is an unsigned 64-bit number kept as a decimal string so it survives JSON. */
  const newStreak = () => ({ count: 0, best: 0, last: 0, mask: '0', ready: true, charge: 0 });

  /* The streak after a visit on `day`; the same object when nothing changes (same day, or the clock went backwards). */
  function streakStep(s, day, shieldOn) {
    if (s.last !== 0 && day <= s.last) return s;
    const gap = s.last === 0 ? 0 : day - s.last;
    const n = Object.assign({}, s);
    if (gap === 1) {
      n.count++;
      if (!n.ready) { n.charge++; if (n.charge >= RECHARGE_DAYS) { n.ready = true; n.charge = 0; } }
    } else if (gap >= 2 && gap <= MAX_SAVED_GAP && shieldOn && s.ready) {
      n.count++; n.ready = false; n.charge = 0;
    } else {
      n.count = 1; if (!n.ready) n.charge = 0;
    }
    n.best = Math.max(s.best, n.count);
    const delta = s.last === 0 ? 64 : gap;
    n.mask = String(delta >= 64 ? 1n : (((BigInt(s.mask) << BigInt(delta)) & M64) | 1n));
    n.last = day;
    return n;
  }

  /* '' when the shield is off, else 'shield ready' or 'shield recharging, N days to go'. */
  const streakLabel = (s, shieldOn) => !shieldOn ? '' : s.ready ? 'shield ready' : `shield recharging, ${RECHARGE_DAYS - s.charge} days to go`;

  /* An old history with only the 64-day mask: its streak is the run of set bits from bit 0. */
  function streakFromLegacy(mask) {
    const m = BigInt(mask); let run = 0;
    while (run < 64 && ((m >> BigInt(run)) & 1n) === 1n) run++;
    return Object.assign(newStreak(), { count: run, best: run, mask: String(m) });
  }

  /* The local calendar day number for a clock time and its time zone offset (milliseconds east of UTC). */
  const streakDay = (nowMs, tzOffsetMs) => Math.floor((nowMs + tzOffsetMs) / 86400000);

  const api = { streakStep, streakLabel, streakFromLegacy, streakDay, newStreak };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else Object.assign(root, api);
})(typeof window !== 'undefined' ? window : globalThis);
