from pathlib import Path
from PIL import Image
REF = Path(__file__).parent / 'pg_reference/component'
OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/voltworks/textures/block/component'
def H(h): return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,)
CU = [H('5e2f1c'), H('9a4f31'), H('c4724a'), H('e39a6c'), H('f7c39a')]
BK = [H('2a1810'), H('3f2618'), H('573522'), H('6f4630')]
def paint(name, fn):
    im = Image.open(REF / f'{name}.png').convert('RGBA'); fn(im); im.save(OUT / f'{name}.png')
def rect(im, x0, y0, rows):
    for dy, row in enumerate(rows):
        for dx, c in enumerate(row):
            if c is not None: im.putpixel((x0 + dx, y0 + dy), c)

def resistor(im):
    b = [H('e2c79a'), H('c9a874'), H('a5845a')]
    br, rd = (H('7a4526'), H('5a3019')), (H('b5432f'), H('86301f'))
    cols = [br, (b[0], b[1]), rd, (b[0], b[1])]
    rect(im, 2, 2, [[c[0] for c in cols], [c[1] for c in cols]])
    rect(im, 8, 2, [[c[0] for c in cols[::-1]], [c[1] for c in cols[::-1]]])
    rect(im, 2, 0, [[c[0] for c in cols]] * 2)
    rect(im, 6, 0, [[c[1] for c in cols[::-1]]] * 2)
    rect(im, 0, 2, [[b[0], b[1]], [b[1], b[2]]]); rect(im, 6, 2, [[b[0], b[1]], [b[1], b[2]]])
    rect(im, 0, 5, [[CU[3], CU[2], CU[2], CU[1]], [CU[2], CU[1], CU[1], CU[0]]])
    for p in [(1, 4), (2, 4), (5, 4), (6, 4)]: im.putpixel(p, CU[3])

def capacitor(im):
    s0, s1, s2, s3 = H('7d8fa6'), H('4d6382'), H('3b4f6c'), H('2b3a52')
    st1, st2 = H('c9cfc6'), H('a3aaa2')
    for i, x in enumerate((0, 3, 6, 9)):
        rows = [[s0, s0, s1], [s3, s3, s3], [s1, s2, s2], [s2, s2, s3]]
        if i == 0: rows = [[s0, st1, s1], [s3, st2, s3], [s1, st1, s2], [s2, st2, s3]]
        rect(im, x, 0, rows)
    z1, z2, z3 = H('d7e1da'), H('a9b8b3'), H('7f8c8a')
    rect(im, 0, 4, [[z2, z3, z2], [z3, z1, z3], [z2, z3, z2]])
    rect(im, 3, 4, [[BK[0]] * 3, [BK[0], BK[1], BK[0]], [BK[0]] * 3])
    im.putpixel((6, 4), CU[3]); im.putpixel((6, 5), CU[2])

def relay(im):
    c0, c1, c2, c3 = H('6f8fb8'), H('4f6f9a'), H('3c5780'), H('2c405f')
    p = H('e6dcc0')
    rect(im, 0, 0, [[c0, c0, c0, c1], [c1, p, p, c2], [c1, c2, c2, c2], [CU[2], CU[1], CU[2], CU[1]]])
    rect(im, 4, 0, [[c0, c1, c1], [c1, c2, c2], [c2, c2, c3], [CU[1], CU[2], CU[1]]])
    rect(im, 0, 4, [[c0, c0, c0, c1], [c0, c1, c1, c2], [c1, c2, c2, c2]])

def triode(im):
    g0, g1, g2 = H('dbe6df'), H('b8cbc4'), H('94aaa3')
    m1, m2 = H('cfd3cc'), H('8f948c')
    a1, a2 = CU[1], H('6e3a24')
    mica = H('e6dcc0')
    side = [[m2, m1, m2], [g0, a1, g1], [g1, a1, g2], [g1, a2, g2], [g2, mica, g2]]
    for x in (0, 3, 6, 9): rect(im, x, 11, side)
    rect(im, 3, 8, [[m2, m1, m2], [m1, H('eef0ea'), m1], [m2, m1, m2]])
    rect(im, 6, 8, [[g2] * 3, [g2, mica, g2], [g2] * 3])
    for x in (8, 10, 12, 14): rect(im, x, 7, [[BK[2], BK[1]]])
    for y in (1, 2, 3): rect(im, 6, y, [[BK[3], BK[2], BK[1]]])
    rect(im, 9, 3, [[BK[3], BK[2], BK[1]]])
    rect(im, 9, 0, [[BK[3], BK[2], BK[2]], [BK[2], CU[3], BK[1]], [BK[2], BK[1], BK[1]]])

if __name__ == '__main__':
    for n, f in [('resistor', resistor), ('capacitor', capacitor), ('relay', relay), ('triode', triode)]: paint(n, f)
    print('painted')
