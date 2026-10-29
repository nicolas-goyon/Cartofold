# Cartofold

Cartofold is a map mod for Minecraft (NeoForge 1.21.1). It gives one map engine to all the maps of the game:

- a minimap on the screen, and a map of the explored world (key M);
- paper maps that replace the vanilla maps, with waypoints and a cartography table that moves and updates them;
- an API for the other mods: they add layers to all the maps (for example territories, routes, markers) and show a map frame in their own screens.

The mod does not depend on other mods. It is necessary on the server and on the client: the server records the exploration.

## Status

- The map engine, the minimap, the map of the world, the waypoints of the player and the settings are ready.
- The API for the other mods is ready: layers and map frames.
- The paper maps are the next milestone.

The plan is in [docs/design.md](docs/design.md) and in the milestones of the issues.

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
