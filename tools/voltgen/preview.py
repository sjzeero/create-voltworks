import json, sys, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from core import ASSETS

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}


EXTRA_ROOTS = [Path("/tmp/create_assets/assets"), Path("/tmp/cpg/src/main/resources/assets")]


def load_tex(ref):
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    for root in [ASSETS] + EXTRA_ROOTS:
        f = root / ns / "textures" / f"{path}.png"
        if f.exists():
            return np.asarray(Image.open(f).convert("RGBA"))
    m = np.zeros((16, 16, 4), np.uint8)
    m[::2, ::2] = m[1::2, 1::2] = (255, 0, 255, 255)
    return m


def load_model(path):
    d = json.loads(Path(path).read_text())
    tex = {k: v for k, v in d.get("textures", {}).items()}
    return d, tex


def face_corners(f, a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    P = lambda x, y, z: np.array([x, y, z], float)
    return {
        "up": [P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)],
        "down": [P(x0, y0, z1), P(x1, y0, z1), P(x1, y0, z0), P(x0, y0, z0)],
        "north": [P(x1, y1, z0), P(x0, y1, z0), P(x0, y0, z0), P(x1, y0, z0)],
        "south": [P(x0, y1, z1), P(x1, y1, z1), P(x1, y0, z1), P(x0, y0, z1)],
        "west": [P(x0, y1, z0), P(x0, y1, z1), P(x0, y0, z1), P(x0, y0, z0)],
        "east": [P(x1, y1, z1), P(x1, y1, z0), P(x1, y0, z0), P(x1, y0, z1)],
    }[f]


def rot_fn(r):
    if not r:
        return lambda p: p
    o = np.array(r["origin"], float)
    t = math.radians(r["angle"])
    c, s = math.cos(t), math.sin(t)
    ax = r["axis"]
    if ax == "x":
        R = np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    elif ax == "y":
        R = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    else:
        R = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return lambda p: R @ (p - o) + o


def collect(models):
    quads = []
    for path, offset in models:
        d, tex = load_model(path)
        while "elements" not in d and "parent" in d:
            ns, p = d["parent"].split(":") if ":" in d["parent"] else ("minecraft", d["parent"])
            pd, ptex = load_model(ASSETS / ns / "models" / f"{p}.json")
            ptex.update(tex)
            d, tex = dict(pd, **{k: v for k, v in d.items() if k != "parent"}), ptex
            d.pop("parent", None) if "elements" in pd else None
        cache = {}
        for el in d.get("elements", []):
            rf = rot_fn(el.get("rotation"))
            for f, fd in el["faces"].items():
                ref = fd["texture"].lstrip("#")
                while ref in tex and not ":" in ref:
                    ref = tex[ref].lstrip("#")
                ref = tex.get(ref, ref)
                if ref not in cache:
                    cache[ref] = load_tex(ref)
                T = cache[ref]
                if T.shape[0] > T.shape[1]:
                    T = T[:T.shape[1]]
                cs = [rf(c) + np.array(offset, float) for c in face_corners(f, el["from"], el["to"])]
                uv = fd.get("uv", [0, 0, 16, 16])
                quads.append((cs, uv, T, f, fd.get("rotation", 0)))
    return quads


def render(models, scale=24, yaw=45, pitch=32, board=None, bg=(46, 50, 56)):
    quads = collect(models)
    yw, pt = math.radians(yaw), math.radians(pitch)
    right = np.array([math.cos(yw), 0, -math.sin(yw)])
    fwd = np.array([-math.sin(yw) * math.cos(pt), -math.sin(pt), -math.cos(yw) * math.cos(pt)])
    up = np.cross(right, fwd)
    allp = [c for q in quads for c in q[0]]
    if board:
        bw, bh = board
        allp += [np.array(p, float) for p in [(0, 0, 0), (bw, 0, 0), (0, 0, bh), (bw, 0, bh)]]
    pr = lambda p: (p @ right * scale, -(p @ up) * scale, p @ fwd)
    xs = [pr(p)[0] for p in allp]
    ys = [pr(p)[1] for p in allp]
    pad = scale
    ox, oy = -min(xs) + pad, -min(ys) + pad
    W, H = int(max(xs) - min(xs) + 2 * pad), int(max(ys) - min(ys) + 2 * pad)
    col = np.zeros((H, W, 3), float)
    col[:] = bg
    zb = np.full((H, W), np.inf)

    def raster(P, UV, T, shade):
        S = [pr(p) for p in P]
        sx = np.array([s[0] + ox for s in S])
        sy = np.array([s[1] + oy for s in S])
        sz = np.array([s[2] for s in S])
        x0, x1 = max(0, int(sx.min())), min(W - 1, int(math.ceil(sx.max())))
        y0, y1 = max(0, int(sy.min())), min(H - 1, int(math.ceil(sy.max())))
        if x1 < x0 or y1 < y0:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
        if abs(d) < 1e-9:
            return
        w1 = ((gx - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (gy - sy[0])) / d
        w2 = ((sx[1] - sx[0]) * (gy - sy[0]) - (gx - sx[0]) * (sy[1] - sy[0])) / d
        w0 = 1 - w1 - w2
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            return
        z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
        u = w0 * UV[0][0] + w1 * UV[1][0] + w2 * UV[2][0]
        v = w0 * UV[0][1] + w1 * UV[1][1] + w2 * UV[2][1]
        th, tw = T.shape[:2]
        ti = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
        tj = np.clip((v / 16 * th).astype(int), 0, th - 1)
        texel = T[tj, ti]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        m &= (texel[..., 3] > 0) & (z < sub - 1e-6)
        sub[m] = z[m]
        col[y0:y1 + 1, x0:x1 + 1][m] = texel[..., :3][m] * shade

    if board:
        bw, bh = board
        g = np.zeros((16, 16, 4), np.uint8)
        g[:] = (52, 104, 60, 255)
        g[0, :] = g[:, 0] = (38, 80, 46, 255)
        P = [np.array(p, float) for p in [(0, -0.001, 0), (bw, -0.001, 0), (bw, -0.001, bh), (0, -0.001, bh)]]
        for i in range(bw):
            for j in range(bh):
                P = [np.array(p, float) for p in [(i, -0.01, j), (i + 1, -0.01, j), (i + 1, -0.01, j + 1), (i, -0.01, j + 1)]]
                U = [(0, 0), (16, 0), (16, 16), (0, 16)]
                raster([P[0], P[1], P[2]], U[:3], g, 1.0)
                raster([P[0], P[2], P[3]], [U[0], U[2], U[3]], g, 1.0)

    for cs, uv, T, f, frot in quads:
        u0, v0, u1, v1 = uv
        U = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        k = (frot // 90) % 4
        U = U[k:] + U[:k]
        sh = SHADE[f]
        raster([cs[0], cs[1], cs[2]], U[:3], T, sh)
        raster([cs[0], cs[2], cs[3]], [U[0], U[2], U[3]], T, sh)
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8))


def sheet(items, cols=4, out="preview.png", title_h=18):
    imgs = []
    for title, models, kw in items:
        im = render(models, **kw)
        c = Image.new("RGB", (im.width, im.height + title_h), (30, 32, 36))
        c.paste(im, (0, title_h))
        ImageDraw.Draw(c).text((4, 3), title, fill=(230, 230, 230))
        imgs.append(c)
    cw = max(i.width for i in imgs)
    rows = [imgs[i:i + cols] for i in range(0, len(imgs), cols)]
    rh = [max(i.height for i in r) for r in rows]
    S = Image.new("RGB", (cw * min(cols, len(imgs)), sum(rh)), (30, 32, 36))
    y = 0
    for r, h in zip(rows, rh):
        for k, im in enumerate(r):
            S.paste(im, (k * cw, y))
        y += h
    S.save(out)
    return out


if __name__ == "__main__":
    paths = sys.argv[1:]
    sheet([(Path(p).stem, [(p, (0, 0, 0))], {}) for p in paths], out="/tmp/voltgen_preview.png")
