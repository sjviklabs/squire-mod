package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom RenderType for squire UI overlays (health bar, level text).
 * Replaces debugQuads() which is not intended for production use.
 */
public final class SquireRenderTypes extends RenderStateShard {

    private SquireRenderTypes() {
        super("squire_render_types", () -> {}, () -> {});
    }

    public static final RenderType HEALTH_BAR = RenderType.create(
            "squire_health_bar",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );
}
