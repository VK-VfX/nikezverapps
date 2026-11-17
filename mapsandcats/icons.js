/* Trio icon set: one hand-drawn set, 24px grid, 2px round stroke, currentColor.
   Replaces every non-cat emoji in the UI (the only emoji in the products are the Noto cats). */
(function () {
  var P = {
    all: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.6"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.6"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.6"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.6"/>',
    food: '<path d="M6 3v6a3 3 0 0 0 6 0V3M9 3v18M17 21V3c-2.2 1.8-3 4.5-3 8h3"/>',
    coffee: '<path d="M5 8h11v6a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5V8zM16 10h2a2.5 2.5 0 0 1 0 5h-2M8 3v2M12 3v2"/>',
    park: '<path d="M12 3 6.5 11H9l-3.5 5h13L15 11h2.5zM12 16v5"/>',
    sight: '<path d="M4 8h3l1.5-2.5h7L17 8h3v11H4V8z"/><circle cx="12" cy="13.5" r="3.5"/>',
    shop: '<path d="M5 8h14l-1 12H6L5 8zM9 8V6.5a3 3 0 0 1 6 0V8"/>',
    fuel: '<path d="M5 20V5a1.5 1.5 0 0 1 1.5-1.5h6A1.5 1.5 0 0 1 14 5v15M4 20h11M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V9l-3-3M7.5 7h4v3h-4z"/>',
    rest: '<path d="M4 11h16v4H4zM6 15v4M18 15v4M5 11V8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v3"/>',
    home: '<path d="M4 11.5 12 5l8 6.5V20H4zM10 20v-5h4v5"/>',
    work: '<rect x="3.5" y="7.5" width="17" height="12" rx="2"/><path d="M9 7.5V6a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 6v1.5M3.5 13h17"/>',
    star: '<path d="M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.5 2.5 3.5 5.3 3.5 8.5s-1 6-3.5 8.5c-2.5-2.5-3.5-5.3-3.5-8.5s1-6 3.5-8.5z"/>',
    pin: '<path d="M12 21s6.5-5.6 6.5-11a6.5 6.5 0 0 0-13 0c0 5.4 6.5 11 6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
    car: '<path d="M5 16.5V12l2-5h10l2 5v4.5M5 16.5h14M5 16.5V19M19 16.5V19M5 12h14"/><circle cx="8" cy="14.2" r=".6"/><circle cx="16" cy="14.2" r=".6"/>',
    walk: '<circle cx="13" cy="4.5" r="1.8"/><path d="M12 8.5l-3 3.5 2 2-1.5 6M12 8.5l3 3 3 .5M11 14l3 2 .5 4.5"/>',
    road: '<path d="M8 3 5 21M16 3l3 18M12 4v3M12 10v4M12 17v3"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    shuffle: '<path d="M4 7h3.5c4 0 5 10 9 10H20M4 17h3.5c1.4 0 2.3-1.2 3.2-2.7M20 7h-3.5c-1.4 0-2.3 1.2-3.2 2.7M17.5 4.5 20 7l-2.5 2.5M17.5 14.5 20 17l-2.5 2.5"/>',
    moon: '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4"/>',
    speaker: '<path d="M4 9.5h3.5L12 6v12l-4.5-3.5H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a8 8 0 0 1 0 11"/>',
    mute: '<path d="M4 9.5h3.5L12 6v12l-4.5-3.5H4z"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>',
    map: '<path d="M3.5 6.5 9 4.5l6 2 5.5-2v13l-5.5 2-6-2-5.5 2zM9 4.5v13M15 6.5v13"/>',
    rain: '<path d="M7 15a4 4 0 0 1 .5-8A5.5 5.5 0 0 1 18 8.5a3.3 3.3 0 0 1-.5 6.5H7zM9 18l-1 2.5M13 18l-1 2.5M17 18l-1 2.5"/>',
    cloud: '<path d="M7 18a4 4 0 0 1 .5-8A5.5 5.5 0 0 1 18 11.5a3.3 3.3 0 0 1-.5 6.5H7z"/>',
    flag: '<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>'
  };
  /* ic('star') outline, ic('star-fill') filled. Extra class optional. */
  window.ic = function (name, cls) {
    var fill = /-fill$/.test(name), n = name.replace(/-fill$/, ''), d = P[n] || P.pin;
    return '<svg class="ic' + (cls ? ' ' + cls : '') + '" viewBox="0 0 24 24" aria-hidden="true" fill="' + (fill ? 'currentColor' : 'none') +
      '" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + d + '</svg>';
  };
  /* static markup: <i data-i="fuel"></i> becomes the icon */
  window.hydrateIcons = function (root) {
    (root || document).querySelectorAll('i[data-i]').forEach(function (el) {
      el.outerHTML = window.ic(el.getAttribute('data-i'));
    });
  };
  window.hydrateIcons();
})();
