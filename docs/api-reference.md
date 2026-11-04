# API reference

This page lists the public API of Cartofold. The [API guide](api.md) explains how to add Cartofold to a mod, and the [examples](api-examples.md) show complete code. The Javadoc jar of each version has the same documentation in the IDE.

The API is in the packages `io.github.nicolasgoyon.cartofold.api` (server and common) and `io.github.nicolasgoyon.cartofold.api.client` (client). `MapProjection` and `MapSlice` are also part of the API, because the API gives or takes them. The other packages can change in a minor version.

Colors: an ARGB color has 8 bits of alpha. An RGB color has no alpha.

## Layers on the server

### `CartofoldLayers`

Static methods. Call them on the server thread, after the start of the server. With no server, they throw `IllegalStateException`.

| Method | Description |
| --- | --- |
| `setForAll(LayerContent content)` | Sets a layer for all the players. The players that join later get it. |
| `updateForAll(ResourceLocation layer, LayerUpdate update)` | Changes some elements of a layer of `setForAll`. A player with a layer of its own with this identifier does not get the change. |
| `removeForAll(ResourceLocation layer)` | Removes a layer of `setForAll`. |
| `set(ServerPlayer player, LayerContent content)` | Sets a layer for one player, until the player leaves. It replaces a layer for all the players with the same identifier, for this player. |
| `update(ServerPlayer player, ResourceLocation layer, LayerUpdate update)` | Changes some elements of a layer of a player. |
| `remove(ServerPlayer player, ResourceLocation layer)` | Removes a layer of a player. |

A change of a layer that does not exist does nothing. A change that goes over the limits throws `IllegalArgumentException`.

### `LayerInfo`

The description of a layer. A record.

| Field | Type | Description |
| --- | --- | --- |
| `id` | `ResourceLocation` | The identifier, in the namespace of the mod. |
| `name` | `Component` | The name in the settings of the player. |
| `order` | `int` | A layer with a higher order draws above the others. |
| `views` | `Set<MapViewKind>` | The kinds of views that show the layer. |
| `onlyExplored` | `boolean` | True to show the layer only on the chunks that the player explored. |

`LayerInfo.of(id, name, order, onlyExplored)` makes a layer for all the kinds of views.

### `MapViewKind`

An enum: `MINIMAP`, `WORLD_MAP`, `PAPER`, `FRAME`. `MapViewKind.all()` gives all the kinds.

### `LayerContent`

All the content of a layer. A record.

| Member | Description |
| --- | --- |
| `new LayerContent(LayerInfo info, List<ChunkArea> areas, List<MapLine> lines, List<MapMarker> markers)` | Makes a content. |
| `apply(LayerUpdate update)` | Gives the content after a change. |
| `marker(String id)` | Gives the marker with this identifier, as an `Optional`. |
| `MAX_ELEMENTS` | 4096: the largest number of areas, of lines and of markers. |

### `LayerUpdate`

A change of some elements of a layer. An element with a new identifier goes into the layer. An element with a known identifier replaces the old one and keeps its place. The removals come before the changes.

| Factory | Description |
| --- | --- |
| `marker(MapMarker marker)` | Adds or changes one marker. |
| `markers(List<MapMarker> markers)` | Adds or changes markers. |
| `removeMarker(String id)` | Removes one marker. |
| `area(ChunkArea area)` | Adds or changes one area. |
| `removeArea(String id)` | Removes one area. |
| `line(MapLine line)` | Adds or changes one line. |
| `removeLine(String id)` | Removes one line. |

`new LayerUpdate(areas, lines, markers, removedAreas, removedLines, removedMarkers)` changes all the kinds in one message. The last three lists have identifiers.

### `ChunkArea`

An area of chunks, for example a territory.

```java
ChunkArea.of(id, dimension, chunks, fillColor, borderColor, name)
ChunkArea.of(id, dimension, chunks, fillColor, borderColor, name, tooltip)
```

| Parameter | Type | Description |
| --- | --- | --- |
| `id` | `String` | The identifier in the layer. |
| `dimension` | `ResourceKey<Level>` | The dimension of the chunks. |
| `chunks` | `Collection<ChunkPos>` | The chunks, 65 536 at most. |
| `fillColor` | `int` | The fill, in ARGB. An alpha of 0 gives no fill. |
| `borderColor` | `int` | The border, in ARGB. An alpha of 0 gives no border. |
| `name` | `Component` | The name of the area. |
| `tooltip` | `List<Component>` | The lines above the area, 16 at most. With no line, the area has no tooltip. |

The getters have the same names. `contains(chunkX, chunkZ)` tells if the area has a chunk.

### `MapLine`

A line through points, for example a route. A record.

| Field | Type | Description |
| --- | --- | --- |
| `id` | `String` | The identifier in the layer. |
| `dimension` | `ResourceKey<Level>` | The dimension of the points. |
| `points` | `List<BlockPos>` | The points, 4096 at most. The views use the X and the Z. |
| `color` | `int` | The color, in ARGB. |
| `width` | `float` | The width, in units of the screen. |

### `MapMarker`

A marker at a position. A record.

| Field | Type | Description |
| --- | --- | --- |
| `id` | `String` | The identifier in the layer. |
| `dimension` | `ResourceKey<Level>` | The dimension of the position. |
| `pos` | `BlockPos` | The position. |
| `color` | `int` | The color, in RGB: the diamond of a marker with no icon, or a tinted icon. |
| `icon` | `Optional<ResourceLocation>` | The texture of a square icon, or empty for a diamond. |
| `name` | `Component` | The name under the marker. An empty text shows no name. |
| `tooltip` | `Optional<Component>` | The text under the mouse. |
| `clickable` | `boolean` | True to post `MarkerClickEvent` on the server after a click. |
| `tinted` | `boolean` | True to multiply the pixels of the icon by the color. |

| Factory | Description |
| --- | --- |
| `MapMarker.of(id, dimension, pos, color, name)` | A diamond with no tooltip and no click. |
| `MapMarker.tinted(id, dimension, pos, color, icon, name)` | An icon in the color of the marker, with no tooltip and no click. |

A constructor without `tinted` keeps the colors of the icon.

### `MarkerShapes`

White textures with a dark outline, for tinted markers: `SQUARE`, `RING`, `CROSS`, `DIAMOND`, `DOT`.

### `MarkerClickEvent`

Cartofold posts this event on `NeoForge.EVENT_BUS`, on the server thread. It comes only for a clickable marker that the server sent to this player.

| Method | Type |
| --- | --- |
| `getPlayer()` | `ServerPlayer` |
| `getLayer()` | `ResourceLocation` |
| `getMarker()` | `MapMarker` |

## Survey maps

### `PaperMapMarkers`

`PaperMapMarkers.register(PaperMarkerSource source)` adds a source of waypoints. Call it once, in the setup of the mod. A source with the identifier of another source replaces it.

### `PaperMarkerSource`

A kind of block that marks the survey maps. A record.

| Field | Type | Description |
| --- | --- | --- |
| `id` | `ResourceLocation` | The identifier of the source. |
| `blocks` | `Predicate<BlockState>` | The blocks of the source. |
| `marker` | `MarkerFunction` | Gives the marker of a block, or empty when the block marks nothing now. |

`PaperMarkerSource.of(id, blocks, type, name)` makes a source with one icon, and a `NameFunction` for the name of each block.

| Type | Method |
| --- | --- |
| `MarkerFunction` | `Optional<Marker> marker(ServerLevel level, BlockPos pos)` |
| `NameFunction` | `Optional<Component> name(ServerLevel level, BlockPos pos)` |
| `Marker` | A record: `Holder<MapDecorationType> type`, `Component name`. |

The server calls these functions on the server thread, with the chunk loaded. `CartofoldMapDecorations.WAYPOINT` is the pin of Cartofold.

## On the client

### `MapFrame`

A widget for the screens of the mods. Add it with `addRenderableWidget`. The methods that give a `MapFrame` return the same frame, so the calls can follow each other.

```java
new MapFrame(int x, int y, int width, int height)
```

**View**

| Method | Description |
| --- | --- |
| `center(double blockX, double blockZ)` | Puts a block at the center. The frame stops to follow the player. |
| `scale(double scale)` | Units of the screen for one block, from 1/8 to 8. |
| `view()` | Gives the center and the scale, as a `MapFrame.View`. |
| `view(MapFrame.View view)` | Sets the center and the scale. |
| `showBox(double minX, double minZ, double maxX, double maxZ)` | Selects the view that shows all of a box of blocks. |
| `followPlayer(boolean follow)` | Puts the player at the center until a move of the mouse. |
| `dimension(ResourceKey<Level> dimension)` | Shows a dimension. By default, the dimension of the player. |
| `slice(MapSlice slice)` | Shows the surface or a band of caves. |

**Content**

| Method | Description |
| --- | --- |
| `layers(Predicate<ResourceLocation> filter)` | Shows only the layers that the filter accepts. |
| `showPlayer(boolean show)` | Shows the arrow of the player. On by default. |
| `showWaypoints(boolean show)` | Shows the waypoints. On by default. |
| `backgroundColor(int color)` | The color where the map has no chunk, in ARGB. |
| `background(Consumer<LayerRenderContext> renderer)` | Draws below the chunks and the layers. |
| `allChunks(boolean all)` | Shows the chunks of all the players. The server accepts it from an operator (permission level 2). |
| `select(ResourceLocation layer, String marker)` | Shows a marker as selected. |
| `clearSelection()` | Shows no marker as selected. |

**Input**

| Method | Description |
| --- | --- |
| `interactive(boolean interactive)` | Allows the moves, the zoom and the clicks. On by default. |
| `onMarkerClick(MarkerClickListener listener)` | Gets `(ResourceLocation layer, String marker)` after a click on a marker, on the client. |
| `onEmptyClick(Runnable listener)` | Gets the clicks that hit no marker. |
| `onViewChange(Consumer<MapFrame.View> listener)` | Gets the view after a move or a zoom of the player. |

**Other**

| Method | Description |
| --- | --- |
| `projection()` | Gives the `MapProjection` of the frame. |
| `shownDimension()` | Gives the dimension of the frame, or null with no player. |

`MapFrame.View` is a record: `centerX`, `centerZ`, `scale`.

### `CartofoldWorldMap`

Opens the map of the world at a position.

| Method | Description |
| --- | --- |
| `CartofoldWorldMap.at(ResourceKey<Level> dimension, double blockX, double blockZ)` | Starts a request. |
| `scale(double value)` | The scale. By default, the scale of the last map. |
| `parent(Screen screen)` | The screen to show when the map closes. |
| `select(ResourceLocation layer, String marker)` | Shows a marker as selected. |
| `open()` | Shows the map. |

### `CartofoldClientLayers`

`CartofoldClientLayers.register(MapLayerRenderer renderer)` adds a layer that the client draws. Call it once, in the setup of the client.

### `MapLayerRenderer`

| Method | Description |
| --- | --- |
| `LayerInfo info()` | The description of the layer. |
| `void render(LayerRenderContext context)` | Draws the layer, on the render thread. |

### `LayerRenderContext`

A record: `GuiGraphics graphics`, `MapProjection projection`, `ResourceKey<Level> dimension`, `MapViewKind kind`.

### `MapProjection`

The conversion between the blocks and the points of the screen, in `io.github.nicolasgoyon.cartofold.view`.

| Method | Description |
| --- | --- |
| `toScreen(double blockX, double blockZ)` | Gives `{x, y}` on the screen. |
| `toBlock(double screenX, double screenY)` | Gives `{x, z}` in the world. |
| `contains(double screenX, double screenY)` | Tells if a point is in the rectangle of the view. |
| `blockBounds()` | Gives `{minX, minZ, maxX, maxZ}`: the box of blocks of the view. |
| `scale()`, `rotation()`, `centerX()`, `centerZ()` | The fields of the view. |

### `MapSlice`

A slice of the world, in `io.github.nicolasgoyon.cartofold.data`.

| Member | Description |
| --- | --- |
| `MapSlice.SURFACE` | The surface. |
| `MapSlice.caveAt(int y)` | The band of 16 blocks at a height. |
| `surface()` | Tells if the slice is the surface. |
| `minY()`, `maxY()` | The limits of the band. |
