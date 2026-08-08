package com.lumina.hexmap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MapRepository {
    public static final String OFFICIAL_MAP_ASSET_PATH = "maps/official-map.lmcmap";
    private static final String CUSTOM_MAP_DIRECTORY = "maps/custom";
    private static final String CUSTOM_MAP_EXTENSION = ".lmcmap";
    private static final String FILE_HEADER = "LUMINA_MAP_V1";
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private MapRepository() {
    }

    public static MapDefinition loadOfficialMap() {
        FileHandle file = Gdx.files.internal(OFFICIAL_MAP_ASSET_PATH);
        return file.exists() ? readDefinition(file, true) : null;
    }

    public static List<MapDefinition> loadCustomMaps() {
        List<MapDefinition> maps = new ArrayList<>();
        FileHandle directory = Gdx.files.local(CUSTOM_MAP_DIRECTORY);
        if (!directory.exists()) {
            return maps;
        }
        for (FileHandle file : directory.list()) {
            if (!file.extension().equalsIgnoreCase("lmcmap")) {
                continue;
            }
            maps.add(readDefinition(file, false));
        }
        maps.sort(new Comparator<MapDefinition>() {
            @Override
            public int compare(MapDefinition left, MapDefinition right) {
                return right.getDisplayName().compareTo(left.getDisplayName());
            }
        });
        return maps;
    }

    public static MapDefinition saveCustomMap(HexMapModel model) {
        LocalDateTime now = LocalDateTime.now();
        String stamp = FILE_TIMESTAMP.format(now);
        String mapId = "custom-map-" + stamp;
        String displayName = "カスタムマップ " + DISPLAY_TIMESTAMP.format(now);
        MapDefinition definition = model.createTileSnapshotDefinition(mapId, displayName, false);
        FileHandle directory = Gdx.files.local(CUSTOM_MAP_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        writeDefinition(directory.child(mapId + CUSTOM_MAP_EXTENSION), definition);
        return definition;
    }

    public static void writeDefinition(FileHandle file, MapDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append(FILE_HEADER).append('\n');
        builder.append("id=").append(definition.getId()).append('\n');
        builder.append("name=").append(definition.getDisplayName()).append('\n');
        builder.append("official=").append(definition.isOfficial()).append('\n');
        builder.append("sourceType=").append(definition.getSourceType().name()).append('\n');
        builder.append("seed=").append(definition.getGenerationSeed()).append('\n');
        builder.append("rows=").append(definition.getRows()).append('\n');
        builder.append("mapColsEven=").append(definition.getMapColsEven()).append('\n');
        builder.append("mapColsOdd=").append(definition.getMapColsOdd()).append('\n');
        for (MapDefinition.TileData tile : definition.getTiles()) {
            builder.append("tile=")
                    .append(tile.getQ()).append(',')
                    .append(tile.getR()).append(',')
                    .append(tile.getTerrain().name()).append(',')
                    .append(tile.getFaction() == null ? "" : tile.getFaction().name()).append(',')
                    .append(tile.isCapital())
                    .append('\n');
        }
        file.writeString(builder.toString(), false, StandardCharsets.UTF_8.name());
    }

    public static MapDefinition readDefinition(FileHandle file, boolean defaultOfficial) {
        List<String> lines = file.readString(StandardCharsets.UTF_8.name()).lines().toList();
        if (lines.isEmpty() || !FILE_HEADER.equals(lines.get(0).trim())) {
            throw new IllegalArgumentException("Unsupported map format: " + file.path());
        }

        String id = "unknown-map";
        String name = file.nameWithoutExtension();
        boolean official = defaultOfficial;
        MapDefinition.SourceType sourceType = MapDefinition.SourceType.TILE_SNAPSHOT;
        int seed = 0;
        int rows = 0;
        int mapColsEven = 0;
        int mapColsOdd = 0;
        List<MapDefinition.TileData> tiles = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("tile=")) {
                tiles.add(parseTile(line.substring(5), file.path()));
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if ("id".equals(key)) {
                id = value;
            } else if ("name".equals(key)) {
                name = value;
            } else if ("official".equals(key)) {
                official = Boolean.parseBoolean(value);
            } else if ("sourceType".equals(key)) {
                sourceType = MapDefinition.SourceType.valueOf(value);
            } else if ("seed".equals(key)) {
                seed = Integer.parseInt(value);
            } else if ("rows".equals(key)) {
                rows = Integer.parseInt(value);
            } else if ("mapColsEven".equals(key)) {
                mapColsEven = Integer.parseInt(value);
            } else if ("mapColsOdd".equals(key)) {
                mapColsOdd = Integer.parseInt(value);
            }
        }

        return new MapDefinition(id, name, official, sourceType, seed, rows, mapColsEven, mapColsOdd, tiles);
    }

    private static MapDefinition.TileData parseTile(String serialized, String path) {
        String[] values = serialized.split(",", -1);
        if (values.length != 5) {
            throw new IllegalArgumentException("Invalid tile entry in " + path + ": " + serialized);
        }
        int q = Integer.parseInt(values[0]);
        int r = Integer.parseInt(values[1]);
        TerrainType terrain = TerrainType.valueOf(values[2]);
        Faction faction = values[3].isEmpty() ? null : Faction.valueOf(values[3]);
        boolean capital = Boolean.parseBoolean(values[4]);
        return new MapDefinition.TileData(q, r, terrain, faction, capital);
    }
}
