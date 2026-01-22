package strangequark.chestfinder.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import strangequark.chestfinder.ChestFinder;

public class HighlightRenderLayer {
    public static final RenderPipeline XRAY_PIPELINE =
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                            .withLocation(Identifier.of(ChestFinder.MOD_ID, "xray"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .build()
            );

    public static final RenderLayer XRAY_LAYER =
            RenderLayer.MultiPhase.of(
                    "chestfinder_xray",
                    256,
                    false,
                    true,
                    XRAY_PIPELINE,
                    RenderLayer.MultiPhaseParameters.builder().build(false)
            );
}
