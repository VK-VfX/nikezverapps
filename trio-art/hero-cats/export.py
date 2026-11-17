#!/usr/bin/env python3
"""Exports the hero cats: renders PNGs with Chromium (node render.js) and copies everything where each app expects it.
  Purrcast: purrcast/app/src/main/res/drawable-nodpi/cat_hero_<design>.png   (Purrcast loads "cat_<name>")
  Zamoled:  zamoled/app/src/main/assets/hero/hero_<design>.png
  Maps and Cats: mapsandcats/assets/hero/hero-<design>.svg
Run: python3 export.py   (needs node + playwright)"""
import pathlib, shutil, subprocess, tempfile
from PIL import Image
HERE = pathlib.Path(__file__).parent
ROOT = HERE.parent.parent
DESIGNS = ["smile", "grin", "smirk", "confident", "angry", "shocked", "sad", "scared", "loaf", "locked"]
subprocess.run(["python3", str(HERE / "build.py")], check=True)
tmp = pathlib.Path(tempfile.mkdtemp())
subprocess.run(["node", str(HERE / "render.js"), str(tmp)], check=True)
for d in ["purrcast/app/src/main/res/drawable-nodpi", "zamoled/app/src/main/assets/hero", "mapsandcats/assets/hero"]:
    (ROOT / d).mkdir(parents=True, exist_ok=True)
for n in DESIGNS:
    png = tmp / f"hero-{n}.png"
    im = Image.open(png).convert("RGBA")
    # a 256-colour palette keeps each cat well under 60 KB without visible change
    q = im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    q = Image.alpha_composite(Image.new("RGBA", im.size, (0, 0, 0, 0)), im).quantize(colors=256, method=Image.Quantize.FASTOCTREE)
    for dest in [ROOT / f"purrcast/app/src/main/res/drawable-nodpi/cat_hero_{n}.png", ROOT / f"zamoled/app/src/main/assets/hero/hero_{n}.png"]:
        q.save(dest, optimize=True)
    shutil.copy(HERE / f"hero-{n}.svg", ROOT / f"mapsandcats/assets/hero/hero-{n}.svg")
shutil.copy(HERE / "NOTICE.txt", ROOT / "mapsandcats/assets/hero/NOTICE.txt")
shutil.copy(HERE / "NOTICE.txt", ROOT / "zamoled/app/src/main/assets/hero/NOTICE.txt")
# a contact sheet on pure black, for a quick look
W = 256
sheet = Image.new("RGB", (4 * W, 2 * W), (0, 0, 0))
for i, n in enumerate(DESIGNS):
    im = Image.open(tmp / f"hero-{n}.png").convert("RGBA").resize((W - 16, W - 16), Image.LANCZOS)
    sheet.paste(im, ((i % 4) * W + 8, (i // 4) * W + 8), im)
sheet.save(HERE / "contact-sheet.png")
print("exported", len(DESIGNS), "designs")
