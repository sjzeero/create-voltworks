import colorsys
from pathlib import Path
from PIL import Image
from core import PAL, ramp, ASSETS
PAL = dict(PAL, violet=ramp("2e1640", "55307a", "8150b0", "a979d4", "d6b4f0"), cyan=ramp("0f3a40", "1f6e78", "3aa6ad", "6fd2d2", "bff6f2"))
SRC = Path('/tmp/pg_tex_backup')
DST = ASSETS / 'voltworks/textures/block/component'
OVR = {'thyratron': {'blue': 'cyan'}, 'triode': {'blue': 'blue'}, 'light_bulb': {'blue': 'glass'}, 'electron_tube': {'red': 'redstone'}}

def cls(r, g, b, name):
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    h *= 360
    if s < 0.13 or (l > 0.9 and s < 0.5):
        return 'iron' if l > 0.62 else 'andesite' if l > 0.3 else 'dark'
    if h < 12 or h >= 330:
        m = 'red' if s > 0.45 and l < 0.55 else 'copper' if l < 0.75 else 'filament'
    elif h < 35:
        m = 'copper' if l < 0.75 else 'filament'
    elif h < 70:
        m = 'brass'
    elif h < 170:
        m = 'green'
    elif h < 255:
        m = 'blue' if s > 0.45 and l < 0.6 else 'glass'
    else:
        m = 'violet'
    return OVR.get(name, {}).get(m, m)

def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]

for f in sorted(SRC.glob('*.png')):
    im = Image.open(f).convert('RGBA')
    groups = {}
    for p in set(im.getdata()):
        if p[3]:
            groups.setdefault(cls(*p[:3], f.stem), set()).add(p[:3])
    lut = {}
    for m, cs in groups.items():
        cs = sorted(cs, key=lum)
        n = len(cs)
        for i, c in enumerate(cs):
            k = 2 if n == 1 else round(i * 4 / (n - 1)) if n >= 5 else [2, 1, 1, 0][0] if False else min(4, round(1 + i * 2 / max(1, n - 1)))
            lut[c] = PAL[m][k]
    out = Image.new('RGBA', im.size)
    out.putdata([lut[p[:3]] + (p[3],) if p[3] else p for p in im.getdata()])
    out.save(DST / f.name)
    print(f.stem, {m: len(c) for m, c in groups.items()})
