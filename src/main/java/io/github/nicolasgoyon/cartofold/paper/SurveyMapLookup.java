package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * The survey maps that the client received from the server (see {@link SurveyMapPayload}). This class uses no class
 * of the client: the payload handler and the items on the common side can call it.
 */
public final class SurveyMapLookup {
    private static final Map<Integer, SurveyMapPayload> CLIENT = new ConcurrentHashMap<>();

    private SurveyMapLookup() {
    }

    /** Keeps the data of a map on the client. */
    public static void accept(SurveyMapPayload payload) {
        CLIENT.put(payload.id(), payload);
    }

    /** The data of a map on the client, or null if the server did not send it. */
    @Nullable
    public static SurveyMapPayload client(SurveyMapId id) {
        return CLIENT.get(id.id());
    }

    /** Removes all the maps, when the client leaves a world. */
    public static void clear() {
        CLIENT.clear();
    }
}
