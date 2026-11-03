package io.github.nicolasgoyon.cartofold.paper;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The next id of a survey map, in the storage of the Overworld. */
final class SurveyMapCounter extends SavedData {
    private static final String KEY = "cartofold_survey_maps";
    private int next;

    static SurveyMapCounter get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SurveyMapCounter::new, SurveyMapCounter::load, null), KEY);
    }

    /** Gives a new id. */
    SurveyMapId next() {
        setDirty();
        return new SurveyMapId(next++);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Next", next);
        return tag;
    }

    private static SurveyMapCounter load(CompoundTag tag, HolderLookup.Provider registries) {
        SurveyMapCounter counter = new SurveyMapCounter();
        counter.next = tag.getInt("Next");
        return counter;
    }
}
