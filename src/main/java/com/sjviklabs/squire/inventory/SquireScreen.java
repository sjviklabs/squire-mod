package com.sjviklabs.squire.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * GUI screen for the squire inventory with equipment column and general inventory grid.
 * Rendered programmatically — no external texture file needed.
 *
 * Layout:
 *   Left column: 4 armor slots + offhand
 *   Right: 3x9 general inventory
 *   Bottom: player inventory + hotbar
 */
public class SquireScreen extends AbstractContainerScreen<SquireMenu> {

    // Colors
    private static final int BG_COLOR = 0xC0101010;
    private static final int BORDER_COLOR = 0xFF404040;
    private static final int SEPARATOR_COLOR = 0xFF606060;
    private static final int SLOT_OUTER = 0xFF373737;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int LABEL_COLOR = 0xFFE0E0E0;

    public SquireScreen(SquireMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 184;
        this.inventoryLabelY = 91;
        this.titleLabelX = 35;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Background panel
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BG_COLOR);

        // Border
        guiGraphics.fill(x, y, x + this.imageWidth, y + 1, BORDER_COLOR);
        guiGraphics.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, BORDER_COLOR);
        guiGraphics.fill(x, y, x + 1, y + this.imageHeight, BORDER_COLOR);
        guiGraphics.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, BORDER_COLOR);

        // Equipment column separator
        guiGraphics.fill(x + 28, y + 4, x + 29, y + 98, SEPARATOR_COLOR);

        // Section separator (squire inv / player inv)
        guiGraphics.fill(x + 4, y + 96, x + this.imageWidth - 4, y + 97, SEPARATOR_COLOR);

        // Equipment slots (left column)
        for (int i = 0; i < 4; i++) {
            drawSlotBg(guiGraphics, x + 6, y + 6 + i * 18);
        }
        drawSlotBg(guiGraphics, x + 6, y + 78); // offhand

        // Squire general inventory (3x9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(guiGraphics, x + 34 + col * 18, y + 6 + row * 18);
            }
        }

        // Player inventory (3x9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(guiGraphics, x + 7 + col * 18, y + 101 + row * 18);
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            drawSlotBg(guiGraphics, x + 7 + col * 18, y + 159);
        }
    }

    private void drawSlotBg(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + 18, y + 18, SLOT_OUTER);
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
