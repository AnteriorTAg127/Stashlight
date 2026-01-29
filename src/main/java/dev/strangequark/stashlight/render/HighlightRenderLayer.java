package dev.strangequark.stashlight.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.mixin.RenderLayerInvoker;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class HighlightRenderLayer {
    public static final RenderPipeline XRAY_PIPELINE =
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                            .withLocation(Identifier.of(Stashlight.MOD_ID, "xray"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .build()
            );

    public static final RenderLayer XRAY_LAYER = RenderLayerInvoker.of(
            "chestfinder_xray_lines",
            RenderSetup.builder(XRAY_PIPELINE).build()
    );
}
