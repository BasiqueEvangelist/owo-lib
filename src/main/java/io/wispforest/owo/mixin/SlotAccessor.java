package io.wispforest.owo.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Slot.class)
public interface SlotAccessor {
    @Invoker
    void invokeOnTake(int amount);
}
