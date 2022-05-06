package io.wispforest.owo.screen;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import java.util.*;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.screen.ScreenHandlerSyncHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ClickType;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.crash.CrashCallable;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

public abstract class ServerScreen<T extends ServerScreen<T, D>, D> {
    public final DefaultedList<Slot> slots = DefaultedList.of();
    private final List<ServerScreenProperty<?>> properties = new ArrayList<>();
    protected final PlayerEntity player;
    private ItemStack cursorStack = ItemStack.EMPTY;
    final DefaultedList<ItemStack> listenerTrackedStacks = DefaultedList.of();
    final DefaultedList<ItemStack> networkTrackedStacks = DefaultedList.of();
    private ItemStack previousCursorStack = ItemStack.EMPTY;
    private int revision;
    @Nullable
    private final ServerScreenType<T, D> type;
    public final int syncId;
    protected final D data;
    private int quickCraftButton = -1;
    private int quickCraftStage;
    private final Set<Slot> quickCraftSlots = Sets.<Slot>newHashSet();
    private final List<ScreenHandlerListener> listeners = Lists.<ScreenHandlerListener>newArrayList();
    @Nullable
    private ScreenHandlerSyncHandler syncHandler;
    private boolean disableSync;

    protected ServerScreen(ServerScreenType<T, D> type, int syncId, PlayerEntity player, D data) {
        this.type = type;
        this.syncId = syncId;
        this.data = data;
        this.player = player;
    }

    public ServerScreenType<T, D> getType() {
        return type;
    }

    public boolean isValid(int slot) {
        return slot == -1 || slot == -999 || slot < this.slots.size();
    }

    protected Slot addSlot(Slot slot) {
        slot.id = this.slots.size();
        this.slots.add(slot);
        this.networkTrackedStacks.add(ItemStack.EMPTY);
        return slot;
    }

    protected <P> ServerScreenProperty<P> addProperty(Class<P> klass, P defaultValue) {
        var property = new ServerScreenProperty<>(klass, defaultValue, properties.size());
        properties.add(property);
        return property;
    }

    public void addListener(ScreenHandlerListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
            this.sendContentUpdates();
        }
    }

    public void syncNetwork() {
        PacketByteBuf buf = PacketByteBufs.create();

        if (ItemStack.areItemsEqual(cursorStack, previousCursorStack)) {
            buf.writeBoolean(true);
            buf.writeItemStack(cursorStack);

            previousCursorStack = cursorStack;
        } else {
            buf.writeBoolean(false);
        }

        for (Slot slot : slots) {
            if (!ItemStack.areItemsEqual(slot.getStack(), this.networkTrackedStacks.get(slot.id))) {
                buf.writeVarInt(slot.id);
                buf.writeItemStack(slot.getStack());

                this.networkTrackedStacks.set(slot.id, slot.getStack());
            }
        }

        buf.writeVarInt(0);

        for (ServerScreenProperty<?> property : properties) {
            if (property.isDirty()) {
                buf.writeVarInt(property.getId());

                property.write(buf);
            }
        }

        buf.writeVarInt(0);

    }

    public DefaultedList<ItemStack> getStacks() {
        DefaultedList<ItemStack> defaultedList = DefaultedList.of();

        for(Slot slot : this.slots) {
            defaultedList.add(slot.getStack());
        }

        return defaultedList;
    }

    /**
     * Sends updates to listeners if any properties or slot stacks have changed.
     */
    public void sendContentUpdates() {
        for(int i = 0; i < this.slots.size(); ++i) {
            ItemStack itemStack = this.slots.get(i).getStack();
            Supplier<ItemStack> supplier = Suppliers.memoize(itemStack::copy);
            this.updateTrackedSlot(i, itemStack, supplier);
            this.checkSlotUpdates(i, itemStack, supplier);
        }

        this.checkCursorStackUpdates();

        for(int i = 0; i < this.properties.size(); ++i) {
            Property property = (Property)this.properties.get(i);
            int j = property.get();
            if (property.hasChanged()) {
                this.notifyPropertyUpdate(i, j);
            }

            this.checkPropertyUpdates(i, j);
        }

    }

    public void updateToClient() {
        for(int i = 0; i < this.slots.size(); ++i) {
            ItemStack itemStack = this.slots.get(i).getStack();
            this.updateTrackedSlot(i, itemStack, itemStack::copy);
        }

        for(int i = 0; i < this.properties.size(); ++i) {
            Property property = (Property)this.properties.get(i);
            if (property.hasChanged()) {
                this.notifyPropertyUpdate(i, property.get());
            }
        }

        this.syncNetwork();
    }

    private void notifyPropertyUpdate(int index, int value) {
        for(ScreenHandlerListener screenHandlerListener : this.listeners) {
            screenHandlerListener.onPropertyUpdate(this, index, value);
        }

    }

    private void updateTrackedSlot(int slot, ItemStack stack, Supplier<ItemStack> copySupplier) {
        ItemStack itemStack = this.listenerTrackedStacks.get(slot);
        if (!ItemStack.areEqual(itemStack, stack)) {
            ItemStack itemStack2 = copySupplier.get();
            this.trackedStacks.set(slot, itemStack2);

            for(ScreenHandlerListener screenHandlerListener : this.listeners) {
                screenHandlerListener.onSlotUpdate(this, slot, itemStack2);
            }
        }

    }

    private void checkSlotUpdates(int slot, ItemStack stack, Supplier<ItemStack> copySupplier) {
        if (!this.disableSync) {
            ItemStack itemStack = this.networkTrackedStacks.get(slot);
            if (!ItemStack.areEqual(itemStack, stack)) {
                ItemStack itemStack2 = copySupplier.get();
                this.networkTrackedStacks.set(slot, itemStack2);
                if (this.syncHandler != null) {
                    this.syncHandler.updateSlot(this, slot, itemStack2);
                }
            }

        }
    }

    private void checkPropertyUpdates(int id, int value) {
        if (!this.disableSync) {
            int i = this.trackedPropertyValues.getInt(id);
            if (i != value) {
                this.trackedPropertyValues.set(id, value);
                if (this.syncHandler != null) {
                    this.syncHandler.updateProperty(this, id, value);
                }
            }

        }
    }

    private void checkCursorStackUpdates() {
        if (!this.disableSync) {
            if (!ItemStack.areEqual(this.getCursorStack(), this.previousCursorStack)) {
                this.previousCursorStack = this.getCursorStack().copy();
                if (this.syncHandler != null) {
                    this.syncHandler.updateCursorStack(this, this.previousCursorStack);
                }
            }

        }
    }

    public void setPreviousTrackedSlot(int slot, ItemStack stack) {
        this.networkTrackedStacks.set(slot, stack.copy());
    }

    public void setPreviousTrackedSlotMutable(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.networkTrackedStacks.size()) {
            this.networkTrackedStacks.set(slot, stack);
        } else {
            LOGGER.debug("Incorrect slot index: {} available slots: {}", slot, this.networkTrackedStacks.size());
        }
    }

    public void setPreviousCursorStack(ItemStack stack) {
        this.previousCursorStack = stack.copy();
    }

    public boolean onButtonClick(PlayerEntity player, int id) {
        return false;
    }

    public Slot getSlot(int index) {
        return this.slots.get(index);
    }

    public ItemStack transferSlot(PlayerEntity player, int index) {
        return this.slots.get(index).getStack();
    }

    /**
     * Performs a slot click. This can behave in many different ways depending mainly on the action type.
     *
     * @param actionType the type of slot click, check the docs for each {@link SlotActionType} value for details
     */
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        try {
            this.internalOnSlotClick(slotIndex, button, actionType, player);
        } catch (Exception var8) {
            CrashReport crashReport = CrashReport.create(var8, "Container click");
            CrashReportSection crashReportSection = crashReport.addElement("Click info");
            crashReportSection.add("Menu Type", (CrashCallable<String>)(() -> this.type != null ? Registry.SCREEN_HANDLER.getId(this.type).toString() : "<no type>"));
            crashReportSection.add("Menu Class", (CrashCallable<String>)(() -> this.getClass().getCanonicalName()));
            crashReportSection.add("Slot Count", this.slots.size());
            crashReportSection.add("Slot", slotIndex);
            crashReportSection.add("Button", button);
            crashReportSection.add("Type", actionType);
            throw new CrashException(crashReport);
        }
    }



    /**
     * The actual logic that handles a slot click. Called by {@link #onSlotClick
     * (int, int, SlotActionType, PlayerEntity)} in a try-catch block that wraps
     * exceptions from this method into a crash report.
     */
    private void internalOnSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        PlayerInventory playerInventory = player.getInventory();
        if (actionType == SlotActionType.QUICK_CRAFT) {
            int i = this.quickCraftStage;
            this.quickCraftStage = unpackQuickCraftStage(button);
            if ((i != 1 || this.quickCraftStage != 2) && i != this.quickCraftStage) {
                this.endQuickCraft();
            } else if (this.getCursorStack().isEmpty()) {
                this.endQuickCraft();
            } else if (this.quickCraftStage == 0) {
                this.quickCraftButton = unpackQuickCraftButton(button);
                if (shouldQuickCraftContinue(this.quickCraftButton, player)) {
                    this.quickCraftStage = 1;
                    this.quickCraftSlots.clear();
                } else {
                    this.endQuickCraft();
                }
            } else if (this.quickCraftStage == 1) {
                Slot slot = this.slots.get(slotIndex);
                ItemStack itemStack = this.getCursorStack();
                if (canInsertItemIntoSlot(slot, itemStack, true)
                    && slot.canInsert(itemStack)
                    && (this.quickCraftButton == 2 || itemStack.getCount() > this.quickCraftSlots.size())
                    && this.canInsertIntoSlot(slot)) {
                    this.quickCraftSlots.add(slot);
                }
            } else if (this.quickCraftStage == 2) {
                if (!this.quickCraftSlots.isEmpty()) {
                    if (this.quickCraftSlots.size() == 1) {
                        int j = ((Slot)this.quickCraftSlots.iterator().next()).id;
                        this.endQuickCraft();
                        this.internalOnSlotClick(j, this.quickCraftButton, SlotActionType.PICKUP, player);
                        return;
                    }

                    ItemStack itemStack2 = this.getCursorStack().copy();
                    int k = this.getCursorStack().getCount();

                    for(Slot slot2 : this.quickCraftSlots) {
                        ItemStack itemStack3 = this.getCursorStack();
                        if (slot2 != null
                            && canInsertItemIntoSlot(slot2, itemStack3, true)
                            && slot2.canInsert(itemStack3)
                            && (this.quickCraftButton == 2 || itemStack3.getCount() >= this.quickCraftSlots.size())
                            && this.canInsertIntoSlot(slot2)) {
                            ItemStack itemStack4 = itemStack2.copy();
                            int l = slot2.hasStack() ? slot2.getStack().getCount() : 0;
                            calculateStackSize(this.quickCraftSlots, this.quickCraftButton, itemStack4, l);
                            int m = Math.min(itemStack4.getMaxCount(), slot2.getMaxItemCount(itemStack4));
                            if (itemStack4.getCount() > m) {
                                itemStack4.setCount(m);
                            }

                            k -= itemStack4.getCount() - l;
                            slot2.setStack(itemStack4);
                        }
                    }

                    itemStack2.setCount(k);
                    this.setCursorStack(itemStack2);
                }

                this.endQuickCraft();
            } else {
                this.endQuickCraft();
            }
        } else if (this.quickCraftStage != 0) {
            this.endQuickCraft();
        } else if ((actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE) && (button == 0 || button == 1)) {
            ClickType clickType = button == 0 ? ClickType.LEFT : ClickType.RIGHT;
            if (slotIndex == EMPTY_SPACE_SLOT_INDEX) {
                if (!this.getCursorStack().isEmpty()) {
                    if (clickType == ClickType.LEFT) {
                        player.dropItem(this.getCursorStack(), true);
                        this.setCursorStack(ItemStack.EMPTY);
                    } else {
                        player.dropItem(this.getCursorStack().split(1), true);
                    }
                }
            } else if (actionType == SlotActionType.QUICK_MOVE) {
                if (slotIndex < 0) {
                    return;
                }

                Slot slot = this.slots.get(slotIndex);
                if (!slot.canTakeItems(player)) {
                    return;
                }

                ItemStack itemStack = this.transferSlot(player, slotIndex);

                while(!itemStack.isEmpty() && ItemStack.areItemsEqualIgnoreDamage(slot.getStack(), itemStack)) {
                    itemStack = this.transferSlot(player, slotIndex);
                }
            } else {
                if (slotIndex < 0) {
                    return;
                }

                Slot slot = this.slots.get(slotIndex);
                ItemStack itemStack = slot.getStack();
                ItemStack itemStack5 = this.getCursorStack();
                player.onPickupSlotClick(itemStack5, slot.getStack(), clickType);
                if (!itemStack5.onStackClicked(slot, clickType, player) && !itemStack.onClicked(itemStack5, slot, clickType, player, this.getCursorStackReference())) {
                    if (itemStack.isEmpty()) {
                        if (!itemStack5.isEmpty()) {
                            int n = clickType == ClickType.LEFT ? itemStack5.getCount() : 1;
                            this.setCursorStack(slot.insertStack(itemStack5, n));
                        }
                    } else if (slot.canTakeItems(player)) {
                        if (itemStack5.isEmpty()) {
                            int n = clickType == ClickType.LEFT ? itemStack.getCount() : (itemStack.getCount() + 1) / 2;
                            Optional<ItemStack> optional = slot.tryTakeStackRange(n, Integer.MAX_VALUE, player);
                            optional.ifPresent(stack -> {
                                this.setCursorStack(stack);
                                slot.onTakeItem(player, stack);
                            });
                        } else if (slot.canInsert(itemStack5)) {
                            if (ItemStack.canCombine(itemStack, itemStack5)) {
                                int n = clickType == ClickType.LEFT ? itemStack5.getCount() : 1;
                                this.setCursorStack(slot.insertStack(itemStack5, n));
                            } else if (itemStack5.getCount() <= slot.getMaxItemCount(itemStack5)) {
                                slot.setStack(itemStack5);
                                this.setCursorStack(itemStack);
                            }
                        } else if (ItemStack.canCombine(itemStack, itemStack5)) {
                            Optional<ItemStack> optional2 = slot.tryTakeStackRange(itemStack.getCount(), itemStack5.getMaxCount() - itemStack5.getCount(), player);
                            optional2.ifPresent(stack -> {
                                itemStack5.increment(stack.getCount());
                                slot.onTakeItem(player, stack);
                            });
                        }
                    }
                }

                slot.markDirty();
            }
        } else if (actionType == SlotActionType.SWAP) {
            Slot slot3 = this.slots.get(slotIndex);
            ItemStack itemStack2 = playerInventory.getStack(button);
            ItemStack itemStack = slot3.getStack();
            if (!itemStack2.isEmpty() || !itemStack.isEmpty()) {
                if (itemStack2.isEmpty()) {
                    if (slot3.canTakeItems(player)) {
                        playerInventory.setStack(button, itemStack);
                        slot3.onTake(itemStack.getCount());
                        slot3.setStack(ItemStack.EMPTY);
                        slot3.onTakeItem(player, itemStack);
                    }
                } else if (itemStack.isEmpty()) {
                    if (slot3.canInsert(itemStack2)) {
                        int o = slot3.getMaxItemCount(itemStack2);
                        if (itemStack2.getCount() > o) {
                            slot3.setStack(itemStack2.split(o));
                        } else {
                            playerInventory.setStack(button, ItemStack.EMPTY);
                            slot3.setStack(itemStack2);
                        }
                    }
                } else if (slot3.canTakeItems(player) && slot3.canInsert(itemStack2)) {
                    int o = slot3.getMaxItemCount(itemStack2);
                    if (itemStack2.getCount() > o) {
                        slot3.setStack(itemStack2.split(o));
                        slot3.onTakeItem(player, itemStack);
                        if (!playerInventory.insertStack(itemStack)) {
                            player.dropItem(itemStack, true);
                        }
                    } else {
                        playerInventory.setStack(button, itemStack);
                        slot3.setStack(itemStack2);
                        slot3.onTakeItem(player, itemStack);
                    }
                }
            }
        } else if (actionType == SlotActionType.CLONE && player.getAbilities().creativeMode && this.getCursorStack().isEmpty() && slotIndex >= 0) {
            Slot slot3 = this.slots.get(slotIndex);
            if (slot3.hasStack()) {
                ItemStack itemStack2 = slot3.getStack().copy();
                itemStack2.setCount(itemStack2.getMaxCount());
                this.setCursorStack(itemStack2);
            }
        } else if (actionType == SlotActionType.THROW && this.getCursorStack().isEmpty() && slotIndex >= 0) {
            Slot slot3 = this.slots.get(slotIndex);
            int j = button == 0 ? 1 : slot3.getStack().getCount();
            ItemStack itemStack = slot3.takeStackRange(j, Integer.MAX_VALUE, player);
            player.dropItem(itemStack, true);
        } else if (actionType == SlotActionType.PICKUP_ALL && slotIndex >= 0) {

        }

    }

    private void doPickupAll(int slotIndex, int button) {
        Slot targetSlot = this.slots.get(slotIndex);
        ItemStack targetStack = this.getCursorStack();
        if (!targetStack.isEmpty() && (!targetSlot.hasStack() || !targetSlot.canTakeItems(player))) {
            int start = button == 0 ? 0 : this.slots.size() - 1;
            int step = button == 0 ? 1 : -1;

            for (int tryId = 0; tryId < 2; ++tryId) {
                for (int i = start; i >= 0 && i < this.slots.size() && targetStack.getCount() < targetStack.getMaxCount(); i += step) {
                    Slot otherSlot = this.slots.get(i);

                    if (otherSlot.hasStack()
                     && canInsertItemIntoSlot(otherSlot, targetStack, true)
                     && otherSlot.canTakeItems(player)
                     && this.canInsertIntoSlot(targetStack, otherSlot)) {
                        ItemStack otherStack = otherSlot.getStack();

                        if (tryId != 0 || otherStack.getCount() != otherStack.getMaxCount()) {
                            ItemStack takenStack = otherSlot.takeStackRange(otherStack.getCount(), targetStack.getMaxCount() - targetStack.getCount(), player);
                            targetStack.increment(takenStack.getCount());
                        }
                    }
                }
            }
        }
    }

    private StackReference getCursorStackReference() {
        return new StackReference() {
            @Override
            public ItemStack get() {
                return getCursorStack();
            }

            @Override
            public boolean set(ItemStack stack) {
                setCursorStack(stack);
                return true;
            }
        };
    }

    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return true;
    }

    public void close(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity) {
            ItemStack itemStack = this.getCursorStack();
            if (!itemStack.isEmpty()) {
                if (player.isAlive() && !((ServerPlayerEntity)player).isDisconnected()) {
                    player.getInventory().offerOrDrop(itemStack);
                } else {
                    player.dropItem(itemStack, false);
                }

                this.setCursorStack(ItemStack.EMPTY);
            }
        }

    }

    protected void dropInventory(PlayerEntity player, Inventory inventory) {
        if (!player.isAlive() || player instanceof ServerPlayerEntity && ((ServerPlayerEntity)player).isDisconnected()) {
            for(int i = 0; i < inventory.size(); ++i) {
                player.dropItem(inventory.removeStack(i), false);
            }

        } else {
            for(int i = 0; i < inventory.size(); ++i) {
                PlayerInventory playerInventory = player.getInventory();
                if (playerInventory.player instanceof ServerPlayerEntity) {
                    playerInventory.offerOrDrop(inventory.removeStack(i));
                }
            }

        }
    }

    public void onContentChanged(Inventory inventory) {
        this.sendContentUpdates();
    }

    public void setStackInSlot(int slot, int revision, ItemStack stack) {
        this.getSlot(slot).setStack(stack);
        this.revision = revision;
    }

    public void updateSlotStacks(int revision, List<ItemStack> stacks, ItemStack cursorStack) {
        for(int i = 0; i < stacks.size(); ++i) {
            this.getSlot(i).setStack((ItemStack)stacks.get(i));
        }

        this.cursorStack = cursorStack;
        this.revision = revision;
    }

    public abstract boolean canUse();

    protected boolean insertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast) {
        boolean bl = false;
        int i = startIndex;
        if (fromLast) {
            i = endIndex - 1;
        }

        if (stack.isStackable()) {
            while(!stack.isEmpty()) {
                if (fromLast) {
                    if (i < startIndex) {
                        break;
                    }
                } else if (i >= endIndex) {
                    break;
                }

                Slot slot = this.slots.get(i);
                ItemStack itemStack = slot.getStack();
                if (!itemStack.isEmpty() && ItemStack.canCombine(stack, itemStack)) {
                    int j = itemStack.getCount() + stack.getCount();
                    if (j <= stack.getMaxCount()) {
                        stack.setCount(0);
                        itemStack.setCount(j);
                        slot.markDirty();
                        bl = true;
                    } else if (itemStack.getCount() < stack.getMaxCount()) {
                        stack.decrement(stack.getMaxCount() - itemStack.getCount());
                        itemStack.setCount(stack.getMaxCount());
                        slot.markDirty();
                        bl = true;
                    }
                }

                if (fromLast) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        if (!stack.isEmpty()) {
            if (fromLast) {
                i = endIndex - 1;
            } else {
                i = startIndex;
            }

            while(true) {
                if (fromLast) {
                    if (i < startIndex) {
                        break;
                    }
                } else if (i >= endIndex) {
                    break;
                }

                Slot slot = this.slots.get(i);
                ItemStack itemStack = slot.getStack();
                if (itemStack.isEmpty() && slot.canInsert(stack)) {
                    if (stack.getCount() > slot.getMaxItemCount()) {
                        slot.setStack(stack.split(slot.getMaxItemCount()));
                    } else {
                        slot.setStack(stack.split(stack.getCount()));
                    }

                    slot.markDirty();
                    bl = true;
                    break;
                }

                if (fromLast) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        return bl;
    }

    public static int unpackQuickCraftButton(int quickCraftData) {
        return quickCraftData >> 2 & 3;
    }

    public static int unpackQuickCraftStage(int quickCraftData) {
        return quickCraftData & 3;
    }

    public static int packQuickCraftData(int quickCraftStage, int buttonId) {
        return quickCraftStage & 3 | (buttonId & 3) << 2;
    }

    public static boolean shouldQuickCraftContinue(int stage, PlayerEntity player) {
        if (stage == 0) {
            return true;
        } else if (stage == 1) {
            return true;
        } else {
            return stage == 2 && player.getAbilities().creativeMode;
        }
    }

    protected void endQuickCraft() {
        this.quickCraftStage = 0;
        this.quickCraftSlots.clear();
    }

    public static boolean canInsertItemIntoSlot(@Nullable Slot slot, ItemStack stack, boolean allowOverflow) {
        boolean bl = slot == null || !slot.hasStack();
        if (!bl && ItemStack.canCombine(stack, slot.getStack())) {
            return slot.getStack().getCount() + (allowOverflow ? 0 : stack.getCount()) <= stack.getMaxCount();
        } else {
            return bl;
        }
    }

    public static void calculateStackSize(Set<Slot> slots, int mode, ItemStack stack, int stackSize) {
        switch(mode) {
            case 0:
                stack.setCount(MathHelper.floor((float)stack.getCount() / (float)slots.size()));
                break;
            case 1:
                stack.setCount(1);
                break;
            case 2:
                stack.setCount(stack.getItem().getMaxCount());
        }

        stack.increment(stackSize);
    }

    public boolean canInsertIntoSlot(Slot slot) {
        return true;
    }

    public void setCursorStack(ItemStack stack) {
        this.cursorStack = stack;
    }

    public ItemStack getCursorStack() {
        return this.cursorStack;
    }

    public void disableSyncing() {
        this.disableSync = true;
    }

    public void enableSyncing() {
        this.disableSync = false;
    }

    public OptionalInt getSlotIndex(Inventory inventory, int index) {
        for(int i = 0; i < this.slots.size(); ++i) {
            Slot slot = this.slots.get(i);
            if (slot.inventory == inventory && index == slot.getIndex()) {
                return OptionalInt.of(i);
            }
        }

        return OptionalInt.empty();
    }

    public int getRevision() {
        return this.revision;
    }

    public int nextRevision() {
        this.revision = this.revision + 1 & 32767;
        return this.revision;
    }
}
