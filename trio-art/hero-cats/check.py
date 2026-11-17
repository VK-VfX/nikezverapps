#!/usr/bin/env python3
"""Fails unless the ten hero cats exist in every app in the right format. Run after export.py."""
import pathlib, sys, xml.dom.minidom
from PIL import Image
HERE = pathlib.Path(__file__).parent
ROOT = HERE.parent.parent
DESIGNS = ["smile", "grin", "smirk", "confident", "angry", "shocked", "sad", "scared", "loaf", "locked"]
bad = []
for n in DESIGNS:
    svg = HERE / f"hero-{n}.svg"
    try:
        xml.dom.minidom.parse(str(svg))
        if 'viewBox="0 0 512 512"' not in svg.read_text(): bad.append(f"{svg.name}: no 512 viewBox")
    except Exception as e:
        bad.append(f"{svg.name}: not valid XML ({e})")
    for p in [ROOT / f"purrcast/app/src/main/res/drawable-nodpi/cat_hero_{n}.png", ROOT / f"zamoled/app/src/main/assets/hero/hero_{n}.png"]:
        if not p.exists(): bad.append(f"missing {p}"); continue
        im = Image.open(p)
        if im.size != (512, 512): bad.append(f"{p.name}: size {im.size}")
        if "A" not in im.convert("RGBA").getbands() or im.convert("RGBA").getpixel((0, 0))[3] != 0: bad.append(f"{p.name}: corner is not transparent")
        if p.stat().st_size > 60 * 1024: bad.append(f"{p.name}: {p.stat().st_size // 1024} KB over 60 KB")
    m = ROOT / f"mapsandcats/assets/hero/hero-{n}.svg"
    if not m.exists() or m.read_bytes() != svg.read_bytes(): bad.append(f"{m.name}: missing or not identical to the source")
print("\n".join(bad) if bad else "OK 10 designs in 3 apps")
sys.exit(1 if bad else 0)
