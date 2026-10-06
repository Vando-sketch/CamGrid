#!/usr/bin/env python3
"""Generates the CamGrid icon artwork from one geometry definition.

The mark is a 2x2 grid of camera tiles with a lens at its centre; the top-right tile is the
"live" accent.
Outputs:
  design/icon/*.svg                       source artwork (text already converted to paths)
  app/src/main/res/drawable/ic_launcher_*.xml   adaptive icon layers (API 26+)

PNG exports (legacy mipmaps, TV banner, social preview) are rendered from the SVGs by render.mjs.

Usage: python3 design/icon/generate.py inter-latin-800-normal.woff inter-latin-500-normal.woff
The font (Inter, SIL OFL 1.1) is only needed for the wordmark in the banner and the social
preview; its outlines are baked into the SVGs, so the font file is not stored in the repo.
"""

import math
import pathlib
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "design" / "icon"
RES = ROOT / "app" / "src" / "main" / "res"

# Geometry on the 108x108 adaptive-icon canvas. The safe zone is a circle of radius 33.
C = 54.0
HALF = 24.0  # half the side of the 2x2 tile block
GAP = 2.0  # half the width of the gutter between tiles
CORNER = 5.5  # tile corner radius
CUT = 17.0  # radius of the hole the lens sits in
LENS = 13.0  # radius of the lens glass

BG_TOP = "#18385A"
BG_BOTTOM = "#081320"
TILE = "#EEF3F8"
LIVE = "#FF4F3F"
GLASS_LIGHT = "#4A92D0"
GLASS_DARK = "#0B2238"
GLINT = "#FFFFFF"

QUADRANTS = [(-1, -1), (1, -1), (-1, 1), (1, 1)]  # tl, tr, bl, br as (sx, sy)
LIVE_QUADRANT = (1, -1)


def f(v):
    return f"{v:.3f}".rstrip("0").rstrip(".")


def tile(sx, sy, cx=C, cy=C):
    """One rounded tile of the 2x2 block, with its inner corner cut away around the lens."""
    k = math.sqrt(CUT**2 - GAP**2)
    g, h, r = GAP, HALF, CORNER
    # Built for the bottom-right tile in (u, v) offsets; mirroring flips the arc direction.
    corner, cut = (0, 1) if sx * sy > 0 else (1, 0)

    def pt(u, v):
        return f"{f(cx + sx * u)},{f(cy + sy * v)}"

    return (
        f"M{pt(g, k)}L{pt(g, h - r)}"
        f"A{f(r)},{f(r)} 0,0 {corner} {pt(g + r, h)}L{pt(h - r, h)}"
        f"A{f(r)},{f(r)} 0,0 {corner} {pt(h, h - r)}L{pt(h, g + r)}"
        f"A{f(r)},{f(r)} 0,0 {corner} {pt(h - r, g)}L{pt(k, g)}"
        f"A{f(CUT)},{f(CUT)} 0,0 {cut} {pt(g, k)}Z"
    )


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0,1 1,{f(2 * r)},0a{f(r)},{f(r)} 0,1 1,{f(-2 * r)},0Z"


LENS_PATH = circle(C, C, LENS)
GLINT_PATH = circle(C - 5, C - 5, 2.6)
GLINT_SMALL_PATH = circle(C + 4.5, C + 4.5, 1.2)
GLASS_CENTER = C - 6
GLASS_RADIUS = LENS * 1.7


# --- SVG --------------------------------------------------------------------------------------

def svg_defs(prefix=""):
    return f"""<defs>
  <linearGradient id="{prefix}bg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="{BG_TOP}"/><stop offset="1" stop-color="{BG_BOTTOM}"/>
  </linearGradient>
  <radialGradient id="{prefix}glass" gradientUnits="userSpaceOnUse" cx="{f(GLASS_CENTER)}" cy="{f(GLASS_CENTER)}" r="{f(GLASS_RADIUS)}">
    <stop offset="0" stop-color="{GLASS_LIGHT}"/><stop offset="1" stop-color="{GLASS_DARK}"/>
  </radialGradient>
</defs>"""


def svg_mark(prefix=""):
    parts = []
    for sx, sy in QUADRANTS:
        color = LIVE if (sx, sy) == LIVE_QUADRANT else TILE
        parts.append(f'<path fill="{color}" d="{tile(sx, sy)}"/>')
    parts.append(f'<path fill="url(#{prefix}glass)" d="{LENS_PATH}"/>')
    parts.append(f'<path fill="{GLINT}" fill-opacity="0.9" d="{GLINT_PATH}"/>')
    parts.append(f'<path fill="{GLINT}" fill-opacity="0.45" d="{GLINT_SMALL_PATH}"/>')
    return "\n  ".join(parts)


def svg(width, height, body, prefix=""):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">\n{svg_defs(prefix)}\n{body}\n</svg>\n'
    )


def placed_mark(cx, cy, scale):
    return (
        f'<g transform="translate({f(cx - C * scale)},{f(cy - C * scale)}) scale({f(scale)})">\n  '
        f"{svg_mark()}\n</g>"
    )


def bg_rect(width, height, rx=0):
    # The gradient is defined in bounding-box units, so it stretches with the shape.
    return f'<rect width="{width}" height="{height}" rx="{rx}" fill="url(#bg)"/>'


class Wordmark:
    def __init__(self, font_path):
        self.font = TTFont(font_path)
        self.glyphs = self.font.getGlyphSet()
        self.cmap = self.font.getBestCmap()
        self.upm = self.font["head"].unitsPerEm

    def width(self, text, size):
        return sum(self.glyphs[self.cmap[ord(ch)]].width for ch in text) * size / self.upm

    def path(self, text, x, baseline, size, tracking=0.0):
        s = size / self.upm
        pen = SVGPathPen(self.glyphs)
        advance = 0.0
        for ch in text:
            glyph = self.glyphs[self.cmap[ord(ch)]]
            glyph.draw(TransformPen(pen, (s, 0, 0, -s, x + advance, baseline)))
            advance += glyph.width * s + tracking * size
        return pen.getCommands()


# --- Android vector drawables -----------------------------------------------------------------

VECTOR_HEAD = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/icon/generate.py; edit that script, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
"""


def argb(hex_color, alpha=1.0):
    return f"#{round(alpha * 255):02X}{hex_color.lstrip('#').upper()}"


def vpath(data, color, alpha=1.0):
    return f'    <path\n        android:fillColor="{argb(color, alpha)}"\n        android:pathData="{data}" />\n'


def vpath_radial(data):
    return f"""    <path android:pathData="{data}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="{f(GLASS_CENTER)}"
                android:centerY="{f(GLASS_CENTER)}"
                android:gradientRadius="{f(GLASS_RADIUS)}"
                android:startColor="{argb(GLASS_LIGHT)}"
                android:endColor="{argb(GLASS_DARK)}" />
        </aapt:attr>
    </path>
"""


def vector_background():
    return VECTOR_HEAD + f"""    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:startColor="{argb(BG_TOP)}"
                android:endColor="{argb(BG_BOTTOM)}" />
        </aapt:attr>
    </path>
</vector>
"""


def vector_foreground():
    body = "".join(vpath(tile(sx, sy), LIVE if (sx, sy) == LIVE_QUADRANT else TILE) for sx, sy in QUADRANTS)
    body += vpath_radial(LENS_PATH)
    body += vpath(GLINT_PATH, GLINT, 0.9) + vpath(GLINT_SMALL_PATH, GLINT, 0.45)
    return VECTOR_HEAD + body + "</vector>\n"


def vector_monochrome():
    # Themed icons (Android 13+) use only the alpha channel, so the lens becomes a solid disc.
    body = "".join(vpath(tile(sx, sy), "#000000") for sx, sy in QUADRANTS)
    body += vpath(LENS_PATH, "#000000")
    return VECTOR_HEAD + body + "</vector>\n"


# --- main -------------------------------------------------------------------------------------

def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    words = Wordmark(sys.argv[1])
    body_text = Wordmark(sys.argv[2])

    # Full-bleed square: adaptive-icon preview, project image, store-style 512 icon.
    (OUT / "camgrid-icon.svg").write_text(svg(108, 108, bg_rect(108, 108) + "\n" + placed_mark(54, 54, 1)))
    # Pre-API-26 launcher icon: rounded square with a slightly larger mark.
    (OUT / "camgrid-icon-legacy.svg").write_text(
        svg(108, 108, f'<rect x="6" y="6" width="96" height="96" rx="22" fill="url(#bg)"/>\n' + placed_mark(54, 54, 1.32))
    )
    # Mark alone on transparent background.
    (OUT / "camgrid-mark.svg").write_text(svg(108, 108, placed_mark(54, 54, 1)))

    # TV banner, 320x180 dp (rendered at xhdpi, 640x360 px).
    size = 40
    tw = words.width("CamGrid", size)
    mark_d = 84
    gap = 20
    x0 = (320 - (mark_d + gap + tw)) / 2
    text = words.path("CamGrid", x0 + mark_d + gap, 90 + size * 0.36, size)
    (OUT / "camgrid-tv-banner.svg").write_text(
        svg(320, 180, bg_rect(320, 180) + "\n" + placed_mark(x0 + mark_d / 2, 90, mark_d / (2 * HALF))
            + f'\n<path fill="#FFFFFF" d="{text}"/>')
    )

    # GitHub social preview, 1280x640.
    title_size = 132
    title_w = words.width("CamGrid", title_size)
    tag = "Live camera grid for Android & Fire TV"
    tag_size = 40
    mark_d = 340
    gap = 80
    x0 = (1280 - (mark_d + gap + max(title_w, body_text.width(tag, tag_size)))) / 2
    tx = x0 + mark_d + gap
    (OUT / "camgrid-social-preview.svg").write_text(
        svg(1280, 640, bg_rect(1280, 640) + "\n" + placed_mark(x0 + mark_d / 2, 320, mark_d / (2 * HALF))
            + f'\n<path fill="#FFFFFF" d="{words.path("CamGrid", tx, 318, title_size)}"/>'
            + f'\n<path fill="#A8C1DA" d="{body_text.path(tag, tx + 4, 392, tag_size)}"/>')
    )

    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "ic_launcher_background.xml").write_text(vector_background())
    (drawable / "ic_launcher_foreground.xml").write_text(vector_foreground())
    (drawable / "ic_launcher_monochrome.xml").write_text(vector_monochrome())


if __name__ == "__main__":
    main()
