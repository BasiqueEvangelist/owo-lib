package io.wispforest.owo.client.screens;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import io.wispforest.owo.screen.QuickCraftStage;
import io.wispforest.owo.screen.ServerScreen;
import io.wispforest.owo.screen.action.*;
import io.wispforest.owo.util.MouseButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public abstract class ClientServerScreen<S extends ServerScreen<S, ?>> extends Screen {
    public static final Identifier BACKGROUND_TEXTURE = new Identifier("textures/gui/container/inventory.png");
    private static final float field_32318 = 100.0F;
    private static final int field_32319 = 500;
    public static final int field_32322 = 100;
    private static final int field_32321 = 200;
    protected int backgroundWidth = 176;
    protected int backgroundHeight = 166;
    protected int titleX;
    protected int titleY;
    protected int playerInventoryTitleX;
    protected int playerInventoryTitleY;
    protected final S serverRepr;
    protected final Text playerInventoryTitle;
    @Nullable
    protected Slot focusedSlot;
    @Nullable
    private Slot touchDragSlotStart;
    @Nullable
    private Slot touchDropOriginSlot;
    @Nullable
    private Slot touchHoveredSlot;
    @Nullable
    private Slot lastClickedSlot;
    protected int x;
    protected int y;
    private boolean touchIsRightClickDrag;
    private ItemStack touchDragStack = ItemStack.EMPTY;
    private int touchDropX;
    private int touchDropY;
    private long touchDropTime;
    private ItemStack touchDropReturningStack = ItemStack.EMPTY;
    private long touchDropTimer;
    protected final Set<Slot> cursorDragSlots = Sets.<Slot>newHashSet();
    protected boolean cursorDragging;
    private int heldButtonType;
    private int heldButtonCode;
    private boolean cancelNextRelease;
    private int draggedStackRemainder;
    private long lastButtonClickTime;
    private int lastClickedButton;
    private boolean doubleClicking;
    private ItemStack quickMovingStack = ItemStack.EMPTY;

    public ClientServerScreen(S repr, Text title) {
        super(title);
        this.serverRepr = repr;
        this.playerInventoryTitle = repr.getPlayer().getInventory().getDisplayName();
        this.cancelNextRelease = true;
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void init() {
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.drawBackground(matrices, delta, mouseX, mouseY);
        RenderSystem.disableDepthTest();

        super.render(matrices, mouseX, mouseY, delta);

        MatrixStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.push();
        matrixStack.translate(this.x, this.y, 0.0);
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        this.focusedSlot = null;

        for (Slot slot : this.serverRepr.getSlots()) {
            if (slot.isEnabled()) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                this.drawSlot(matrices, slot);
            }

            if (this.isPointOverSlot(slot, mouseX, mouseY) && slot.isEnabled()) {
                this.focusedSlot = slot;
                int l = slot.x;
                int m = slot.y;
                HandledScreen.drawSlotHighlight(matrices, l, m, this.getZOffset());
            }
        }

        this.drawForeground(matrices, mouseX, mouseY);

        ItemStack cursorStack = this.touchDragStack.isEmpty() ? this.serverRepr.getCursorStack() : this.touchDragStack;

        if (!cursorStack.isEmpty()) {
            int yOffset = this.touchDragStack.isEmpty() ? 8 : 16;

            String specialCountLabel = null;
            if (!this.touchDragStack.isEmpty() && this.touchIsRightClickDrag) {
                cursorStack = cursorStack.copy();
                cursorStack.setCount(MathHelper.ceil((float)cursorStack.getCount() / 2.0F));
            } else if (this.cursorDragging && this.cursorDragSlots.size() > 1) {
                cursorStack = cursorStack.copy();
                cursorStack.setCount(this.draggedStackRemainder);
                if (cursorStack.isEmpty()) {
                    specialCountLabel = Formatting.YELLOW + "0";
                }
            }

            this.drawItem(cursorStack, mouseX - this.x - 8, mouseY - this.y - yOffset, specialCountLabel);
        }

        if (!this.touchDropReturningStack.isEmpty()) {
            float f = (float)(Util.getMeasuringTimeMs() - this.touchDropTime) / 100.0F;
            if (f >= 1.0F) {
                f = 1.0F;
                this.touchDropReturningStack = ItemStack.EMPTY;
            }

            this.drawItem(
                this.touchDropReturningStack,
                this.touchDropX + (int)((this.touchDropOriginSlot.x - this.touchDropX) * f),
                this.touchDropY + (int)((this.touchDropOriginSlot.y - this.touchDropY) * f),
                null
            );
        }

        matrixStack.pop();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
    }

    protected void drawMouseoverTooltip(MatrixStack matrices, int x, int y) {
        if (this.serverRepr.getCursorStack().isEmpty() && this.focusedSlot != null && this.focusedSlot.hasStack()) {
            this.renderTooltip(matrices, this.focusedSlot.getStack(), x, y);
        }

    }

    private void drawItem(ItemStack stack, int x, int y, String amountText) {
        MatrixStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.translate(0.0, 0.0, 32.0);
        RenderSystem.applyModelViewMatrix();
        this.setZOffset(200);
        this.itemRenderer.zOffset = 200.0F;
        this.itemRenderer.renderInGuiWithOverrides(stack, x, y);
        this.itemRenderer.renderGuiItemOverlay(this.textRenderer, stack, x, y - (this.touchDragStack.isEmpty() ? 0 : 8), amountText);
        this.setZOffset(0);
        this.itemRenderer.zOffset = 0.0F;
    }

    protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        this.textRenderer.draw(matrices, this.title, (float)this.titleX, (float)this.titleY, 4210752);
        this.textRenderer.draw(matrices, this.playerInventoryTitle, (float)this.playerInventoryTitleX, (float)this.playerInventoryTitleY, 4210752);
    }

    protected abstract void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY);

    private void drawSlot(MatrixStack matrices, Slot slot) {
        ItemStack slotStack = slot.getStack();

        boolean drawDragBackground = false;
        boolean bl2 = slot == this.touchDragSlotStart && !this.touchDragStack.isEmpty() && !this.touchIsRightClickDrag;

        ItemStack cursorStack = this.serverRepr.getCursorStack();
        String specialCountLabel = null;
        if (slot == this.touchDragSlotStart && !this.touchDragStack.isEmpty() && this.touchIsRightClickDrag && !slotStack.isEmpty()) {
            slotStack = slotStack.copy();
            slotStack.setCount(slotStack.getCount() / 2);
        } else if (this.cursorDragging && this.cursorDragSlots.contains(slot) && !cursorStack.isEmpty()) {
            if (this.cursorDragSlots.size() == 1) {
                return;
            }

            if (ScreenHandler.canInsertItemIntoSlot(slot, cursorStack, true) && this.serverRepr.canInsertIntoSlot(slot)) {
                slotStack = cursorStack.copy();
                drawDragBackground = true;
                ScreenHandler.calculateStackSize(this.cursorDragSlots, this.heldButtonType, slotStack, slot.getStack().isEmpty() ? 0 : slot.getStack().getCount());
                int k = Math.min(slotStack.getMaxCount(), slot.getMaxItemCount(slotStack));
                if (slotStack.getCount() > k) {
                    specialCountLabel = Formatting.YELLOW.toString() + k;
                    slotStack.setCount(k);
                }
            } else {
                this.cursorDragSlots.remove(slot);
                this.calculateOffset();
            }
        }

        this.setZOffset(100);
        this.itemRenderer.zOffset = 100.0F;
        if (slotStack.isEmpty() && slot.isEnabled()) {
            Pair<Identifier, Identifier> pair = slot.getBackgroundSprite();
            if (pair != null) {
                Sprite sprite = this.client.getSpriteAtlas(pair.getFirst()).apply(pair.getSecond());
                RenderSystem.setShaderTexture(0, sprite.getAtlas().getId());
                drawSprite(matrices, slot.x, slot.y, this.getZOffset(), 16, 16, sprite);
                bl2 = true;
            }
        }

        if (!bl2) {
            if (drawDragBackground) {
                fill(matrices, slot.x, slot.y, slot.x + 16, slot.y + 16, -2130706433);
            }

            RenderSystem.enableDepthTest();
            this.itemRenderer.renderInGuiWithOverrides(this.client.player, slotStack, slot.x, slot.y, slot.x + slot.y * this.backgroundWidth);
            this.itemRenderer.renderGuiItemOverlay(this.textRenderer, slotStack, slot.x, slot.y, specialCountLabel);
        }

        this.itemRenderer.zOffset = 0.0F;
        this.setZOffset(0);
    }

    private void calculateOffset() {
        ItemStack cursorStack = this.serverRepr.getCursorStack();
        if (!cursorStack.isEmpty() && this.cursorDragging) {
            if (this.heldButtonType == 2) {
                this.draggedStackRemainder = cursorStack.getMaxCount();
            } else {
                this.draggedStackRemainder = cursorStack.getCount();

                for(Slot slot : this.cursorDragSlots) {
                    ItemStack itemStack2 = cursorStack.copy();
                    ItemStack itemStack3 = slot.getStack();
                    int i = itemStack3.isEmpty() ? 0 : itemStack3.getCount();
                    ScreenHandler.calculateStackSize(this.cursorDragSlots, this.heldButtonType, itemStack2, i);
                    int j = Math.min(itemStack2.getMaxCount(), slot.getMaxItemCount(itemStack2));
                    if (itemStack2.getCount() > j) {
                        itemStack2.setCount(j);
                    }

                    this.draggedStackRemainder -= itemStack2.getCount() - i;
                }

            }
        }
    }

    @Nullable
    private Slot getSlotAt(double x, double y) {
        for (Slot slot : serverRepr.getSlots()) {
            if (this.isPointOverSlot(slot, x, y) && slot.isEnabled()) {
                return slot;
            }
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int buttonId) {
        if (super.mouseClicked(mouseX, mouseY, buttonId)) {
            return true;
        }

        boolean canDrag = this.client.options.pickItemKey.matchesMouse(buttonId) && this.client.interactionManager.hasCreativeInventory();
        Slot slot = this.getSlotAt(mouseX, mouseY);
        long currentTime = Util.getMeasuringTimeMs();
        this.doubleClicking = this.lastClickedSlot == slot && currentTime - this.lastButtonClickTime < 250L && this.lastClickedButton == buttonId;
        this.cancelNextRelease = false;

        if (buttonId != 0 && buttonId != GLFW.GLFW_MOUSE_BUTTON_RIGHT && !canDrag) {
            if (this.focusedSlot != null && this.serverRepr.getCursorStack().isEmpty()) {
                if (this.client.options.swapHandsKey.matchesMouse(buttonId)) {
                    this.serverRepr.runAction(new SwapSlotsAction(this.focusedSlot.id, 40));
                } else {
                    for (int i = 0; i < 9; ++i) {
                        if (this.client.options.hotbarKeys[i].matchesMouse(buttonId)) {
                            this.serverRepr.runAction(new SwapSlotsAction(this.focusedSlot.id, i));
                        }
                    }
                }

            }
        } else {
            boolean isOutsideWindow = this.isClickOutsideBounds(mouseX, mouseY, this.x, this.y, buttonId);
            int slotId = -1;

            if (slot != null) {
                slotId = slot.id;
            }

            if (isOutsideWindow) {
                slotId = -999;
            }

            if (this.client.options.touchscreen && isOutsideWindow && this.serverRepr.getCursorStack().isEmpty()) {
                this.client.setScreen(null);
                return true;
            }

            if (slotId != -1) {
                if (this.client.options.touchscreen) {
                    if (slot != null && slot.hasStack()) {
                        this.touchDragSlotStart = slot;
                        this.touchDragStack = ItemStack.EMPTY;
                        this.touchIsRightClickDrag = buttonId == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                    } else {
                        this.touchDragSlotStart = null;
                    }
                } else if (!this.cursorDragging) {
                    if (this.serverRepr.getCursorStack().isEmpty()) {
                        if (canDrag) {
                            this.serverRepr.runAction(new CloneSlotAction(slotId));
                        } else {
                            boolean bl3 = slotId != -999
                                && (
                                InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT)
                                    || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT)
                            );
                            SlotActionType slotActionType = SlotActionType.PICKUP;
                            if (bl3) {
                                this.quickMovingStack = slot != null && slot.hasStack() ? slot.getStack().copy() : ItemStack.EMPTY;
                                slotActionType = SlotActionType.QUICK_MOVE;

                                this.serverRepr.runAction(new QuickMoveAction(slotId));
                            } else if (slotId == -999) {
                                slotActionType = SlotActionType.THROW;

                                this.serverRepr.runAction(new ThrowSlotAction(slotId, buttonId != 0));
                            } else {
                                this.serverRepr.runAction(new PickupSlotAction(slotId, buttonId));
                            }
                        }

                        this.cancelNextRelease = true;
                    } else {
                        this.cursorDragging = true;
                        this.heldButtonCode = buttonId;
                        this.cursorDragSlots.clear();
                        if (buttonId == 0) {
                            this.heldButtonType = 0;
                        } else if (buttonId == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                            this.heldButtonType = 1;
                        } else if (canDrag) {
                            this.heldButtonType = 2;
                        }
                    }
                }
            }
        }

        this.lastClickedSlot = slot;
        this.lastButtonClickTime = currentTime;
        this.lastClickedButton = buttonId;
        return true;
    }

    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < (double)left || mouseY < (double)top || mouseX >= (double)(left + this.backgroundWidth) || mouseY >= (double)(top + this.backgroundHeight);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Slot slot = this.getSlotAt(mouseX, mouseY);
        ItemStack itemStack = this.serverRepr.getCursorStack();
        if (this.touchDragSlotStart != null && this.client.options.touchscreen) {
            if (button == 0 || button == 1) {
                if (this.touchDragStack.isEmpty()) {
                    if (slot != this.touchDragSlotStart && !this.touchDragSlotStart.getStack().isEmpty()) {
                        this.touchDragStack = this.touchDragSlotStart.getStack().copy();
                    }
                } else if (this.touchDragStack.getCount() > 1 && slot != null && ScreenHandler.canInsertItemIntoSlot(slot, this.touchDragStack, false)) {
                    long l = Util.getMeasuringTimeMs();
                    if (this.touchHoveredSlot == slot) {
                        if (l - this.touchDropTimer > 500L) {
                            serverRepr.runAction(new PickupSlotAction(this.touchDragSlotStart.id, 0));
                            serverRepr.runAction(new PickupSlotAction(slot.id, 1));
                            serverRepr.runAction(new PickupSlotAction(this.touchDragSlotStart.id, 0));
                            this.touchDropTimer = l + 750L;
                            this.touchDragStack.decrement(1);
                        }
                    } else {
                        this.touchHoveredSlot = slot;
                        this.touchDropTimer = l;
                    }
                }
            }
        } else if (this.cursorDragging
            && slot != null
            && !itemStack.isEmpty()
            && (itemStack.getCount() > this.cursorDragSlots.size() || this.heldButtonType == 2)
            && ScreenHandler.canInsertItemIntoSlot(slot, itemStack, true)
            && slot.canInsert(itemStack)
            && this.serverRepr.canInsertIntoSlot(slot)) {
            this.cursorDragSlots.add(slot);
            this.calculateOffset();
        }

        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Slot slot = this.getSlotAt(mouseX, mouseY);
        boolean isOutsideWindow = this.isClickOutsideBounds(mouseX, mouseY, this.x, this.y, button);

        int k = -1;

        if (slot != null) {
            k = slot.id;
        }

        if (isOutsideWindow) {
            k = -999;
        }

        if (this.doubleClicking && slot != null && button == MouseButton.LEFT && this.serverRepr.canInsertIntoSlot(ItemStack.EMPTY, slot)) {
            if (hasShiftDown()) {
                if (!this.quickMovingStack.isEmpty()) {
                    for(Slot otherSlot : this.serverRepr.getSlots()) {
                        if (otherSlot != null
                            && otherSlot.canTakeItems(this.client.player)
                            && otherSlot.hasStack()
                            && otherSlot.inventory == slot.inventory
                            && ScreenHandler.canInsertItemIntoSlot(otherSlot, this.quickMovingStack, true)) {
                            this.serverRepr.runAction(new QuickMoveAction(otherSlot.id));
                        }
                    }
                }
            } else {
                this.serverRepr.runAction(new PickupAllAction(k, button));

            }

            this.doubleClicking = false;
            this.lastButtonClickTime = 0L;
        } else {
            if (this.cursorDragging && this.heldButtonCode != button) {
                this.cursorDragging = false;
                this.cursorDragSlots.clear();
                this.cancelNextRelease = true;
                return true;
            }

            if (this.cancelNextRelease) {
                this.cancelNextRelease = false;
                return true;
            }

            if (this.touchDragSlotStart != null && this.client.options.touchscreen) {
                if (button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (this.touchDragStack.isEmpty() && slot != this.touchDragSlotStart) {
                        this.touchDragStack = this.touchDragSlotStart.getStack();
                    }

                    boolean insertable = ScreenHandler.canInsertItemIntoSlot(slot, this.touchDragStack, false);
                    if (k != GLFW.GLFW_KEY_UNKNOWN && !this.touchDragStack.isEmpty() && insertable) {
                        this.serverRepr.runAction(new PickupSlotAction(this.touchDragSlotStart.id, button));
                        this.serverRepr.runAction(new PickupSlotAction(k, MouseButton.LEFT));
                        if (this.serverRepr.getCursorStack().isEmpty()) {
                            this.touchDropReturningStack = ItemStack.EMPTY;
                        } else {
                            this.serverRepr.runAction(new PickupSlotAction(this.touchDragSlotStart.id, button));
                            this.touchDropX = MathHelper.floor(mouseX - (double) this.x);
                            this.touchDropY = MathHelper.floor(mouseY - (double) this.y);
                            this.touchDropOriginSlot = this.touchDragSlotStart;
                            this.touchDropReturningStack = this.touchDragStack;
                            this.touchDropTime = Util.getMeasuringTimeMs();
                        }
                    } else if (!this.touchDragStack.isEmpty()) {
                        this.touchDropX = MathHelper.floor(mouseX - (double) this.x);
                        this.touchDropY = MathHelper.floor(mouseY - (double) this.y);
                        this.touchDropOriginSlot = this.touchDragSlotStart;
                        this.touchDropReturningStack = this.touchDragStack;
                        this.touchDropTime = Util.getMeasuringTimeMs();
                    }

                    this.touchDragStack = ItemStack.EMPTY;
                    this.touchDragSlotStart = null;
                }
            } else if (this.cursorDragging && !this.cursorDragSlots.isEmpty()) {
                this.serverRepr.runAction(new QuickCraftAction(QuickCraftStage.STARTING, heldButtonType, -999));

                for(Slot slot2 : this.cursorDragSlots) {
                    this.serverRepr.runAction(new QuickCraftAction(QuickCraftStage.IN_PROGRESS, heldButtonType, slot2.id));
                }

                this.serverRepr.runAction(new QuickCraftAction(QuickCraftStage.ENDING, heldButtonType, -999));
            } else if (!this.serverRepr.getCursorStack().isEmpty()) {
                if (this.client.options.pickItemKey.matchesMouse(button)) {
                    this.serverRepr.runAction(new CloneSlotAction(k));
                } else {
                    boolean isQuickmove = k != -999 && KeyUtils.isShiftDown();

                    if (isQuickmove) {
                        this.quickMovingStack = slot != null && slot.hasStack() ? slot.getStack().copy() : ItemStack.EMPTY;
                        this.serverRepr.runAction(new QuickMoveAction(k));
                    } else {
                        this.serverRepr.runAction(new PickupSlotAction(k, button));
                    }
                }
            }
        }

        if (this.serverRepr.getCursorStack().isEmpty()) {
            this.lastButtonClickTime = 0L;
        }

        this.cursorDragging = false;
        return true;
    }

    private boolean isPointOverSlot(Slot slot, double pointX, double pointY) {
        return this.isPointWithinBounds(slot.x, slot.y, 16, 16, pointX, pointY);
    }

    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        int i = this.x;
        int j = this.y;
        pointX -= (double)i;
        pointY -= (double)j;
        return pointX >= (double)(x - 1) && pointX < (double)(x + width + 1) && pointY >= (double)(y - 1) && pointY < (double)(y + height + 1);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        } else {
            this.handleHotbarKeyPressed(keyCode, scanCode);
            if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
                if (this.client.options.pickItemKey.matchesKey(keyCode, scanCode)) {
                    this.serverRepr.runAction(new CloneSlotAction(this.focusedSlot.id));
                } else if (this.client.options.dropKey.matchesKey(keyCode, scanCode)) {
                    this.serverRepr.runAction(new ThrowSlotAction(this.focusedSlot.id, hasControlDown()));
                }
            }

            return true;
        }
    }

    protected boolean handleHotbarKeyPressed(int keyCode, int scanCode) {
        if (this.serverRepr.getCursorStack().isEmpty() && this.focusedSlot != null) {
            if (this.client.options.swapHandsKey.matchesKey(keyCode, scanCode)) {
                this.serverRepr.runAction(new SwapSlotsAction(this.focusedSlot.id, 40));
                return true;
            }

            for(int i = 0; i < 9; ++i) {
                if (this.client.options.hotbarKeys[i].matchesKey(keyCode, scanCode)) {
                    this.serverRepr.runAction(new SwapSlotsAction(this.focusedSlot.id, i));
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void removed() {
        if (this.client.player != null) {
            this.serverRepr.onClose();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public final void tick() {
        super.tick();
        if (this.client.player.isAlive() && !this.client.player.isRemoved()) {
            this.screenTick();
        } else {
            this.client.player.closeHandledScreen();
        }

    }

    protected void screenTick() {
    }

    @Override
    public void close() {
        this.client.player.closeHandledScreen();
        super.close();
    }
}
