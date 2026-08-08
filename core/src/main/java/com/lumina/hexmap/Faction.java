package com.lumina.hexmap;

import com.badlogic.gdx.graphics.Color;

public enum Faction {
    NORTH_FEDERATION("北方連邦", new Color(0.60f, 0.70f, 0.78f, 1f), new Color(0.25f, 0.28f, 0.12f, 1f), 0.17f, 0.74f),
    HOLY_KINGDOM("神聖王国", new Color(0.90f, 0.83f, 0.45f, 1f), new Color(0.63f, 0.47f, 0.16f, 1f), 0.28f, 0.63f),
    EMPIRE("帝国", new Color(0.75f, 0.28f, 0.24f, 1f), new Color(0.50f, 0.27f, 0.20f, 1f), 0.40f, 0.58f),
    REPUBLIC("共和国", new Color(0.38f, 0.56f, 0.82f, 1f), new Color(0.14f, 0.26f, 0.39f, 1f), 0.16f, 0.39f),
    MARITIME_FEDERATION("海洋連邦", new Color(0.17f, 0.70f, 0.76f, 1f), new Color(0.25f, 0.37f, 0.48f, 1f), 0.30f, 0.28f),
    DRAGON_EMPIRE("龍皇国", new Color(0.93f, 0.76f, 0.33f, 1f), new Color(0.48f, 0.45f, 0.37f, 1f), 0.68f, 0.62f),
    EASTERN_REPUBLIC("東嶺共和国", new Color(0.66f, 0.50f, 0.80f, 1f), new Color(0.29f, 0.32f, 0.39f, 1f), 0.84f, 0.60f),
    SANDSEA_ALLIANCE("砂海同盟", new Color(0.86f, 0.60f, 0.29f, 1f), new Color(0.72f, 0.58f, 0.42f, 1f), 0.69f, 0.36f);

    private final String label;
    private final Color color;
    private final Color mapColor;
    private final float mapCenterX;
    private final float mapCenterY;

    Faction(String label, Color color, Color mapColor, float mapCenterX, float mapCenterY) {
        this.label = label;
        this.color = color;
        this.mapColor = mapColor;
        this.mapCenterX = mapCenterX;
        this.mapCenterY = mapCenterY;
    }

    public String getLabel() {
        return label;
    }

    public Color getColor() {
        return color;
    }

    public Color getMapColor() {
        return mapColor;
    }

    public float getMapCenterX() {
        return mapCenterX;
    }

    public float getMapCenterY() {
        return mapCenterY;
    }
}
