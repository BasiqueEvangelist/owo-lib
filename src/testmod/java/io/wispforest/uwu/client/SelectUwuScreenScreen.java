package io.wispforest.uwu.client;

import io.wispforest.owo.config.ui.ConfigScreen;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.VerticalFlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.uwu.Uwu;
import net.minecraft.text.LiteralText;
import org.jetbrains.annotations.NotNull;

public class SelectUwuScreenScreen extends BaseOwoScreen<VerticalFlowLayout> {

    @Override
    protected @NotNull OwoUIAdapter<VerticalFlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(VerticalFlowLayout rootComponent) {
        this.uiAdapter.rootComponent.surface(Surface.flat(0x77000000))
                .verticalAlignment(VerticalAlignment.CENTER)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        this.uiAdapter.rootComponent.child(
                Components.label(new LiteralText("Available screens"))
                        .shadow(true)
                        .margins(Insets.bottom(5))
        );

        var panel = Containers.verticalFlow(Sizing.content(), Sizing.content()).<FlowLayout>configure(layout -> {
            layout.padding(Insets.of(5))
                    .surface(Surface.PANEL)
                    .horizontalAlignment(HorizontalAlignment.CENTER);
        });

        panel.child(Components.button(new LiteralText("code demo"), button -> this.client.setScreen(new ComponentTestScreen())).margins(Insets.vertical(3)));
        panel.child(Components.button(new LiteralText("xml demo"), button -> this.client.setScreen(new TestParseScreen())).margins(Insets.vertical(3)));
        panel.child(Components.button(new LiteralText("code config"), button -> this.client.setScreen(new TestConfigScreen())).margins(Insets.vertical(3)));
        panel.child(Components.button(new LiteralText("xml config"), button -> this.client.setScreen(ConfigScreen.create(Uwu.CONFIG, null))).margins(Insets.vertical(3)));
        panel.child(Components.button(new LiteralText("optimization test"), button -> this.client.setScreen(new TooManyComponentsScreen())).margins(Insets.vertical(3)));

        this.uiAdapter.rootComponent.child(panel);
    }
}
