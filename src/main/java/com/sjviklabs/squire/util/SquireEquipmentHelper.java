package com.sjviklabs.squire.util;

import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.sjviklabs.squire.util.SquireAbilities;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.state.BlockState;
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

        // Bow (only equip if squire has ranged ability and arrows)
        if (newItem.getItem() instanceof BowItem) {
            if (SquireAbilities.hasRangedCombat(squire) && squire.hasArrows()) {
                ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
                if (!(currentMainhand.getItem() instanceof BowItem)) {
                    swapEquipment(squire, EquipmentSlot.MAINHAND, newItem);
                }
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
        // Prefer bow ONLY during active ranged combat. Otherwise best melee weapon.
        {
            boolean inRangedCombat = squire.getSquireAI() != null
                    && squire.getSquireAI().isInState(com.sjviklabs.squire.ai.statemachine.SquireAIState.COMBAT_RANGED);
            boolean preferBow = inRangedCombat
                    && SquireAbilities.hasRangedCombat(squire) && squire.hasArrows();
            ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);

            if (preferBow) {
                // Find a bow in inventory if not already holding one
                if (!(currentMainhand.getItem() instanceof BowItem)) {
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack candidate = inv.getItem(i);
                        if (!candidate.isEmpty() && candidate.getItem() instanceof BowItem && !isCursed(candidate)) {
                            swapEquipmentFromSlot(squire, EquipmentSlot.MAINHAND, inv, i);
                            break;
                        }
                    }
                }
            } else {
                // Standard melee weapon selection
                int bestIdx = -1;
                ItemStack bestWeapon = currentMainhand;

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
        }

        // --- Offhand shield ---
        // Stow shield when bow is in mainhand (can't use both). Equip shield otherwise.
        {
            ItemStack finalMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
            boolean holdingBow = finalMainhand.getItem() instanceof BowItem;
            ItemStack currentOffhand = squire.getItemBySlot(EquipmentSlot.OFFHAND);

            if (holdingBow && isShield(currentOffhand)) {
                // Stow shield into inventory while using bow
                squire.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                inv.addItem(currentOffhand);
            } else if (!holdingBow && !isShield(currentOffhand)) {
                // Not holding bow — equip a shield if available
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
    // Auto-crafting: basic gear from scavenged materials
    // ------------------------------------------------------------------

    /**
     * If the squire has no melee weapon (equipped or in inventory), attempt to craft
     * a wooden sword from 2 planks + 1 stick. If the squire has no shield (equipped or
     * in inventory), attempt to craft one from 6 planks + 1 iron ingot.
     * <p>
     * Called periodically alongside {@link #runFullEquipCheck}. Only consumes materials
     * when crafting succeeds. Crafted items go into inventory (equip check will pick
     * them up on the next cycle).
     */
    public static void tryCraftBasicGear(SquireEntity squire) {
        if (!com.sjviklabs.squire.config.SquireConfig.autoCraftEnabled.get()) return;

        SquireInventory inv = squire.getSquireInventory();

        // --- Craft a wooden sword if squire has no melee weapon ---
        if (!hasMeleeWeapon(squire, inv)) {
            int planksNeeded = 2;
            int sticksNeeded = 1;
            if (countPlanks(inv) >= planksNeeded && countItem(inv, Items.STICK) >= sticksNeeded) {
                consumePlanks(inv, planksNeeded);
                consumeItem(inv, Items.STICK, sticksNeeded);
                inv.addItem(new ItemStack(Items.WOODEN_SWORD));
                squire.playSound(SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.8F, 1.0F);
                var log = squire.getActivityLog();
                if (log != null) {
                    log.log("CRAFT", "Crafted a wooden sword from planks and sticks");
                }
            }
        }

        // --- Craft a bow if squire has ranged ability, no bow, and has arrows ---
        if (SquireAbilities.hasRangedCombat(squire) && !hasBow(squire, inv) && squire.hasArrows()) {
            int sticksNeeded = 3;
            int stringNeeded = 3;
            if (countItem(inv, Items.STICK) >= sticksNeeded && countItem(inv, Items.STRING) >= stringNeeded) {
                consumeItem(inv, Items.STICK, sticksNeeded);
                consumeItem(inv, Items.STRING, stringNeeded);
                inv.addItem(new ItemStack(Items.BOW));
                squire.playSound(SoundEvents.VILLAGER_WORK_FLETCHER, 0.8F, 1.0F);
                var log = squire.getActivityLog();
                if (log != null) {
                    log.log("CRAFT", "Crafted a bow from sticks and string");
                }
            }
        }

        // --- Craft arrows if squire has a bow but few arrows ---
        if (hasBow(squire, inv) && countItem(inv, Items.ARROW) < 8) {
            if (countItem(inv, Items.FLINT) >= 1
                    && countItem(inv, Items.STICK) >= 1
                    && countItem(inv, Items.FEATHER) >= 1) {
                consumeItem(inv, Items.FLINT, 1);
                consumeItem(inv, Items.STICK, 1);
                consumeItem(inv, Items.FEATHER, 1);
                inv.addItem(new ItemStack(Items.ARROW, 4));
                squire.playSound(SoundEvents.VILLAGER_WORK_FLETCHER, 0.6F, 1.2F);
                var log = squire.getActivityLog();
                if (log != null) {
                    log.log("CRAFT", "Crafted 4 arrows from flint, sticks, and feathers");
                }
            }
        }

        // --- Craft a shield if squire has no shield ---
        if (!hasShieldAnywhere(squire, inv)) {
            int planksNeeded = 6;
            int ironNeeded = 1;
            if (countPlanks(inv) >= planksNeeded && countItem(inv, Items.IRON_INGOT) >= ironNeeded) {
                consumePlanks(inv, planksNeeded);
                consumeItem(inv, Items.IRON_INGOT, ironNeeded);
                inv.addItem(new ItemStack(Items.SHIELD));
                squire.playSound(SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.8F, 1.0F);
                var log = squire.getActivityLog();
                if (log != null) {
                    log.log("CRAFT", "Crafted a shield from planks and iron");
                }
            }
        }
    }

    /** Check if squire has a bow equipped or in inventory. */
    private static boolean hasBow(SquireEntity squire, SquireInventory inv) {
        if (squire.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem) return true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof BowItem) return true;
        }
        return false;
    }

    /** Check if squire has any melee weapon (SwordItem or AxeItem) equipped or in inventory. */
    private static boolean hasMeleeWeapon(SquireEntity squire, SquireInventory inv) {
        ItemStack mainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
        if (mainhand.getItem() instanceof SwordItem || mainhand.getItem() instanceof AxeItem) return true;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) return true;
        }
        return false;
    }

    /** Check if squire has any shield equipped or in inventory. */
    private static boolean hasShieldAnywhere(SquireEntity squire, SquireInventory inv) {
        if (isShield(squire.getItemBySlot(EquipmentSlot.OFFHAND))) return true;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isShield(inv.getItem(i))) return true;
        }
        return false;
    }

    /** Count total planks (any wood type) in inventory using the ItemTag. */
    private static int countPlanks(SquireInventory inv) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.PLANKS)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Count total of a specific item in inventory. */
    private static int countItem(SquireInventory inv, Item item) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Consume N planks (any wood type) from inventory. */
    private static void consumePlanks(SquireInventory inv, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.PLANKS)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                remaining -= take;
            }
        }
    }

    /** Consume N of a specific item from inventory. */
    private static void consumeItem(SquireInventory inv, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                remaining -= take;
            }
        }
    }

    /**
     * Force-switch from ranged to melee loadout: stow bow, equip best melee weapon,
     * equip shield. Called directly when transitioning out of COMBAT_RANGED, bypassing
     * the state-based logic in runFullEquipCheck (which would still see COMBAT_RANGED
     * since the state machine hasn't ticked yet).
     */
    public static void switchToMeleeLoadout(SquireEntity squire) {
        SquireInventory inv = squire.getSquireInventory();

        // --- Stow bow, equip best melee weapon ---
        ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
        if (currentMainhand.getItem() instanceof BowItem) {
            // Put bow back in inventory
            squire.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            inv.addItem(currentMainhand);
        }

        // Find best melee weapon in inventory
        ItemStack bestWeapon = squire.getItemBySlot(EquipmentSlot.MAINHAND);
        int bestIdx = -1;
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

        // --- Equip shield ---
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

    // ------------------------------------------------------------------
    // Tool selection for mining
    // ------------------------------------------------------------------

    /**
     * Scan inventory for the tool with the highest destroy speed against the given
     * block state. If a better tool is found, swap it into mainhand. If no tool
     * is effective, leaves current mainhand unchanged.
     */
    public static void selectBestTool(SquireEntity squire, BlockState blockState) {
        SquireInventory inv = squire.getSquireInventory();
        ItemStack currentMainhand = squire.getItemBySlot(EquipmentSlot.MAINHAND);
        float bestSpeed = currentMainhand.getDestroySpeed(blockState);
        int bestIdx = -1;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            float speed = candidate.getDestroySpeed(blockState);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestIdx = i;
            }
        }

        if (bestIdx >= 0) {
            swapEquipmentFromSlot(squire, EquipmentSlot.MAINHAND, inv, bestIdx);
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
        playEquipSound(squire, slot);

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
        playEquipSound(squire, slot);

        if (!old.isEmpty()) {
            inv.addItem(old);
        }
    }

    /**
     * Play an appropriate equip sound based on the equipment slot type.
     * Armor slots get the armor equip sound, weapon/offhand get a generic equip sound.
     */
    private static void playEquipSound(SquireEntity squire, EquipmentSlot slot) {
        switch (slot) {
            case HEAD, CHEST, LEGS, FEET ->
                    squire.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 1.0F);
            case MAINHAND, OFFHAND ->
                    squire.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.0F);
        }
    }
}
