package com.lumina.hexmap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class HexMapModel {
    private static final String WORLD_MAP_IMAGE_PATH = "world_map_v0.3.png";
    private static final int[][] AXIAL_NEIGHBOR_DIRECTIONS = {
            {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };
    private static final int GENERATION_REROLL_ATTEMPTS = 3;
    private static final int LARGE_PENINSULA_MIN_TILES = 6;
    private static final int LARGE_PENINSULA_MIN_DEPTH = 4;
    private static final MainlandAssignment[] WEST_MAINLAND_ASSIGNMENTS = {
            new MainlandAssignment(Faction.NORTH_FEDERATION, 28f, 0.31f, 0.80f),
            new MainlandAssignment(Faction.HOLY_KINGDOM, 14f, 0.11f, 0.60f),
            new MainlandAssignment(Faction.EMPIRE, 32f, 0.42f, 0.58f),
            new MainlandAssignment(Faction.REPUBLIC, 13f, 0.14f, 0.34f),
            new MainlandAssignment(Faction.MARITIME_FEDERATION, 6f, 0.34f, 0.39f)
    };
    private static final float[][] WESTERN_MARITIME_ISLAND_SPECS = {
            {0.528f, 0.448f, 1f},
            {0.544f, 0.404f, 2f},
            {0.522f, 0.366f, 1f},
            {0.552f, 0.328f, 2f},
            {0.534f, 0.292f, 1f}
    };
    private static final int MAINLAND_DISTANCE_LIMIT = 16;
    private static final int MAINLAND_DISTANCE_FALLBACK_LIMIT = 20;
    private static final MainlandAssignment[] EAST_MAINLAND_ASSIGNMENTS = {
            new MainlandAssignment(Faction.DRAGON_EMPIRE, 45f, 0.65f, 0.61f),
            new MainlandAssignment(Faction.EASTERN_REPUBLIC, 25f, 0.82f, 0.60f),
            new MainlandAssignment(Faction.SANDSEA_ALLIANCE, 30f, 0.69f, 0.33f)
    };

    private final List<Tile> tiles = new ArrayList<>();
    private final Map<Long, Tile> tileIndex = new HashMap<>();
    private final HexMapConfig config;

    private float mapMinX, mapMaxX, mapMinY, mapMaxY;
    private float horizontalWrapWidth;
    private int generationSeed;
    private int generationPassIndex;

    public HexMapModel(HexMapConfig config) {
        this.config = config;
    }

    public void build() {
        buildWithSeed(ThreadLocalRandom.current().nextInt());
    }

    public void buildWithSeed(int seed) {
        generationSeed = seed;
        generationPassIndex = 0;
        tiles.clear();
        tileIndex.clear();
        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        Pixmap worldMap = loadWorldMap();

        try {
            for (int row = 0; row < config.getMapRows(); row++) {
                int maxCols = (row % 2 == 0) ? config.getMapColsOdd() : config.getMapColsEven();
                for (int col = 0; col < maxCols; col++) {
                    int[] axial = offsetToAxial(col, row);
                    Vector2 center = axialToWorld(axial[0], axial[1]);
                    float nx = normalizedColumn(col, maxCols);
                    float ny = normalizedRow(row);
                    MapSample sample = sampleWorldMap(worldMap, getContinentalSampleX(nx, ny), getContinentalSampleY(nx, ny));
                    TerrainType terrain = determineTerrain(row, nx, ny, sample);
                    Faction faction = determineFaction(terrain, nx, ny, sample);
                    Tile tile = new Tile(axial[0], axial[1], center, terrain, faction, false);
                    tiles.add(tile);
                    tileIndex.put(tileKey(tile.q, tile.r), tile);
                    minX = Math.min(minX, center.x);
                    maxX = Math.max(maxX, center.x);
                    minY = Math.min(minY, center.y);
                    maxY = Math.max(maxY, center.y);
                }
            }
        } finally {
            if (worldMap != null) {
                worldMap.dispose();
            }
        }

        float halfTileW = getHexWidth() / 2f;
        float halfTileH = config.getHexSize();
        horizontalWrapWidth = Math.max(config.getMapColsEven(), config.getMapColsOdd()) * getHexWidth();
        mapMinX = minX - halfTileW - config.getMapEdgePadding();
        mapMaxX = maxX + halfTileW + config.getMapEdgePadding();
        mapMinY = minY - halfTileH - config.getMapEdgePadding();
        mapMaxY = maxY + halfTileH + config.getMapEdgePadding();
        TileState[] baseStates = snapshotTileStates();
        runGenerationPass();
        rerollViolatingRegions(baseStates);
    }

    public void loadMapDefinition(MapDefinition mapDefinition) {
        if (mapDefinition == null) {
            build();
            return;
        }
        if (mapDefinition.getSourceType() == MapDefinition.SourceType.GENERATED_SEED) {
            buildWithSeed(mapDefinition.getGenerationSeed());
            return;
        }

        generationSeed = mapDefinition.getGenerationSeed();
        generationPassIndex = 0;
        tiles.clear();
        tileIndex.clear();
        Map<Long, MapDefinition.TileData> tileDataByKey = new HashMap<>();
        for (MapDefinition.TileData tileData : mapDefinition.getTiles()) {
            tileDataByKey.put(tileKey(tileData.getQ(), tileData.getR()), tileData);
        }

        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        for (int row = 0; row < config.getMapRows(); row++) {
            int maxCols = (row % 2 == 0) ? config.getMapColsOdd() : config.getMapColsEven();
            for (int col = 0; col < maxCols; col++) {
                int[] axial = offsetToAxial(col, row);
                Vector2 center = axialToWorld(axial[0], axial[1]);
                MapDefinition.TileData tileData = tileDataByKey.get(tileKey(axial[0], axial[1]));
                TerrainType terrain = tileData == null ? TerrainType.WATER : tileData.getTerrain();
                Faction faction = tileData == null ? null : tileData.getFaction();
                boolean capital = tileData != null && tileData.isCapital();
                Tile tile = new Tile(axial[0], axial[1], center, terrain, faction, capital);
                tiles.add(tile);
                tileIndex.put(tileKey(tile.q, tile.r), tile);
                minX = Math.min(minX, center.x);
                maxX = Math.max(maxX, center.x);
                minY = Math.min(minY, center.y);
                maxY = Math.max(maxY, center.y);
            }
        }
        updateMapBounds(minX, maxX, minY, maxY);
    }

    public MapDefinition createTileSnapshotDefinition(String id, String displayName, boolean official) {
        List<MapDefinition.TileData> tileData = new ArrayList<>();
        for (Tile tile : tiles) {
            tileData.add(new MapDefinition.TileData(tile.q, tile.r, tile.terrain, tile.faction, tile.capital));
        }
        return MapDefinition.tileSnapshot(id, displayName, official, config.getMapRows(), config.getMapColsEven(),
                config.getMapColsOdd(), tileData);
    }

    private void updateMapBounds(float minX, float maxX, float minY, float maxY) {
        float halfTileW = getHexWidth() / 2f;
        float halfTileH = config.getHexSize();
        horizontalWrapWidth = Math.max(config.getMapColsEven(), config.getMapColsOdd()) * getHexWidth();
        mapMinX = minX - halfTileW - config.getMapEdgePadding();
        mapMaxX = maxX + halfTileW + config.getMapEdgePadding();
        mapMinY = minY - halfTileH - config.getMapEdgePadding();
        mapMaxY = maxY + halfTileH + config.getMapEdgePadding();
    }

    public List<Tile> getTiles() { return tiles; }
    public float getMapMinX() { return mapMinX; }
    public float getMapMaxX() { return mapMaxX; }
    public float getMapMinY() { return mapMinY; }
    public float getMapMaxY() { return mapMaxY; }
    public float getHorizontalWrapWidth() { return horizontalWrapWidth; }

    public float getHexSize() { return config.getHexSize(); }
    public float getHexWidth() { return getHexSize() * getSqrt3(); }
    public float getHexHeight() { return 2f * getHexSize(); }
    public float getSqrt3() { return config.getSqrt3(); }
    public float getSouthPolarLimitY() { return axialToWorld(0, 0).y; }
    public float getNorthPolarLimitY() { return axialToWorld(0, config.getMapRows() - 1).y; }

    public float wrapWorldX(float worldX) {
        if (horizontalWrapWidth <= 0f) {
            return worldX;
        }

        float wrapped = worldX % horizontalWrapWidth;
        if (wrapped < 0f) {
            wrapped += horizontalWrapWidth;
        }
        return wrapped;
    }

    public Tile findTile(int q, int r) {
        return tileIndex.get(tileKey(q, r));
    }

    public Tile findTileWrapped(int q, int r) {
        Tile tile = findTile(q, r);
        if (tile != null) {
            return tile;
        }

        int wrapColumns = Math.max(config.getMapColsEven(), config.getMapColsOdd());
        if (wrapColumns <= 0) {
            return null;
        }

        tile = findTile(q - wrapColumns, r);
        if (tile != null) {
            return tile;
        }
        return findTile(q + wrapColumns, r);
    }

    public Tile getNeighborWrapped(Tile tile, int directionIndex) {
        if (tile == null || directionIndex < 0 || directionIndex >= AXIAL_NEIGHBOR_DIRECTIONS.length) {
            return null;
        }

        int[] direction = AXIAL_NEIGHBOR_DIRECTIONS[directionIndex];
        return findTileWrapped(tile.q + direction[0], tile.r + direction[1]);
    }

    public int[] worldToAxialRounded(float worldX, float worldY) {
        Vector2 axial = worldToAxial(worldX, worldY);
        return axialRound(axial.x, axial.y);
    }

    public Vector2 worldToAxial(float x, float y) {
        float q = (getSqrt3() / 3f * x - 1f / 3f * y) / getHexSize();
        float r = (2f / 3f * y) / getHexSize();
        return new Vector2(q, r);
    }

    private Pixmap loadWorldMap() {
        FileHandle file = Gdx.files.internal(WORLD_MAP_IMAGE_PATH);
        if (!file.exists()) {
            Gdx.app.log("HexMapModel", "World map image not found: " + WORLD_MAP_IMAGE_PATH);
            return null;
        }
        return new Pixmap(file);
    }

    private TerrainType determineTerrain(int row, float nx, float ny, MapSample sample) {
        TerrainType polarTerrain = determinePolarTerrain(row, nx);
        if (polarTerrain != null) {
            return polarTerrain;
        }
        if (!isLandTile(nx, ny, sample)) {
            return TerrainType.WATER;
        }

        Faction faction = determinePoliticalFaction(nx, ny, sample);
        boolean rugged = isRuggedSample(sample);
        float terrainNoise = noise((int) (nx * 1000f) + row * 13, (int) (ny * 1000f) + row * 31);
        float detailNoise = noise((int) (nx * 2000f) + row * 17, (int) (ny * 2000f) + row * 53);

        if (faction == Faction.EASTERN_REPUBLIC && isEasternMountainBelt(nx, ny) && (rugged || terrainNoise < 0.72f)) {
            return TerrainType.MOUNTAIN;
        }
        if (faction == Faction.DRAGON_EMPIRE && isEasternMountainBelt(nx, ny) && (rugged && terrainNoise < 0.45f)) {
            return TerrainType.MOUNTAIN;
        }

        switch (faction) {
            case NORTH_FEDERATION:
                if (ny > 0.80f || terrainNoise < 0.38f) {
                    return TerrainType.TUNDRA;
                }
                if (rugged && detailNoise < 0.22f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.74f) {
                    return TerrainType.FOREST;
                }
                if (terrainNoise < 0.90f) {
                    return TerrainType.HILLS;
                }
                return TerrainType.PLAIN;
            case HOLY_KINGDOM:
                if (rugged && detailNoise < 0.18f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.28f) {
                    return TerrainType.HILLS;
                }
                if (terrainNoise < 0.38f) {
                    return TerrainType.FOREST;
                }
                return TerrainType.PLAIN;
            case EMPIRE:
                if (rugged && detailNoise < 0.16f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.18f) {
                    return TerrainType.HILLS;
                }
                if (terrainNoise < 0.28f) {
                    return TerrainType.FOREST;
                }
                return TerrainType.PLAIN;
            case REPUBLIC:
                if (rugged && detailNoise < 0.18f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.30f) {
                    return TerrainType.HILLS;
                }
                if (terrainNoise < 0.42f) {
                    return TerrainType.FOREST;
                }
                return TerrainType.PLAIN;
            case MARITIME_FEDERATION:
                if (rugged && detailNoise < 0.14f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.18f) {
                    return TerrainType.HILLS;
                }
                if (terrainNoise < 0.30f) {
                    return TerrainType.FOREST;
                }
                return TerrainType.PLAIN;
            case DRAGON_EMPIRE:
                if (rugged && detailNoise < 0.24f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.34f) {
                    return TerrainType.HILLS;
                }
                if (terrainNoise < 0.44f) {
                    return TerrainType.FOREST;
                }
                return TerrainType.PLAIN;
            case EASTERN_REPUBLIC:
                if (rugged || terrainNoise < 0.48f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.84f) {
                    return TerrainType.HILLS;
                }
                return TerrainType.PLAIN;
            case SANDSEA_ALLIANCE:
                if (rugged && detailNoise < 0.20f) {
                    return TerrainType.MOUNTAIN;
                }
                if (terrainNoise < 0.58f) {
                    return TerrainType.DESERT;
                }
                if (terrainNoise < 0.84f) {
                    return TerrainType.HILLS;
                }
                return TerrainType.PLAIN;
            default:
                return TerrainType.PLAIN;
        }
    }

    private Faction determineFaction(TerrainType terrain, float nx, float ny, MapSample sample) {
        if (terrain == TerrainType.WATER || terrain.isPolar()) {
            return null;
        }
        return determinePoliticalFaction(nx, ny, sample);
    }

    private Faction determinePoliticalFaction(float nx, float ny, MapSample sample) {
        Faction best = null;
        float bestScore = Float.MAX_VALUE;
        for (Faction faction : Faction.values()) {
            Color mapColor = faction.getMapColor();
            float dr = sample.r - mapColor.r;
            float dg = sample.g - mapColor.g;
            float db = sample.b - mapColor.b;
            float colorScore = dr * dr + dg * dg + db * db;
            float dx = nx - faction.getMapCenterX();
            float dy = ny - faction.getMapCenterY();
            float positionScore = dx * dx + dy * dy;
            float regionScore = getFactionRegionScore(faction, nx, ny);
            float score = regionScore * 2.4f + colorScore * 0.9f + positionScore * 0.35f
                    + factionRandomBias(faction.ordinal() * 211 + (int) (nx * 1000f), (int) (ny * 1000f), 0.10f);
            if (score < bestScore) {
                bestScore = score;
                best = faction;
            }
        }
        return best;
    }

    private void assignFactionCapitals() {
        for (Faction faction : Faction.values()) {
            Tile capital = findBestCapitalTile(faction);
            if (capital != null) {
                capital.capital = true;
            }
        }
    }

    private void clearFactionCapitals() {
        for (Tile tile : tiles) {
            tile.capital = false;
        }
    }

    private void runGenerationPass() {
        enforceCentralOceanSeparation();
        trimLargeWesternPeninsulas();
        placeWesternMaritimeIslands();
        rebalanceContinentalTerritories();
        clearFactionCapitals();
        assignFactionCapitals();
        fixTerritoryConnectivity();
        placeWesternMaritimeIslands();
        clearFactionCapitals();
        assignFactionCapitals();
        refreshEasternMountainBorder();
    }

    private TileState[] snapshotTileStates() {
        TileState[] states = new TileState[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            states[i] = new TileState(tile.terrain, tile.faction, tile.capital);
        }
        return states;
    }

    private void rerollViolatingRegions(TileState[] baseStates) {
        for (int attempt = 0; attempt < GENERATION_REROLL_ATTEMPTS; attempt++) {
            Set<Tile> violations = collectGenerationRuleViolations();
            if (violations.isEmpty()) {
                return;
            }
            resetRegionToBase(baseStates, violations);
            generationPassIndex = attempt + 1;
            runGenerationPass();
        }
    }

    private Set<Tile> collectGenerationRuleViolations() {
        Set<Tile> violations = new HashSet<>();
        Map<Tile, Integer> landComponentIds = new HashMap<>();
        Map<Integer, Integer> landComponentSizes = new HashMap<>();
        buildLandComponents(landComponentIds, landComponentSizes);
        List<Integer> mainlandComponentIds = findLargestComponents(landComponentSizes, 2);
        Set<Integer> mainlandSet = new HashSet<>(mainlandComponentIds);
        Map<Faction, Tile> capitalTiles = findCapitalTiles();

        for (Map.Entry<Faction, Tile> entry : capitalTiles.entrySet()) {
            Faction faction = entry.getKey();
            Tile capitalTile = entry.getValue();
            Integer componentId = landComponentIds.get(capitalTile);
            if (componentId == null || !mainlandSet.contains(componentId)) {
                continue;
            }

            Map<Tile, Integer> factionDistances = computeFactionDistances(capitalTile, componentId, faction, landComponentIds);
            List<Tile> componentTiles = getComponentTiles(componentId, landComponentIds);
            MainlandAssignment[] assignments = getMainlandAssignmentsForComponent(componentId, landComponentIds);
            Map<Faction, Integer> targetCounts = buildTargetCounts(componentTiles.size(), assignments);
            int distanceLimit = determineMainlandDistanceLimit(factionDistances, componentTiles, targetCounts.getOrDefault(faction, 0));
            for (Tile tile : tiles) {
                if (tile.faction != faction) {
                    continue;
                }
                Integer tileComponentId = landComponentIds.get(tile);
                if (tileComponentId == null || tileComponentId != componentId) {
                    continue;
                }
                if (!isWithinMainlandDistance(factionDistances, tile, distanceLimit)) {
                    violations.add(tile);
                }
            }
        }

        for (Tile tile : tiles) {
            if (tile.faction != Faction.MARITIME_FEDERATION) {
                continue;
            }
            Integer componentId = landComponentIds.get(tile);
            if (componentId != null && mainlandSet.contains(componentId)) {
                continue;
            }
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor == null || neighbor.terrain == TerrainType.WATER || neighbor.terrain.isPolar()) {
                    continue;
                }
                if (neighbor.faction != Faction.MARITIME_FEDERATION) {
                    violations.add(tile);
                    break;
                }
            }
        }

        return violations;
    }

    private void resetRegionToBase(TileState[] baseStates, Set<Tile> violations) {
        Set<Tile> resetTiles = new HashSet<>();
        for (Tile tile : violations) {
            resetTiles.add(tile);
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor != null) {
                    resetTiles.add(neighbor);
                }
            }
        }

        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            if (!resetTiles.contains(tile)) {
                continue;
            }
            TileState state = baseStates[i];
            tile.terrain = state.terrain;
            tile.faction = state.faction;
            tile.capital = state.capital;
        }
    }

    private void fixTerritoryConnectivity() {
        Map<Tile, Integer> landComponentIds = new HashMap<>();
        Map<Integer, Integer> landComponentSizes = new HashMap<>();
        buildLandComponents(landComponentIds, landComponentSizes);
        if (landComponentSizes.isEmpty()) {
            return;
        }

        Map<Faction, Tile> capitalTiles = findCapitalTiles();
        Map<Faction, Integer> capitalComponentIds = new HashMap<>();
        for (Map.Entry<Faction, Tile> entry : capitalTiles.entrySet()) {
            Integer componentId = landComponentIds.get(entry.getValue());
            if (componentId != null) {
                capitalComponentIds.put(entry.getKey(), componentId);
            }
        }

        List<Integer> mainlandComponentIds = findLargestComponents(landComponentSizes, 2);
        cleanMainlandTerritories(mainlandComponentIds, landComponentIds, capitalTiles, capitalComponentIds);
        cleanIslandTerritories(mainlandComponentIds, landComponentIds, capitalTiles, capitalComponentIds);
    }

    private void buildLandComponents(Map<Tile, Integer> componentIds, Map<Integer, Integer> componentSizes) {
        int nextComponentId = 0;
        for (Tile tile : tiles) {
            if (!isConnectedLand(tile) || componentIds.containsKey(tile)) {
                continue;
            }
            int componentId = nextComponentId++;
            int componentSize = floodFillLandComponent(tile, componentId, componentIds);
            componentSizes.put(componentId, componentSize);
        }
    }

    private void rebalanceContinentalTerritories() {
        Map<Tile, Integer> landComponentIds = new HashMap<>();
        Map<Integer, Integer> landComponentSizes = new HashMap<>();
        buildLandComponents(landComponentIds, landComponentSizes);

        List<Integer> mainlandComponentIds = findLargestComponents(landComponentSizes, 2);
        if (mainlandComponentIds.size() < 2) {
            return;
        }

        int westMainlandId = mainlandComponentIds.get(0);
        int eastMainlandId = mainlandComponentIds.get(1);
        if (getComponentAverageNormalizedX(westMainlandId, landComponentIds) > getComponentAverageNormalizedX(eastMainlandId, landComponentIds)) {
            westMainlandId = mainlandComponentIds.get(1);
            eastMainlandId = mainlandComponentIds.get(0);
        }

        assignMainlandTerritories(westMainlandId, landComponentIds, WEST_MAINLAND_ASSIGNMENTS, MainlandZone.WEST);
        assignMainlandTerritories(eastMainlandId, landComponentIds, EAST_MAINLAND_ASSIGNMENTS, MainlandZone.EAST);
        assignWesternMaritimeArchipelago(landComponentIds, westMainlandId, eastMainlandId);
        enforceEasternMountainBorder(eastMainlandId, landComponentIds);
    }

    private void enforceCentralOceanSeparation() {
        for (Tile tile : tiles) {
            float nx = normalizedWorldX(tile.center.x);
            float ny = normalizedRow(tile.r);
            if (!isCentralOceanBarrier(nx, ny)) {
                continue;
            }
            tile.terrain = TerrainType.WATER;
            tile.faction = null;
            tile.capital = false;
        }
    }

    private void trimLargeWesternPeninsulas() {
        Map<Tile, Integer> landComponentIds = new HashMap<>();
        Map<Integer, Integer> landComponentSizes = new HashMap<>();
        buildLandComponents(landComponentIds, landComponentSizes);
        List<Integer> mainlandComponentIds = findLargestComponents(landComponentSizes, 2);
        if (mainlandComponentIds.size() < 2) {
            return;
        }

        int westMainlandId = mainlandComponentIds.get(0);
        int eastMainlandId = mainlandComponentIds.get(1);
        if (getComponentAverageNormalizedX(westMainlandId, landComponentIds) > getComponentAverageNormalizedX(eastMainlandId, landComponentIds)) {
            westMainlandId = mainlandComponentIds.get(1);
        }

        float mainlandAverageX = getComponentAverageNormalizedX(westMainlandId, landComponentIds);
        Set<Tile> peninsulaTiles = new HashSet<>();
        for (Tile tile : getComponentTiles(westMainlandId, landComponentIds)) {
            if (!isWesternPeninsulaNeckCandidate(tile, westMainlandId, mainlandAverageX, landComponentIds)) {
                continue;
            }
            peninsulaTiles.addAll(findWesternPeninsulaBranch(tile, westMainlandId, mainlandAverageX, landComponentIds));
        }

        for (Tile tile : peninsulaTiles) {
            float nx = normalizedWorldX(tile.center.x);
            float ny = normalizedRow(tile.r);
            if (isWesternMaritimeArchipelago(nx, ny) || isWesternMaritimeChannel(nx, ny) || isCentralSeaIslands(nx, ny)) {
                continue;
            }
            tile.terrain = TerrainType.WATER;
            tile.faction = null;
            tile.capital = false;
        }
    }

    private void placeWesternMaritimeIslands() {
        Set<Tile> reservedTiles = new HashSet<>();
        for (Tile tile : tiles) {
            float nx = normalizedWorldX(tile.center.x);
            float ny = normalizedRow(tile.r);
            if (isWesternMaritimeChannel(nx, ny) || isWesternMaritimeArchipelago(nx, ny)) {
                tile.terrain = TerrainType.WATER;
                tile.faction = null;
                tile.capital = false;
            }
        }

        for (float[] islandSpec : WESTERN_MARITIME_ISLAND_SPECS) {
            Tile anchor = findClosestIslandTile(islandSpec[0], islandSpec[1], reservedTiles);
            if (anchor == null) {
                continue;
            }
            List<Tile> islandTiles = new ArrayList<>();
            islandTiles.add(anchor);
            if ((int) islandSpec[2] >= 2) {
                Tile neighbor = findBestIslandNeighbor(anchor, islandSpec[0], islandSpec[1], reservedTiles);
                if (neighbor != null) {
                    islandTiles.add(neighbor);
                }
            }
            carveIsolatedIsland(islandTiles, reservedTiles);
        }
    }

    private boolean isWesternPeninsulaNeckCandidate(Tile tile, int componentId, float mainlandAverageX,
            Map<Tile, Integer> landComponentIds) {
        float nx = normalizedWorldX(tile.center.x);
        float ny = normalizedRow(tile.r);
        if (nx < mainlandAverageX + normalizedTileSpan(3.0f)) {
            return false;
        }
        if (ny > 0.44f && ny < 0.70f) {
            return false;
        }
        int sameComponentNeighbors = 0;
        for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
            Tile neighbor = getNeighborWrapped(tile, directionIndex);
            if (neighbor == null || neighbor.terrain == TerrainType.WATER || neighbor.terrain.isPolar()) {
                continue;
            }
            Integer neighborComponentId = landComponentIds.get(neighbor);
            if (neighborComponentId != null && neighborComponentId == componentId) {
                sameComponentNeighbors++;
            }
        }
        return sameComponentNeighbors >= 2 && sameComponentNeighbors <= 3;
    }

    private Set<Tile> findWesternPeninsulaBranch(Tile neckTile, int componentId, float mainlandAverageX,
            Map<Tile, Integer> landComponentIds) {
        Set<Tile> peninsulaTiles = new HashSet<>();
        Set<Tile> visited = new HashSet<>();
        for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
            Tile neighbor = getNeighborWrapped(neckTile, directionIndex);
            if (neighbor == null || visited.contains(neighbor)) {
                continue;
            }
            Integer neighborComponentId = landComponentIds.get(neighbor);
            if (neighborComponentId == null || neighborComponentId != componentId) {
                continue;
            }
            List<Tile> branch = collectBranchExcludingNeck(neighbor, neckTile, componentId, landComponentIds, visited);
            if (isWesternPeninsulaBranch(branch, neckTile, mainlandAverageX)) {
                peninsulaTiles.addAll(branch);
            }
        }
        return peninsulaTiles;
    }

    private List<Tile> collectBranchExcludingNeck(Tile startTile, Tile neckTile, int componentId,
            Map<Tile, Integer> landComponentIds, Set<Tile> visited) {
        List<Tile> branchTiles = new ArrayList<>();
        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.addLast(startTile);
        visited.add(startTile);

        while (!queue.isEmpty()) {
            Tile tile = queue.removeFirst();
            branchTiles.add(tile);
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor == null || neighbor == neckTile || visited.contains(neighbor)) {
                    continue;
                }
                Integer neighborComponentId = landComponentIds.get(neighbor);
                if (neighborComponentId == null || neighborComponentId != componentId) {
                    continue;
                }
                visited.add(neighbor);
                queue.addLast(neighbor);
            }
        }

        return branchTiles;
    }

    private boolean isWesternPeninsulaBranch(List<Tile> branchTiles, Tile neckTile, float mainlandAverageX) {
        if (branchTiles.size() < LARGE_PENINSULA_MIN_TILES) {
            return false;
        }

        float totalX = 0f;
        float totalY = 0f;
        float maxX = 0f;
        int maxDepth = 0;
        Set<Tile> branchSet = new HashSet<>(branchTiles);
        int narrowTiles = 0;
        for (Tile tile : branchTiles) {
            float nx = normalizedWorldX(tile.center.x);
            float ny = normalizedRow(tile.r);
            totalX += nx;
            totalY += ny;
            maxX = Math.max(maxX, nx);
            maxDepth = Math.max(maxDepth, wrappedHexDistance(neckTile, tile));
            if (countNeighborsInSet(tile, branchSet) <= 3) {
                narrowTiles++;
            }
        }

        float averageX = totalX / branchTiles.size();
        float averageY = totalY / branchTiles.size();
        boolean upperOrLowerFlank = averageY <= 0.42f || averageY >= 0.72f;
        boolean eastwardBranch = averageX > mainlandAverageX + normalizedTileSpan(4.2f)
                && maxX > normalizedWorldX(neckTile.center.x) + normalizedTileSpan(1.2f);
        boolean narrowBranch = narrowTiles * 2 >= branchTiles.size();
        return upperOrLowerFlank && eastwardBranch && narrowBranch && maxDepth >= LARGE_PENINSULA_MIN_DEPTH;
    }

    private int countNeighborsInSet(Tile tile, Set<Tile> tileSet) {
        int count = 0;
        for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
            Tile neighbor = getNeighborWrapped(tile, directionIndex);
            if (neighbor != null && tileSet.contains(neighbor)) {
                count++;
            }
        }
        return count;
    }

    private void assignMainlandTerritories(int componentId, Map<Tile, Integer> landComponentIds,
            MainlandAssignment[] assignments, MainlandZone zone) {
        List<Tile> componentTiles = getComponentTiles(componentId, landComponentIds);
        if (componentTiles.isEmpty()) {
            return;
        }

        Map<Faction, Integer> targetCounts = buildTargetCounts(componentTiles.size(), assignments);
        Map<Faction, Set<Tile>> ownedTiles = new HashMap<>();
        Set<Tile> usedSeeds = new HashSet<>();
        Set<Tile> unassignedTiles = new HashSet<>(componentTiles);

        for (MainlandAssignment assignment : assignments) {
            ownedTiles.put(assignment.faction, new HashSet<Tile>());
        }

        for (MainlandAssignment assignment : assignments) {
            Tile seedTile = findBestSeedTile(componentTiles, usedSeeds, assignment, zone);
            if (seedTile == null) {
                continue;
            }
            seedTile.faction = assignment.faction;
            ownedTiles.get(assignment.faction).add(seedTile);
            usedSeeds.add(seedTile);
            unassignedTiles.remove(seedTile);
        }

        while (!unassignedTiles.isEmpty()) {
            boolean hasQuotaPressure = hasUnmetTargets(assignments, targetCounts, ownedTiles);
            Tile bestTile = null;
            Faction bestFaction = null;
            float bestPriority = Float.MAX_VALUE;

            for (MainlandAssignment assignment : assignments) {
                Set<Tile> factionTiles = ownedTiles.get(assignment.faction);
                if (factionTiles == null || factionTiles.isEmpty()) {
                    continue;
                }

                TileCandidate candidate = findBestExpansionCandidate(unassignedTiles, factionTiles, assignment, zone);
                if (candidate == null) {
                    continue;
                }

                int ownedCount = factionTiles.size();
                int targetCount = Math.max(1, targetCounts.getOrDefault(assignment.faction, 0));
                float quotaPenalty = 0f;
                if (hasQuotaPressure) {
                    if (ownedCount >= targetCount) {
                        quotaPenalty += 4f + (ownedCount - targetCount) * 0.15f;
                    } else {
                        quotaPenalty += (ownedCount / (float) targetCount) * 0.35f;
                    }
                }

                float priority = candidate.score + quotaPenalty;
                if (priority < bestPriority) {
                    bestPriority = priority;
                    bestTile = candidate.tile;
                    bestFaction = assignment.faction;
                }
            }

            if (bestTile == null || bestFaction == null) {
                break;
            }

            bestTile.faction = bestFaction;
            ownedTiles.get(bestFaction).add(bestTile);
            unassignedTiles.remove(bestTile);
        }

        if (!unassignedTiles.isEmpty()) {
            for (Tile tile : unassignedTiles) {
                Faction fallbackFaction = findClosestAssignedFaction(tile, assignments, zone);
                if (fallbackFaction != null) {
                    tile.faction = fallbackFaction;
                }
            }
        }
    }

    private void assignWesternMaritimeArchipelago(Map<Tile, Integer> landComponentIds, int westMainlandId, int eastMainlandId) {
        for (Tile tile : tiles) {
            Integer componentId = landComponentIds.get(tile);
            if (componentId == null || componentId == westMainlandId || componentId == eastMainlandId) {
                continue;
            }

            float nx = normalizedWorldX(tile.center.x);
            float ny = normalizedRow(tile.r);
            if (isWesternMaritimeArchipelago(nx, ny)) {
                tile.faction = Faction.MARITIME_FEDERATION;
            }
        }
    }

    private void enforceEasternMountainBorder(int eastMainlandId, Map<Tile, Integer> landComponentIds) {
        for (Tile tile : tiles) {
            Integer componentId = landComponentIds.get(tile);
            if (componentId == null || componentId != eastMainlandId) {
                continue;
            }
            if (tile.faction != Faction.DRAGON_EMPIRE && tile.faction != Faction.EASTERN_REPUBLIC) {
                continue;
            }
            if (!hasEasternRivalNeighbor(tile, landComponentIds, eastMainlandId)) {
                continue;
            }

            float roll = noise(tile.q * 41 + 977, tile.r * 67 + 421);
            if (roll <= 0.80f) {
                tile.terrain = TerrainType.MOUNTAIN;
            } else if (tile.terrain == TerrainType.PLAIN || tile.terrain == TerrainType.FOREST
                    || tile.terrain == TerrainType.DESERT || tile.terrain == TerrainType.TUNDRA) {
                tile.terrain = TerrainType.HILLS;
            }
        }
    }

    private void refreshEasternMountainBorder() {
        Map<Tile, Integer> landComponentIds = new HashMap<>();
        Map<Integer, Integer> landComponentSizes = new HashMap<>();
        buildLandComponents(landComponentIds, landComponentSizes);
        List<Integer> mainlandComponentIds = findLargestComponents(landComponentSizes, 2);
        if (mainlandComponentIds.size() < 2) {
            return;
        }

        int firstId = mainlandComponentIds.get(0);
        int secondId = mainlandComponentIds.get(1);
        int eastMainlandId = getComponentAverageNormalizedX(firstId, landComponentIds)
                > getComponentAverageNormalizedX(secondId, landComponentIds) ? firstId : secondId;
        enforceEasternMountainBorder(eastMainlandId, landComponentIds);
    }

    private void cleanMainlandTerritories(List<Integer> mainlandComponentIds, Map<Tile, Integer> landComponentIds,
            Map<Faction, Tile> capitalTiles, Map<Faction, Integer> capitalComponentIds) {
        for (int componentId : mainlandComponentIds) {
            List<Tile> componentTiles = getComponentTiles(componentId, landComponentIds);
            MainlandAssignment[] assignments = getMainlandAssignmentsForComponent(componentId, landComponentIds);
            Map<Faction, Integer> targetCounts = buildTargetCounts(componentTiles.size(), assignments);
            Set<Tile> anchoredTiles = new HashSet<>();
            List<Faction> allowedFactions = new ArrayList<>();
            Map<Faction, Integer> factionDistanceLimits = new HashMap<>();
            Map<Faction, Map<Tile, Integer>> factionCapitalDistances = new HashMap<>();

            for (Faction faction : Faction.values()) {
                Integer capitalComponentId = capitalComponentIds.get(faction);
                Tile capitalTile = capitalTiles.get(faction);
                if (capitalTile == null || capitalComponentId == null || capitalComponentId != componentId) {
                    continue;
                }

                allowedFactions.add(faction);
                Map<Tile, Integer> capitalDistances = computeFactionDistances(capitalTile, componentId, faction, landComponentIds);
                factionCapitalDistances.put(faction, capitalDistances);
                int distanceLimit = determineMainlandDistanceLimit(capitalDistances, componentTiles,
                        targetCounts.getOrDefault(faction, 0));
                factionDistanceLimits.put(faction, distanceLimit);
                anchoredTiles.addAll(collectFactionTiles(capitalTile, componentId, faction, landComponentIds, capitalDistances,
                        distanceLimit));
            }

            if (allowedFactions.isEmpty()) {
                continue;
            }

            List<Tile> pendingTiles = new ArrayList<>();
            for (Tile tile : tiles) {
                Integer tileComponentId = landComponentIds.get(tile);
                if (tileComponentId == null || tileComponentId != componentId || anchoredTiles.contains(tile)) {
                    continue;
                }
                pendingTiles.add(tile);
            }

            redistributePendingTiles(componentId, pendingTiles, anchoredTiles, allowedFactions, landComponentIds, capitalTiles,
                    factionDistanceLimits, factionCapitalDistances);
        }
    }

    private List<Tile> getComponentTiles(int componentId, Map<Tile, Integer> landComponentIds) {
        List<Tile> componentTiles = new ArrayList<>();
        for (Tile tile : tiles) {
            Integer tileComponentId = landComponentIds.get(tile);
            if (tileComponentId != null && tileComponentId == componentId) {
                componentTiles.add(tile);
            }
        }
        return componentTiles;
    }

    private float getComponentAverageNormalizedX(int componentId, Map<Tile, Integer> landComponentIds) {
        float total = 0f;
        int count = 0;
        for (Tile tile : tiles) {
            Integer tileComponentId = landComponentIds.get(tile);
            if (tileComponentId == null || tileComponentId != componentId) {
                continue;
            }
            total += normalizedWorldX(tile.center.x);
            count++;
        }
        return count == 0 ? 0.5f : total / count;
    }

    private Map<Faction, Integer> buildTargetCounts(int totalTiles, MainlandAssignment[] assignments) {
        Map<Faction, Integer> targetCounts = new HashMap<>();
        float totalShare = 0f;
        for (MainlandAssignment assignment : assignments) {
            totalShare += assignment.share;
        }

        int assignedCount = 0;
        float bestRemainder = -1f;
        Faction bestRemainderFaction = null;
        for (MainlandAssignment assignment : assignments) {
            float exactCount = totalTiles * (assignment.share / totalShare);
            int count = (int) Math.floor(exactCount);
            targetCounts.put(assignment.faction, count);
            assignedCount += count;

            float remainder = exactCount - count;
            if (remainder > bestRemainder) {
                bestRemainder = remainder;
                bestRemainderFaction = assignment.faction;
            }
        }

        int remaining = totalTiles - assignedCount;
        while (remaining > 0 && bestRemainderFaction != null) {
            targetCounts.put(bestRemainderFaction, targetCounts.get(bestRemainderFaction) + 1);
            remaining--;
            bestRemainderFaction = findNextRemainderFaction(targetCounts, totalTiles, assignments);
        }
        return targetCounts;
    }

    private Faction findNextRemainderFaction(Map<Faction, Integer> targetCounts, int totalTiles, MainlandAssignment[] assignments) {
        float totalShare = 0f;
        for (MainlandAssignment assignment : assignments) {
            totalShare += assignment.share;
        }

        Faction bestFaction = null;
        float bestGap = -Float.MAX_VALUE;
        for (MainlandAssignment assignment : assignments) {
            float desired = totalTiles * (assignment.share / totalShare);
            float gap = desired - targetCounts.getOrDefault(assignment.faction, 0);
            if (gap > bestGap) {
                bestGap = gap;
                bestFaction = assignment.faction;
            }
        }
        return bestFaction;
    }

    private MainlandAssignment[] getMainlandAssignmentsForComponent(int componentId, Map<Tile, Integer> landComponentIds) {
        return getComponentAverageNormalizedX(componentId, landComponentIds) < 0.5f
                ? WEST_MAINLAND_ASSIGNMENTS
                : EAST_MAINLAND_ASSIGNMENTS;
    }

    private int determineMainlandDistanceLimit(Map<Tile, Integer> capitalDistances, List<Tile> componentTiles, int targetCount) {
        int reachableWithinDefault = 0;
        for (Tile tile : componentTiles) {
            if (isWithinMainlandDistance(capitalDistances, tile, MAINLAND_DISTANCE_LIMIT)) {
                reachableWithinDefault++;
            }
        }
        return reachableWithinDefault >= targetCount ? MAINLAND_DISTANCE_LIMIT : MAINLAND_DISTANCE_FALLBACK_LIMIT;
    }

    private Map<Tile, Integer> computeFactionDistances(Tile startTile, int componentId, Faction faction,
            Map<Tile, Integer> landComponentIds) {
        Map<Tile, Integer> distances = new HashMap<>();
        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.addLast(startTile);
        distances.put(startTile, 0);

        while (!queue.isEmpty()) {
            Tile tile = queue.removeFirst();
            int nextDistance = distances.get(tile) + 1;
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor == null || distances.containsKey(neighbor)) {
                    continue;
                }
                if (neighbor.faction != faction) {
                    continue;
                }
                Integer neighborComponentId = landComponentIds.get(neighbor);
                if (neighborComponentId == null || neighborComponentId != componentId) {
                    continue;
                }
                distances.put(neighbor, nextDistance);
                queue.addLast(neighbor);
            }
        }

        return distances;
    }

    private boolean isWithinMainlandDistance(Map<Tile, Integer> capitalDistances, Tile tile, int distanceLimit) {
        Integer distance = capitalDistances.get(tile);
        return distance != null && distance <= distanceLimit;
    }

    private Tile findClosestIslandTile(float centerX, float centerY, Set<Tile> reservedTiles) {
        Tile bestTile = null;
        float bestScore = Float.MAX_VALUE;
        for (Tile tile : tiles) {
            if (tile.terrain.isPolar() || reservedTiles.contains(tile)) {
                continue;
            }
            float nx = normalizedWorldX(tile.center.x);
            float ny = normalizedRow(tile.r);
            if (!isWesternMaritimeArchipelago(nx, ny)) {
                continue;
            }
            if (!hasValidMaritimeIslandConnection(tile, reservedTiles)) {
                continue;
            }
            float score = locationBias(tile, centerX, centerY)
                    + tileRandomBias(tile, 601, 0.025f);
            if (score < bestScore) {
                bestScore = score;
                bestTile = tile;
            }
        }
        return bestTile;
    }

    private Tile findBestIslandNeighbor(Tile anchor, float centerX, float centerY, Set<Tile> reservedTiles) {
        Tile bestTile = null;
        float bestScore = Float.MAX_VALUE;
        for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
            Tile neighbor = getNeighborWrapped(anchor, directionIndex);
            if (neighbor == null || neighbor.terrain.isPolar() || reservedTiles.contains(neighbor)) {
                continue;
            }
            float nx = normalizedWorldX(neighbor.center.x);
            float ny = normalizedRow(neighbor.r);
            if (!isWesternMaritimeArchipelago(nx, ny)) {
                continue;
            }
            float score = locationBias(neighbor, centerX, centerY)
                    + tileRandomBias(neighbor, 733, 0.020f);
            if (score < bestScore) {
                bestScore = score;
                bestTile = neighbor;
            }
        }
        return bestTile;
    }

    private boolean hasValidMaritimeIslandConnection(Tile candidateTile, Set<Tile> reservedTiles) {
        for (Tile tile : tiles) {
            if (tile.faction != Faction.MARITIME_FEDERATION) {
                continue;
            }

            int distance = wrappedHexDistance(candidateTile, tile);
            if (distance < 2 || distance > 3) {
                continue;
            }
            if (hasWaterOnlyPath(candidateTile, tile, distance)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWaterOnlyPath(Tile startTile, Tile targetTile, int maxSteps) {
        ArrayDeque<WaterPathNode> queue = new ArrayDeque<>();
        Set<Tile> visited = new HashSet<>();
        queue.addLast(new WaterPathNode(startTile, 0));
        visited.add(startTile);

        while (!queue.isEmpty()) {
            WaterPathNode node = queue.removeFirst();
            if (node.steps >= maxSteps) {
                continue;
            }

            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(node.tile, directionIndex);
                if (neighbor == null) {
                    continue;
                }
                if (neighbor == targetTile) {
                    return node.steps + 1 == maxSteps;
                }
                if (visited.contains(neighbor) || neighbor.terrain != TerrainType.WATER) {
                    continue;
                }
                visited.add(neighbor);
                queue.addLast(new WaterPathNode(neighbor, node.steps + 1));
            }
        }
        return false;
    }

    private void carveIsolatedIsland(List<Tile> islandTiles, Set<Tile> reservedTiles) {
        Set<Tile> islandSet = new HashSet<>(islandTiles);
        for (Tile tile : islandTiles) {
            tile.terrain = TerrainType.PLAIN;
            tile.faction = Faction.MARITIME_FEDERATION;
            tile.capital = false;
        }

        for (Tile tile : islandTiles) {
            reservedTiles.add(tile);
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor == null || neighbor.terrain.isPolar()) {
                    continue;
                }
                if (!islandSet.contains(neighbor)) {
                    neighbor.terrain = TerrainType.WATER;
                    neighbor.faction = null;
                    neighbor.capital = false;
                }
                reservedTiles.add(neighbor);
            }
        }
    }

    private Tile findBestSeedTile(List<Tile> componentTiles, Set<Tile> usedSeeds, MainlandAssignment assignment, MainlandZone zone) {
        Tile bestTile = null;
        float bestScore = Float.MAX_VALUE;
        for (Tile tile : componentTiles) {
            if (usedSeeds.contains(tile)) {
                continue;
            }
            float score = getContinentClaimScore(tile, assignment.faction, zone)
                    + locationBias(tile, assignment.seedX, assignment.seedY) * 0.8f
                    + tileFactionRandomBias(tile, assignment.faction, 809, 0.030f);
            if (score < bestScore) {
                bestScore = score;
                bestTile = tile;
            }
        }
        return bestTile;
    }

    private boolean hasUnmetTargets(MainlandAssignment[] assignments, Map<Faction, Integer> targetCounts, Map<Faction, Set<Tile>> ownedTiles) {
        for (MainlandAssignment assignment : assignments) {
            int target = targetCounts.getOrDefault(assignment.faction, 0);
            int current = ownedTiles.get(assignment.faction).size();
            if (current < target) {
                return true;
            }
        }
        return false;
    }

    private TileCandidate findBestExpansionCandidate(Set<Tile> unassignedTiles, Set<Tile> ownedTiles,
            MainlandAssignment assignment, MainlandZone zone) {
        Tile bestTile = null;
        float bestScore = Float.MAX_VALUE;

        for (Tile tile : unassignedTiles) {
            int adjacentOwned = countAdjacentOwnedTiles(tile, ownedTiles);
            boolean isFrontier = adjacentOwned > 0;
            float score = getContinentClaimScore(tile, assignment.faction, zone)
                    + locationBias(tile, assignment.seedX, assignment.seedY) * 0.25f
                    - adjacentOwned * 0.55f
                    + (isFrontier ? 0f : 1.5f);
            if (score < bestScore) {
                bestScore = score;
                bestTile = tile;
            }
        }

        return bestTile == null ? null : new TileCandidate(bestTile, bestScore);
    }

    private int countAdjacentOwnedTiles(Tile tile, Set<Tile> ownedTiles) {
        int count = 0;
        for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
            Tile neighbor = getNeighborWrapped(tile, directionIndex);
            if (neighbor != null && ownedTiles.contains(neighbor)) {
                count++;
            }
        }
        return count;
    }

    private Faction findClosestAssignedFaction(Tile tile, MainlandAssignment[] assignments, MainlandZone zone) {
        Faction bestFaction = null;
        float bestScore = Float.MAX_VALUE;
        for (MainlandAssignment assignment : assignments) {
            float score = getContinentClaimScore(tile, assignment.faction, zone)
                    + locationBias(tile, assignment.seedX, assignment.seedY) * 0.25f;
            if (score < bestScore) {
                bestScore = score;
                bestFaction = assignment.faction;
            }
        }
        return bestFaction;
    }

    private float getContinentClaimScore(Tile tile, Faction faction, MainlandZone zone) {
        float nx = normalizedWorldX(tile.center.x);
        float ny = normalizedRow(tile.r);
        float randomBias = tileFactionRandomBias(tile, faction, 911, 0.050f);
        if (zone == MainlandZone.WEST) {
            switch (faction) {
                case NORTH_FEDERATION:
                    return minScore(
                            regionScore(nx, ny, 0.30f, 0.82f, 0.12f, 0.09f),
                            regionScore(nx, ny, 0.34f, 0.73f, 0.11f, 0.09f),
                            regionScore(nx, ny, 0.25f, 0.78f, 0.09f, 0.08f)) + randomBias;
                case HOLY_KINGDOM:
                    return minScore(
                            regionScore(nx, ny, 0.09f, 0.69f, 0.08f, 0.12f),
                            regionScore(nx, ny, 0.13f, 0.61f, 0.08f, 0.10f),
                            regionScore(nx, ny, 0.18f, 0.54f, 0.08f, 0.09f),
                            regionScore(nx, ny, 0.10f, 0.48f, 0.05f, 0.07f)) + randomBias;
                case EMPIRE:
                    return minScore(
                            regionScore(nx, ny, 0.42f, 0.66f, 0.10f, 0.10f),
                            regionScore(nx, ny, 0.43f, 0.55f, 0.11f, 0.10f),
                            regionScore(nx, ny, 0.34f, 0.55f, 0.08f, 0.08f)) + randomBias;
                case REPUBLIC:
                    return minScore(
                            regionScore(nx, ny, 0.12f, 0.40f, 0.10f, 0.09f),
                            regionScore(nx, ny, 0.19f, 0.32f, 0.10f, 0.08f),
                            regionScore(nx, ny, 0.09f, 0.28f, 0.06f, 0.06f)) + randomBias;
                case MARITIME_FEDERATION:
                    return minScore(
                            regionScore(nx, ny, 0.34f, 0.42f, 0.07f, 0.07f),
                            regionScore(nx, ny, 0.38f, 0.33f, 0.06f, 0.06f),
                            regionScore(nx, ny, 0.30f, 0.27f, 0.05f, 0.05f)) + randomBias;
                default:
                    return getFactionRegionScore(faction, nx, ny) + randomBias;
            }
        }

        switch (faction) {
            case DRAGON_EMPIRE:
                return minScore(
                        regionScore(nx, ny, 0.65f, 0.69f, 0.12f, 0.10f),
                        regionScore(nx, ny, 0.64f, 0.56f, 0.12f, 0.12f),
                        regionScore(nx, ny, 0.59f, 0.60f, 0.07f, 0.09f)) + randomBias;
            case EASTERN_REPUBLIC:
                return minScore(
                        regionScore(nx, ny, 0.82f, 0.64f, 0.09f, 0.11f),
                        regionScore(nx, ny, 0.84f, 0.54f, 0.07f, 0.09f),
                        regionScore(nx, ny, 0.76f, 0.58f, 0.05f, 0.09f)) + randomBias;
            case SANDSEA_ALLIANCE:
                return minScore(
                        regionScore(nx, ny, 0.69f, 0.38f, 0.15f, 0.11f),
                        regionScore(nx, ny, 0.76f, 0.28f, 0.12f, 0.09f),
                        regionScore(nx, ny, 0.61f, 0.28f, 0.10f, 0.08f)) + randomBias;
            default:
                return getFactionRegionScore(faction, nx, ny) + randomBias;
        }
    }

    private float locationBias(Tile tile, float centerX, float centerY) {
        float nx = normalizedWorldX(tile.center.x);
        float ny = normalizedRow(tile.r);
        float dx = wrappedNormalizedDelta(nx, centerX);
        float dy = ny - centerY;
        return dx * dx + dy * dy;
    }

    private boolean hasEasternRivalNeighbor(Tile tile, Map<Tile, Integer> landComponentIds, int eastMainlandId) {
        for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
            Tile neighbor = getNeighborWrapped(tile, directionIndex);
            if (neighbor == null || neighbor.faction == null) {
                continue;
            }
            Integer componentId = landComponentIds.get(neighbor);
            if (componentId == null || componentId != eastMainlandId) {
                continue;
            }
            if ((tile.faction == Faction.DRAGON_EMPIRE && neighbor.faction == Faction.EASTERN_REPUBLIC)
                    || (tile.faction == Faction.EASTERN_REPUBLIC && neighbor.faction == Faction.DRAGON_EMPIRE)) {
                return true;
            }
        }
        return false;
    }

    private void cleanIslandTerritories(List<Integer> mainlandComponentIds, Map<Tile, Integer> landComponentIds,
            Map<Faction, Tile> capitalTiles, Map<Faction, Integer> capitalComponentIds) {
        Set<Integer> mainlandSet = new HashSet<>(mainlandComponentIds);
        Set<Tile> visitedFactionClusters = new HashSet<>();

        for (Tile seedTile : tiles) {
            Integer componentId = landComponentIds.get(seedTile);
            if (componentId == null || mainlandSet.contains(componentId) || visitedFactionClusters.contains(seedTile)) {
                continue;
            }

            Set<Tile> anchoredTiles = new HashSet<>();
            Set<Faction> anchoredFactions = new HashSet<>();
            List<Tile> islandTiles = new ArrayList<>();
            for (Tile tile : tiles) {
                Integer tileComponentId = landComponentIds.get(tile);
                if (tileComponentId != null && tileComponentId == componentId) {
                    islandTiles.add(tile);
                }
            }

            for (Tile tile : islandTiles) {
                if (visitedFactionClusters.contains(tile) || tile.faction == null) {
                    continue;
                }

                List<Tile> factionCluster = collectFactionCluster(tile, componentId, landComponentIds, visitedFactionClusters);
                if (shouldKeepIslandCluster(factionCluster, capitalTiles, capitalComponentIds, componentId)) {
                    anchoredTiles.addAll(factionCluster);
                    anchoredFactions.add(tile.faction);
                }
            }

            List<Tile> pendingTiles = new ArrayList<>();
            for (Tile tile : islandTiles) {
                if (!anchoredTiles.contains(tile)) {
                    pendingTiles.add(tile);
                }
            }

            if (pendingTiles.isEmpty()) {
                continue;
            }

            if (anchoredFactions.isEmpty()) {
                List<Faction> candidateFactions = new ArrayList<>();
                for (Faction faction : Faction.values()) {
                    candidateFactions.add(faction);
                }
                Faction islandOwner = findBestFactionForGroup(islandTiles, candidateFactions, capitalTiles, null, null);
                if (islandOwner != null) {
                    for (Tile tile : islandTiles) {
                        tile.faction = islandOwner;
                    }
                }
                continue;
            }

            redistributePendingTiles(componentId, pendingTiles, anchoredTiles, new ArrayList<>(anchoredFactions),
                    landComponentIds, capitalTiles, null, null);
        }
    }

    private void redistributePendingTiles(int componentId, List<Tile> pendingTiles, Set<Tile> anchoredTiles,
            List<Faction> allowedFactions, Map<Tile, Integer> landComponentIds, Map<Faction, Tile> capitalTiles,
            Map<Faction, Integer> factionDistanceLimits, Map<Faction, Map<Tile, Integer>> factionCapitalDistances) {
        List<Tile> remainingTiles = new ArrayList<>(pendingTiles);
        while (!remainingTiles.isEmpty()) {
            Map<Tile, Faction> updates = new HashMap<>();
            for (Tile tile : remainingTiles) {
                Faction bestFaction = findBestFrontierFaction(tile, componentId, anchoredTiles, allowedFactions,
                        landComponentIds, capitalTiles, factionDistanceLimits, factionCapitalDistances);
                if (bestFaction != null) {
                    updates.put(tile, bestFaction);
                }
            }

            if (updates.isEmpty()) {
                Faction fallbackFaction = findBestFactionForGroup(remainingTiles, allowedFactions, capitalTiles,
                        factionDistanceLimits, factionCapitalDistances);
                if (fallbackFaction == null) {
                    break;
                }
                for (Tile tile : remainingTiles) {
                    tile.faction = fallbackFaction;
                    anchoredTiles.add(tile);
                }
                break;
            }

            for (Map.Entry<Tile, Faction> entry : updates.entrySet()) {
                entry.getKey().faction = entry.getValue();
                anchoredTiles.add(entry.getKey());
            }
            remainingTiles.removeAll(updates.keySet());
        }
    }

    private int floodFillLandComponent(Tile startTile, int componentId, Map<Tile, Integer> componentIds) {
        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.add(startTile);
        componentIds.put(startTile, componentId);
        int count = 0;

        while (!queue.isEmpty()) {
            Tile tile = queue.removeFirst();
            count++;
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor == null || !isConnectedLand(neighbor) || componentIds.containsKey(neighbor)) {
                    continue;
                }
                componentIds.put(neighbor, componentId);
                queue.addLast(neighbor);
            }
        }

        return count;
    }

    private boolean isConnectedLand(Tile tile) {
        return tile != null && tile.terrain != TerrainType.WATER && !tile.terrain.isPolar();
    }

    private List<Integer> findLargestComponents(Map<Integer, Integer> componentSizes, int count) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int bestComponentId = -1;
            int bestSize = -1;
            for (Map.Entry<Integer, Integer> entry : componentSizes.entrySet()) {
                if (result.contains(entry.getKey())) {
                    continue;
                }
                if (entry.getValue() > bestSize) {
                    bestSize = entry.getValue();
                    bestComponentId = entry.getKey();
                }
            }
            if (bestComponentId >= 0) {
                result.add(bestComponentId);
            }
        }
        return result;
    }

    private Map<Faction, Tile> findCapitalTiles() {
        Map<Faction, Tile> capitals = new HashMap<>();
        for (Faction faction : Faction.values()) {
            Tile capitalTile = findCapitalTile(faction);
            if (capitalTile != null) {
                capitals.put(faction, capitalTile);
            }
        }
        return capitals;
    }

    private Tile findCapitalTile(Faction faction) {
        for (Tile tile : tiles) {
            if (tile.capital && tile.faction == faction) {
                return tile;
            }
        }
        return null;
    }

    private Set<Tile> collectFactionTiles(Tile startTile, int componentId, Faction faction, Map<Tile, Integer> landComponentIds,
            Map<Tile, Integer> capitalDistances, int distanceLimit) {
        Set<Tile> collectedTiles = new HashSet<>();
        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.add(startTile);
        collectedTiles.add(startTile);

        while (!queue.isEmpty()) {
            Tile tile = queue.removeFirst();
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor == null || neighbor.faction != faction || collectedTiles.contains(neighbor)) {
                    continue;
                }
                Integer neighborComponentId = landComponentIds.get(neighbor);
                if (neighborComponentId == null || neighborComponentId != componentId) {
                    continue;
                }
                if (!isWithinMainlandDistance(capitalDistances, neighbor, distanceLimit)) {
                    continue;
                }
                collectedTiles.add(neighbor);
                queue.addLast(neighbor);
            }
        }

        return collectedTiles;
    }

    private List<Tile> collectFactionCluster(Tile startTile, int componentId, Map<Tile, Integer> landComponentIds,
            Set<Tile> visitedTiles) {
        List<Tile> clusterTiles = new ArrayList<>();
        Faction faction = startTile.faction;
        if (faction == null) {
            return clusterTiles;
        }

        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.add(startTile);
        visitedTiles.add(startTile);

        while (!queue.isEmpty()) {
            Tile tile = queue.removeFirst();
            clusterTiles.add(tile);
            for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
                Tile neighbor = getNeighborWrapped(tile, directionIndex);
                if (neighbor == null || neighbor.faction != faction || visitedTiles.contains(neighbor)) {
                    continue;
                }
                Integer neighborComponentId = landComponentIds.get(neighbor);
                if (neighborComponentId == null || neighborComponentId != componentId) {
                    continue;
                }
                visitedTiles.add(neighbor);
                queue.addLast(neighbor);
            }
        }

        return clusterTiles;
    }

    private boolean shouldKeepIslandCluster(List<Tile> factionCluster, Map<Faction, Tile> capitalTiles,
            Map<Faction, Integer> capitalComponentIds, int componentId) {
        if (factionCluster.isEmpty()) {
            return false;
        }

        Faction faction = factionCluster.get(0).faction;
        Tile capitalTile = capitalTiles.get(faction);
        if (capitalTile == null) {
            return false;
        }

        if (faction == Faction.MARITIME_FEDERATION && isWesternMaritimeArchipelagoCluster(factionCluster)) {
            return true;
        }

        Integer capitalComponentId = capitalComponentIds.get(faction);
        if (capitalComponentId != null && capitalComponentId == componentId) {
            return true;
        }

        int minDistance = Integer.MAX_VALUE;
        for (Tile tile : factionCluster) {
            minDistance = Math.min(minDistance, wrappedHexDistance(capitalTile, tile));
        }
        return minDistance <= getIslandRetentionDistance(faction);
    }

    private boolean isWesternMaritimeArchipelagoCluster(List<Tile> factionCluster) {
        int archipelagoTiles = 0;
        for (Tile tile : factionCluster) {
            if (isWesternMaritimeArchipelago(normalizedWorldX(tile.center.x), normalizedRow(tile.r))) {
                archipelagoTiles++;
            }
        }
        return archipelagoTiles * 2 >= factionCluster.size();
    }

    private int getIslandRetentionDistance(Faction faction) {
        if (faction == Faction.MARITIME_FEDERATION) {
            return 14;
        }
        return 9;
    }

    private Faction findBestFrontierFaction(Tile tile, int componentId, Set<Tile> anchoredTiles, List<Faction> allowedFactions,
            Map<Tile, Integer> landComponentIds, Map<Faction, Tile> capitalTiles, Map<Faction, Integer> factionDistanceLimits,
            Map<Faction, Map<Tile, Integer>> factionCapitalDistances) {
        Map<Faction, Integer> neighborCounts = new HashMap<>();
        for (int directionIndex = 0; directionIndex < AXIAL_NEIGHBOR_DIRECTIONS.length; directionIndex++) {
            Tile neighbor = getNeighborWrapped(tile, directionIndex);
            if (neighbor == null || neighbor.faction == null || !anchoredTiles.contains(neighbor)) {
                continue;
            }
            Integer neighborComponentId = landComponentIds.get(neighbor);
            if (neighborComponentId != null && neighborComponentId == componentId && allowedFactions.contains(neighbor.faction)) {
                neighborCounts.put(neighbor.faction, neighborCounts.getOrDefault(neighbor.faction, 0) + 1);
            }
        }

        if (neighborCounts.isEmpty()) {
            return null;
        }

        Faction bestFaction = null;
        float bestScore = Float.MAX_VALUE;
        for (Faction faction : allowedFactions) {
            if (!neighborCounts.containsKey(faction)) {
                continue;
            }
            if (factionDistanceLimits != null) {
                Integer distanceLimit = factionDistanceLimits.get(faction);
                Map<Tile, Integer> capitalDistances = factionCapitalDistances == null ? null : factionCapitalDistances.get(faction);
                if (distanceLimit == null || capitalDistances == null
                        || !isWithinMainlandDistance(capitalDistances, tile, distanceLimit)) {
                    continue;
                }
            }
            float score = getFactionClaimScore(tile, faction, capitalTiles.get(faction), neighborCounts.get(faction));
            if (score < bestScore) {
                bestScore = score;
                bestFaction = faction;
            }
        }

        return bestFaction;
    }

    private Faction findBestFactionForGroup(List<Tile> groupTiles, List<Faction> candidateFactions, Map<Faction, Tile> capitalTiles,
            Map<Faction, Integer> factionDistanceLimits, Map<Faction, Map<Tile, Integer>> factionCapitalDistances) {
        Faction bestFaction = null;
        float bestScore = Float.MAX_VALUE;
        for (Faction faction : candidateFactions) {
            Tile capitalTile = capitalTiles.get(faction);
            if (factionDistanceLimits != null) {
                Integer distanceLimit = factionDistanceLimits.get(faction);
                Map<Tile, Integer> capitalDistances = factionCapitalDistances == null ? null : factionCapitalDistances.get(faction);
                if (capitalTile == null || distanceLimit == null || capitalDistances == null) {
                    continue;
                }
                boolean allWithinLimit = true;
                for (Tile tile : groupTiles) {
                    if (!isWithinMainlandDistance(capitalDistances, tile, distanceLimit)) {
                        allWithinLimit = false;
                        break;
                    }
                }
                if (!allWithinLimit) {
                    continue;
                }
            }
            float score = 0f;
            for (Tile tile : groupTiles) {
                score += getFactionClaimScore(tile, faction, capitalTile, 0);
            }
            score /= Math.max(1, groupTiles.size());
            if (capitalTile != null) {
                int minDistance = Integer.MAX_VALUE;
                for (Tile tile : groupTiles) {
                    minDistance = Math.min(minDistance, wrappedHexDistance(capitalTile, tile));
                }
                score += minDistance * 0.02f;
            }
            if (score < bestScore) {
                bestScore = score;
                bestFaction = faction;
            }
        }
        return bestFaction;
    }

    private float getFactionClaimScore(Tile tile, Faction faction, Tile capitalTile, int adjacentTileCount) {
        float nx = normalizedWorldX(tile.center.x);
        float ny = normalizedRow(tile.r);
        float dx = wrappedNormalizedDelta(nx, faction.getMapCenterX());
        float dy = ny - faction.getMapCenterY();
        float positionScore = dx * dx + dy * dy;
        float score = getFactionRegionScore(faction, nx, ny) * 2.4f + positionScore * 0.45f
                - adjacentTileCount * 0.50f
                + tileFactionRandomBias(tile, faction, 1013, 0.060f);
        if (capitalTile != null) {
            score += wrappedHexDistance(capitalTile, tile) * 0.02f;
        }
        return score;
    }

    private float factionRandomBias(int a, int b, float amplitude) {
        return (noise(a, b) - 0.5f) * amplitude;
    }

    private float tileRandomBias(Tile tile, int salt, float amplitude) {
        return factionRandomBias(tile.q * 53 + salt, tile.r * 97 + salt * 3, amplitude);
    }

    private float tileFactionRandomBias(Tile tile, Faction faction, int salt, float amplitude) {
        int factionSalt = faction.ordinal() * 211 + salt;
        return factionRandomBias(tile.q * 53 + factionSalt, tile.r * 97 + factionSalt * 3, amplitude);
    }

    private float getContinentalSampleX(float nx, float ny) {
        float macroWarp = (sampleContinentalNoise(nx, ny, 431, 2.4f, 2.0f) - 0.5f) * normalizedTileSpan(4.8f);
        float localWarp = (sampleContinentalNoise(nx, ny, 587, 6.2f, 5.4f) - 0.5f) * normalizedTileSpan(1.8f);
        return wrapNormalized01(nx + macroWarp + localWarp);
    }

    private float getContinentalSampleY(float nx, float ny) {
        float latitudeDamping = 0.55f + 0.45f * (1f - Math.abs(ny - 0.5f) * 2f);
        float macroWarp = (sampleContinentalNoise(nx, ny, 719, 2.1f, 2.7f) - 0.5f) * 0.070f * latitudeDamping;
        float localWarp = (sampleContinentalNoise(nx, ny, 863, 5.1f, 6.4f) - 0.5f) * 0.030f * latitudeDamping;
        return clamp01(ny + macroWarp + localWarp);
    }

    private float getContinentalThresholdBias(float nx, float ny) {
        float macroBias = (sampleContinentalNoise(nx, ny, 977, 2.0f, 2.3f) - 0.5f) * 0.75f;
        float localBias = (sampleContinentalNoise(nx, ny, 1031, 5.5f, 4.7f) - 0.5f) * 0.28f;
        return macroBias + localBias;
    }

    private float sampleContinentalNoise(float nx, float ny, int salt, float frequencyX, float frequencyY) {
        int sx = (int) Math.floor(nx * 1024f * frequencyX) + salt;
        int sy = (int) Math.floor(ny * 1024f * frequencyY) + salt * 3;
        float coarse = noise(sx, sy);
        float detail = noise(sx * 2 + 17, sy * 2 + 31);
        return coarse * 0.72f + detail * 0.28f;
    }

    private float wrapNormalized01(float value) {
        float wrapped = value % 1f;
        if (wrapped < 0f) {
            wrapped += 1f;
        }
        return wrapped;
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float wrappedNormalizedDelta(float a, float b) {
        float delta = Math.abs(a - b);
        return Math.min(delta, 1f - delta);
    }

    private int wrappedHexDistance(Tile a, Tile b) {
        int wrapColumns = Math.max(config.getMapColsEven(), config.getMapColsOdd());
        int directDistance = axialDistance(a.q, a.r, b.q, b.r);
        int negativeWrapDistance = axialDistance(a.q, a.r, b.q - wrapColumns, b.r);
        int positiveWrapDistance = axialDistance(a.q, a.r, b.q + wrapColumns, b.r);
        return Math.min(directDistance, Math.min(negativeWrapDistance, positiveWrapDistance));
    }

    private int axialDistance(int q1, int r1, int q2, int r2) {
        int dq = q1 - q2;
        int dr = r1 - r2;
        int ds = (-q1 - r1) - (-q2 - r2);
        return Math.max(Math.abs(dq), Math.max(Math.abs(dr), Math.abs(ds)));
    }

    private Tile findBestCapitalTile(Faction faction) {
        Tile best = null;
        float bestScore = Float.MAX_VALUE;
        for (Tile tile : tiles) {
            if (tile.faction != faction) {
                continue;
            }
            if (!isCapitalTerrain(tile.terrain)) {
                continue;
            }

            float nx = normalizedWorldX(tile.center.x);
            float ny = normalizedRow(tile.r);
            float dx = nx - faction.getMapCenterX();
            float dy = ny - faction.getMapCenterY();
            float score = dx * dx + dy * dy;
            if (tile.terrain == TerrainType.HILLS) {
                score += 0.01f;
            } else if (tile.terrain == TerrainType.FOREST) {
                score += 0.02f;
            } else if (tile.terrain == TerrainType.DESERT || tile.terrain == TerrainType.TUNDRA) {
                score += 0.03f;
            }

            if (score < bestScore) {
                bestScore = score;
                best = tile;
            }
        }

        if (best != null) {
            return best;
        }

        for (Tile tile : tiles) {
            if (tile.faction == faction && tile.terrain != TerrainType.WATER && !tile.terrain.isPolar()) {
                return tile;
            }
        }
        return null;
    }

    private boolean isCapitalTerrain(TerrainType terrain) {
        return terrain == TerrainType.PLAIN
                || terrain == TerrainType.FOREST
                || terrain == TerrainType.DESERT
                || terrain == TerrainType.HILLS
                || terrain == TerrainType.TUNDRA;
    }

    private boolean isLandTile(float nx, float ny, MapSample sample) {
        float shapeNx = getContinentalSampleX(nx, ny);
        float shapeNy = getContinentalSampleY(nx, ny);
        boolean maritimeIslands = isMaritimeIslands(nx, ny);
        boolean westernMaritimeArchipelago = isWesternMaritimeArchipelago(nx, ny);
        boolean centralSeaIslands = isCentralSeaIslands(nx, ny);
        boolean northernPolarShelf = isNorthernPolarShelf(nx, ny);
        boolean southernPolarShelf = isSouthernPolarShelf(nx, ny);
        float landThreshold = isPolarAdjacentLatitude(ny) ? 2.9f : 2.4f;
        float coreThreshold = isPolarAdjacentLatitude(ny) ? 1.45f : 1.15f;
        float shapeBias = getContinentalThresholdBias(nx, ny);
        float bestFactionRegionScore = getBestFactionRegionScore(shapeNx, shapeNy);
        landThreshold += shapeBias;
        coreThreshold += shapeBias * 0.42f;
        boolean land = bestFactionRegionScore <= landThreshold
                || maritimeIslands
                || westernMaritimeArchipelago
                || centralSeaIslands
                || isNorthernFederationArcticBridge(nx, ny)
                || northernPolarShelf
                || southernPolarShelf;
        if (!land) {
            return false;
        }
        if (!westernMaritimeArchipelago && isWesternMaritimeChannel(nx, ny)) {
            return false;
        }
        if (!centralSeaIslands && !westernMaritimeArchipelago && isCentralOcean(nx, ny)) {
            return false;
        }
        if (!maritimeIslands && isDawnOcean(nx, ny)) {
            return false;
        }
        boolean coreLand = bestFactionRegionScore <= coreThreshold
                || maritimeIslands
                || westernMaritimeArchipelago
                || centralSeaIslands
                || isNorthernFederationArcticBridge(nx, ny)
                || northernPolarShelf
                || southernPolarShelf;
        if (!coreLand && bestFactionRegionScore > 2.1f && isLikelyWaterSample(sample)) {
            return false;
        }
        return true;
    }

    private float getBestFactionRegionScore(float nx, float ny) {
        float bestScore = Float.MAX_VALUE;
        for (Faction faction : Faction.values()) {
            bestScore = Math.min(bestScore, getFactionRegionScore(faction, nx, ny));
        }
        return bestScore;
    }

    private float getFactionRegionScore(Faction faction, float nx, float ny) {
        switch (faction) {
            case NORTH_FEDERATION:
                return minScore(
                        regionScore(nx, ny, 0.12f, 0.76f, 0.12f, 0.08f),
                        regionScore(nx, ny, 0.21f, 0.75f, 0.11f, 0.10f),
                        regionScore(nx, ny, 0.17f, 0.64f, 0.11f, 0.12f),
                        regionScore(nx, ny, 0.08f, 0.85f, 0.08f, 0.06f));
            case HOLY_KINGDOM:
                return minScore(
                        regionScore(nx, ny, 0.10f, 0.69f, 0.08f, 0.12f),
                        regionScore(nx, ny, 0.14f, 0.60f, 0.08f, 0.10f),
                        regionScore(nx, ny, 0.19f, 0.54f, 0.08f, 0.09f),
                        regionScore(nx, ny, 0.09f, 0.49f, 0.05f, 0.07f));
            case EMPIRE:
                return minScore(
                        regionScore(nx, ny, 0.42f, 0.63f, 0.13f, 0.11f),
                        regionScore(nx, ny, 0.46f, 0.54f, 0.11f, 0.10f),
                        regionScore(nx, ny, 0.35f, 0.55f, 0.09f, 0.08f));
            case REPUBLIC:
                return minScore(
                        regionScore(nx, ny, 0.13f, 0.43f, 0.11f, 0.10f),
                        regionScore(nx, ny, 0.20f, 0.35f, 0.11f, 0.08f),
                        regionScore(nx, ny, 0.10f, 0.32f, 0.07f, 0.06f));
            case MARITIME_FEDERATION:
                return minScore(
                        regionScore(nx, ny, 0.30f, 0.28f, 0.10f, 0.08f),
                        regionScore(nx, ny, 0.22f, 0.24f, 0.06f, 0.05f),
                        regionScore(nx, ny, 0.37f, 0.24f, 0.06f, 0.05f),
                        regionScore(nx, ny, 0.49f, 0.43f, 0.03f, 0.03f),
                        regionScore(nx, ny, 0.55f, 0.40f, 0.03f, 0.03f));
            case DRAGON_EMPIRE:
                return minScore(
                        regionScore(nx, ny, 0.66f, 0.69f, 0.11f, 0.10f),
                        regionScore(nx, ny, 0.66f, 0.56f, 0.13f, 0.13f),
                        regionScore(nx, ny, 0.73f, 0.56f, 0.08f, 0.10f));
            case EASTERN_REPUBLIC:
                return minScore(
                        regionScore(nx, ny, 0.83f, 0.61f, 0.09f, 0.12f),
                        regionScore(nx, ny, 0.86f, 0.53f, 0.06f, 0.08f),
                        regionScore(nx, ny, 0.79f, 0.58f, 0.05f, 0.08f));
            case SANDSEA_ALLIANCE:
                return minScore(
                        regionScore(nx, ny, 0.68f, 0.40f, 0.16f, 0.13f),
                        regionScore(nx, ny, 0.74f, 0.29f, 0.11f, 0.09f),
                        regionScore(nx, ny, 0.61f, 0.31f, 0.09f, 0.08f));
            default:
                return Float.MAX_VALUE;
        }
    }

    private boolean isMaritimeIslands(float nx, float ny) {
        return ellipse(nx, ny, 0.30f, 0.30f, 0.09f, 0.09f)
                || ellipse(nx, ny, 0.23f, 0.24f, 0.06f, 0.05f)
                || ellipse(nx, ny, 0.36f, 0.24f, 0.05f, 0.05f)
                || ellipse(nx, ny, 0.11f, 0.26f, 0.04f, 0.04f);
    }

    private boolean isCentralSeaIslands(float nx, float ny) {
        return ellipse(nx, ny, 0.46f, 0.44f, 0.03f, 0.03f)
                || ellipse(nx, ny, 0.56f, 0.40f, 0.025f, 0.025f)
                || ellipse(nx, ny, 0.50f, 0.28f, 0.02f, 0.02f);
    }

    private boolean isWesternMaritimeArchipelago(float nx, float ny) {
        return ellipse(nx, ny, 0.528f, 0.448f, normalizedTileSpan(0.20f), 0.008f)
                || ellipse(nx, ny, 0.544f, 0.404f, normalizedTileSpan(0.22f), 0.008f)
                || ellipse(nx, ny, 0.522f, 0.366f, normalizedTileSpan(0.18f), 0.008f)
                || ellipse(nx, ny, 0.552f, 0.328f, normalizedTileSpan(0.20f), 0.008f)
                || ellipse(nx, ny, 0.534f, 0.292f, normalizedTileSpan(0.17f), 0.007f);
    }

    private boolean isWesternMaritimeChannel(float nx, float ny) {
        return ellipse(nx, ny, 0.498f, 0.446f, normalizedTileSpan(1.62f), 0.023f)
                || ellipse(nx, ny, 0.510f, 0.404f, normalizedTileSpan(1.52f), 0.023f)
                || ellipse(nx, ny, 0.494f, 0.366f, normalizedTileSpan(1.40f), 0.022f)
                || ellipse(nx, ny, 0.526f, 0.328f, normalizedTileSpan(1.30f), 0.021f)
                || ellipse(nx, ny, 0.508f, 0.292f, normalizedTileSpan(1.22f), 0.020f);
    }

    private boolean isCentralOceanBarrier(float nx, float ny) {
        float centerX = getCentralOceanCenterX(ny);
        boolean continuousSpine = wrappedNormalizedDelta(nx, centerX) <= normalizedTileSpan(1.35f);
        return continuousSpine
                || ellipse(nx, ny, 0.61f, 0.52f, normalizedTileSpan(1.25f), 0.33f)
                || ellipse(nx, ny, 0.63f, 0.35f, normalizedTileSpan(1.05f), 0.17f)
                || ellipse(nx, ny, 0.59f, 0.69f, normalizedTileSpan(1.00f), 0.17f);
    }

    private boolean isNorthernFederationArcticBridge(float nx, float ny) {
        return ellipse(nx, ny, 0.15f, 0.82f, 0.13f, 0.11f)
                || ellipse(nx, ny, 0.27f, 0.82f, 0.10f, 0.08f)
                || ellipse(nx, ny, 0.08f, 0.89f, 0.08f, 0.06f);
    }

    private boolean isSouthernCoastalShelf(float nx, float ny) {
        return ellipse(nx, ny, 0.20f, 0.18f, 0.17f, 0.09f)
                || ellipse(nx, ny, 0.73f, 0.16f, 0.18f, 0.10f)
                || ellipse(nx, ny, 0.88f, 0.20f, 0.09f, 0.06f);
    }

    private boolean isNorthernPolarShelf(float nx, float ny) {
        return ellipse(nx, ny, 0.18f, 0.84f, 0.18f, 0.08f)
                || ellipse(nx, ny, 0.34f, 0.81f, 0.12f, 0.07f)
                || ellipse(nx, ny, 0.70f, 0.84f, 0.16f, 0.08f)
                || ellipse(nx, ny, 0.86f, 0.80f, 0.11f, 0.07f);
    }

    private boolean isSouthernPolarShelf(float nx, float ny) {
        return ellipse(nx, ny, 0.14f, 0.16f, 0.15f, 0.08f)
                || ellipse(nx, ny, 0.31f, 0.19f, 0.12f, 0.07f)
                || ellipse(nx, ny, 0.66f, 0.17f, 0.17f, 0.08f)
                || ellipse(nx, ny, 0.86f, 0.21f, 0.11f, 0.06f);
    }

    private boolean isCentralOcean(float nx, float ny) {
        float centerX = getCentralOceanCenterX(ny);
        float halfWidth = normalizedTileSpan(2.2f) * (0.94f + 0.10f * (float) Math.sin(ny * 5.2f));
        float shoulderWidth = normalizedTileSpan(1.7f) * (0.96f + 0.08f * (float) Math.cos(ny * 6.1f));
        return ellipse(nx, ny, centerX, 0.52f, halfWidth, 0.30f)
                || ellipse(nx, ny, centerX + normalizedTileSpan(0.5f), 0.34f, shoulderWidth, 0.15f)
                || ellipse(nx, ny, centerX - normalizedTileSpan(0.5f), 0.69f, shoulderWidth, 0.16f);
    }

    private float getCentralOceanCenterX(float ny) {
        float centerShift = (float) Math.sin(ny * 7.5f) * normalizedTileSpan(0.7f)
                + (noise((int) (ny * 1000f), 77) - 0.5f) * normalizedTileSpan(0.8f);
        return 0.64f + centerShift;
    }

    private boolean isDawnOcean(float nx, float ny) {
        // Because the map wraps horizontally, the visible seam is both edges combined.
        float wave = 0.90f + 0.12f * (float) Math.sin(ny * 11.0f) + 0.08f * (noise((int) (ny * 2000f), 99) - 0.5f);
        float maxWidth = normalizedTileSpan(2f) * wave;
        return nx < maxWidth || nx > 1f - maxWidth;
    }

    private boolean isEasternMountainBelt(float nx, float ny) {
        return ellipse(nx, ny, 0.77f, 0.58f, 0.06f, 0.22f)
                || ellipse(nx, ny, 0.73f, 0.48f, 0.05f, 0.16f);
    }

    private boolean ellipse(float nx, float ny, float centerX, float centerY, float radiusX, float radiusY) {
        float dx = (nx - centerX) / radiusX;
        float dy = (ny - centerY) / radiusY;
        return dx * dx + dy * dy <= 1f;
    }

    private float normalizedTileSpan(float tileCount) {
        float maxColumns = Math.max(config.getMapColsEven(), config.getMapColsOdd());
        if (maxColumns <= 1f) {
            return 0f;
        }
        return tileCount / (maxColumns - 1f);
    }

    private boolean isLikelyWaterSample(MapSample sample) {
        float blueLead = sample.b - Math.max(sample.r, sample.g);
        boolean oceanHue = sample.hue >= 0.52f && sample.hue <= 0.64f;
        return blueLead > 0.08f
                || (oceanHue && sample.saturation > 0.22f && sample.brightness < 0.62f);
    }

    private float minScore(float... scores) {
        float best = Float.MAX_VALUE;
        for (float score : scores) {
            if (score < best) {
                best = score;
            }
        }
        return best;
    }

    private float regionScore(float nx, float ny, float centerX, float centerY, float radiusX, float radiusY) {
        float dx = (nx - centerX) / radiusX;
        float dy = (ny - centerY) / radiusY;
        return dx * dx + dy * dy;
    }

    private boolean isPolarAdjacentLatitude(float ny) {
        return ny < 0.24f || ny > 0.76f;
    }

    private TerrainType determinePolarTerrain(int row, float nx) {
        int northEdge = config.getMapRows() - 1;
        if (row == 0) {
            return TerrainType.ANTARCTIC;
        }
        if (row == northEdge) {
            return TerrainType.ARCTIC;
        }

        return null;
    }

    private MapSample sampleWorldMap(Pixmap worldMap, float nx, float ny) {
        if (worldMap == null) {
            return new MapSample(new Color(0.5f, 0.5f, 0.5f, 1f));
        }

        int centerX = Math.min(worldMap.getWidth() - 1, Math.max(0, Math.round(nx * (worldMap.getWidth() - 1))));
        int centerY = Math.min(worldMap.getHeight() - 1, Math.max(0, worldMap.getHeight() - 1 - Math.round(ny * (worldMap.getHeight() - 1))));
        int radius = 4;
        float totalR = 0f;
        float totalG = 0f;
        float totalB = 0f;
        int samples = 0;

        for (int y = centerY - radius; y <= centerY + radius; y++) {
            if (y < 0 || y >= worldMap.getHeight()) {
                continue;
            }
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (x < 0 || x >= worldMap.getWidth()) {
                    continue;
                }
                Color color = new Color();
                Color.rgba8888ToColor(color, worldMap.getPixel(x, y));
                totalR += color.r;
                totalG += color.g;
                totalB += color.b;
                samples++;
            }
        }

        if (samples == 0) {
            return new MapSample(new Color(0.5f, 0.5f, 0.5f, 1f));
        }

        return new MapSample(new Color(totalR / samples, totalG / samples, totalB / samples, 1f));
    }

    private boolean isRuggedSample(MapSample sample) {
        return sample.saturation < 0.22f && sample.brightness > 0.25f && sample.brightness < 0.78f;
    }

    private float normalizedColumn(int col, int maxCols) {
        if (maxCols <= 1) {
            return 0.5f;
        }
        return (float) col / (maxCols - 1);
    }

    private float normalizedRow(int row) {
        if (config.getMapRows() <= 1) {
            return 0.5f;
        }
        return (float) row / (config.getMapRows() - 1);
    }

    private float normalizedWorldX(float worldX) {
        float wrapped = wrapWorldX(worldX);
        if (horizontalWrapWidth <= 0f) {
            return 0.5f;
        }
        return wrapped / horizontalWrapWidth;
    }

    private float noise(int a, int b) {
        int n = a * 374761393 + b * 668265263 + generationSeed * 1442695041 + generationPassIndex * 1013904223;
        n = (n ^ (n >> 13)) * 1274126177;
        n ^= n >> 16;
        return (n & 0x7fffffff) / 2147483647f;
    }

    private int[] offsetToAxial(int col, int row) {
        int q = col - (row - (row & 1)) / 2;
        int r = row;
        return new int[] {q, r};
    }

    private Vector2 axialToWorld(int q, int r) {
        float x = getHexSize() * getSqrt3() * (q + r / 2f);
        float y = getHexSize() * 1.5f * r;
        return new Vector2(x, y);
    }

    private int[] axialRound(float q, float r) {
        float x = q;
        float z = r;
        float y = -x - z;

        int rx = Math.round(x);
        int ry = Math.round(y);
        int rz = Math.round(z);

        float xDiff = Math.abs(rx - x);
        float yDiff = Math.abs(ry - y);
        float zDiff = Math.abs(rz - z);

        if (xDiff > yDiff && xDiff > zDiff) {
            rx = -ry - rz;
        } else if (yDiff > zDiff) {
            ry = -rx - rz;
        } else {
            rz = -rx - ry;
        }

        return new int[] {rx, rz};
    }

    private long tileKey(int q, int r) {
        return (((long) q) << 32) ^ (r & 0xffffffffL);
    }

    private static class MapSample {
        private final float r;
        private final float g;
        private final float b;
        private final float hue;
        private final float brightness;
        private final float saturation;

        private MapSample(Color color) {
            this.r = color.r;
            this.g = color.g;
            this.b = color.b;
            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            this.brightness = max;
            this.saturation = max <= 0f ? 0f : (max - min) / max;
            if (max == min) {
                this.hue = 0f;
            } else if (max == r) {
                this.hue = ((g - b) / (max - min) / 6f + 1f) % 1f;
            } else if (max == g) {
                this.hue = ((b - r) / (max - min) + 2f) / 6f;
            } else {
                this.hue = ((r - g) / (max - min) + 4f) / 6f;
            }
        }
    }

    public static class Tile {
        public final int q;
        public final int r;
        public final Vector2 center;
        public TerrainType terrain;
        public Faction faction;
        public boolean capital;

        public Tile(int q, int r, Vector2 center, TerrainType terrain, Faction faction, boolean capital) {
            this.q = q;
            this.r = r;
            this.center = center;
            this.terrain = terrain;
            this.faction = faction;
            this.capital = capital;
        }
    }

    private enum MainlandZone {
        WEST,
        EAST
    }

    private static class MainlandAssignment {
        private final Faction faction;
        private final float share;
        private final float seedX;
        private final float seedY;

        private MainlandAssignment(Faction faction, float share, float seedX, float seedY) {
            this.faction = faction;
            this.share = share;
            this.seedX = seedX;
            this.seedY = seedY;
        }
    }

    private static class TileCandidate {
        private final Tile tile;
        private final float score;

        private TileCandidate(Tile tile, float score) {
            this.tile = tile;
            this.score = score;
        }
    }

    private static class WaterPathNode {
        private final Tile tile;
        private final int steps;

        private WaterPathNode(Tile tile, int steps) {
            this.tile = tile;
            this.steps = steps;
        }
    }

    private static class TileState {
        private final TerrainType terrain;
        private final Faction faction;
        private final boolean capital;

        private TileState(TerrainType terrain, Faction faction, boolean capital) {
            this.terrain = terrain;
            this.faction = faction;
            this.capital = capital;
        }
    }
}
