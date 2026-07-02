package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.HighlightTarget;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.world.phys.Vec3;

public final class HighlightRenderer {

    private HighlightRenderer() {
    }

    public static void render(WorldRenderContext context) {
        var cfg = Config.get().highlight();
        if (cfg == null) return;

        Vec3 cam = context.gameRenderer().getMainCamera().getPosition();

        HighlightManager.removeExpired();

        if (cfg.blockOutlineEnabled()) {
            VertexConsumer vc = context.consumers().getBuffer(HighlightRenderLayer.XRAY_LAYER);
            for (HighlightTarget highlight : HighlightManager.getActiveHighlights()) {
                long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();
                if (cfg.guiSlotPulse() && !HighlightEffect.shouldRender(elapsed)) continue;

                PoseStack matrices = context.matrices();
                matrices.pushPose();
                matrices.translate(
                        highlight.pos().getX() - cam.x,
                        highlight.pos().getY() - cam.y,
                        highlight.pos().getZ() - cam.z
                );
                HighlightGeometry.drawWireframeBox(matrices, vc, cam, highlight.pos(), highlight.color());
                matrices.popPose();
            }
        }

        if (cfg.worldMarkerBeamEnabled() || cfg.worldMarkerBoxEnabled()) {
            WorldMarkerRenderer.render(context, cam);
        }
    }
}
