# Redstone Photography

A server-side Fabric mod for taking photographs: craft a camera and film, take a picture of exactly what you see, develop the film in a cauldron in a darkroom lit only by redstone, and hang the print on a wall.

![A photograph of a row of coloured wool hung on a stone brick wall, two blocks by two](screenshots/photograph-on-wall.png)

## Taking a picture

1. **Craft a camera** from obsidian, copper, an amethyst lens, a button and redstone, and **film** from paper and ink. The recipes are below.
2. **Right-click with the camera.** It takes a picture of what is on your screen, without your hotbar, hand or crosshair, as a square from the middle of the view. Each picture uses one film from your inventory, your off hand first, and gives you an **Exposed Film** marked with who took it. A creative player needs no film.

The camera won't take a picture while a screen is open over the world; close it and try again.

## Developing

Exposed film is developed in a **water cauldron** in a **darkroom**:

- **No daylight.** The room has to be closed: no sky light may reach the cauldron, through a window, an open door or a gap in the roof.
- **Only redstone light.** The room has to be lit, and every light that reaches the cauldron has to be redstone light: a lit redstone lamp, a redstone torch, or glowing redstone ore. A torch, a lantern, glowstone, lava or anything else that shines into the room spoils it. Light behind a solid wall doesn't count; light round the corner of an open doorway does.

Right-click the cauldron with the exposed film. If the room isn't a darkroom you are told why and keep the film. If it is, the film floats on the water and develops for **ten seconds**. Keep other light out while it does: if daylight or any light but redstone reaches the cauldron before it is done, the film is fogged and lost. The redstone light going off does no harm.

When it is done the photograph pops out of the cauldron, and the water drops a level. Right-click the cauldron with an empty hand while it develops to take the film back out, undeveloped.

## Hanging

Right-click a wall with a photograph. A photograph is two blocks by two, centred on the block you click, and needs a flat wall its full size with open space in front. Hit any part of it to take the whole thing down; as with vanilla paintings, a creative player gets nothing back. If the wall behind it goes, the photograph comes down.

Photographs are drawn on maps, so a print is the picture in the colours a map can show, dithered to keep the shading.

## On the server

Every picture taken is kept as a PNG, 512 pixels square, in the world folder under `redstone-photography/`, named by the picture's id. A film carries only that id until it is developed, so a picture can't be developed once its file has gone.

The picture comes from the photographer's own client, so a modified client can send any picture it likes as a photograph.

The safelights are the block tag `redstone-photography:safelights`: redstone lamps, redstone torches and redstone ore. A datapack can add to it.

## Recipes

- **Camera**: obsidian, a stone button and a copper ingot along the top; redstone, an amethyst shard and obsidian below.
- **Film** (4): two paper and an ink sac.

## Pandorical

Redstone Photography runs server-side, and Pandorical is required: the server will not load this mod without it. Pandorical syncs the items to connecting clients, and its client takes the pictures.

Clients need Pandorical to take pictures. Photographs on walls are maps in invisible item frames, which every client already knows how to draw.

## Development

Installing is in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
