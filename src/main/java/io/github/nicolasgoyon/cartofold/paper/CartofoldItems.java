package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.Cartofold;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The items and the data components of Cartofold. */
public final class CartofoldItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cartofold.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Cartofold.MOD_ID);

    /** The id of the map of a survey map item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SurveyMapId>> SURVEY_MAP_ID =
            COMPONENTS.register("survey_map", () -> DataComponentType.<SurveyMapId>builder()
                    .persistent(SurveyMapId.CODEC).networkSynchronized(SurveyMapId.STREAM_CODEC).build());

    /** The action of the cartography table on a survey map (see {@link SurveyMapTable}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SurveyMapAction>> SURVEY_MAP_ACTION =
            COMPONENTS.register("survey_map_action", () -> DataComponentType.<SurveyMapAction>builder()
                    .persistent(SurveyMapAction.CODEC).networkSynchronized(SurveyMapAction.STREAM_CODEC).build());

    public static final DeferredItem<EmptySurveyMapItem> EMPTY_SURVEY_MAP =
            ITEMS.registerItem("empty_survey_map", EmptySurveyMapItem::new, new Item.Properties());
    public static final DeferredItem<SurveyMapItem> SURVEY_MAP =
            ITEMS.registerItem("survey_map", SurveyMapItem::new, new Item.Properties());

    private CartofoldItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(CartofoldItems::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(EMPTY_SURVEY_MAP.get());
        }
    }
}
