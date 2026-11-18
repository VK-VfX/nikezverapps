/* The progress save code: a short, typo-proof text to keep in Notes, a screenshot, a calendar or a file.
   Same format as Purrcast (Java) and Zamoled (Kotlin); all three are checked against docs/save-code-vectors.json.
   Pure rules, no browser. */
(function (root) {
  const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ', PREFIXES = ['PRC1', 'ZAM1', 'MAC1'];
  const extras = p => p === 'PRC1' ? 1 : p === 'MAC1' ? 2 : 0;

  function crc16(bytes) {
    let c = 0xFFFF;
    for (const b of bytes) { c ^= b << 8; for (let k = 0; k < 8; k++) c = (c & 0x8000) ? ((c << 1) ^ 0x1021) & 0xFFFF : (c << 1) & 0xFFFF; }
    return c;
  }

  function saveCodeEncode(prefix, payload) {
    const crc = crc16(payload), full = [...payload, crc >> 8, crc & 255];
    let chars = '', acc = 0, bits = 0;
    for (const x of full) { acc = (acc << 8) | x; bits += 8; while (bits >= 5) { chars += ALPHABET[(acc >> (bits - 5)) & 31]; bits -= 5; } acc &= (1 << bits) - 1; }
    if (bits > 0) chars += ALPHABET[(acc << (5 - bits)) & 31];
    let out = prefix; for (let i = 0; i < chars.length; i += 4) out += '-' + chars.slice(i, i + 4);
    return out;
  }

  const norm = c => { const u = c.toUpperCase(); return u === 'O' ? '0' : (u === 'I' || u === 'L') ? '1' : u; };
  const wordChar = c => /[0-9A-Za-z]/.test(c);
  function find(s, prefix) {
    for (let i = 0; i + prefix.length <= s.length; i++) {
      if (i > 0 && wordChar(s[i - 1])) continue;
      let ok = true; for (let k = 0; k < prefix.length; k++) if (norm(s[i + k]) !== norm(prefix[k])) { ok = false; break; }
      if (ok) return i;
    }
    return -1;
  }

  function saveCodeDecode(pasted, prefix) {
    if (!pasted) return { kind: 'notFound' };
    const s = String(pasted), at = find(s, prefix);
    if (at < 0) { for (const p of PREFIXES) if (p !== prefix && find(s, p) >= 0) return { kind: 'wrongApp', other: p }; return { kind: 'notFound' }; }
    let pos = at + prefix.length, acc = 0, bits = 0;
    const nextByte = () => {
      while (bits < 8) {
        let v = -1;
        while (pos < s.length) { const c = s[pos++]; if (c === '-' || c === ' ' || c === '\n' || c === '\r' || c === '\t') continue; v = ALPHABET.indexOf(norm(c)); break; }
        if (v < 0) return -1;
        acc = (acc << 5) | v; bits += 5;
      }
      const b = (acc >> (bits - 8)) & 255; bits -= 8; acc &= (1 << bits) - 1; return b;
    };
    const out = [], varints = 3 + extras(prefix), typo = { kind: 'typo' };
    const nine = () => { for (let i = 0; i < 9; i++) { const b = nextByte(); if (b < 0) return false; out.push(b); } return true; };
    for (let v = 0; v < varints; v++) {
      if (v === 3 && !nine()) return typo;
      for (let i = 0; ; i++) { const b = nextByte(); if (b < 0 || i > 9) return typo; out.push(b); if ((b & 0x80) === 0) break; }
    }
    if (varints === 3 && !nine()) return typo;
    const hi = nextByte(), lo = nextByte();
    if (hi < 0 || lo < 0 || crc16(out) !== ((hi << 8) | lo)) return typo;
    return { kind: 'ok', payload: Uint8Array.from(out) };
  }

  const api = { saveCodeEncode, saveCodeDecode, saveCodeCrc16: crc16 };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else Object.assign(root, api);
})(typeof window !== 'undefined' ? window : globalThis);
