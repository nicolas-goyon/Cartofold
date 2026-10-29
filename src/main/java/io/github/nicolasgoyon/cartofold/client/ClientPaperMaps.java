package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.network.PaperMapInfoPayload;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The places of the vanilla maps that the client received from the server. This class uses no class of the client:
 * the payload handler on the common side can call it.
 */
public final class ClientPaperMaps {
    private static final Map<Integer, PaperMapInfoPayload> MAPS = new ConcurrentHashMap<>();

    private ClientPaperMaps() {
    }

    /** Keeps the place of a map. */
    public static void accept(PaperMapInfoPayload info) {
        MAPS.put(info.map(), info);
    }

    /** The place of a map, or empty if the server did not send it. */
    public static Optional<PaperMapInfoPayload> get(int map) {
        return Optional.ofNullable(MAPS.get(map));
    }

    /** Removes all the places, when the client leaves a world. */
    public static void clear() {
        MAPS.clear();
    }
}
