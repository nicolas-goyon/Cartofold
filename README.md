# Cartofold

Cartofold is a map mod for Minecraft (NeoForge 1.21.1). It gives one map engine to all the maps of the game:

- a minimap on the screen, and a map of the explored world (key M);
- survey maps: paper maps of their own, with the layers of the other mods, waypoints and a cartography table that moves and updates them;
- an API for the other mods: they add layers to all the maps (for example territories, routes, markers) and show a map frame in their own screens.

The mod does not depend on other mods. It is necessary on the server and on the client: the server records the exploration.

## Features

- **Map engine**: the server records the exploration of each player (the Overworld, the End, and the floor of the Nether under its ceiling) and sends to each client only the chunks that the player explored.
- **Minimap**: square or round, five zoom levels, the north, the arrow of the player and the coordinates. It turns with the player or keeps the north at the top.
- **Map of the world**: move, zoom around the mouse, center on the player, the coordinates under the mouse.
- **Caves**: in a cave, the minimap and the map of the world show the caves at the height of the player, in bands of 16 blocks. Page Up and Page Down select a band on the map of the world.
- **Waypoints of the player**: a name, a color and a position, on the minimap and on the map of the world. A death adds a waypoint.
- **Survey maps**: paper maps of their own (the vanilla maps stay vanilla), with the layers of the other mods. A spyglass in the inventory explores farther. The cartography table zooms, copies, locks, moves and updates them. Banners, lodestones and the blocks of other mods mark them.
- **API**: layers (areas of chunks, lines, markers with a click), map frames for the screens of other mods, and blocks that mark the paper maps.
- **Settings**: the settings screen of the client, in English and in French.

The design is in [docs/design.md](docs/design.md).

## Keys

| Key | Action |
| --- | --- |
| M | Open the map of the world |
| Y | Show or hide the minimap |
| `[` and `]` | Zoom out and zoom in the minimap |
| B | Add a waypoint at the player |

The settings screen opens from the list of the mods and from the map of the world.

## API

Other mods add layers to all the maps and show map frames in their own screens. See [docs/api.md](docs/api.md). Each release puts the jar in GitHub Packages.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md): the workflow, the build, the tests and the versions.

## License

[MIT](LICENSE)
