#!/usr/bin/env python3
"""Exports the hero cats: renders PNGs with Chromium (node render.js, 768 px, shrunk to 512) and copies everything where each app expects it.
There are nine emotions in five mask looks each (hero-<emotion>-<0..4>) plus one locked silhouette.
  Purrcast: purrcast/app/src/main/res/drawable-nodpi/cat_hero_<emotion>_<look>.png   (Purrcast loads "cat_<name>")
  Zamoled:  zamoled/app/src/main/assets/hero/hero_<emotion>_<look>.png
  Maps and Cats: mapsandcats/assets/hero/hero-<emotion>-<look>.svg
  Zamoled supercat wallpaper: zamoled/app/src/main/assets/hero/portrait/hero_<emotion>_<look>.png (768 px, for the big portrait)
Run: python3 export.py   (needs node + playwright)"""
import pathlib, re, shutil, subprocess, tempfile
from PIL import Image
HERE = pathlib.Path(__file__).parent
ROOT = HERE.parent.parent
EMOTIONS = ["smile", "grin", "smirk", "confident", "angry", "shocked", "sad", "scared", "loaf"]
LOOKS = 5
NAMES = [f"{e}-{v}" for e in EMOTIONS for v in range(LOOKS)] + ["locked"]
PUR = ROOT / "purrcast/app/src/main/res/drawable-nodpi"
ZAM = ROOT / "zamoled/app/src/main/assets/hero"
MAC = ROOT / "mapsandcats/assets/hero"
subprocess.run(["python3", str(HERE / "build.py")], check=True)
tmp = pathlib.Path(tempfile.mkdtemp())
subprocess.run(["node", str(HERE / "render.js"), str(tmp)], check=True)
POR = ZAM / "portrait"
for d in (PUR, ZAM, MAC, POR):
    d.mkdir(parents=True, exist_ok=True)
# drop what the previous export left behind
for d, pat in ((PUR, "cat_hero_*.png"), (ZAM, "hero_*.png"), (POR, "hero_*.png"), (MAC, "hero-*.svg")):
    for f in d.glob(pat): f.unlink()
for n in NAMES:
    im = Image.open(tmp / f"hero-{n}.png").convert("RGBA").resize((512, 512), Image.LANCZOS)
    q = im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    flat = n.replace("-", "_")
    q.save(PUR / f"cat_hero_{flat}.png", optimize=True)
    q.save(ZAM / f"hero_{flat}.png", optimize=True)
    shutil.copy(HERE / f"hero-{n}.svg", MAC / f"hero-{n}.svg")
    if n != "locked":
        big = Image.open(tmp / f"hero-{n}.png").convert("RGBA")
        big.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(POR / f"hero_{flat}.png", optimize=True)
shutil.copy(HERE / "NOTICE.txt", MAC / "NOTICE.txt")
shutil.copy(HERE / "NOTICE.txt", ZAM / "NOTICE.txt")
# a contact sheet on pure black: every emotion in every look
W = 160
sheet = Image.new("RGB", (LOOKS * W, len(EMOTIONS) * W), (0, 0, 0))
for r, e in enumerate(EMOTIONS):
    for v in range(LOOKS):
        im = Image.open(tmp / f"hero-{e}-{v}.png").convert("RGBA").resize((W - 8, W - 8), Image.LANCZOS)
        sheet.paste(im, (v * W + 4, r * W + 4), im)
sheet.save(HERE / "contact-sheet.png")
print("exported", len(NAMES), "pictures")
