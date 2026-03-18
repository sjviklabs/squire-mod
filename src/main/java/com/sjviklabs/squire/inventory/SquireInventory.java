package com.sjviklabs.squire.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SquireInventory implements Container {
    private final ItemStack[] items;
    private final int size;

    public SquireInventory(int size) {
        this.size = size;
        this.items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            this.items[i] = ItemStack.EMPTY;
        }
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.size ? this.items[slot] : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= 0 && slot < this.size && !this.items[slot].isEmpty() && amount > 0) {
            ItemStack split = this.items[slot].split(amount);
            if (this.items[slot].isEmpty()) {
                this.items[slot] = ItemStack.EMPTY;
            }
            this.setChanged();
            return split;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= 0 && slot < this.size) {
            ItemStack stack = this.items[slot];
            this.items[slot] = ItemStack.EMPTY;
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.size) {
            this.items[slot] = stack;
            if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
                stack.setCount(this.getMaxStackSize());
            }
            this.setChanged();
        }
    }

    @Override
    public void setChanged() {
        // No-op for entity inventory
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < this.size; i++) {
            this.items[i] = ItemStack.EMPTY;
        }
    }

    public boolean canAddItem(ItemStack stack) {
        for (ItemStack existing : this.items) {
            if (existing.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public ItemStack addItem(ItemStack stack) {
        ItemStack remaining = stack.copy();

        // First pass: try to merge with existing stacks
        for (int i = 0; i < this.size && !remaining.isEmpty(); i++) {
            if (ItemStack.isSameItemSameComponents(this.items[i], remaining)) {
                int space = this.items[i].getMaxStackSize() - this.items[i].getCount();
                int toAdd = Math.min(remaining.getCount(), space);
                if (toAdd > 0) {
                    this.items[i].grow(toAdd);
                    remaining.shrink(toAdd);
                }
            }
        }

        // Second pass: find empty slots
        for (int i = 0; i < this.size && !remaining.isEmpty(); i++) {
            if (this.items[i].isEmpty()) {
                this.items[i] = remaining.copy();
                remaining = ItemStack.EMPTY;
            }
        }

        this.setChanged();
        return remaining;
    }

    public ListTag toTag(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < this.size; i++) {
            if (!this.items[i].isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                list.add(this.items[i].save(registries, itemTag));
            }
        }
        return list;
    }

    public void fromTag(ListTag list, HolderLookup.Provider registries) {
        this.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < this.size) {
                this.items[slot] = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
            }
        }
    }

    public void dropAll(Level level, BlockPos pos) {
        for (int i = 0; i < this.size; i++) {
            if (!this.items[i].isEmpty()) {
                level.addFreshEntity(new ItemEntity(
                        level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        this.items[i].copy()
                ));
                this.items[i] = ItemStack.EMPTY;
            }
        }
    }
}
