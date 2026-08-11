package com.lumina.editor;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.lumina.LuminaGame;
import com.lumina.hexmap.Faction;
import com.lumina.hexmap.HexMapModel;
import com.lumina.hexmap.MapDefinition;
import com.lumina.hexmap.MapRepository;
import com.lumina.hexmap.TerrainType;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class MapEditorController {
    public interface MapEditorHost {
        void updateEditorLayout(int width, int height);
        void rebuildCitySelectionButtons(float innerX, float innerWidth, float topY);
        void setEditorSectionExpanded(com.lumina.LuminaGame.EditorAccordionSection section);
        void openEditorSection(com.lumina.LuminaGame.EditorAccordionSection section);
        boolean isEditorSectionExpanded(com.lumina.LuminaGame.EditorAccordionSection section);
        void relayoutEditorScrollableContent();
        void scrollEditorPanel(float delta);
        boolean isEditorScrollableButtonVisible(Rectangle button);
        com.lumina.LuminaGame.EditorTool getEditorTool();
        TerrainType getEditorTerrainBrush();
        Faction getEditorFactionBrush();
        Faction getSelectedFaction();
        int getSelectedCityId();
        int getSelectedQ();
        int getSelectedR();
        boolean isEditorCityNameEditing();
        StringBuilder getEditorCityNameBuffer();
        boolean hasEditorUnsavedChanges();
        int getNextCityId();
        int getLastEditorPaintQ();
        int getLastEditorPaintR();
        float getEditorListViewportTop();
        float getEditorListViewportBottom();
        float getEditorScrollOffset();
        float getEditorScrollMax();
        Rectangle getEditorPanelRect();
        Rectangle[] getEditorToolButtons();
        Rectangle getEditorFactionBadgeRect();
        Rectangle getEditorCityNameRect();
        Rectangle getEditorCityNameButtonRect();
        Rectangle getCitySectionHeaderRect();
        Rectangle getTerrainSectionHeaderRect();
        Rectangle getFactionSectionHeaderRect();
        Rectangle[] getCitySelectButtons();
        int[] getCitySelectIds();
        String[] getCitySelectLabels();
        Rectangle[] getTerrainButtons();
        Rectangle[] getFactionButtons();
        Rectangle getEditorValidationRect();
        Rectangle getSaveMapButton();
        Rectangle getOverwriteMapButton();
        com.badlogic.gdx.graphics.OrthographicCamera getCamera();
        com.badlogic.gdx.graphics.g2d.SpriteBatch getSpriteBatch();
        com.badlogic.gdx.graphics.g2d.BitmapFont getMenuFont();
        com.badlogic.gdx.graphics.glutils.ShapeRenderer getShapeRenderer();
        HexMapModel getHexMapModel();
        MapDefinition getCurrentMapDefinition();
        void setCurrentMapDefinition(MapDefinition mapDefinition);
        List<HexMapModel.Tile> getTiles();
        void startEditorCityNameEditing(int cityId);
        void commitEditorCityName();
        void cancelEditorCityNameEditing();
        void setEditorTool(com.lumina.LuminaGame.EditorTool tool);
        void setEditorTerrainBrush(TerrainType terrain);
        void setEditorFactionBrush(Faction faction);
        void setSelectedFaction(Faction faction);
        void setSelectedCityId(int cityId);
        void setSelectedQ(int q);
        void setSelectedR(int r);
        void setNextCityId(int nextCityId);
        void setLastEditorPaintQ(int q);
        void setLastEditorPaintR(int r);
        void setEditorHasUnsavedChanges(boolean hasUnsavedChanges);
        void setMenuMessage(String message);
        com.badlogic.gdx.graphics.Color terrainButtonColor(TerrainType terrain);
        HexMapModel.Tile findCityCenterTile(int cityId);
        void clearFactionCapital(Faction faction, HexMapModel.Tile newCapitalTile);
    }

    private final MapEditorHost game;

    public MapEditorController(MapEditorHost game) {
        this.game = game;
    }

    public void updateEditorLayout(int width, int height) {
        game.updateEditorLayout(width, height);
    }

    public void rebuildCitySelectionButtons(float innerX, float innerWidth, float topY) {
        game.rebuildCitySelectionButtons(innerX, innerWidth, topY);
    }

    public void setEditorSectionExpanded(LuminaGame.EditorAccordionSection section) {
        game.setEditorSectionExpanded(section);
    }

    public void openEditorSection(LuminaGame.EditorAccordionSection section) {
        game.openEditorSection(section);
    }

    public boolean isEditorSectionExpanded(LuminaGame.EditorAccordionSection section) {
        return game.isEditorSectionExpanded(section);
    }

    public void relayoutEditorScrollableContent() {
        game.relayoutEditorScrollableContent();
    }

    public void scrollEditorPanel(float delta) {
        game.scrollEditorPanel(delta);
    }

    public boolean isEditorScrollableButtonVisible(Rectangle button) {
        return game.isEditorScrollableButtonVisible(button);
    }

    public boolean handleEditorUiClick(float screenX, float uiY) {
        Rectangle editorPanelRect = game.getEditorPanelRect();
        if (editorPanelRect == null || !editorPanelRect.contains(screenX, uiY)) {
            return false;
        }

        Rectangle[] editorToolButtons = game.getEditorToolButtons();
        for (int i = 0; i < editorToolButtons.length; i++) {
            if (editorToolButtons[i].contains(screenX, uiY)) {
                game.setEditorTool(LuminaGame.EditorTool.values()[i]);
                if (game.getEditorTool() == LuminaGame.EditorTool.TERRAIN) {
                    game.openEditorSection(LuminaGame.EditorAccordionSection.TERRAIN);
                } else if (game.getEditorTool() == LuminaGame.EditorTool.FACTION) {
                    game.openEditorSection(LuminaGame.EditorAccordionSection.FACTION);
                } else {
                    game.openEditorSection(LuminaGame.EditorAccordionSection.CITY);
                }
                game.setMenuMessage("");
                return true;
            }
        }

        Rectangle editorFactionBadgeRect = game.getEditorFactionBadgeRect();
        if (editorFactionBadgeRect != null && editorFactionBadgeRect.contains(screenX, uiY)) {
            if (game.getEditorFactionBrush() == null) {
                game.setMenuMessage("国家ブラシが未選択です。");
                return true;
            }
            game.setEditorTool(LuminaGame.EditorTool.FACTION);
            game.setSelectedFaction(game.getEditorFactionBrush());
            game.setSelectedCityId(-1);
            game.openEditorSection(LuminaGame.EditorAccordionSection.FACTION);
            refreshCitySelectionButtons();
            game.setMenuMessage("国家ブラシを再選択: " + game.getEditorFactionBrush().getLabel());
            return true;
        }

        Rectangle citySectionHeaderRect = game.getCitySectionHeaderRect();
        Rectangle terrainSectionHeaderRect = game.getTerrainSectionHeaderRect();
        Rectangle factionSectionHeaderRect = game.getFactionSectionHeaderRect();
        if (citySectionHeaderRect != null && citySectionHeaderRect.contains(screenX, uiY)) {
            game.setEditorSectionExpanded(LuminaGame.EditorAccordionSection.CITY);
            return true;
        }
        if (terrainSectionHeaderRect != null && terrainSectionHeaderRect.contains(screenX, uiY)) {
            game.setEditorSectionExpanded(LuminaGame.EditorAccordionSection.TERRAIN);
            return true;
        }
        if (factionSectionHeaderRect != null && factionSectionHeaderRect.contains(screenX, uiY)) {
            game.setEditorSectionExpanded(LuminaGame.EditorAccordionSection.FACTION);
            return true;
        }

        Rectangle editorCityNameButtonRect = game.getEditorCityNameButtonRect();
        if (editorCityNameButtonRect != null && editorCityNameButtonRect.contains(screenX, uiY)) {
            if (game.getSelectedCityId() < 0) {
                game.setMenuMessage("先に都市を選択してください。");
                return true;
            }
            if (game.isEditorCityNameEditing()) {
                game.commitEditorCityName();
            } else {
                game.startEditorCityNameEditing(game.getSelectedCityId());
            }
            return true;
        }

        Rectangle editorCityNameRect = game.getEditorCityNameRect();
        if (editorCityNameRect != null && editorCityNameRect.contains(screenX, uiY)) {
            if (game.getSelectedCityId() < 0) {
                game.setMenuMessage("先に都市を選択してください。");
                return true;
            }
            game.startEditorCityNameEditing(game.getSelectedCityId());
            return true;
        }

        Rectangle[] citySelectButtons = game.getCitySelectButtons();
        if (isEditorSectionExpanded(LuminaGame.EditorAccordionSection.CITY) && citySelectButtons != null) {
            int[] citySelectIds = game.getCitySelectIds();
            String[] citySelectLabels = game.getCitySelectLabels();
            for (int i = 0; i < citySelectButtons.length; i++) {
                if (citySelectButtons[i] != null && citySelectButtons[i].contains(screenX, uiY)) {
                    game.cancelEditorCityNameEditing();
                    game.setSelectedCityId(citySelectIds[i]);
                    for (HexMapModel.Tile tile : game.getTiles()) {
                        if (tile.cityCenter && tile.cityId == game.getSelectedCityId()) {
                            game.setEditorTool(LuminaGame.EditorTool.FACTION);
                            game.setSelectedFaction(tile.faction);
                            game.setEditorFactionBrush(tile.faction);
                            game.openEditorSection(LuminaGame.EditorAccordionSection.CITY);
                            game.setMenuMessage(citySelectLabels[i] + " を選択しました。");
                            refreshCitySelectionButtons();
                            return true;
                        }
                    }
                    game.setMenuMessage("都市を見つけられませんでした。");
                    return true;
                }
            }
        }

        Rectangle[] terrainButtons = game.getTerrainButtons();
        for (int i = 0; isEditorSectionExpanded(LuminaGame.EditorAccordionSection.TERRAIN) && i < terrainButtons.length; i++) {
            if (isEditorScrollableButtonVisible(terrainButtons[i]) && terrainButtons[i].contains(screenX, uiY)) {
                game.setEditorTerrainBrush(TerrainType.values()[i]);
                game.setEditorTool(LuminaGame.EditorTool.TERRAIN);
                game.openEditorSection(LuminaGame.EditorAccordionSection.TERRAIN);
                game.setMenuMessage("地形ブラシ: " + terrainLabel(game.getEditorTerrainBrush()));
                return true;
            }
        }

        Rectangle[] factionButtons = game.getFactionButtons();
        for (int i = 0; isEditorSectionExpanded(LuminaGame.EditorAccordionSection.FACTION) && i < factionButtons.length; i++) {
            if (isEditorScrollableButtonVisible(factionButtons[i]) && factionButtons[i].contains(screenX, uiY)) {
                game.setEditorFactionBrush(i == 0 ? null : Faction.values()[i - 1]);
                game.setEditorTool(LuminaGame.EditorTool.FACTION);
                game.setSelectedCityId(-1);
                game.setSelectedFaction(game.getEditorFactionBrush());
                game.openEditorSection(LuminaGame.EditorAccordionSection.FACTION);
                game.setMenuMessage(game.getEditorFactionBrush() == null ? "都市圏ブラシ: なし" : "都市圏ブラシ: " + game.getEditorFactionBrush().getLabel());
                refreshCitySelectionButtons();
                return true;
            }
        }

        Rectangle saveMapButton = game.getSaveMapButton();
        if (saveMapButton != null && saveMapButton.contains(screenX, uiY)) {
            String capitalWarning = validateCapitalsForSave();
            if (capitalWarning != null) {
                game.setMenuMessage(capitalWarning);
                return true;
            }
            MapDefinition savedMap = MapRepository.saveCustomMap(game.getHexMapModel());
            game.setCurrentMapDefinition(savedMap);
            game.setMenuMessage("カスタムマップを保存しました: " + savedMap.getDisplayName());
            game.setEditorHasUnsavedChanges(false);
            return true;
        }

        Rectangle overwriteMapButton = game.getOverwriteMapButton();
        if (overwriteMapButton != null && overwriteMapButton.contains(screenX, uiY)) {
            if (!canOverwriteCurrentMap()) {
                game.setMenuMessage("上書き対象のカスタムマップを開いてください。");
                return true;
            }
            String capitalWarning = validateCapitalsForSave();
            if (capitalWarning != null) {
                game.setMenuMessage(capitalWarning);
                return true;
            }
            MapDefinition currentMapDefinition = game.getCurrentMapDefinition();
            currentMapDefinition = MapRepository.overwriteCustomMap(game.getHexMapModel(), currentMapDefinition);
            game.setCurrentMapDefinition(currentMapDefinition);
            game.setEditorHasUnsavedChanges(false);
            game.setMenuMessage("カスタムマップを上書き保存しました: " + currentMapDefinition.getDisplayName());
            return true;
        }

        return true;
    }

    public void applyEditorToTile(HexMapModel.Tile tile, boolean fromDrag) {
        if (game.getEditorTool() == LuminaGame.EditorTool.TERRAIN) {
            if (game.getEditorTerrainBrush() == TerrainType.WATER && tile.cityCenter) {
                game.setMenuMessage("都市は極地以外の陸上タイルに置いてください。");
                return;
            }
            tile.terrain = game.getEditorTerrainBrush();
            if (tile.terrain.isPolar()) {
                tile.faction = null;
                tile.capital = false;
                tile.cityCenter = false;
                tile.cityId = -1;
            }
            game.setMenuMessage("地形を変更: " + terrainLabel(tile.terrain));
            game.setEditorHasUnsavedChanges(true);
            return;
        }
        if (game.getEditorTool() == LuminaGame.EditorTool.FACTION) {
            if (tile.terrain.isPolar()) {
                game.setMenuMessage("極地には都市圏を設定できません。");
                return;
            }
            if (tile.cityCenter) {
                if (tile.cityId < 0) {
                    tile.cityId = game.getNextCityId();
                    game.setNextCityId(game.getNextCityId() + 1);
                }
                game.setEditorFactionBrush(tile.faction);
                game.setSelectedFaction(tile.faction);
                game.setSelectedCityId(tile.cityId);
                refreshCitySelectionButtons();
                game.setMenuMessage(tile.capital ? "首都を選択: " + tile.faction.getLabel() : "都市を選択: " + tile.faction.getLabel());
                return;
            }
            if (game.getSelectedCityId() < 0 || game.getEditorFactionBrush() == null) {
                game.setMenuMessage("先に都市を選択してください。");
                return;
            }
            if (tile.faction != null && tile.faction != game.getEditorFactionBrush()) {
                game.setMenuMessage("他国の都市圏は直接設定できません。先に解除してください。");
                return;
            }
            if (tile.cityId >= 0 && tile.cityId != game.getSelectedCityId()) {
                game.setMenuMessage("別の都市圏と重複します。");
                return;
            }
            tile.faction = game.getEditorFactionBrush();
            tile.cityId = game.getSelectedCityId();
            if (tile.capital) {
                tile.capital = false;
            }
            if (tile.cityCenter) {
                tile.cityCenter = false;
            }
            game.setMenuMessage("都市圏を変更: " + tile.faction.getLabel());
            game.setEditorHasUnsavedChanges(true);
            return;
        }
        if (tile.terrain.isPolar()) {
            game.setMenuMessage("都市/首都は極地には設定できません。");
            return;
        }
        if (tile.faction == null) {
            Faction placementFaction = game.getEditorFactionBrush() != null ? game.getEditorFactionBrush() : game.getSelectedFaction();
            if (placementFaction == null) {
                game.setMenuMessage("先に国家を選択してください。");
                return;
            }
            tile.faction = placementFaction;
        }
        if (tile.cityId < 0) {
            tile.cityId = game.getNextCityId();
            game.setNextCityId(game.getNextCityId() + 1);
        }
        if (!tile.cityCenter) {
            tile.cityCenter = true;
            tile.capital = false;
            game.setSelectedCityId(tile.cityId);
            game.setSelectedFaction(tile.faction);
            refreshCitySelectionButtons();
            game.setMenuMessage("都市を設定: " + tile.faction.getLabel());
        } else if (!tile.capital) {
            game.clearFactionCapital(tile.faction, tile);
            tile.capital = true;
            tile.cityCenter = true;
            game.setSelectedCityId(tile.cityId);
            game.setSelectedFaction(tile.faction);
            refreshCitySelectionButtons();
            game.setMenuMessage("首都に昇格しました: " + tile.faction.getLabel());
        } else {
            tile.capital = false;
            tile.cityCenter = true;
            game.setSelectedCityId(tile.cityId);
            game.setSelectedFaction(tile.faction);
            refreshCitySelectionButtons();
            game.setMenuMessage("都市に降格しました: " + tile.faction.getLabel());
        }
        game.setSelectedFaction(tile.faction);
        game.setEditorHasUnsavedChanges(true);
    }

    public boolean applyEditorAtScreenCoordinate(int screenX, int screenY, boolean fromDrag, boolean erase) {
        Vector3 world = new Vector3(screenX, screenY, 0);
        game.getCamera().unproject(world);
        world.x = game.getHexMapModel().wrapWorldX(world.x);
        int[] rounded = game.getHexMapModel().worldToAxialRounded(world.x, world.y);
        HexMapModel.Tile tile = game.getHexMapModel().findTile(rounded[0], rounded[1]);
        if (tile == null) {
            if (!fromDrag) {
                game.setSelectedQ(Integer.MIN_VALUE);
                game.setSelectedR(Integer.MIN_VALUE);
                game.setSelectedFaction(null);
            }
            return false;
        }
        if (fromDrag && tile.q == game.getLastEditorPaintQ() && tile.r == game.getLastEditorPaintR()) {
            return true;
        }
        game.setSelectedQ(tile.q);
        game.setSelectedR(tile.r);
        if (erase) {
            clearEditorCityArea(tile);
        } else {
            applyEditorToTile(tile, fromDrag);
        }
        game.setLastEditorPaintQ(tile.q);
        game.setLastEditorPaintR(tile.r);
        return true;
    }

    public void clearEditorCityArea(HexMapModel.Tile tile) {
        if (tile == null || tile.terrain.isPolar()) {
            game.setMenuMessage("このタイルの都市圏は解除できません。");
            return;
        }
        if (tile.faction == null || tile.cityId < 0) {
            game.setMenuMessage("解除できる都市圏がありません。");
            return;
        }

        Faction ownerFaction = tile.faction;
        int removedCityId = tile.cityId;
        if (tile.cityCenter) {
            for (HexMapModel.Tile other : game.getTiles()) {
                if (other.cityId == removedCityId) {
                    other.faction = null;
                    other.capital = false;
                    other.cityCenter = false;
                    other.cityId = -1;
                }
            }
            if (game.getSelectedCityId() == removedCityId) {
                game.setSelectedCityId(-1);
            }
            game.setSelectedFaction(ownerFaction);
            game.setEditorFactionBrush(ownerFaction);
            refreshCitySelectionButtons();
            game.setEditorHasUnsavedChanges(true);
            game.setMenuMessage("都市と都市圏を解除: " + ownerFaction.getLabel());
            return;
        }

        tile.faction = null;
        tile.capital = false;
        tile.cityCenter = false;
        tile.cityId = -1;
        game.setSelectedFaction(ownerFaction);
        game.setEditorFactionBrush(ownerFaction);
        refreshCitySelectionButtons();
        game.setEditorHasUnsavedChanges(true);
        game.setMenuMessage("都市圏を解除: " + ownerFaction.getLabel());
    }

    private void refreshCitySelectionButtons() {
        Rectangle panelRect = game.getEditorPanelRect();
        Rectangle[] toolButtons = game.getEditorToolButtons();
        if (panelRect != null && toolButtons != null && toolButtons.length > 0) {
            game.rebuildCitySelectionButtons(panelRect.x + 12f, panelRect.width - 24f, toolButtons[0].y);
        }
    }

    public void renderEditorPanelShapes() {
        Rectangle editorPanelRect = game.getEditorPanelRect();
        if (editorPanelRect == null) {
            return;
        }

        boolean hasValidationWarnings = !collectMissingCapitalFactions().isEmpty()
                || !collectCenterlessCityAreas().isEmpty()
                || !collectIsolatedCities().isEmpty();
        com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer = game.getShapeRenderer();
        shapeRenderer.setColor(0.10f, 0.12f, 0.18f, 0.94f);
        shapeRenderer.rect(editorPanelRect.x, editorPanelRect.y, editorPanelRect.width, editorPanelRect.height);

        Rectangle editorFactionBadgeRect = game.getEditorFactionBadgeRect();
        if (editorFactionBadgeRect != null) {
            float pulse = 0.84f + 0.16f * (float) Math.sin(com.badlogic.gdx.utils.TimeUtils.millis() / 180.0);
            shapeRenderer.setColor(0.16f, 0.18f, 0.24f, 1f);
            shapeRenderer.rect(editorFactionBadgeRect.x, editorFactionBadgeRect.y, editorFactionBadgeRect.width, editorFactionBadgeRect.height);
            com.badlogic.gdx.graphics.Color brushColor = game.getEditorFactionBrush() == null
                    ? new com.badlogic.gdx.graphics.Color(0.35f, 0.35f, 0.38f, 1f)
                    : new com.badlogic.gdx.graphics.Color(game.getEditorFactionBrush().getColor());
            shapeRenderer.setColor(brushColor);
            shapeRenderer.rect(editorFactionBadgeRect.x + 6f, editorFactionBadgeRect.y + 4f, 18f, 16f);
            if (game.getEditorFactionBrush() != null) {
                com.badlogic.gdx.graphics.Color glow = new com.badlogic.gdx.graphics.Color(game.getEditorFactionBrush().getColor());
                glow.a = pulse;
                shapeRenderer.setColor(glow);
                shapeRenderer.rect(editorFactionBadgeRect.x, editorFactionBadgeRect.y + editorFactionBadgeRect.height - 3f,
                        editorFactionBadgeRect.width, 3f);
            }
        }

        Rectangle editorCityNameRect = game.getEditorCityNameRect();
        if (editorCityNameRect != null) {
            shapeRenderer.setColor(game.isEditorCityNameEditing() ? 0.28f : 0.16f,
                    0.18f, game.isEditorCityNameEditing() ? 0.34f : 0.24f, 1f);
            shapeRenderer.rect(editorCityNameRect.x, editorCityNameRect.y, editorCityNameRect.width, editorCityNameRect.height);
        }

        Rectangle editorCityNameButtonRect = game.getEditorCityNameButtonRect();
        if (editorCityNameButtonRect != null) {
            boolean canEditCityName = game.getSelectedCityId() >= 0;
            shapeRenderer.setColor(
                    game.isEditorCityNameEditing() ? 0.36f : (canEditCityName ? 0.24f : 0.18f),
                    game.isEditorCityNameEditing() ? 0.30f : (canEditCityName ? 0.28f : 0.20f),
                    game.isEditorCityNameEditing() ? 0.18f : (canEditCityName ? 0.36f : 0.22f),
                    1f);
            shapeRenderer.rect(editorCityNameButtonRect.x, editorCityNameButtonRect.y,
                    editorCityNameButtonRect.width, editorCityNameButtonRect.height);
        }

        renderEditorAccordionHeader(game.getCitySectionHeaderRect(), isEditorSectionExpanded(LuminaGame.EditorAccordionSection.CITY));
        renderEditorAccordionHeader(game.getTerrainSectionHeaderRect(), isEditorSectionExpanded(LuminaGame.EditorAccordionSection.TERRAIN));
        renderEditorAccordionHeader(game.getFactionSectionHeaderRect(), isEditorSectionExpanded(LuminaGame.EditorAccordionSection.FACTION));

        Rectangle[] editorToolButtons = game.getEditorToolButtons();
        for (int i = 0; i < editorToolButtons.length; i++) {
            Rectangle button = editorToolButtons[i];
            boolean active = game.getEditorTool() == LuminaGame.EditorTool.values()[i];
            shapeRenderer.setColor(active ? 0.42f : 0.20f, active ? 0.32f : 0.22f, active ? 0.18f : 0.28f, 1f);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }

        TerrainType[] terrains = TerrainType.values();
        Rectangle[] terrainButtons = game.getTerrainButtons();
        for (int i = 0; isEditorSectionExpanded(LuminaGame.EditorAccordionSection.TERRAIN) && i < terrainButtons.length; i++) {
            Rectangle button = terrainButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            com.badlogic.gdx.graphics.Color terrainColor = terrainButtonColor(terrains[i]);
            if (game.getEditorTerrainBrush() == terrains[i]) {
                terrainColor = terrainColor.cpy().lerp(com.badlogic.gdx.graphics.Color.WHITE, 0.35f);
            }
            shapeRenderer.setColor(terrainColor);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }

        Rectangle[] citySelectButtons = game.getCitySelectButtons();
        int[] citySelectIds = game.getCitySelectIds();
        if (isEditorSectionExpanded(LuminaGame.EditorAccordionSection.CITY) && citySelectButtons != null) {
            for (int i = 0; i < citySelectButtons.length; i++) {
                Rectangle button = citySelectButtons[i];
                if (!isEditorScrollableButtonVisible(button)) {
                    continue;
                }
                if (citySelectIds[i] == game.getSelectedCityId()) {
                    shapeRenderer.setColor(0.42f, 0.36f, 0.18f, 1f);
                } else {
                    shapeRenderer.setColor(0.22f, 0.24f, 0.30f, 0.92f);
                }
                shapeRenderer.rect(button.x, button.y, button.width, button.height);
            }
        }

        Rectangle[] factionButtons = game.getFactionButtons();
        for (int i = 0; isEditorSectionExpanded(LuminaGame.EditorAccordionSection.FACTION) && i < factionButtons.length; i++) {
            Rectangle button = factionButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            com.badlogic.gdx.graphics.Color color = i == 0
                    ? new com.badlogic.gdx.graphics.Color(0.20f, 0.20f, 0.22f, 1f)
                    : new com.badlogic.gdx.graphics.Color(Faction.values()[i - 1].getColor());
            if ((i == 0 && game.getEditorFactionBrush() == null) || (i > 0 && game.getEditorFactionBrush() == Faction.values()[i - 1])) {
                color = color.cpy().lerp(com.badlogic.gdx.graphics.Color.WHITE, 0.30f);
            }
            shapeRenderer.setColor(color);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }

        float editorScrollMax = game.getEditorScrollMax();
        if (editorScrollMax > 0f) {
            Rectangle rect = game.getEditorPanelRect();
            float trackX = rect.x + rect.width - 8f;
            float trackHeight = game.getEditorListViewportTop() - game.getEditorListViewportBottom();
            shapeRenderer.setColor(0.20f, 0.22f, 0.28f, 1f);
            shapeRenderer.rect(trackX, game.getEditorListViewportBottom(), 4f, trackHeight);
            float thumbHeight = Math.max(28f, trackHeight * (trackHeight / (trackHeight + editorScrollMax)));
            float travel = Math.max(0f, trackHeight - thumbHeight);
            float thumbY = game.getEditorListViewportTop() - thumbHeight - (travel * (game.getEditorScrollOffset() / editorScrollMax));
            shapeRenderer.setColor(0.55f, 0.60f, 0.70f, 1f);
            shapeRenderer.rect(trackX - 1f, thumbY, 6f, thumbHeight);
        }

        Rectangle editorValidationRect = game.getEditorValidationRect();
        if (editorValidationRect != null) {
            shapeRenderer.setColor(hasValidationWarnings ? 0.44f : (game.hasEditorUnsavedChanges() ? 0.24f : 0.16f),
                    hasValidationWarnings ? 0.20f : (game.hasEditorUnsavedChanges() ? 0.24f : 0.28f),
                    hasValidationWarnings ? 0.18f : (game.hasEditorUnsavedChanges() ? 0.14f : 0.22f),
                    1f);
            shapeRenderer.rect(editorValidationRect.x, editorValidationRect.y,
                    editorValidationRect.width, editorValidationRect.height);
        }

        Rectangle saveMapButton = game.getSaveMapButton();
        Rectangle overwriteMapButton = game.getOverwriteMapButton();
        shapeRenderer.setColor(hasValidationWarnings ? 0.42f : (game.hasEditorUnsavedChanges() ? 0.30f : 0.22f),
                hasValidationWarnings ? 0.26f : (game.hasEditorUnsavedChanges() ? 0.46f : 0.42f),
                hasValidationWarnings ? 0.20f : (game.hasEditorUnsavedChanges() ? 0.26f : 0.30f),
                1f);
        shapeRenderer.rect(saveMapButton.x, saveMapButton.y, saveMapButton.width, saveMapButton.height);
        shapeRenderer.setColor(canOverwriteCurrentMap()
                        ? (hasValidationWarnings ? 0.46f : (game.hasEditorUnsavedChanges() ? 0.40f : 0.36f))
                        : 0.26f,
                hasValidationWarnings ? 0.24f : (game.hasEditorUnsavedChanges() ? 0.30f : 0.34f),
                hasValidationWarnings ? 0.18f : (game.hasEditorUnsavedChanges() ? 0.22f : 0.28f),
                1f);
        shapeRenderer.rect(overwriteMapButton.x, overwriteMapButton.y, overwriteMapButton.width, overwriteMapButton.height);
    }

    public void renderEditorPanelText() {
        Rectangle editorPanelRect = game.getEditorPanelRect();
        if (editorPanelRect == null) {
            return;
        }
        boolean hasValidationWarnings = !collectMissingCapitalFactions().isEmpty()
                || !collectCenterlessCityAreas().isEmpty()
                || !collectIsolatedCities().isEmpty();
        float titleY = editorPanelRect.y + editorPanelRect.height - 4f;
        game.getMenuFont().draw(game.getSpriteBatch(), "編集モード", editorPanelRect.x + 14f, titleY);
        Rectangle editorFactionBadgeRect = game.getEditorFactionBadgeRect();
        if (editorFactionBadgeRect != null) {
            game.getMenuFont().draw(game.getSpriteBatch(), "国家ブラシ: " + currentEditorFactionLabel(),
                    editorFactionBadgeRect.x + 30f, editorFactionBadgeRect.y + 18f);
        }
        Rectangle editorCityNameRect = game.getEditorCityNameRect();
        if (editorCityNameRect != null) {
            HexMapModel.Tile selectedCity = findCityCenterTile(game.getSelectedCityId());
            String cityNameLabel = selectedCity == null ? "都市名: 未設定"
                    : "都市名: " + (game.isEditorCityNameEditing() ? game.getEditorCityNameBuffer().toString() + "_" : cityDisplayName(selectedCity));
            game.getMenuFont().draw(game.getSpriteBatch(), cityNameLabel, editorCityNameRect.x + 8f, editorCityNameRect.y + 18f);
        }
        Rectangle editorCityNameButtonRect = game.getEditorCityNameButtonRect();
        if (editorCityNameButtonRect != null) {
            String cityNameButtonLabel = game.isEditorCityNameEditing() ? "保存" : "編集";
            game.getMenuFont().draw(game.getSpriteBatch(), cityNameButtonLabel,
                    editorCityNameButtonRect.x + 12f, editorCityNameButtonRect.y + 18f);
        }

        String[] toolLabels = {"地形", "都市圏", "都市/首都"};
        Rectangle[] editorToolButtons = game.getEditorToolButtons();
        for (int i = 0; i < editorToolButtons.length; i++) {
            Rectangle button = editorToolButtons[i];
            game.getMenuFont().draw(game.getSpriteBatch(), toolLabels[i], button.x + 10f, button.y + 22f);
        }

        renderEditorAccordionHeaderText(game.getCitySectionHeaderRect(), "都市", game.getCitySelectButtons().length,
                isEditorSectionExpanded(LuminaGame.EditorAccordionSection.CITY));
        renderEditorAccordionHeaderText(game.getTerrainSectionHeaderRect(), "地形", game.getTerrainButtons().length,
                isEditorSectionExpanded(LuminaGame.EditorAccordionSection.TERRAIN));
        renderEditorAccordionHeaderText(game.getFactionSectionHeaderRect(), "都市圏", game.getFactionButtons().length,
                isEditorSectionExpanded(LuminaGame.EditorAccordionSection.FACTION));

        Rectangle[] citySelectButtons = game.getCitySelectButtons();
        int[] citySelectIds = game.getCitySelectIds();
        String[] citySelectLabels = game.getCitySelectLabels();
        if (isEditorSectionExpanded(LuminaGame.EditorAccordionSection.CITY) && citySelectButtons != null) {
            for (int i = 0; i < citySelectButtons.length; i++) {
                Rectangle button = citySelectButtons[i];
                if (!isEditorScrollableButtonVisible(button)) {
                    continue;
                }
                String label = citySelectLabels[i];
                if (citySelectIds[i] == game.getSelectedCityId()) {
                    label = "▶ " + label;
                }
                game.getMenuFont().draw(game.getSpriteBatch(), label, button.x + 8f, button.y + 21f);
            }
        }
        TerrainType[] terrains = TerrainType.values();
        Rectangle[] terrainButtons = game.getTerrainButtons();
        for (int i = 0; isEditorSectionExpanded(LuminaGame.EditorAccordionSection.TERRAIN) && i < terrainButtons.length; i++) {
            Rectangle button = terrainButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            game.getMenuFont().draw(game.getSpriteBatch(), terrainLabel(terrains[i]), button.x + 8f, button.y + 21f);
        }

        Rectangle[] factionButtons = game.getFactionButtons();
        for (int i = 0; isEditorSectionExpanded(LuminaGame.EditorAccordionSection.FACTION) && i < factionButtons.length; i++) {
            Rectangle button = factionButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            String label = i == 0 ? "都市圏なし" : Faction.values()[i - 1].getLabel();
            game.getMenuFont().draw(game.getSpriteBatch(), label, button.x + 8f, button.y + 21f);
        }

        Rectangle editorValidationRect = game.getEditorValidationRect();
        if (editorValidationRect != null) {
            game.getMenuFont().setColor(hasValidationWarnings
                    ? com.badlogic.gdx.graphics.Color.SALMON
                    : (game.hasEditorUnsavedChanges() ? com.badlogic.gdx.graphics.Color.GOLD : com.badlogic.gdx.graphics.Color.WHITE));
            game.getMenuFont().draw(game.getSpriteBatch(), buildEditorValidationSummary(), editorValidationRect.x + 8f, editorValidationRect.y + 26f);
            game.getMenuFont().setColor(com.badlogic.gdx.graphics.Color.WHITE);
        }

        Rectangle saveMapButton = game.getSaveMapButton();
        Rectangle overwriteMapButton = game.getOverwriteMapButton();
        game.getMenuFont().draw(game.getSpriteBatch(), "マップ保存", saveMapButton.x + 10f, saveMapButton.y + 31f);
        game.getMenuFont().draw(game.getSpriteBatch(), "上書き保存", overwriteMapButton.x + 10f, overwriteMapButton.y + 28f);
    }

    public String buildEditorStatusText() {
        String toolText;
        if (game.getEditorTool() == LuminaGame.EditorTool.TERRAIN) {
            toolText = "地形:" + terrainLabel(game.getEditorTerrainBrush());
        } else if (game.getEditorTool() == LuminaGame.EditorTool.FACTION) {
            toolText = "国家:" + currentEditorFactionLabel();
        } else {
            HexMapModel.Tile selectedCity = findCityCenterTile(game.getSelectedCityId());
            toolText = "都市:" + (selectedCity == null ? "未設定"
                    : (game.isEditorCityNameEditing()
                    ? game.getEditorCityNameBuffer().toString() + "_"
                    : cityDisplayName(selectedCity)));
        }
        return "編集モード / " + toolText;
    }

    public com.badlogic.gdx.graphics.g2d.SpriteBatch getSpriteBatch() {
        return game.getSpriteBatch();
    }

    public com.badlogic.gdx.graphics.g2d.BitmapFont getMenuFont() {
        return game.getMenuFont();
    }

    public com.badlogic.gdx.graphics.glutils.ShapeRenderer getShapeRenderer() {
        return game.getShapeRenderer();
    }

    public void startEditorCityNameEditing(int cityId) {
        game.startEditorCityNameEditing(cityId);
    }

    public void commitEditorCityName() {
        game.commitEditorCityName();
    }

    public void cancelEditorCityNameEditing() {
        game.cancelEditorCityNameEditing();
    }

    public String currentEditorFactionLabel() {
        Faction faction = game.getEditorFactionBrush();
        return faction == null ? "なし" : faction.getLabel();
    }

    public String cityDisplayName(HexMapModel.Tile tile) {
        if (tile == null) {
            return "未設定";
        }
        return tile.cityName == null || tile.cityName.isEmpty() ? "都市" + tile.cityId : tile.cityName;
    }

    public com.badlogic.gdx.graphics.Color terrainButtonColor(TerrainType terrain) {
        return game.terrainButtonColor(terrain);
    }

    public String terrainLabel(TerrainType terrain) {
        switch (terrain) {
            case PLAIN:
                return "平地";
            case ROAD:
                return "街道";
            case DESERT:
                return "砂漠";
            case TUNDRA:
                return "ツンドラ";
            case FOREST:
                return "森林";
            case HILLS:
                return "丘陵";
            case ANTARCTIC:
                return "南極";
            case ARCTIC:
                return "北極";
            case MOUNTAIN:
                return "山岳";
            case MOUNTAIN_RANGE:
                return "山脈";
            case WATER:
            default:
                return "海";
        }
    }

    public HexMapModel.Tile findCityCenterTile(int cityId) {
        return game.findCityCenterTile(cityId);
    }

    public void renderEditorAccordionHeader(Rectangle rect, boolean expanded) {
        if (rect == null) {
            return;
        }
        game.getShapeRenderer().setColor(expanded ? 0.24f : 0.16f, expanded ? 0.24f : 0.18f, expanded ? 0.34f : 0.26f, 1f);
        game.getShapeRenderer().rect(rect.x, rect.y, rect.width, rect.height);
    }

    public void renderEditorAccordionHeaderText(Rectangle rect, String label, int itemCount, boolean expanded) {
        if (rect == null || rect.y + rect.height < game.getEditorListViewportBottom() || rect.y > game.getEditorListViewportTop()) {
            return;
        }
        String prefix = expanded ? "[-] " : "[+] ";
        game.getMenuFont().draw(game.getSpriteBatch(), prefix + label + " (" + itemCount + ")", rect.x + 8f, rect.y + 18f);
    }

    public boolean canOverwriteCurrentMap() {
        MapDefinition currentMapDefinition = game.getCurrentMapDefinition();
        return currentMapDefinition != null
                && !currentMapDefinition.isOfficial()
                && currentMapDefinition.getId() != null
                && currentMapDefinition.getId().startsWith("custom-map-");
    }

    public String validateCapitalsForSave() {
        List<String> missingCapitalFactions = collectMissingCapitalFactions();
        if (missingCapitalFactions.isEmpty()) {
            return null;
        }
        return "首都が未設定の国家があります: " + String.join(" / ", missingCapitalFactions);
    }

    public String buildEditorValidationSummary() {
        List<String> missingCapitalFactions = collectMissingCapitalFactions();
        List<String> centerlessCityAreas = collectCenterlessCityAreas();
        List<String> isolatedCities = collectIsolatedCities();
        if (missingCapitalFactions.isEmpty()) {
            if (!centerlessCityAreas.isEmpty()) {
                return summarizeValidationList("都市中心なし", centerlessCityAreas);
            }
            if (!isolatedCities.isEmpty()) {
                return summarizeValidationList("孤立都市", isolatedCities);
            }
            return game.hasEditorUnsavedChanges() ? "保存チェック: 問題なし / 未保存あり" : "保存チェック: 問題なし";
        }
        return summarizeValidationList("首都未設定", missingCapitalFactions);
    }

    public String summarizeValidationList(String prefix, List<String> items) {
        if (items.size() <= 2) {
            return prefix + ": " + String.join(" / ", items);
        }
        return prefix + ": " + items.get(0) + " / " + items.get(1) + " / ほか" + (items.size() - 2) + "件";
    }

    public List<String> collectMissingCapitalFactions() {
        List<String> missingCapitalFactions = new ArrayList<>();
        for (Faction faction : Faction.values()) {
            boolean hasOwnedTile = false;
            boolean hasCapital = false;
            for (HexMapModel.Tile tile : game.getTiles()) {
                if (tile.faction != faction) {
                    continue;
                }
                hasOwnedTile = true;
                if (tile.capital) {
                    hasCapital = true;
                    break;
                }
            }
            if (hasOwnedTile && !hasCapital) {
                missingCapitalFactions.add(faction.getLabel());
            }
        }
        return missingCapitalFactions;
    }

    public List<String> collectCenterlessCityAreas() {
        List<String> centerlessCityAreas = new ArrayList<>();
        Set<Integer> seenCityIds = new HashSet<>();
        for (HexMapModel.Tile tile : game.getTiles()) {
            if (tile.cityId < 0 || tile.faction == null) {
                continue;
            }
            if (seenCityIds.contains(tile.cityId)) {
                continue;
            }
            seenCityIds.add(tile.cityId);
            boolean hasCenter = false;
            for (HexMapModel.Tile other : game.getTiles()) {
                if (other.cityId == tile.cityId && other.cityCenter) {
                    hasCenter = true;
                    break;
                }
            }
            if (!hasCenter) {
                centerlessCityAreas.add(tile.faction.getLabel() + " #" + tile.cityId);
            }
        }
        return centerlessCityAreas;
    }

    public List<String> collectIsolatedCities() {
        List<String> isolatedCities = new ArrayList<>();
        for (HexMapModel.Tile tile : game.getTiles()) {
            if (!tile.cityCenter || tile.cityId < 0 || tile.faction == null) {
                continue;
            }
            int cityAreaCount = 0;
            for (HexMapModel.Tile other : game.getTiles()) {
                if (other.cityId == tile.cityId) {
                    cityAreaCount++;
                }
            }
            if (cityAreaCount <= 1) {
                isolatedCities.add((tile.capital ? "首都" : "都市") + " #" + tile.cityId);
            }
        }
        return isolatedCities;
    }
}
