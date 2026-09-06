#!/usr/bin/env python3
"""Generate Player Portals' item sprite: vanilla's flint and steel with lapis where the flint was.

The striker is crafted the way flint and steel is, with lapis in the flint's place, so it looks
the way flint and steel does with lapis in the flint's place. The steel is vanilla's own pixels,
untouched; the flint is repainted through the lapis block's blues, keeping its shading. A steel
shard of our own drawing came first, and read as a blue-and-white paper dart: nothing about it
said "strike".

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
ITEMS = os.path.join(HERE, "src/main/resources/assets/player-portals-justfatlard/textures/item")
BLOCKS = os.path.join(HERE, "src/main/resources/assets/player-portals-justfatlard/textures/block")
GREY_PORTAL = os.path.join(BLOCKS, "nether_portal_grey.png")
OUT = os.path.join(ITEMS, "portal_striker.png")
LINKED_OUT = os.path.join(ITEMS, "linked_striker.png")

# Where the sparks fly off a linked striker: above the steel, where striking it would throw them.
# A linked striker is a striker that has been struck once, and it should look like one.
SPARKS = [(10, 1), (12, 2), (13, 4), (11, 4)]

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
    # For greyscale and truecolour a tRNS chunk names one colour as the transparent one. Vanilla's
    # flint and steel is exactly that: an 8-bit grey with black keyed out, and read without the
    # key every one of its empty pixels came back as solid black.
    grey_key = struct.unpack(">H", trns)[0] if trns and ctype == 0 else None
    rgb_key = struct.unpack(">HHH", trns) if trns and ctype == 2 else None
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
                    row.append((v, v, v, 0 if value == grey_key else 255))
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
                rgb = (out[i], out[i + 1], out[i + 2])
                row.append(rgb + (0 if rgb == rgb_key else 255,))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 0 if out[i] == grey_key else 255))
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


def luminance(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def portal_light():
    """The brightest thing in a nether portal, which is what a struck frame throws off."""
    return tones("block/nether_portal.png")[-1]


def build_linked(sprite):
    """The same striker, already sparking: the sparks are the nether portal's own light."""
    spark = portal_light() + (255,)
    linked = [list(row) for row in sprite]
    for x, y in SPARKS:
        linked[y][x] = spark
    return linked


def build_item():
    """Vanilla's flint and steel, its flint turned to lapis.

    The sprite is two things that never touch: the steel C on the left and the flint on the
    right. Which is which is settled by colour rather than by position, because the two share no
    colour at all: whatever appears in the left third of the sprite is steel, and every other
    colour is flint.
    """
    rows = [list(row) for row in vanilla("item/flint_and_steel.png")[:16]]
    steel = {row[x][:3] for row in rows for x in range(6) if row[x][3]}

    # Lapis darkest to brightest. The flint's own values are spread across that range, so the
    # lump keeps every facet the vanilla artist gave it and only changes colour.
    blues = tones("block/lapis_block.png", keep=6)
    flint = [row[x][:3] for row in rows for x in range(16) if row[x][3] and row[x][:3] not in steel]
    lo, hi = min(map(luminance, flint)), max(map(luminance, flint))

    sprite = []
    for row in rows:
        out = []
        for px in row:
            if not px[3] or px[:3] in steel:
                out.append(px if px[3] else CLEAR)
                continue
            t = (luminance(px) - lo) / (hi - lo) if hi > lo else 0
            step = t * (len(blues) - 1)
            a, b = blues[int(step)], blues[min(len(blues) - 1, int(step) + 1)]
            f = step - int(step)
            out.append(tuple(round(a[i] + (b[i] - a[i]) * f) for i in range(3)) + (255,))
        sprite.append(out)
    return sprite


def build_grey_portal():
    """Vanilla's portal, drained to its brightness, and the tint that puts the purple back.

    A block tint multiplies the texture, and the portal texture is purple: a white tint left it
    purple, a yellow tint made it brown, and half the dye palette came out looking like no dye at
    all. Drained to grey, the texture is nothing but shading and the tint is the whole of the
    colour, so white is white and yellow is yellow. Ordinary portals get the purple back through
    the tint's fallback, which is the vanilla texture's own average colour over its own average
    brightness: the one tint that makes grey times tint land where vanilla was.
    """
    rows = vanilla("block/nether_portal.png")
    # Brightness first, then how far the texture's strongest channel sits above it: the grey is
    # lifted by that much so the fallback tint fits under white, since a tint cannot brighten.
    total = [0, 0, 0]
    lum_total = 0
    for row in rows:
        for px in row:
            if px[3] == 0:
                continue
            for i in range(3):
                total[i] += px[i]
            lum_total += luminance(px)
    lift = max(total[i] / lum_total for i in range(3))
    grey = []
    grey_total = 0
    for row in rows:
        out = []
        for px in row:
            if px[3] == 0:
                out.append(CLEAR)
                continue
            l = min(255, round(luminance(px) * lift))
            out.append((l, l, l, px[3]))
            grey_total += l
        grey.append(out)
    fallback = tuple(min(255, round(total[i] / grey_total * 255)) for i in range(3))
    return grey, fallback


if __name__ == "__main__":
    grey, fallback = build_grey_portal()
    write_png(GREY_PORTAL, grey)
    with open(GREY_PORTAL + ".mcmeta", "w") as f:
        f.write('{\n  "animation": {}\n}\n')
    print("portal fallback tint: 0xFF%02X%02X%02X (put in Main.VANILLA_PORTAL)" % fallback)
    sprite = build_item()
    assert len(sprite) == 16 and len(sprite[0]) == 16, "item sprites are 16x16"
    write_png(OUT, sprite)
    write_png(LINKED_OUT, build_linked(sprite))
