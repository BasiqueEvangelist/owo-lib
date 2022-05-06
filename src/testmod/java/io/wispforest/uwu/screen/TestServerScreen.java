package io.wispforest.uwu.screen;

import io.wispforest.owo.client.screens.ScreenUtils;
import io.wispforest.owo.screen.ServerScreen;
import io.wispforest.owo.util.ImplementedInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;

public class TestServerScreen extends ServerScreen<TestServerScreen, Void> {
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private final ImplementedInventory inventory = () -> stacks;

    protected TestServerScreen(int syncId, PlayerEntity player, Void data) {
        super(UwuScreenExample.TEST_SCREEN, syncId, player, data);

        ScreenUtils.generatePlayerSlots(100, 100, player.getInventory(), this::addSlot);

        addSlot(new Slot(inventory, 0, 120, 120));
    }

    @Override
    public boolean canUse() {
        return true;
    }
}
