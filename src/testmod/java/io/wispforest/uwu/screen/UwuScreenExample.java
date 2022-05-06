package io.wispforest.uwu.screen;

import io.wispforest.owo.screen.ServerScreen;
import io.wispforest.owo.screen.ServerScreenType;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.literal;

public final class UwuScreenExample {
    public static final ServerScreenType<TestServerScreen, Void> TEST_SCREEN = ServerScreenType.create(new Identifier("uwu:test_screen"), Void.class, TestServerScreen::new);

    private UwuScreenExample() {

    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            dispatcher.register(literal("open_test_screen")
                .executes(ctx -> {
                    var player = ctx.getSource().getPlayer();

                    TestServerScreen scren = new TestServerScreen(ServerScreen.getNextScreenId(player), player, null);
                    scren.open();

                    return 1;
                }));
        });
    }
}
