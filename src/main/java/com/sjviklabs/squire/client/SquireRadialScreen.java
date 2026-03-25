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
 * Opens when the player holds the radial menu keybind while looking at a squire.
 * 8 wedges around a center point, each mapped to a command ID.
 * Releasing the key (or clicking) on a wedge sends a SquireCommandPayload to the server.
 */
public class SquireRadialScreen extends Screen {

    /** Wedge definitions: label key and command ID, ordered clockwise from top. */    private static final WedgeEntry[] WEDGES = {
            new WedgeEntry("squire.radial.follow",   SquireCommandPayload.CMD_FOLLOW),
            new WedgeEntry("squire.radial.guard",    SquireCommandPayload.CMD_GUARD),
            new WedgeEntry("squire.radial.patrol",   SquireCommandPayload.CMD_PATROL),
            new WedgeEntry("squire.radial.stay",     SquireCommandPayload.CMD_STAY),
            new WedgeEntry("squire.radial.store",    SquireCommandPayload.CMD_STORE),
            new WedgeEntry("squire.radial.fetch",    SquireCommandPayload.CMD_FETCH),
            new WedgeEntry("squire.radial.mount",    SquireCommandPayload.CMD_MOUNT),
            new WedgeEntry("squire.radial.inventory", SquireCommandPayload.CMD_INVENTORY),
    };

    private static final int WEDGE_COUNT = WEDGES.length;
    private static final float WEDGE_ANGLE = (float) (2.0 * Math.PI / WEDGE_COUNT);

    /** Radii for the ring (in screen pixels). */
    private static final float INNER_RADIUS = 40.0F;
    private static final float OUTER_RADIUS = 100.0F;
    /** Dead zone in the center — no selection if mouse is within this radius. */
    private static final float DEAD_ZONE = 20.0F;

    private static final int SEGMENTS_PER_WEDGE = 20; // arc smoothness

    // Colors (ARGB)
    private static final int COLOR_BG = 0x80202020;       // dark translucent
    private static final int COLOR_HOVER = 0xB0D4AF37;    // gold highlight
    private static final int COLOR_BORDER = 0xC0FFFFFF;    // white border lines
    private static final int COLOR_TEXT = 0xFFFFFFFF;      // white text
    private static final int COLOR_TEXT_HOVER = 0xFFFFFF00; // yellow text on hover
    private static final int COLOR_CENTER = 0x60404040;    // center circle
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Calculate center
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Determine hovered wedge from mouse position
        float dx = mouseX - cx;
        float dy = mouseY - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < DEAD_ZONE) {
            hoveredWedge = -1;
        } else {
            // Angle from top (negative Y), clockwise
            float angle = (float) Math.atan2(dx, -dy);
            if (angle < 0) angle += (float) (2.0 * Math.PI);
            hoveredWedge = (int) (angle / WEDGE_ANGLE);
            if (hoveredWedge >= WEDGE_COUNT) hoveredWedge = 0;
        }
        // Render wedges
        for (int i = 0; i < WEDGE_COUNT; i++) {
            boolean hovered = (i == hoveredWedge);
            float startAngle = i * WEDGE_ANGLE - (float) Math.PI; // offset so 0 = top
            drawWedge(graphics, cx, cy, startAngle, startAngle + WEDGE_ANGLE,
                    INNER_RADIUS, OUTER_RADIUS, hovered ? COLOR_HOVER : COLOR_BG);
        }

        // Center circle
        drawFilledCircle(graphics, cx, cy, INNER_RADIUS, COLOR_CENTER);

        // Wedge labels
        for (int i = 0; i < WEDGE_COUNT; i++) {
            boolean hovered = (i == hoveredWedge);
            float midAngle = i * WEDGE_ANGLE - (float) Math.PI + WEDGE_ANGLE / 2.0F;
            float labelRadius = (INNER_RADIUS + OUTER_RADIUS) / 2.0F;
            float lx = cx + (float) Math.sin(midAngle) * labelRadius;
            float ly = cy - (float) Math.cos(midAngle) * labelRadius;

            String label = Component.translatable(WEDGES[i].langKey).getString();
            int textWidth = this.font.width(label);
            graphics.drawString(this.font, label,
                    (int) (lx - textWidth / 2.0F), (int) (ly - this.font.lineHeight / 2.0F),
                    hovered ? COLOR_TEXT_HOVER : COLOR_TEXT, true);
        }

        // Center label
        String centerLabel = hoveredWedge >= 0
                ? Component.translatable(WEDGES[hoveredWedge].langKey).getString()
                : "";
        if (!centerLabel.isEmpty()) {
            int tw = this.font.width(centerLabel);
            graphics.drawString(this.font, centerLabel,
                    cx - tw / 2, cy - this.font.lineHeight / 2, COLOR_TEXT_HOVER, true);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredWedge >= 0) {
            dispatchCommand(hoveredWedge);
            this.onClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // Close and dispatch when the radial keybind is released
        if (SquireKeybinds.RADIAL_MENU.matches(keyCode, scanCode)) {
            if (hoveredWedge >= 0) {
                dispatchCommand(hoveredWedge);
            }
            this.onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
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
     * Uses triangle strip from inner arc to outer arc.
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
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= SEGMENTS_PER_WEDGE; i++) {
            float angle = startAngle + (endAngle - startAngle) * i / SEGMENTS_PER_WEDGE;
            float sinA = (float) Math.sin(angle);
            float cosA = (float) Math.cos(angle);

            // Outer vertex
            buf.addVertex(matrix, cx + sinA * outerR, cy - cosA * outerR, 0.0F)
                    .setColor(r, g, b, a);
            // Inner vertex
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
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        // Center vertex
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

    // ------------------------------------------------------------------
    // Wedge definition record
    // ------------------------------------------------------------------

    private record WedgeEntry(String langKey, int commandId) {}
}