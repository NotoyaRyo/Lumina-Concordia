package com.lumina.hexmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapDefinition {
    public enum SourceType {
        GENERATED_SEED,
        TILE_SNAPSHOT
    }

    private final String id;
    private final String displayName;
    private final boolean official;
    private final SourceType sourceType;
    private final int generationSeed;
    private final int rows;
    private final int mapColsEven;
    private final int mapColsOdd;
    private final List<TileData> tiles;

    public MapDefinition(String id, String displayName, boolean official, SourceType sourceType, int generationSeed,
            int rows, int mapColsEven, int mapColsOdd, List<TileData> tiles) {
        this.id = id;
        this.displayName = displayName;
        this.official = official;
        this.sourceType = sourceType;
        this.generationSeed = generationSeed;
        this.rows = rows;
        this.mapColsEven = mapColsEven;
        this.mapColsOdd = mapColsOdd;
        this.tiles = tiles == null ? Collections.<TileData>emptyList() : Collections.unmodifiableList(new ArrayList<>(tiles));
    }

    public static MapDefinition generatedSeed(String id, String displayName, boolean official, int generationSeed,
            int rows, int mapColsEven, int mapColsOdd) {
        return new MapDefinition(id, displayName, official, SourceType.GENERATED_SEED, generationSeed, rows, mapColsEven, mapColsOdd,
                Collections.<TileData>emptyList());
    }

    public static MapDefinition tileSnapshot(String id, String displayName, boolean official, int rows, int mapColsEven,
            int mapColsOdd, List<TileData> tiles) {
        return new MapDefinition(id, displayName, official, SourceType.TILE_SNAPSHOT, 0, rows, mapColsEven, mapColsOdd, tiles);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOfficial() {
        return official;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public int getGenerationSeed() {
        return generationSeed;
    }

    public int getRows() {
        return rows;
    }

    public int getMapColsEven() {
        return mapColsEven;
    }

    public int getMapColsOdd() {
        return mapColsOdd;
    }

    public List<TileData> getTiles() {
        return tiles;
    }

    public static class TileData {
        private final int q;
        private final int r;
        private final TerrainType terrain;
        private final Faction faction;
        private final boolean capital;
        private final boolean cityCenter;
        private final int cityId;
        private final String cityName;

        public TileData(int q, int r, TerrainType terrain, Faction faction, boolean capital) {
            this(q, r, terrain, faction, capital, capital, -1, "");
        }

        public TileData(int q, int r, TerrainType terrain, Faction faction, boolean capital, boolean cityCenter) {
            this(q, r, terrain, faction, capital, cityCenter, -1, "");
        }

        public TileData(int q, int r, TerrainType terrain, Faction faction, boolean capital, boolean cityCenter, int cityId) {
            this(q, r, terrain, faction, capital, cityCenter, cityId, "");
        }

        public TileData(int q, int r, TerrainType terrain, Faction faction, boolean capital, boolean cityCenter, int cityId, String cityName) {
            this.q = q;
            this.r = r;
            this.terrain = terrain;
            this.faction = faction;
            this.capital = capital;
            this.cityCenter = cityCenter;
            this.cityId = cityId;
            this.cityName = cityName == null ? "" : cityName;
        }

        public int getQ() {
            return q;
        }

        public int getR() {
            return r;
        }

        public TerrainType getTerrain() {
            return terrain;
        }

        public Faction getFaction() {
            return faction;
        }

        public boolean isCapital() {
            return capital;
        }

        public boolean isCityCenter() {
            return cityCenter;
        }

        public int getCityId() {
            return cityId;
        }

        public String getCityName() {
            return cityName;
        }
    }
}
