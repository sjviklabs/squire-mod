package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.ability.AbilityHelper;
import com.sjviklabs.squire.ability.SquireAbility;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.util.SquireAbilities;
import com.sjviklabs.squire.config.SquireConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;

/**
 * Squire inventory screen — player-style layout with entity preview,
 * stats header, level-gated backpack rows, and locked-row visuals.
 *
 * Mirrors the vanilla player inventory feel so players don't have to
 * relearn a new UI. Armor left, weapons right, backpack center,
 * player inventory at the bottom in the standard position.
 */
public class SquireScreen extends AbstractContainerScreen<SquireMenu> {

    // Colors
    private static final int BG_COLOR = 0xC0101010;
    private static final int BG_HEADER = 0xC0181818;
    private static final int BORDER_COLOR = 0xFF404040;
    private static final int SEPARATOR_COLOR = 0xFF606060;
    private static final int SLOT_OUTER = 0xFF373737;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int SLOT_LOCKED_OUTER = 0xFF2A2A2A;
    private static final int SLOT_LOCKED_INNER = 0xFF4A4A4A;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int LABEL_DIM = 0xFF808080;
    private static final int HP_BAR_BG = 0xFF3A1010;
    private static final int HP_BAR_FG = 0xFFCC3333;
    private static final int XP_BAR_BG = 0xFF103A10;
    private static final int XP_BAR_FG = 0xFF33CC33;
    private static final int LOCK_X_COLOR = 0xFF5A5A5A;

    // Layout constants matching SquireMenu
    private static final int ENTITY_AREA_WIDTH = 51;
    private static final int ARMOR_COL_X = ENTITY_AREA_WIDTH + 1;
    private static final int BACKPACK_X = ARMOR_COL_X + 22;
    private static final int WEAPON_COL_X = BACKPACK_X + 9 * 18 + 4;
    private static final int BACKPACK_Y = 18;
    private static final int PLAYER_INV_X = 30;
    private static final int STATS_HEIGHT = 14;

    public SquireScreen(SquireMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = menu.getGuiWidth();
        this.imageHeight = menu.getGuiHeight();
        this.inventoryLabelY = menu.getPlayerInvY() - 11;
        this.inventoryLabelX = PLAYER_INV_X;
        this.titleLabelX = ENTITY_AREA_WIDTH + 2;
        this.titleLabelY = 5;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Main background
        g.fill(x, y, x + imageWidth, y + imageHeight, BG_COLOR);

        // Header background (stats area)
        g.fill(x, y, x + imageWidth, y + BACKPACK_Y, BG_HEADER);

        // Border
        drawBorder(g, x, y, imageWidth, imageHeight);

        // Header separator
        g.fill(x + 2, y + BACKPACK_Y - 1, x + imageWidth - 2, y + BACKPACK_Y, SEPARATOR_COLOR);

        // Entity preview area background
        g.fill(x + 2, y + BACKPACK_Y, x + ENTITY_AREA_WIDTH - 1, y + BACKPACK_Y + 4 * 18, 0x40000000);

        // Entity preview separator (vertical line)
        g.fill(x + ENTITY_AREA_WIDTH - 1, y + BACKPACK_Y, x + ENTITY_AREA_WIDTH, y + BACKPACK_Y + 4 * 18, SEPARATOR_COLOR);

        // Render squire entity in the preview area
        renderEntityPreview(g, x, y, mouseX, mouseY);

        // Armor slots (left column)
        for (int i = 0; i < 4; i++) {
            drawSlotBg(g, x + ARMOR_COL_X, y + BACKPACK_Y + i * 18);
        }

        // Weapon slots (right column)
        drawSlotBg(g, x + WEAPON_COL_X, y + BACKPACK_Y);      // mainhand
        drawSlotBg(g, x + WEAPON_COL_X, y + BACKPACK_Y + 18); // offhand

        // Backpack slots — active rows
        int activeRows = SquireMenu.rowsForTier(menu.getBackpackTier());
        for (int row = 0; row < activeRows; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(g, x + BACKPACK_X + col * 18, y + BACKPACK_Y + row * 18);
            }
        }

        // Backpack slots — locked rows (greyed out with X)
        for (int row = activeRows; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                drawLockedSlot(g, x + BACKPACK_X + col * 18, y + BACKPACK_Y + row * 18);
            }
        }

        // Locked row level labels
        int[] tierLevels = {0, 10, 20, 30};
        for (int row = activeRows; row < 4; row++) {
            String lockLabel = "Lv." + tierLevels[row];
            int labelX = x + BACKPACK_X + (9 * 18) / 2 - font.width(lockLabel) / 2;
            int labelY = y + BACKPACK_Y + row * 18 + 5;
            g.drawString(font, lockLabel, labelX, labelY, LOCK_X_COLOR, false);
        }

        // Section separator (squire inv / player inv)
        int sepY = y + menu.getPlayerInvY() - 12;
        g.fill(x + 4, sepY, x + imageWidth - 4, sepY + 1, SEPARATOR_COLOR);

        // Player inventory (3x9)
        int playerInvY = y + menu.getPlayerInvY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBg(g, x + PLAYER_INV_X + col * 18, playerInvY + row * 18);
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            drawSlotBg(g, x + PLAYER_INV_X + col * 18, playerInvY + 58);
        }

        // Stats in header
        renderStats(g, x, y);
    }

    private void renderEntityPreview(GuiGraphics g, int guiX, int guiY, int mouseX, int mouseY) {
        LivingEntity entity = getSquireEntity();
        if (entity == null) return;

        int centerX = guiX + ENTITY_AREA_WIDTH / 2;
        int entityY = guiY + BACKPACK_Y + 4 * 18 - 4;
        int scale = 28;

        // Use vanilla's entity renderer — same as player inventory screen
        float lookX = (float) (centerX - mouseX);
        float lookY = (float) (entityY - 40 - mouseY);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, centerX - 20, guiY + BACKPACK_Y + 2, centerX + 20, entityY,
                scale, 0.0625F, lookX, lookY, entity);
    }

    private void renderStats(GuiGraphics g, int guiX, int guiY) {
        int statsX = guiX + ENTITY_AREA_WIDTH + 2;
        int statsY = guiY + 4;
        int barWidth = 80;
        int barHeight = 5;

        // Level
        int level = menu.getSquireLevel();
        String levelStr = "Lv." + level;

        // HP bar
        float hpFraction = menu.getHealthMax() > 0 ? menu.getHealthCurrent() / menu.getHealthMax() : 0;
        int hpBarX = statsX + font.width(levelStr) + 6;
        g.fill(hpBarX, statsY + 1, hpBarX + barWidth, statsY + 1 + barHeight, HP_BAR_BG);
        g.fill(hpBarX, statsY + 1, hpBarX + (int)(barWidth * hpFraction), statsY + 1 + barHeight, HP_BAR_FG);
        String hpStr = String.format("%.0f/%.0f", menu.getHealthCurrent(), menu.getHealthMax());
        g.drawString(font, hpStr, hpBarX + barWidth + 3, statsY, LABEL_COLOR, false);

        // XP bar
        int xpBarX = hpBarX + barWidth + font.width(hpStr) + 10;
        int nextLevelXP = SquireAbilities.xpForLevel(level + 1, SquireConfig.xpPerLevel.get());
        int currentLevelXP = SquireAbilities.xpForLevel(level, SquireConfig.xpPerLevel.get());
        float xpFraction = (nextLevelXP > currentLevelXP)
                ? (float)(menu.getTotalXP() - currentLevelXP) / (nextLevelXP - currentLevelXP) : 1.0f;
        xpFraction = Math.max(0, Math.min(1, xpFraction));
        g.fill(xpBarX, statsY + 1, xpBarX + barWidth, statsY + 1 + barHeight, XP_BAR_BG);
        g.fill(xpBarX, statsY + 1, xpBarX + (int)(barWidth * xpFraction), statsY + 1 + barHeight, XP_BAR_FG);
        String xpStr = menu.getTotalXP() + "/" + nextLevelXP + " XP";
        g.drawString(font, xpStr, xpBarX + barWidth + 3, statsY, LABEL_DIM, false);

        // Mode
        String modeStr = SquireEntity.modeName(menu.getSquireMode());
        g.drawString(font, modeStr, guiX + imageWidth - font.width(modeStr) - 6, statsY, LABEL_DIM, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Level as title
        String title = "Lv." + menu.getSquireLevel() + " Squire";
        g.drawString(this.font, title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);

        // Player inventory label
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);

        // Backpack tier label
        String[] tierNames = {"Satchel", "Pack", "Knapsack", "War Chest"};
        String tierName = tierNames[menu.getBackpackTier()];
        int tierLabelX = BACKPACK_X + (9 * 18) / 2 - font.width(tierName) / 2;
        g.drawString(this.font, tierName, tierLabelX, BACKPACK_Y - 10, LABEL_DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    // --- Helpers ---

    private void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_OUTER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
    }

    private void drawLockedSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_LOCKED_OUTER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_LOCKED_INNER);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, BORDER_COLOR);
        g.fill(x, y + h - 1, x + w, y + h, BORDER_COLOR);
        g.fill(x, y, x + 1, y + h, BORDER_COLOR);
        g.fill(x + w - 1, y, x + w, y + h, BORDER_COLOR);
    }

    @Nullable
    private LivingEntity getSquireEntity() {
        if (this.minecraft != null && this.minecraft.level != null) {
            var entity = this.minecraft.level.getEntity(menu.getSquireEntityId());
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }
}
