package com.lumina.save;

import com.lumina.hexmap.MapDefinition;

public class SaveSlot {
    private final int slotIndex;
    private final String displayName;
    private final long savedAtEpochMillis;
    private final MapDefinition mapDefinition;

    public SaveSlot(int slotIndex, String displayName, long savedAtEpochMillis, MapDefinition mapDefinition) {
        this.slotIndex = slotIndex;
        this.displayName = displayName;
        this.savedAtEpochMillis = savedAtEpochMillis;
        this.mapDefinition = mapDefinition;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getSavedAtEpochMillis() {
        return savedAtEpochMillis;
    }

    public MapDefinition getMapDefinition() {
        return mapDefinition;
    }

    public boolean isEmpty() {
        return mapDefinition == null;
    }
}
