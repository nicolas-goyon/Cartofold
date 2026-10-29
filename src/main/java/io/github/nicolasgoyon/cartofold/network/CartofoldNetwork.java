package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.client.ClientExploration;
import io.github.nicolasgoyon.cartofold.client.ClientLayers;
import io.github.nicolasgoyon.cartofold.client.ClientWaypoints;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapLookup;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
import io.github.nicolasgoyon.cartofold.server.LayerService;
import io.github.nicolasgoyon.cartofold.server.WaypointService;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The payloads of Cartofold. They are optional: a client with Cartofold can join a server with no Cartofold, and the
 * reverse.
 */
public final class CartofoldNetwork {
    /** The version of the payloads. Increase it when a payload changes. */
    public static final String VERSION = "1";

    private CartofoldNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToServer(ExplorationRequestPayload.TYPE, ExplorationRequestPayload.STREAM_CODEC,
                ExplorationService::onRequest);
        registrar.playToClient(ExplorationChunksPayload.TYPE, ExplorationChunksPayload.STREAM_CODEC,
                (payload, context) -> ClientExploration.get().accept(payload));
        registrar.playToServer(WaypointActionPayload.TYPE, WaypointActionPayload.STREAM_CODEC,
                WaypointService::onAction);
        registrar.playToClient(WaypointsPayload.TYPE, WaypointsPayload.STREAM_CODEC,
                (payload, context) -> ClientWaypoints.get().set(payload.waypoints()));
        registrar.playToClient(LayerPayload.TYPE, LayerPayload.STREAM_CODEC,
                (payload, context) -> ClientLayers.get().set(payload.content()));
        registrar.playToClient(LayerRemovePayload.TYPE, LayerRemovePayload.STREAM_CODEC,
                (payload, context) -> ClientLayers.get().remove(payload.layer()));
        registrar.playToClient(SurveyMapPayload.TYPE, SurveyMapPayload.STREAM_CODEC,
                (payload, context) -> SurveyMapLookup.accept(payload));
        registrar.playToServer(MarkerClickPayload.TYPE, MarkerClickPayload.STREAM_CODEC, LayerService::onMarkerClick);
    }
}
