#!/usr/bin/env python3
"""Generate Player Portals' item sprite: an obsidian striker, already sparking.

Both palettes are read out of the vanilla jar rather than written down: the body
is obsidian's own colours and the sparks are the nether portal's, because those
are the two things the item is made of and the two things it makes.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow, the
same script generated art approach as the rest of the suite. Deterministic:
re-running produces identical bytes.

Usage: python3 generate_textures.py [path/to/minecraft.jar]
"""

import glob
import os
import struct
import sys
import zipfile
import zlib
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/player-portals-justfatlard/textures/item/portal_striker.png")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the sprite is cut from the same jar the
    mod is built against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the
    vanilla art comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth
    vanilla actually ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))




def tones(texture, keep=5):
    """A texture's own colours, darkest first."""
    rows = vanilla(texture)[:16]
    counts = Counter(px for row in rows for px in row if px[3])
    return sorted((px for px, _ in counts.most_common(keep)),
                  key=lambda p: 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2])


def obsidian():
    """Outline, shadow, body, highlight."""
    stone = tones("block/obsidian.png")
    return stone[0], stone[-3], stone[-2], stone[-1]


def portal_light():
    """The brightest thing in a nether portal, which is what a struck frame throws off."""
    return tones("block/nether_portal.png")[-1]


# One wedge of obsidian, struck: the shape is a shard rather than a tool because what the
# striker is, is the frame material with a spark already on it.
SHARD = [(4, 3), (11, 6), (9, 14), (3, 10)]
SPARKS = [(12, 2), (13, 4), (10, 1), (14, 6), (11, 4)]

SUPERSAMPLE = 8
COVERAGE = 0.43
LIT = 0.3
SHADOW = 0.62
OUTLINED = 0.35


def inside(poly, x, y):
    """Even-odd ray cast."""
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        (xi, yi), (xj, yj) = poly[i], poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def rasterize(poly, size=16):
    step = 1.0 / SUPERSAMPLE
    covered = []
    for y in range(size):
        for x in range(size):
            hits = sum(
                1
                for sy in range(SUPERSAMPLE)
                for sx in range(SUPERSAMPLE)
                if inside(poly, x + (sx + 0.5) * step, y + (sy + 0.5) * step)
            )
            if hits >= COVERAGE * SUPERSAMPLE * SUPERSAMPLE:
                covered.append((x, y))
    return covered


def draw_shard(sprite, poly):
    outline, dark, mid, light = obsidian()
    covered = rasterize(poly, len(sprite))
    if not covered:
        return sprite
    filled = set(covered)
    x0 = min(x for x, _ in covered)
    y0 = min(y for _, y in covered)
    span = (max(x for x, _ in covered) - x0) + (max(y for _, y in covered) - y0) or 1
    for (x, y) in covered:
        across = ((x - x0) + (y - y0)) / span
        on_edge = any((x + dx, y + dy) not in filled
                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if on_edge and across > OUTLINED:
            sprite[y][x] = outline
        elif across < LIT:
            sprite[y][x] = light
        elif across < SHADOW:
            sprite[y][x] = mid
        else:
            sprite[y][x] = dark
    return sprite


def draw_sparks(sprite, spots):
    spark = portal_light()
    for (x, y) in spots:
        if 0 <= x < len(sprite[0]) and 0 <= y < len(sprite):
            sprite[y][x] = spark
    return sprite


def build_item():
    sprite = [[CLEAR] * 16 for _ in range(16)]
    draw_shard(sprite, SHARD)
    draw_sparks(sprite, SPARKS)
    return sprite


if __name__ == "__main__":
    sprite = build_item()
    assert len(sprite) == 16 and len(sprite[0]) == 16, "item sprites are 16x16"
    write_png(OUT, sprite)
