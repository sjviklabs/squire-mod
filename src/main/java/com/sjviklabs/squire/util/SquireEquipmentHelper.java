package com.sjviklabs.squire.util;

import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.tags.EnchantmentTags;

import javax.annotation.Nullable;

/**
 * Static utility class for comparing equipment quality and auto-equipping
 * the best armor, weapon, and shield from a squire's inventory.
 */
public final class SquireEquipmentHelper {

    private SquireEquipmentHelper() {}

    // ------------------------------------------------------------------
    // Public entry points
    // ------------------------------------------------------------------

    /**
     * Check whether {@code newItem} is an upgrade over the squire's current equipment
     * in the relevant slot. If yes, swap: old equipment goes back to inventory,
     * new item is equipped.
     */
    public static void tryAutoEquip(SquireEntity squire, ItemStack newItem) {
        if (newItem.isEmpty() || isCursed(newItem)) return;

        // Armor
        EquipmentSlot armorSlot = getArmorSlot(newItem);
        if (armorSlot != null) {
            ItemStack current = squire.getItemBySlot(armorSlot);
            if (isBetterArmor(newItem, current, armorSlot)) {
                swapEquipment(squire, armorSlot, newItem);
            }
            return;
        }

        // Shield
        if (isShield(newItem)) {
            ItemStack currentOffhand = squire.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!isShield(currentOffhand) || isCursed(currentOffhand)) {
                swapEquipment(squire, EquipmentSlot.OFFHAND, newItem);
            }
            return;
        }

        // Weapon (sword or axe)
        if (newItem.getItem() instanceof SwordItem || newItem.getItem() instanceof AxeItem) {
            ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
            if (isBetterWeapon(newItem, currentMainhand)) {
                swapEquipment(squire, EquipmentSlot.MAINHAND, newItem);
            }
        }
    }

    /**
     * Full inventory scan: equip the best armor in each slot, best weapon in
     * mainhand, and a shield in offhand. Called periodically on a tick interval.
     */
    public static void runFullEquipCheck(SquireEntity squire) {
        SquireInventory inv = squire.getSquireInventory();

        // --- Armor slots ---
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            int bestIdx = -1;
            ItemStack bestStack = squire.getItemBySlot(slot);

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack candidate = inv.getItem(i);
                if (candidate.isEmpty()) continue;
                EquipmentSlot candidateSlot = getArmorSlot(candidate);
                if (candidateSlot != slot) continue;
                if (isCursed(candidate)) continue;
                if (isBetterArmor(candidate, bestStack, slot)) {
                    bestStack = candidate;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                swapEquipmentFromSlot(squire, slot, inv, bestIdx);
            }
        }

        // --- Mainhand weapon ---
        {
            int bestIdx = -1;
            ItemStack bestWeapon = squire.getItemBySlot(EquipmentSlot.MAINHAND);

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack candidate = inv.getItem(i);
                if (candidate.isEmpty()) continue;
                if (!(candidate.getItem() instanceof SwordItem) && !(candidate.getItem() instanceof AxeItem)) continue;
                if (isCursed(candidate)) continue;
                if (isBetterWeapon(candidate, bestWeapon)) {
                    bestWeapon = candidate;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                swapEquipmentFromSlot(squire, EquipmentSlot.MAINHAND, inv, bestIdx);
            }
        }

        // --- Offhand shield ---
        {
            ItemStack currentOffhand = squire.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!isShield(currentOffhand)) {
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack candidate = inv.getItem(i);
                    if (isShield(candidate) && !isCursed(candidate)) {
                        swapEquipmentFromSlot(squire, EquipmentSlot.OFFHAND, inv, i);
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Comparison helpers
    // ------------------------------------------------------------------

    /**
     * Compare armor by combined defense + toughness. Cursed candidates are rejected.
     */
    public static boolean isBetterArmor(ItemStack candidate, ItemStack current, EquipmentSlot slot) {
        if (candidate.isEmpty() || isCursed(candidate)) return false;
        if (!(candidate.getItem() instanceof ArmorItem candidateArmor)) return false;
        if (candidateArmor.getEquipmentSlot() != slot) return false;

        double candidateScore = candidateArmor.getDefense() + candidateArmor.getToughness();

        if (current.isEmpty() || !(current.getItem() instanceof ArmorItem currentArmor)) {
            return true; // Anything beats empty
        }

        double currentScore = currentArmor.getDefense() + currentArmor.getToughness();
        return candidateScore > currentScore;
    }

    /**
     * Compare weapons by attack damage attribute. At equal damage, prefer SwordItem
     * over AxeItem (swords swing faster). Cursed candidates are rejected.
     */
    public static boolean isBetterWeapon(ItemStack candidate, ItemStack current) {
        if (candidate.isEmpty() || isCursed(candidate)) return false;

        double candidateDmg = getAttackDamage(candidate);
        double currentDmg = getAttackDamage(current);

        if (candidateDmg > currentDmg) return true;
        if (candidateDmg == currentDmg) {
            // Prefer sword over axe at equal damage (faster attack speed)
            boolean candidateIsSword = candidate.getItem() instanceof SwordItem;
            boolean currentIsSword = current.getItem() instanceof SwordItem;
            return candidateIsSword && !currentIsSword;
        }
        return false;
    }

    /**
     * @return true if the item is a ShieldItem
     */
    public static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShieldItem;
    }

    /**
     * @return the EquipmentSlot for an ArmorItem, or null if not armor
     */
    @Nullable
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot();
        }
        return null;
    }

    /**
     * Check for Curse of Binding or Curse of Vanishing via the
     * {@link EnchantmentTags#CURSE} tag.
     */
    public static boolean isCursed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(EnchantmentTags.CURSE)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Extract the total attack damage from an item's attribute modifiers.
     * Returns 0 if no attack damage modifier is found (or item is empty).
     */
    private static double getAttackDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        ItemAttributeModifiers modifiers = stack.getAttributeModifiers();
        double[] total = {0.0};
        modifiers.forEach(EquipmentSlot.MAINHAND, (Holder<Attribute> attr, AttributeModifier mod) -> {
            if (attr.equals(Attributes.ATTACK_DAMAGE)) {
                if (mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    total[0] += mod.amount();
                }
            }
        });
        return total[0];
    }

    /**
     * Swap: unequip current item in slot (put it in inventory), equip newItem
     * by removing it from inventory and setting it in the equipment slot.
     * Used by {@link #tryAutoEquip} for items just picked up.
     */
    private static void swapEquipment(SquireEntity squire, EquipmentSlot slot, ItemStack newItem) {
        SquireInventory inv = squire.getSquireInventory();
        ItemStack old = squire.getItemBySlot(slot);

        // Find the new item in inventory and remove it
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == newItem) {
                inv.removeItemNoUpdate(i);
                break;
            }
        }

        // Equip the new item
        squire.setItemSlot(slot, newItem.copy());

        // Return old item to inventory
        if (!old.isEmpty()) {
            inv.addItem(old);
        }
    }

    /**
     * Swap equipment using a known inventory slot index. Used by
     * {@link #runFullEquipCheck} where we already know which slot has the best item.
     */
    private static void swapEquipmentFromSlot(SquireEntity squire, EquipmentSlot slot, SquireInventory inv, int invIdx) {
        ItemStack old = squire.getItemBySlot(slot);
        ItemStack newItem = inv.removeItemNoUpdate(invIdx);

        squire.setItemSlot(slot, newItem);

        if (!old.isEmpty()) {
            inv.addItem(old);
        }
    }
}
