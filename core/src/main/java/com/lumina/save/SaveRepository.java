package com.lumina.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.lumina.hexmap.HexMapModel;
import com.lumina.hexmap.MapDefinition;
import com.lumina.hexmap.MapRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class SaveRepository {
    private static final String SAVE_DIRECTORY = "saves";
    private static final String SAVE_EXTENSION = ".lmsave";
    private static final String MAP_EXTENSION = ".lmcmap";
    private static final String SAVE_HEADER = "LUMINA_SAVE_V1";
    private static final int MAX_SLOTS = 8;
    private static final DateTimeFormatter DISPLAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private SaveRepository() {
    }

    public static int maxSlots() {
        return MAX_SLOTS;
    }

    public static List<SaveSlot> loadSlots() {
        List<SaveSlot> slots = new ArrayList<>();
        for (int slotIndex = 1; slotIndex <= MAX_SLOTS; slotIndex++) {
            slots.add(loadSlot(slotIndex));
        }
        return slots;
    }

    public static SaveSlot loadSlot(int slotIndex) {
        validateSlot(slotIndex);
        FileHandle directory = Gdx.files.local(SAVE_DIRECTORY);
        FileHandle saveFile = directory.child(slotFileName(slotIndex));
        if (!saveFile.exists()) {
            return new SaveSlot(slotIndex, defaultSlotName(slotIndex), 0L, null);
        }
        return readSlot(saveFile, slotIndex);
    }

    public static SaveSlot saveToSlot(int slotIndex, HexMapModel model) {
        validateSlot(slotIndex);
        FileHandle directory = Gdx.files.local(SAVE_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        LocalDateTime now = LocalDateTime.now();
        long epochMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String displayName = "セーブ " + DISPLAY_TIMESTAMP.format(now);

        MapDefinition snapshot = model.createTileSnapshotDefinition(
                "save-slot-" + slotIndex + "-map",
                "セーブスロット" + slotIndex,
                false);

        FileHandle mapFile = directory.child(slotMapFileName(slotIndex));
        MapRepository.writeDefinition(mapFile, snapshot);

        FileHandle saveFile = directory.child(slotFileName(slotIndex));
        StringBuilder builder = new StringBuilder();
        builder.append(SAVE_HEADER).append('\n');
        builder.append("slot=").append(slotIndex).append('\n');
        builder.append("name=").append(displayName).append('\n');
        builder.append("savedAt=").append(epochMillis).append('\n');
        builder.append("mapFile=").append(mapFile.name()).append('\n');
        saveFile.writeString(builder.toString(), false, StandardCharsets.UTF_8.name());

        return new SaveSlot(slotIndex, displayName, epochMillis, snapshot);
    }

    private static SaveSlot readSlot(FileHandle saveFile, int fallbackSlot) {
        List<String> lines = saveFile.readString(StandardCharsets.UTF_8.name()).lines().toList();
        if (lines.isEmpty() || !SAVE_HEADER.equals(lines.get(0).trim())) {
            throw new IllegalArgumentException("Unsupported save format: " + saveFile.path());
        }

        int slotIndex = fallbackSlot;
        String displayName = defaultSlotName(fallbackSlot);
        long savedAt = 0L;
        String mapFileName = null;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if ("slot".equals(key)) {
                slotIndex = Integer.parseInt(value);
            } else if ("name".equals(key)) {
                displayName = value;
            } else if ("savedAt".equals(key)) {
                savedAt = Long.parseLong(value);
            } else if ("mapFile".equals(key)) {
                mapFileName = value;
            }
        }

        if (mapFileName == null || mapFileName.isEmpty()) {
            throw new IllegalArgumentException("Save file is missing mapFile: " + saveFile.path());
        }

        FileHandle mapFile = saveFile.parent().child(mapFileName);
        if (!mapFile.exists()) {
            throw new IllegalArgumentException("Referenced save map does not exist: " + mapFile.path());
        }
        MapDefinition mapDefinition = MapRepository.readDefinition(mapFile, false);
        return new SaveSlot(slotIndex, displayName, savedAt, mapDefinition);
    }

    private static String slotFileName(int slotIndex) {
        return "slot-" + slotIndex + SAVE_EXTENSION;
    }

    private static String slotMapFileName(int slotIndex) {
        return "slot-" + slotIndex + MAP_EXTENSION;
    }

    private static String defaultSlotName(int slotIndex) {
        return "スロット" + slotIndex + " (空き)";
    }

    private static void validateSlot(int slotIndex) {
        if (slotIndex < 1 || slotIndex > MAX_SLOTS) {
            throw new IllegalArgumentException("slotIndex out of range: " + slotIndex);
        }
    }
}
