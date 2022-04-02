package io.wispforest.owo.screen;

import io.wispforest.owo.access.ExtendedPlayerEntity;
import io.wispforest.owo.mixin.SlotAccessor;
import io.wispforest.owo.util.MouseButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;
import java.util.function.Consumer;

import static net.minecraft.screen.ScreenHandler.canInsertItemIntoSlot;

public abstract class ServerScreen<T> {
    protected final ServerScreenType<T> type;
    protected final int screenId;
    private final T data;
    protected PlayerEntity player;
    protected ItemStack cursorStack = ItemStack.EMPTY;
    protected ItemStack prevCursorStack = ItemStack.EMPTY;
    protected final DefaultedList<Slot> slots = DefaultedList.of();
    protected final List<ItemStack> prevItemStacks = new ArrayList<>();
    protected final List<ServerScreenAction<?>> actions = new ArrayList<>();
    private final List<ServerScreenProperty<?>> properties = new ArrayList<>();
    private int quickCraftStage = 0;
    private final Set<Slot> quickCraftSlots = new HashSet<>();
    private MouseButton quickCraftButton = null;

    protected ServerScreen(ServerScreenType<T> type, int screenId, T data) {
        this.type = type;
        this.screenId = screenId;
        this.data = data;
    }

    public ServerScreenType<T> getType() {
        return type;
    }

    public void openFor(PlayerEntity player) {
        if (!player.world.isClient)
            ((ServerPlayerEntity) player).networkHandler.sendPacket(type.createOpenPacket(data, screenId));

        ((ExtendedPlayerEntity) player).setCurrentServerScreen(this);
        player.currentScreenHandler = new FakeScreenHandler();
        this.player = player;
    }

    protected <P> ServerScreenProperty<P> addProperty(Class<P> klass, P defaultValue) {
        ServerScreenProperty<P> prop = new ServerScreenProperty<>(klass, defaultValue);
        properties.add(prop);
        return prop;
    }

    protected Slot addSlot(Slot slot) {
        slots.add(slot);
        prevItemStacks.add(ItemStack.EMPTY);

        return slot;
    }

    protected <D> ServerScreenAction<D> addClientAction(Class<D> klass, Consumer<D> executor) {
        ServerScreenAction<D> action = new ServerScreenAction<>(actions.size(), EnvType.CLIENT, klass, executor, this);
        actions.add(action);
        return action;
    }

    protected <D> ServerScreenAction<D> addServerAction(Class<D> klass, Consumer<D> executor) {
        ServerScreenAction<D> action = new ServerScreenAction<>(actions.size(), EnvType.SERVER, klass, executor, this);
        actions.add(action);
        return action;
    }

    public ItemStack quickTransferFrom(PlayerEntity player, int index) {
        return this.slots.get(index).getStack();
    }

    public ItemStack getCursorStack() {
        return cursorStack;
    }

    public StackReference getCursorStackReference() {
        return new StackReference() {
            @Override
            public ItemStack get() {
                return cursorStack;
            }

            @Override
            public boolean set(ItemStack stack) {
                cursorStack = stack;
                return true;
            }
        };
    }

    private void internalOnSlotClick(int slotIndex, int rawButton, SlotActionType actionType, PlayerEntity player) {
        MouseButton button = MouseButton.fromInt(rawButton);

        if (this.quickCraftStage != 0 && actionType != SlotActionType.QUICK_CRAFT) {
            this.endQuickCraft();
        }

        switch (actionType) {
            case PICKUP -> pickupAction(slotIndex, player, button);
            case SWAP -> swapSlots(slotIndex, rawButton, player);
            case CLONE -> cloneAction(slotIndex, player);
            case THROW -> dropAction(slotIndex, player, button);
            case QUICK_CRAFT -> quickCraftAction(slotIndex, ScreenHandler.unpackQuickCraftStage(rawButton), MouseButton.fromInt(ScreenHandler.unpackQuickCraftButton(rawButton)));
            case PICKUP_ALL -> pickupAllAction(slotIndex, player, button);
            case QUICK_MOVE -> quickMoveAction(slotIndex, player);
        }

    }

    protected boolean insertItem(ItemStack sourceStack, int startIndex, int endIndex, boolean fromLast) {
        boolean changed = false;
        int i = startIndex;
        if (fromLast) {
            i = endIndex - 1;
        }

        if (sourceStack.isStackable()) {
            while(!sourceStack.isEmpty()) {
                if (fromLast ? i >= startIndex : i < endIndex) {
                    Slot slot = this.slots.get(i);
                    ItemStack slotStack = slot.getStack();

                    if (!slotStack.isEmpty() && ItemStack.canCombine(sourceStack, slotStack)) {
                        int j = slotStack.getCount() + sourceStack.getCount();
                        if (j <= sourceStack.getMaxCount()) {
                            sourceStack.setCount(0);
                            slotStack.setCount(j);
                            slot.markDirty();
                            changed = true;
                        } else if (slotStack.getCount() < sourceStack.getMaxCount()) {
                            sourceStack.decrement(sourceStack.getMaxCount() - slotStack.getCount());
                            slotStack.setCount(sourceStack.getMaxCount());
                            slot.markDirty();
                            changed = true;
                        }
                    }

                    if (fromLast) {
                        --i;
                    } else {
                        ++i;
                    }
                }
            }

            return changed;
        }

        if (!sourceStack.isEmpty()) {
            if (fromLast) {
                i = endIndex - 1;
            } else {
                i = startIndex;
            }

            while(true) {
                if (fromLast ? i >= startIndex : i < endIndex) {
                    Slot slot = this.slots.get(i);
                    ItemStack itemStack = slot.getStack();
                    if (itemStack.isEmpty() && slot.canInsert(sourceStack)) {
                        if (sourceStack.getCount() > slot.getMaxItemCount()) {
                            slot.setStack(sourceStack.split(slot.getMaxItemCount()));
                        } else {
                            slot.setStack(sourceStack.split(sourceStack.getCount()));
                        }

                        slot.markDirty();
                        changed = true;
                        break;
                    }

                    if (fromLast) {
                        --i;
                    } else {
                        ++i;
                    }
                }
            }
        }

        return changed;
    }

    private void quickMoveAction(int slotIndex, PlayerEntity player) {
        if (slotIndex < 0) return;

        Slot slot = slots.get(slotIndex);

        if (!slot.canTakeItems(player)) return;

        ItemStack stack;

        do {
            stack = this.quickTransferFrom(player, slotIndex);
        } while (!stack.isEmpty() && ItemStack.areItemsEqualIgnoreDamage(slot.getStack(), stack));
    }

    private void pickupAllAction(int slotIndex, PlayerEntity player, MouseButton button) {
        if (slotIndex < 0) return;
        Slot slotStack = this.slots.get(slotIndex);
        if (cursorStack.isEmpty() || (slotStack.hasStack() && !slotStack.canTakeItems(player))) return;

        int startSlot = button == MouseButton.LEFT ? 0 : this.slots.size() - 1;
        int step = button == MouseButton.LEFT ? 1 : -1;

        for (int tryNum = 0; tryNum < 2; ++tryNum) {
            for (int p = startSlot; p >= 0 && p < this.slots.size() && cursorStack.getCount() < cursorStack.getMaxCount(); p += step) {
                Slot otherSlot = this.slots.get(p);

                if (!otherSlot.hasStack() || !canInsertItemIntoSlot(otherSlot, cursorStack, true) || !otherSlot.canTakeItems(player) || !this.canInsertIntoSlot(cursorStack, otherSlot))
                    continue;

                ItemStack otherSlotStack = otherSlot.getStack();

                if (tryNum == 0 && otherSlotStack.getCount() == otherSlotStack.getMaxCount())
                    continue;

                ItemStack takenStack = otherSlot.takeStackRange(otherSlotStack.getCount(), cursorStack.getMaxCount() - cursorStack.getCount(), player);
                cursorStack.increment(takenStack.getCount());
            }
        }
    }

    private void dropAction(int slotIndex, PlayerEntity player, MouseButton button) {
        if (!this.getCursorStack().isEmpty() || slotIndex < 0) return;
        Slot stack = this.slots.get(slotIndex);
        int j = button == MouseButton.LEFT ? 1 : stack.getStack().getCount();
        ItemStack droppedStack = stack.takeStackRange(j, Integer.MAX_VALUE, player);
        player.dropItem(droppedStack, true);
    }

    private void cloneAction(int slotIndex, PlayerEntity player) {
        if (!player.getAbilities().creativeMode || !this.getCursorStack().isEmpty() || slotIndex < 0) return;

        Slot sourceStack = this.slots.get(slotIndex);
        if (sourceStack.hasStack()) {
            ItemStack newStack = sourceStack.getStack().copy();
            newStack.setCount(newStack.getMaxCount());
            cursorStack = newStack;
        }
    }

    private void pickupAction(int slotIndex, PlayerEntity player, MouseButton button) {
        if (slotIndex == -1) { // Drop the item.
            if (!cursorStack.isEmpty()) {
                if (button == MouseButton.LEFT) {
                    player.dropItem(this.getCursorStack(), true);
                    cursorStack = ItemStack.EMPTY;
                } else {
                    player.dropItem(this.getCursorStack().split(1), true);
                }
            }
            return;
        }

        if (slotIndex < 0) return;

        Slot slot = this.slots.get(slotIndex);
        ItemStack slotStack = slot.getStack();
        player.onPickupSlotClick(cursorStack, slot.getStack(), button.toClickType());

        slot.markDirty();

        if (cursorStack.onStackClicked(slot, button.toClickType(), player) || slotStack.onClicked(cursorStack, slot, button.toClickType(), player, this.getCursorStackReference()))
            return;

        if (slotStack.isEmpty()) {
            if (!cursorStack.isEmpty())
                cursorStack = slot.insertStack(cursorStack, button == MouseButton.LEFT ? cursorStack.getCount() : 1);

            return;
        }

        if (!slot.canTakeItems(player)) return;

        if (cursorStack.isEmpty()) {
            slot
                .tryTakeStackRange(
                    button == MouseButton.LEFT ? slotStack.getCount() : (slotStack.getCount() + 1) / 2,
                    Integer.MAX_VALUE,
                    player
                ).ifPresent(stack -> {
                    cursorStack = stack;
                    slot.onTakeItem(player, stack);
                });
        } else if (slot.canInsert(cursorStack)) {
            if (ItemStack.canCombine(slotStack, cursorStack)) {
                int n = button == MouseButton.LEFT ? cursorStack.getCount() : 1;
                cursorStack = slot.insertStack(cursorStack, n);
            } else if (cursorStack.getCount() <= slot.getMaxItemCount(cursorStack)) {
                slot.setStack(cursorStack);
                cursorStack = slotStack;
            }
        } else if (ItemStack.canCombine(slotStack, cursorStack)) {
            slot.tryTakeStackRange(slotStack.getCount(), cursorStack.getMaxCount() - cursorStack.getCount(), player)
                .ifPresent(stack -> {
                    cursorStack.increment(stack.getCount());
                    slot.onTakeItem(player, stack);
                });
        }
    }


    public void quickCraftAction(int slotIndex, int stage, MouseButton button) {
        int prevStage = this.quickCraftStage;
        this.quickCraftStage = stage;

        if ((prevStage != 1 || stage != 2) && prevStage != stage) {
            this.endQuickCraft();
            return;
        }

        if (this.getCursorStack().isEmpty()) {
            this.endQuickCraft();
            return;
        }

        switch (this.quickCraftStage) {
            case 0 -> {
                this.quickCraftButton = button;
                if (button == MouseButton.MIDDLE && !player.getAbilities().creativeMode) {
                    this.endQuickCraft();
                    return;
                }
                this.quickCraftStage = 1;
                this.quickCraftSlots.clear();
            }
            case 1 -> {
                Slot slot = this.slots.get(slotIndex);
                ItemStack itemStack = this.getCursorStack();
                if (canInsertItemIntoSlot(slot, itemStack, true)
                    && slot.canInsert(itemStack)
                    && (this.quickCraftButton == MouseButton.MIDDLE || itemStack.getCount() > this.quickCraftSlots.size())
                    && this.canInsertIntoSlot(slot)) {
                    this.quickCraftSlots.add(slot);
                }
            }
            case 2 -> {
                if (!this.quickCraftSlots.isEmpty()) {
                    if (this.quickCraftSlots.size() == 1) {
                        int j = this.quickCraftSlots.iterator().next().id;
                        this.endQuickCraft();
                        this.pickupAction(j, player, quickCraftButton);
                        return;
                    }

                    ItemStack cursorStackCopy = this.getCursorStack().copy();
                    int cursorStackCount = this.getCursorStack().getCount();

                    for (Slot otherSlot : this.quickCraftSlots) {
                        if (otherSlot != null
                            && canInsertItemIntoSlot(otherSlot, cursorStack, true)
                            && otherSlot.canInsert(cursorStack)
                            && (this.quickCraftButton == MouseButton.MIDDLE || cursorStack.getCount() >= this.quickCraftSlots.size())
                            && this.canInsertIntoSlot(otherSlot)) {
                            ItemStack newStack = cursorStackCopy.copy();
                            int oldSize = otherSlot.hasStack() ? otherSlot.getStack().getCount() : 0;
                            ScreenHandler.calculateStackSize(this.quickCraftSlots, this.quickCraftButton.ordinal(), newStack, oldSize);
                            int maxCount = Math.min(newStack.getMaxCount(), otherSlot.getMaxItemCount(newStack));
                            if (newStack.getCount() > maxCount) {
                                newStack.setCount(maxCount);
                            }

                            cursorStackCount -= newStack.getCount() - oldSize;
                            otherSlot.setStack(newStack);
                        }
                    }

                    cursorStackCopy.setCount(cursorStackCount);
                    cursorStack = cursorStackCopy;
                }
                this.endQuickCraft();
            }
            default -> this.endQuickCraft();
        }
    }

    public void endQuickCraft() {
        this.quickCraftStage = 0;
        this.quickCraftSlots.clear();
    }

    public void swapSlots(int slotIndexA, int slotIndexB, PlayerEntity player) {
        PlayerInventory playerInventory = player.getInventory();
        Slot slotA = this.slots.get(slotIndexA);
        ItemStack stackB = playerInventory.getStack(slotIndexB);
        ItemStack stackA = slotA.getStack();
        if (stackB.isEmpty() && stackA.isEmpty()) return;

        if (stackB.isEmpty()) {
            if (slotA.canTakeItems(player)) {
                playerInventory.setStack(slotIndexB, stackA);
                ((SlotAccessor) slotA).invokeOnTake(stackA.getCount());
                slotA.setStack(ItemStack.EMPTY);
                slotA.onTakeItem(player, stackA);
            }
        } else if (stackA.isEmpty()) {
            if (slotA.canInsert(stackB)) {
                int maxCount = slotA.getMaxItemCount(stackB);

                if (stackB.getCount() > maxCount) {
                    slotA.setStack(stackB.split(maxCount));
                } else {
                    playerInventory.setStack(slotIndexB, ItemStack.EMPTY);
                    slotA.setStack(stackB);
                }
            }
        } else if (slotA.canTakeItems(player) && slotA.canInsert(stackB)) {
            int o = slotA.getMaxItemCount(stackB);
            if (stackB.getCount() > o) {
                slotA.setStack(stackB.split(o));
                slotA.onTakeItem(player, stackA);
                if (!playerInventory.insertStack(stackA)) {
                    player.dropItem(stackA, true);
                }
            } else {
                playerInventory.setStack(slotIndexB, stackA);
                slotA.setStack(stackB);
                slotA.onTakeItem(player, stackA);
            }
        }
    }

    public boolean canInsertIntoSlot(Slot slot) {
        return true;
    }

    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return true;
    }

    public void sync() {
        int dirtyPropertyCount = 0;
        int dirtySlotsCount = 0;

        for (var prop : properties) {
            if (prop.isDirty())
                dirtyPropertyCount++;
        }

        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);

            if (!prevItemStacks.get(i).equals(slot.getStack()))
                dirtySlotsCount++;
        }

        if (dirtyPropertyCount == 0 && dirtySlotsCount == 0 && prevCursorStack.equals(cursorStack)) return;

        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeVarInt(screenId);

        if (!prevCursorStack.equals(cursorStack)) {
            buf.writeBoolean(true);

            buf.writeItemStack(cursorStack);

            prevCursorStack = cursorStack;
        } else {
            buf.writeBoolean(false);
        }

        buf.writeVarInt(dirtyPropertyCount);

        for (int i = 0; i < properties.size(); i++) {
            var prop = properties.get(i);

            if (!prop.isDirty()) continue;

            buf.writeVarInt(i);
            prop.write(buf);
        }

        buf.writeVarInt(dirtySlotsCount);

        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);

            if (slot.getStack().equals(prevItemStacks.get(i))) continue;

            prevItemStacks.set(i, slot.getStack().copy());
            buf.writeVarInt(i);
            buf.writeItemStack(slot.getStack());
        }

        ServerPlayNetworking.send((ServerPlayerEntity) player, ServerScreenInternals.SYNC_DATA_ID, buf);
    }

    void readSync(PacketByteBuf buf) {
        boolean cursorStackChanged = buf.readBoolean();

        if (cursorStackChanged) {
            prevCursorStack = cursorStack = buf.readItemStack();
        }

        int dirtyPropertyCount = buf.readVarInt();

        for (int i = 0; i < dirtyPropertyCount; i++) {
            int propertyId = buf.readVarInt();
            ServerScreenProperty<?> property = properties.get(propertyId);

            property.read(buf);
        }

        int dirtySlotCount = buf.readVarInt();

        for (int i = 0; i < dirtySlotCount; i++) {
            int slotId = buf.readVarInt();
            Slot slot = slots.get(slotId);

            slot.setStack(buf.readItemStack());
        }
    }
}
