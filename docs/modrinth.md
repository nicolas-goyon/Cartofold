# Cartofold

One map engine for all the maps of the game. The name comes from *Kartoffel*: the German word for potato.

Cartofold is a map mod for Minecraft 1.21.1 (NeoForge). It needs no other mod. Install it on the server and on the client: the server records the exploration, and each player gets only the chunks that this player explored.

## Features

- **Minimap**: square or round, five zoom levels, the north, the arrow of the player and the coordinates. It turns with the player or keeps the north at the top.
- **Map of the world** (key M): move, zoom around the mouse, center on the player, the coordinates under the mouse.
- **Caves**: in a cave, the minimap and the map of the world show the caves at the height of the player, in bands of 16 blocks.
- **Waypoints**: a name, a color and a position. A death adds a waypoint.
- **Banners**: a banner that a player marks on a vanilla map or on a survey map also shows on the minimap and on the map of the world of this player.
- **Survey maps**: paper maps of their own, with the layers of the other mods. The cartography table zooms, copies, locks, moves and updates them. Banners and lodestones mark them.
- **Settings**: the settings screen of the client, in English and in French.

## Keys

| Key | Action |
| --- | --- |
| M | Open the map of the world |
| Y | Show or hide the minimap |
| `[` and `]` | Zoom out and zoom in the minimap |
| B | Add a waypoint at the player |

## For the authors of mods

Other mods add layers to all the maps (territories, routes, markers with a color and a click), show map frames in their own screens, open the map of the world at a position, and mark their blocks on the survey maps.

The Maven of Modrinth gives the jar, the sources jar and the Javadoc jar with no token. The API is in [docs/api.md](https://github.com/nicolas-goyon/Cartofold/blob/develop/docs/api.md).

## Versions

- **Release**: the stable versions.
- **Alpha**: a build of each change of the branch `develop`. The version has the form `0.23.0-dev.abc1234`. Do not use an alpha in a world that you want to keep.

The source code is on [GitHub](https://github.com/nicolas-goyon/Cartofold), under the MIT license.
