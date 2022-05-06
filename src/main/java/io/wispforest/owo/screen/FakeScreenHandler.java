package io.wispforest.owo.screen;

import io.wispforest.owo.access.ExtendedPlayerEntity;
import io.wispforest.owo.mixin.DefaultedListAccessor;
import io.wispforest.owo.mixin.ScreenHandlerInvoker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public class FakeScreenHandler extends ScreenHandler {
    private final ServerScreen<?, ?> screen;

    FakeScreenHandler(ServerScreen<?, ?> screen) {
        super(null, -1);
        this.screen = screen;

        ((ScreenHandlerInvoker) this).setSlots(screen.slots);
        ((ScreenHandlerInvoker) this).setTrackedStacks(DefaultedListAccessor.create(screen.listenerTrackedStacks, ItemStack.EMPTY));
        ((ScreenHandlerInvoker) this).setPreviousTrackedStacks(DefaultedListAccessor.create(screen.networkTrackedStacks, ItemStack.EMPTY));
    }

    @Override
    public void sendContentUpdates() {
        screen.syncNetwork();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return screen.canUse();
    }

    @Override
    public void close(PlayerEntity player) {
        ((ExtendedPlayerEntity) player).owo$setCurrentServerScreen(null);
    }

    public ServerScreen<?, ?> getScreen() {
        return screen;
    }
}
