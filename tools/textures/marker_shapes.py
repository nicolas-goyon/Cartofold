#!/usr/bin/env python3
"""Generate the marker shapes of Cartofold.

Output:
- src/main/resources/assets/cartofold/textures/marker/<shape>.png

    python3 tools/textures/marker_shapes.py

A shape is 16 x 16 pixels: white pixels with a dark outline. The map multiplies the pixels by the color of the marker,
so the white pixels take this color and the outline stays dark. The other pixels stay transparent.

Requires Pillow.
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "cartofold",
                   "textures", "marker")

SIZE = 16
MIDDLE = (SIZE - 1) / 2
WHITE = (255, 255, 255, 255)
OUTLINE = (24, 24, 24, 255)


def square(x, y):
    return max(abs(x), abs(y)) <= 5.5


def ring(x, y):
    return 3.5 <= math.hypot(x, y) <= 6.5


def cross(x, y):
    return abs(abs(x) - abs(y)) <= 1 and max(abs(x), abs(y)) <= 5.5


def diamond(x, y):
    return abs(x) + abs(y) <= 6.5


def dot(x, y):
    return math.hypot(x, y) <= 4


SHAPES = {"square": square, "ring": ring, "cross": cross, "diamond": diamond, "dot": dot}


def draw(shape):
    """The pixels of a shape, and an outline on the free pixels next to them."""
    inside = {(x, y) for x in range(SIZE) for y in range(SIZE) if shape(x - MIDDLE, y - MIDDLE)}
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for x, y in inside:
        image.putpixel((x, y), WHITE)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            near = (x + dx, y + dy)
            if near not in inside and 0 <= near[0] < SIZE and 0 <= near[1] < SIZE:
                image.putpixel(near, OUTLINE)
    return image


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, shape in SHAPES.items():
        draw(shape).save(os.path.join(OUT, name + ".png"))


if __name__ == "__main__":
    main()
