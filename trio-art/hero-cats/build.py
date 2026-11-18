#!/usr/bin/env python3
"""Builds the hero-cat SVGs (viewBox 0 0 512 512): one superhero cat, nine emotions, plus a locked silhouette.
The head, ears, whiskers and nose are the Noto cat from mapsandcats/assets/cats; the mask, cape and the faces are drawn on top
in the same style. The hero is always the same character (red mask, red cape, gold clasp); only the face and a few extras change.
Run: python3 build.py"""
import re, pathlib
HERE = pathlib.Path(__file__).parent
CATS = HERE.parent.parent / "mapsandcats" / "assets" / "cats"

def elements(name):
    s = (CATS / name).read_text()
    return re.findall(r"<(?:path|ellipse|circle)[^>]*?/>", s)

GRIN = elements("grin-cat.svg")
GRIN_SMILE = elements("grin-smile-cat.svg")
SMIRK = elements("smirk-cat.svg")
# head parts (same in every Noto cat face): ears and head, stripes, whiskers, inner ears, nose
HEAD = "".join([GRIN[0], GRIN[1], GRIN[2], GRIN[4], GRIN[10]])
NOSE = GRIN[5]
TEETH = "".join(GRIN_SMILE[5:8])               # the big toothy grin from the smiling cat
SMIRK_MOUTH = SMIRK[4] + SMIRK[5]              # the one-sided smirk and its tongue flick

GOLD = "#ffd34d"
INK = "#2b2b33"

# Five hero looks: each pairs a mask shape with its own colour, and the cape and collar follow the colour.
THEMES = [
    dict(shape="domino",  c="#e5484d", dark="#a3201f", light="#ff8a8e", cape="#c92f2f"),   # red domino
    dict(shape="wing",    c="#3a86ff", dark="#1d4fb8", light="#8fbaff", cape="#2b63d6"),   # blue wing (bat)
    dict(shape="visor",   c="#9b5de5", dark="#6330ad", light="#c9a0f5", cape="#7c3fc4"),   # purple visor
    dict(shape="goggles", c="#12b886", dark="#0a7a58", light="#6fe3bf", cape="#0e9a70"),   # green goggles
    dict(shape="cateye",  c="#ec407a", dark="#a81b4f", light="#ff9fbf", cape="#c2185b"),   # rose cat-eye
]
T = THEMES[0]
SHAPES = {
    "domino": "M14 67C14 52 34 47 50 55C58 59 70 59 78 55C94 47 114 52 114 67C114 84 94 87 82 77C75 72 53 72 46 77C34 87 14 84 14 67Z",
    "wing": "M5 52L24 55C32 47 44 48 52 55C59 60 69 60 76 55C84 48 96 47 104 55L123 52C124 70 117 90 98 86C90 84 84 78 76 76C68 73 60 73 52 76C44 78 38 84 30 86C11 90 4 70 5 52Z",
    "visor": "M7 60C28 46 100 46 121 60L121 76C100 91 28 91 7 76Z",
    "cateye": "M7 73C9 58 26 50 46 55C56 58 72 58 82 55C102 50 119 58 121 73C113 68 107 68 102 71C106 85 92 89 80 80C72 75 56 75 48 80C36 89 22 85 26 71C21 68 15 68 7 73Z",
}

def mix(a, b, t):
    pa = [int(a[i:i+2], 16) for i in (1, 3, 5)]
    pb = [int(b[i:i+2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02x}" for x, y in zip(pa, pb))

def defs():
    c, d, l, cp = T["c"], T["dark"], T["light"], T["cape"]
    return ('<defs>'
            f'<linearGradient id="mg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{l}"/><stop offset=".42" stop-color="{c}"/><stop offset="1" stop-color="{d}"/></linearGradient>'
            f'<linearGradient id="cg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{c}"/><stop offset="1" stop-color="{cp}"/></linearGradient>'
            '<linearGradient id="gold" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff0a0"/><stop offset=".5" stop-color="#ffd34d"/><stop offset="1" stop-color="#e0a000"/></linearGradient>'
            '<radialGradient id="fur" cx=".5" cy=".28" r=".6"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>'
            '<radialGradient id="chin" cx=".5" cy=".98" r=".55"><stop offset="0" stop-color="#b85a00" stop-opacity=".2"/><stop offset="1" stop-color="#b85a00" stop-opacity="0"/></radialGradient>'
            '</defs>')

def svg(body):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">' + defs() +
            f'<g transform="translate(0 4) scale(4)">{body}</g></svg>')

def cape_back():
    cp, c = T["cape"], T["c"]
    dk = mix(cp, "#000000", .18)
    return (f'<path d="M30 90C12 88 1 102 4 124C10 113 19 108 31 109Z" fill="{dk}"/><path d="M30 90C20 92 12 100 10 112C16 106 22 103 31 103Z" fill="url(#cg)"/>'
            f'<path d="M98 90C116 88 127 102 124 124C118 113 109 108 97 109Z" fill="{dk}"/><path d="M98 90C108 92 116 100 118 112C112 106 106 103 97 103Z" fill="url(#cg)"/>'
            '<path d="M13 106C15 100 19 96 24 94M115 106C113 100 109 96 104 94" fill="none" stroke="#fff" stroke-opacity=".28" stroke-width="1.6" stroke-linecap="round"/>')

def collar():
    c, d = T["c"], T["dark"]
    return (f'<path d="M38 112Q64 126 90 112V120Q64 134 38 120Z" fill="url(#cg)" stroke="{d}" stroke-width="1.2" stroke-linejoin="round"/>'
            '<path d="M42 114.5Q64 127 86 114.5" fill="none" stroke="#fff" stroke-opacity=".35" stroke-width="1.3" stroke-linecap="round"/>'
            '<circle cx="64" cy="122.5" r="5.2" fill="url(#gold)" stroke="#c98f00" stroke-width="1.3"/>'
            '<path d="M64 119.5l1.1 2.2 2.4.3-1.8 1.7.5 2.4-2.2-1.2-2.2 1.2.5-2.4-1.8-1.7 2.4-.3z" fill="#fff6a8"/>')

def mask(hole=1.0, hole_fill="#fff7e0"):
    c, d, l = T["c"], T["dark"], T["light"]
    shape = T["shape"]
    shadow = ""
    if shape == "goggles":
        strap = mix(d, "#000000", .35)
        return (f'<path d="M3 60H125V74H3Z" fill="{strap}"/><path d="M3 62H125" stroke="#fff" stroke-opacity=".18" stroke-width="1.2"/>'
                f'<rect x="54" y="63" width="20" height="7" rx="3" fill="{d}"/>'
                f'<circle cx="41" cy="66.5" r="17.5" fill="url(#mg)" stroke="{d}" stroke-width="1.8"/><circle cx="87" cy="66.5" r="17.5" fill="url(#mg)" stroke="{d}" stroke-width="1.8"/>'
                f'<ellipse cx="41" cy="66.5" rx="{13.8*hole}" ry="{12.4*min(hole,1.05)}" fill="{hole_fill}"/><ellipse cx="87" cy="66.5" rx="{13.8*hole}" ry="{12.4*min(hole,1.05)}" fill="{hole_fill}"/>'
                '<path d="M28 59Q34 53 43 52M74 59Q80 53 89 52" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="2" stroke-linecap="round"/>')
    path = SHAPES[shape]
    return (f'<path d="{path}" transform="translate(0 2.2)" fill="#5a2a00" opacity=".16"/>'
            f'<path d="{path}" fill="url(#mg)" stroke="{d}" stroke-width="1.6" stroke-linejoin="round"/>'
            '<path d="M20 62C28 54 40 53 47 57M81 57C88 53 100 54 108 62" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="2.2" stroke-linecap="round"/>'
            f'<ellipse cx="41" cy="66.5" rx="{13.5*hole}" ry="{9.2*hole}" fill="{hole_fill}"/><ellipse cx="87" cy="66.5" rx="{13.5*hole}" ry="{9.2*hole}" fill="{hole_fill}"/>')

def blush(op=".35"):
    return f'<ellipse cx="27" cy="82" rx="8" ry="5" fill="#ff6f91" opacity="{op}"/><ellipse cx="101" cy="82" rx="8" ry="5" fill="#ff6f91" opacity="{op}"/>'

def sparkle(x, y, r, fill="#fff"):
    return f'<path d="M{x} {y-r}Q{x} {y} {x+r} {y}Q{x} {y} {x} {y+r}Q{x} {y} {x-r} {y}Q{x} {y} {x} {y-r}Z" fill="{fill}"/>'

def round_eye(cx, cy, r=5.6, shine=True, ry=None):
    ry = ry or r * 1.15
    s = f'<ellipse cx="{cx}" cy="{cy}" rx="{r}" ry="{ry}" fill="#1c1c22"/>'
    return s + (f'<circle cx="{cx-r*.35}" cy="{cy-ry*.4}" r="{max(1.4, r*.34)}" fill="#fff"/>' if shine else "")

def stroke(d, w=4, color=INK, extra=""):
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" {extra}/>'

def drop(x, y, s=1.0, fill="#7fd3ff", edge="#3fa6e0"):
    return (f'<path d="M{x} {y-6*s}c{2.6*s} {4.2*s} {4.4*s} {6.6*s} {4.4*s} {9*s}a{4.4*s} {4.4*s} 0 0 1-{8.8*s} 0c0-{2.4*s} {1.8*s}-{4.8*s} {4.4*s}-{9*s}z" fill="{fill}" stroke="{edge}" stroke-width="1.1"/>')

def head_base():
    # the cape behind, the Noto cat head, a soft fur highlight on the forehead and shade under the chin, then blush
    return (cape_back() + HEAD + '<ellipse cx="64" cy="44" rx="30" ry="14" fill="url(#fur)"/><ellipse cx="64" cy="100" rx="40" ry="22" fill="url(#chin)"/>' + blush())

EMOTIONS = ["smile", "grin", "smirk", "confident", "angry", "shocked", "sad", "scared", "loaf"]

def designs():
    D = {}

    # smile: closed happy eyes, a gentle smile, a gold star on the mask
    D["smile"] = svg(head_base() + mask() +
        stroke("M32 69Q41 57 50 69", 4.2) + stroke("M78 69Q87 57 96 69", 4.2) + NOSE +
        stroke("M46 88Q55 97 64 89Q73 97 82 88", 3.8) + collar() +
        f'<path d="M64 31l3.2 6.6 7.2 1-5.2 5.1 1.2 7.2L64 47.4l-6.4 3.5 1.2-7.2-5.2-5.1 7.2-1z" fill="url(#gold)" stroke="#e0a800" stroke-width="1.2" stroke-linejoin="round"/>' +
        sparkle(108, 38, 7) + sparkle(20, 40, 5))

    # grin: round bright eyes and the big toothy grin
    D["grin"] = svg(head_base() + mask() + round_eye(41, 67) + round_eye(87, 67) + NOSE + TEETH + collar() +
        sparkle(108, 36, 7) + sparkle(18, 44, 5) + sparkle(112, 62, 4))

    # smirk: half-closed eyes, a sideways smirk, one raised brow
    D["smirk"] = svg(head_base() + mask() +
        '<circle cx="42.5" cy="68.5" r="5.2" fill="#1c1c22"/><circle cx="88.5" cy="68.5" r="5.2" fill="#1c1c22"/>'
        f'<path d="M27.5 66.5A13.5 9.2 0 0 1 54.5 66.5Z" fill="{T['c']}"/><path d="M73.5 66.5A13.5 9.2 0 0 1 100.5 66.5Z" fill="{T['c']}"/>' +
        stroke("M28 66.5H55", 3) + stroke("M73 66.5H100", 3) + stroke("M28 52Q40 46 53 52", 3.2) + NOSE + stroke("M46 91Q58 97 70 91Q80 88 86 80", 4.2) + stroke("M86 80q3 0 4-3", 2.6) + collar() + sparkle(110, 56, 6))

    # confident: dark shades over the mask, a proud smile, a gold star
    D["confident"] = svg(head_base() + mask() +
        '<rect x="25" y="57" width="32" height="19" rx="9" fill="#15151b"/><rect x="71" y="57" width="32" height="19" rx="9" fill="#15151b"/>'
        '<path d="M57 63H71" stroke="#15151b" stroke-width="3.2" stroke-linecap="round"/>'
        '<path d="M31 62L38 71M36 60L46 72M77 62L84 71M82 60L92 72" stroke="#fff" stroke-opacity=".6" stroke-width="2" stroke-linecap="round"/>' +
        NOSE + stroke("M44 88Q64 105 84 88", 4.2) + stroke("M84 88q4-1 6-5", 2.6) + collar() +
        f'<path d="M64 24l3.2 6.6 7.2 1-5.2 5.1 1.2 7.2L64 40.4l-6.4 3.5 1.2-7.2-5.2-5.1 7.2-1z" fill="url(#gold)" stroke="#e0a800" stroke-width="1.2" stroke-linejoin="round"/>' + sparkle(108, 40, 6))

    # angry: slanted brows, a scowl with two small fangs, steam and an anger mark
    D["angry"] = svg(head_base() + mask() +
        round_eye(46, 69.5, 4.6, False) + round_eye(82, 69.5, 4.6, False) +
        f'<path d="M27 56L55 56L55 67Z" fill="{T['c']}"/><path d="M101 56L73 56L73 67Z" fill="{T['c']}"/>' +
        stroke("M25 54L56 65", 4.4) + stroke("M103 54L72 65", 4.4) + NOSE +
        stroke("M46 100Q64 84 82 100", 4.2) + '<path d="M54.5 93.5l3 6 3-4.6z M73.5 93.5l-3 6-3-4.6z" fill="#fff" stroke="#2b2b33" stroke-width="1.1" stroke-linejoin="round"/>' + collar() +
        '<g fill="none" stroke="#ff3b3b" stroke-width="3.4" stroke-linecap="round"><path d="M92 17q6 0 6 6M108 17q-6 0-6 6M92 33q6 0 6-6M108 33q-6 0-6-6"/></g>')

    # shocked: huge eyes with tiny pupils, an open mouth, bristling lines
    D["shocked"] = svg(head_base() + mask(1.12) +
        '<circle cx="41" cy="66.5" r="2.9" fill="#1c1c22"/><circle cx="87" cy="66.5" r="2.9" fill="#1c1c22"/>' + NOSE +
        '<ellipse cx="64" cy="96" rx="8.5" ry="11" fill="#3a1f24"/><ellipse cx="64" cy="102" rx="5.2" ry="4.2" fill="#ef7f9d"/>' + collar() +
        stroke("M18 30L8 22M112 30L122 22M64 20V8M30 20L24 8M98 20L104 8", 3.4, "#ffd34d") +
        f'<path d="M69 22 54 42h9l-4 14 17-24h-9z" fill="url(#gold)" stroke="#e0a800" stroke-width="1.2" stroke-linejoin="round" transform="translate(0 6) scale(.85) translate(10 -2)"/>')

    # sad: shiny worried eyes, tilted brows, a small frown and tears
    D["sad"] = svg(head_base() + mask() +
        round_eye(41, 68, 6.4, True, 7) + round_eye(87, 68, 6.4, True, 7) +
        '<circle cx="45" cy="71.5" r="1.7" fill="#fff" opacity=".8"/><circle cx="91" cy="71.5" r="1.7" fill="#fff" opacity=".8"/>' +
        stroke("M27 58L52 51", 4) + stroke("M101 58L76 51", 4) + NOSE + stroke("M52 99Q64 90 76 99", 3.8) + collar() +
        '<path d="M32 76Q28 90 31 100M96 76Q100 90 97 100" fill="none" stroke="#7fd3ff" stroke-width="5" stroke-linecap="round" opacity=".85"/>' +
        drop(31, 107, .9) + drop(97, 107, .9) + drop(48, 14, .8))

    # scared: tiny pupils in wide eyes, a wobbly mouth, sweat drops and shiver lines
    D["scared"] = svg(head_base() + mask(1.1) +
        '<circle cx="41" cy="67" r="2.3" fill="#1c1c22"/><circle cx="87" cy="67" r="2.3" fill="#1c1c22"/>' +
        stroke("M27 56L52 51", 3.6) + stroke("M101 56L76 51", 3.6) + NOSE +
        stroke("M46 96l6 -5 6 5 6 -5 6 5 6 -5 6 5", 3.4) + collar() +
        drop(22, 42, 1) + drop(106, 36, .9) + drop(100, 54, .7) +
        stroke("M8 70l-5 3 5 3-5 3M120 70l5 3-5 3 5 3", 3, "#8cc4ff"))

    # loaf: the hero as a bread loaf, paws tucked, the cape hanging down both sides and a collar under the chin
    LOAF_HEAD = f'<g transform="translate(17 -2) scale(.74)">{HEAD}{blush()}{mask()}' + stroke("M32 69Q41 57 50 69", 4.6) + stroke("M78 69Q87 57 96 69", 4.6) + NOSE + stroke("M50 89Q57 96 64 90Q71 96 78 89", 4.2) + '</g>'
    D["loaf"] = svg(
        '<path d="M112 114Q128 110 124 90" fill="none" stroke="#ffc022" stroke-width="7" stroke-linecap="round"/><path d="M124 90q-1-5-4-6" fill="none" stroke="#ff9b31" stroke-width="7" stroke-linecap="round"/>'
        '<path d="M8 104C8 80 32 64 64 64C96 64 120 80 120 104L120 112C120 120 112 124 102 124H26C16 124 8 120 8 112Z" fill="#ffc022"/>'
        '<path d="M28 74Q34 84 32 94M100 74Q94 84 96 94M64 66V78" fill="none" stroke="#ff9b31" stroke-opacity=".6" stroke-width="3.4" stroke-linecap="round"/>' +
        LOAF_HEAD +
        f'<path d="M12 94Q30 92 38 108L30 124H20C10 118 8 106 12 94Z" fill="url(#cg)"/><path d="M12 94Q24 94 30 104Q26 108 24 118Q14 112 12 94Z" fill="{mix(T['cape'], '#000000', .15)}"/>'
        f'<path d="M116 94Q98 92 90 108L98 124H108C118 118 120 106 116 94Z" fill="url(#cg)"/><path d="M116 94Q104 94 98 104Q102 108 104 118Q114 112 116 94Z" fill="{mix(T['cape'], '#000000', .15)}"/>'
        f'<path d="M44 82Q64 96 84 82V90Q64 104 44 90Z" fill="url(#cg)" stroke="{T['dark']}" stroke-width="1.2" stroke-linejoin="round"/>'
        f'<circle cx="64" cy="94" r="4.6" fill="url(#gold)" stroke="#e0a800" stroke-width="1.3"/>'
        '<ellipse cx="50" cy="119" rx="10" ry="5" fill="#ffc022" stroke="#ff9b31" stroke-width="1.2"/><ellipse cx="78" cy="119" rx="10" ry="5" fill="#ffc022" stroke="#ff9b31" stroke-width="1.2"/>' + sparkle(112, 46, 5))

    return D

# locked: only the outline of the hero, with a question mark
_hl = (HEAD.replace('style="fill:#ffc022"', 'style="fill:#101014;stroke:#3df5d0;stroke-opacity:.85;stroke-width:2.4;stroke-linejoin:round"')
               .replace("#ff7043", "#101014").replace("#ffd1d1", "#101014"))
LOCKED = svg(_hl +
    f'<path d="{SHAPES["domino"]}" fill="#3df5d0" fill-opacity=".14" stroke="#3df5d0" stroke-opacity=".6" stroke-width="1.6" stroke-dasharray="3 3"/>'
    '<text x="64" y="76" text-anchor="middle" font-family="Arial Rounded MT Bold, Arial, sans-serif" font-weight="800" font-size="30" fill="#3df5d0" fill-opacity=".9">?</text>')


for old in HERE.glob("hero-*.svg"):
    old.unlink()
for v, theme in enumerate(THEMES):
    T = theme
    for name, body in designs().items():
        (HERE / f"hero-{name}-{v}.svg").write_text(body)
(HERE / "hero-locked.svg").write_text(LOCKED)
print("wrote", len(THEMES) * len(EMOTIONS) + 1, "svgs")
