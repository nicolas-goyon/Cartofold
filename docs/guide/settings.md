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
| `zoom` | 2 | The zoom level, from 0 (far) to 4 (near). |
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
| `height` | 1.0 | The height of a marker above its position, in blocks, from 0 to 64. |
| `scale` | 1.0 | The size of the markers, from 0.5 to 4. |
| `names` | true | Show the names. |
| `distances` | true | Show the distances. |
| `hiddenLayers` | empty | The identifiers of the layers that do not show in the world. |
