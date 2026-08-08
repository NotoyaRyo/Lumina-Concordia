package com.lumina.hexmap;

public class HexMapConfig {
    private final float hexSize;
    private final int mapRows;
    private final int mapColsEven;
    private final int mapColsOdd;
    private final float mapEdgePadding;

    public HexMapConfig(float hexSize, int mapRows, int mapColsEven, int mapColsOdd, float mapEdgePadding) {
        this.hexSize = hexSize;
        this.mapRows = mapRows;
        this.mapColsEven = mapColsEven;
        this.mapColsOdd = mapColsOdd;
        this.mapEdgePadding = mapEdgePadding;
    }

    public float getHexSize() { return hexSize; }
    public int getMapRows() { return mapRows; }
    public int getMapColsEven() { return mapColsEven; }
    public int getMapColsOdd() { return mapColsOdd; }
    public float getMapEdgePadding() { return mapEdgePadding; }

    public float getSqrt3() { return (float) Math.sqrt(3); }
    public float getHexWidth() { return getSqrt3() * hexSize; }
    public float getHexHeight() { return 2f * hexSize; }
}
