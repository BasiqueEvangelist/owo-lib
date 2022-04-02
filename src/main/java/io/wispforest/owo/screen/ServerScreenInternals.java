package io.wispforest.owo.screen;

import io.wispforest.owo.Owo;
import io.wispforest.owo.access.ExtendedPlayerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.ApiStatus;

import java.text.MessageFormat;

@ApiStatus.Internal
public final class ServerScreenInternals {
    public static final Identifier OPEN_SCREEN_ID = new Identifier("owo", "open_screen");
    public static final Identifier SYNC_DATA_ID = new Identifier("owo", "sync_screen_data");
    public static final Identifier RUN_ACTION_ID = new Identifier("owo", "run_screen_action");

    private ServerScreenInternals() {

    }

    @Environment(EnvType.CLIENT)
    public static class Client {
        public static void register() {
            ClientPlayNetworking.registerGlobalReceiver(ServerScreenInternals.SYNC_DATA_ID, (client, handler, buf, responseSender) -> {
                int screenId = buf.readVarInt();
                ExtendedPlayerEntity player = (ExtendedPlayerEntity) client.player;
                ServerScreen<?> currentScreen = player.getCurrentServerScreen();

                if (currentScreen == null || currentScreen.screenId != screenId) {
                    Owo.LOGGER.warn("Received invalid sync data packet for {}, while current screen is {}", screenId, currentScreen == null ? "missing" : currentScreen.screenId);
                    return;
                }

                currentScreen.readSync(buf);
            });

            ClientPlayNetworking.registerGlobalReceiver(ServerScreenInternals.RUN_ACTION_ID, (client, handler, buf, responseSender) -> {
                int screenId = buf.readVarInt();
                ExtendedPlayerEntity player = (ExtendedPlayerEntity) client.player;

                ServerScreen<?> currentScreen = player.getCurrentServerScreen();

                if (currentScreen == null || currentScreen.screenId != screenId) {
                    Owo.LOGGER.warn("Received invalid sync data packet for {}, while current screen is {}", screenId, currentScreen == null ? "missing" : currentScreen.screenId);
                    return;
                }

                int actionId = buf.readVarInt();

                if (currentScreen.actions.size() <= actionId) {
                    Owo.LOGGER.warn("Invalid action id {} for screen {}", actionId, currentScreen);
                    return;
                }

                ServerScreenAction<?> action = currentScreen.actions.get(actionId);

                action.readAndRun(buf);
            });
        }
    }
}
