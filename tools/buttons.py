"""Draws the “Watch the video” buttons that sit under the demo videos in the READMEs and the wiki.

    python3 tools/buttons.py

Each button lands in docs/media as watch-<lang>.png, drawn at twice the size it shows at: height="40" in Markdown.
The blue of the button is the accent of the UI in tools/diagrams.py, and white text on it reads in both themes.
Pillow draws the text: install it with "pip install Pillow".
"""
import os
import pathlib

from PIL import Image, ImageDraw, ImageFont

OUT = pathlib.Path(__file__).resolve().parent.parent / "docs" / "media"

LABELS = {"en": "Watch the video", "ru": "Смотреть видео"}

HEIGHT, RADIUS, PAD, GAP, SIZE = 80, 16, 36, 18, 34
BLUE, WHITE = "#0969da", "#ffffff"


def font(size):
    """Helvetica Bold on a Mac, its metric twin on Linux."""
    for path, index in (("/System/Library/Fonts/Helvetica.ttc", 1), ("/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf", 0)):
        if os.path.exists(path):
            return ImageFont.truetype(path, size, index=index)
    raise SystemExit("no Helvetica or Liberation Sans to draw the text")


def button(label):
    face = font(SIZE)
    left, top, right, bottom = face.getbbox(label)
    tri = 22
    width = PAD + tri + GAP + (right - left) + PAD
    img = Image.new("RGBA", (width, HEIGHT), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, width - 1, HEIGHT - 1), radius=RADIUS, fill=BLUE)
    mid = HEIGHT / 2
    d.polygon([(PAD, mid - tri / 2 - 2), (PAD, mid + tri / 2 + 2), (PAD + tri, mid)], fill=WHITE)
    d.text((PAD + tri + GAP - left, mid - (top + bottom) / 2), label, font=face, fill=WHITE)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for lang, label in LABELS.items():
        path = OUT / f"watch-{lang}.png"
        button(label).save(path, optimize=True)
        print(path.relative_to(OUT.parent.parent))


if __name__ == "__main__":
    main()
