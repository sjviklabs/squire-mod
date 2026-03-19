package com.sjviklabs.squire.inventory;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nullable;

/**
 * Custom equipment slot for the squire menu. Mirrors vanilla ArmorSlot
 * (which is package-private) with proper item validation and empty slot icons.
 */
public class SquireEquipmentSlot extends Slot {

    private final LivingEntity owner;
    private final EquipmentSlot equipmentSlot;
    @Nullable
    private final ResourceLocation emptyIcon;

    public SquireEquipmentSlot(Container container, LivingEntity owner, EquipmentSlot equipmentSlot,
                               int slotIndex, int x, int y, @Nullable ResourceLocation emptyIcon) {
        super(container, slotIndex, x, y);
        this.owner = owner;
        this.equipmentSlot = equipmentSlot;
        this.emptyIcon = emptyIcon;
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        this.owner.onEquipItem(this.equipmentSlot, oldStack, newStack);
        super.setByPlayer(newStack, oldStack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.canEquip(this.equipmentSlot, this.owner);
    }

    @Override
    public boolean mayPickup(Player player) {
        ItemStack stack = this.getItem();
        if (!stack.isEmpty() && !player.isCreative()
                && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        }
        return super.mayPickup(player);
    }

    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        if (this.emptyIcon != null) {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, this.emptyIcon);
        }
        return super.getNoItemIcon();
    }
}
