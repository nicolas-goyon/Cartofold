/**
 * The API of Cartofold for the other mods, on the server and on the client.
 *
 * <ul>
 *   <li>{@link io.github.nicolasgoyon.cartofold.api.CartofoldLayers}: a mod of the server sends the content of a
 *       layer ({@link io.github.nicolasgoyon.cartofold.api.LayerContent}) to all the players or to one player.</li>
 *   <li>A layer has areas of chunks ({@link io.github.nicolasgoyon.cartofold.api.ChunkArea}), lines
 *       ({@link io.github.nicolasgoyon.cartofold.api.MapLine}) and markers
 *       ({@link io.github.nicolasgoyon.cartofold.api.MapMarker}). Its description
 *       ({@link io.github.nicolasgoyon.cartofold.api.LayerInfo}) gives its order and the kinds of views that show it
 *       ({@link io.github.nicolasgoyon.cartofold.api.MapViewKind}).</li>
 *   <li>{@link io.github.nicolasgoyon.cartofold.api.PaperMapMarkers}: the blocks that mark waypoints on the paper
 *       maps.</li>
 *   <li>A click on a clickable marker posts a {@link io.github.nicolasgoyon.cartofold.api.MarkerClickEvent} on the
 *       server.</li>
 * </ul>
 *
 * <p>The classes of the client are in {@link io.github.nicolasgoyon.cartofold.api.client}. The other packages of
 * Cartofold are not part of the API: they can change in a minor version.
 */
package io.github.nicolasgoyon.cartofold.api;
