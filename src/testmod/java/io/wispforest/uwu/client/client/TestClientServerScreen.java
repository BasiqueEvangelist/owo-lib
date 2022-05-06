package io.wispforest.uwu.client.client;

import io.wispforest.owo.client.screens.ClientServerScreen;
import io.wispforest.uwu.screen.TestServerScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class TestClientServerScreen extends ClientServerScreen<TestServerScreen> {
    public TestClientServerScreen(TestServerScreen repr) {
        super(repr, new LiteralText("test title"));
    }

    @Override
    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {

    }
}
