#!/usr/bin/env python3
"""Draws textures/block/component/buzzer.png (16x16, 1 texel per model pixel,
the same density as every other board component, e.g. capacitor/resistor).

Layout (uv units = texels):
  top    0..3 x 0..3   3x3 "plus" disc seen from above: lit rim, dark centre sound hole
  side   4..7 x 0..2   black housing wall, light top edge (3 wide for the centre bar)
  bottom 8..11 x 0..3  flat dark plastic
  lead   12..13 x 0..1 tinned lead wire (same grey as the capacitor leads)
Run from the repo root: python3 tools/buzzer_texture.py
"""
from PIL import Image

OUT = "src/main/resources/assets/voltworks/textures/block/component/buzzer.png"
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

HOLE = (12, 12, 13, 255)
LIT = (64, 64, 68, 255)      # rim lit from north-west
MID = (48, 48, 51, 255)
SHADE = (36, 36, 39, 255)
EDGE = (58, 58, 62, 255)     # top edge of the wall
WALL = (30, 30, 32, 255)
WALL2 = (26, 26, 28, 255)
BOTTOM = (20, 20, 21, 255)
LEAD = (176, 176, 176, 255)

# top (plus shape; corners are never sampled)
px[1, 0] = LIT
px[0, 1] = LIT
px[1, 1] = HOLE
px[2, 1] = SHADE
px[1, 2] = MID
for x in (0, 2):
    for y in (0, 2):
        px[x, y] = MID

# side: 3x2
for x in range(4, 7):
    px[x, 0] = EDGE
    px[x, 1] = WALL if x != 5 else WALL2

# bottom
for x in range(8, 11):
    for y in range(0, 3):
        px[x, y] = BOTTOM

px[12, 0] = LEAD
img.save(OUT)
