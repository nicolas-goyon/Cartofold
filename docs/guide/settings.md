# Keys and settings

## Keys

| Key | Action |
| --- | --- |
| M | Open the map of the world |
| Y | Show or hide the minimap |
| `[` and `]` | Zoom out and zoom in the minimap |
| B | Add a waypoint at your position |

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
