package io.wispforest.owo.braid.widgets;

import io.wispforest.owo.braid.core.Constraints;
import io.wispforest.owo.braid.core.Size;
import io.wispforest.owo.braid.framework.instance.LeafWidgetInstance;
import io.wispforest.owo.braid.framework.widget.LeafInstanceWidget;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;

import java.util.OptionalDouble;

public class ItemStackWidget extends LeafInstanceWidget {

    public final ItemStack stack;
    public final boolean showOverlay;

    public ItemStackWidget(ItemStack stack, boolean showOverlay) {
        this.stack = stack;
        this.showOverlay = showOverlay;
    }

    @Override
    public LeafWidgetInstance<?> instantiate() {
        return new Instance(this);
    }

    public static class Instance extends LeafWidgetInstance<ItemStackWidget> {

        public static final Size DEFAULT_SIZE = Size.square(16);

        public Instance(ItemStackWidget widget) {
            super(widget);
        }

        @Override
        protected void doLayout(Constraints constraints) {
            var size = DEFAULT_SIZE.constrained(constraints);
            this.transform.setSize(size);
        }

        @Override
        protected double measureIntrinsicWidth(double height) {
            return 16;
        }

        @Override
        protected double measureIntrinsicHeight(double width) {
            return 16;
        }

        @Override
        protected OptionalDouble measureBaselineOffset() {
            return OptionalDouble.empty();
        }

        @Override
        public void draw(OwoUIDrawContext ctx) {
//            this.host().client().getItemRenderer().update(ITEM_RENDER_STATE, this.widget.stack, ModelTransformationMode.GUI, false, null, null, 0);

            var client = MinecraftClient.getInstance();

            final boolean notSideLit = !client.getItemRenderer().getModel(this.widget.stack, null, null, 0).isSideLit();
            if (notSideLit) {
                ctx.draw();
                DiffuseLighting.disableGuiDepthLighting();
            }

            var matrices = ctx.getMatrices();
            matrices.push();

            // Scale according to component size and translate to the center
            matrices.scale((float) (this.transform.width() / 16), (float) (this.transform.height() / 16), 1);
            matrices.translate(8.0, 8.0, 8.0);

            // Vanilla scaling and y inversion
            matrices.scale(16, -16, 16);


            client.getItemRenderer().renderItem(this.widget.stack, ModelTransformationMode.GUI, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, matrices, OwoUIDrawContext.of(ctx).getVertexConsumers(), client.world, 0);
            ctx.draw();

            // Clean up
            matrices.pop();

            if (this.widget.showOverlay) {
                ctx.drawItemInSlot(client.textRenderer, this.widget.stack, 0, 0);
            }
            if (notSideLit) {
                DiffuseLighting.enableGuiDepthLighting();
            }
        }
    }
}
