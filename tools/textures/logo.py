#!/usr/bin/env python3
"""Generate the logo of Cartofold: a potato in the middle of a paper map. "Kartoffel" is the German word for potato.

Output:
- src/main/resources/cartofold_logo.png

    python3 tools/textures/logo.py

The art is 64 x 64 pixels. The script scales it by 8 with no smoothing, so the pixels stay sharp on the mod list and on
Modrinth. The other pixels stay transparent.

Requires Pillow.
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "cartofold_logo.png")

SIZE = 64
SCALE = 8
MARGIN = 3
CENTER_X = 31.5
CENTER_Y = 32.5
TILT = math.radians(-20)

EDGE = (92, 64, 40, 255)
PAPER_EDGE = (184, 156, 104, 255)
PAPER = (226, 208, 158, 255)
PAPER_LIGHT = (238, 224, 180, 255)
PAPER_FOLD = (202, 180, 128, 255)
LAND = (146, 176, 96, 255)
LAND_DARK = (118, 152, 78, 255)
WATER = (112, 156, 204, 255)

OUTLINE = (58, 36, 20, 255)
SKIN_DARK = (148, 98, 50, 255)
SKIN = (194, 142, 80, 255)
SKIN_LIGHT = (224, 180, 116, 255)
SKIN_SHINE = (242, 212, 156, 255)
DIMPLE = (112, 72, 38, 255)


def on_paper(x, y):
    """True if a pixel is on the sheet. The sides of the sheet have small notches."""
    inset = MARGIN + (1 if (x // 5 + y // 5) % 3 == 0 else 0)
    return inset <= x < SIZE - inset and inset <= y < SIZE - inset


def paper(x, y):
    """The color of a pixel of the sheet."""
    near = (x - 1, x + 1)
    if not (on_paper(near[0], y) and on_paper(near[1], y) and on_paper(x, y - 1) and on_paper(x, y + 1)):
        return EDGE
    if not (on_paper(x - 2, y) and on_paper(x + 2, y) and on_paper(x, y - 2) and on_paper(x, y + 2)):
        return PAPER_EDGE
    if x in (21, 42) or y == 32:
        return PAPER_FOLD
    corner = math.hypot(x - 11, y - 12) + 1.3 * math.sin(x * 0.9) * math.cos(y * 0.7)
    other = math.hypot(x - 53, y - 52) + 1.3 * math.cos(x * 0.8) * math.sin(y * 0.8)
    if corner < 10:
        return LAND_DARK if (x + 2 * y) % 7 == 0 else LAND
    if other < 11:
        return LAND_DARK if (x + 2 * y) % 7 == 0 else LAND
    if abs((x - 54) + (y - 8) * 0.8 + 2 * math.sin(y * 0.5)) < 3 and y < 30:
        return WATER
    if abs((y - 54) + (x - 9) * 0.5 + 2 * math.sin(x * 0.5)) < 2.5 and x < 34:
        return WATER
    return PAPER_LIGHT if (x // 2 + y // 2) % 5 == 0 else PAPER


def potato(x, y):
    """The color of a pixel of the potato, or None out of the potato."""
    dx = x - CENTER_X
    dy = y - CENTER_Y
    px = dx * math.cos(TILT) + dy * math.sin(TILT)
    py = -dx * math.sin(TILT) + dy * math.cos(TILT)
    angle = math.atan2(py, px)
    a = 20.0
    b = 14.5
    edge = a * b / math.hypot(b * math.cos(angle), a * math.sin(angle))
    edge += 1.3 * math.sin(3 * angle + 0.6) + 0.8 * math.sin(5 * angle + 2.0)
    distance = math.hypot(px, py)
    if distance > edge:
        return None
    if distance > edge - 1.2:
        return OUTLINE
    # The light comes from the top left.
    light = (dx * 0.6 + dy * 0.8) / edge
    if math.hypot(x - 23, y - 25) < 3.2:
        return SKIN_SHINE
    if light > 0.42:
        return SKIN_DARK
    if light < -0.28:
        return SKIN_LIGHT
    return SKIN


def color(x, y):
    """The color of a pixel, or None out of the sheet."""
    if not on_paper(x, y):
        return None
    return potato(x, y) or paper(x, y)


def draw():
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            pixel = color(x, y)
            if pixel:
                image.putpixel((x, y), pixel)
    # The eyes of the potato: a dark dimple with a light line under it.
    for x, y in ((27, 31), (37, 25), (41, 34), (31, 39), (22, 37)):
        for dx in (0, 1):
            image.putpixel((x + dx, y), DIMPLE)
            image.putpixel((x + dx, y + 1), SKIN_LIGHT)
    return image.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST)


def main():
    draw().save(OUT, optimize=True)


if __name__ == "__main__":
    main()
