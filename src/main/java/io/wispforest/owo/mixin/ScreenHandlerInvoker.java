package io.wispforest.owo.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ScreenHandler.class)
public interface ScreenHandlerInvoker {

    @Invoker("insertItem")
    boolean owo$insertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast);

    @Accessor
    @Mutable
    void setSlots(DefaultedList<Slot> slots);

    @Accessor
    @Mutable
    void setTrackedStacks(DefaultedList<ItemStack> slots);

    @Accessor
    @Mutable
    void setPreviousTrackedStacks(DefaultedList<ItemStack> slots);
}
