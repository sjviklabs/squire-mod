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
 * Squire inventory menu with equipment slots (4 armor + offhand + mainhand) and 27-slot general inventory.
 *
 * Layout (196x202 GUI):
 *   Left column (x=7): helmet, chestplate, leggings, boots (y=7,25,43,61), offhand (y=79), mainhand (y=97)
 *   Right area (x=35): 3 rows x 9 cols general inventory (y=7)
 *   Bottom: player inventory (y=122) + hotbar (y=180)
 */
public class SquireMenu extends AbstractContainerMenu {

    private static final int SQUIRE_INV_SIZE = 27;
    private static final int EQUIP_SLOT_COUNT = 6; // 4 armor + offhand + mainhand
    private static final int TOTAL_SQUIRE_SLOTS = SQUIRE_INV_SIZE + EQUIP_SLOT_COUNT;

    private final Container squireInventory;
    private final Container equipmentContainer;
    private final int squireEntityId;

    // Client constructor (from network) — no entity available, use dummy containers
    public SquireMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(SQUIRE_INV_SIZE),
                new SimpleContainer(EQUIP_SLOT_COUNT),
                null,
                buf.readVarInt());
    }

    // Server constructor (called from SquireEntity.mobInteract)
    public SquireMenu(int containerId, Inventory playerInventory, Container squireInventory,
                      Container equipContainer, SquireEntity squire, int squireEntityId) {
        super(ModMenuTypes.SQUIRE_MENU.get(), containerId);
        this.squireInventory = squireInventory;
        this.equipmentContainer = equipContainer != null ? equipContainer : new SimpleContainer(EQUIP_SLOT_COUNT);
        this.squireEntityId = squireEntityId;
        checkContainerSize(squireInventory, SQUIRE_INV_SIZE);

        // --- Equipment slots (left column, menu indices 0-4) ---
        EquipmentSlot[] armorOrder = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        ResourceLocation[] armorIcons = {
                InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
                InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
                InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS
        };

        for (int i = 0; i < 4; i++) {
            if (squire != null) {
                this.addSlot(new SquireEquipmentSlot(
                        this.equipmentContainer, squire, armorOrder[i],
                        i, 7, 7 + i * 18, armorIcons[i]));
            } else {
                this.addSlot(new Slot(this.equipmentContainer, i, 7, 7 + i * 18));
            }
        }

        // Offhand/shield slot (menu index 4)
        if (squire != null) {
            this.addSlot(new SquireEquipmentSlot(
                    this.equipmentContainer, squire, EquipmentSlot.OFFHAND,
                    4, 7, 79, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));
        } else {
            this.addSlot(new Slot(this.equipmentContainer, 4, 7, 79));
        }

        // Mainhand/weapon slot (menu index 5) — below offhand
        if (squire != null) {
            this.addSlot(new SquireEquipmentSlot(
                    this.equipmentContainer, squire, EquipmentSlot.MAINHAND,
                    5, 7, 97, null));
        } else {
            this.addSlot(new Slot(this.equipmentContainer, 5, 7, 97));
        }

        // --- Squire general inventory (3 rows x 9, menu indices 6-32) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(squireInventory, col + row * 9, 35 + col * 18, 7 + row * 18));
            }
        }

        // --- Player inventory (3 rows x 9, menu indices 33-59) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 122 + row * 18));
            }
        }

        // --- Player hotbar (menu indices 60-68) ---
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 180));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < TOTAL_SQUIRE_SLOTS) {
            // Move from squire (equipment or inventory) to player
            if (!this.moveItemStackTo(stack, TOTAL_SQUIRE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Move from player — try equipment slots first, then general inventory
            if (!this.moveItemStackTo(stack, 0, EQUIP_SLOT_COUNT, false)) {
                if (!this.moveItemStackTo(stack, EQUIP_SLOT_COUNT, TOTAL_SQUIRE_SLOTS, false)) {
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
