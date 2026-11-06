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

Put a survey map in the cartography table. The table shows a screen for the survey map. With a vanilla map, the table stays vanilla.

The second item selects the action:

| Second item | Result |
| --- | --- |
| Paper | The action of the selected button. |
| Glass pane | Make a locked copy. |
| Empty survey map | Make a copy. |

With a paper, the buttons select the action:

- **Zoom out**: a new map with the next scale. This is the action when no other button is selected.
- **North, South, West, East**: a new map, moved by half of its side, with the same scale. Half of the explored map stays.
- **Update**: when you take the map, the server paints it from the generated land, for 1024 chunks at most. A large map needs more than one update.

The selected button is yellow. Select the same button again to zoom out.

The panel at the right shows the action, a large preview of the result and the cost: the item that the table uses. With no result, it shows the reason and the items that you can use.

A locked map does not zoom, move, update or lock again. It only gives copies.
