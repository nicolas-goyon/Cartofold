# Keys and settings

## Keys

| Key | Action |
| --- | --- |
| M | Open the map of the world |
| Y | Show or hide the minimap |
| `[` and `]` | Zoom out and zoom in the minimap |
| B | Add a waypoint at your position |
| U | Show or hide the markers in the world |

Change the keys in the controls of the game.

## Settings

Open the settings from the list of the mods, or with the button **Settings** of the map of the world. The file is `config/cartofold-client.toml`.

### Minimap

| Setting | Default | Description |
| --- | --- | --- |
| `enabled` | true | Show the minimap. |
| `shape` | `SQUARE` | `SQUARE` or `ROUND`. |
| `corner` | `TOP_LEFT` | The corner of the screen. |
| `size` | 100 | The size in units of the screen, from 48 to 256. |
| `zoom` | 2 | The zoom level, from -2 (far) to 4 (near). Each level doubles the size of the land: -2 shows 16 blocks in one unit of the screen. |
| `speedZoom` | true | Zoom out the minimap when you go faster. The zoom level is then the zoom when you stop. |
| `speedZoomStart` | 0.5 | The speed of the first zoom out, in blocks each second. A walk is about 4.3, a sprint about 5.6. |
| `speedZoomFull` | 15.0 | The speed of the strongest zoom out, in blocks each second. An elytra is about 30. |
| `speedZoomOut` | 5.0 | The strongest zoom out, from 1 to 16: 5 shows 5 times more land on each side. |
| `speedZoomLimit` | -2 | The zoom with the speed does not go farther than this zoom level. |
| `rotate` | false | Turn the minimap with you. |
| `coordinates` | true | Show your coordinates. |
| `waypoints` | true | Show the waypoints. |
| `caves` | true | Show the caves at your height. |

### Map of the world

| Setting | Default | Description |
| --- | --- | --- |
| `waypoints` | true | Show the waypoints. |
| `waypointNames` | true | Show the names of the waypoints. |

### Markers

| Setting | Default | Description |
| --- | --- | --- |
| `scale` | 1.0 | The scale of the markers, of their names and of the arrow of the player, from 0.5 to 3. |

### Maps

| Setting | Default | Description |
| --- | --- | --- |
| `resolution` | `FULL` | The resolution of the land on the minimap, on the map of the world and in the map frames: `FULL`, `HALF` or `QUARTER`. A lower resolution uses less memory. |

### Layers

| Setting | Default | Description |
| --- | --- | --- |
| `hidden` | empty | The identifiers of the hidden layers. |
| `bannerViews` | `BOTH` | The views of the banners: `MINIMAP`, `WORLD_MAP` or `BOTH`. |

### Markers in the world

| Setting | Default | Description |
| --- | --- | --- |
| `enabled` | true | Show the markers in the world. The key U also changes it. |
| `waypoints` | true | Show the waypoints. |
| `minDistance` | 50 | Hide the markers nearer than this distance, in blocks. |
| `maxDistance` | 1000 | Hide the markers farther than this distance, in blocks. 0 shows all the far markers. |
| `opacity` | 80 | The opacity of the markers, in percent, from 10 to 100. |
| `height` | 10.0 | The height of a marker above its position, in blocks, from 0 to 64. |
| `scale` | 0.5 | The size of the markers, from 0.5 to 4. |
| `names` | true | Show the names. |
| `distances` | true | Show the distances. |
| `hiddenLayers` | empty | The identifiers of the layers that do not show in the world. |

### Entities

The section `entities` selects the loaded entities on the minimap, and its section `worldMap` selects them on the map of the world, with the same names. Each entity shows as a dot. The client knows only the entities near you, so the dots show only near you.

| Setting | Default | Description |
| --- | --- | --- |
| `players` | true | Show the other players, as white dots. The map of the world shows their names. A player that sneaks hides its name. |
| `hostile` | true | Show the hostile mobs, as red dots. |
| `passive` | true | Show the animals and the other passive mobs, as green dots. |
| `villagers` | true | Show the villagers and the wandering traders, as blue dots. |
| `objects` | false | Show the items on the ground, the boats and the minecarts, as yellow dots. |
| `height` | 24 | Show only the entities at most this number of blocks above or below you, from 0 to 512. 0 shows the entities at all heights. The two views use it. |
| `hiddenKinds` | empty | The identifiers of the kinds of entities of the other mods that the maps do not show, for example `mymod:caravans`. The two views use it. |

Other mods can give their entities a kind with its own color (for example the caravans of a mod). Such an entity keeps the setting of its kind of Cartofold: a caravan that is a passive mob shows only when `passive` is true.

## Settings of the server

The file of the server is `serverconfig/cartofold-server.toml`, in the folder of the world. A change of the file goes to the players at once.

| Setting | Default | Description |
| --- | --- | --- |
| `entities.players` | true | Let the maps of the players show the other players. |
| `entities.hostile` | true | Let the maps of the players show the hostile mobs. |
| `entities.passive` | true | Let the maps of the players show the animals and the passive mobs. |
| `entities.villagers` | true | Let the maps of the players show the villagers and the wandering traders. |
| `entities.objects` | true | Let the maps of the players show the items and the vehicles. |
| `entities.sameTeamOnly` | false | The maps of a player show only the other players of its team (`/team`). A player with no team sees no other player. |
| `exploration.spyglassFactor` | 2.0 | A player with a spyglass in the inventory explores a radius this number of times larger, from 1 to 4. 1 gives the spyglass no effect. |

A setting at false hides this kind of entity on the maps of all the players, for a fair game. A changed client can still see the entities near its player.

The spyglass never loads or generates a chunk. The normal radius is the view distance, 12 chunks at most. Past this radius, a player with a spyglass explores the chunks that the server has in memory. It also explores the chunks with map data of Cartofold, and no other chunk (see [Spyglass](maps.md#spyglass)). A larger view distance thus gives the spyglass more land. Each second, the server reads the factor squared times more chunks for each player with a spyglass. These reads are in memory. For each region of Cartofold that is not in memory, the server checks the disk once each second.
