package io.wispforest.owo.itemgroup.gui;

import io.wispforest.owo.itemgroup.Icon;
import io.wispforest.owo.itemgroup.OwoItemGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmChatLinkScreen;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * A button placed to the right side of the creative inventory. Provides defaults
 * for linking to sites, but can execute arbitrary actions
 */
public final class ItemGroupButton implements OwoItemGroup.ButtonDefinition {

    public static final Identifier ICONS_TEXTURE = new Identifier("owo", "textures/gui/icons.png");

    private final Icon icon;
    private final String name;
    private final Identifier texture;
    private final Runnable action;

    public ItemGroupButton(Icon icon, String name, Identifier texture, Runnable action) {
        this.icon = icon;
        this.name = name;
        this.action = action;
        this.texture = texture;
    }

    public ItemGroupButton(Icon icon, String name, Runnable action) {
        this(icon, name, ItemGroupTab.DEFAULT_TEXTURE, action);
    }

    public static ItemGroupButton github(String url) {
        return link(Icon.of(ICONS_TEXTURE, 0, 0, 64, 64), "github", url);
    }

    public static ItemGroupButton modrinth(String url) {
        return link(Icon.of(ICONS_TEXTURE, 16, 0, 64, 64), "modrinth", url);
    }

    public static ItemGroupButton curseforge(String url) {
        return link(Icon.of(ICONS_TEXTURE, 32, 0, 64, 64), "curseforge", url);
    }

    public static ItemGroupButton discord(String url) {
        return link(Icon.of(ICONS_TEXTURE, 48, 0, 64, 64), "discord", url);
    }

    /**
     * Creates a button that opens the given link when clicked
     *
     * @param icon The icon for this button to use
     * @param name The name of this button, used for the translation key
     * @param url  The url to open
     * @return The created button
     */
    public static ItemGroupButton link(Icon icon, String name, String url) {
        return new ItemGroupButton(icon, name, () -> {
            final var client = MinecraftClient.getInstance();
            var screen = client.currentScreen;
            client.setScreen(new ConfirmChatLinkScreen(confirmed -> {
                if (confirmed) Util.getOperatingSystem().open(url);
                client.setScreen(screen);
            }, url, true));
        });
    }

    @Override
    public Identifier texture() {
        return this.texture;
    }

    @Override
    public Icon icon() {
        return icon;
    }

    @Override
    public String getTranslationKey(String groupKey) {
        return "itemGroup." + groupKey + ".button." + name;
    }

    public Runnable action() {
        return action;
    }

}
