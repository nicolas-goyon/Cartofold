package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.client.MapLayerRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return contents.values().stream().sorted(Comparator.comparingInt(content -> content.info().order())).toList();
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
