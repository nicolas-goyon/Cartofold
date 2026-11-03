package io.github.nicolasgoyon.cartofold.view;

import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.tile.TileImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The projection of a map view between the blocks of the world and the points of the screen.
 *
 * <p>The view is a rectangle of the screen. The center of the rectangle shows the block {@code (centerX, centerZ)}. A
 * block is {@code scale} units of the screen on each side. The map turns by {@code rotation} radians around the center
 * of the rectangle: with a rotation of 0, the north is at the top. A positive rotation turns the map clockwise.
 *
 * @param x        the left side of the rectangle, in units of the screen
 * @param y        the top side of the rectangle, in units of the screen
 * @param width    the width of the rectangle, in units of the screen
 * @param height   the height of the rectangle, in units of the screen
 * @param centerX  the X of the block at the center
 * @param centerZ  the Z of the block at the center
 * @param scale    the number of units of the screen for one block
 * @param rotation the rotation of the map, in radians
 */
public record MapProjection(
        double x, double y, double width, double height, double centerX, double centerZ, double scale,
        double rotation) {
    public MapProjection {
        if (!(width > 0) || !(height > 0) || !(scale > 0)) {
            throw new IllegalArgumentException("A view needs a positive size and a positive scale");
        }
    }

    /** A view with the north at the top. */
    public static MapProjection north(
            double x, double y, double width, double height, double centerX, double centerZ, double scale) {
        return new MapProjection(x, y, width, height, centerX, centerZ, scale, 0);
    }

    /** The X of the center of the rectangle, on the screen. */
    public double screenCenterX() {
        return x + width / 2;
    }

    /** The Y of the center of the rectangle, on the screen. */
    public double screenCenterY() {
        return y + height / 2;
    }

    /**
     * The point of the screen of a position in the world.
     *
     * @return the X and the Y on the screen
     */
    public double[] toScreen(double blockX, double blockZ) {
        double dx = (blockX - centerX) * scale;
        double dz = (blockZ - centerZ) * scale;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        return new double[] {screenCenterX() + dx * cos - dz * sin, screenCenterY() + dx * sin + dz * cos};
    }

    /**
     * The position in the world of a point of the screen.
     *
     * @return the X and the Z in the world
     */
    public double[] toBlock(double screenX, double screenY) {
        double dx = screenX - screenCenterX();
        double dy = screenY - screenCenterY();
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        return new double[] {centerX + (dx * cos + dy * sin) / scale, centerZ + (-dx * sin + dy * cos) / scale};
    }

    /**
     * The smallest box of blocks that contains the rectangle.
     *
     * @return the minimum X, the minimum Z, the maximum X and the maximum Z
     */
    public double[] blockBounds() {
        double cos = Math.abs(Math.cos(rotation));
        double sin = Math.abs(Math.sin(rotation));
        double halfX = (width * cos + height * sin) / 2 / scale;
        double halfZ = (width * sin + height * cos) / 2 / scale;
        return new double[] {centerX - halfX, centerZ - halfZ, centerX + halfX, centerZ + halfZ};
    }

    /** The regions in the rectangle. */
    public List<RegionPos> regions() {
        double[] bounds = blockBounds();
        RegionPos min = RegionPos.ofBlock((int) Math.floor(bounds[0]), (int) Math.floor(bounds[1]));
        RegionPos max = RegionPos.ofBlock((int) Math.floor(bounds[2]), (int) Math.floor(bounds[3]));
        List<RegionPos> regions = new ArrayList<>();
        for (int z = min.z(); z <= max.z(); z++) {
            for (int x = min.x(); x <= max.x(); x++) {
                regions.add(new RegionPos(x, z));
            }
        }
        return regions;
    }

    /**
     * The level of detail of the textures for this view: the most detail that the pixels of the window can show.
     *
     * @param guiScale the number of pixels of the window for one unit of the screen
     * @return the level of detail, from 0 to {@link TileImage#LODS} - 1
     */
    public int lod(double guiScale) {
        double blocksPerPixel = 1 / (scale * guiScale);
        int lod = blocksPerPixel <= 1 ? 0 : (int) Math.floor(Math.log(blocksPerPixel) / Math.log(2));
        return Math.min(lod, TileImage.LODS - 1);
    }

}
