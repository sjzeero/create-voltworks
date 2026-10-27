#!/usr/bin/env python3
"""Draws textures/block/component/buzzer.png and models/component/buzzer.json.

16x16 texture, 1 texel per model pixel (same density as the board and the other components).
The buzzer is a 3x3x2 round piezo disc raised one pixel on two copper leads, like the capacitor.
Palette follows the black diode body (#08..#50 greys) and the copper legs of resistor/diode.

Texture layout (uv = texels):
  top    0..3 x 0..3   lit ring around a dark sound hole, corners = dark bevel of the round edge,
                       highlight on the north rim, faint '+' mark on the west rim (next to the + pad)
  side   3..6 x 0..2   darker outer columns (curved wall), lighter top lip
  lead   6..7 x 0..2   copper
  bottom 7..10 x 0..3
Run from the repo root: python3 tools/buzzer_texture.py
"""
import json
from PIL import Image

TEX = "src/main/resources/assets/voltworks/textures/block/component/buzzer.png"
MODEL = "src/main/resources/assets/voltworks/models/component/buzzer.json"


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

top = [
    ["1c1c1c", "6a6a6a", "161616"],
    ["8a8a88", "080808", "383838"],
    ["161616", "383838", "101010"],
]
for y, row in enumerate(top):
    for x, c in enumerate(row):
        px[x, y] = hx(c)

side = [["1c1c1c", "4c4c4c", "1c1c1c"],
        ["121212", "2e2e2e", "121212"]]
for y, row in enumerate(side):
    for x, c in enumerate(row):
        px[3 + x, y] = hx(c)

px[6, 0] = hx("d67b5b")
px[6, 1] = hx("c26b4c")

for x in range(7, 10):
    for y in range(3):
        px[x, y] = hx("161616")

img.save(TEX)

T = "#0"
wall = {"uv": [3, 0, 6, 2], "texture": T}
lead = {"uv": [6, 1, 7, 2], "texture": T}
elements = [
    {"name": "Disc", "from": [0, 1, 0], "to": [3, 3, 3], "faces": {
        "north": wall, "east": wall, "south": wall, "west": wall,
        "up": {"uv": [0, 0, 3, 3], "texture": T},
        "down": {"uv": [7, 0, 10, 3], "texture": T}}},
    {"name": "Lead +", "from": [0, 0, 1.5], "to": [1, 1, 1.5], "faces": {"north": lead, "south": lead}},
    {"name": "Lead -", "from": [2, 0, 1.5], "to": [3, 1, 1.5], "faces": {"north": lead, "south": lead}},
]
model = {
    "credit": "Voltworks buzzer: round piezo disc on two leads, 3x3, 1 texel per pixel",
    "textures": {"0": "voltworks:block/component/buzzer", "particle": "voltworks:block/component/buzzer"},
    "elements": elements,
}
with open(MODEL, "w") as f:
    json.dump(model, f, indent="\t")
    f.write("\n")
