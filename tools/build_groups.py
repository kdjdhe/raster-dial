"""Build all lenticular groups from RASTER_SRC (default: <repo>/art).

For each character: rank images by colorfulness (1 = most colorful),
rotate 90 deg clockwise, fit 1080x2400, export WebP, and write groups.json.
"""
import json
import os
import shutil
from PIL import Image
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.environ.get("RASTER_SRC", os.path.join(ROOT, "art"))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "groups")
W, H = 1080, 2400

GROUPS = [
    ("Yixuan", "Yixuan", ["Yixuan1.jpg", "Yixuan2.jpg", "Yixuan3.jpg"]),
    ("Pyrois", "Pyrois", ["Pyrois1.png", "Pyrois2.png"]),
    ("Miyabi", "Miyabi", ["Miyabi1.png", "Miyabi2.png", "Miyabi3.png"]),
    ("Vivian", "Vivian", ["Vivian1.png", "Vivian2.png", "Vivian3.png"]),
    ("YeShunguang", "Ye Shunguang", ["Ye Shunguang1.png", "Ye Shunguang2.png", "Ye Shunguang3.png"]),
]

NAME_ZH = {
    "Yixuan": "仪玄",
    "Pyrois": "佩洛伊斯",
    "Miyabi": "星见雅",
    "Vivian": "薇薇安",
    "YeShunguang": "叶瞬光",
}


def colorfulness(path):
    im = Image.open(path).convert("RGB")
    im.thumbnail((320, 320), Image.LANCZOS)
    a = np.asarray(im, dtype=np.float32)
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
    rg = r - g
    yb = 0.5 * (r + g) - b
    std = float(np.sqrt(rg.std() ** 2 + yb.std() ** 2))
    mean = float(np.sqrt(rg.mean() ** 2 + yb.mean() ** 2))
    return std + 0.3 * mean


def fit(im):
    im = im.convert("RGB").transpose(Image.ROTATE_270)  # 90 deg clockwise
    w, h = im.size
    s = max(W / w, H / h)
    im = im.resize((int(round(w * s)), int(round(h * s))), Image.LANCZOS)
    left = (im.width - W) // 2
    top = (im.height - H) // 2
    return im.crop((left, top, left + W, top + H))


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT, exist_ok=True)

    catalog = {"default": "Pyrois", "groups": []}
    total_bytes = 0

    for gid, name, files in GROUPS:
        scored = []
        for fn in files:
            path = os.path.join(SRC, fn)
            if not os.path.isfile(path):
                raise SystemExit("missing: " + path)
            scored.append((fn, colorfulness(path)))
        scored.sort(key=lambda x: x[1], reverse=True)  # 1 = most colorful

        gdir = os.path.join(OUT, gid)
        os.makedirs(gdir, exist_ok=True)
        sources = []
        scores = []
        sizes = []
        for idx, (fn, score) in enumerate(scored, start=1):
            im = fit(Image.open(os.path.join(SRC, fn)))
            out = os.path.join(gdir, "%d.webp" % idx)
            im.save(out, "WEBP", quality=90, method=6)
            n = os.path.getsize(out)
            total_bytes += n
            sources.append(fn)
            scores.append(round(score, 2))
            sizes.append(n)
            print("  %s/%d.webp  <- %-26s color=%6.1f  %5.1f KB" %
                  (gid, idx, fn, score, n / 1024.0))

        catalog["groups"].append({
            "id": gid,
            "name": name,
            "name_zh": NAME_ZH[gid],
            "count": len(files),
            "sources": sources,
            "scores": scores,
        })
        print("%-14s %d images, %s" % (gid, len(files), ", ".join(sources)))

    with open(os.path.join(OUT, "groups.json"), "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)

    print("\nTOTAL %d groups, %d images, %.1f MB" %
          (len(catalog["groups"]), sum(g["count"] for g in catalog["groups"]),
           total_bytes / 1024.0 / 1024.0))


if __name__ == "__main__":
    main()
