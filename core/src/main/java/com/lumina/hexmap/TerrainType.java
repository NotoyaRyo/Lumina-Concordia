package com.lumina.hexmap;

public enum TerrainType {
    PLAIN(1f, true),
    ROAD(0.5f, true),
    DESERT(1.5f, true),
    TUNDRA(1f, true),
    FOREST(1.5f, true),
    HILLS(1.5f, true),
    ANTARCTIC(999f, false),
    ARCTIC(999f, false),
    MOUNTAIN(2f, true),
    MOUNTAIN_RANGE(999f, false),
    WATER(999f, false);

    private final float movementCost;
    private final boolean passable;

    TerrainType(float movementCost, boolean passable) {
        this.movementCost = movementCost;
        this.passable = passable;
    }

    public float getMovementCost() {
        return movementCost;
    }

    public boolean isPassable() {
        return passable;
    }

    public boolean isPolar() {
        return this == ANTARCTIC || this == ARCTIC;
    }
}
