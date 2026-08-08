package com.lumina.hexmap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class HexMapRenderer {
    private final HexMapModel model;
    private final HexMapConfig config;
    private final Texture backgroundTexture;
    private final BitmapFont font;

    public HexMapRenderer(HexMapModel model, HexMapConfig config, Texture backgroundTexture) {
        this.model = model;
        this.config = config;
        this.backgroundTexture = backgroundTexture;
        this.font = new BitmapFont();
        this.font.getData().setScale(0.65f);
        this.font.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
    }

    public void renderBackground(SpriteBatch spriteBatch) {
        if (backgroundTexture == null) return;

        float wrapWidth = model.getHorizontalWrapWidth();
        renderBackgroundCopy(spriteBatch, 0f);
        if (wrapWidth > 0f) {
            renderBackgroundCopy(spriteBatch, -wrapWidth);
            renderBackgroundCopy(spriteBatch, wrapWidth);
        }
    }

    private void renderBackgroundCopy(SpriteBatch spriteBatch, float xOffset) {
        float startX = model.getMapMinX();
        float startY = model.getMapMinY();
        float mapWidth = model.getMapMaxX() - startX;
        float mapHeight = model.getMapMaxY() - startY;
        float tileWidth = backgroundTexture.getWidth();
        float tileHeight = backgroundTexture.getHeight();

        if (tileWidth <= 0 || tileHeight <= 0) return;

        int columns = (int) Math.ceil(mapWidth / tileWidth);
        int rows = (int) Math.ceil(mapHeight / tileHeight);

        for (int row = 0; row <= rows; row++) {
            for (int col = 0; col <= columns; col++) {
                float x = startX + xOffset + col * tileWidth;
                float y = startY + row * tileHeight;
                spriteBatch.draw(backgroundTexture, x, y, tileWidth, tileHeight);
            }
        }
    }

    public void renderLabels(SpriteBatch spriteBatch) {
        GlyphLayout layout = new GlyphLayout();
        float wrapWidth = model.getHorizontalWrapWidth();
        float[] offsets = wrapWidth > 0f ? new float[] {-wrapWidth, 0f, wrapWidth} : new float[] {0f};
        for (float xOffset : offsets) {
            for (HexMapModel.Tile tile : model.getTiles()) {
                String label = labelForTerrain(tile.terrain);
                if (label.isEmpty()) continue;
                font.setColor(labelColorForTerrain(tile.terrain));
                layout.setText(font, label);
                float x = tile.center.x + xOffset - layout.width / 2f;
                float y = tile.center.y + layout.height / 2f;
                font.draw(spriteBatch, layout, x, y);
            }
        }
    }

    public void renderFilled(ShapeRenderer shapeRenderer, Integer selectedQ, Integer selectedR) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float wrapWidth = model.getHorizontalWrapWidth();
        float[] offsets = wrapWidth > 0f ? new float[] {-wrapWidth, 0f, wrapWidth} : new float[] {0f};
        for (float xOffset : offsets) {
            for (HexMapModel.Tile tile : model.getTiles()) {
                float drawX = tile.center.x + xOffset;
                shapeRenderer.setColor(colorForTerrain(tile.terrain));
                drawHexagon(shapeRenderer, drawX, tile.center.y);
                if (selectedQ != null && selectedR != null && tile.q == selectedQ && tile.r == selectedR) {
                    shapeRenderer.setColor(new Color(1f, 1f, 1f, 0.25f));
                    drawHexagon(shapeRenderer, drawX, tile.center.y);
                }
            }
        }
        shapeRenderer.end();
    }

    public void renderTerritoryOverlay(ShapeRenderer shapeRenderer, Faction highlightedFaction) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float wrapWidth = model.getHorizontalWrapWidth();
        float[] offsets = wrapWidth > 0f ? new float[] {-wrapWidth, 0f, wrapWidth} : new float[] {0f};
        for (float xOffset : offsets) {
            for (HexMapModel.Tile tile : model.getTiles()) {
                if (tile.faction == null) continue;
                float drawX = tile.center.x + xOffset;
                Color overlay = new Color(tile.faction.getColor());
                overlay.a = tile.faction == highlightedFaction ? 0.48f : 0.18f;
                shapeRenderer.setColor(overlay);
                drawHexagon(shapeRenderer, drawX, tile.center.y);
                if (tile.capital) {
                    Color capitalColor = new Color(tile.faction.getColor());
                    capitalColor.a = tile.faction == highlightedFaction ? 1f : 0.85f;
                    shapeRenderer.setColor(capitalColor);
                    shapeRenderer.circle(drawX, tile.center.y, config.getHexSize() * 0.28f, 12);
                }
            }
        }
        shapeRenderer.end();
    }

    public void renderBorders(ShapeRenderer shapeRenderer, Integer selectedQ, Integer selectedR, Faction highlightedFaction) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        float wrapWidth = model.getHorizontalWrapWidth();
        float[] offsets = wrapWidth > 0f ? new float[] {-wrapWidth, 0f, wrapWidth} : new float[] {0f};
        for (float xOffset : offsets) {
            for (HexMapModel.Tile tile : model.getTiles()) {
                if (selectedQ != null && selectedR != null && tile.q == selectedQ && tile.r == selectedR) {
                    shapeRenderer.setColor(Color.SKY);
                } else if (tile.capital) {
                    shapeRenderer.setColor(tile.faction.getColor());
                } else if (tile.faction != null) {
                    Color factionBorder = new Color(tile.faction.getColor());
                    factionBorder.a = tile.faction == highlightedFaction ? 0.95f : 0.45f;
                    shapeRenderer.setColor(factionBorder);
                } else {
                    shapeRenderer.setColor(Color.LIGHT_GRAY);
                }
                drawHexBorder(shapeRenderer, tile.center.x + xOffset, tile.center.y);
            }
        }
        shapeRenderer.end();
    }

    public void renderFactionHighlight(ShapeRenderer shapeRenderer, Faction highlightedFaction) {
        if (highlightedFaction == null) {
            return;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float wrapWidth = model.getHorizontalWrapWidth();
        float[] offsets = wrapWidth > 0f ? new float[] {-wrapWidth, 0f, wrapWidth} : new float[] {0f};
        Color edgeColor = new Color(1f, 0.96f, 0.55f, 0.95f);

        for (float xOffset : offsets) {
            for (HexMapModel.Tile tile : model.getTiles()) {
                if (tile.faction != highlightedFaction) {
                    continue;
                }

                float centerX = tile.center.x + xOffset;
                float centerY = tile.center.y;
                float[] points = hexVertices(centerX, centerY);
                for (int directionIndex = 0; directionIndex < 6; directionIndex++) {
                    HexMapModel.Tile neighbor = model.getNeighborWrapped(tile, directionIndex);
                    if (neighbor == null || neighbor.faction == null || neighbor.faction == highlightedFaction) {
                        continue;
                    }

                    int vertexIndexA = findBorderEdgeIndex(tile, neighbor);
                    int vertexIndexB = (directionIndex + 1) % 6;
                    vertexIndexB = (vertexIndexA + 1) % 6;
                    float x1 = points[vertexIndexA * 2];
                    float y1 = points[vertexIndexA * 2 + 1];
                    float x2 = points[vertexIndexB * 2];
                    float y2 = points[vertexIndexB * 2 + 1];
                    shapeRenderer.setColor(edgeColor);
                    shapeRenderer.rectLine(x1, y1, x2, y2, 4f);
                }
            }
        }

        shapeRenderer.end();
    }

    private int findBorderEdgeIndex(HexMapModel.Tile tile, HexMapModel.Tile neighbor) {
        float dx = neighbor.center.x - tile.center.x;
        float dy = neighbor.center.y - tile.center.y;
        float wrapWidth = model.getHorizontalWrapWidth();
        if (wrapWidth > 0f) {
            dx -= Math.round(dx / wrapWidth) * wrapWidth;
        }

        float bestScore = -Float.MAX_VALUE;
        int bestEdgeIndex = 0;
        float[] points = hexVertices(0f, 0f);
        for (int edgeIndex = 0; edgeIndex < 6; edgeIndex++) {
            int next = (edgeIndex + 1) % 6;
            float midX = (points[edgeIndex * 2] + points[next * 2]) * 0.5f;
            float midY = (points[edgeIndex * 2 + 1] + points[next * 2 + 1]) * 0.5f;
            float score = midX * dx + midY * dy;
            if (score > bestScore) {
                bestScore = score;
                bestEdgeIndex = edgeIndex;
            }
        }
        return bestEdgeIndex;
    }

    private void drawHexagon(ShapeRenderer shapeRenderer, float centerX, float centerY) {
        float[] points = hexVertices(centerX, centerY);
        for (int i = 0; i < 6; i++) {
            int next = (i + 1) % 6;
            shapeRenderer.triangle(
                    centerX, centerY,
                    points[i * 2], points[i * 2 + 1],
                    points[next * 2], points[next * 2 + 1]);
        }
    }

    private void drawHexBorder(ShapeRenderer shapeRenderer, float centerX, float centerY) {
        float[] points = hexVertices(centerX, centerY);
        shapeRenderer.polyline(points);
    }

    private float[] hexVertices(float centerX, float centerY) {
        float hexSize = config.getHexSize();
        float[] points = new float[12];
        for (int i = 0; i < 6; i++) {
            float angle = (float) Math.toRadians(60 * i - 30);
            points[i * 2] = centerX + hexSize * (float) Math.cos(angle);
            points[i * 2 + 1] = centerY + hexSize * (float) Math.sin(angle);
        }
        return points;
    }

    private Color colorForTerrain(TerrainType terrain) {
        switch (terrain) {
            case ROAD:
                return new Color(0.72f, 0.64f, 0.44f, 1f);
            case DESERT:
                return new Color(0.84f, 0.73f, 0.46f, 1f);
            case TUNDRA:
                return new Color(0.70f, 0.78f, 0.74f, 1f);
            case FOREST:
                return new Color(0.18f, 0.44f, 0.18f, 1f);
            case HILLS:
                return new Color(0.50f, 0.45f, 0.28f, 1f);
            case ANTARCTIC:
            case ARCTIC:
                return new Color(0.90f, 0.94f, 1.00f, 1f);
            case MOUNTAIN:
                return new Color(0.55f, 0.55f, 0.58f, 1f);
            case WATER:
                return new Color(0.22f, 0.42f, 0.82f, 1f);
            case PLAIN:
            default:
                return new Color(0.64f, 0.75f, 0.52f, 1f);
        }
    }

    private String labelForTerrain(TerrainType terrain) {
        switch (terrain) {
            case DESERT:
                return "DST";
            case TUNDRA:
                return "TUN";
            case FOREST:
                return "FOR";
            case HILLS:
                return "HIL";
            case ANTARCTIC:
            case ARCTIC:
                return "";
            case MOUNTAIN:
                return "MTN";
            case WATER:
                return "WTR";
            case ROAD:
                return "RD";
            case PLAIN:
            default:
                return "PLA";
        }
    }

    private Color labelColorForTerrain(TerrainType terrain) {
        switch (terrain) {
            case WATER:
            case FOREST:
            case HILLS:
            case MOUNTAIN:
                return Color.WHITE;
            case TUNDRA:
            case ANTARCTIC:
            case ARCTIC:
                return Color.BLACK;
            default:
                return Color.BLACK;
        }
    }

    public void dispose() {
        font.dispose();
    }
}
