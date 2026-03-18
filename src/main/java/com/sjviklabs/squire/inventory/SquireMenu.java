package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SquireMenu extends AbstractContainerMenu {
    private static final int SQUIRE_INV_SIZE = 27;
    private final Container squireInventory;
    private final int squireEntityId;

    // Client constructor (from network)
    public SquireMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(SQUIRE_INV_SIZE), buf.readVarInt());
    }

    // Server constructor
    public SquireMenu(int containerId, Inventory playerInventory, Container squireInventory, int squireEntityId) {
        super(ModMenuTypes.SQUIRE_MENU.get(), containerId);
        this.squireInventory = squireInventory;
        this.squireEntityId = squireEntityId;
        checkContainerSize(squireInventory, SQUIRE_INV_SIZE);

        // Squire inventory slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(squireInventory, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < SQUIRE_INV_SIZE) {
                // Move from squire to player
                if (!this.moveItemStackTo(stack, SQUIRE_INV_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Move from player to squire
                if (!this.moveItemStackTo(stack, 0, SQUIRE_INV_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.squireInventory.stillValid(player);
    }
}
