package io.wispforest.owo.screen;

public enum QuickCraftStage {
    STARTING,
    IN_PROGRESS,
    ENDING;

    public static QuickCraftStage fromId(int id) {
        return values()[id];
    }
}
