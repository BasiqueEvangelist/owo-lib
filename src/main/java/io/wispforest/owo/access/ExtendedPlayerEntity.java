package io.wispforest.owo.access;

import io.wispforest.owo.screen.ServerScreen;
import org.jetbrains.annotations.ApiStatus;

public interface ExtendedPlayerEntity {
    ServerScreen<?, ?> owo$getCurrentServerScreen();

    @ApiStatus.Internal
    void owo$setCurrentServerScreen(ServerScreen<?, ?> screen);
}
