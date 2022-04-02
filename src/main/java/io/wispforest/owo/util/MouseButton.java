package io.wispforest.owo.util;

import net.minecraft.util.ClickType;

public enum MouseButton {
    LEFT, RIGHT, MIDDLE;

    public ClickType toClickType() {
        return switch (this) {
            case LEFT -> ClickType.LEFT;
            case RIGHT -> ClickType.RIGHT;
            case MIDDLE -> null;
        };
    }

    public static MouseButton fromInt(int raw) {
        return switch (raw) {
            case 0 -> LEFT;
            case 1 -> RIGHT;
            case 2 -> MIDDLE;
            default -> null;
        };
    }
}
