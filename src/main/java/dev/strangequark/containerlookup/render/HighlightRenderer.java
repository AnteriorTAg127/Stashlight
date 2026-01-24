package dev.strangequark.containerlookup.render;


import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import dev.strangequark.containerlookup.model.HighlightPos;

public final class HighlightRenderer {

    private HighlightRenderer() {
    }

    public static void render(WorldRenderContext context) {
        VertexConsumer vc = context.consumers().getBuffer(HighlightRenderLayer.XRAY_LAYER);
        Vec3d cam = context.gameRenderer().getCamera().getPos();

        HighlightManager.removeExpired();

        for (HighlightPos highlight : HighlightManager.getActiveHighlights()) {
            long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();
            if (!HighlightEffect.shouldRender(elapsed)) continue;

            MatrixStack matrices = context.matrices();
            matrices.push();
            matrices.translate(
                    highlight.pos().getX() - cam.x,
                    highlight.pos().getY() - cam.y,
                    highlight.pos().getZ() - cam.z
            );
            HighlightGeometry.drawWireframeBox(matrices, vc, cam, highlight.pos());
            matrices.pop();
        }
    }
}
