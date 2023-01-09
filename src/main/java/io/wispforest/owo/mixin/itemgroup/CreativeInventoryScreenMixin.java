package io.wispforest.owo.mixin.itemgroup;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.itemgroup.OwoItemGroup;
import io.wispforest.owo.itemgroup.gui.ItemGroupButtonWidget;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.util.CursorAdapter;
import io.wispforest.owo.util.pond.OwoCreativeInventoryScreenExtensions;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin extends AbstractInventoryScreen<CreativeInventoryScreen.CreativeScreenHandler> implements OwoCreativeInventoryScreenExtensions {

    @Shadow
    @Final
    private static Identifier TEXTURE;

    @Shadow
    protected abstract void init();

    @Shadow private static int selectedTab;
    @Unique
    private final List<ItemGroupButtonWidget> owo$buttons = new ArrayList<>();

    @Unique
    private OwoItemGroup owo$owoGroup = null;

    @Unique
    private final CursorAdapter owo$cursorAdapter = CursorAdapter.ofClientWindow();

    // ----------
    // Background
    // ----------

    @ModifyArg(method = "drawBackground", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V", ordinal = 1))
    private Identifier injectCustomGroupTexture(Identifier original) {
        if (!(ItemGroup.GROUPS[selectedTab] instanceof OwoItemGroup owoGroup) || owoGroup.getCustomTexture() == null) return original;
        return owoGroup.getCustomTexture();
    }

    // ----------------
    // Scrollbar slider
    // ----------------

    @ModifyArg(method = "drawBackground", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V", ordinal = 2))
    private Identifier injectCustomScrollbarTexture(Identifier original) {
        if (!(ItemGroup.GROUPS[selectedTab] instanceof OwoItemGroup owoGroup) || owoGroup.getCustomTexture() == null) return original;
        this.owo$owoGroup = owoGroup;
        return owoGroup.getCustomTexture();
    }

    @ModifyArg(method = "drawBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/CreativeInventoryScreen;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V", ordinal = 1), index = 3)
    private int injectCustomScrollbarTextureU(int original) {
        if (owo$owoGroup == null) return original;
        return original - 232;
    }

    @ModifyArg(method = "drawBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/CreativeInventoryScreen;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V", ordinal = 1), index = 4)
    private int injectCustomScrollbarTextureV(int original) {
        if (owo$owoGroup == null) return original;
        return 136;
    }

    @Inject(method = "drawBackground", at = @At("RETURN"))
    private void releaseGroupInstance(MatrixStack matrices, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        this.owo$owoGroup = null;
    }

    // -------------
    // Group headers
    // -------------

    @Inject(method = "renderTabIcon", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemGroup;getIcon()Lnet/minecraft/item/ItemStack;"))
    private void injectCustomTabTexture(MatrixStack matrices, ItemGroup group, CallbackInfo ci) {
        if (!(group instanceof OwoItemGroup owoGroup) || owoGroup.getCustomTexture() == null) return;
        this.owo$owoGroup = owoGroup;
        RenderSystem.setShaderTexture(0, owoGroup.getCustomTexture());
    }

    @ModifyArg(method = "renderTabIcon", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/CreativeInventoryScreen;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V"), index = 3)
    private int injectCustomTabTextureLocation(int original) {
        if (this.owo$owoGroup == null) return original;
        return this.owo$owoGroup.getColumn() == 0 ? 195 : 223;
    }

    @Inject(method = "renderTabIcon", at = @At("RETURN"))
    private void restoreTabTexture(MatrixStack matrices, ItemGroup group, CallbackInfo ci) {
        if (this.owo$owoGroup == null) return;
        this.owo$owoGroup = null;
        RenderSystem.setShaderTexture(0, TEXTURE);
    }

    // -------------
    // oωo tab title
    // -------------

    @ModifyArg(method = "drawForeground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/Text;FFI)I"))
    private Text injectTabNameAsTitle(Text original) {
        if (!(ItemGroup.GROUPS[selectedTab] instanceof OwoItemGroup owoGroup) || !owoGroup.shouldDisplayTabNamesAsTitle()) return original;
        if (owoGroup.getSelectedTab().primary()) {
            return owoGroup.getSelectedTab().name();
        } else {
            return new TranslatableText(
                    "text.owo.itemGroup.tab_template",
                    owoGroup.getDisplayName(),
                    owoGroup.getSelectedTab().name()
            );
        }
    }

    // ---------------
    // oωo tab buttons
    // ---------------

    @Inject(at = @At("HEAD"), method = "setSelectedTab(Lnet/minecraft/item/ItemGroup;)V")
    private void setSelectedTab(ItemGroup group, CallbackInfo ci) {
        owo$buttons.forEach(this::remove);
        owo$buttons.clear();

        if (group instanceof OwoItemGroup owoGroup) {

            int tabRootY = this.y;

            final var tabStackHeight = owoGroup.getTabStackHeight();
            tabRootY -= 13 * (tabStackHeight - 4);

            if (owoGroup.shouldDisplaySingleTab() || owoGroup.tabs.size() > 1) {
                for (int i = 0; i < owoGroup.tabs.size(); i++) {
                    var tab = owoGroup.tabs.get(i);

                    int xOffset = this.x - 27 - (i / tabStackHeight) * 26;
                    int yOffset = tabRootY + 10 + (i % tabStackHeight) * 30;

                    var tabButton = new ItemGroupButtonWidget(xOffset, yOffset, false, tab, group.getName(), owo$createSelectAction(this, owoGroup, i));

                    if (i == owoGroup.getSelectedTabIndex()) tabButton.isSelected = true;

                    owo$buttons.add(tabButton);
                    this.addDrawableChild(tabButton);
                }
            }

            final var buttonStackHeight = owoGroup.getButtonStackHeight();
            tabRootY = this.y - 13 * (buttonStackHeight - 4);

            var buttons = owoGroup.getButtons();
            for (int i = 0; i < buttons.size(); i++) {
                var button = buttons.get(i);

                int xOffset = this.x + 198 + (i / buttonStackHeight) * 26;
                int yOffset = tabRootY + 10 + (i % buttonStackHeight) * 30;

                var tabButton = new ItemGroupButtonWidget(xOffset, yOffset, true, button, group.getName(), button1 -> button.action().run());

                owo$buttons.add(tabButton);
                this.addDrawableChild(tabButton);
            }
        }
    }

    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V")
    private void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta, CallbackInfo cbi) {
        boolean anyButtonHovered = false;

        for (var button : owo$buttons) {
            if (button.trulyHovered()) {
                renderTooltip(matrixStack, button.getMessage(), mouseX, mouseY);
                anyButtonHovered = true;
            }
        }

        this.owo$cursorAdapter.applyStyle(anyButtonHovered ? CursorStyle.HAND : CursorStyle.NONE);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void createCursorAdapter(CallbackInfo ci) {
//        this.owo$cursorAdapter = CursorAdapter.ofClientWindow();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void disposeCursorAdapter(CallbackInfo ci) {
        this.owo$cursorAdapter.dispose();
    }

    @Override
    public int owo$getRootX() {
        return this.x;
    }

    @Override
    public int owo$getRootY() {
        return this.y;
    }

    @Unique
    private ButtonWidget.PressAction owo$createSelectAction(Screen targetScreen, OwoItemGroup group, int targetTabIndex) {
        return button -> {
            group.setSelectedTab(targetTabIndex);
            this.clearChildren();
            this.init();
            ((ItemGroupButtonWidget) button).isSelected = true;
        };
    }

    public CreativeInventoryScreenMixin(CreativeInventoryScreen.CreativeScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }
}