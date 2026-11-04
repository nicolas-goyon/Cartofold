# Survey maps

Survey maps are the paper maps of Cartofold. The vanilla maps stay vanilla.

## Make a survey map

Craft an **empty survey map**:

| | | |
| --- | --- | --- |
| Ink sac | Paper | Ink sac |
| Paper | Compass | Paper |
| Ink sac | Paper | Ink sac |

Use the empty survey map. You get a survey map around your position.

## Explore

A survey map in your hand explores the land within 128 blocks of you. A spyglass in your inventory makes this distance 3 times larger.

The map shows the layers of the other mods on its explored pixels.

## Mark a place

Use a survey map on a block to add a marker. Use the map on the block again to remove the marker.

- **Banners**: the marker has the icon, the color and the name of the banner.
- **Lodestones**: the marker is a pin.
- Other mods can add their own blocks.

A marker goes away from the map when its block goes away.

## Cartography table

The table works as with the vanilla maps, and shows a preview:

| Second item | Result |
| --- | --- |
| Paper | Zoom out the map. |
| Glass pane | Make a locked copy. |
| Empty survey map | Make a copy. |

With a survey map and a paper, five buttons select another action:

- **North, south, west, east**: a new map, moved by half of its side, with the same scale.
- **Update**: the server paints the map from the generated land, for 1024 chunks at most.

Select the same button again to zoom out. A locked map does not zoom, move, update or lock again.
