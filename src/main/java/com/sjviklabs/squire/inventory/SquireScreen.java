package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.SquireMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class SquireScreen extends AbstractContainerScreen<SquireMenu> {
    private static final ResourceLocation CHEST_GUI =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public SquireScreen(SquireMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 168; // 3 rows + player inventory
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // Draw using the chest texture (top half for 3 rows)
        guiGraphics.blit(CHEST_GUI, x, y, 0, 0, this.imageWidth, 3 * 18 + 17);
        // Draw player inventory section
        guiGraphics.blit(CHEST_GUI, x, y + 3 * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
