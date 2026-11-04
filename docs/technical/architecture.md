# Architecture

This page describes the code. The [design](../design.md) gives the principles and the features.

## Packages

All the packages are in `io.github.nicolasgoyon.cartofold`.

| Package | Side | Content |
| --- | --- | --- |
| `api` | Common | The public API on the server. |
| `api.client` | Client | The public API on the client: `MapFrame`, `CartofoldWorldMap`, the layers of the client. |
| `client` | Client | The views, the screens, the tiles and the settings. |
| `data` | Common | The regions, the masks and the slices. |
| `mixin` | Both | The changes of the vanilla classes. |
| `network` | Common | The payloads. |
| `paper` | Common | The survey maps. |
| `server` | Server | The services: exploration, layers, waypoints and banners. |
| `tile` | Common | The images of the tiles and their levels of detail. |
| `view` | Common | The pure logic of the views: projection, zoom, layout of the names. |
| `waypoint` | Common | The waypoints and the marks of the banners. |

Only `api`, `api.client` and `view.MapProjection` are part of the public API.

## Data flow

1. `ExplorationService` puts the loaded chunks near each player in the mask of this player. Chunks with no recent sample go to a queue.
2. `ColumnSampler` reads the chunks of the queue: 8 chunks at most each tick.
3. The client tells the regions of its open views with `ExplorationRequestPayload`: 128 regions at most for each slice, and 4 slices at most.
4. `ExplorationSync` sends the explored chunks of these regions: 32 chunks at most each tick for each player.
5. `ClientExploration` keeps the regions. `MapTiles` makes the textures.
6. `MapView` draws the tiles, then `LayerRenderer` draws the layers. `MapLabels` draws the names above all the markers.

## Payloads

`CartofoldNetwork.VERSION` changes when the format of a payload changes.

| Payload | Direction | Content |
| --- | --- | --- |
| `ExplorationRequestPayload` | To the server | The regions of the open views. |
| `ExplorationChunksPayload` | To the client | The columns of some chunks. |
| `WaypointActionPayload` | To the server | A change of a waypoint. |
| `WaypointsPayload` | To the client | All the waypoints of the player. |
| `LayerPayload` | To the client | All the content of a layer. |
| `LayerUpdatePayload` | To the client | A change of some elements of a layer. |
| `LayerRemovePayload` | To the client | The removal of a layer. |
| `MarkerClickPayload` | To the server | A click on a clickable marker. |
| `SurveyMapPayload` | To the client | All the data of a survey map. |

## Files of the world

| Path | Content |
| --- | --- |
| `data/cartofold/waypoints/<uuid>.json` | The waypoints of a player. |
| `data/cartofold/banners/<uuid>.json` | The marked banners of a player. |
| `<dimension>/caves/<band>/regions` and `.../players` | The exploration data of a band of caves. |

The vanilla storage of the Overworld keeps the survey maps (`cartofold_survey_map_<id>`).

## Build and tests

| Task | Purpose |
| --- | --- |
| `./gradlew build` | Compiles, checks the format and the translations, runs Error Prone and the JUnit tests. |
| `./gradlew runGameTestServer` | Runs the in-game tests. |
| `./gradlew spotlessApply` | Fixes the format. |
| `mkdocs serve` | Shows this site. Install `requirements-docs.txt` first. |

## Workflows

| Workflow | Trigger | Purpose |
| --- | --- | --- |
| Build | Push and pull request | Builds, tests and publishes the dev builds. |
| Pull request | Pull request | Checks the title, and shows the next version. |
| Release | Push on `main` | Makes a release. |
| Pages | Change of the documentation, or a release | Builds this site. |
