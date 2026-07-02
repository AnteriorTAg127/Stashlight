package dev.strangequark.stashlight.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.HighlightTarget;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders world-level markers for highlighted containers: beacon-style beams
 * and selection boxes. Respects max marker count and max distance.
 */
public final class WorldMarkerRenderer {

    private WorldMarkerRenderer() {
    }

    public static void render(WorldRenderContext context, Vec3 cam) {
        var cfg = Config.get().highlight();
        if (cfg == null) return;

        List<HighlightTarget> active = HighlightManager.getActiveHighlights();
        if (active.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        int maxDistance = cfg.worldMarkerMaxDistance();
        int maxDistanceSq = maxDistance * maxDistance;
        int maxMarkers = cfg.worldMarkerMaxMarkers();

        // Deduplicate by block position and enforce distance / count limits.
        Set<BlockPos> seen = new HashSet<>();
        List<HighlightTarget> toRender = new ArrayList<>();
        for (HighlightTarget target : active) {
            if (!seen.add(target.pos())) continue;
            if (target.pos().distSqr(playerPos) > maxDistanceSq) continue;
            toRender.add(target);
            if (toRender.size() >= maxMarkers) break;
        }

        if (toRender.isEmpty()) return;

        long now = System.currentTimeMillis();

        if (cfg.worldMarkerBeamEnabled()) {
            VertexConsumer beamVc = context.consumers().getBuffer(HighlightRenderLayer.XRAY_LAYER);
            for (HighlightTarget target : toRender) {
                long elapsed = now - target.startTimeMillis();
                if (cfg.guiSlotPulse() && !HighlightEffect.shouldRender(elapsed)) continue;
                renderBeam(matrices(context), beamVc, target, cam, cfg.worldMarkerBeamColor());
            }
        }

        if (cfg.worldMarkerBoxEnabled()) {
            VertexConsumer boxVc = context.consumers().getBuffer(HighlightRenderLayer.XRAY_LAYER);
            for (HighlightTarget target : toRender) {
                long elapsed = now - target.startTimeMillis();
                if (cfg.guiSlotPulse() && !HighlightEffect.shouldRender(elapsed)) continue;
                renderBox(context, target, cam, cfg.worldMarkerBoxColor());
            }
        }
    }

    private static PoseStack matrices(WorldRenderContext context) {
        return context.matrices();
    }

    private static void renderBeam(PoseStack matrices, VertexConsumer vc, HighlightTarget target, Vec3 cam, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        matrices.pushPose();
        matrices.translate(
                target.pos().getX() + 0.5 - cam.x,
                target.pos().getY() - cam.y,
                target.pos().getZ() + 0.5 - cam.z
        );

        Matrix4f pose = matrices.last().pose();
        float width = 0.25f;
        float height = 256f;

        // South face
        vertex(vc, pose, -width, 0, width, r, g, b, a);
        vertex(vc, pose, -width, height, width, r, g, b, a);
        vertex(vc, pose, width, height, width, r, g, b, a);
        vertex(vc, pose, width, 0, width, r, g, b, a);

        // North face
        vertex(vc, pose, width, 0, -width, r, g, b, a);
        vertex(vc, pose, width, height, -width, r, g, b, a);
        vertex(vc, pose, -width, height, -width, r, g, b, a);
        vertex(vc, pose, -width, 0, -width, r, g, b, a);

        // East face
        vertex(vc, pose, width, 0, width, r, g, b, a);
        vertex(vc, pose, width, height, width, r, g, b, a);
        vertex(vc, pose, width, height, -width, r, g, b, a);
        vertex(vc, pose, width, 0, -width, r, g, b, a);

        // West face
        vertex(vc, pose, -width, 0, -width, r, g, b, a);
        vertex(vc, pose, -width, height, -width, r, g, b, a);
        vertex(vc, pose, -width, height, width, r, g, b, a);
        vertex(vc, pose, -width, 0, width, r, g, b, a);

        matrices.popPose();
    }

    private static void renderBox(WorldRenderContext context, HighlightTarget target, Vec3 cam, int color) {
        PoseStack matrices = context.matrices();
        matrices.pushPose();
        matrices.translate(
                target.pos().getX() - cam.x,
                target.pos().getY() - cam.y,
                target.pos().getZ() - cam.z
        );
        VertexConsumer vc = context.consumers().getBuffer(HighlightRenderLayer.XRAY_LAYER);
        HighlightGeometry.drawWireframeBox(matrices, vc, cam, target.pos(), color);
        matrices.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, a).setNormal(0, 1, 0);
    }
}
