package io.wispforest.owo.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;

public class FakeScreenHandler extends ScreenHandler {
    protected FakeScreenHandler() {
        super(null, -1);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
