package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.ExploredMask;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * The files of the exploration data, in the folder {@code data/cartofold/} of the world.
 *
 * <ul>
 *   <li>{@code <namespace>/<dimension>/regions/r.<x>.<z>.dat}: the map data of a region of the surface
 *       ({@link MapRegion}).</li>
 *   <li>{@code <namespace>/<dimension>/players/<uuid>/r.<x>.<z>.dat}: the explored columns of a region of the surface
 *       for a player ({@link ExploredMask}).</li>
 *   <li>{@code <namespace>/<dimension>/caves/<band>/regions/...} and {@code .../caves/<band>/players/...}: the same
 *       files for a band of caves ({@link MapSlice}).</li>
 *   <li>A write goes to a temporary file, then replaces the file: a stop of the server during a write does not break
 *       the file.</li>
 * </ul>
 */
public final class ExplorationStorage {
    private final Path root;

    /**
     * Makes the storage.
     *
     * @param root the folder {@code data/cartofold/} of the world
     */
    public ExplorationStorage(Path root) {
        this.root = root;
    }

    private Path folder(ExplorationKey key) {
        ResourceLocation id = key.dimension().location();
        Path dimension = root.resolve(id.getNamespace()).resolve(id.getPath());
        return key.slice().surface() ? dimension : dimension.resolve(key.slice().folder());
    }

    /** The file of the map data of a region. */
    Path regionFile(ExplorationKey key, RegionPos pos) {
        return folder(key).resolve("regions").resolve(pos.fileName());
    }

    /** The file of the explored columns of a region for a player. */
    Path maskFile(ExplorationKey key, UUID player, RegionPos pos) {
        return folder(key).resolve("players").resolve(player.toString()).resolve(pos.fileName());
    }

    /**
     * Reads the map data of a region.
     *
     * @return the region, or empty with no file
     * @throws IOException if the file is not valid
     */
    public Optional<MapRegion> readRegion(ExplorationKey key, RegionPos pos) throws IOException {
        Path file = regionFile(key, pos);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            return Optional.of(MapRegion.read(pos, in));
        }
    }

    /**
     * Reads the explored columns of a region for a player.
     *
     * @return the mask, or empty with no file
     * @throws IOException if the file is not valid
     */
    public Optional<ExploredMask> readMask(ExplorationKey key, UUID player, RegionPos pos) throws IOException {
        Path file = maskFile(key, player, pos);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            return Optional.of(ExploredMask.read(pos, in));
        }
    }

    /** The bytes of a region, for a write later. */
    static byte[] bytes(MapRegion region) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        region.write(out);
        return out.toByteArray();
    }

    /** The bytes of a mask, for a write later. */
    static byte[] bytes(ExploredMask mask) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mask.write(out);
        return out.toByteArray();
    }

    /**
     * Writes bytes to a file: to a temporary file, then in place of the file.
     *
     * @param file  the file
     * @param bytes the content
     * @throws IOException if the write fails
     */
    static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
