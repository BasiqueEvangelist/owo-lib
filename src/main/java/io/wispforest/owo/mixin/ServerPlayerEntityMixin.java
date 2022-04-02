package io.wispforest.owo.mixin;

import io.wispforest.owo.access.ExtendedPlayerEntity;
import io.wispforest.owo.screen.ServerScreen;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements ExtendedPlayerEntity {
    private ServerScreen<?> owo$currentServerScreen;

    @Override
    public ServerScreen<?> getCurrentServerScreen() {
        return owo$currentServerScreen;
    }

    @Override
    public void setCurrentServerScreen(ServerScreen<?> screen) {
        owo$currentServerScreen = screen;
    }
}
