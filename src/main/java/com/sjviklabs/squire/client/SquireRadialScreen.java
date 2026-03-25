package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.network.SquireCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * Radial command wheel for squire interaction.
 * Opens when the player presses the radial menu keybind near a squire.
 * 8 wedges around a center point, each mapped to a command ID.
 * Click a wedge or press R again to close.
 *
 * Angle convention: 0 = top (negative Y), increases clockwise.
 * Both hover detection and rendering use this same convention.
 */
public class SquireRadialScreen extends Screen {

    /** Wedge definitions: label key and command ID, ordered clockwise from top. */
    private static final WedgeEntry[] WEDGES = {
            new WedgeEntry("squire.radial.follow",    SquireCommandPayload.CMD_FOLLOW),
            new WedgeEntry("squire.radial.guard",     SquireCommandPayload.CMD_GUARD),
            new WedgeEntry("squire.radial.patrol",    SquireCommandPayload.CMD_PATROL),
            new WedgeEntry("squire.radial.stay",      SquireCommandPayload.CMD_STAY),
            new WedgeEntry("squire.radial.store",     SquireCommandPayload.CMD_STORE),
            new WedgeEntry("squire.radial.fetch",     SquireCommandPayload.CMD_FETCH),
            new WedgeEntry("squire.radial.mount",     SquireCommandPayload.CMD_MOUNT),
            new WedgeEntry("squire.radial.inventory", SquireCommandPayload.CMD_INVENTORY),
    };

    private static final int WEDGE_COUNT = WEDGES.length;
    private static final float WEDGE_ANGLE = (float) (2.0 * Math.PI / WEDGE_COUNT);

    /** Radii for the ring (in screen pixels). */
    private static final float INNER_RADIUS = 55.0F;
    private static final float OUTER_RADIUS = 130.0F;
    /** Dead zone in the center — no selection if mouse is within this radius. */
    private static final float DEAD_ZONE = 25.0F;

    private static final int SEGMENTS_PER_WEDGE = 20;

    // Colors (ARGB) — semi-transparent with strong contrast for readability
    private static final int COLOR_SCREEN_DIM = 0x60000000;
    private static final int COLOR_BG = 0xB01A1A2E;
    private static final int COLOR_HOVER = 0xC0D4AF37;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_HOVER = 0xFFFFFF00;
    private static final int COLOR_CENTER = 0xB0101020;
    private static final int COLOR_SEPARATOR = 0xA0FFFFFF;
    private static final int COLOR_RING_BORDER = 0xC0888888;

    private final int squireEntityId;
    private int hoveredWedge = -1;

    public SquireRadialScreen(SquireEntity squire) {
        super(Component.translatable("squire.radial.title"));
        this.squireEntityId = squire.getId();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Override to prevent Minecraft's default screen blur effect.
     * We draw our own lightweight dim in render() instead.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: skip the vanilla blurred background
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim the background
        graphics.fill(0, 0, this.width, this.height, COLOR_SCREEN_DIM);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Determine hovered wedge from mouse position
        float dx = mouseX - cx;
        float dy = mouseY - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < DEAD_ZONE) {
            hoveredWedge = -1;
        } else {
            // Angle from top (negative Y), clockwise. Range [0, 2*PI).
            float angle = (float) Math.atan2(dx, -dy);
            if (angle < 0) angle += (float) (2.0 * Math.PI);
            hoveredWedge = (int) (angle / WEDGE_ANGLE);
            if (hoveredWedge >= WEDGE_COUNT) hoveredWedge = 0;
        }

        // Render wedges — centered so wedge 0 straddles top center
        // Wedge i spans from (i - 0.5) * WEDGE_ANGLE to (i + 0.5) * WEDGE_ANGLE
        float halfWedge = WEDGE_ANGLE / 2.0F;
        for (int i = 0; i < WEDGE_COUNT; i++) {
            boolean hovered = (i == hoveredWedge);
            float startAngle = i * WEDGE_ANGLE - halfWedge;
            drawWedge(graphics, cx, cy, startAngle, startAngle + WEDGE_ANGLE,
                    INNER_RADIUS, OUTER_RADIUS, hovered ? COLOR_HOVER : COLOR_BG);
        }

        // Wedge separator lines
        for (int i = 0; i < WEDGE_COUNT; i++) {
            float angle = i * WEDGE_ANGLE - halfWedge;
            float sinA = (float) Math.sin(angle);
            float cosA = (float) Math.cos(angle);
            int x1 = (int) (cx + sinA * INNER_RADIUS);
            int y1 = (int) (cy - cosA * INNER_RADIUS);
            int x2 = (int) (cx + sinA * OUTER_RADIUS);
            int y2 = (int) (cy - cosA * OUTER_RADIUS);
            drawLine(graphics, x1, y1, x2, y2, COLOR_SEPARATOR);
        }

        // Center circle
        drawFilledCircle(graphics, cx, cy, INNER_RADIUS, COLOR_CENTER);

        // Ring border outlines (inner + outer edges) for crisp definition
        drawCircleOutline(graphics, cx, cy, INNER_RADIUS, COLOR_RING_BORDER);
        drawCircleOutline(graphics, cx, cy, OUTER_RADIUS, COLOR_RING_BORDER);

        // Wedge labels — placed at the midpoint angle of each wedge
        for (int i = 0; i < WEDGE_COUNT; i++) {
            boolean hovered = (i == hoveredWedge);
            float midAngle = i * WEDGE_ANGLE;
            float labelRadius = (INNER_RADIUS + OUTER_RADIUS) / 2.0F;
            float lx = cx + (float) Math.sin(midAngle) * labelRadius;
            float ly = cy - (float) Math.cos(midAngle) * labelRadius;

            String label = Component.translatable(WEDGES[i].langKey).getString();
            int textWidth = this.font.width(label);
            int textX = (int) (lx - textWidth / 2.0F);
            int textY = (int) (ly - this.font.lineHeight / 2.0F);

            graphics.drawString(this.font, label, textX, textY,
                    hovered ? COLOR_TEXT_HOVER : COLOR_TEXT, true);
        }

        // Center label shows hovered command name
        if (hoveredWedge >= 0) {
            String centerLabel = Component.translatable(WEDGES[hoveredWedge].langKey).getString();
            int tw = this.font.width(centerLabel);
            graphics.drawString(this.font, centerLabel,
                    cx - tw / 2, cy - this.font.lineHeight / 2, COLOR_TEXT_HOVER, true);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredWedge >= 0) {
            dispatchCommand(hoveredWedge);
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SquireKeybinds.RADIAL_MENU.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void dispatchCommand(int wedgeIndex) {
        if (wedgeIndex < 0 || wedgeIndex >= WEDGES.length) return;
        int commandId = WEDGES[wedgeIndex].commandId;
        PacketDistributor.sendToServer(new SquireCommandPayload(commandId, squireEntityId));
    }

    // ------------------------------------------------------------------
    // Rendering helpers
    // ------------------------------------------------------------------

    /**
     * Draw a filled wedge (arc segment) between two angles and two radii.
     * Angle convention: 0 = top, positive = clockwise.
     * Vertex positions: x = cx + sin(angle) * r, y = cy - cos(angle) * r.
     */
    private void drawWedge(GuiGraphics graphics, int cx, int cy,
                           float startAngle, float endAngle,
                           float innerR, float outerR, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= SEGMENTS_PER_WEDGE; i++) {
            float angle = startAngle + (endAngle - startAngle) * i / SEGMENTS_PER_WEDGE;
            float sinA = (float) Math.sin(angle);
            float cosA = (float) Math.cos(angle);

            buf.addVertex(matrix, cx + sinA * outerR, cy - cosA * outerR, 0.0F)
                    .setColor(r, g, b, a);
            buf.addVertex(matrix, cx + sinA * innerR, cy - cosA * innerR, 0.0F)
                    .setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /**
     * Draw a filled circle at (cx, cy) with the given radius and color.
     */
    private void drawFilledCircle(GuiGraphics graphics, int cx, int cy, float radius, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        int segments = 32;
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        buf.addVertex(matrix, cx, cy, 0.0F).setColor(r, g, b, a);

        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2.0 * Math.PI * i / segments);
            buf.addVertex(matrix, cx + (float) Math.sin(angle) * radius,
                    cy - (float) Math.cos(angle) * radius, 0.0F)
                    .setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /**
     * Draw a thin line between two points.
     */
    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        buf.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a);
        buf.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /**
     * Draw a circle outline (ring of line segments) at (cx, cy) with given radius.
     */
    private void drawCircleOutline(GuiGraphics graphics, int cx, int cy, float radius, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        int segments = 64;
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);
            buf.addVertex(matrix, cx + (float) Math.sin(angle1) * radius,
                    cy - (float) Math.cos(angle1) * radius, 0.0F).setColor(r, g, b, a);
            buf.addVertex(matrix, cx + (float) Math.sin(angle2) * radius,
                    cy - (float) Math.cos(angle2) * radius, 0.0F).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private record WedgeEntry(String langKey, int commandId) {}
}
