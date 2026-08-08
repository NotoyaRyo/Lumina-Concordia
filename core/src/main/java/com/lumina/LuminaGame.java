package com.lumina;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class LuminaGame extends ApplicationAdapter {
    private enum ScreenMode { MENU, GAME }
    private enum MenuState { MAIN, SETTINGS, MAP_SELECT, LOAD_SELECT }
    private enum GameMode { PLAY, MAP_EDITOR }
    private enum EditorTool { TERRAIN, FACTION, CAPITAL }
    private enum SaveSelectionAction { LOAD_GAME, SAVE_GAME }

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
    private static final String[] PAUSE_MENU_LABELS = {"再開", "セーブ", "設定", "メニューに戻る"};
    private static final String JAPANESE_FONT_CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS
            + "あいうえおかきくけこがぎぐげござじずぜぞさしすせそただちつてとだぢっづなにぬねのはひふへほばびぶべぼぱぴぷぺぽまみむめもゃやゅゆょよらりるれろわをん"
            + "ァィゥェォカキクケコガギグゲゴサシスセソザジズゼゾタチツテトダヂッヅナニヌネノハヒフヘホバビブベボパピプペポマミムメモャヤュユョヨラリルレロワヲン"
            + "ー〜。、！？「」『』北方連邦神聖王国帝国共和国海洋龍皇東嶺砂海同盟続設定戻選択公式編集保存自動生成領土地形首都なし右下開始図面平街道砂漠森林丘陵南北極山岳利用可能変更解除水域陸上指定新規しました"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            + "一二三四五六七八九十百千万億兆京";
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
    private static final String UI_JAPANESE_CHARS = "はじめからつづきからゲーム設定オプションマップ選択マップエディタマップ自動生成戻る公式カスタムポーズ再開セーブ設定メニューに戻る編集モード地形領土首都領土なしマップ保存保存しました新規自動生成マップ選択国モード山脈ロードデータスロット空き上書き";

    private OrthographicCamera camera;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private BitmapFont titleFont;
    private BitmapFont menuFont;
    private Matrix4 uiProjection;

    private Rectangle[] menuButtons = new Rectangle[0];
    private String[] menuLabels = new String[0];
    private final List<MapDefinition> selectableMaps = new ArrayList<>();
    private final List<SaveSlot> selectableSaves = new ArrayList<>();

    private Rectangle topMenuBar;
    private Rectangle hamburgerButton;
    private Rectangle pauseMenuRect;
    private Rectangle[] pauseMenuButtons;
    private boolean pauseMenuVisible = false;

    private Rectangle editorPanelRect;
    private Rectangle[] editorToolButtons;
    private Rectangle[] terrainButtons;
    private Rectangle[] factionButtons;
    private Rectangle saveMapButton;
    private Rectangle overwriteMapButton;
    private float editorScrollOffset = 0f;
    private float editorScrollMax = 0f;
    private float editorListViewportTop = 0f;
    private float editorListViewportBottom = 0f;
    private float terrainSectionLabelY = 0f;
    private float factionSectionLabelY = 0f;

    private String menuMessage = "";
    private ScreenMode screenMode = ScreenMode.MENU;
    private MenuState menuState = MenuState.MAIN;
    private MenuState mapSelectionReturnState = MenuState.MAIN;
    private MenuState saveSelectionReturnState = MenuState.MAIN;
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

    private final int mapRows = 38;
    private final int mapColsEven = 58;
    private final int mapColsOdd = 58;
    private int selectedQ = Integer.MIN_VALUE;
    private int selectedR = Integer.MIN_VALUE;
    private Faction selectedFaction;
    private int activeSaveSlot = -1;
    private int lastEditorPaintQ = Integer.MIN_VALUE;
    private int lastEditorPaintR = Integer.MIN_VALUE;
    private int prevMiddleX = -1;
    private int prevMiddleY = -1;

    @Override
    public void create() {
        shapeRenderer = new ShapeRenderer();
        spriteBatch = new SpriteBatch();
        titleFont = createFont(52);
        menuFont = createFont(24);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiProjection = new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

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
                if (button != Input.Buttons.LEFT) {
                    return false;
                }

                float uiY = Gdx.graphics.getHeight() - screenY;
                if (screenMode == ScreenMode.MENU) {
                    return handleMenuClick(screenX, uiY);
                }
                return handleGameClick(screenX, screenY, uiY);
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (screenMode != ScreenMode.GAME || gameMode != GameMode.MAP_EDITOR || pauseMenuVisible) {
                    return false;
                }
                if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                    return false;
                }
                float uiY = Gdx.graphics.getHeight() - screenY;
                if (editorPanelRect != null && editorPanelRect.contains(screenX, uiY)) {
                    return false;
                }
                return applyEditorAtScreenCoordinate(screenX, screenY, true);
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT) {
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
    }

    private void updateEditorLayout(int width, int height) {
        float panelHeight = height - TOP_MENU_HEIGHT - EDITOR_PANEL_MARGIN * 2f;
        editorPanelRect = new Rectangle(width - EDITOR_PANEL_WIDTH - EDITOR_PANEL_MARGIN,
                EDITOR_PANEL_MARGIN,
                EDITOR_PANEL_WIDTH,
                Math.max(220f, panelHeight));

        editorToolButtons = new Rectangle[EditorTool.values().length];
        float innerX = editorPanelRect.x + EDITOR_PANEL_MARGIN;
        float innerWidth = editorPanelRect.width - EDITOR_PANEL_MARGIN * 2f;
        float toolY = editorPanelRect.y + editorPanelRect.height - EDITOR_PANEL_MARGIN - 36f;
        float toolWidth = (innerWidth - 8f * 2f) / 3f;
        for (int i = 0; i < editorToolButtons.length; i++) {
            editorToolButtons[i] = new Rectangle(innerX + i * (toolWidth + 8f), toolY, toolWidth, 32f);
        }

        saveMapButton = new Rectangle(innerX, editorPanelRect.y + EDITOR_PANEL_MARGIN, innerWidth, EDITOR_SAVE_BUTTON_HEIGHT);
        overwriteMapButton = new Rectangle(innerX, saveMapButton.y + saveMapButton.height + 8f, innerWidth, EDITOR_SAVE_BUTTON_HEIGHT - 6f);
        editorListViewportTop = toolY - 10f;
        editorListViewportBottom = overwriteMapButton.y + overwriteMapButton.height + 16f;

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
                menuMessage = "オプション画面は後で実装します。";
            }
            return;
        }

        if (menuState == MenuState.SETTINGS) {
            if (index == 0) {
                openMapSelection(GameMode.MAP_EDITOR, MenuState.SETTINGS);
            } else if (index == 1) {
                launchGeneratedMap();
            } else if (index == 2) {
                setMenuState(MenuState.MAIN);
                menuMessage = "";
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

    private void launchStoredMap(MapDefinition definition, GameMode targetMode) {
        currentMapDefinition = definition;
        hexMapModel.loadMapDefinition(definition);
        activeSaveSlot = -1;
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
        activeSaveSlot = -1;
        enterGame(GameMode.PLAY);
        menuMessage = "新しい自動生成マップを開始しました。";
    }

    private void enterGame(GameMode targetMode) {
        gameMode = targetMode;
        screenMode = ScreenMode.GAME;
        pauseMenuVisible = false;
        selectedQ = Integer.MIN_VALUE;
        selectedR = Integer.MIN_VALUE;
        selectedFaction = null;
        editorTool = EditorTool.TERRAIN;
        editorTerrainBrush = TerrainType.PLAIN;
        editorFactionBrush = Faction.NORTH_FEDERATION;
        centerCameraOnMap();
    }

    private boolean handleGameClick(int screenX, int screenY, float uiY) {
        if (hamburgerButton != null && hamburgerButton.contains(screenX, uiY)) {
            pauseMenuVisible = !pauseMenuVisible;
            return true;
        }

        if (pauseMenuVisible) {
            for (int i = 0; i < pauseMenuButtons.length; i++) {
                Rectangle button = pauseMenuButtons[i];
                if (button != null && button.contains(screenX, uiY)) {
                    handlePauseMenuSelection(i);
                    return true;
                }
            }
            if (pauseMenuRect != null && !pauseMenuRect.contains(screenX, uiY)) {
                pauseMenuVisible = false;
                return true;
            }
        }

        if (gameMode == GameMode.MAP_EDITOR && handleEditorUiClick(screenX, uiY)) {
            return true;
        }

        if (gameMode == GameMode.MAP_EDITOR) {
            return applyEditorAtScreenCoordinate(screenX, screenY, false);
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
        if (tile.faction != null && tile.faction == selectedFaction) {
            selectedFaction = null;
            selectedQ = Integer.MIN_VALUE;
            selectedR = Integer.MIN_VALUE;
        } else {
            selectedFaction = tile.faction;
        }
        return true;
    }

    private boolean applyEditorAtScreenCoordinate(int screenX, int screenY, boolean fromDrag) {
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
        applyEditorToTile(tile, fromDrag);
        lastEditorPaintQ = tile.q;
        lastEditorPaintR = tile.r;
        return true;
    }

    private void handlePauseMenuSelection(int index) {
        String label = PAUSE_MENU_LABELS[index];
        if ("再開".equals(label)) {
            pauseMenuVisible = false;
        } else if ("セーブ".equals(label)) {
            if (gameMode == GameMode.MAP_EDITOR) {
                menuMessage = "右下のマップ保存から保存してください。";
                pauseMenuVisible = false;
                return;
            }
            pauseMenuVisible = false;
            openSaveSelection(SaveSelectionAction.SAVE_GAME, MenuState.MAIN);
            menuMessage = activeSaveSlot > 0
                    ? "保存先スロットを選択してください（現在: スロット" + activeSaveSlot + "）。"
                    : "保存先スロットを選択してください。";
        } else if ("設定".equals(label)) {
            pauseMenuVisible = false;
            screenMode = ScreenMode.MENU;
            setMenuState(MenuState.SETTINGS);
        } else if ("メニューに戻る".equals(label)) {
            pauseMenuVisible = false;
            screenMode = ScreenMode.MENU;
            setMenuState(MenuState.MAIN);
        }
    }

    private boolean handleEditorUiClick(float screenX, float uiY) {
        if (editorPanelRect == null || !editorPanelRect.contains(screenX, uiY)) {
            return false;
        }

        for (int i = 0; i < editorToolButtons.length; i++) {
            if (editorToolButtons[i].contains(screenX, uiY)) {
                editorTool = EditorTool.values()[i];
                menuMessage = "";
                return true;
            }
        }

        for (int i = 0; i < terrainButtons.length; i++) {
            if (isEditorScrollableButtonVisible(terrainButtons[i]) && terrainButtons[i].contains(screenX, uiY)) {
                editorTerrainBrush = TerrainType.values()[i];
                editorTool = EditorTool.TERRAIN;
                menuMessage = "地形ブラシ: " + terrainLabel(editorTerrainBrush);
                return true;
            }
        }

        for (int i = 0; i < factionButtons.length; i++) {
            if (isEditorScrollableButtonVisible(factionButtons[i]) && factionButtons[i].contains(screenX, uiY)) {
                editorFactionBrush = i == 0 ? null : Faction.values()[i - 1];
                editorTool = EditorTool.FACTION;
                menuMessage = editorFactionBrush == null ? "領土ブラシ: なし" : "領土ブラシ: " + editorFactionBrush.getLabel();
                return true;
            }
        }

        if (saveMapButton != null && saveMapButton.contains(screenX, uiY)) {
            MapDefinition savedMap = MapRepository.saveCustomMap(hexMapModel);
            currentMapDefinition = savedMap;
            menuMessage = "カスタムマップを保存しました: " + savedMap.getDisplayName();
            return true;
        }

        if (overwriteMapButton != null && overwriteMapButton.contains(screenX, uiY)) {
            if (!canOverwriteCurrentMap()) {
                menuMessage = "上書き対象のカスタムマップを開いてください。";
                return true;
            }
            currentMapDefinition = MapRepository.overwriteCustomMap(hexMapModel, currentMapDefinition);
            menuMessage = "カスタムマップを上書き保存しました: " + currentMapDefinition.getDisplayName();
            return true;
        }

        return true;
    }

    private void applyEditorToTile(HexMapModel.Tile tile, boolean fromDrag) {
        if (editorTool == EditorTool.TERRAIN) {
            tile.terrain = editorTerrainBrush;
            if (tile.terrain == TerrainType.WATER || tile.terrain.isPolar()) {
                tile.faction = null;
                tile.capital = false;
            }
            menuMessage = "地形を変更: " + terrainLabel(tile.terrain);
        } else if (editorTool == EditorTool.FACTION) {
            if (tile.terrain == TerrainType.WATER || tile.terrain.isPolar()) {
                menuMessage = "水域または極地には領土を設定できません。";
                return;
            }
            if (!fromDrag && tile.faction != null && tile.faction != editorFactionBrush) {
                editorFactionBrush = tile.faction;
                menuMessage = "領土ブラシを選択: " + editorFactionBrush.getLabel();
                selectedFaction = tile.faction;
                return;
            }
            tile.faction = editorFactionBrush;
            if (tile.faction == null) {
                tile.capital = false;
                menuMessage = "領土を解除しました。";
            } else {
                menuMessage = "領土を変更: " + tile.faction.getLabel();
            }
        } else {
            if (tile.faction == null || tile.terrain == TerrainType.WATER || tile.terrain.isPolar()) {
                menuMessage = "首都は陸上の領土タイルにのみ設定できます。";
                return;
            }
            if (tile.capital) {
                tile.capital = false;
                menuMessage = "首都指定を解除しました。";
            } else {
                clearFactionCapital(tile.faction);
                tile.capital = true;
                menuMessage = "首都を設定: " + tile.faction.getLabel();
            }
        }
        selectedFaction = tile.faction;
    }

    private boolean canOverwriteCurrentMap() {
        return currentMapDefinition != null
                && !currentMapDefinition.isOfficial()
                && currentMapDefinition.getId() != null
                && currentMapDefinition.getId().startsWith("custom-map-");
    }

    private void clearFactionCapital(Faction faction) {
        for (HexMapModel.Tile other : hexMapModel.getTiles()) {
            if (other.faction == faction) {
                other.capital = false;
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
            parameter.incremental = false;
            parameter.hinting = FreeTypeFontGenerator.Hinting.None;
            parameter.flip = false;
            BitmapFont font = generator.generateFont(parameter);
            generator.dispose();
            if (!fontSupportsEditorUi(font)) {
                font.dispose();
                return null;
            }
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
        String required = "編集モード地形領土首都平地街道砂漠森林丘陵南極北極山岳海マップ保存";
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
        float baseContentCursorY = editorToolButtons[0].y - 54f;
        int terrainRows = (terrainButtons.length + 1) / 2;
        float baseFactionStartY = baseContentCursorY - terrainRows * (EDITOR_BUTTON_HEIGHT + 6f) - 24f;
        float baseContentBottom = baseFactionStartY - (factionButtons.length - 1) * (EDITOR_BUTTON_HEIGHT + 6f);
        float desiredMax = Math.max(0f, editorListViewportBottom - baseContentBottom);
        if (Math.abs(desiredMax - editorScrollMax) > 0.01f) {
            editorScrollMax = desiredMax;
        }
        if (editorScrollOffset > editorScrollMax) {
            editorScrollOffset = editorScrollMax;
        }

        float contentCursorY = baseContentCursorY + editorScrollOffset;
        terrainSectionLabelY = contentCursorY + 20f;

        for (int i = 0; i < terrainButtons.length; i++) {
            int column = i % 2;
            int row = i / 2;
            terrainButtons[i].set(innerX + column * (terrainWidth + 10f),
                    contentCursorY - row * (EDITOR_BUTTON_HEIGHT + 6f),
                    terrainWidth,
                    EDITOR_BUTTON_HEIGHT);
        }

        contentCursorY = contentCursorY - terrainRows * (EDITOR_BUTTON_HEIGHT + 6f) - 24f;
        factionSectionLabelY = contentCursorY + 20f;

        for (int i = 0; i < factionButtons.length; i++) {
            factionButtons[i].set(innerX,
                    contentCursorY - i * (EDITOR_BUTTON_HEIGHT + 6f),
                    innerWidth,
                    EDITOR_BUTTON_HEIGHT);
        }
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
        hexMapRenderer.renderTerritoryOverlay(shapeRenderer, selectedFaction);
        hexMapRenderer.renderBorders(shapeRenderer, selectedQ == Integer.MIN_VALUE ? null : selectedQ,
                selectedR == Integer.MIN_VALUE ? null : selectedR, selectedFaction);
        hexMapRenderer.renderFactionHighlight(shapeRenderer, selectedFaction);

        spriteBatch.begin();
        hexMapRenderer.renderLabels(spriteBatch);
        spriteBatch.end();
    }

    private void renderGameOverlay() {
        shapeRenderer.setProjectionMatrix(uiProjection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.06f, 0.08f, 0.12f, 0.85f);
        shapeRenderer.rect(topMenuBar.x, topMenuBar.y, topMenuBar.width, topMenuBar.height);

        shapeRenderer.setColor(0.18f, 0.22f, 0.28f, 1f);
        shapeRenderer.rect(hamburgerButton.x, hamburgerButton.y, hamburgerButton.width, hamburgerButton.height);

        if (gameMode == GameMode.MAP_EDITOR) {
            renderEditorPanelShapes();
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

        String statusText = gameMode == GameMode.MAP_EDITOR ? buildEditorStatusText()
                : "選択国: " + (selectedFaction == null ? "なし" : selectedFaction.getLabel());
        menuFont.draw(spriteBatch, statusText, 16f, topMenuBar.y + topMenuBar.height - 14f);
        if (!menuMessage.isEmpty()) {
            menuFont.draw(spriteBatch, menuMessage, 16f, topMenuBar.y + 18f);
        }

        if (gameMode == GameMode.MAP_EDITOR) {
            renderEditorPanelText();
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

    private void renderEditorPanelShapes() {
        shapeRenderer.setColor(0.10f, 0.12f, 0.18f, 0.94f);
        shapeRenderer.rect(editorPanelRect.x, editorPanelRect.y, editorPanelRect.width, editorPanelRect.height);

        for (int i = 0; i < editorToolButtons.length; i++) {
            Rectangle button = editorToolButtons[i];
            boolean active = editorTool == EditorTool.values()[i];
            shapeRenderer.setColor(active ? 0.42f : 0.20f, active ? 0.32f : 0.22f, active ? 0.18f : 0.28f, 1f);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }

        TerrainType[] terrains = TerrainType.values();
        for (int i = 0; i < terrainButtons.length; i++) {
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

        for (int i = 0; i < factionButtons.length; i++) {
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

        shapeRenderer.setColor(0.22f, 0.42f, 0.30f, 1f);
        shapeRenderer.rect(saveMapButton.x, saveMapButton.y, saveMapButton.width, saveMapButton.height);
        shapeRenderer.setColor(canOverwriteCurrentMap() ? 0.36f : 0.26f, 0.34f, 0.28f, 1f);
        shapeRenderer.rect(overwriteMapButton.x, overwriteMapButton.y, overwriteMapButton.width, overwriteMapButton.height);
    }

    private void renderEditorPanelText() {
        float titleY = editorPanelRect.y + editorPanelRect.height - 12f;
        menuFont.draw(spriteBatch, "編集モード", editorPanelRect.x + 14f, titleY);

        String[] toolLabels = {"地形", "領土", "首都"};
        for (int i = 0; i < editorToolButtons.length; i++) {
            Rectangle button = editorToolButtons[i];
            menuFont.draw(spriteBatch, toolLabels[i], button.x + 10f, button.y + 22f);
        }

        if (terrainSectionLabelY >= editorListViewportBottom && terrainSectionLabelY <= editorListViewportTop + 28f) {
            menuFont.draw(spriteBatch, "地形", editorPanelRect.x + 14f, terrainSectionLabelY);
        }
        TerrainType[] terrains = TerrainType.values();
        for (int i = 0; i < terrainButtons.length; i++) {
            Rectangle button = terrainButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            menuFont.draw(spriteBatch, terrainLabel(terrains[i]), button.x + 8f, button.y + 21f);
        }

        if (factionSectionLabelY >= editorListViewportBottom && factionSectionLabelY <= editorListViewportTop + 28f) {
            menuFont.draw(spriteBatch, "領土", editorPanelRect.x + 14f, factionSectionLabelY);
        }
        for (int i = 0; i < factionButtons.length; i++) {
            Rectangle button = factionButtons[i];
            if (!isEditorScrollableButtonVisible(button)) {
                continue;
            }
            String label = i == 0 ? "領土なし" : Faction.values()[i - 1].getLabel();
            menuFont.draw(spriteBatch, label, button.x + 8f, button.y + 21f);
        }

        menuFont.draw(spriteBatch, "マップ保存", saveMapButton.x + 10f, saveMapButton.y + 31f);
        menuFont.draw(spriteBatch, "上書き保存", overwriteMapButton.x + 10f, overwriteMapButton.y + 28f);
        if (editorScrollMax > 0f) {
            menuFont.draw(spriteBatch, "ホイールでスクロール", editorPanelRect.x + 14f, overwriteMapButton.y + overwriteMapButton.height + 14f);
        }
    }

    private String buildEditorStatusText() {
        String toolText;
        if (editorTool == EditorTool.TERRAIN) {
            toolText = "地形:" + terrainLabel(editorTerrainBrush);
        } else if (editorTool == EditorTool.FACTION) {
            toolText = "領土:" + (editorFactionBrush == null ? "なし" : editorFactionBrush.getLabel());
        } else {
            toolText = "首都";
        }
        return "モード: マップエディタ / " + toolText;
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
    }
}
