package io.wispforest.owo.client;

import io.wispforest.owo.screen.ServerScreenInternals;
import io.wispforest.owo.screen.ServerScreenType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.ApiStatus;

@Environment(EnvType.CLIENT)
@ApiStatus.Internal
public class OwoClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ServerScreenInternals.Client.register();
    }
}
