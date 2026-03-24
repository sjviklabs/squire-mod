package com.sjviklabs.squire.util;

import com.sjviklabs.squire.ability.AbilityHelper;
import com.sjviklabs.squire.ability.SquireAbility;
import com.sjviklabs.squire.entity.SquireEntity;

/**
 * Static helpers for checking level-gated abilities.
 *
 * These methods delegate to {@link AbilityHelper} and {@link SquireAbility}
 * for backward compatibility — existing call sites don't need to change.
 * New code should use {@code AbilityHelper.has(squire, SquireAbility.X)} directly.
 */
public final class SquireAbilities {

    private SquireAbilities() {}

    // ---- Combat / Defense ability checks (delegates to registry) ----

    public static boolean hasFireResistance(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.FIRE_RESISTANCE);
    }

    public static boolean hasRangedCombat(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.RANGED_COMBAT);
    }

    public static boolean hasShieldBlock(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.SHIELD_BLOCK);
    }

    public static boolean hasThorns(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.THORNS);
    }

    public static boolean hasLifesteal(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.LIFESTEAL);
    }

    public static boolean hasUndying(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.UNDYING);
    }

    // ---- Work ability checks ----

    public static boolean hasAutoTorch(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.AUTO_TORCH);
    }

    public static boolean hasVeinMine(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.VEIN_MINE);
    }

    public static boolean hasFortuneSense(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.FORTUNE_SENSE);
    }

    public static boolean hasChestDeposit(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.CHEST_DEPOSIT);
    }

    public static boolean hasTireless(SquireEntity squire) {
        return AbilityHelper.has(squire, SquireAbility.TIRELESS);
    }

    // ---- XP / level math (not ability-specific) ----

    /**
     * Scaling XP curve: level = floor(sqrt(totalXP / baseXpPerLevel))
     * Lv1=100, Lv2=400, Lv3=900, ..., Lv30=90000
     */
    public static int calculateLevel(int totalXP, int baseXpPerLevel, int maxLevel) {
        if (totalXP <= 0 || baseXpPerLevel <= 0) return 0;
        int level = (int) Math.floor(Math.sqrt((double) totalXP / baseXpPerLevel));
        return Math.min(level, maxLevel);
    }

    /**
     * Total XP required to reach a given level.
     */
    public static int xpForLevel(int level, int baseXpPerLevel) {
        return level * level * baseXpPerLevel;
    }
}
