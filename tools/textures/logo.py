#!/usr/bin/env python3
"""Generate the logo of Cartofold: a vanilla map in the shape of a potato. "Kartoffel" is the German word for potato.

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
CENTER_X = 31.5
CENTER_Y = 36.0
SPROUT_X = 38
TILT = math.radians(-24)

OUTLINE = (58, 38, 22, 255)
SKIN_DARK = (150, 104, 56, 255)
SKIN = (196, 148, 88, 255)
SKIN_LIGHT = (226, 186, 124, 255)
PAPER = (232, 216, 168, 255)
PAPER_DARK = (208, 188, 136, 255)
LAND = (124, 168, 72, 255)
LAND_DARK = (88, 132, 56, 255)
WATER = (72, 124, 196, 255)
SAND = (214, 196, 128, 255)
MARK = (204, 40, 40, 255)
SPROUT = (120, 196, 72, 255)
SPROUT_DARK = (64, 132, 56, 255)
DIMPLE = (120, 82, 46, 255)


def radius(angle):
    """The edge of the potato in a direction: an oval with soft bumps."""
    a = 28.0
    b = 22.0
    oval = a * b / math.hypot(b * math.cos(angle), a * math.sin(angle))
    return oval + 1.5 * math.sin(3 * angle + 0.6) + 0.9 * math.sin(5 * angle + 2.0)


def polar(x, y):
    """The distance from the center, as a part of the edge in this direction."""
    dx = x - CENTER_X
    dy = y - CENTER_Y
    px = dx * math.cos(TILT) + dy * math.sin(TILT)
    py = -dx * math.sin(TILT) + dy * math.cos(TILT)
    return math.hypot(px, py), radius(math.atan2(py, px))


def land(x, y):
    """The color of the map at a pixel, or None for the paper."""
    river = 33 + 5 * math.sin(x / 6.0)
    if abs(y - river + (x - 32) * 0.55) < 1.6:
        return WATER
    first = math.hypot(x - 23, (y - 28) * 1.2) + 1.5 * math.sin(x * 0.9) * math.cos(y * 0.7)
    second = math.hypot(x - 42, (y - 44) * 1.15) + 1.5 * math.cos(x * 0.8) * math.sin(y * 0.9)
    third = math.hypot(x - 45, y - 24) + 1.2 * math.sin(y * 1.1)
    for value, size in ((first, 10.5), (second, 9.5), (third, 6.0)):
        if value < size - 3:
            return LAND_DARK if (x + y * 2) % 7 == 0 else LAND
        if value < size - 1.5:
            return LAND
        if value < size:
            return SAND
    return None


def color(x, y):
    """The color of a pixel of the potato, or None out of the potato."""
    distance, edge = polar(x, y)
    if distance > edge:
        return None
    if distance > edge - 1.3:
        return OUTLINE
    if distance > edge - 4.2:
        # The light comes from the top left.
        light = (x - CENTER_X) * 0.6 + (y - CENTER_Y) * 0.8
        return SKIN_LIGHT if light < -9 else SKIN_DARK if light > 9 else SKIN
    pixel = land(x, y) or PAPER
    if pixel == PAPER and (x + y) % 2 == 0 and int(x // 13) % 2 == 1:
        pixel = PAPER_DARK
    # The folds of the map: one dark line and one light line.
    if x in (22, 42):
        return OUTLINE if pixel is None else tuple(max(0, c - 44) for c in pixel[:3]) + (255,)
    return pixel


def draw():
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            pixel = color(x, y)
            if pixel:
                image.putpixel((x, y), pixel)
    # The eyes of the potato, on its skin.
    for y in range(SIZE):
        for x in range(SIZE):
            if color(x, y) in (SKIN, SKIN_LIGHT, SKIN_DARK) and (x * 7 + y * 13) % 89 == 0:
                image.putpixel((x, y), DIMPLE)
                image.putpixel((x + 1, y), DIMPLE)
    # The red cross of a treasure.
    for step in range(-2, 3):
        image.putpixel((37 + step, 35 + step), MARK)
        image.putpixel((37 + step, 35 - step), MARK)
        image.putpixel((38 + step, 35 + step), MARK)
        image.putpixel((38 + step, 35 - step), MARK)
    # The sprout grows from the top of the potato.
    top = next(y for y in range(SIZE) if color(SPROUT_X, y) is not None)
    for y in range(top - 5, top + 1):
        image.putpixel((SPROUT_X, y), SPROUT_DARK)
    for step in range(1, 6):
        leaf = top - 5 - step // 2
        image.putpixel((SPROUT_X + step, leaf), SPROUT)
        image.putpixel((SPROUT_X + step, leaf - 1), SPROUT)
        image.putpixel((SPROUT_X - step, leaf), SPROUT)
        image.putpixel((SPROUT_X - step, leaf - 1), SPROUT)
    return image.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST)


def main():
    draw().save(OUT, optimize=True)


if __name__ == "__main__":
    main()
