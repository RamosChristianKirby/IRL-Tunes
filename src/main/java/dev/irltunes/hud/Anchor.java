package dev.irltunes.hud;

/** Screen position of the widget. */
public enum Anchor {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    /** 0 = left/top, 1 = center, 2 = right/bottom. */
    public final int h, v;

    Anchor(int h, int v) {
        this.h = h;
        this.v = v;
    }

    public int x(int screenWidth, int width, int offset) {
        return switch (h) {
            case 0 -> offset;
            case 1 -> (screenWidth - width) / 2;
            default -> screenWidth - width - offset;
        };
    }

    public int y(int screenHeight, int height, int offset) {
        return v == 0 ? offset : screenHeight - height - offset;
    }
}
