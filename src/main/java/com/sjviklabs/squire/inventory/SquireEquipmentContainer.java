package com.sjviklabs.squire.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Thin wrapper that exposes a LivingEntity's 4 armor slots + offhand as a Container.
 * Slot mapping: 0=head, 1=chest, 2=legs, 3=feet, 4=offhand.
 * Used by SquireMenu so equipment slots can be added like any other Container slot.
 */
public class SquireEquipmentContainer implements Container {

    private static final EquipmentSlot[] SLOT_MAP = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    private final LivingEntity entity;

    public SquireEquipmentContainer(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public int getContainerSize() {
        return SLOT_MAP.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : SLOT_MAP) {
            if (!this.entity.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.entity.getItemBySlot(SLOT_MAP[index]);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack current = getItem(index);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack split = current.split(count);
        this.entity.setItemSlot(SLOT_MAP[index], current);
        setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack current = getItem(index);
        this.entity.setItemSlot(SLOT_MAP[index], ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.entity.setItemSlot(SLOT_MAP[index], stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        // Entity equipment is already tracked by the entity
    }

    @Override
    public boolean stillValid(Player player) {
        return this.entity.isAlive() && this.entity.distanceTo(player) < 8.0;
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot slot : SLOT_MAP) {
            this.entity.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    public static EquipmentSlot getEquipmentSlot(int containerIndex) {
        return SLOT_MAP[containerIndex];
    }
}
