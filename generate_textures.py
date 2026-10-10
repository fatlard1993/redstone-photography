#!/usr/bin/env python3
"""Draw redstone-photography's item sprites and mod icon. Run from the mod root after one build
(the vanilla art comes out of the Minecraft jar Loom caches); rewrites the PNGs in place.

A sprite is a hand-placed pixel map. Each letter is a material and a band of its shades: the
shades are the vanilla item's own palette, darkest to lightest, and where in its band a pixel
falls is read from the matching vanilla block's texture at that spot, so every fill carries the
game's own speckle. The darkest shade of each material is its outline, as vanilla draws them.
"""
import io
import os
import zipfile

from PIL import Image

OUT = "src/main/resources/assets/redstone-photography"

_TEXTURES = {}


def vanilla(name):
    """A texture out of the Minecraft jar the mod is built against, which Loom caches after a build."""
    if name not in _TEXTURES:
        version = next(line.split("=")[1].strip() for line in open("gradle.properties") if line.startswith("minecraft_version"))
        jar = os.environ.get("MINECRAFT_JAR") or os.path.expanduser(f"~/.gradle/caches/fabric-loom/{version}/minecraft-client.jar")
        with zipfile.ZipFile(jar) as archive:
            _TEXTURES[name] = Image.open(io.BytesIO(archive.read(f"assets/minecraft/textures/{name}"))).convert("RGBA")
    return _TEXTURES[name]


def luminance(color):
    r, g, b = color[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def shades(name):
    """Every opaque colour in a texture, darkest first."""
    image = vanilla(name)
    found = {image.getpixel((x, y))[:3] for y in range(image.height) for x in range(image.width) if image.getpixel((x, y))[3]}
    return sorted(found, key=luminance)


def grain(name, x, y):
    """How light the texture is at a spot, 0 to 1 among its own shades."""
    palette = shades(name)
    pixel = vanilla(name).getpixel((x % 16, y % 16))[:3]
    return palette.index(pixel) / max(1, len(palette) - 1)


class Material:
    """A band of an item's shades, the place in the band read from a block's grain."""

    def __init__(self, palette, low, high, noise, darken=1.0):
        self.palette, self.low, self.high, self.noise, self.darken = palette, low, high, noise, darken

    def at(self, x, y):
        colors = shades(self.palette)
        t = self.low + (self.high - self.low) * grain(self.noise, x, y)
        r, g, b = colors[round(t * (len(colors) - 1))]
        return (int(r * self.darken), int(g * self.darken), int(b * self.darken), 255)


def sprite(rows, materials, dy=0):
    assert len(rows) == 16 and all(len(row) == 16 for row in rows), "a sprite is 16 rows of 16"
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != "." and 0 <= y + dy < 16:
                image.putpixel((x, y + dy), materials[key].at(x, y))
    return image


OBSIDIAN = "block/obsidian.png"
COPPER = "item/copper_ingot.png"
AMETHYST = "item/amethyst_shard.png"
REDSTONE = "item/redstone.png"
INK = "item/ink_sac.png"
IRON = "item/iron_ingot.png"
PAPER = "item/paper.png"

CAMERA_MATERIALS = {
    "o": Material(OBSIDIAN, 0, 0, OBSIDIAN, darken=0.7),
    "D": Material(OBSIDIAN, 0.0, 0.35, OBSIDIAN),
    "O": Material(OBSIDIAN, 0.15, 0.7, OBSIDIAN),
    "P": Material(OBSIDIAN, 0.55, 1.0, OBSIDIAN),
    "k": Material(COPPER, 0, 0, "block/copper_block.png"),
    "d": Material(COPPER, 0.15, 0.4, "block/copper_block.png"),
    "c": Material(COPPER, 0.6, 0.85, "block/copper_block.png"),
    "C": Material(COPPER, 0.9, 1.0, "block/copper_block.png"),
    "m": Material(AMETHYST, 0.1, 0.3, "block/amethyst_block.png"),
    "a": Material(AMETHYST, 0.35, 0.6, "block/amethyst_block.png"),
    "A": Material(AMETHYST, 0.65, 0.85, "block/amethyst_block.png"),
    "W": Material(AMETHYST, 1, 1, "block/amethyst_block.png"),
    "q": Material(REDSTONE, 0, 0, "block/redstone_block.png"),
    "r": Material(REDSTONE, 0.6, 0.8, "block/redstone_block.png"),
    "R": Material(REDSTONE, 0.9, 1.0, "block/redstone_block.png"),
}


def camera():
    """An obsidian box camera from the front: copper viewfinder and redstone shutter on top, an amethyst lens in a copper ring."""
    return sprite([
        "................",
        "..kkkk....qqqq..",
        "..kCCk....qRRq..",
        "..kccd....qrrq..",
        ".oooooooooooooo.",
        "oPPPPPkkkkPPPPDo",
        "oPOOOkCCcckOOODo",
        "oPOOkCAWAadkOODo",
        "oPOOkcAAaadkOODo",
        "oPOOkcaaamdkOODo",
        "oPOOkcammmdkOODo",
        "oPOOOkddddkOOODo",
        "oPOOOOkkkkOOODDo",
        "oDDDDDDDDDDDDDDo",
        ".oooooooooooooo.",
        "................",
    ], CAMERA_MATERIALS)


def film_materials(label, stripe):
    return {
        "o": Material(INK, 0, 0, "block/black_wool.png"),
        "B": Material(INK, 0.25, 0.6, "block/black_wool.png"),
        "H": Material(IRON, 0.6, 1.0, "block/iron_block.png"),
        "G": Material(IRON, 0.2, 0.5, "block/iron_block.png"),
        "L": label,
        "I": stripe,
        "F": Material(INK, 0.1, 0.4, "block/black_wool.png"),
        "h": Material(PAPER, 0.7, 1.0, "block/white_wool.png"),
    }


FILM_ROWS = [
    "................",
    "................",
    "...oooooooooo...",
    "..oHHHHHHHHHHo..",
    "..oGGGGGGGGGGo..",
    "...oooooooooo...",
    "...oBLLLLLLBoooo",
    "...oBLLLLLLBoFFF",
    "...oBIIIIIIBohFh",
    "...oBLLLLLLBoFFF",
    "...oBLLLLLLBohFh",
    "...oBBBBBBBBoooo",
    "...oooooooooo...",
    "..oGGGGGGGGGGo..",
    "...oooooooooo...",
    "................",
]


def film():
    """A roll of film in its can, a paper label inked round the middle, the leader out to the right."""
    return sprite(FILM_ROWS, film_materials(Material(PAPER, 0.7, 1.0, "block/white_wool.png"),
                                            Material(INK, 0.0, 0.25, "block/black_wool.png")))


def exposed_film():
    """The same can wound shut, its label reddened: a picture in it, waiting for the darkroom."""
    rows = [row[:12] + "...." if 6 <= y <= 11 else row for y, row in enumerate(FILM_ROWS)]
    return sprite(rows, film_materials(Material(REDSTONE, 0.65, 1.0, "block/redstone_block.png"),
                                       Material(REDSTONE, 0.2, 0.4, "block/redstone_block.png")))


def photograph():
    """Vanilla's painting with its hanger off, the wooden frame turned to a paper print's border and
    the canvas to a hill under a summer sky, the painting's own vignette kept over it."""
    painting = vanilla("item/painting.png")
    frame = shades("item/painting.png")
    # The painting's shades by what they are: canvas and mat lightest to darkest, then frame wood
    mat = {(200, 197, 189): 1.0, (179, 174, 159): 0.93, (158, 150, 139): 0.85, (150, 138, 120): 0.75}
    wood = [(177, 159, 104), (147, 114, 74), (120, 68, 15), (99, 60, 21)]
    border = Material(PAPER, 0.5, 1.0, "block/white_wool.png")
    border_dark = Material(PAPER, 0.15, 0.45, "block/white_wool.png")
    sky = Material("block/light_blue_concrete_powder.png", 0, 1, "block/light_blue_concrete_powder.png")
    hill = Material("block/moss_block.png", 0, 1, "block/moss_block.png")
    sun = Material("block/gold_block.png", 0.6, 1, "block/gold_block.png")
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(4, 15):
        for x in range(16):
            pixel = painting.getpixel((x, y))
            if not pixel[3]:
                continue
            color = pixel[:3]
            if color in mat:
                if x in (10, 11) and y in (6, 7):
                    base = sun.at(x, y)
                elif y >= 9 + abs(x - 6) // 3:
                    base = hill.at(x, y)
                else:
                    base = sky.at(x, y)
                out = tuple(int(c * mat[color]) for c in base[:3]) + (255,)
            elif color in wood:
                out = (border if wood.index(color) < 2 else border_dark).at(x, y)
            else:
                out = (40, 36, 30, 255)
            image.putpixel((x, y - 2), out)
    assert set(mat) | set(wood) <= set(frame), "vanilla's painting has changed its shades"
    return image


def outlined(image, color):
    """The sprite with a one-pixel rim round it, so it stands off whatever it is laid on."""
    out = image.copy()
    for y in range(16):
        for x in range(16):
            if image.getpixel((x, y))[3]:
                continue
            near = [(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if any(0 <= nx < 16 and 0 <= ny < 16 and image.getpixel((nx, ny))[3] for nx, ny in near):
                out.putpixel((x, y), color + (255,))
    return out


def icon():
    """The camera at 6x against a lit redstone lamp, the only light film is safe under, with lamp
    showing all round it. Rimmed so its dark obsidian does not sink into the lamp's dark lines."""
    lamp = vanilla("block/redstone_lamp_on.png").resize((128, 128), Image.NEAREST)
    lamp.alpha_composite(outlined(camera(), (24, 6, 4)).resize((96, 96), Image.NEAREST), (16, 13))
    return lamp


camera().save(f"{OUT}/textures/item/camera.png")
film().save(f"{OUT}/textures/item/film.png")
exposed_film().save(f"{OUT}/textures/item/exposed_film.png")
photograph().save(f"{OUT}/textures/item/photograph.png")
icon().save(f"{OUT}/icon.png")
