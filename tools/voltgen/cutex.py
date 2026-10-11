import colorsys
from pathlib import Path
from PIL import Image
REF = Path(__file__).parent / 'pg_reference/component'
OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/voltworks/textures/block/component'
CU = [(60, 26, 14), (122, 58, 34), (178, 92, 58), (218, 130, 88), (246, 186, 146), (255, 226, 200)]
def cu(l):
    t = max(0, min(1, l)) * (len(CU) - 1); i = min(int(t), len(CU) - 2); f = t - i
    return tuple(round(CU[i][k] + (CU[i + 1][k] - CU[i][k]) * f) for k in range(3))
SKIP = {'label', 'gauge', 'relay', 'redstone_relay'}
for f in sorted(p for p in REF.glob('*.png') if p.stem not in SKIP):
    im = Image.open(f).convert('RGBA'); px = im.load(); n = 0
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if not a: continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if s < 0.16 and 0.62 <= l <= 0.93:
                px[x, y] = cu((l - 0.45) / 0.5) + (a,); n += 1
    im.save(OUT / f.name); print(f.stem, n)
