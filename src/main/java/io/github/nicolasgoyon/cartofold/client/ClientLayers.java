package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.LayerUpdate;
import io.github.nicolasgoyon.cartofold.api.client.MapLayerRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * The layers on the client: the layers that the server sent, and the layers that the mods of the client draw. This
 * class uses no class of the client: the payload handlers on the common side can call it.
 */
public final class ClientLayers {
    private static final ClientLayers INSTANCE = new ClientLayers();

    private final Map<ResourceLocation, LayerContent> contents = new LinkedHashMap<>();
    private final Map<ResourceLocation, MapLayerRenderer> renderers = new LinkedHashMap<>();
    private long version;
    /** The layers of the server in their order, for {@link #version}, or null before the first use. */
    @Nullable
    private List<LayerContent> sorted;
    private long sortedVersion = -1;

    /** Makes an empty store. The game uses {@link #get()}. */
    public ClientLayers() {
    }

    /** The layers of the client. */
    public static ClientLayers get() {
        return INSTANCE;
    }

    /** Sets the content of a layer of the server. */
    public synchronized void set(LayerContent content) {
        contents.put(content.info().id(), content);
        version++;
    }

    /** Changes some elements of a layer of the server. The client ignores a change of a layer that it does not have. */
    public synchronized void update(ResourceLocation layer, LayerUpdate update) {
        LayerContent content = contents.get(layer);
        if (content != null) {
            contents.put(layer, content.apply(update));
            version++;
        }
    }

    /** Removes a layer of the server. */
    public synchronized void remove(ResourceLocation layer) {
        contents.remove(layer);
        version++;
    }

    /** Removes the layers of the server, when the client leaves a world. */
    public synchronized void clear() {
        contents.clear();
        version++;
    }

    /** The version of the layers of the server: it changes with each change of a layer. */
    public synchronized long version() {
        return version;
    }

    /** Adds a layer that a mod of the client draws. */
    public synchronized void register(MapLayerRenderer renderer) {
        renderers.put(renderer.info().id(), renderer);
    }

    /** The layers of the server, in their order. */
    public synchronized List<LayerContent> contents() {
        // The views ask for the layers more than once in each frame: the sorted list stays until the next change.
        if (sorted == null || sortedVersion != version) {
            sorted = contents.values().stream().sorted(Comparator.comparingInt(content -> content.info().order()))
                    .toList();
            sortedVersion = version;
        }
        return sorted;
    }

    /** The layers that the mods of the client draw. */
    public synchronized List<MapLayerRenderer> renderers() {
        return List.copyOf(renderers.values());
    }

    /** The descriptions of all the layers, in their order. */
    public synchronized List<LayerInfo> infos() {
        List<LayerInfo> infos = new ArrayList<>();
        contents.values().forEach(content -> infos.add(content.info()));
        renderers.values().forEach(renderer -> {
            if (!contents.containsKey(renderer.info().id())) {
                infos.add(renderer.info());
            }
        });
        infos.sort(Comparator.comparingInt(LayerInfo::order));
        return infos;
    }
}
