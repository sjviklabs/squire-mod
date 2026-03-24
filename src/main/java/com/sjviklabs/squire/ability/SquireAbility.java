package com.sjviklabs.squire.ability;

import com.sjviklabs.squire.config.SquireConfig;

import java.util.function.Supplier;

/**
 * Registry of all squire abilities. Each ability has:
 * - A unique ID and display name
 * - A category (COMBAT, DEFENSE, WORK, UTILITY)
 * - A config supplier for the unlock level (hot-reloadable)
 * - A description for tooltips/UI
 *
 * Adding a new ability:
 * 1. Add an enum entry here with its config supplier
 * 2. Add the config value in SquireConfig
 * 3. Use SquireAbility.isUnlocked(squire) at the call site
 * That's it. No other wiring needed.
 */
public enum SquireAbility {

    // ---- Combat abilities ----
    FIRE_RESISTANCE(Category.DEFENSE, "Fire Resistance",
            "Permanent fire resistance effect",
            () -> SquireConfig.abilityFireResLevel.get()),

    RANGED_COMBAT(Category.COMBAT, "Ranged Combat",
            "Can use bows for ranged attacks",
            () -> SquireConfig.abilityRangedLevel.get()),

    SHIELD_BLOCK(Category.DEFENSE, "Shield Block",
            "Actively blocks with shield against ranged attackers",
            () -> SquireConfig.abilityShieldLevel.get()),

    THORNS(Category.DEFENSE, "Thorns",
            "Reflects 20% of melee damage back to attacker",
            () -> SquireConfig.abilityThornsLevel.get()),

    LIFESTEAL(Category.COMBAT, "Lifesteal",
            "Heals 10% of melee damage dealt",
            () -> SquireConfig.abilityLifestealLevel.get()),

    UNDYING(Category.DEFENSE, "Undying",
            "Revives once on death at 50% HP (5-min cooldown)",
            () -> SquireConfig.abilityUndyingLevel.get()),

    // ---- Work abilities (Phase 3+) ----
    AUTO_TORCH(Category.WORK, "Auto-Torch",
            "Places torches in dark areas while following",
            () -> SquireConfig.abilityAutoTorchLevel.get()),

    VEIN_MINE(Category.WORK, "Vein Mining",
            "Mining an ore block mines the connected vein",
            () -> SquireConfig.abilityVeinMineLevel.get()),

    FORTUNE_SENSE(Category.WORK, "Fortune Sense",
            "Prioritizes fortune tools on ore blocks",
            () -> SquireConfig.abilityFortuneSenseLevel.get()),

    CHEST_DEPOSIT(Category.WORK, "Chest Deposit",
            "Can deposit inventory into chests",
            () -> SquireConfig.abilityChestDepositLevel.get()),

    TIRELESS(Category.WORK, "Tireless",
            "No mining speed decay during long area clears",
            () -> SquireConfig.abilityTirelessLevel.get());

    // ================================================================

    private final Category category;
    private final String displayName;
    private final String description;
    private final Supplier<Integer> unlockLevelSupplier;

    SquireAbility(Category category, String displayName, String description,
                  Supplier<Integer> unlockLevelSupplier) {
        this.category = category;
        this.displayName = displayName;
        this.description = description;
        this.unlockLevelSupplier = unlockLevelSupplier;
    }

    /** The level required to unlock this ability (reads from config). */
    public int getUnlockLevel() {
        return unlockLevelSupplier.get();
    }

    /** Check if a squire at the given level has this ability. */
    public boolean isUnlockedAt(int level) {
        return level >= getUnlockLevel();
    }

    public Category getCategory() { return category; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    // ================================================================

    public enum Category {
        COMBAT("Combat"),
        DEFENSE("Defense"),
        WORK("Work"),
        UTILITY("Utility");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
}
