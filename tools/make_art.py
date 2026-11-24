"""Generates the mod's pixel-art icon and banner (pure Python, no Pillow needed).

Usage (from the project root):  python3 tools/make_art.py
Outputs:
  src/main/resources/assets/buildchecklist/icon.png  (128x128, in-game / Mod Menu)
  docs/icon.png                                      (512x512, Modrinth / GitHub)
  docs/banner.png                                    (1200x300, README / Modrinth gallery)
"""
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ---- palette (RGBA) ----
C = {
    "K": (43, 29, 14, 255),     # outline
    "B": (139, 90, 43, 255),    # board
    "b": (107, 68, 32, 255),    # board shade
    "M": (200, 200, 205, 255),  # metal clip
    "m": (120, 120, 128, 255),  # metal shade
    "P": (242, 236, 217, 255),  # paper
    "p": (214, 205, 180, 255),  # paper shade
    "D": (150, 141, 118, 255),  # text lines / empty box
    "G": (76, 200, 76, 255),    # check
    "g": (38, 128, 38, 255),    # check shade
    "S": (143, 143, 143, 255),  # stone
    "s": (110, 110, 110, 255),
    "L": (196, 236, 246, 255),  # glass
    "l": (120, 190, 210, 255),
    "O": (190, 150, 95, 255),   # planks
    "o": (140, 105, 60, 255),
}


def canvas(w, h, color=(0, 0, 0, 0)):
    return [[color] * w for _ in range(h)]


def rect(img, x0, y0, x1, y1, color):
    """Fills the inclusive rectangle x0..x1, y0..y1."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= y < len(img) and 0 <= x < len(img[0]):
                img[y][x] = color


def stamp(img, x0, y0, rows, palette=C):
    """Draws a small pattern; '.' is transparent."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != ".":
                img[y0 + dy][x0 + dx] = palette[ch]


def block(img, x, y, light, dark):
    """5x5 block icon with a simple bevel."""
    rect(img, x, y, x + 4, y + 4, C[light])
    rect(img, x + 4, y, x + 4, y + 4, C[dark])
    rect(img, x, y + 4, x + 4, y + 4, C[dark])


CHECK = [
    "....G",
    "...Gg",
    "G.Gg.",
    "gGg..",
    ".g...",
]


def make_icon():
    img = canvas(32, 32)
    # Board with outline and shaded right/bottom edge.
    rect(img, 3, 3, 28, 30, C["K"])
    rect(img, 4, 4, 27, 29, C["B"])
    rect(img, 27, 4, 27, 29, C["b"])
    rect(img, 4, 29, 27, 29, C["b"])
    # Paper.
    rect(img, 7, 7, 24, 27, C["P"])
    rect(img, 24, 7, 24, 27, C["p"])
    rect(img, 7, 27, 24, 27, C["p"])
    # Metal clip on top.
    rect(img, 11, 1, 20, 6, C["K"])
    rect(img, 12, 2, 19, 5, C["M"])
    rect(img, 12, 5, 19, 5, C["m"])
    rect(img, 14, 1, 17, 1, C["K"])
    # Three rows: block icon, text line, check / empty box.
    rows = [(9, "S", "s", True), (15, "L", "l", True), (21, "O", "o", False)]
    for y, light, dark, done in rows:
        block(img, 9, y, light, dark)
        rect(img, 16, y + 2, 18, y + 2, C["D"])
        if done:
            stamp(img, 19, y, CHECK)
        else:
            rect(img, 19, y, 23, y + 4, C["D"])
            rect(img, 20, y + 1, 22, y + 3, C["P"])
    return img


# 5x7 pixel font (only the letters we need).
FONT = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    " ": ["...", "...", "...", "...", "...", "...", "..."],
}


def text_width(text, scale):
    return sum((len(FONT[ch][0]) + 1) * scale for ch in text) - scale


def draw_text(img, x, y, text, scale, color, shadow):
    """Minecraft-like text: the shadow is the same text offset by one (scaled) pixel."""
    for offset, col in ((scale, shadow), (0, color)):
        cx = x + offset
        for ch in text:
            glyph = FONT[ch]
            for gy, row in enumerate(glyph):
                for gx, px in enumerate(row):
                    if px == "#":
                        rect(img, cx + gx * scale, y + offset + gy * scale,
                             cx + gx * scale + scale - 1, y + offset + gy * scale + scale - 1, col)
            cx += (len(glyph[0]) + 1) * scale


def make_banner(icon):
    w, h = 240, 60
    img = canvas(w, h)
    # Sky: a few flat bands, lighter towards the horizon.
    bands = [(120, 167, 255), (134, 178, 255), (150, 190, 255), (166, 202, 255)]
    for y in range(h):
        r, g, b = bands[min(len(bands) - 1, y * len(bands) // 46)]
        rect(img, 0, y, w - 1, y, (r, g, b, 255))
    # Ground: one grass row with "hanging" grass pixels, then speckled dirt (fixed seed = same image every run).
    rnd = random.Random(26)
    grass, grass_dark = (95, 159, 53, 255), (72, 128, 40, 255)
    dirt, dirt_dark, dirt_light = (134, 96, 67, 255), (108, 76, 52, 255), (155, 115, 82, 255)
    ground = 47
    for x in range(w):
        rect(img, x, ground, x, ground + 1, grass if rnd.random() > 0.2 else grass_dark)
        if rnd.random() > 0.55:
            img[ground + 2][x] = grass_dark
        for y in range(ground + 2 if img[ground + 2][x] != grass_dark else ground + 3, h):
            v = rnd.random()
            img[y][x] = dirt_dark if v < 0.2 else dirt_light if v > 0.88 else dirt
    # Icon standing on the grass.
    ix, iy = 14, ground - 32 + 2
    for y in range(32):
        for x in range(32):
            if icon[y][x][3]:
                img[iy + y][ix + x] = icon[y][x]
    # Title and subtitle.
    tx = 56
    draw_text(img, tx, 11, "BUILD CHECKLIST", 2, (255, 255, 255, 255), (63, 63, 63, 255))
    draw_text(img, tx, 32, "FABRIC MOD", 1, (255, 255, 85, 255), (63, 63, 21, 255))
    assert tx + text_width("BUILD CHECKLIST", 2) < w, "title does not fit"
    return img


def upscale(img, factor):
    """Nearest-neighbour upscale, keeps pixels sharp."""
    return [[px for px in row for _ in range(factor)] for row in img for _ in range(factor)]


def write_png(path, img):
    h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in img)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path.relative_to(ROOT)} ({w}x{h})")


if __name__ == "__main__":
    icon = make_icon()
    write_png(ROOT / "src/main/resources/assets/buildchecklist/icon.png", upscale(icon, 4))
    write_png(ROOT / "docs/icon.png", upscale(icon, 16))
    write_png(ROOT / "docs/banner.png", upscale(make_banner(icon), 5))
