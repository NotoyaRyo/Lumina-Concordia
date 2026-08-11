package com.lumina;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.lumina.hexmap.Faction;
import com.lumina.hexmap.HexMapConfig;
import com.lumina.hexmap.HexMapModel;
import com.lumina.hexmap.HexMapRenderer;
import com.lumina.hexmap.MapDefinition;
import com.lumina.hexmap.MapRepository;
import com.lumina.hexmap.TerrainType;
import com.lumina.save.SaveRepository;
import com.lumina.save.SaveSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class LuminaGame extends ApplicationAdapter {
    private enum ScreenMode { MENU, GAME }
    private enum MenuState { MAIN, SETTINGS, MAP_SELECT, LOAD_SELECT, AUTO_GENERATE, OPTIONS }
    private enum GameMode { PLAY, MAP_EDITOR }
    private enum EditorTool { TERRAIN, FACTION, CAPITAL }
    private enum EditorAccordionSection { CITY, TERRAIN, FACTION }
    private enum SaveSelectionAction { LOAD_GAME, SAVE_GAME }
    private enum TurnPhase { PLAYER_FACTION_SELECT, PLAYER_TURN, AI_TURN }
    private enum PendingEditorExitAction { NONE, RETURN_TO_MENU }

    private static final float TOP_MENU_HEIGHT = 52f;
    private static final float HAMBURGER_SIZE = 36f;
    private static final float PAUSE_MENU_WIDTH = 360f;
    private static final float PAUSE_MENU_HEIGHT = 300f;
    private static final float PAUSE_BUTTON_HEIGHT = 48f;
    private static final float PAUSE_BUTTON_MARGIN = 16f;
    private static final float MAX_CAMERA_ZOOM = 4f;
    private static final float EDITOR_PANEL_WIDTH = 300f;
    private static final float EDITOR_PANEL_MARGIN = 12f;
    private static final float EDITOR_BUTTON_HEIGHT = 30f;
    private static final float EDITOR_SAVE_BUTTON_HEIGHT = 46f;
    private static final float EDITOR_SCROLL_STEP = 36f;
    private static final int EDITOR_CITY_NAME_MAX_LENGTH = 24;
    private static final float EDITOR_SECTION_HEADER_HEIGHT = 24f;
    private static final float EDITOR_SECTION_HEADER_GAP = 6f;
    private static final float EDITOR_TOP_BLOCK_GAP = 6f;
    private static final float UNIT_MAX_MOVEMENT = 3f;
    private static final int UNIT_MAX_HP = 10;
    private static final int UNIT_ATTACK_DAMAGE = 5;
    private static final int PLAYER_VISION_RANGE = 6;
    private static final String SETTINGS_PREFS = "lumina-settings";
    private static final int[][] WINDOW_PRESETS = {
            {1280, 720},
            {1600, 900},
            {1920, 1080}
    };
    private static final String[] PAUSE_MENU_LABELS = {"再開", "セーブ", "設定", "メニューに戻る"};
    // Bootstrap glyphs only. The actual font is generated incrementally from a Windows/system font at runtime.
    private static final String JAPANESE_FONT_CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS + "　・ー〜。、！？「」『』";
    private static final String[] JAPANESE_FONT_ASSET_PATHS = {"azuki.ttf", "NotoSansJP-Regular.otf"};
    private static final String[] JAPANESE_SYSTEM_FONT_PATHS = {
            "C:/Windows/Fonts/MS Gothic.ttf",
            "C:/Windows/Fonts/MS Mincho.ttf",
            "C:/Windows/Fonts/msgothic.ttc",
            "C:/Windows/Fonts/meiryo.ttc",
            "C:/Windows/Fonts/YuGothR.ttc",
            "C:/Windows/Fonts/yumin.ttf",
            "C:/Windows/Fonts/YuGothM.ttc"
    };
    // Minimal UI bootstrap glyphs. Other characters are generated on demand from the selected system font.
    private static final String UI_JAPANESE_CHARS = "編集モード地形都市圏都市首都平地街道砂漠森林丘陵南極北極山岳山脈海マップ保存名称入力中未設定戻開始";

    private OrthographicCamera camera;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private BitmapFont titleFont;
    private BitmapFont menuFont;
    private Matrix4 uiProjection;
    private final List<FreeTypeFontGenerator> activeFontGenerators = new ArrayList<>();

    private Rectangle[] menuButtons = new Rectangle[0];
    private String[] menuLabels = new String[0];
    private final List<MapDefinition> selectableMaps = new ArrayList<>();
    private final List<SaveSlot> selectableSaves = new ArrayList<>();

    private Rectangle topMenuBar;
    private Rectangle hamburgerButton;
    private Rectangle endTurnButton;
    private Rectangle pauseMenuRect;
    private Rectangle[] pauseMenuButtons;
    private boolean pauseMenuVisible = false;

    private Rectangle editorPanelRect;
    private Rectangle[] editorToolButtons;
    private Rectangle editorFactionBadgeRect;
    private Rectangle editorCityNameRect;
    private Rectangle editorCityNameButtonRect;
    private Rectangle citySectionHeaderRect;
    private Rectangle terrainSectionHeaderRect;
    private Rectangle factionSectionHeaderRect;
    private Rectangle[] citySelectButtons;
    private Rectangle[] terrainButtons;
    private Rectangle[] factionButtons;
    private Rectangle editorValidationRect;
    private Rectangle saveMapButton;
    private Rectangle overwriteMapButton;
    private float editorScrollOffset = 0f;
    private float editorScrollMax = 0f;
    private float editorListViewportTop = 0f;
    private float editorListViewportBottom = 0f;
    private int[] citySelectIds = new int[0];
    private String[] citySelectLabels = new String[0];
    private static final float EDITOR_SECTION_SPACING = 18f;
    private static final float EDITOR_VALIDATION_HEIGHT = 42f;
    private boolean editorHasUnsavedChanges = false;
    private boolean editorCityNameEditing = false;
    private int editingCityNameId = -1;
    private StringBuilder editorCityNameBuffer = new StringBuilder();
    private EditorAccordionSection expandedEditorSection = EditorAccordionSection.CITY;

    private String menuMessage = "";
    private ScreenMode screenMode = ScreenMode.MENU;
    private MenuState menuState = MenuState.MAIN;
    private MenuState mapSelectionReturnState = MenuState.MAIN;
    private MenuState saveSelectionReturnState = MenuState.MAIN;
    private MenuState optionsReturnState = MenuState.MAIN;
    private boolean optionsReturnToGame = false;
    private PendingEditorExitAction pendingEditorExitAction = PendingEditorExitAction.NONE;
    private GameMode gameMode = GameMode.PLAY;
    private GameMode pendingMapSelectionMode = GameMode.PLAY;
    private SaveSelectionAction saveSelectionAction = SaveSelectionAction.LOAD_GAME;
    private EditorTool editorTool = EditorTool.TERRAIN;
    private TerrainType editorTerrainBrush = TerrainType.PLAIN;
    private Faction editorFactionBrush = Faction.NORTH_FEDERATION;

    private Texture backgroundTexture;
    private HexMapModel hexMapModel;
    private HexMapRenderer hexMapRenderer;
    private HexMapConfig hexMapConfig;
    private MapDefinition currentMapDefinition;
    private MapDefinition generatedPreviewMapDefinition;
    private Preferences preferences;

    private final int mapRows = 38;
    private final int mapColsEven = 58;
    private final int mapColsOdd = 58;
    private int selectedQ = Integer.MIN_VALUE;
    private int selectedR = Integer.MIN_VALUE;
    private Faction selectedFaction;
    private int selectedCityId = -1;
    private final List<Faction> playableFactions = new ArrayList<>();
    private final List<Unit> units = new ArrayList<>();
    private Faction playerFaction;
    private Faction currentTurnFaction;
    private TurnPhase turnPhase = TurnPhase.PLAYER_FACTION_SELECT;
    private int turnNumber = 1;
    private Unit selectedUnit;
    private Rectangle[] factionSelectButtons = new Rectangle[0];
    private int windowPresetIndex = 0;
    private float masterVolume = 1f;
    private int activeSaveSlot = -1;
    private int lastEditorPaintQ = Integer.MIN_VALUE;
    private int lastEditorPaintR = Integer.MIN_VALUE;
    private int nextCityId = 1;
    private int prevMiddleX = -1;
    private int prevMiddleY = -1;

    private static class Unit {
        private final Faction faction;
        private int q;
        private int r;
        private int hp;
        private float movementRemaining;

        private Unit(Faction faction, int q, int r) {
            this.faction = faction;
            this.q = q;
            this.r = r;
            this.hp = UNIT_MAX_HP;
            this.movementRemaining = UNIT_MAX_MOVEMENT;
        }
    }

    @Override
    public void create() {
        shapeRenderer = new ShapeRenderer();
        spriteBatch = new SpriteBatch();
        titleFont = createFont(52);
        menuFont = createFont(24);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiProjection = new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        preferences = Gdx.app.getPreferences(SETTINGS_PREFS);
        loadSystemSettings();
        applyWindowPreset(false);

        hexMapConfig = new HexMapConfig(50f, mapRows, mapColsEven, mapColsOdd, 12f);
        hexMapModel = new HexMapModel(hexMapConfig);
        loadBackgroundTexture();
        loadInitialMap();
        hexMapRenderer = new HexMapRenderer(hexMapModel, hexMapConfig, backgroundTexture);

        updateMenuLayout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        setMenuState(MenuState.MAIN);
        centerCameraOnMap();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (screenMode != ScreenMode.GAME || gameMode != GameMode.MAP_EDITOR || !editorCityNameEditing) {
                    return false;
                }
                if (keycode == Input.Keys.ENTER) {
                    commitEditorCityName();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    cancelEditorCityNameEditing();
                    return true;
                }
                if (keycode == Input.Keys.BACKSPACE && editorCityNameBuffer.length() > 0) {
                    editorCityNameBuffer.deleteCharAt(editorCityNameBuffer.length() - 1);
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(char character) {
                if (screenMode != ScreenMode.GAME || gameMode != GameMode.MAP_EDITOR || !editorCityNameEditing) {
                    return false;
                }
                if (character == '\r' || character == '\n' || character == '\b' || character == 27) {
                    return true;
                }
                if (Character.isISOControl(character) || editorCityNameBuffer.length() >= EDITOR_CITY_NAME_MAX_LENGTH) {
                    return false;
                }
                editorCityNameBuffer.append(character);
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (screenMode != ScreenMode.GAME) {
                    return false;
                }
                float uiY = Gdx.graphics.getHeight() - Gdx.input.getY();
                if (gameMode == GameMode.MAP_EDITOR && editorPanelRect != null && editorPanelRect.contains(Gdx.input.getX(), uiY)) {
                    scrollEditorPanel(amountY * EDITOR_SCROLL_STEP);
                    return true;
                }
                Vector3 screenCoords = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
                camera.update();
                Vector3 beforeZoom = camera.unproject(new Vector3(screenCoords));

                camera.zoom += amountY * 0.25f;
                if (camera.zoom < 0.2f) {
                    camera.zoom = 0.2f;
                }
                if (camera.zoom > MAX_CAMERA_ZOOM) {
                    camera.zoom = MAX_CAMERA_ZOOM;
                }
                camera.update();

                Vector3 afterZoom = camera.unproject(new Vector3(screenCoords));
                camera.position.add(beforeZoom.x - afterZoom.x, beforeZoom.y - afterZoom.y, 0);
                clampCameraToMap();
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT && button != Input.Buttons.RIGHT) {
                    return false;
                }

                float uiY = Gdx.graphics.getHeight() - screenY;
                if (screenMode == ScreenMode.MENU) {
                    if (button != Input.Buttons.LEFT) {
                        return false;
                    }
                    return handleMenuClick(screenX, uiY);
                }
                return handleGameClick(screenX, screenY, uiY, button);
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (screenMode != ScreenMode.GAME || gameMode != GameMode.MAP_EDITOR || pauseMenuVisible) {
                    return false;
                }
                boolean erase = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
                boolean paint = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
                if (!erase && !paint) {
                    return false;
                }
                float uiY = Gdx.graphics.getHeight() - screenY;
                if (editorPanelRect != null && editorPanelRect.contains(screenX, uiY)) {
                    return false;
                }
                return applyEditorAtScreenCoordinate(screenX, screenY, true, erase);
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT || button == Input.Buttons.RIGHT) {
                    lastEditorPaintQ = Integer.MIN_VALUE;
                    lastEditorPaintR = Integer.MIN_VALUE;
                }
                return false;
            }
        });
    }

    private void loadInitialMap() {
        MapDefinition officialMap = MapRepository.loadOfficialMap();
        if (officialMap != null) {
            currentMapDefinition = officialMap;
            hexMapModel.loadMapDefinition(officialMap);
        } else {
            currentMapDefinition = MapDefinition.generatedSeed("generated-default", "自動生成マップ", false,
                    ThreadLocalRandom.current().nextInt(), mapRows, mapColsEven, mapColsOdd);
            hexMapModel.loadMapDefinition(currentMapDefinition);
        }
        syncCityIdCounter();
        editorHasUnsavedChanges = false;
    }

    private void loadBackgroundTexture() {
        String assetFile = "background.png";
        if (!Gdx.files.internal(assetFile).exists()) {
            assetFile = "libgdx.png";
        }
        backgroundTexture = new Texture(Gdx.files.internal(assetFile));
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        uiProjection.setToOrtho2D(0, 0, width, height);
        updateMenuLayout(width, height);
        centerCameraOnMap();
    }

    private void updateMenuLayout(int width, int height) {
        float buttonWidth = Math.min(420f, width * 0.72f);
        float buttonHeight = 58f;
        float totalHeight = menuLabels.length == 0 ? 0f : menuLabels.length * buttonHeight + (menuLabels.length - 1) * 16f;
        float startX = (width - buttonWidth) / 2f;
        float startY = height * 0.52f + totalHeight / 2f - buttonHeight;
        menuButtons = new Rectangle[menuLabels.length];
        for (int i = 0; i < menuLabels.length; i++) {
            menuButtons[i] = new Rectangle(startX, startY - i * (buttonHeight + 16f), buttonWidth, buttonHeight);
        }

        topMenuBar = new Rectangle(0, height - TOP_MENU_HEIGHT, width, TOP_MENU_HEIGHT);
        hamburgerButton = new Rectangle(width - HAMBURGER_SIZE - 12f,
                height - TOP_MENU_HEIGHT + (TOP_MENU_HEIGHT - HAMBURGER_SIZE) / 2f,
                HAMBURGER_SIZE, HAMBURGER_SIZE);
        endTurnButton = new Rectangle(width - HAMBURGER_SIZE - 12f - 150f - 10f,
                height - TOP_MENU_HEIGHT + 8f,
                150f,
                TOP_MENU_HEIGHT - 16f);

        float pmX = (width - PAUSE_MENU_WIDTH) / 2f;
        float pmY = (height - PAUSE_MENU_HEIGHT) / 2f;
        pauseMenuRect = new Rectangle(pmX, pmY, PAUSE_MENU_WIDTH, PAUSE_MENU_HEIGHT);
        pauseMenuButtons = new Rectangle[PAUSE_MENU_LABELS.length];
        float btnWidth = PAUSE_MENU_WIDTH - PAUSE_BUTTON_MARGIN * 2f;
        float startYButtons = pmY + PAUSE_MENU_HEIGHT - PAUSE_BUTTON_MARGIN - PAUSE_BUTTON_HEIGHT - 36f;
        for (int i = 0; i < PAUSE_MENU_LABELS.length; i++) {
            pauseMenuButtons[i] = new Rectangle(pmX + PAUSE_BUTTON_MARGIN,
                    startYButtons - i * (PAUSE_BUTTON_HEIGHT + 12f),
                    btnWidth, PAUSE_BUTTON_HEIGHT);
        }

        updateEditorLayout(width, height);
        rebuildFactionSelectionButtons(width, height);
    }

    private void updateEditorLayout(int width, int height) {
        float panelHeight = height - TOP_MENU_HEIGHT - EDITOR_PANEL_MARGIN * 2f;
        editorPanelRect = new Rectangle(width - EDITOR_PANEL_WIDTH - EDITOR_PANEL_MARGIN,
                EDITOR_PANEL_MARGIN,
                EDITOR_PANEL_WIDTH,
                Math.max(220f, panelHeight));

        float innerX = editorPanelRect.x + EDITOR_PANEL_MARGIN;
        float innerWidth = editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f;
        float panelTop = editorPanelRect.y + editorPanelRect.height;
        float titleBaselineY = panelTop - 12f;
        float badgeY = titleBaselineY - 34f - 24f;
        float cityNameY = badgeY - EDITOR_TOP_BLOCK_GAP - 24f;
        float toolY = cityNameY - 10f - 32f;

        editorToolButtons = new Rectangle[EditorTool.values().length];
        float toolWidth = (innerWidth - 8f * 2f) / 3f;
        for (int i = 0; i < editorToolButtons.length; i++) {
            editorToolButtons[i] = new Rectangle(innerX + i * (toolWidth + 8f), toolY, toolWidth, 32f);
        }
        editorFactionBadgeRect = new Rectangle(innerX, badgeY, innerWidth, 24f);
        float cityNameButtonWidth = 68f;
        float cityNameGap = 8f;
        editorCityNameRect = new Rectangle(innerX, cityNameY, innerWidth - cityNameButtonWidth - cityNameGap, 24f);
        editorCityNameButtonRect = new Rectangle(editorCityNameRect.x + editorCityNameRect.width + cityNameGap,
                cityNameY, cityNameButtonWidth, 24f);

        saveMapButton = new Rectangle(innerX, editorPanelRect.y + EDITOR_PANEL_MARGIN, innerWidth, EDITOR_SAVE_BUTTON_HEIGHT);
        overwriteMapButton = new Rectangle(innerX, saveMapButton.y + saveMapButton.height + 8f, innerWidth, EDITOR_SAVE_BUTTON_HEIGHT - 6f);
        editorValidationRect = new Rectangle(innerX, overwriteMapButton.y + overwriteMapButton.height + 8f,
                innerWidth, EDITOR_VALIDATION_HEIGHT);
        editorListViewportTop = toolY - 14f;
        editorListViewportBottom = editorValidationRect.y + editorValidationRect.height + 10f;

        rebuildCitySelectionButtons(innerX, innerWidth, toolY);
        terrainButtons = new Rectangle[TerrainType.values().length];
        float terrainWidth = (innerWidth - 10f) / 2f;
        factionButtons = new Rectangle[Faction.values().length + 1];
        for (int i = 0; i < terrainButtons.length; i++) {
            int column = i % 2;
            terrainButtons[i] = new Rectangle(innerX + column * (terrainWidth + 10f), 0f, terrainWidth, EDITOR_BUTTON_HEIGHT);
        }
        for (int i = 0; i < factionButtons.length; i++) {
            factionButtons[i] = new Rectangle(innerX, 0f, innerWidth, EDITOR_BUTTON_HEIGHT);
        }
        relayoutEditorScrollableContent();
    }

    private void setMenuState(MenuState state) {
        menuState = state;
        if (state == MenuState.MAIN) {
            menuLabels = new String[] {"はじめから", "つづきから", "ゲーム設定", "オプション"};
        } else if (state == MenuState.SETTINGS) {
            menuLabels = new String[] {"マップエディタ", "マップ自動生成", "戻る"};
        } else if (state == MenuState.OPTIONS) {
            menuLabels = buildOptionsMenuLabels();
        } else if (state == MenuState.AUTO_GENERATE) {
            menuLabels = new String[] {"生成する", "このマップで開始", "戻る"};
        } else if (state == MenuState.LOAD_SELECT) {
            rebuildSaveSelectionMenu();
            return;
        } else {
            rebuildMapSelectionMenu();
            return;
        }
        updateMenuLayout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void rebuildMapSelectionMenu() {
        selectableMaps.clear();
        MapDefinition officialMap = MapRepository.loadOfficialMap();
        if (officialMap != null) {
            selectableMaps.add(officialMap);
        }
        selectableMaps.addAll(MapRepository.loadCustomMaps());

        menuLabels = new String[selectableMaps.size() + 1];
        for (int i = 0; i < selectableMaps.size(); i++) {
            MapDefinition definition = selectableMaps.get(i);
            menuLabels[i] = definition.getDisplayName() + (definition.isOfficial() ? " [公式]" : " [カスタム]");
        }
        menuLabels[menuLabels.length - 1] = "戻る";
        updateMenuLayout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void rebuildSaveSelectionMenu() {
        selectableSaves.clear();
        selectableSaves.addAll(SaveRepository.loadSlots());

        menuLabels = new String[selectableSaves.size() + 1];
        for (int i = 0; i < selectableSaves.size(); i++) {
            SaveSlot slot = selectableSaves.get(i);
            String slotLabel = "スロット" + slot.getSlotIndex() + ": ";
            menuLabels[i] = slotLabel + (slot.isEmpty() ? "空き" : slot.getDisplayName());
        }
        menuLabels[menuLabels.length - 1] = "戻る";
        updateMenuLayout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void rebuildFactionSelectionButtons(int width, int height) {
        if (playableFactions.isEmpty()) {
            factionSelectButtons = new Rectangle[0];
            return;
        }
        float buttonWidth = Math.min(360f, width * 0.6f);
        float buttonHeight = 44f;
        float spacing = 10f;
        float totalHeight = playableFactions.size() * buttonHeight + (playableFactions.size() - 1) * spacing;
        float startX = (width - buttonWidth) / 2f;
        float startY = (height + totalHeight) / 2f - buttonHeight;
        factionSelectButtons = new Rectangle[playableFactions.size()];
        for (int i = 0; i < playableFactions.size(); i++) {
            factionSelectButtons[i] = new Rectangle(startX, startY - i * (buttonHeight + spacing), buttonWidth, buttonHeight);
        }
    }

    private void rebuildCitySelectionButtons(float innerX, float innerWidth, float topY) {
        Faction targetFaction = editorTool == EditorTool.FACTION
                ? editorFactionBrush
                : (selectedFaction != null ? selectedFaction : editorFactionBrush);
        List<HexMapModel.Tile> cityTiles = new ArrayList<>();
        if (targetFaction != null) {
            for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
                if (tile.faction == targetFaction && tile.cityCenter) {
                    cityTiles.add(tile);
                }
            }
        }

        cityTiles.sort((left, right) -> {
            if (left.capital != right.capital) {
                return left.capital ? -1 : 1;
            }
            return Integer.compare(left.cityId, right.cityId);
        });

        citySelectButtons = new Rectangle[cityTiles.size()];
        citySelectIds = new int[cityTiles.size()];
        citySelectLabels = new String[cityTiles.size()];
        float buttonHeight = 28f;
        float spacing = 6f;
        for (int i = 0; i < cityTiles.size(); i++) {
            HexMapModel.Tile tile = cityTiles.get(i);
            String label = tile.capital ? "★首都" : "都市";
            label += " #" + tile.cityId + " " + cityDisplayName(tile);
            citySelectButtons[i] = new Rectangle(innerX, 0f, innerWidth, buttonHeight);
            citySelectIds[i] = tile.cityId;
            citySelectLabels[i] = label;
        }
        relayoutEditorScrollableContent();
    }

    private void setExpandedEditorSection(EditorAccordionSection section) {
        if (expandedEditorSection == section) {
            expandedEditorSection = null;
        } else {
            expandedEditorSection = section;
        }
        relayoutEditorScrollableContent();
    }

    private void openEditorSection(EditorAccordionSection section) {
        if (expandedEditorSection != section) {
            expandedEditorSection = section;
            relayoutEditorScrollableContent();
        }
    }

    private boolean isEditorSectionExpanded(EditorAccordionSection section) {
        return expandedEditorSection == section;
    }

    private float layoutEditorSections(float innerX, float innerWidth, float terrainWidth, float startY, boolean applyLayout) {
        float contentCursorY = startY;

        if (applyLayout) {
            citySectionHeaderRect = new Rectangle(innerX, contentCursorY - EDITOR_SECTION_HEADER_HEIGHT,
                    innerWidth, EDITOR_SECTION_HEADER_HEIGHT);
        }
        contentCursorY -= EDITOR_SECTION_HEADER_HEIGHT;
        if (isEditorSectionExpanded(EditorAccordionSection.CITY)) {
            contentCursorY -= EDITOR_SECTION_HEADER_GAP;
            for (int i = 0; i < citySelectButtons.length; i++) {
                citySelectButtons[i].set(innerX,
                        contentCursorY - 28f - i * (28f + 6f),
                        innerWidth,
                        28f);
            }
            contentCursorY -= citySelectButtons.length * (28f + 6f);
        }
        contentCursorY -= EDITOR_SECTION_SPACING;

        if (applyLayout) {
            terrainSectionHeaderRect = new Rectangle(innerX, contentCursorY - EDITOR_SECTION_HEADER_HEIGHT,
                    innerWidth, EDITOR_SECTION_HEADER_HEIGHT);
        }
        contentCursorY -= EDITOR_SECTION_HEADER_HEIGHT;
        if (isEditorSectionExpanded(EditorAccordionSection.TERRAIN)) {
            contentCursorY -= EDITOR_SECTION_HEADER_GAP;
            for (int i = 0; i < terrainButtons.length; i++) {
                int column = i % 2;
                int row = i / 2;
                terrainButtons[i].set(innerX + column * (terrainWidth + 10f),
                        contentCursorY - EDITOR_BUTTON_HEIGHT - row * (EDITOR_BUTTON_HEIGHT + 6f),
                        terrainWidth,
                        EDITOR_BUTTON_HEIGHT);
            }
            contentCursorY -= ((terrainButtons.length + 1) / 2) * (EDITOR_BUTTON_HEIGHT + 6f);
        }
        contentCursorY -= EDITOR_SECTION_SPACING;

        if (applyLayout) {
            factionSectionHeaderRect = new Rectangle(innerX, contentCursorY - EDITOR_SECTION_HEADER_HEIGHT,
                    innerWidth, EDITOR_SECTION_HEADER_HEIGHT);
        }
        contentCursorY -= EDITOR_SECTION_HEADER_HEIGHT;
        if (isEditorSectionExpanded(EditorAccordionSection.FACTION)) {
            contentCursorY -= EDITOR_SECTION_HEADER_GAP;
            for (int i = 0; i < factionButtons.length; i++) {
                factionButtons[i].set(innerX,
                        contentCursorY - EDITOR_BUTTON_HEIGHT - i * (EDITOR_BUTTON_HEIGHT + 6f),
                        innerWidth,
                        EDITOR_BUTTON_HEIGHT);
            }
            contentCursorY -= factionButtons.length * (EDITOR_BUTTON_HEIGHT + 6f);
        }
        contentCursorY -= EDITOR_SECTION_SPACING;
        return contentCursorY;
    }

    private boolean handleMenuClick(float screenX, float uiY) {
        for (int i = 0; i < menuButtons.length; i++) {
            if (menuButtons[i] != null && menuButtons[i].contains(screenX, uiY)) {
                handleMenuSelection(i);
                return true;
            }
        }
        return false;
    }

    private void handleMenuSelection(int index) {
        if (menuState == MenuState.MAIN) {
            if (index == 0) {
                openMapSelection(GameMode.PLAY, MenuState.MAIN);
            } else if (index == 1) {
                openSaveSelection(SaveSelectionAction.LOAD_GAME, MenuState.MAIN);
            } else if (index == 2) {
                setMenuState(MenuState.SETTINGS);
                menuMessage = "";
            } else if (index == 3) {
                openOptions(MenuState.MAIN, false);
            }
            return;
        }

        if (menuState == MenuState.SETTINGS) {
            if (index == 0) {
                openMapSelection(GameMode.MAP_EDITOR, MenuState.SETTINGS);
            } else if (index == 1) {
                setMenuState(MenuState.AUTO_GENERATE);
                menuMessage = generatedPreviewMapDefinition == null
                        ? "生成する を押すと新しいマップを作成します。"
                        : "プレビュー生成済み: このマップで開始できます。";
            } else if (index == 2) {
                setMenuState(MenuState.MAIN);
                menuMessage = "";
            }
            return;
        }

        if (menuState == MenuState.AUTO_GENERATE) {
            if (index == 0) {
                generatePreviewMap();
            } else if (index == 1) {
                launchPreviewGeneratedMap();
            } else if (index == 2) {
                setMenuState(MenuState.SETTINGS);
                menuMessage = "";
            }
            return;
        }

        if (menuState == MenuState.OPTIONS) {
            if (index == 0) {
                windowPresetIndex = (windowPresetIndex + 1) % WINDOW_PRESETS.length;
                applyWindowPreset(true);
                menuMessage = "画面サイズを変更しました。";
                setMenuState(MenuState.OPTIONS);
            } else if (index == 1) {
                adjustMasterVolume(-0.1f);
                setMenuState(MenuState.OPTIONS);
            } else if (index == 2) {
                adjustMasterVolume(0.1f);
                setMenuState(MenuState.OPTIONS);
            } else if (index == 3) {
                if (optionsReturnToGame) {
                    screenMode = ScreenMode.GAME;
                } else {
                    setMenuState(optionsReturnState);
                }
            }
            return;
        }

        if (menuState == MenuState.LOAD_SELECT) {
            if (index == menuLabels.length - 1) {
                if (saveSelectionAction == SaveSelectionAction.SAVE_GAME) {
                    screenMode = ScreenMode.GAME;
                } else {
                    setMenuState(saveSelectionReturnState);
                }
                menuMessage = "";
                return;
            }
            if (index >= 0 && index < selectableSaves.size()) {
                handleSaveSlotSelection(selectableSaves.get(index));
            }
            return;
        }

        if (index == menuLabels.length - 1) {
            setMenuState(mapSelectionReturnState);
            menuMessage = "";
            return;
        }

        if (index >= 0 && index < selectableMaps.size()) {
            launchStoredMap(selectableMaps.get(index), pendingMapSelectionMode);
        }
    }

    private void openMapSelection(GameMode targetMode, MenuState returnState) {
        pendingMapSelectionMode = targetMode;
        mapSelectionReturnState = returnState;
        setMenuState(MenuState.MAP_SELECT);
        menuMessage = selectableMaps.isEmpty() ? "利用可能なマップがありません。" : "";
    }

    private void openSaveSelection(SaveSelectionAction action, MenuState returnState) {
        saveSelectionAction = action;
        saveSelectionReturnState = returnState;
        screenMode = ScreenMode.MENU;
        setMenuState(MenuState.LOAD_SELECT);
        menuMessage = "";
    }

    private void openOptions(MenuState returnState, boolean returnToGame) {
        optionsReturnState = returnState;
        optionsReturnToGame = returnToGame;
        screenMode = ScreenMode.MENU;
        setMenuState(MenuState.OPTIONS);
        menuMessage = "";
    }

    private String[] buildOptionsMenuLabels() {
        int[] preset = WINDOW_PRESETS[windowPresetIndex];
        int volumePercent = Math.round(masterVolume * 100f);
        return new String[] {
                "解像度を変更: " + preset[0] + "x" + preset[1],
                "音量を下げる: " + volumePercent + "%",
                "音量を上げる: " + volumePercent + "%",
                "戻る"
        };
    }

    private void loadSystemSettings() {
        windowPresetIndex = preferences.getInteger("windowPresetIndex", 0);
        if (windowPresetIndex < 0 || windowPresetIndex >= WINDOW_PRESETS.length) {
            windowPresetIndex = 0;
        }
        masterVolume = preferences.getFloat("masterVolume", 1f);
        if (masterVolume < 0f) {
            masterVolume = 0f;
        }
        if (masterVolume > 1f) {
            masterVolume = 1f;
        }
    }

    private void applyWindowPreset(boolean save) {
        int[] preset = WINDOW_PRESETS[windowPresetIndex];
        Gdx.graphics.setWindowedMode(preset[0], preset[1]);
        if (save) {
            preferences.putInteger("windowPresetIndex", windowPresetIndex);
            preferences.flush();
        }
    }

    private void adjustMasterVolume(float delta) {
        masterVolume += delta;
        if (masterVolume < 0f) {
            masterVolume = 0f;
        }
        if (masterVolume > 1f) {
            masterVolume = 1f;
        }
        preferences.putFloat("masterVolume", masterVolume);
        preferences.flush();
        menuMessage = "音量を " + Math.round(masterVolume * 100f) + "% に変更しました。";
    }

    private void launchStoredMap(MapDefinition definition, GameMode targetMode) {
        currentMapDefinition = definition;
        hexMapModel.loadMapDefinition(definition);
        syncCityIdCounter();
        activeSaveSlot = -1;
        editorHasUnsavedChanges = false;
        enterGame(targetMode);
        menuMessage = "";
    }

    private void handleSaveSlotSelection(SaveSlot slot) {
        if (saveSelectionAction == SaveSelectionAction.SAVE_GAME) {
            SaveSlot saved = SaveRepository.saveToSlot(slot.getSlotIndex(), hexMapModel);
            activeSaveSlot = saved.getSlotIndex();
            menuMessage = "セーブしました: スロット" + saved.getSlotIndex();
            screenMode = ScreenMode.GAME;
            pauseMenuVisible = false;
            return;
        }

        if (slot.isEmpty()) {
            menuMessage = "このスロットにはセーブデータがありません。";
            return;
        }
        currentMapDefinition = slot.getMapDefinition();
        hexMapModel.loadMapDefinition(currentMapDefinition);
        activeSaveSlot = slot.getSlotIndex();
        enterGame(GameMode.PLAY);
        menuMessage = "ロードしました: スロット" + slot.getSlotIndex();
    }

    private void launchGeneratedMap() {
        currentMapDefinition = MapDefinition.generatedSeed("auto-generated", "自動生成マップ", false,
                ThreadLocalRandom.current().nextInt(), mapRows, mapColsEven, mapColsOdd);
        hexMapModel.loadMapDefinition(currentMapDefinition);
        syncCityIdCounter();
        activeSaveSlot = -1;
        enterGame(GameMode.PLAY);
        menuMessage = "新しい自動生成マップを開始しました。";
    }

    private void generatePreviewMap() {
        generatedPreviewMapDefinition = MapDefinition.generatedSeed("auto-generated", "自動生成マップ", false,
                ThreadLocalRandom.current().nextInt(), mapRows, mapColsEven, mapColsOdd);
        hexMapModel.loadMapDefinition(generatedPreviewMapDefinition);
        syncCityIdCounter();
        editorHasUnsavedChanges = false;
        menuMessage = "新しい自動生成マップを作成しました。";
    }

    private void launchPreviewGeneratedMap() {
        if (generatedPreviewMapDefinition == null) {
            menuMessage = "先に 生成する を押してください。";
            return;
        }
        currentMapDefinition = generatedPreviewMapDefinition;
        activeSaveSlot = -1;
        enterGame(GameMode.PLAY);
        menuMessage = "自動生成マップを開始しました。";
    }

    private void enterGame(GameMode targetMode) {
        gameMode = targetMode;
        screenMode = ScreenMode.GAME;
        pauseMenuVisible = false;
        selectedQ = Integer.MIN_VALUE;
        selectedR = Integer.MIN_VALUE;
        selectedFaction = null;
        selectedCityId = -1;
        editorTool = EditorTool.TERRAIN;
        editorTerrainBrush = TerrainType.PLAIN;
        editorFactionBrush = Faction.NORTH_FEDERATION;
        if (targetMode == GameMode.PLAY) {
            initializePlayState();
        } else {
            units.clear();
            playableFactions.clear();
            playerFaction = null;
            currentTurnFaction = null;
            selectedUnit = null;
        }
        centerCameraOnMap();
    }

    private void syncCityIdCounter() {
        int maxCityId = 0;
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (tile.cityId > maxCityId) {
                maxCityId = tile.cityId;
            }
        }
        nextCityId = Math.max(1, maxCityId + 1);
    }

    private void initializePlayState() {
        units.clear();
        playableFactions.clear();
        selectedUnit = null;
        selectedFaction = null;
        playerFaction = null;
        currentTurnFaction = null;
        turnNumber = 1;
        turnPhase = TurnPhase.PLAYER_FACTION_SELECT;
        Set<Faction> present = new HashSet<>();
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (tile.faction != null && !tile.terrain.isPolar()) {
                present.add(tile.faction);
            }
        }
        for (Faction faction : Faction.values()) {
            if (present.contains(faction)) {
                playableFactions.add(faction);
            }
        }
        rebuildFactionSelectionButtons(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        menuMessage = playableFactions.isEmpty()
                ? "プレイ可能な陣営がありません。"
                : "プレイヤー陣営を選択してください。";
    }

    private void startMatchWithPlayerFaction(Faction faction) {
        playerFaction = faction;
        spawnInitialUnits();
        beginTurn(playerFaction);
        menuMessage = "あなたの陣営: " + playerFaction.getLabel();
    }

    private void spawnInitialUnits() {
        units.clear();
        for (Faction faction : playableFactions) {
            HexMapModel.Tile spawnTile = findFactionCapitalTile(faction);
            if (spawnTile == null) {
                spawnTile = findFactionAnyLandTile(faction);
            }
            if (spawnTile != null && findUnitAt(spawnTile.q, spawnTile.r) == null) {
                units.add(new Unit(faction, spawnTile.q, spawnTile.r));
            }
        }
    }

    private HexMapModel.Tile findFactionCapitalTile(Faction faction) {
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (tile.faction == faction && tile.capital && tile.terrain.isPassable() && !tile.terrain.isPolar()) {
                return tile;
            }
        }
        return null;
    }

    private HexMapModel.Tile findFactionAnyLandTile(Faction faction) {
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (tile.faction == faction && tile.terrain.isPassable() && !tile.terrain.isPolar()) {
                return tile;
            }
        }
        return null;
    }

    private boolean handleGameClick(int screenX, int screenY, float uiY, int button) {
        if (button != Input.Buttons.LEFT && gameMode != GameMode.MAP_EDITOR) {
            return false;
        }
        if (gameMode == GameMode.PLAY && turnPhase == TurnPhase.PLAYER_FACTION_SELECT) {
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            return handleFactionSelectionClick(screenX, uiY);
        }

        if (button == Input.Buttons.LEFT && hamburgerButton != null && hamburgerButton.contains(screenX, uiY)) {
            pauseMenuVisible = !pauseMenuVisible;
            if (!pauseMenuVisible) {
                pendingEditorExitAction = PendingEditorExitAction.NONE;
            }
            return true;
        }

        if (button == Input.Buttons.LEFT && gameMode == GameMode.PLAY && turnPhase == TurnPhase.PLAYER_TURN
                && endTurnButton != null && endTurnButton.contains(screenX, uiY)) {
            endCurrentTurn();
            return true;
        }

        if (pauseMenuVisible) {
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            for (int i = 0; i < pauseMenuButtons.length; i++) {
                Rectangle pauseButton = pauseMenuButtons[i];
                if (pauseButton != null && pauseButton.contains(screenX, uiY)) {
                    handlePauseMenuSelection(i);
                    return true;
                }
            }
            if (pauseMenuRect != null && !pauseMenuRect.contains(screenX, uiY)) {
                pauseMenuVisible = false;
                pendingEditorExitAction = PendingEditorExitAction.NONE;
                return true;
            }
        }

        if (button == Input.Buttons.LEFT && gameMode == GameMode.MAP_EDITOR && handleEditorUiClick(screenX, uiY)) {
            return true;
        }

        if (gameMode == GameMode.MAP_EDITOR) {
            return applyEditorAtScreenCoordinate(screenX, screenY, false, button == Input.Buttons.RIGHT);
        }

        if (button != Input.Buttons.LEFT) {
            return false;
        }

        if (gameMode == GameMode.PLAY && turnPhase != TurnPhase.PLAYER_TURN) {
            menuMessage = "現在は " + (currentTurnFaction == null ? "待機中" : currentTurnFaction.getLabel() + " のターンです。");
            return true;
        }

        Vector3 world = new Vector3(screenX, screenY, 0);
        camera.unproject(world);
        world.x = hexMapModel.wrapWorldX(world.x);
        int[] rounded = hexMapModel.worldToAxialRounded(world.x, world.y);
        HexMapModel.Tile tile = hexMapModel.findTile(rounded[0], rounded[1]);
        if (tile == null) {
            selectedQ = Integer.MIN_VALUE;
            selectedR = Integer.MIN_VALUE;
            selectedFaction = null;
            return false;
        }

        selectedQ = tile.q;
        selectedR = tile.r;

        Unit clickedUnit = findUnitAt(tile.q, tile.r);
        if (selectedUnit == null) {
            if (clickedUnit != null && clickedUnit.faction == playerFaction) {
                selectedUnit = clickedUnit;
                selectedFaction = playerFaction;
                menuMessage = "ユニット選択: HP " + clickedUnit.hp + " / 移動 " + String.format("%.1f", clickedUnit.movementRemaining);
            } else {
                selectedFaction = tile.faction;
            }
            return true;
        }

        if (clickedUnit != null && clickedUnit.faction == playerFaction) {
            selectedUnit = clickedUnit;
            selectedFaction = playerFaction;
            return true;
        }

        if (clickedUnit != null && clickedUnit.faction != playerFaction) {
            if (tryAttackUnit(selectedUnit, clickedUnit)) {
                selectedUnit = null;
            }
            return true;
        }

        if (tryMoveUnit(selectedUnit, tile)) {
            selectedUnit = null;
        } else {
            selectedFaction = tile.faction;
        }
        return true;
    }

    private boolean handleFactionSelectionClick(float screenX, float uiY) {
        for (int i = 0; i < factionSelectButtons.length; i++) {
            Rectangle button = factionSelectButtons[i];
            if (button.contains(screenX, uiY)) {
                startMatchWithPlayerFaction(playableFactions.get(i));
                return true;
            }
        }
        return false;
    }

    private boolean applyEditorAtScreenCoordinate(int screenX, int screenY, boolean fromDrag, boolean erase) {
        Vector3 world = new Vector3(screenX, screenY, 0);
        camera.unproject(world);
        world.x = hexMapModel.wrapWorldX(world.x);
        int[] rounded = hexMapModel.worldToAxialRounded(world.x, world.y);
        HexMapModel.Tile tile = hexMapModel.findTile(rounded[0], rounded[1]);
        if (tile == null) {
            if (!fromDrag) {
                selectedQ = Integer.MIN_VALUE;
                selectedR = Integer.MIN_VALUE;
                selectedFaction = null;
            }
            return false;
        }
        if (fromDrag && tile.q == lastEditorPaintQ && tile.r == lastEditorPaintR) {
            return true;
        }
        selectedQ = tile.q;
        selectedR = tile.r;
        if (erase) {
            clearEditorCityArea(tile);
        } else {
            applyEditorToTile(tile, fromDrag);
        }
        lastEditorPaintQ = tile.q;
        lastEditorPaintR = tile.r;
        return true;
    }

    private void clearEditorCityArea(HexMapModel.Tile tile) {
        if (tile == null || tile.terrain.isPolar()) {
            menuMessage = "このタイルの都市圏は解除できません。";
            return;
        }
        if (tile.faction == null || tile.cityId < 0) {
            menuMessage = "解除できる都市圏がありません。";
            return;
        }

        Faction ownerFaction = tile.faction;
        int removedCityId = tile.cityId;
        if (tile.cityCenter) {
            for (HexMapModel.Tile other : hexMapModel.getTiles()) {
                if (other.cityId == removedCityId) {
                    other.faction = null;
                    other.capital = false;
                    other.cityCenter = false;
                    other.cityId = -1;
                }
            }
            if (selectedCityId == removedCityId) {
                selectedCityId = -1;
            }
            selectedFaction = ownerFaction;
            editorFactionBrush = ownerFaction;
            rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                    editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                    editorToolButtons[0].y);
            editorHasUnsavedChanges = true;
            menuMessage = "都市と都市圏を解除: " + ownerFaction.getLabel();
            return;
        }

        tile.faction = null;
        tile.capital = false;
        tile.cityCenter = false;
        tile.cityId = -1;
        selectedFaction = ownerFaction;
        editorFactionBrush = ownerFaction;
        rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                editorToolButtons[0].y);
        editorHasUnsavedChanges = true;
        menuMessage = "都市圏を解除: " + ownerFaction.getLabel();
    }

    private void handlePauseMenuSelection(int index) {
        String label = PAUSE_MENU_LABELS[index];
        if ("再開".equals(label)) {
            pauseMenuVisible = false;
            pendingEditorExitAction = PendingEditorExitAction.NONE;
        } else if ("セーブ".equals(label)) {
            if (gameMode == GameMode.MAP_EDITOR) {
                menuMessage = "右下のマップ保存から保存してください。";
                pauseMenuVisible = false;
                pendingEditorExitAction = PendingEditorExitAction.NONE;
                return;
            }
            pauseMenuVisible = false;
            pendingEditorExitAction = PendingEditorExitAction.NONE;
            openSaveSelection(SaveSelectionAction.SAVE_GAME, MenuState.MAIN);
            menuMessage = activeSaveSlot > 0
                    ? "保存先スロットを選択してください（現在: スロット" + activeSaveSlot + "）。"
                    : "保存先スロットを選択してください。";
        } else if ("設定".equals(label)) {
            pauseMenuVisible = false;
            pendingEditorExitAction = PendingEditorExitAction.NONE;
            openOptions(MenuState.MAIN, true);
        } else if ("メニューに戻る".equals(label)) {
            if (gameMode == GameMode.MAP_EDITOR && editorHasUnsavedChanges
                    && pendingEditorExitAction != PendingEditorExitAction.RETURN_TO_MENU) {
                pendingEditorExitAction = PendingEditorExitAction.RETURN_TO_MENU;
                menuMessage = "未保存の変更があります。もう一度押すとメニューに戻ります。";
                return;
            }
            pauseMenuVisible = false;
            pendingEditorExitAction = PendingEditorExitAction.NONE;
            screenMode = ScreenMode.MENU;
            setMenuState(MenuState.MAIN);
        }
    }

    private Unit findUnitAt(int q, int r) {
        for (Unit unit : units) {
            if (unit.q == q && unit.r == r && unit.hp > 0) {
                return unit;
            }
        }
        return null;
    }

    private boolean tryMoveUnit(Unit unit, HexMapModel.Tile destination) {
        if (unit == null || destination == null) {
            return false;
        }
        if (!isAdjacent(unit.q, unit.r, destination.q, destination.r)) {
            menuMessage = "移動は隣接タイルのみです。";
            return false;
        }
        if (!destination.terrain.isPassable() || destination.terrain.isPolar()) {
            menuMessage = "その地形には移動できません。";
            return false;
        }
        if (findUnitAt(destination.q, destination.r) != null) {
            menuMessage = "そのタイルには既にユニットがあります。";
            return false;
        }
        float moveCost = destination.terrain.getMovementCost();
        if (unit.movementRemaining < moveCost) {
            menuMessage = "移動力が足りません。";
            return false;
        }
        unit.q = destination.q;
        unit.r = destination.r;
        unit.movementRemaining -= moveCost;
        selectedFaction = destination.faction;
        menuMessage = "移動しました。残り移動力: " + String.format("%.1f", unit.movementRemaining);
        return true;
    }

    private boolean tryAttackUnit(Unit attacker, Unit defender) {
        if (attacker == null || defender == null) {
            return false;
        }
        if (!isAdjacent(attacker.q, attacker.r, defender.q, defender.r)) {
            menuMessage = "攻撃は隣接ユニットのみです。";
            return false;
        }
        if (attacker.movementRemaining <= 0f) {
            menuMessage = "このユニットは行動済みです。";
            return false;
        }
        defender.hp -= UNIT_ATTACK_DAMAGE;
        attacker.movementRemaining = 0f;
        if (defender.hp <= 0) {
            units.remove(defender);
            menuMessage = defender.faction.getLabel() + " のユニットを撃破しました。";
        } else {
            menuMessage = defender.faction.getLabel() + " へ攻撃（残HP " + defender.hp + "）";
        }
        if (defender.faction == selectedFaction && defender.hp <= 0) {
            selectedFaction = null;
        }
        removeDefeatedFactionsIfNeeded();
        return true;
    }

    private void removeDefeatedFactionsIfNeeded() {
        playableFactions.removeIf(faction -> !hasLivingUnit(faction));
        if (currentTurnFaction != null && !playableFactions.contains(currentTurnFaction) && !playableFactions.isEmpty()) {
            currentTurnFaction = playableFactions.get(0);
        }
    }

    private boolean hasLivingUnit(Faction faction) {
        for (Unit unit : units) {
            if (unit.faction == faction && unit.hp > 0) {
                return true;
            }
        }
        return false;
    }

    private void beginTurn(Faction faction) {
        currentTurnFaction = faction;
        for (Unit unit : units) {
            if (unit.faction == faction && unit.hp > 0) {
                unit.movementRemaining = UNIT_MAX_MOVEMENT;
            }
        }
        if (faction == playerFaction) {
            turnPhase = TurnPhase.PLAYER_TURN;
            menuMessage = "プレイヤーターン: " + faction.getLabel();
        } else {
            turnPhase = TurnPhase.AI_TURN;
            executeAiTurn(faction);
            endCurrentTurn();
        }
    }

    private void endCurrentTurn() {
        selectedUnit = null;
        removeDefeatedFactionsIfNeeded();
        if (playerFaction != null && !hasLivingUnit(playerFaction)) {
            turnPhase = TurnPhase.PLAYER_TURN;
            menuMessage = "あなたのユニットが全滅しました。";
            return;
        }
        if (playerFaction != null && playableFactions.size() == 1 && playableFactions.get(0) == playerFaction) {
            turnPhase = TurnPhase.PLAYER_TURN;
            currentTurnFaction = playerFaction;
            menuMessage = "勝利: 他勢力のユニットが全滅しました。";
            return;
        }
        if (playableFactions.isEmpty()) {
            return;
        }
        int currentIndex = playableFactions.indexOf(currentTurnFaction);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = (currentIndex + 1) % playableFactions.size();
        if (nextIndex == 0) {
            turnNumber++;
        }
        beginTurn(playableFactions.get(nextIndex));
    }

    private void executeAiTurn(Faction aiFaction) {
        List<Unit> aiUnits = new ArrayList<>();
        for (Unit unit : units) {
            if (unit.faction == aiFaction && unit.hp > 0) {
                aiUnits.add(unit);
            }
        }
        for (Unit aiUnit : aiUnits) {
            Unit target = findNearestEnemy(aiUnit);
            if (target == null) {
                continue;
            }
            if (isAdjacent(aiUnit.q, aiUnit.r, target.q, target.r)) {
                tryAttackUnit(aiUnit, target);
                continue;
            }
            HexMapModel.Tile nextTile = findBestNeighborToward(aiUnit, target);
            if (nextTile != null) {
                tryMoveUnit(aiUnit, nextTile);
            }
        }
        menuMessage = "AIターン終了: " + aiFaction.getLabel();
    }

    private Unit findNearestEnemy(Unit source) {
        Unit nearest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Unit unit : units) {
            if (unit.hp <= 0 || unit.faction == source.faction) {
                continue;
            }
            int distance = hexDistance(source.q, source.r, unit.q, unit.r);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = unit;
            }
        }
        return nearest;
    }

    private HexMapModel.Tile findBestNeighborToward(Unit mover, Unit target) {
        HexMapModel.Tile origin = hexMapModel.findTile(mover.q, mover.r);
        if (origin == null) {
            return null;
        }
        HexMapModel.Tile bestTile = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dir = 0; dir < 6; dir++) {
            HexMapModel.Tile neighbor = hexMapModel.getNeighborWrapped(origin, dir);
            if (neighbor == null || !neighbor.terrain.isPassable() || neighbor.terrain.isPolar()) {
                continue;
            }
            if (findUnitAt(neighbor.q, neighbor.r) != null) {
                continue;
            }
            if (mover.movementRemaining < neighbor.terrain.getMovementCost()) {
                continue;
            }
            int distance = hexDistance(neighbor.q, neighbor.r, target.q, target.r);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTile = neighbor;
            }
        }
        return bestTile;
    }

    private boolean isAdjacent(int q1, int r1, int q2, int r2) {
        HexMapModel.Tile origin = hexMapModel.findTile(q1, r1);
        if (origin == null) {
            return false;
        }
        for (int dir = 0; dir < 6; dir++) {
            HexMapModel.Tile neighbor = hexMapModel.getNeighborWrapped(origin, dir);
            if (neighbor != null && neighbor.q == q2 && neighbor.r == r2) {
                return true;
            }
        }
        return false;
    }

    private int hexDistance(int q1, int r1, int q2, int r2) {
        int dq = q1 - q2;
        int dr = r1 - r2;
        int ds = (q1 + r1) - (q2 + r2);
        if (ds < 0) {
            ds = -ds;
        }
        if (dq < 0) {
            dq = -dq;
        }
        if (dr < 0) {
            dr = -dr;
        }
        return (dq + dr + ds) / 2;
    }

    private boolean handleEditorUiClick(float screenX, float uiY) {
        if (editorPanelRect == null || !editorPanelRect.contains(screenX, uiY)) {
            return false;
        }

        for (int i = 0; i < editorToolButtons.length; i++) {
            if (editorToolButtons[i].contains(screenX, uiY)) {
                editorTool = EditorTool.values()[i];
                if (editorTool == EditorTool.TERRAIN) {
                    openEditorSection(EditorAccordionSection.TERRAIN);
                } else if (editorTool == EditorTool.FACTION) {
                    openEditorSection(EditorAccordionSection.FACTION);
                } else {
                    openEditorSection(EditorAccordionSection.CITY);
                }
                menuMessage = "";
                return true;
            }
        }

        if (editorFactionBadgeRect != null && editorFactionBadgeRect.contains(screenX, uiY)) {
            if (editorFactionBrush == null) {
                menuMessage = "国家ブラシが未選択です。";
                return true;
            }
            editorTool = EditorTool.FACTION;
            selectedFaction = editorFactionBrush;
            selectedCityId = -1;
            openEditorSection(EditorAccordionSection.FACTION);
            rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                    editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                    editorToolButtons[0].y);
            menuMessage = "国家ブラシを再選択: " + editorFactionBrush.getLabel();
            return true;
        }

        if (citySectionHeaderRect != null && citySectionHeaderRect.contains(screenX, uiY)) {
            setExpandedEditorSection(EditorAccordionSection.CITY);
            return true;
        }
        if (terrainSectionHeaderRect != null && terrainSectionHeaderRect.contains(screenX, uiY)) {
            setExpandedEditorSection(EditorAccordionSection.TERRAIN);
            return true;
        }
        if (factionSectionHeaderRect != null && factionSectionHeaderRect.contains(screenX, uiY)) {
            setExpandedEditorSection(EditorAccordionSection.FACTION);
            return true;
        }

        if (editorCityNameButtonRect != null && editorCityNameButtonRect.contains(screenX, uiY)) {
            if (selectedCityId < 0) {
                menuMessage = "先に都市を選択してください。";
                return true;
            }
            if (editorCityNameEditing) {
                commitEditorCityName();
            } else {
                startEditorCityNameEditing(selectedCityId);
            }
            return true;
        }

        if (editorCityNameRect != null && editorCityNameRect.contains(screenX, uiY)) {
            if (selectedCityId < 0) {
                menuMessage = "先に都市を選択してください。";
                return true;
            }
            startEditorCityNameEditing(selectedCityId);
            return true;
        }

        if (isEditorSectionExpanded(EditorAccordionSection.CITY) && citySelectButtons != null) {
            for (int i = 0; i < citySelectButtons.length; i++) {
                if (citySelectButtons[i] != null && citySelectButtons[i].contains(screenX, uiY)) {
                    cancelEditorCityNameEditing();
                    selectedCityId = citySelectIds[i];
                    for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
                        if (tile.cityCenter && tile.cityId == selectedCityId) {
                            editorTool = EditorTool.FACTION;
                            selectedFaction = tile.faction;
                            editorFactionBrush = tile.faction;
                            openEditorSection(EditorAccordionSection.CITY);
                            menuMessage = citySelectLabels[i] + " を選択しました。";
                            rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                                    editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                                    editorToolButtons[0].y);
                            return true;
                        }
                    }
                    menuMessage = "都市を見つけられませんでした。";
                    return true;
                }
            }
        }

        for (int i = 0; isEditorSectionExpanded(EditorAccordionSection.TERRAIN) && i < terrainButtons.length; i++) {
            if (isEditorScrollableButtonVisible(terrainButtons[i]) && terrainButtons[i].contains(screenX, uiY)) {
                editorTerrainBrush = TerrainType.values()[i];
                editorTool = EditorTool.TERRAIN;
                openEditorSection(EditorAccordionSection.TERRAIN);
                menuMessage = "地形ブラシ: " + terrainLabel(editorTerrainBrush);
                return true;
            }
        }

        for (int i = 0; isEditorSectionExpanded(EditorAccordionSection.FACTION) && i < factionButtons.length; i++) {
            if (isEditorScrollableButtonVisible(factionButtons[i]) && factionButtons[i].contains(screenX, uiY)) {
                editorFactionBrush = i == 0 ? null : Faction.values()[i - 1];
                editorTool = EditorTool.FACTION;
                selectedCityId = -1;
                selectedFaction = editorFactionBrush;
                openEditorSection(EditorAccordionSection.FACTION);
                menuMessage = editorFactionBrush == null ? "都市圏ブラシ: なし" : "都市圏ブラシ: " + editorFactionBrush.getLabel();
                rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                        editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                        editorToolButtons[0].y);
                return true;
            }
        }

        if (saveMapButton != null && saveMapButton.contains(screenX, uiY)) {
            String capitalWarning = validateCapitalsForSave();
            if (capitalWarning != null) {
                menuMessage = capitalWarning;
                return true;
            }
            MapDefinition savedMap = MapRepository.saveCustomMap(hexMapModel);
            currentMapDefinition = savedMap;
            editorHasUnsavedChanges = false;
            menuMessage = "カスタムマップを保存しました: " + savedMap.getDisplayName();
            return true;
        }

        if (overwriteMapButton != null && overwriteMapButton.contains(screenX, uiY)) {
            if (!canOverwriteCurrentMap()) {
                menuMessage = "上書き対象のカスタムマップを開いてください。";
                return true;
            }
            String capitalWarning = validateCapitalsForSave();
            if (capitalWarning != null) {
                menuMessage = capitalWarning;
                return true;
            }
            currentMapDefinition = MapRepository.overwriteCustomMap(hexMapModel, currentMapDefinition);
            editorHasUnsavedChanges = false;
            menuMessage = "カスタムマップを上書き保存しました: " + currentMapDefinition.getDisplayName();
            return true;
        }

        return true;
    }

    private void applyEditorToTile(HexMapModel.Tile tile, boolean fromDrag) {
        if (editorTool == EditorTool.TERRAIN) {
            if (editorTerrainBrush == TerrainType.WATER && tile.cityCenter) {
                menuMessage = "都市は極地以外の陸上タイルに置いてください。";
                return;
            }
            tile.terrain = editorTerrainBrush;
            if (tile.terrain.isPolar()) {
                tile.faction = null;
                tile.capital = false;
                tile.cityCenter = false;
                tile.cityId = -1;
            }
            menuMessage = "地形を変更: " + terrainLabel(tile.terrain);
        } else if (editorTool == EditorTool.FACTION) {
            if (tile.terrain.isPolar()) {
                menuMessage = "極地には都市圏を設定できません。";
                return;
            }
            if (tile.cityCenter) {
                if (tile.cityId < 0) {
                    tile.cityId = nextCityId++;
                }
                editorFactionBrush = tile.faction;
                selectedFaction = tile.faction;
                selectedCityId = tile.cityId;
                rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                        editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                        editorToolButtons[0].y);
                menuMessage = tile.capital ? "首都を選択: " + tile.faction.getLabel() : "都市を選択: " + tile.faction.getLabel();
                return;
            }
            if (selectedCityId < 0 || editorFactionBrush == null) {
                menuMessage = "先に都市を選択してください。";
                return;
            }
            if (tile.faction != null && tile.faction != editorFactionBrush) {
                menuMessage = "他国の都市圏は直接設定できません。先に解除してください。";
                return;
            }
            if (tile.cityId >= 0 && tile.cityId != selectedCityId) {
                menuMessage = "別の都市圏と重複します。";
                return;
            }
            tile.faction = editorFactionBrush;
            tile.cityId = selectedCityId;
            if (tile.capital) {
                tile.capital = false;
            }
            if (tile.cityCenter) {
                tile.cityCenter = false;
            }
            menuMessage = "都市圏を変更: " + tile.faction.getLabel();
        } else {
            if (tile.terrain.isPolar()) {
                menuMessage = "都市/首都は極地には設定できません。";
                return;
            }
            if (tile.faction == null) {
                Faction placementFaction = editorFactionBrush != null ? editorFactionBrush : selectedFaction;
                if (placementFaction == null) {
                    menuMessage = "先に国家を選択してください。";
                    return;
                }
                tile.faction = placementFaction;
            }
            if (tile.cityId < 0) {
                tile.cityId = nextCityId++;
            }
            if (!tile.cityCenter) {
                tile.cityCenter = true;
                tile.capital = false;
                selectedCityId = tile.cityId;
                selectedFaction = tile.faction;
                rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                        editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                        editorToolButtons[0].y);
                menuMessage = "都市を設定: " + tile.faction.getLabel();
            } else if (!tile.capital) {
                clearFactionCapital(tile.faction, tile);
                tile.capital = true;
                tile.cityCenter = true;
                selectedCityId = tile.cityId;
                selectedFaction = tile.faction;
                rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                        editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                        editorToolButtons[0].y);
                menuMessage = "首都に昇格しました: " + tile.faction.getLabel();
            } else {
                tile.capital = false;
                tile.cityCenter = true;
                selectedCityId = tile.cityId;
                selectedFaction = tile.faction;
                rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                        editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                        editorToolButtons[0].y);
                menuMessage = "都市に降格しました: " + tile.faction.getLabel();
            }
        }
        selectedFaction = tile.faction;
        editorHasUnsavedChanges = true;
    }

    private boolean canOverwriteCurrentMap() {
        return currentMapDefinition != null
                && !currentMapDefinition.isOfficial()
                && currentMapDefinition.getId() != null
                && currentMapDefinition.getId().startsWith("custom-map-");
    }

    private String validateCapitalsForSave() {
        List<String> missingCapitalFactions = collectMissingCapitalFactions();
        if (missingCapitalFactions.isEmpty()) {
            return null;
        }
        return "首都が未設定の国家があります: " + String.join(" / ", missingCapitalFactions);
    }

    private List<String> collectMissingCapitalFactions() {
        List<String> missingCapitalFactions = new ArrayList<>();
        for (Faction faction : Faction.values()) {
            boolean hasOwnedTile = false;
            boolean hasCapital = false;
            for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
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

    private String buildEditorValidationSummary() {
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
            return editorHasUnsavedChanges ? "保存チェック: 問題なし / 未保存あり" : "保存チェック: 問題なし";
        }
        return summarizeValidationList("首都未設定", missingCapitalFactions);
    }

    private List<String> collectCenterlessCityAreas() {
        List<String> centerlessCityAreas = new ArrayList<>();
        Set<Integer> seenCityIds = new HashSet<>();
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (tile.cityId < 0 || tile.faction == null) {
                continue;
            }
            if (seenCityIds.contains(tile.cityId)) {
                continue;
            }
            seenCityIds.add(tile.cityId);
            boolean hasCenter = false;
            for (HexMapModel.Tile other : hexMapModel.getTiles()) {
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

    private List<String> collectIsolatedCities() {
        List<String> isolatedCities = new ArrayList<>();
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (!tile.cityCenter || tile.cityId < 0 || tile.faction == null) {
                continue;
            }
            int cityAreaCount = 0;
            for (HexMapModel.Tile other : hexMapModel.getTiles()) {
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

    private String summarizeValidationList(String prefix, List<String> items) {
        if (items.size() <= 2) {
            return prefix + ": " + String.join(" / ", items);
        }
        return prefix + ": " + items.get(0) + " / " + items.get(1) + " / ほか" + (items.size() - 2) + "件";
    }

    private void clearFactionCapital(Faction faction, HexMapModel.Tile newCapitalTile) {
        for (HexMapModel.Tile other : hexMapModel.getTiles()) {
            if (other.faction == faction && other.capital && other != newCapitalTile) {
                other.capital = false;
                other.cityCenter = true;
            }
        }
    }

    private BitmapFont createFont(int size) {
        BitmapFont fallbackFont = null;

        // First try asset fonts
        for (String assetPath : JAPANESE_FONT_ASSET_PATHS) {
            FileHandle fontFile = Gdx.files.internal(assetPath);
            if (fontFile.exists()) {
                Gdx.app.log("LuminaGame", "Trying asset font: " + assetPath);
                fallbackFont = tryCreateFont(fontFile, size);
                if (fallbackFont != null) {
                    Gdx.app.log("LuminaGame", "Successfully loaded asset font: " + assetPath);
                    return fallbackFont;
                }
            }
        }

        // Then try system fonts
        for (String systemPath : JAPANESE_SYSTEM_FONT_PATHS) {
            try {
                FileHandle fontFile = Gdx.files.absolute(systemPath);
                if (fontFile.exists()) {
                    Gdx.app.log("LuminaGame", "Trying system font: " + systemPath);
                    fallbackFont = tryCreateFont(fontFile, size);
                    if (fallbackFont != null) {
                        Gdx.app.log("LuminaGame", "Successfully loaded system font: " + systemPath);
                        return fallbackFont;
                    }
                }
            } catch (Exception e) {
                Gdx.app.error("LuminaGame", "Error checking system font path: " + systemPath, e);
            }
        }

        Gdx.app.error("LuminaGame", "No usable Japanese font found.");
        return new BitmapFont();
    }

    private BitmapFont tryCreateFont(FileHandle fontFile, int size) {
        if (!isLikelyFontFile(fontFile)) {
            return null;
        }

        try {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = size;
            parameter.characters = JAPANESE_FONT_CHARACTERS + UI_JAPANESE_CHARS;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.renderCount = 2;
            parameter.incremental = true;
            parameter.hinting = FreeTypeFontGenerator.Hinting.None;
            parameter.flip = false;
            BitmapFont font = generator.generateFont(parameter);
            if (!fontSupportsEditorUi(font)) {
                font.dispose();
                generator.dispose();
                return null;
            }
            activeFontGenerators.add(generator);
            return font;
        } catch (Exception ex) {
            Gdx.app.error("LuminaGame", "Failed to load font: " + fontFile.path(), ex);
            return null;
        }
    }

    private boolean isLikelyFontFile(FileHandle fontFile) {
        if (!fontFile.exists()) {
            return false;
        }
        try (java.io.InputStream input = fontFile.read()) {
            byte[] header = new byte[4];
            int readBytes = input.read(header);
            if (readBytes < 4) {
                return false;
            }
            String magic = new String(header, "ISO-8859-1");
            return "OTTO".equals(magic)
                    || "ttcf".equals(magic)
                    || (header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00)
                    || "true".equals(magic);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean fontSupportsEditorUi(BitmapFont font) {
        String required = "編集モード地形都市圏都市首都平地街道砂漠森林丘陵南極北極山岳山脈海マップ保存";
        for (int i = 0; i < required.length(); i++) {
            if (!font.getData().hasGlyph(required.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void scrollEditorPanel(float delta) {
        if (editorScrollMax <= 0f) {
            return;
        }
        editorScrollOffset += delta;
        if (editorScrollOffset < 0f) {
            editorScrollOffset = 0f;
        }
        if (editorScrollOffset > editorScrollMax) {
            editorScrollOffset = editorScrollMax;
        }
        relayoutEditorScrollableContent();
    }

    private void relayoutEditorScrollableContent() {
        if (editorPanelRect == null || terrainButtons == null || factionButtons == null || editorToolButtons == null || editorToolButtons.length == 0) {
            return;
        }
        float innerX = editorPanelRect.x + EDITOR_PANEL_MARGIN;
        float innerWidth = editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f;
        float terrainWidth = (innerWidth - 10f) / 2f;
        float baseContentTop = editorListViewportTop;
        float baseContentBottom = layoutEditorSections(innerX, innerWidth, terrainWidth, baseContentTop, false);
        float desiredMax = Math.max(0f, editorListViewportBottom - baseContentBottom);
        if (Math.abs(desiredMax - editorScrollMax) > 0.01f) {
            editorScrollMax = desiredMax;
        }
        if (editorScrollOffset > editorScrollMax) {
            editorScrollOffset = editorScrollMax;
        }

        layoutEditorSections(innerX, innerWidth, terrainWidth, baseContentTop + editorScrollOffset, true);
    }

    private boolean isEditorScrollableButtonVisible(Rectangle button) {
        return button.y + button.height >= editorListViewportBottom && button.y <= editorListViewportTop;
    }

    private void centerCameraOnMap() {
        if (hexMapModel == null) {
            return;
        }
        float wrapWidth = hexMapModel.getHorizontalWrapWidth();
        float centerX = wrapWidth > 0f ? wrapWidth / 2f : (hexMapModel.getMapMinX() + hexMapModel.getMapMaxX()) / 2f;
        float centerY = (hexMapModel.getMapMinY() + hexMapModel.getMapMaxY()) / 2f;
        camera.position.set(centerX, centerY, 0);
        clampCameraToMap();
        camera.update();
    }

    private void clampCameraToMap() {
        if (hexMapModel == null) {
            return;
        }

        float halfW = camera.viewportWidth * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        float mapMinX = hexMapModel.getMapMinX();
        float mapMaxX = hexMapModel.getMapMaxX();
        float mapMinY = hexMapModel.getMapMinY();
        float mapMaxY = hexMapModel.getMapMaxY();
        if (mapMaxX <= mapMinX || mapMaxY <= mapMinY) {
            return;
        }

        float wrapWidth = hexMapModel.getHorizontalWrapWidth();
        if (wrapWidth > 0f) {
            camera.position.x = hexMapModel.wrapWorldX(camera.position.x);
        } else {
            float mapWidth = mapMaxX - mapMinX;
            if (mapWidth <= 2f * halfW) {
                camera.position.x = (mapMinX + mapMaxX) / 2f;
            } else {
                float minCamX = mapMinX + halfW;
                float maxCamX = mapMaxX - halfW;
                if (camera.position.x < minCamX) {
                    camera.position.x = minCamX;
                }
                if (camera.position.x > maxCamX) {
                    camera.position.x = maxCamX;
                }
            }
        }

        float topBarWorldHeight = screenMode == ScreenMode.GAME ? TOP_MENU_HEIGHT * camera.zoom : 0f;
        float visibleTopHalf = Math.max(0f, halfH - topBarWorldHeight);
        float minCamY = hexMapModel.getSouthPolarLimitY() + halfH;
        float maxCamY = hexMapModel.getNorthPolarLimitY() - visibleTopHalf;
        if (minCamY > maxCamY) {
            camera.position.y = (minCamY + maxCamY) / 2f;
        } else {
            if (camera.position.y < minCamY) {
                camera.position.y = minCamY;
            }
            if (camera.position.y > maxCamY) {
                camera.position.y = maxCamY;
            }
        }
    }

    @Override
    public void render() {
        if (screenMode == ScreenMode.MENU) {
            renderMenu();
            return;
        }

        updateGameCamera();
        renderGameWorld();
        renderGameOverlay();
    }

    private void updateGameCamera() {
        float delta = Gdx.graphics.getDeltaTime();
        float speed = 300f * delta * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.position.x -= speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            camera.position.x += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            camera.position.y += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.position.y -= speed;
        }

        float zoomSpeed = 1.5f;
        if (Gdx.input.isKeyPressed(Input.Keys.Z)) {
            camera.zoom -= zoomSpeed * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.X)) {
            camera.zoom += zoomSpeed * delta;
        }
        if (camera.zoom < 0.2f) {
            camera.zoom = 0.2f;
        }
        if (camera.zoom > MAX_CAMERA_ZOOM) {
            camera.zoom = MAX_CAMERA_ZOOM;
        }

        boolean middlePressed = Gdx.input.isButtonPressed(Input.Buttons.MIDDLE);
        int mouseX = Gdx.input.getX();
        int mouseY = Gdx.input.getY();
        if (middlePressed) {
            if (prevMiddleX >= 0 && prevMiddleY >= 0) {
                int dx = mouseX - prevMiddleX;
                int dy = mouseY - prevMiddleY;
                camera.position.x -= dx * camera.zoom;
                camera.position.y += dy * camera.zoom;
            }
            prevMiddleX = mouseX;
            prevMiddleY = mouseY;
        } else {
            prevMiddleX = -1;
            prevMiddleY = -1;
        }

        clampCameraToMap();
        camera.update();
    }

    private void renderGameWorld() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();
        hexMapRenderer.renderBackground(spriteBatch);
        spriteBatch.end();

        shapeRenderer.setProjectionMatrix(camera.combined);
        hexMapRenderer.renderFilled(shapeRenderer, selectedQ == Integer.MIN_VALUE ? null : selectedQ,
                selectedR == Integer.MIN_VALUE ? null : selectedR);
        hexMapRenderer.renderTerritoryOverlay(shapeRenderer, selectedFaction, selectedCityId);
        hexMapRenderer.renderBorders(shapeRenderer, selectedQ == Integer.MIN_VALUE ? null : selectedQ,
                selectedR == Integer.MIN_VALUE ? null : selectedR, selectedFaction);
        hexMapRenderer.renderFactionHighlight(shapeRenderer, selectedFaction);
        renderUnits();

        spriteBatch.begin();
        hexMapRenderer.renderLabels(spriteBatch);
        renderUnitLabels();
        renderCityNames();
        spriteBatch.end();
    }

    private void renderGameOverlay() {
        shapeRenderer.setProjectionMatrix(uiProjection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.06f, 0.08f, 0.12f, 0.85f);
        shapeRenderer.rect(topMenuBar.x, topMenuBar.y, topMenuBar.width, topMenuBar.height);

        shapeRenderer.setColor(0.18f, 0.22f, 0.28f, 1f);
        shapeRenderer.rect(hamburgerButton.x, hamburgerButton.y, hamburgerButton.width, hamburgerButton.height);
        if (gameMode == GameMode.PLAY && turnPhase == TurnPhase.PLAYER_TURN) {
            shapeRenderer.setColor(0.20f, 0.36f, 0.22f, 1f);
            shapeRenderer.rect(endTurnButton.x, endTurnButton.y, endTurnButton.width, endTurnButton.height);
        }

        if (gameMode == GameMode.MAP_EDITOR) {
            renderEditorPanelShapes();
        }
        if (gameMode == GameMode.PLAY && turnPhase == TurnPhase.PLAYER_FACTION_SELECT) {
            renderFactionSelectionPanelShapes();
        }

        if (pauseMenuVisible) {
            shapeRenderer.setColor(0.12f, 0.14f, 0.18f, 0.95f);
            shapeRenderer.rect(pauseMenuRect.x, pauseMenuRect.y, pauseMenuRect.width, pauseMenuRect.height);
            for (Rectangle button : pauseMenuButtons) {
                shapeRenderer.setColor(0.18f, 0.22f, 0.28f, 1f);
                shapeRenderer.rect(button.x, button.y, button.width, button.height);
            }
        }
        shapeRenderer.end();

        spriteBatch.setProjectionMatrix(uiProjection);
        spriteBatch.begin();
        menuFont.draw(spriteBatch, "≡", hamburgerButton.x + hamburgerButton.width * 0.18f,
                hamburgerButton.y + hamburgerButton.height * 0.72f);
        if (gameMode == GameMode.PLAY && turnPhase == TurnPhase.PLAYER_TURN) {
            menuFont.draw(spriteBatch, "ターン終了", endTurnButton.x + 18f, endTurnButton.y + endTurnButton.height * 0.68f);
        }

        String statusText = gameMode == GameMode.MAP_EDITOR ? buildEditorStatusText()
                : buildPlayStatusText();
        menuFont.draw(spriteBatch, statusText, 16f, topMenuBar.y + topMenuBar.height - 14f);
        if (!menuMessage.isEmpty()) {
            menuFont.draw(spriteBatch, menuMessage, 16f, topMenuBar.y + 18f);
        }

        if (gameMode == GameMode.MAP_EDITOR) {
            renderEditorPanelText();
        }
        if (gameMode == GameMode.PLAY && turnPhase == TurnPhase.PLAYER_FACTION_SELECT) {
            renderFactionSelectionPanelText();
        }

        if (pauseMenuVisible) {
            menuFont.draw(spriteBatch, "ポーズ", pauseMenuRect.x + 16f,
                    pauseMenuRect.y + pauseMenuRect.height - PAUSE_BUTTON_MARGIN - 4f);
            for (int i = 0; i < pauseMenuButtons.length; i++) {
                Rectangle button = pauseMenuButtons[i];
                menuFont.draw(spriteBatch, PAUSE_MENU_LABELS[i], button.x + 12f, button.y + PAUSE_BUTTON_HEIGHT * 0.7f);
            }
        }
        spriteBatch.end();
    }

    private void renderUnits() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Unit unit : units) {
            if (unit.hp <= 0 || !isUnitVisibleToPlayer(unit)) {
                continue;
            }
            HexMapModel.Tile tile = hexMapModel.findTile(unit.q, unit.r);
            if (tile == null) {
                continue;
            }
            shapeRenderer.setColor(unit.faction.getColor());
            shapeRenderer.circle(tile.center.x, tile.center.y, hexMapConfig.getHexSize() * 0.28f, 20);
            if (unit == selectedUnit) {
                shapeRenderer.setColor(1f, 1f, 1f, 0.75f);
                shapeRenderer.circle(tile.center.x, tile.center.y, hexMapConfig.getHexSize() * 0.36f, 20);
            }
        }
        shapeRenderer.end();
    }

    private void renderUnitLabels() {
        for (Unit unit : units) {
            if (unit.hp <= 0 || !isUnitVisibleToPlayer(unit)) {
                continue;
            }
            HexMapModel.Tile tile = hexMapModel.findTile(unit.q, unit.r);
            if (tile == null) {
                continue;
            }
            menuFont.draw(spriteBatch, String.valueOf(unit.hp), tile.center.x - 7f, tile.center.y + 4f);
        }
    }

    private void renderCityNames() {
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (!tile.cityCenter || tile.cityName == null || tile.cityName.isEmpty()) {
                continue;
            }
            menuFont.draw(spriteBatch, tile.cityName, tile.center.x - 18f, tile.center.y + hexMapConfig.getHexSize() * 0.95f);
        }
    }

    private boolean isUnitVisibleToPlayer(Unit unit) {
        if (playerFaction == null || unit.faction == playerFaction) {
            return true;
        }
        for (Unit playerUnit : units) {
            if (playerUnit.faction != playerFaction || playerUnit.hp <= 0) {
                continue;
            }
            if (hexDistance(playerUnit.q, playerUnit.r, unit.q, unit.r) <= PLAYER_VISION_RANGE) {
                return true;
            }
        }
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (tile.faction == playerFaction && tile.capital) {
                if (hexDistance(tile.q, tile.r, unit.q, unit.r) <= PLAYER_VISION_RANGE) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderFactionSelectionPanelShapes() {
        for (int i = 0; i < factionSelectButtons.length; i++) {
            Rectangle button = factionSelectButtons[i];
            Faction faction = playableFactions.get(i);
            com.badlogic.gdx.graphics.Color color = new com.badlogic.gdx.graphics.Color(faction.getColor());
            color.a = 0.9f;
            shapeRenderer.setColor(color);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }
    }

    private void renderFactionSelectionPanelText() {
        menuFont.draw(spriteBatch, "プレイヤー陣営を選択", 40f, Gdx.graphics.getHeight() - 120f);
        for (int i = 0; i < factionSelectButtons.length; i++) {
            Rectangle button = factionSelectButtons[i];
            menuFont.draw(spriteBatch, playableFactions.get(i).getLabel(), button.x + 12f, button.y + button.height * 0.7f);
        }
    }

    private String buildPlayStatusText() {
        if (turnPhase == TurnPhase.PLAYER_FACTION_SELECT) {
            return "陣営選択中";
        }
        String yourFaction = playerFaction == null ? "未選択" : playerFaction.getLabel();
        String turnFaction = currentTurnFaction == null ? "なし" : currentTurnFaction.getLabel();
        return "ターン " + turnNumber + " / あなた: " + yourFaction + " / 現在: " + turnFaction;
    }

    private void renderEditorPanelShapes() {
        boolean hasValidationWarnings = !collectMissingCapitalFactions().isEmpty()
                || !collectCenterlessCityAreas().isEmpty()
                || !collectIsolatedCities().isEmpty();
        shapeRenderer.setColor(0.10f, 0.12f, 0.18f, 0.94f);
        shapeRenderer.rect(editorPanelRect.x, editorPanelRect.y, editorPanelRect.width, editorPanelRect.height);

        if (editorFactionBadgeRect != null) {
            float pulse = 0.84f + 0.16f * (float) Math.sin(TimeUtils.millis() / 180.0);
            shapeRenderer.setColor(0.16f, 0.18f, 0.24f, 1f);
            shapeRenderer.rect(editorFactionBadgeRect.x, editorFactionBadgeRect.y, editorFactionBadgeRect.width, editorFactionBadgeRect.height);
            com.badlogic.gdx.graphics.Color brushColor = editorFactionBrush == null
                    ? new com.badlogic.gdx.graphics.Color(0.35f, 0.35f, 0.38f, 1f)
                    : new com.badlogic.gdx.graphics.Color(editorFactionBrush.getColor());
            shapeRenderer.setColor(brushColor);
            shapeRenderer.rect(editorFactionBadgeRect.x + 6f, editorFactionBadgeRect.y + 4f, 18f, 16f);
            if (editorFactionBrush != null) {
                com.badlogic.gdx.graphics.Color glow = new com.badlogic.gdx.graphics.Color(editorFactionBrush.getColor());
                glow.a = pulse;
                shapeRenderer.setColor(glow);
                shapeRenderer.rect(editorFactionBadgeRect.x, editorFactionBadgeRect.y + editorFactionBadgeRect.height - 3f,
                        editorFactionBadgeRect.width, 3f);
            }
        }
        if (editorCityNameRect != null) {
            shapeRenderer.setColor(editorCityNameEditing ? 0.28f : 0.16f, 0.18f, editorCityNameEditing ? 0.34f : 0.24f, 1f);
            shapeRenderer.rect(editorCityNameRect.x, editorCityNameRect.y, editorCityNameRect.width, editorCityNameRect.height);
        }
        if (editorCityNameButtonRect != null) {
            boolean canEditCityName = selectedCityId >= 0;
            shapeRenderer.setColor(
                    editorCityNameEditing ? 0.36f : (canEditCityName ? 0.24f : 0.18f),
                    editorCityNameEditing ? 0.30f : (canEditCityName ? 0.28f : 0.20f),
                    editorCityNameEditing ? 0.18f : (canEditCityName ? 0.36f : 0.22f),
                    1f);
            shapeRenderer.rect(editorCityNameButtonRect.x, editorCityNameButtonRect.y,
                    editorCityNameButtonRect.width, editorCityNameButtonRect.height);
        }
        renderEditorAccordionHeader(citySectionHeaderRect, isEditorSectionExpanded(EditorAccordionSection.CITY));
        renderEditorAccordionHeader(terrainSectionHeaderRect, isEditorSectionExpanded(EditorAccordionSection.TERRAIN));
        renderEditorAccordionHeader(factionSectionHeaderRect, isEditorSectionExpanded(EditorAccordionSection.FACTION));

        for (int i = 0; i < editorToolButtons.length; i++) {
            Rectangle button = editorToolButtons[i];
            boolean active = editorTool == EditorTool.values()[i];
            shapeRenderer.setColor(active ? 0.42f : 0.20f, active ? 0.32f : 0.22f, active ? 0.18f : 0.28f, 1f);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }

        TerrainType[] terrains = TerrainType.values();
        for (int i = 0; isEditorSectionExpanded(EditorAccordionSection.TERRAIN) && i < terrainButtons.length; i++) {
            Rectangle button = terrainButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            com.badlogic.gdx.graphics.Color terrainColor = terrainButtonColor(terrains[i]);
            if (editorTerrainBrush == terrains[i]) {
                terrainColor = terrainColor.cpy().lerp(com.badlogic.gdx.graphics.Color.WHITE, 0.35f);
            }
            shapeRenderer.setColor(terrainColor);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }

        if (isEditorSectionExpanded(EditorAccordionSection.CITY) && citySelectButtons != null) {
            for (int i = 0; i < citySelectButtons.length; i++) {
                Rectangle button = citySelectButtons[i];
                if (!isEditorScrollableButtonVisible(button)) {
                    continue;
                }
                if (citySelectIds[i] == selectedCityId) {
                    shapeRenderer.setColor(0.42f, 0.36f, 0.18f, 1f);
                } else {
                    shapeRenderer.setColor(0.22f, 0.24f, 0.30f, 0.92f);
                }
                shapeRenderer.rect(button.x, button.y, button.width, button.height);
            }
        }

        for (int i = 0; isEditorSectionExpanded(EditorAccordionSection.FACTION) && i < factionButtons.length; i++) {
            Rectangle button = factionButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            com.badlogic.gdx.graphics.Color color = i == 0
                    ? new com.badlogic.gdx.graphics.Color(0.20f, 0.20f, 0.22f, 1f)
                    : new com.badlogic.gdx.graphics.Color(Faction.values()[i - 1].getColor());
            if ((i == 0 && editorFactionBrush == null) || (i > 0 && editorFactionBrush == Faction.values()[i - 1])) {
                color = color.cpy().lerp(com.badlogic.gdx.graphics.Color.WHITE, 0.30f);
            }
            shapeRenderer.setColor(color);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }

        if (editorScrollMax > 0f) {
            float trackX = editorPanelRect.x + editorPanelRect.width - 8f;
            float trackHeight = editorListViewportTop - editorListViewportBottom;
            shapeRenderer.setColor(0.20f, 0.22f, 0.28f, 1f);
            shapeRenderer.rect(trackX, editorListViewportBottom, 4f, trackHeight);
            float thumbHeight = Math.max(28f, trackHeight * (trackHeight / (trackHeight + editorScrollMax)));
            float travel = Math.max(0f, trackHeight - thumbHeight);
            float thumbY = editorListViewportTop - thumbHeight - (travel * (editorScrollOffset / editorScrollMax));
            shapeRenderer.setColor(0.55f, 0.60f, 0.70f, 1f);
            shapeRenderer.rect(trackX - 1f, thumbY, 6f, thumbHeight);
        }

        if (editorValidationRect != null) {
            shapeRenderer.setColor(hasValidationWarnings ? 0.44f : (editorHasUnsavedChanges ? 0.24f : 0.16f),
                    hasValidationWarnings ? 0.20f : (editorHasUnsavedChanges ? 0.24f : 0.28f),
                    hasValidationWarnings ? 0.18f : (editorHasUnsavedChanges ? 0.14f : 0.22f),
                    1f);
            shapeRenderer.rect(editorValidationRect.x, editorValidationRect.y,
                    editorValidationRect.width, editorValidationRect.height);
        }

        shapeRenderer.setColor(hasValidationWarnings ? 0.42f : (editorHasUnsavedChanges ? 0.30f : 0.22f),
                hasValidationWarnings ? 0.26f : (editorHasUnsavedChanges ? 0.46f : 0.42f),
                hasValidationWarnings ? 0.20f : (editorHasUnsavedChanges ? 0.26f : 0.30f),
                1f);
        shapeRenderer.rect(saveMapButton.x, saveMapButton.y, saveMapButton.width, saveMapButton.height);
        shapeRenderer.setColor(canOverwriteCurrentMap()
                        ? (hasValidationWarnings ? 0.46f : (editorHasUnsavedChanges ? 0.40f : 0.36f))
                        : 0.26f,
                hasValidationWarnings ? 0.24f : (editorHasUnsavedChanges ? 0.30f : 0.34f),
                hasValidationWarnings ? 0.18f : (editorHasUnsavedChanges ? 0.22f : 0.28f),
                1f);
        shapeRenderer.rect(overwriteMapButton.x, overwriteMapButton.y, overwriteMapButton.width, overwriteMapButton.height);
    }

    private void renderEditorPanelText() {
        boolean hasValidationWarnings = !collectMissingCapitalFactions().isEmpty()
                || !collectCenterlessCityAreas().isEmpty()
                || !collectIsolatedCities().isEmpty();
        float titleY = editorPanelRect.y + editorPanelRect.height - 4f;
        menuFont.draw(spriteBatch, "編集モード", editorPanelRect.x + 14f, titleY);
        if (editorFactionBadgeRect != null) {
            menuFont.draw(spriteBatch, "国家ブラシ: " + currentEditorFactionLabel(),
                    editorFactionBadgeRect.x + 30f, editorFactionBadgeRect.y + 18f);
        }
        if (editorCityNameRect != null) {
            HexMapModel.Tile selectedCity = findCityCenterTile(selectedCityId);
            String cityNameLabel = selectedCity == null ? "都市名: 未設定"
                    : "都市名: " + (editorCityNameEditing ? editorCityNameBuffer.toString() + "_" : cityDisplayName(selectedCity));
            menuFont.draw(spriteBatch, cityNameLabel, editorCityNameRect.x + 8f, editorCityNameRect.y + 18f);
        }
        if (editorCityNameButtonRect != null) {
            String cityNameButtonLabel = editorCityNameEditing ? "保存" : "編集";
            menuFont.draw(spriteBatch, cityNameButtonLabel,
                    editorCityNameButtonRect.x + 12f, editorCityNameButtonRect.y + 18f);
        }

        String[] toolLabels = {"地形", "都市圏", "都市/首都"};
        for (int i = 0; i < editorToolButtons.length; i++) {
            Rectangle button = editorToolButtons[i];
            menuFont.draw(spriteBatch, toolLabels[i], button.x + 10f, button.y + 22f);
        }

        renderEditorAccordionHeaderText(citySectionHeaderRect, "都市", citySelectButtons.length,
                isEditorSectionExpanded(EditorAccordionSection.CITY));
        renderEditorAccordionHeaderText(terrainSectionHeaderRect, "地形", terrainButtons.length,
                isEditorSectionExpanded(EditorAccordionSection.TERRAIN));
        renderEditorAccordionHeaderText(factionSectionHeaderRect, "都市圏", factionButtons.length,
                isEditorSectionExpanded(EditorAccordionSection.FACTION));

        if (isEditorSectionExpanded(EditorAccordionSection.CITY) && citySelectButtons != null) {
            for (int i = 0; i < citySelectButtons.length; i++) {
                Rectangle button = citySelectButtons[i];
                if (!isEditorScrollableButtonVisible(button)) {
                    continue;
                }
                String label = citySelectLabels[i];
                if (citySelectIds[i] == selectedCityId) {
                    label = "▶ " + label;
                }
                menuFont.draw(spriteBatch, label, button.x + 8f, button.y + 21f);
            }
        }
        TerrainType[] terrains = TerrainType.values();
        for (int i = 0; isEditorSectionExpanded(EditorAccordionSection.TERRAIN) && i < terrainButtons.length; i++) {
            Rectangle button = terrainButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            menuFont.draw(spriteBatch, terrainLabel(terrains[i]), button.x + 8f, button.y + 21f);
        }

        for (int i = 0; isEditorSectionExpanded(EditorAccordionSection.FACTION) && i < factionButtons.length; i++) {
            Rectangle button = factionButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            String label = i == 0 ? "都市圏なし" : Faction.values()[i - 1].getLabel();
            menuFont.draw(spriteBatch, label, button.x + 8f, button.y + 21f);
        }

        if (editorValidationRect != null) {
            menuFont.setColor(hasValidationWarnings
                    ? com.badlogic.gdx.graphics.Color.SALMON
                    : (editorHasUnsavedChanges ? com.badlogic.gdx.graphics.Color.GOLD : com.badlogic.gdx.graphics.Color.WHITE));
            menuFont.draw(spriteBatch, buildEditorValidationSummary(), editorValidationRect.x + 8f, editorValidationRect.y + 26f);
            menuFont.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        }

        menuFont.draw(spriteBatch, "マップ保存", saveMapButton.x + 10f, saveMapButton.y + 31f);
        menuFont.draw(spriteBatch, "上書き保存", overwriteMapButton.x + 10f, overwriteMapButton.y + 28f);
    }

    private String buildEditorStatusText() {
        String toolText;
        if (editorTool == EditorTool.TERRAIN) {
            toolText = "地形:" + terrainLabel(editorTerrainBrush);
        } else if (editorTool == EditorTool.FACTION) {
            toolText = "都市圏:" + (editorFactionBrush == null ? "なし" : editorFactionBrush.getLabel())
                    + (selectedCityId < 0 ? " / 都市未選択" : " / 都市ID " + selectedCityId);
        } else {
            toolText = "都市/首都" + (selectedCityId < 0 ? "" : " / 都市ID " + selectedCityId);
        }
        return "モード: マップエディタ / " + (editorHasUnsavedChanges ? "未保存あり / " : "")
                + "国家:" + currentEditorFactionLabel() + " / " + toolText;
    }

    private HexMapModel.Tile findCityCenterTile(int cityId) {
        if (cityId < 0) {
            return null;
        }
        for (HexMapModel.Tile tile : hexMapModel.getTiles()) {
            if (tile.cityCenter && tile.cityId == cityId) {
                return tile;
            }
        }
        return null;
    }

    private String cityDisplayName(HexMapModel.Tile tile) {
        if (tile == null) {
            return "未設定";
        }
        if (tile.cityName != null && !tile.cityName.isEmpty()) {
            return tile.cityName;
        }
        return tile.capital ? "未設定の首都" : "未設定の都市";
    }

    private void renderEditorAccordionHeader(Rectangle rect, boolean expanded) {
        if (rect == null) {
            return;
        }
        shapeRenderer.setColor(expanded ? 0.24f : 0.16f, expanded ? 0.24f : 0.18f, expanded ? 0.34f : 0.26f, 1f);
        shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height);
    }

    private void renderEditorAccordionHeaderText(Rectangle rect, String label, int itemCount, boolean expanded) {
        if (rect == null || rect.y + rect.height < editorListViewportBottom || rect.y > editorListViewportTop) {
            return;
        }
        String prefix = expanded ? "[-] " : "[+] ";
        menuFont.draw(spriteBatch, prefix + label + " (" + itemCount + ")", rect.x + 8f, rect.y + 18f);
    }

    private void startEditorCityNameEditing(int cityId) {
        HexMapModel.Tile cityCenter = findCityCenterTile(cityId);
        if (cityCenter == null) {
            menuMessage = "先に都市を設定してください。";
            return;
        }
        editorCityNameEditing = true;
        editingCityNameId = cityId;
        editorCityNameBuffer.setLength(0);
        editorCityNameBuffer.append(cityCenter.cityName == null ? "" : cityCenter.cityName);
        menuMessage = "都市名を入力中: Enterで保存 / Escで戻る";
    }

    private void commitEditorCityName() {
        HexMapModel.Tile cityCenter = findCityCenterTile(editingCityNameId);
        if (cityCenter == null) {
            cancelEditorCityNameEditing();
            menuMessage = "先に都市を設定してください。";
            return;
        }
        cityCenter.cityName = editorCityNameBuffer.toString().trim();
        editorCityNameEditing = false;
        editingCityNameId = -1;
        editorHasUnsavedChanges = true;
        rebuildCitySelectionButtons(editorPanelRect.x + EDITOR_PANEL_MARGIN,
                editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f,
                editorToolButtons[0].y);
        menuMessage = "都市名を設定: " + cityDisplayName(cityCenter);
    }

    private void cancelEditorCityNameEditing() {
        editorCityNameEditing = false;
        editingCityNameId = -1;
        editorCityNameBuffer.setLength(0);
    }

    private String currentEditorFactionLabel() {
        return editorFactionBrush == null ? "なし" : editorFactionBrush.getLabel();
    }

    private com.badlogic.gdx.graphics.Color terrainButtonColor(TerrainType terrain) {
        switch (terrain) {
            case ROAD:
                return new com.badlogic.gdx.graphics.Color(0.72f, 0.64f, 0.44f, 1f);
            case DESERT:
                return new com.badlogic.gdx.graphics.Color(0.84f, 0.73f, 0.46f, 1f);
            case TUNDRA:
                return new com.badlogic.gdx.graphics.Color(0.70f, 0.78f, 0.74f, 1f);
            case FOREST:
                return new com.badlogic.gdx.graphics.Color(0.18f, 0.44f, 0.18f, 1f);
            case HILLS:
                return new com.badlogic.gdx.graphics.Color(0.50f, 0.45f, 0.28f, 1f);
            case ANTARCTIC:
            case ARCTIC:
                return new com.badlogic.gdx.graphics.Color(0.90f, 0.94f, 1.00f, 1f);
            case MOUNTAIN:
                return new com.badlogic.gdx.graphics.Color(0.55f, 0.55f, 0.58f, 1f);
            case MOUNTAIN_RANGE:
                return new com.badlogic.gdx.graphics.Color(0.35f, 0.33f, 0.40f, 1f);
            case WATER:
                return new com.badlogic.gdx.graphics.Color(0.22f, 0.42f, 0.82f, 1f);
            case PLAIN:
            default:
                return new com.badlogic.gdx.graphics.Color(0.64f, 0.75f, 0.52f, 1f);
        }
    }

    private String terrainLabel(TerrainType terrain) {
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

    private void renderMenu() {
        ScreenUtils.clear(0.05f, 0.08f, 0.12f, 1f);

        shapeRenderer.setProjectionMatrix(uiProjection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Rectangle button : menuButtons) {
            shapeRenderer.setColor(0.16f, 0.22f, 0.32f, 1f);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }
        shapeRenderer.end();

        spriteBatch.setProjectionMatrix(uiProjection);
        spriteBatch.begin();
        titleFont.draw(spriteBatch, "Lumina-Concordia", 40f, Gdx.graphics.getHeight() - 60f);
        menuFont.draw(spriteBatch, menuSubtitle(), 40f, Gdx.graphics.getHeight() - 104f);

        for (int i = 0; i < menuButtons.length; i++) {
            Rectangle button = menuButtons[i];
            menuFont.draw(spriteBatch, menuLabels[i], button.x + 24f, button.y + button.height - 18f);
        }

        if (!menuMessage.isEmpty()) {
            menuFont.draw(spriteBatch, menuMessage, 40f, 40f);
        }
        spriteBatch.end();
    }

    private String menuSubtitle() {
        if (menuState == MenuState.SETTINGS) {
            return "ゲーム設定";
        }
        if (menuState == MenuState.MAP_SELECT) {
            return pendingMapSelectionMode == GameMode.MAP_EDITOR ? "編集するマップを選択" : "開始するマップを選択";
        }
        if (menuState == MenuState.LOAD_SELECT) {
            return saveSelectionAction == SaveSelectionAction.SAVE_GAME
                    ? "保存先スロットを選択"
                    : "ロードするセーブデータを選択";
        }
        if (menuState == MenuState.AUTO_GENERATE) {
            return "マップ自動生成";
        }
        if (menuState == MenuState.OPTIONS) {
            return "オプション";
        }
        return "トップメニュー";
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        if (titleFont != null) {
            titleFont.dispose();
        }
        if (menuFont != null) {
            menuFont.dispose();
        }
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
        }
        if (hexMapRenderer != null) {
            hexMapRenderer.dispose();
        }
        for (FreeTypeFontGenerator generator : activeFontGenerators) {
            generator.dispose();
        }
        activeFontGenerators.clear();
    }
}
