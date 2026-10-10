import json, math, random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets"


def ramp(*hexes):
    return [tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) for h in hexes]


# outline, shadow, base, light, highlight
PAL = {
    "brass": ramp("5a3a1c", "a1662d", "d39c45", "f0c86a", "fff0a8"),
    "copper": ramp("4a2216", "8f4a30", "c26a45", "e7966a", "ffc7a0"),
    "andesite": ramp("3a3c3c", "6b6e6c", "8f928e", "aeb1ab", "cfd2ca"),
    "iron": ramp("3d4347", "6d757a", "a3abb0", "c9d0d3", "eef3f4"),
    "zinc": ramp("4b5554", "7f8c8a", "a9b8b3", "cbd9d3", "eaf6f0"),
    "dark": ramp("141212", "262221", "36302d", "4a423d", "625850"),
    "bakelite": ramp("2a120a", "4a2414", "6a3820", "86502e", "a56c40"),
    "ceramic": ramp("6e6253", "b3a690", "d8ccb4", "ece3cf", "fbf6ea"),
    "glass": ramp("3d5a63", "6e9aa3", "9fc7cc", "c7e4e4", "f4ffff"),
    "red": ramp("4a0f0f", "8a1f1c", "bd3329", "de5a43", "f58e6e"),
    "blue": ramp("152447", "25407a", "3a62ad", "5a86cc", "8fb3e6"),
    "green": ramp("173d1c", "2a6630", "3f8f45", "62b061", "94d48a"),
    "yellow": ramp("5a4210", "a07a1c", "d8ad2e", "f0cf55", "fff09a"),
    "white": ramp("77756d", "b8b5aa", "dcd9cd", "efede4", "ffffff"),
    "black": ramp("0b0b0d", "1a1a1e", "2a2a30", "3d3d45", "55555f"),
    "redstone": ramp("3a0000", "7a0a05", "b51a0e", "e53a1f", "ff7a52"),
    "filament": ramp("7a3a00", "c8700a", "ff9f1c", "ffd060", "fff2b8"),
    "wood": ramp("3f2a14", "6e4a26", "94683a", "b48650", "d2a670"),
}

DIRS = ("down", "up", "north", "south", "west", "east")


class Mat:
    def __init__(self, pal, bevel=True, noise=0.035, top_boost=1, seed=0):
        self.pal = PAL[pal] if isinstance(pal, str) else pal
        self.bevel, self.noise, self.top_boost, self.seed = bevel, noise, top_boost, seed

    def paint(self, w, h, face, rng):
        p = self.pal
        base = 3 if face == "up" and self.top_boost else 2
        if face == "down":
            base = 1
        img = Image.new("RGBA", (w, h))
        for y in range(h):
            for x in range(w):
                i = base
                if self.bevel and w >= 3 and h >= 3:
                    if x == 0 or y == 0:
                        i = min(4, base + 1)
                    if x == w - 1 or y == h - 1:
                        i = max(0, base - 1)
                elif self.bevel and min(w, h) == 2 and h >= 2 and y == h - 1 and face not in ("up", "down"):
                    i = max(0, base - 1)
                c = p[i]
                if self.noise:
                    f = 1 + rng.uniform(-self.noise, self.noise)
                    c = tuple(max(0, min(255, int(v * f))) for v in c)
                img.putpixel((x, y), c + (255,))
        return img


def M(name, **kw):
    return Mat(name, **kw)


class Box:
    def __init__(self, frm, to, mat, name="", faces=None, skip=(), paint=None, rot=None, uv_mats=None):
        self.frm, self.to, self.mat, self.name = list(frm), list(to), mat, name
        self.faces = [f for f in (faces or DIRS) if f not in skip]
        self.paint = paint or {}
        self.rot = rot
        self.uv_mats = uv_mats or {}

    def face_size(self, f):
        dx, dy, dz = (self.to[i] - self.frm[i] for i in range(3))
        return {"up": (dx, dz), "down": (dx, dz), "north": (dx, dy), "south": (dx, dy),
                "west": (dz, dy), "east": (dz, dy)}[f]


class Model:
    def __init__(self, name, namespace="voltworks", folder="component", tex_name=None, tex_folder="block/component",
                 credit="Voltworks (Create-style redesign)", parent=None, extra=None):
        self.name, self.ns, self.folder = name, namespace, folder
        self.tex_name = tex_name or name
        self.tex_folder = tex_folder
        self.boxes, self.credit, self.parent, self.extra = [], credit, parent, extra or {}

    def add(self, *a, **kw):
        b = Box(*a, **kw)
        self.boxes.append(b)
        return b

    def build(self, atlas=None):
        own = atlas is None
        if own:
            atlas = Atlas(self.tex_name, self.tex_folder, self.ns)
        elements = []
        for b in self.boxes:
            faces = {}
            for f in b.faces:
                w, h = b.face_size(f)
                if w <= 0 or h <= 0:
                    continue
                mat = b.uv_mats.get(f, b.mat)
                uv = atlas.alloc(w, h, mat, f, b.paint.get(f))
                faces[f] = {"uv": uv, "texture": "#0"}
            el = {"name": b.name or "part", "from": b.frm, "to": b.to, "faces": faces}
            if b.rot:
                el["rotation"] = b.rot
            elements.append(el)
        data = {"credit": self.credit}
        if self.parent:
            data["parent"] = self.parent
        data["textures"] = {"0": atlas.ref, "particle": atlas.ref}
        data["elements"] = elements
        data.update(self.extra)
        self.data, self.atlas = data, atlas
        return data

    def write(self, shared_atlas=False):
        if not hasattr(self, "data"):
            self.build()
        p = ASSETS / self.ns / "models" / self.folder / f"{self.name}.json"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(self.data, indent=2) + "\n")
        if not shared_atlas:
            self.atlas.save()
        return p


class Atlas:
    def __init__(self, name, folder="block/component", ns="voltworks", seed=None):
        self.name, self.folder, self.ns = name, folder, ns
        self.ref = f"{ns}:{folder}/{name}"
        self.rects = []
        self.rng = random.Random(seed if seed is not None else hash(name) & 0xffff)
        self.cache = {}

    def alloc(self, w, h, mat, face, painter):
        pw, ph = max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))
        key = (pw, ph, id(mat), face if face in ("up", "down") else "side", painter)
        if painter is None and key in self.cache:
            r = self.cache[key]
        else:
            img = mat.paint(pw, ph, face, self.rng)
            if painter:
                painter(img, mat.pal)
            r = {"img": img, "w": pw, "h": ph}
            self.rects.append(r)
            if painter is None:
                self.cache[key] = r
        return (r, w, h)

    def pack(self):
        size = 16
        while True:
            ok, x, y, row = True, 0, 0, 0
            for r in sorted(self.rects, key=lambda r: -r["h"]):
                if x + r["w"] > size:
                    x, y, row = 0, y + row, 0
                if y + r["h"] > size or r["w"] > size:
                    ok = False
                    break
                r["x"], r["y"] = x, y
                x += r["w"]
                row = max(row, r["h"])
            if ok:
                return size
            size *= 2

    def finalize(self, models):
        self.size = self.pack()
        s = 16 / self.size
        for m in models:
            for el in m.data["elements"]:
                for f, fd in el["faces"].items():
                    if isinstance(fd["uv"], tuple):
                        r, w, h = fd["uv"]
                        fd["uv"] = [round(r["x"] * s, 4), round(r["y"] * s, 4), round((r["x"] + w) * s, 4), round((r["y"] + h) * s, 4)]

    def image(self):
        img = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        for r in self.rects:
            img.paste(r["img"], (r["x"], r["y"]))
        return img

    def save(self):
        p = ASSETS / self.ns / "textures" / self.folder / f"{self.name}.png"
        p.parent.mkdir(parents=True, exist_ok=True)
        self.image().save(p)
        return p


def family(atlas, *models):
    for m in models:
        m.build(atlas)
    atlas.finalize(models)
    for m in models:
        m.write(shared_atlas=True)
    atlas.save()


def single(model):
    model.build()
    model.atlas.finalize([model])
    model.write()


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), tuple(c[:3]) + (255,))
