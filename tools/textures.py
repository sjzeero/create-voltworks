from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/eecircuits/textures/item"

BLANK = {
    "o": (58, 56, 50), "L": (196, 192, 172), "b": (164, 160, 142), "s": (122, 118, 104),
    "h": (84, 80, 70), "H": (138, 134, 118),
}
COPPER = {
    "o": (24, 50, 30), "L": (98, 158, 76), "b": (64, 124, 54), "s": (42, 90, 40),
    "c": (231, 124, 86), "C": (144, 73, 49),
    "k": (40, 40, 44), "K": (78, 80, 82), "p": (176, 176, 176),
}
GOLDEN = {
    "o": (16, 36, 44), "L": (64, 128, 124), "b": (38, 96, 94), "s": (26, 66, 70),
    "c": (250, 214, 74), "C": (178, 100, 17),
    "k": (36, 34, 40), "K": (74, 72, 80), "p": (250, 214, 74),
    "q": (236, 230, 220), "Q": (176, 164, 150),
}
INCOMPLETE = dict(COPPER, g=(250, 214, 74), G=(178, 100, 17))

GRIDS = {
    "blank_circuit": (BLANK, [
        "................",
        "................",
        "................",
        ".oooooooooooooo.",
        ".oLLLLLLLLLLLLo.",
        ".oLhbbhbbhbbhso.",
        ".oLHbbHbbHbbHso.",
        ".oLbbbbbbbbbbso.",
        ".oLhbbhbbhbbhso.",
        ".oLHbbHbbHbbHso.",
        ".osssssssssssso.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
        "................",
    ]),
    "copper_circuit": (COPPER, [
        "................",
        "................",
        "................",
        ".oooooooooooooo.",
        ".oLLLLLLLLLLLLo.",
        ".oLccbbbpbbpbso.",
        ".oLbCbbKKKKkbso.",
        ".oLbccbKkkkkcco.",
        ".oLbbCbKkkkkbso.",
        ".occcCbkkkkkbso.",
        ".oLbbbbbpbbpbso.",
        ".osssssssssssso.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
    ]),
    "incomplete_golden_circuit": (INCOMPLETE, [
        "................",
        "................",
        "................",
        ".oooooooooooooo.",
        ".oLLLLLLLLLLLLo.",
        ".oLccbbbpbbpbso.",
        ".oLbCbbKKKKkbso.",
        ".oLbccbKkkkkggo.",
        ".oLbbCbKkkkkbGo.",
        ".occcCbkkkkkggo.",
        ".oLbbbbbpbbpbGo.",
        ".osssssssssssso.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
    ]),
    "golden_circuit": (GOLDEN, [
        "................",
        "................",
        "................",
        ".oooooooooooooo.",
        ".oLLLLLLLLLLLLo.",
        ".occbqbpbpbpbso.",
        ".oLCbQKKKKKKkso.",
        ".oLcbbKkkkkkkco.",
        ".oLccbKkkkkkkCo.",
        ".oLbCbkkkkkkkso.",
        ".occCbbpbpbpbso.",
        ".osssssssssssso.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
    ]),
}


def draw(name, palette, rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    assert len(rows) == 16, name
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch] + (255,))
    img.save(OUT / f"{name}.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (palette, rows) in GRIDS.items():
        draw(name, palette, rows)
