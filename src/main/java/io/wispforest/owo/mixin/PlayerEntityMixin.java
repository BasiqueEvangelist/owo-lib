package io.wispforest.owo.mixin;

import io.wispforest.owo.access.ExtendedPlayerEntity;
import io.wispforest.owo.screen.ServerScreen;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin implements ExtendedPlayerEntity {
    private ServerScreen<?, ?> owo$currentServerScreen;

    @Override
    public ServerScreen<?, ?> owo$getCurrentServerScreen() {
        return owo$currentServerScreen;
    }

    @Override
    public void owo$setCurrentServerScreen(ServerScreen<?, ?> screen) {
        owo$currentServerScreen = screen;
    }
}
