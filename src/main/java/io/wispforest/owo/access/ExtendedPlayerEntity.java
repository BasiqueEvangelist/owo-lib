package io.wispforest.owo.access;

import io.wispforest.owo.screen.ServerScreen;
import org.jetbrains.annotations.ApiStatus;

public interface ExtendedPlayerEntity {
    ServerScreen<?> getCurrentServerScreen();

    @ApiStatus.Internal
    void setCurrentServerScreen(ServerScreen<?> screen);
}
