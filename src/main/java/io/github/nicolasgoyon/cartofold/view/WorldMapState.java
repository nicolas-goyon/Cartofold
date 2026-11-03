package io.github.nicolasgoyon.cartofold.view;

/**
 * The center and the scale of the map of the world. The state follows the player until the player moves the map.
 */
public final class WorldMapState {
    /** The smallest scale: one unit of the screen for 8 blocks. */
    public static final double MIN_SCALE = 1.0 / 8;
    /** The largest scale: 8 units of the screen for one block. */
    public static final double MAX_SCALE = 8;
    /** Each step of the wheel multiplies or divides the scale by this factor. */
    public static final double ZOOM_FACTOR = 2;

    private double centerX;
    private double centerZ;
    private double scale;
    private boolean follow = true;

    /**
     * Makes a state.
     *
     * @param centerX the X of the block at the center
     * @param centerZ the Z of the block at the center
     * @param scale   the number of units of the screen for one block
     */
    public WorldMapState(double centerX, double centerZ, double scale) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    public double centerX() {
        return centerX;
    }

    public double centerZ() {
        return centerZ;
    }

    public double scale() {
        return scale;
    }

    /** True while the center follows the player. */
    public boolean follows() {
        return follow;
    }

    /** The projection of the state in a rectangle, with the north at the top. */
    public MapProjection projection(double x, double y, double width, double height) {
        return MapProjection.north(x, y, width, height, centerX, centerZ, scale);
    }

    /** Puts the center on the player, and follows the player again. */
    public void center(double playerX, double playerZ) {
        centerX = playerX;
        centerZ = playerZ;
        follow = true;
    }

    /** Puts the center on the player, if the state follows the player. */
    public void track(double playerX, double playerZ) {
        if (follow) {
            centerX = playerX;
            centerZ = playerZ;
        }
    }

    /**
     * Sets the center and the scale. The state stops to follow the player.
     *
     * @param blockX the X of the block at the center
     * @param blockZ the Z of the block at the center
     * @param next   the number of units of the screen for one block
     */
    public void set(double blockX, double blockZ, double next) {
        centerX = blockX;
        centerZ = blockZ;
        scale = Math.clamp(next, MIN_SCALE, MAX_SCALE);
        follow = false;
    }

    /**
     * Selects the center and the largest scale that show a box of blocks in a rectangle. The state stops to follow the
     * player. When the box is too large for {@link #MIN_SCALE}, the rectangle shows the middle of the box.
     *
     * @param minX   the smallest X of the box
     * @param minZ   the smallest Z of the box
     * @param maxX   the largest X of the box
     * @param maxZ   the largest Z of the box
     * @param width  the width of the rectangle, in units of the screen
     * @param height the height of the rectangle, in units of the screen
     * @param margin the free space on each side of the rectangle, in units of the screen
     */
    public void fit(double minX, double minZ, double maxX, double maxZ, double width, double height, double margin) {
        double boxWidth = Math.max(Math.abs(maxX - minX), 1);
        double boxHeight = Math.max(Math.abs(maxZ - minZ), 1);
        double freeWidth = Math.max(width - 2 * margin, 1);
        double freeHeight = Math.max(height - 2 * margin, 1);
        set((minX + maxX) / 2, (minZ + maxZ) / 2, Math.min(freeWidth / boxWidth, freeHeight / boxHeight));
    }

    /**
     * Moves the map with the mouse: the map goes with the mouse.
     *
     * @param dx the move of the mouse on X, in units of the screen
     * @param dy the move of the mouse on Y, in units of the screen
     */
    public void drag(double dx, double dy) {
        centerX -= dx / scale;
        centerZ -= dy / scale;
        follow = false;
    }

    /**
     * Changes the scale. The block under the point of the screen stays under this point.
     *
     * @param projection the projection before the change
     * @param screenX    the X of the point on the screen (the mouse)
     * @param screenY    the Y of the point on the screen
     * @param steps      the steps of the wheel: more than 0 to zoom in
     */
    public void zoom(MapProjection projection, double screenX, double screenY, double steps) {
        double next = Math.clamp(scale * Math.pow(ZOOM_FACTOR, steps), MIN_SCALE, MAX_SCALE);
        if (next == scale) {
            return;
        }
        double[] block = projection.toBlock(screenX, screenY);
        double offsetX = screenX - projection.screenCenterX();
        double offsetY = screenY - projection.screenCenterY();
        scale = next;
        centerX = block[0] - offsetX / scale;
        centerZ = block[1] - offsetY / scale;
        if (offsetX != 0 || offsetY != 0) {
            follow = false;
        }
    }
}
