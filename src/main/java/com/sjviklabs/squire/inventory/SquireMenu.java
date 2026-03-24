package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Squire inventory menu — player-style layout with level-gated backpack.
 *
 * Layout (left to right):
 *   Entity preview area (51px wide, no slots)
 *   Armor column: helmet, chest, legs, boots (4 slots)
 *   Backpack grid: 1-4 rows x 9 cols (9/18/27/36 slots based on level)
 *   Weapon column: mainhand, offhand (2 slots)
 *   Bottom: standard player inventory (27 + 9 hotbar)
 *
 * Backpack tiers:
 *   0 (Lv 1-9):   Satchel   — 9 slots  (1 row)
 *   1 (Lv 10-19): Pack      — 18 slots (2 rows)
 *   2 (Lv 20-29): Knapsack  — 27 slots (3 rows)
 *   3 (Lv 30):    War Chest — 36 slots (4 rows)
 */
public class SquireMenu extends AbstractContainerMenu {

    private static final int EQUIP_SLOT_COUNT = 6; // 4 armor + offhand + mainhand

    // Layout constants
    private static final int ENTITY_AREA_WIDTH = 51;
    private static final int ARMOR_COL_X = ENTITY_AREA_WIDTH + 1;   // 52
    private static final int BACKPACK_X = ARMOR_COL_X + 22;          // 74
    private static final int WEAPON_COL_X = BACKPACK_X + 9 * 18 + 4; // 240
    private static final int BACKPACK_Y = 18;
    private static final int PLAYER_INV_X = 30;

    private final Container squireInventory;
    private final Container equipmentContainer;
    private final int squireEntityId;
    private final int backpackTier;
    private final int backpackSlots;
    private final int squireLevel;
    private final int totalXP;
    private final float healthCurrent;
    private final float healthMax;
    private final byte squireMode;

    /** Calculate backpack tier from squire level. */
    public static int tierFromLevel(int level) {
        if (level >= 30) return 3;
        if (level >= 20) return 2;
        if (level >= 10) return 1;
        return 0;
    }

    /** Number of backpack slots for a given tier. */
    public static int slotsForTier(int tier) {
        return (tier + 1) * 9;
    }

    /** Number of backpack rows for a given tier. */
    public static int rowsForTier(int tier) {
        return tier + 1;
    }

    // Client constructor (from network)
    public SquireMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                null,  // squireInventory — created from tier below
                null,  // equipContainer
                null,  // squire entity
                buf.readVarInt(),  // entityId
                buf.readVarInt(),  // level
                buf.readVarInt(),  // totalXP
                buf.readFloat(),   // health
                buf.readFloat(),   // maxHealth
                buf.readByte());   // mode
    }

    // Internal constructor shared by client and server paths
    private SquireMenu(int containerId, Inventory playerInventory,
                       Container squireInventory, Container equipContainer,
                       SquireEntity squire, int squireEntityId,
                       int level, int totalXP, float health, float maxHealth, byte mode) {
        super(ModMenuTypes.SQUIRE_MENU.get(), containerId);
        this.squireEntityId = squireEntityId;
        this.squireLevel = level;
        this.totalXP = totalXP;
        this.healthCurrent = health;
        this.healthMax = maxHealth;
        this.squireMode = mode;
        this.backpackTier = tierFromLevel(level);
        this.backpackSlots = slotsForTier(backpackTier);

        // Create containers if not provided (client side)
        this.squireInventory = squireInventory != null ? squireInventory : new SimpleContainer(backpackSlots);
        this.equipmentContainer = equipContainer != null ? equipContainer : new SimpleContainer(EQUIP_SLOT_COUNT);

        addEquipmentSlots(squire);
        addBackpackSlots();
        addPlayerInventory(playerInventory);
    }

    // Server constructor (called from SquireEntity.mobInteract)
    public SquireMenu(int containerId, Inventory playerInventory, Container squireInventory,
                      Container equipContainer, SquireEntity squire, int squireEntityId) {
        this(containerId, playerInventory, squireInventory, equipContainer, squire, squireEntityId,
                squire != null ? squire.getSquireLevel() : 0,
                squire != null ? squire.getProgression().getTotalXP() : 0,
                squire != null ? squire.getHealth() : 20f,
                squire != null ? squire.getMaxHealth() : 20f,
                squire != null ? squire.getSquireMode() : 0);
    }

    // --- Slot setup ---

    private void addEquipmentSlots(SquireEntity squire) {
        EquipmentSlot[] armorOrder = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        ResourceLocation[] armorIcons = {
                InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
                InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
                InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS
        };

        // Armor column (left of backpack)
        for (int i = 0; i < 4; i++) {
            int slotY = BACKPACK_Y + i * 18;
            if (squire != null) {
                this.addSlot(new SquireEquipmentSlot(
                        this.equipmentContainer, squire, armorOrder[i],
                        i, ARMOR_COL_X, slotY, armorIcons[i]));
            } else {
                this.addSlot(new Slot(this.equipmentContainer, i, ARMOR_COL_X, slotY));
            }
        }

        // Mainhand weapon (right of backpack, top)
        if (squire != null) {
            this.addSlot(new SquireEquipmentSlot(
                    this.equipmentContainer, squire, EquipmentSlot.MAINHAND,
                    5, WEAPON_COL_X, BACKPACK_Y, null));
        } else {
            this.addSlot(new Slot(this.equipmentContainer, 5, WEAPON_COL_X, BACKPACK_Y));
        }

        // Offhand/shield (right of backpack, below mainhand)
        if (squire != null) {
            this.addSlot(new SquireEquipmentSlot(
                    this.equipmentContainer, squire, EquipmentSlot.OFFHAND,
                    4, WEAPON_COL_X, BACKPACK_Y + 18, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));
        } else {
            this.addSlot(new Slot(this.equipmentContainer, 4, WEAPON_COL_X, BACKPACK_Y + 18));
        }
    }

    private void addBackpackSlots() {
        int rows = rowsForTier(backpackTier);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = col + row * 9;
                if (idx < squireInventory.getContainerSize()) {
                    this.addSlot(new Slot(squireInventory, idx, BACKPACK_X + col * 18, BACKPACK_Y + row * 18));
                }
            }
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        int playerInvY = getPlayerInvY();

        // Player inventory (3 rows x 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18, playerInvY + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    PLAYER_INV_X + col * 18, playerInvY + 58));
        }
    }

    // --- Layout helpers ---

    /** Y position where player inventory starts, adjusts based on backpack tier. */
    public int getPlayerInvY() {
        // 4 rows max for backpack, always show space for 4 rows + gap
        return BACKPACK_Y + 4 * 18 + 14;
    }

    /** Total GUI height. */
    public int getGuiHeight() {
        return getPlayerInvY() + 3 * 18 + 4 + 18 + 4; // player inv + gap + hotbar + bottom pad
    }

    /** Total GUI width. */
    public int getGuiWidth() {
        return WEAPON_COL_X + 18 + 8; // weapon col + slot width + right pad
    }

    // --- Accessors for screen rendering ---

    public int getBackpackTier() { return backpackTier; }
    public int getBackpackSlots() { return backpackSlots; }
    public int getSquireLevel() { return squireLevel; }
    public int getTotalXP() { return totalXP; }
    public float getHealthCurrent() { return healthCurrent; }
    public float getHealthMax() { return healthMax; }
    public byte getSquireMode() { return squireMode; }
    public int getSquireEntityId() { return squireEntityId; }

    // --- Shift-click transfer ---

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int totalSquireSlots = EQUIP_SLOT_COUNT + backpackSlots;

        if (index < totalSquireSlots) {
            // Move from squire to player
            if (!this.moveItemStackTo(stack, totalSquireSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Move from player — try equipment first, then backpack
            if (!this.moveItemStackTo(stack, 0, EQUIP_SLOT_COUNT, false)) {
                if (!this.moveItemStackTo(stack, EQUIP_SLOT_COUNT, totalSquireSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.squireInventory.stillValid(player);
    }
}
