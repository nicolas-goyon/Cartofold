# API of Cartofold

Other mods use the API of Cartofold to add layers to all the maps (the minimap, the map of the world, the map frames and the paper maps), to show map frames in their own screens, and to mark their blocks on the paper maps.

The API is in the packages `io.github.nicolasgoyon.cartofold.api` (server and common) and `io.github.nicolasgoyon.cartofold.api.client` (client). The class `io.github.nicolasgoyon.cartofold.view.MapProjection` is also part of the API. The other packages are not part of the API: they can change in a minor version. The Javadoc jar of each version has the documentation of the API.

## Add Cartofold to a mod

Each release puts the jar in GitHub Packages, with the sources jar and the Javadoc jar. GitHub Packages needs a GitHub token with the permission `read:packages`, also for a public package. In GitHub Actions, the `GITHUB_TOKEN` of the workflow can read the package when the workflow has `packages: read`.

In `build.gradle` (ModDevGradle):

```groovy
repositories {
    maven {
        name = 'Cartofold'
        url = uri('https://maven.pkg.github.com/nicolas-goyon/cartofold')
        credentials {
            username = providers.environmentVariable('GITHUB_ACTOR').getOrElse('')
            password = providers.environmentVariable('GITHUB_TOKEN').getOrElse('')
        }
        content {
            includeGroup 'io.github.nicolasgoyon.cartofold'
        }
    }
}

dependencies {
    // A required dependency: the mod and the dev runs have Cartofold.
    implementation 'io.github.nicolasgoyon.cartofold:cartofold:0.4.0'
}
```

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

- `LayerInfo.order`: a layer with a higher order draws above the layers with a lower order. The waypoints of the player are above all the layers.
- `LayerInfo.views`: the kinds of views that show the layer (`MapViewKind.MINIMAP`, `WORLD_MAP`, `PAPER`, `FRAME`).
- `LayerInfo.onlyExplored`: the fills, the borders, the lines and the markers show only on the chunks that the player explored. A border shows when one of its two sides is explored.
- A layer has 4096 areas, 4096 lines and 4096 markers at most. An area has 65 536 chunks at most, and a line has 4096 points at most.
- The player can hide a layer with the "Layers" button of the map of the world.

### Clicks on markers

A click on a clickable marker (on the map of the world or on a map frame) posts a `MarkerClickEvent` on `NeoForge.EVENT_BUS`, on the server thread. The event comes only for a marker that the server sent to this player.

```java
NeoForge.EVENT_BUS.addListener(MarkerClickEvent.class, event -> {
    if (event.getLayer().equals(id)) {
        openJournal(event.getPlayer(), event.getMarker().id());
    }
});
```

## Waypoints of blocks on the paper maps

The paper maps are the vanilla maps. A player marks a place on a map from a block, as with a banner: a use of the map on the block adds a decoration with a name, and a second use removes it. A mod adds its own blocks with `PaperMapMarkers`:

```java
PaperMapMarkers.register(new PaperMarkerSource(
        ResourceLocation.fromNamespaceAndPath("mymod", "village_heart"),
        state -> state.is(MyBlocks.VILLAGE_HEART.get()),
        MyMapDecorations.VILLAGE, // a MapDecorationType, or CartofoldMapDecorations.WAYPOINT
        (level, pos) -> villageAt(level, pos).map(village -> Component.literal(village.name()))));
```

- The name function gives the name of the waypoint. An empty name tells that the block does not mark a waypoint now: the use does nothing, and the maps lose the waypoint.
- When the chunk of a waypoint is loaded, the server checks the block again: a block that changes removes the waypoint, and the name follows the name function.
- The decoration type `cartofold:waypoint` (`CartofoldMapDecorations.WAYPOINT`) is a pin. The lodestone uses it.

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
- `dimension(...)` shows another dimension than the dimension of the player.
- `projection()` gives the screen point of a block and the block of a screen point, for the draws of the screen above the frame.

The client asks the server for the regions of each frame on the screen: the frame shows the chunks that the player explored, also far from the player.
