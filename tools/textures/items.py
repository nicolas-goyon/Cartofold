#!/usr/bin/env python3
"""Generate the item icons of Cartofold.

Output:
- src/main/resources/assets/cartofold/textures/item/empty_survey_map.png
- src/main/resources/assets/cartofold/textures/item/survey_map.png

    python3 tools/textures/items.py

The icons are 16 x 16 pixels. A survey map is a sheet of blue-grey paper with a dark frame, so that it does not look
like a vanilla map. The empty survey map has a compass rose. The survey map has land, water and a teal route.

Requires Pillow.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "cartofold",
                   "textures", "item")

FRAME = (38, 48, 64, 255)
PAPER = (196, 210, 222, 255)
PAPER_DARK = (166, 182, 198, 255)
ROSE = (60, 80, 104, 255)
NORTH = (196, 52, 52, 255)
LAND = (110, 156, 84, 255)
LAND_DARK = (84, 124, 64, 255)
WATER = (70, 110, 190, 255)
ROUTE = (40, 190, 200, 255)


def sheet():
    """The sheet: a frame around rows 1 to 14 and columns 1 to 14, and a paper with a light grain."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(1, 15):
        for y in range(1, 15):
            border = x in (1, 14) or y in (1, 14)
            grain = PAPER_DARK if (x * 7 + y * 3) % 11 == 0 else PAPER
            image.putpixel((x, y), FRAME if border else grain)
    return image


def empty_survey_map():
    image = sheet()
    for i in range(4, 12):
        image.putpixel((i, 7), ROSE)
        image.putpixel((7, i), ROSE)
    for i in range(5, 11):
        image.putpixel((i, i), ROSE)
        image.putpixel((i, 14 - i), ROSE)
    image.putpixel((7, 3), NORTH)
    image.putpixel((7, 4), NORTH)
    return image


def survey_map():
    image = sheet()
    for x in range(2, 14):
        for y in range(2, 14):
            if x + y < 12:
                image.putpixel((x, y), WATER)
            elif (x * 5 + y * 3) % 7 == 0:
                image.putpixel((x, y), LAND_DARK)
            else:
                image.putpixel((x, y), LAND)
    for x, y in ((6, 12), (7, 11), (8, 10), (9, 9), (10, 8), (11, 7), (12, 6)):
        image.putpixel((x, y), ROUTE)
    image.putpixel((10, 4), NORTH)
    return image


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, image in (("empty_survey_map", empty_survey_map()), ("survey_map", survey_map())):
        path = os.path.join(OUT, name + ".png")
        image.save(path)
        print("Wrote", os.path.relpath(path))


if __name__ == "__main__":
    main()
