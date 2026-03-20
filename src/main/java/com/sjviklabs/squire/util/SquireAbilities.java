package com.sjviklabs.squire.util;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;

/**
 * Static helpers for checking level-gated abilities.
 * Each method reads the squire's current level and compares to config threshold.
 */
public final class SquireAbilities {

    private SquireAbilities() {}

    public static boolean hasFireResistance(SquireEntity squire) {
        return squire.getSquireLevel() >= SquireConfig.abilityFireResLevel.get();
    }

    public static boolean hasRangedCombat(SquireEntity squire) {
        return squire.getSquireLevel() >= SquireConfig.abilityRangedLevel.get();
    }

    public static boolean hasShieldBlock(SquireEntity squire) {
        return squire.getSquireLevel() >= SquireConfig.abilityShieldLevel.get();
    }

    public static boolean hasThorns(SquireEntity squire) {
        return squire.getSquireLevel() >= SquireConfig.abilityThornsLevel.get();
    }

    public static boolean hasLifesteal(SquireEntity squire) {
        return squire.getSquireLevel() >= SquireConfig.abilityLifestealLevel.get();
    }

    public static boolean hasUndying(SquireEntity squire) {
        return squire.getSquireLevel() >= SquireConfig.abilityUndyingLevel.get();
    }

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
