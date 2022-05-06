package io.wispforest.owo.util;

import net.minecraft.util.ClickType;

public final class MouseButton {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int MIDDLE = 2;

    private MouseButton() {

    }

    public static ClickType toClickType(int id) {
        return switch (id) {
            case LEFT -> ClickType.LEFT;
            case RIGHT -> ClickType.RIGHT;
            default -> null;
        };
    }
}
