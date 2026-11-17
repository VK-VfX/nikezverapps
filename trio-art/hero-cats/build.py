#!/usr/bin/env python3
"""Builds the eight hero-cat SVGs (viewBox 0 0 512 512) from the Noto cat face in mapsandcats/assets/cats.
Every design is the same smiling Noto cat with a superhero mask and one costume idea. Run: python3 build.py"""
import re, pathlib
HERE = pathlib.Path(__file__).parent
CATS = HERE.parent.parent / "mapsandcats" / "assets" / "cats"
BASE = (CATS / "grin-smile-cat.svg").read_text()
INNER = re.search(r"<svg[^>]*>(.*)</svg>", BASE, re.S).group(1)
# the smiling closed eyes are the one path without a style attribute that starts at the left eye
EYES = re.search(r'<path d="(M32\.56 70\.26[^"]+)"', BASE).group(1)

# A domino mask: wings over the cheeks, a dip over the nose bridge, a little flick at each outer corner.
MASK = ("M14 67C14 52 34 47 50 55C58 59 70 59 78 55C94 47 114 52 114 67"
        "C114 84 94 87 82 77C75 72 53 72 46 77C34 87 14 84 14 67Z")

def svg(body):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">'
            f'<g transform="translate(0 4) scale(4)">{body}</g></svg>')

def face(fill="#ffc022", ear="#ff7043"):
    s = INNER.replace("#ffc022", fill).replace("#ff7043", ear)
    return s

def mask(color, edge, gloss="#ffffff"):
    return (f'<path d="{MASK}" fill="{color}" stroke="{edge}" stroke-width="1.6" stroke-linejoin="round"/>'
            f'<path d="M20 63C28 55 40 54 47 58" fill="none" stroke="{gloss}" stroke-opacity=".55" stroke-width="2.2" stroke-linecap="round"/>'
            # the two eye openings, with the smiling eyes drawn back on top so the cat still smiles through the mask
            '<ellipse cx="41" cy="66.5" rx="13.5" ry="9.2" fill="#fff7e0"/><ellipse cx="87" cy="66.5" rx="13.5" ry="9.2" fill="#fff7e0"/>'
            f'<path d="{EYES}" fill="#2b2b33"/>')

def blush(op=".35"):
    return f'<ellipse cx="27" cy="82" rx="8" ry="5" fill="#ff6f91" opacity="{op}"/><ellipse cx="101" cy="82" rx="8" ry="5" fill="#ff6f91" opacity="{op}"/>'

def sparkle(x, y, r, fill="#fff"):
    return (f'<path d="M{x} {y-r}Q{x} {y} {x+r} {y}Q{x} {y} {x} {y+r}Q{x} {y} {x-r} {y}Q{x} {y} {x} {y-r}Z" fill="{fill}"/>')

designs = {}

# classic: red mask, a gold star and sparkles
designs["classic"] = svg(face() + blush() + mask("#e5484d", "#a3201f") +
    '<path d="M64 31l3.2 6.6 7.2 1-5.2 5.1 1.2 7.2L64 47.4l-6.4 3.5 1.2-7.2-5.2-5.1 7.2-1z" fill="#ffd34d" stroke="#e0a800" stroke-width="1.2" stroke-linejoin="round"/>' +
    sparkle(108, 38, 7) + sparkle(20, 40, 5))

# sun: teal mask, round sunglasses pushed up on the forehead
designs["sun"] = svg(face() + blush() + mask("#3df5d0", "#0a8f78") +
    '<g transform="rotate(-6 64 38)"><path d="M54 38h20" stroke="#1f2430" stroke-width="3" stroke-linecap="round"/>'
    '<circle cx="44" cy="39" r="11" fill="#1f2430" stroke="#3df5d0" stroke-width="2.4"/><circle cx="84" cy="39" r="11" fill="#1f2430" stroke="#3df5d0" stroke-width="2.4"/>'
    '<path d="M38 34Q42 31 47 33M78 34Q82 31 87 33" stroke="#fff" stroke-opacity=".75" stroke-width="2.2" fill="none" stroke-linecap="round"/></g>' +
    sparkle(110, 52, 6, "#fff6a8"))

# rain: blue mask, a little umbrella over one ear, three drops
designs["rain"] = svg(face() + blush() + mask("#4fa3ff", "#1f6fd1") +
    '<g transform="rotate(-14 38 24)"><path d="M18 26A20 17 0 0 1 58 26Q53 22 48 26Q43 22 38 26Q33 22 28 26Q23 22 18 26Z" fill="#ffd34d" stroke="#e0a800" stroke-width="1.6" stroke-linejoin="round"/>'
    '<path d="M38 9V26M28 14 30 26M48 14 46 26" stroke="#fff" stroke-opacity=".7" stroke-width="1.6" stroke-linecap="round"/>'
    '<path d="M38 26V42Q38 47 34 47" fill="none" stroke="#7a5a2b" stroke-width="3" stroke-linecap="round"/></g>' +
    '<path d="M100 20c3 5 5 8 5 11a5 5 0 0 1-10 0c0-3 2-6 5-11z" fill="#4fa3ff"/><path d="M114 40c2 3 3 5 3 7a3 3 0 0 1-6 0c0-2 1-4 3-7z" fill="#8cc4ff"/><path d="M88 12c2 3 3 5 3 7a3 3 0 0 1-6 0c0-2 1-4 3-7z" fill="#8cc4ff"/>')

# night: the black cat with a gold mask, moon and a star
designs["night"] = svg(face("#3a3a44", "#8a5a6a") + blush(".22") + mask("#f5c542", "#b88a0a") +
    '<path d="M104 18a13 13 0 1 0 8 24 10 10 0 1 1-8-24z" fill="#ffe9a8"/>' + sparkle(86, 24, 6, "#ffe9a8") + sparkle(22, 34, 5, "#ffe9a8"))

# storm: purple mask, a red cape fanning out under the chin, a lightning bolt
designs["storm"] = svg(
    # the cape: two wings billowing out behind the head, a collar band under the chin and a gold clasp
    '<path d="M30 90C12 88 1 102 4 124C10 113 19 108 31 109Z" fill="#c92f2f"/><path d="M30 90C20 92 12 100 10 112C16 106 22 103 31 103Z" fill="#e5484d"/>'
    '<path d="M98 90C116 88 127 102 124 124C118 113 109 108 97 109Z" fill="#c92f2f"/><path d="M98 90C108 92 116 100 118 112C112 106 106 103 97 103Z" fill="#e5484d"/>' +
    face() + blush() + mask("#b66bff", "#7a2fd0") +
    '<path d="M38 112Q64 126 90 112V120Q64 134 38 120Z" fill="#e5484d" stroke="#a82525" stroke-width="1.2" stroke-linejoin="round"/>'
    '<circle cx="64" cy="122.5" r="5" fill="#ffd34d" stroke="#e0a800" stroke-width="1.4"/><path d="M64 119.5l1.1 2.2 2.4.3-1.8 1.7.5 2.4-2.2-1.2-2.2 1.2.5-2.4-1.8-1.7 2.4-.3z" fill="#fff6a8"/>'
    '<path d="M69 20 54 42h9l-4 14 17-24h-9z" fill="#ffd34d" stroke="#e0a800" stroke-width="1.2" stroke-linejoin="round"/>')

# hot: orange mask, sweat drops, heat waves
designs["hot"] = svg(face() + blush(".5") + mask("#ff9f43", "#d4741a") +
    '<path d="M22 38c3 5 5 8 5 11a5 5 0 0 1-10 0c0-3 2-6 5-11z" fill="#7fd3ff" stroke="#3fa6e0" stroke-width="1.2"/>'
    '<path d="M108 30c2 4 4 6 4 9a4 4 0 0 1-8 0c0-3 2-5 4-9z" fill="#7fd3ff" stroke="#3fa6e0" stroke-width="1.2"/>'
    '<path d="M46 14c-4-4 4-6 0-10M64 12c-4-4 4-6 0-10M82 14c-4-4 4-6 0-10" fill="none" stroke="#fff" stroke-opacity=".8" stroke-width="2.2" stroke-linecap="round"/>')

# sleepy: the mask pushed up on the forehead, a small z
designs["sleepy"] = svg(face() + blush() +
    f'<g transform="translate(0 -33) rotate(-4 64 62)"><path d="{MASK}" fill="#9a8cff" stroke="#6a5ad8" stroke-width="1.6" stroke-linejoin="round"/>'
    '<ellipse cx="41" cy="66.5" rx="9" ry="5" fill="#2f2a66" opacity=".55"/><ellipse cx="87" cy="66.5" rx="9" ry="5" fill="#2f2a66" opacity=".55"/></g>' +
    '<text x="96" y="30" font-family="Arial Rounded MT Bold, Arial, sans-serif" font-weight="800" font-size="20" fill="#cfd6ff">z</text>'
    '<text x="108" y="18" font-family="Arial Rounded MT Bold, Arial, sans-serif" font-weight="800" font-size="14" fill="#cfd6ff">z</text>')

# locked: only the outline of the hero, with a question mark
locked = INNER
locked = locked.replace('style="fill:#ffc022"', 'style="fill:#101014;stroke:#3df5d0;stroke-opacity:.85;stroke-width:2.4;stroke-linejoin:round"')
designs["locked"] = svg(
    re.sub(r'<path d="M54\.12[^>]*/>', "", locked.replace("#ff7043", "#101014"))[:0] +
    f'<g>{INNER.split("<path d=\"M54.12")[0].replace("style=\"fill:#ffc022\"", "style=\"fill:#101014;stroke:#3df5d0;stroke-opacity:.85;stroke-width:2.4;stroke-linejoin:round\"")}</g>'
    f'<path d="{MASK}" fill="#3df5d0" fill-opacity=".14" stroke="#3df5d0" stroke-opacity=".6" stroke-width="1.6" stroke-dasharray="3 3"/>'
    '<text x="64" y="76" text-anchor="middle" font-family="Arial Rounded MT Bold, Arial, sans-serif" font-weight="800" font-size="30" fill="#3df5d0" fill-opacity=".9">?</text>')

for name, body in designs.items():
    (HERE / f"hero-{name}.svg").write_text(body)
print("wrote", len(designs), "svgs")
