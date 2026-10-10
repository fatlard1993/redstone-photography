# Redstone Photography - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `Main.java` | Registration, Pandorical content sync, and the events: using a cauldron, hitting a photograph, tracking frames and developing film |
| `CameraItem.java` | Taking a picture: asks the shooter's client through Pandorical's `capture()`, then spends a film and saves the file |
| `Film.java` | Exposed film as an item: the picture's id and who took it |
| `Photos.java` | The picture files in the world folder, and printing one onto maps: scaled, then dithered into map colours |
| `Darkroom.java` | Whether a cauldron stands in a darkroom: no sky light, and only safelights reaching it |
| `Developing.java` | Film floating in a cauldron as it develops, checked every second, and the photograph it becomes |
| `Prints.java` | Photographs as items and on walls: hanging, fitting, taking down |
| `PhotographItem.java` | The photograph item, hung on the wall it is used against |
| `mixin/ItemFrameAccessor.java` | Setting an item frame fixed, which vanilla only does from saved data |
| `generate_textures.py` | Draws the item sprites and the mod icon |

## Taking a picture

The camera asks for a 512 square through `PandoricalApi.capture()`, and the shooter's client takes it: it hides the HUD as F1 does, waits for a frame drawn without it, reads that frame back as F2 does, crops the middle square and sends it as a PNG in slices. The film is spent only when the picture arrives, so a declined or lost capture costs nothing. The file is kept at `<world>/redstone-photography/<id>.png`, and the exposed film carries the id.

## Darkroom

Light is followed back from the block above the cauldron the way block light spreads: a level lost each block, more through what dims it, and stopped by what it cannot pass (`getLightDampening` of 15). Any block found whose light would still be above nought on arriving, and which is not in `redstone-photography:safelights`, spoils the room. Sky light is read directly; the room must also have some block light, which by then can only be redstone light.

## Developing

A film in a cauldron is an item display tagged `redstone-photography.developing`, floating at the water's surface and holding the film with the game time it went in. Displays persist, so developing film survives a restart. Each one loaded is checked once a second: the cauldron gone or emptied gives the film back; daylight or any light but a safelight reaching it loses the film (the room going dark does not); ten seconds in, the photo is printed onto four fresh locked maps and the photograph pops out.

## Printing

A print is 256 map pixels square, two maps by two. The picture is box-averaged down to that and Floyd-Steinberg dithered onto every colour a map can show (each base colour at all four brightnesses), matched by redmean distance.

## On a wall

As MC Paint hangs a painting: one invisible, fixed item frame per block, each holding its map, tagged `redstone-photography.photograph` and sharing a group id in the map's custom data, which is how hitting any one of them finds the rest. A server tick every second checks each tracked photograph and takes down any whose wall has gone.

## Testing

`src/gametest` takes a picture through a real client, end to end: the camera clicked, the file checked, a darkroom built and a torch in it caught, the film developed and the print hung. Its last frame, `build/run/clientGameTest/screenshots/photograph-on-wall.png`, is the readme's picture; copy it to `screenshots/` when it changes. Run it under xvfb:

```
xvfb-run -a -s "-screen 0 2048x1152x24" ./gradlew runClientGameTest
```

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
