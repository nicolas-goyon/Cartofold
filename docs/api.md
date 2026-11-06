# API of Cartofold

Other mods use the API of Cartofold to add layers to all the maps (the minimap, the map of the world, the map frames and the paper maps), to show map frames in their own screens, and to mark their blocks on the survey maps.

This page is the guide. The [examples](api-examples.md) give complete code, and the [reference](api-reference.md) and the [Javadoc](https://nicolas-goyon.github.io/Cartofold/javadoc/io/github/nicolasgoyon/cartofold/api/package-summary.html) list all the methods and their parameters.

The API is in the packages `io.github.nicolasgoyon.cartofold.api` (server and common) and `io.github.nicolasgoyon.cartofold.api.client` (client). The class `io.github.nicolasgoyon.cartofold.view.MapProjection` is also part of the API. The other packages are not part of the API: they can change in a minor version. The Javadoc jar of each version has the documentation of the API.

## Add Cartofold to a mod

Each release puts the jar, the sources jar and the Javadoc jar on [Modrinth](https://modrinth.com/mod/cartofold). The Maven of Modrinth needs no account and no token. The jar is also a file of each GitHub Release.

In `build.gradle` (ModDevGradle):

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = 'Modrinth'
                url = uri('https://api.modrinth.com/maven')
            }
        }
        filter {
            includeGroup 'maven.modrinth'
        }
    }
}

dependencies {
    // A required dependency: the mod and the dev runs have Cartofold.
    implementation 'maven.modrinth:cartofold:0.23.4'
}
```

Each release also puts the jar in GitHub Packages, with the group `io.github.nicolasgoyon.cartofold` and the artifact `cartofold`. GitHub Packages needs a GitHub token with the permission `read:packages`, also for a public package.

Each push on `develop` also puts a dev build on Modrinth, in the channel `alpha`, for example `maven.modrinth:cartofold:0.23.4-dev.abc1234`. A dev build can change the API with no notice.

A modpack of packwiz adds Cartofold with `packwiz modrinth add cartofold`.

For an optional dependency, use `compileOnly` for the compilation, and `runtimeOnly` if the dev runs need Cartofold.

In `META-INF/neoforge.mods.toml`:

```toml
[[dependencies.mymod]]
modId = "cartofold"
type = "required" # or "optional"
versionRange = "[0.4,)"
ordering = "NONE"
side = "BOTH"
```

With an optional dependency, call the API only when Cartofold is loaded (`ModList.get().isLoaded("cartofold")`), and put the calls in a class that only this check loads. Then the game never loads the classes of Cartofold when Cartofold is not there.

## Layers from the server

A mod of the server sends the content of a layer with `CartofoldLayers`. A new content of a layer replaces its old content. The calls go on the server thread, after the start of the server.

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mymod", "territories");
// Order 10, on all the kinds of views, only on the chunks that the player explored.
LayerInfo info = LayerInfo.of(id, Component.translatable("mymod.layer.territories"), 10, true);

ChunkArea territory = ChunkArea.of("north_village", Level.OVERWORLD, chunks,
        0x4000FF00, // the fill, in ARGB
        0xFF00FF00, // the border, in ARGB
        Component.literal("North village"));
MapLine road = new MapLine("road_1", Level.OVERWORLD, List.of(start, middle, end), 0xFFE0C080, 2);
MapMarker heart = new MapMarker("north_village_heart", Level.OVERWORLD, heartPos, 0xFFAA00,
        Optional.empty(), Component.literal("North village"),
        Optional.of(Component.literal("Click to open the journal")), true);

LayerContent content = new LayerContent(info, List.of(territory), List.of(road), List.of(heart));

// For all the players: the players that join later also get the layer.
CartofoldLayers.setForAll(content);
// Or for one player: the mod sends the layer again when the player joins again.
CartofoldLayers.set(player, content);
```

An area can have a tooltip of one or more lines. The map of the world and the map frames show it when the mouse is above a chunk of the area. When a marker with a tooltip is also under the mouse, the map shows the tooltip of the marker.

```java
ChunkArea territory = ChunkArea.of("north_village", Level.OVERWORLD, chunks, 0x4000FF00, 0xFF00FF00,
        Component.literal("North village"),
        List.of(Component.literal("North village"), Component.literal("Red faction").withColor(0xFF4040)));
```

An area can also show only on the chunks that the player explored, with `area.withOnlyExplored(true)`. The other chunks of the area stay dark, as a fog of war, and the border goes around the explored part. The markers and the lines of the layer still show everywhere: `LayerInfo.onlyExplored` hides all the elements of a layer.

```java
ChunkArea territory = ChunkArea.of("north_village", Level.OVERWORLD, chunks, 0x4000FF00, 0xFF00FF00,
        Component.literal("North village")).withOnlyExplored(true);
```

- `LayerInfo.order`: a layer with a higher order draws above the layers with a lower order. The waypoints of the player are above all the layers.
- `LayerInfo.views`: the kinds of views that show the layer (`MapViewKind.MINIMAP`, `WORLD_MAP`, `PAPER`, `FRAME`).
- `LayerInfo.onlyExplored`: the fills, the borders, the lines and the markers show only on the chunks that the player explored. A border shows when one of its two sides is explored. With `false`, the layer also shows on the background of a map frame.
- A layer has 4096 areas, 4096 lines and 4096 markers at most. An area has 65 536 chunks at most, an area and a marker have 16 lines of tooltip at most, and a line has 4096 points at most.
- The player can hide a layer with the "Layers" button of the map of the world.

### Changes of some elements

A mod can change some elements of a layer, with no new copy of the layer. This is good for the markers that move often. The server sends only the change to the clients.

```java
MapMarker caravan = MapMarker.tinted("caravan_7", Level.OVERWORLD, pos, factionColor, MarkerShapes.DIAMOND,
        Component.literal("Caravan"));

// For the layer of setForAll.
CartofoldLayers.updateForAll(id, LayerUpdate.marker(caravan));
CartofoldLayers.updateForAll(id, LayerUpdate.removeMarker("caravan_7"));
// For the layer of one player.
CartofoldLayers.update(player, id, LayerUpdate.markers(caravans));
```

- An element with a new identifier goes into the layer. An element with the identifier of an element of the layer replaces it.
- `LayerUpdate.area`, `removeArea`, `line` and `removeLine` change the areas and the lines. The constructor of `LayerUpdate` changes elements of all the kinds in one step.
- A change of a layer that the server does not have does nothing. The limits of a layer stay the same.
- A mod can also put the moving markers in a layer of their own, and send this layer only. The player then shows and hides the two layers one by one.

### Names of the markers

The map of the world and the map frames show the name of a marker under the marker. A name does not cover a name that the map already drew: the highest layers and the waypoints come first. The other markers show with no name. When the player zooms in, the hidden names show again. The selected marker and the marker under the mouse always show their names. A marker with an empty name shows no name.

### Colors of the markers

A marker with a tinted icon shows the same icon in many colors, with no texture for each color. The views multiply the pixels of the icon by the color of the marker: a white pixel takes the color, and a dark outline stays dark.

```java
// A shape of Cartofold: SQUARE, RING, CROSS, DIAMOND or DOT.
MapMarker village = MapMarker.tinted("north_village", Level.OVERWORLD, pos, factionColor, MarkerShapes.SQUARE,
        Component.literal("North village"));
// A white icon of the mod.
ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("mymod", "textures/map/ruin.png");
MapMarker ruin = MapMarker.tinted("old_ruin", Level.OVERWORLD, pos, factionColor, icon, Component.literal("Ruin"));
```

The last argument of the constructor of `MapMarker` also selects a tinted icon. The icon has the same size in the minimap, the map of the world and the map frames. On a paper map, it has the size of an icon of a vanilla map.

### Clicks on markers

A click on a clickable marker (on the map of the world or on a map frame) posts a `MarkerClickEvent` on `NeoForge.EVENT_BUS`, on the server thread. The event comes only for a marker that the server sent to this player.

```java
NeoForge.EVENT_BUS.addListener(MarkerClickEvent.class, event -> {
    if (event.getLayer().equals(id)) {
        openJournal(event.getPlayer(), event.getMarker().id());
    }
});
```

## Markers of blocks on the survey maps

The survey maps are the paper maps of Cartofold. A player marks a place on a survey map from a block, as with a banner on a vanilla map: a use of the map on the block adds a marker, and a second use removes it. A mod adds its own blocks with `PaperMapMarkers`:

```java
PaperMapMarkers.register(PaperMarkerSource.of(
        ResourceLocation.fromNamespaceAndPath("mymod", "village_heart"),
        state -> state.is(MyBlocks.VILLAGE_HEART.get()),
        MyMapDecorations.VILLAGE, // a MapDecorationType, or CartofoldMapDecorations.WAYPOINT
        (level, pos) -> villageAt(level, pos).map(village -> Component.literal(village.name()))));
```

- The name function gives the name of the marker. An empty name tells that the block does not mark a waypoint now: the use does nothing, and the maps lose the marker.
- For an icon that changes with the block, give a `PaperMarkerSource` with a `MarkerFunction`: it gives the icon and the name of each block.
- When the server sends a map and the chunk of a marker is loaded, it checks the block again: a block that changes removes the marker, and the icon and the name follow the source.
- Cartofold has sources for the banners (the icons and the names of the vanilla banners) and for the lodestone (the pin `cartofold:waypoint`, `CartofoldMapDecorations.WAYPOINT`).
- The vanilla maps keep the vanilla banners only.

## Layers from the client

A mod of the client can draw a layer itself, from data that the client has. Cartofold calls the renderer in each view that shows the layer, after the chunks.

```java
CartofoldClientLayers.register(new MapLayerRenderer() {
    @Override
    public LayerInfo info() {
        return LayerInfo.of(ResourceLocation.fromNamespaceAndPath("mymod", "weather"),
                Component.literal("Weather"), 20, false);
    }

    @Override
    public void render(LayerRenderContext context) {
        double[] point = context.projection().toScreen(stormX, stormZ);
        context.graphics().fill((int) point[0] - 2, (int) point[1] - 2, (int) point[0] + 2, (int) point[1] + 2,
                0xFF4060FF);
    }
});
```

## Open the map of the world

A screen of a mod opens the map of the world at a position with `CartofoldWorldMap`. The map stays at this position until the player moves it. Escape then shows the screen of the mod again.

```java
CartofoldWorldMap.at(Level.OVERWORLD, villageX, villageZ)
        .scale(2) // optional: by default, the map keeps the scale of the last map
        .parent(this) // optional: the screen to show when the player closes the map
        .select(layer, "north_village") // optional: this marker shows as selected
        .open();
```

The game calls `init()` of the screen of the mod again when the map closes: keep the state of the screen in its fields. The server sends the chunks of the dimension of the player only. In another dimension, the map shows the chunks that the client has, and the layers.

## Map frames

`MapFrame` is a widget for the screens of the other mods. It shows the explored map, the layers with the kind `MapViewKind.FRAME`, the waypoints and the player.

```java
@Override
protected void init() {
    addRenderableWidget(new MapFrame(left, top, 200, 150)
            .center(villageX, villageZ)
            .scale(0.5) // units of the screen for one block, from 1/8 to 8
            .layers(layer -> layer.getNamespace().equals("mymod"))
            .showWaypoints(false));
}
```

- `followPlayer(true)` puts the player at the center until a move of the mouse.
- `interactive(false)` stops the moves, the zoom and the clicks of the mouse.
- `dimension(...)` shows another dimension than the dimension of the player. The server sends the chunks of the dimension of the player only: in another dimension, the frame shows the chunks that the client has, the background and the layers.
- `projection()` gives the screen point of a block and the block of a screen point, for the draws of the screen above the frame.
- `onMarkerClick((layer, marker) -> ...)` gets the clicks on the markers on the client, with no message to the server. It gets all the markers, also the markers that are not clickable.
- `view()` gives the center and the scale at any time, and `view(view)` sets them. A screen keeps the view between two openings.
- `center(...)` and `scale(...)` also work at any time, not only when the screen makes the frame.
- `showBox(minX, minZ, maxX, maxZ)` selects the center and the scale that show all of a box of blocks.
- `onViewChange(view -> ...)` gets the new view when the player moves or zooms the frame.
- `onEmptyClick(() -> ...)` gets the clicks that hit no marker. A drag of the map is not a click.
- `backgroundColor(color)` sets the color of the frame where the map has no chunk.
- `background(context -> ...)` draws a background of the mod, below the chunks and below the layers. The context has the projection of the frame, as in a layer of the client. Use it for a relief that the mod calculates.
- `allChunks(true)` shows the chunks that the other players explored, for a screen of an operator (permission level 2). For other players, the server sends only the explored chunks. The server has no map data for the chunks that no player explored: the background shows there. The client counts these chunks as explored until it frees their regions, also for the layers with `onlyExplored = true`.
- `select(layer, marker)` shows a marker as selected: the marker has a mark, and its name always shows. `clearSelection()` removes the selection.

A click on a clickable marker also posts `MarkerClickEvent` on the server.

The client asks the server for the regions of each frame on the screen: in the dimension of the player, the frame shows the chunks that the player explored, also far from the player.
