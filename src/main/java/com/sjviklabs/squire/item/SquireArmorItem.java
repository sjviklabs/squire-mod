package com.sjviklabs.squire.item;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * Custom armor piece for the squire set. Between iron and diamond tier.
 * When a SquireEntity wears all 4 pieces, grants a set bonus (passive regen).
 * Players can wear for decent protection but no set bonus.
 */
public class SquireArmorItem extends ArmorItem {

    public SquireArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    /**
     * Check if the entity is wearing a full set of squire armor (all 4 slots).
     */
    public static boolean isFullSquireArmor(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof SquireArmorItem)) {
                return false;
            }
        }
        return true;
    }
}
