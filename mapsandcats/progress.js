/* Maps and Cats progress for the save code: the streak block plus xp and cats caught. Pure rules; the same layout as
   Purrcast (Java) and Zamoled (Kotlin), checked against docs/save-code-vectors.json. Needs streak.js and savecode.js. */
(function (root) {
  const M64 = (1n << 64n) - 1n;

  function varint(n, out) { n = BigInt(n); for (;;) { const b = Number(n & 0x7Fn); n >>= 7n; if (n) out.push(b | 0x80); else { out.push(b); return; } } }
  function reader(bytes) {
    let p = 0;
    const u8 = () => { if (p >= bytes.length) throw new Error('short payload'); return bytes[p++]; };
    const vi = () => { let v = 0n, sh = 0n; for (;;) { const x = u8(); v |= BigInt(x & 0x7F) << sh; if (!(x & 0x80)) return Number(v); sh += 7n; } };
    return { u8, vi };
  }

  /* The payload without its CRC: streak block, then xp and cats caught. */
  function progressPayload(S) {
    const s = S.streak || {}, o = [];
    varint(s.count | 0, o); varint(s.best | 0, o); varint(s.last || 0, o);
    const m = BigInt(s.mask || '0') & M64; for (let i = 7; i >= 0; i--) o.push(Number((m >> BigInt(i * 8)) & 0xFFn));
    o.push((s.ready === false ? 0 : 1) | ((s.charge | 0) << 1));
    varint(S.xp | 0, o); varint(S.caught | 0, o);
    return Uint8Array.from(o);
  }
  function progressFromPayload(bytes) {
    const r = reader(bytes), count = r.vi(), best = r.vi(), last = r.vi();
    let m = 0n; for (let i = 0; i < 8; i++) m = (m << 8n) | BigInt(r.u8());
    const sh = r.u8();
    return { streak: { count, best, last, mask: String(m), ready: !!(sh & 1), charge: sh >> 1 }, xp: r.vi(), caught: r.vi() };
  }
  /* Never lowers: keeps the newer streak block, takes the larger best, xp and caught. */
  function mergeStreakBlock(local, remote) {
    const keep = Object.assign({}, (local.last || 0) >= (remote.last || 0) ? local : remote);
    keep.best = Math.max(local.best | 0, remote.best | 0, keep.count | 0);
    return keep;
  }
  function progressMerge(S, remote) {
    const streak = mergeStreakBlock(S.streak || { count: 0, best: 0, last: 0, mask: '0', ready: true, charge: 0 }, remote.streak);
    return { streak, xp: Math.max(S.xp | 0, remote.xp), caught: Math.max(S.caught | 0, remote.caught) };
  }
  /* Applies a decoded payload to S (mutates S) and returns the message to show. */
  function progressApply(S, payload) {
    let remote; try { remote = progressFromPayload(payload); } catch { throw new Error('That code has a typo. Check the characters near the end.'); }
    const m = progressMerge(S, remote); S.streak = Object.assign({}, S.streak, m.streak); S.xp = m.xp; S.caught = m.caught;
    return `Restored: ${S.streak.count} day streak, best ${S.streak.best}.`;
  }
  function progressRestoreText(S, pasted) {
    const r = root.saveCodeDecode(pasted, 'MAC1');
    if (r.kind === 'typo') throw new Error('That code has a typo. Check the characters near the end.');
    if (r.kind === 'wrongApp') throw new Error('That code belongs to ' + ({ PRC1: 'Purrcast', ZAM1: 'Zamoled' }[r.other] || 'another app') + '.');
    if (r.kind !== 'ok') throw new Error("I couldn't find a Maps and Cats code in that.");
    return progressApply(S, r.payload);
  }
  const SETTINGS = ['theme', 'speedo', 'meowAt', 'catVoice', 'limitSign', 'weight', 'avoidHw', 'voice', 'heroOn'];
  /* The optional backup file: the code, saved places and a few settings. No history, no tracks. */
  function progressBackup(S) {
    const settings = {}; for (const k of SETTINGS) if (S[k] !== undefined) settings[k] = S[k];
    return JSON.stringify({ app: 'mapsandcats', format: 1, saved: new Date().toISOString().slice(0, 10), progress: root.saveCodeEncode('MAC1', progressPayload(S)), saved_places: S.saved, settings }, null, 2);
  }
  function progressRestoreBackup(S, json, withSettings) {
    let o; try { o = JSON.parse(json); } catch { throw new Error('That file could not be read.'); }
    if (o.app !== 'mapsandcats') throw new Error('That file is not a Maps and Cats backup.');
    let msg = progressRestoreText(S, o.progress || ''), added = 0;
    const sp = o.saved_places || {}, near = (a, b) => a && b && Math.round(a.lat * 1e4) === Math.round(b.lat * 1e4) && Math.round(a.lon * 1e4) === Math.round(b.lon * 1e4);
    if (sp.home && !S.saved.home) { S.saved.home = sp.home; added++; }
    if (sp.work && !S.saved.work) { S.saved.work = sp.work; added++; }
    for (const f of sp.favs || []) if (!S.saved.favs.some(x => near(x, f))) { S.saved.favs.push(f); added++; }
    if (withSettings && o.settings) for (const k of SETTINGS) if (k in o.settings) S[k] = o.settings[k];
    return msg + (added ? ` ${added} saved place${added === 1 ? '' : 's'} added.` : '');
  }

  const api = { progressPayload, progressFromPayload, progressMerge, progressApply, progressRestoreText, progressBackup, progressRestoreBackup };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else Object.assign(root, api);
})(typeof window !== 'undefined' ? window : globalThis);
