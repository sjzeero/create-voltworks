import random
from pathlib import Path
from PIL import Image
REF = Path(__file__).parent / 'pg_reference/component'
OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/voltworks/textures/block/component'
def H(h): return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,)
def sh(c, k): return tuple(max(0, min(255, round(v * k))) for v in c[:3]) + (255,)
class P:
    def __init__(s, name, seed=1): s.n = name; s.im = Image.open(REF / f'{name}.png').convert('RGBA'); s.r = random.Random(seed)
    def face(s, x, y, w, h, base, top=1.18, bot=0.72, noise=0.06, edge=True):
        for j in range(h):
            k = top if (edge and j == 0) else bot if (edge and j == h - 1 and h > 2) else 1.0
            for i in range(w): s.im.putpixel((x + i, y + j), sh(base, k * (1 + s.r.uniform(-noise, noise))))
    def col(s, x, y, h, c, top=1.15, bot=0.75):
        for j in range(h): s.im.putpixel((x, y + j), sh(c, top if j == 0 else bot if j == h - 1 and h > 2 else 1) )
    def px(s, x, y, c): s.im.putpixel((x, y), c)
    def save(s): s.im.save(OUT / f'{s.n}.png')

def resistor():
    p = P('resistor'); body = H('7a2b2b')
    for x, w, h in ((2, 4, 2), (8, 4, 2), (0, 2, 2), (6, 2, 2)): p.face(x, 2, w, h, body, top=1.2, bot=0.8)
    p.face(2, 0, 4, 2, sh(body, 1.15), edge=False); p.face(6, 0, 4, 2, sh(body, 0.75), edge=False)
    bands = {0: H('e8d6a8'), 2: H('e8d6a8'), 3: H('d9a63a')}
    for i, c in bands.items():
        for x0 in (2, 11 - 0):
            xx = x0 + i if x0 == 2 else 11 - i
            p.col(xx, 2, 2, c, 1.1, 0.82)
        p.col(2 + i, 0, 2, sh(c, 1.08), 1, 1); p.col(9 - i, 0, 2, sh(c, 0.78), 1, 1)
    p.save()

def capacitor():
    p = P('capacitor', 2); body = H('2f5a5c'); band = H('c4724a')
    for x in (0, 3, 6, 9):
        p.face(x, 0, 3, 4, body)
        for i in range(3): p.px(x + i, 1, sh(band, 1.0 + p.r.uniform(-0.07, 0.07)))
    p.face(0, 4, 3, 3, H('b9c4bf'), top=1.1, bot=0.85)
    p.px(1, 5, H('8a948f'))
    p.face(3, 4, 3, 3, H('243f40'), edge=False)
    p.save()

def relay():
    p = P('relay', 3); bk = H('5a3520'); br = H('d6a648')
    p.face(0, 0, 4, 4, bk); p.face(4, 0, 3, 4, sh(bk, 0.88))
    for x in range(7): p.px(x, 0, sh(br, 1.0 + p.r.uniform(-0.06, 0.06)))
    p.px(1, 2, H('e8d6a8')); p.px(2, 2, H('e8d6a8'))
    p.face(0, 4, 4, 3, br, top=1.15, bot=0.8)
    p.px(1, 5, sh(br, 0.7)); p.px(2, 5, sh(br, 0.7))
    p.save()

def triode():
    p = P('triode', 4); gl = H('d99a3c'); base = H('2f2622')
    for x in (0, 3, 6, 9):
        p.face(x, 11, 3, 5, gl, top=1.25, bot=0.85, noise=0.05)
        p.px(x + 1, 13, sh(gl, 0.62)); p.px(x + 1, 14, sh(gl, 0.62))
    p.face(3, 8, 3, 3, sh(gl, 1.2), edge=False); p.px(4, 9, H('fff0c8'))
    p.face(6, 8, 3, 3, sh(gl, 0.8), edge=False)
    for x in (8, 10, 12, 14): p.face(x, 7, 2, 1, H('c4724a'), edge=False)
    for y in (1, 2, 3): p.face(6, y, 3, 1, base, edge=False)
    p.face(9, 3, 3, 1, base, edge=False); p.face(9, 0, 3, 3, sh(base, 1.25))
    p.save()

if __name__ == '__main__':
    for f in (resistor, capacitor, relay, triode): f()
    print('painted')
