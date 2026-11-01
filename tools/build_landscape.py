"""Build 1920x1080 landscape variants from the portrait group catalog.

Reads app/src/main/assets/groups/groups.json (written by build_groups.py),
re-reads each ranked source from RASTER_SRC without rotating it, and writes
app/src/main/assets/groups_land/<id>/1..N.webp.
"""
import json
import os
import shutil

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.environ.get("RASTER_SRC", os.path.join(ROOT, "art"))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
CATALOG = os.path.join(ASSETS, "groups", "groups.json")
OUT = os.path.join(ASSETS, "groups_land")
W, H = 1920, 1080


def fit(im):
    im = im.convert("RGB")
    w, h = im.size
    s = max(W / w, H / h)
    im = im.resize((int(round(w * s)), int(round(h * s))), Image.LANCZOS)
    left = (im.width - W) // 2
    top = (im.height - H) // 2
    return im.crop((left, top, left + W, top + H))


def main():
    with open(CATALOG, encoding="utf-8") as f:
        catalog = json.load(f)

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)

    total = 0
    for group in catalog["groups"]:
        gdir = os.path.join(OUT, group["id"])
        os.makedirs(gdir)
        for idx, fn in enumerate(group["sources"], start=1):
            path = os.path.join(SRC, fn)
            if not os.path.isfile(path):
                raise SystemExit("missing: " + path)
            im = fit(Image.open(path))
            out = os.path.join(gdir, "%d.webp" % idx)
            im.save(out, "WEBP", quality=90, method=6)
            n = os.path.getsize(out)
            total += n
            print("  %s/%d.webp <- %-26s %5.1f KB" % (group["id"], idx, fn, n / 1024.0))

    print("\nTOTAL %d groups, %.1f MB" % (len(catalog["groups"]), total / 1024.0 / 1024.0))


if __name__ == "__main__":
    main()
