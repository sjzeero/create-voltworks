#!/usr/bin/env python3
"""Draws textures/block/component/buzzer.png (64x64, 4 texels per model pixel).

Layout (uv units of 1/16):
  top    0..3 x 0..3   black piezo disc, satin rim highlight, centre sound hole, small "+" mark
  side   0..3 x 4..6   black housing wall with a light top edge
  bottom 4..7 x 0..3   flat dark plastic
  lead   8..9 x 0..1   tinned lead wire
Run from the repo root: python3 tools/buzzer_texture.py
"""
import math
from PIL import Image

S = 4
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()

# top: 12x12 texels
c = 6.0
for y in range(12):
    for x in range(12):
        dx, dy = x + 0.5 - c, y + 0.5 - c
        r = math.hypot(dx, dy)
        if r > 6.0:
            col = (20, 20, 22)            # outside the round edge (hidden corners of the octagon)
        elif r > 5.0:
            # rim: lit from the top-left
            light = 0.5 - 0.5 * (dx + dy) / (r or 1) / 1.4
            v = int(46 + 30 * light)
            col = (v, v, v + 2)
        elif r > 1.9:
            v = 28 if (x + y) % 5 else 32  # satin black face with faint grain
            col = (v, v, v + 2)
        elif r > 1.2:
            col = (70, 70, 74)            # hole rim
        else:
            col = (6, 6, 7)               # sound hole
        px[x, y] = col + (255,)
# "+" mark next to pad 0 (x low side)
for (x, y) in [(2, 6), (3, 5), (3, 6), (3, 7), (4, 6)]:
    px[x, y] = (205, 205, 205, 255)

# side: 12 wide, 8 tall (rows 16..23)
for y in range(16, 24):
    for x in range(12):
        if y == 16:
            v = 70
        elif y == 23:
            v = 14
        else:
            v = 30 - (y - 17)
        px[x, y] = (v, v, v + 2, 255)

# bottom
for y in range(12):
    for x in range(16, 28):
        px[x, y] = (18, 18, 20, 255)

# lead (tinned wire)
for y in range(4):
    for x in range(32, 36):
        v = (200, 200, 205) if x in (33, 34) else (150, 150, 158)
        px[x, y] = v + (255,)

img.save("src/main/resources/assets/voltworks/textures/block/component/buzzer.png")
print("ok")
