package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shows and hides the layers in all the views. The settings of the client keep the hidden layers. */
public final class LayersScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int ROW = 24;
    /** The largest number of layers on the screen. */
    private static final int MAX_ROWS = 10;

    @Nullable
    private final Screen parent;

    public LayersScreen(@Nullable Screen parent) {
        super(Component.translatable("cartofold.layers.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<LayerInfo> layers = ClientLayers.get().infos();
        int left = width / 2 - WIDTH / 2;
        int top = 40;
        int row = 0;
        for (LayerInfo layer : layers.subList(0, Math.min(layers.size(), MAX_ROWS))) {
            String id = layer.id().toString();
            boolean shown = !CartofoldClientConfig.HIDDEN_LAYERS.get().contains(id);
            addRenderableWidget(CycleButton.onOffBuilder(shown).create(left, top + row * ROW, WIDTH, 20,
                    layer.name(), (button, value) -> setShown(id, value)));
            row++;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private static void setShown(String id, boolean shown) {
        List<String> hidden = new ArrayList<>(CartofoldClientConfig.HIDDEN_LAYERS.get());
        hidden.remove(id);
        if (!shown) {
            hidden.add(id);
        }
        CartofoldClientConfig.HIDDEN_LAYERS.set(hidden);
        CartofoldClientConfig.SPEC.save();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
        if (ClientLayers.get().infos().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("cartofold.layers.none"), width / 2, 48,
                    0xFFA0A0A0);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
