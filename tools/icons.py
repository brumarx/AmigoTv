"""Gera os ícones PNG da app (ícone, banner de TV e ícones dos atalhos)."""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "res/drawable"
BG = (21, 23, 28)
ACCENT = (46, 134, 222)
TILE = (60, 66, 80)
os.makedirs(OUT, exist_ok=True)


def font(size):
    for f in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def tiles(d, x0, y0, size):
    """Grelha 2x2 de mosaicos com um "play" no primeiro."""
    gap = size // 10
    t = (size - gap) // 2
    for r in range(2):
        for c in range(2):
            x, y = x0 + c * (t + gap), y0 + r * (t + gap)
            d.rounded_rectangle([x, y, x + t, y + t], radius=t // 5, fill=ACCENT if (r, c) == (0, 0) else TILE)
    cx, cy, s = x0 + t // 2, y0 + t // 2, t // 4
    d.polygon([(cx - s * 0.7, cy - s), (cx - s * 0.7, cy + s), (cx + s, cy)], fill="white")


def icon():
    im = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, 191, 191], radius=40, fill=BG)
    tiles(d, 36, 36, 120)
    im.save(os.path.join(OUT, "icon.png"))


def banner():
    im = Image.new("RGB", (320, 180), BG)
    d = ImageDraw.Draw(im)
    tiles(d, 22, 50, 80)
    d.text((118, 68), "TV Atalhos", font=font(30), fill="white")
    im.save(os.path.join(OUT, "banner.png"))


def slot(n):
    im = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, 191, 191], radius=40, fill=ACCENT)
    d.polygon([(62, 50), (62, 142), (140, 96)], fill="white")
    d.ellipse([124, 118, 180, 174], fill=BG)
    d.text((152, 146), str(n), font=font(36), fill="white", anchor="mm")
    im.save(os.path.join(OUT, f"slot{n}.png"))


icon()
banner()
for n in (1, 2, 3):
    slot(n)
