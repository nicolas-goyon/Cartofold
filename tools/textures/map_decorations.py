#!/usr/bin/env python3
"""Generate the map decoration icons of Cartofold.

Output:
- src/main/resources/assets/cartofold/textures/map/decorations/waypoint.png

    python3 tools/textures/map_decorations.py

The icon of a map decoration is 8 x 8 pixels, as the icons of the game. The icon of a waypoint is a pin: a teal disc
with a light center and a dark outline, and a point at the bottom. The other pixels stay transparent.

Requires Pillow.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "cartofold",
                   "textures", "map", "decorations", "waypoint.png")

SIZE = 8
OUTLINE = (16, 40, 44, 255)
TEAL = (40, 190, 200, 255)
LIGHT = (200, 250, 250, 255)


def waypoint():
    """A disc in the top 6 rows (middle 3.5, 2.5), and a point in the two bottom rows."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for x in range(SIZE):
        for y in range(6):
            distance = ((x - 3.5) ** 2 + (y - 2.5) ** 2) ** 0.5
            if distance <= 1.0:
                image.putpixel((x, y), LIGHT)
            elif distance <= 2.2:
                image.putpixel((x, y), TEAL)
            elif distance <= 3.1:
                image.putpixel((x, y), OUTLINE)
    for x in (3, 4):
        image.putpixel((x, 6), TEAL)
        image.putpixel((x, 7), OUTLINE)
    image.putpixel((2, 6), OUTLINE)
    image.putpixel((5, 6), OUTLINE)
    return image


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    waypoint().save(OUT)
    print("Wrote", os.path.relpath(OUT))


if __name__ == "__main__":
    main()
