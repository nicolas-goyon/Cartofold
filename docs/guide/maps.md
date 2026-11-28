# Minimap and map of the world

The maps show only the places that you explored. The server records the chunks near you as you move.

Your game keeps a copy of the map of each world in the folder `cartofold/cache` of the game. When you join the world again, the map comes from this copy at once. The server then sends only the places that changed.

## Spyglass

Keep a spyglass in your inventory to explore farther. You do not need to use it. The server explores the land 2 times farther from you (the server can change this factor, see the [settings of the server](settings.md#settings-of-the-server)).

The server never loads new land for the spyglass. Past the normal distance, you explore only these places:

- The land at the edge of the view of the server: 2 chunks more than the view distance.
- The land near the other players, and the land that the server keeps loaded (the forced chunks, for example).
- The land that a player of the server explored before. The map shows this land as it was at this time.

The normal distance is the view distance of the server, 12 chunks at most. With the default view distance (10 chunks), the spyglass thus gives 12 chunks in a new place. It gives 20 chunks in a place that other players explored.

## Minimap

The minimap shows the map around you, the north, your arrow and your coordinates.

- Press **Y** to show or hide it.
- Press **[** to zoom out and **]** to zoom in. The minimap has five zoom levels.
- The minimap zooms out when you go faster, also in a vehicle. It has five levels of speed: stopped, walk, sprint, a little faster (a horse, a boat on water) and very fast (an elytra, a boat on ice). The zoom level is the zoom when you stop. The setting `speedZoom` stops this.

The [settings](settings.md) select the shape, the corner of the screen and the size. The minimap can also turn with you.

## Entities

The minimap and the map of the world show the loaded entities near you as dots: the players in white, the hostile mobs in red, the animals and the passive mobs in green, and the items and the vehicles in yellow. The [settings](settings.md#entities) select the kinds. A server can stop each kind (see the [settings of the server](settings.md#settings-of-the-server)).

## Map of the world

Press **M** to open the map of the world.

| Action | Control |
| --- | --- |
| Move the map | Drag with the mouse, or use the arrow keys |
| Zoom | Mouse wheel |
| Center the map on you | Space |
| Add a waypoint, or change a waypoint | Right click |
| Click a marker of a layer | Left click |
| Select the surface or the caves | Button at the top left, Page Up, Page Down, Home |
| Show or hide the layers | Button **Layers** |
| Share your map with a player | Button **Share** |
| Open the settings | Button **Settings** |
| Close the map | Escape, or **M** |

The map follows you until you move it. It shows the coordinates under the mouse at the bottom left.

### Share your map

The button **Share** shows the other players on the server. Click a player to offer your map to this player. The player gets a message with two buttons: **Accept** and **Refuse**.

- When the player accepts, the places that you explored go to the map of this player: the surface and the caves, in all the dimensions. The map of the player keeps its own places. Your map does not change.
- The copy is a copy of this time: the places that you explore later do not go to the other player.
- An offer expires after 5 minutes, or when one of the two players leaves. You can send 5 offers each minute.
- The commands `/cartofold share <player>`, `/cartofold share accept <player>` and `/cartofold share refuse <player>` do the same.

### Names of the markers

The map does not draw a name above another name. Zoom in to see the hidden names. A marker under the mouse always shows its name.

## Caves

You are in a cave when you are 5 blocks or more under the surface, in the dark. A cave view then shows the caves at your height, in a band of 16 blocks.

- The minimap shows your band. Its coordinates say "(caves)".
- The map of the world opens on your band.
- On the map of the world, **Page Up** and **Page Down** select the band above or below. **Home** selects the surface.

The setting **Caves** turns this view off. The dimensions with a ceiling, such as the Nether, have no cave view: their map shows the floor under the ceiling.

## Layers

Other mods add layers to the maps, for example territories, routes and markers. Use the button **Layers** of the map of the world to show or hide each layer. The button **HUD** of a layer shows or hides its markers in the [world](waypoints.md#markers-in-the-world).
