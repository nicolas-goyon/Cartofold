# Cartofold design

Cartofold is a map mod for Minecraft 1.21.1 (NeoForge). It gives one map engine to all the maps of the game: the minimap, the map of the world (key M), the paper maps and the map frames of the other mods. The other mods add layers to all these maps with an API.

## Principles

- One engine: all the views use the same data, the same tiles and the same projection.
- The server owns the data: the server records the exploration and sends it to the clients. Cartofold is necessary on the server and on the client.
- No dependency: Cartofold does not need another mod. The other mods depend on Cartofold, as a required or an optional dependency.
- No change of the vanilla textures: the views draw their own textures. Thus the mods that change the vanilla maps (for example the atlas of the maps of ImmediatelyFast) do not stop Cartofold.
- No generation of chunks: the exploration reads only the chunks that the server has.

## Map engine

### Exploration data

- The server records the map colors of the chunks near each player, as the vanilla maps do: the map color of the top block of each column, the depth of the water and the height for the shading.
- The data is for each player. A later feature can share the exploration in a team.
- The storage: regions of 512 x 512 blocks, one folder for each dimension, in the folder of the world (`data/cartofold/`). A region file has the colors and the heights of its explored columns, and a mask of the explored columns.
- Every second, the loaded chunks near each player (the view distance of the server, 12 chunks at most) go to its mask. A chunk with no sample, or with a sample older than 2 minutes, goes to a queue. The server thread reads 8 chunks of the queue at most each tick. The IO threads of the game write the files at each save of the world.
- A region or a mask with no use for 5 minutes goes out of memory, after its save.
- The server sends to each client the chunks that the player explored in the regions near the player, and in the regions that a view asks for (the map of the world). After the first send, it sends only the changed chunks. The map data of the chunks that the player did not explore stays on the server.
- The client keeps 160 regions at most (about 1 MiB each). The regions of the last request stay; the other regions go out, the oldest change first. The server sends a region again when a request has it again.
- In a dimension with a ceiling (the Nether), the map shows the floor under the ceiling: the sampler goes down from the top of the logical height through the ceiling to the air, then to the first block with a color. The caves of the Overworld have no view of their own.

### Tiles

- The client makes the textures from the regions: one texture for each region, and textures with less detail for the small scales.
- A change of a region updates only its textures. A cache keeps the textures near the views and frees the others.

### Map view

- A view draws the tiles in a rectangle of the screen, with a center, a scale and a rotation. The projection gives the pixel of a block and the block of a pixel.
- The view then draws the layers, in their order, and the markers.
- The minimap, the map of the world, the paper maps and the map frames use the same view.

## Views

- **Minimap**: on the screen, square or round, with zoom levels, the north, the player arrow and the coordinates. The north is at the top, or the map turns with the player. The key Y shows and hides it, and the keys [ and ] change the zoom. The player selects the corner of the screen and the size. The settings are in `config/cartofold-client.toml`.
- **Map of the world** (key M): move with the mouse and the arrow keys, zoom with the wheel (1 unit of the screen for 8 blocks to 8 units for one block), the space key centers the map on the player, the coordinates under the mouse. The map follows the player until the player moves it. The client asks the server for the regions of the map (128 regions at most, with the regions near the player).
- **Waypoints of the player**: a name, a color, a position. The server keeps them (`data/cartofold/waypoints/<uuid>.json`, 256 at most) and sends them to the client. The key B adds a waypoint at the player, and a right click on the map of the world adds or changes a waypoint. The minimap and the map of the world show them: a waypoint out of the minimap stays on its side. A death adds a waypoint, and the 3 last deaths stay.
- **Settings of the client**: the minimap, the map of the world and the visibility of the layers, in `config/cartofold-client.toml`. The settings screen opens from the list of the mods and from the button of the map of the world.

## Layer API

- A layer draws on all the views: fills and borders of chunks (for example territories), lines (for example routes), markers with an icon, a name, a tooltip and a click.
- Each layer has an order and a visibility for each kind of view (minimap, map of the world, paper maps, frames). The player can hide a layer with the "Layers" button of the map of the world. A layer can show its parts only on the explored chunks.
- A mod of the server gives the content of its layer to the clients with `CartofoldLayers`: for all the players (the players that join later also get it), or for one player. A new content replaces the old content. The client keeps the layers until it leaves the world.
- A click on a clickable marker posts a `MarkerClickEvent` on the server, only for a marker that the server sent to this player.
- A mod of the client can also draw a layer itself, with `CartofoldClientLayers` and a `MapLayerRenderer`.
- A **map frame** (`MapFrame`) is a widget for the screens of the other mods: a map view with a selection of layers, a center and a scale, the player and the waypoints. It can follow the player, and the mouse can move it, zoom it and click its markers. For example the journal and the admin screen of Folkhold. Each open view (the map of the world, each frame) asks the server for its regions.
- The API is in the packages `api` and `api.client`, with Javadoc and the guide [api.md](api.md). Each release puts the jar, the sources jar and the Javadoc jar in GitHub Packages.

## Paper maps

- The paper maps of Cartofold are items of their own: the **empty survey map** and the **survey map**. The vanilla maps stay vanilla: Cartofold does not change them, so it does not compete with the other mods that change the vanilla maps. The mods that interact with the survey maps use the API of Cartofold.
- Recipe of the empty survey map: a compass in the middle, paper on the sides and ink sacs in the corners. A use gives a survey map around the player, with the center on the grid of the vanilla maps.
- A survey map has 128 x 128 pixels, a center, a scale and markers, as a vanilla map. The storage of the Overworld keeps its data (`cartofold_survey_map_<id>`).
- In the hand of a player, the pixels within 128 blocks get the colors of the exploration data of Cartofold, every 10 ticks. A spyglass in the inventory makes the radius 3 times larger. The update never loads or generates a chunk: a loaded chunk with no data goes to the queue of the samples.
- The client draws the pixels and the layers in one texture, then the markers and the player, with the poses of the vanilla maps in the hand and in the item frames. The layers show only on the explored pixels.
- **Markers of blocks**: a player marks a place on a survey map from a block, as with a banner on a vanilla map: a use of the map on the block adds a marker with an icon and a name, and a second use removes it. The banners (with their colors and their names) and the lodestone (the pin `cartofold:waypoint`) are sources of Cartofold. Another mod adds its own blocks with `PaperMapMarkers` (for example the heart of a village of Folkhold). A marker of a block that changes goes away from the map.
- **Cartography table**: the table works with the survey maps as the vanilla table with the vanilla maps, and shows their preview. A paper zooms a map out, a glass pane locks a copy, and an empty survey map gives a copy. With a survey map and a paper, five buttons of the screen select:
  - a move of the map by half of its side to the north, the south, the west or the east (the game puts the center of a map on a fixed grid). The moved map is a new map with the same scale and the explored pixels in their new places;
  - an update of the map at a distance: when the player takes the map, the server paints its pixels from the chunks that are already generated, 1024 chunks at most (the chunks with unexplored pixels first) and 4 chunks each tick. The paper is the cost.
  - The same button again selects the zoom. A locked map does not zoom, move, update or lock again. The buttons show only with a survey map: the vanilla maps keep the vanilla table.
- The server sends to a player the survey maps of its inventory, of its open menu and of the item frames within 64 blocks.

## Compatibility

- ImmediatelyFast and Moonlight change the rendering of the vanilla maps. Cartofold draws its own textures (the tiles of its views and the survey maps) and does not change the vanilla maps.
- Xaero's Minimap and Xaero's World Map can stay with Cartofold. The modpack of Folkhold uses Cartofold in their place when the minimap and the map of the world of Cartofold are ready.

## Roadmap

| Milestone | Scope |
| --- | --- |
| M0 · Foundations | The Gradle project, the CI, the conventions, this document, the release workflow |
| M1 · Map engine | The exploration data on the server, the tiles, the map view |
| M2 · Minimap and world map | The minimap, the map of the world, the waypoints of the player, the settings |
| M3 · Layer API | The layers of the other mods, the map frames, the publication of the API |
| M4 · Paper maps | The paper maps, their waypoints, the spyglass, the cartography table |
