#!/usr/bin/env python3
"""Fails unless every hero cat exists in every app in the right format. Run after export.py."""
import pathlib, sys, xml.dom.minidom
from PIL import Image
HERE = pathlib.Path(__file__).parent
ROOT = HERE.parent.parent
EMOTIONS = ["smile", "grin", "smirk", "confident", "angry", "shocked", "sad", "scared", "loaf"]
NAMES = [f"{e}-{v}" for e in EMOTIONS for v in range(5)] + ["locked"]
bad = []
for n in NAMES:
    svg = HERE / f"hero-{n}.svg"
    try:
        xml.dom.minidom.parse(str(svg))
        if 'viewBox="0 0 512 512"' not in svg.read_text(): bad.append(f"{svg.name}: no 512 viewBox")
    except Exception as e:
        bad.append(f"{svg.name}: not valid XML ({e})")
    flat = n.replace("-", "_")
    for p in [ROOT / f"purrcast/app/src/main/res/drawable-nodpi/cat_hero_{flat}.png", ROOT / f"zamoled/app/src/main/assets/hero/hero_{flat}.png"]:
        if not p.exists(): bad.append(f"missing {p}"); continue
        im = Image.open(p)
        if im.size != (512, 512): bad.append(f"{p.name}: size {im.size}")
        if im.convert("RGBA").getpixel((0, 0))[3] != 0: bad.append(f"{p.name}: corner is not transparent")
        if p.stat().st_size > 70 * 1024: bad.append(f"{p.name}: {p.stat().st_size // 1024} KB over 70 KB")
    m = ROOT / f"mapsandcats/assets/hero/hero-{n}.svg"
    if not m.exists() or m.read_bytes() != svg.read_bytes(): bad.append(f"{m.name}: missing or not identical to the source")
print("\n".join(bad) if bad else f"OK {len(NAMES)} pictures in 3 apps")
sys.exit(1 if bad else 0)
