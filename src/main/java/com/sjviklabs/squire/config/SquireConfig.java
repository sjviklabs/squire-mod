package com.sjviklabs.squire.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Squire mod configuration. Registered as COMMON (shared between client and server).
 *
 * Usage: SquireConfig.maxSquiresPerPlayer.getAsInt()
 *        SquireConfig.followStartDistance.getAsDouble()
 */
public final class SquireConfig {

    public static final ModConfigSpec SPEC;

    // --- Debug / testing ---
    public static final ModConfigSpec.BooleanValue godMode;
    public static final ModConfigSpec.BooleanValue activityLogging;

    // --- Per-player limits ---
    public static final ModConfigSpec.IntValue maxSquiresPerPlayer;

    // --- Movement / following ---
    public static final ModConfigSpec.DoubleValue followStartDistance;
    public static final ModConfigSpec.DoubleValue followStopDistance;
    public static final ModConfigSpec.DoubleValue sprintDistance;

    // --- Combat ---
    public static final ModConfigSpec.DoubleValue combatLeashDistance;
    public static final ModConfigSpec.DoubleValue aggroRange;

    // --- Items ---
    public static final ModConfigSpec.DoubleValue itemPickupRange;

    // --- Survival ---
    public static final ModConfigSpec.DoubleValue eatHealthThreshold;
    public static final ModConfigSpec.DoubleValue baseHealth;
    public static final ModConfigSpec.DoubleValue naturalRegenRate;

    // --- Mining ---
    public static final ModConfigSpec.DoubleValue mineReach;
    public static final ModConfigSpec.DoubleValue breakSpeedMultiplier;
    public static final ModConfigSpec.DoubleValue miningSpeedPerLevel;
    public static final ModConfigSpec.IntValue maxClearVolume;

    // --- Placing ---
    public static final ModConfigSpec.DoubleValue placeReach;

    // --- Progression ---
    public static final ModConfigSpec.IntValue xpPerKill;
    public static final ModConfigSpec.IntValue xpPerBlock;
    public static final ModConfigSpec.IntValue xpPerLevel;
    public static final ModConfigSpec.IntValue maxLevel;
    public static final ModConfigSpec.DoubleValue healthPerLevel;
    public static final ModConfigSpec.DoubleValue damagePerLevel;
    public static final ModConfigSpec.DoubleValue speedPerLevel;

    // --- Ability unlock levels ---
    // Combat/Defense
    public static final ModConfigSpec.IntValue abilityFireResLevel;
    public static final ModConfigSpec.IntValue abilityRangedLevel;
    public static final ModConfigSpec.IntValue abilityShieldLevel;
    public static final ModConfigSpec.IntValue abilityThornsLevel;
    public static final ModConfigSpec.IntValue abilityLifestealLevel;
    public static final ModConfigSpec.IntValue abilityUndyingLevel;
    // Work
    public static final ModConfigSpec.IntValue abilityAutoTorchLevel;
    public static final ModConfigSpec.IntValue abilityVeinMineLevel;
    public static final ModConfigSpec.IntValue abilityFortuneSenseLevel;
    public static final ModConfigSpec.IntValue abilityChestDepositLevel;
    public static final ModConfigSpec.IntValue abilityTirelessLevel;

    // --- Ranged combat ---
    public static final ModConfigSpec.DoubleValue rangedOptimalRange;
    public static final ModConfigSpec.IntValue rangedCooldownTicks;
    public static final ModConfigSpec.DoubleValue rangedInaccuracy;

    // --- Tick intervals ---
    public static final ModConfigSpec.IntValue itemScanInterval;
    public static final ModConfigSpec.IntValue equipCheckInterval;
    public static final ModConfigSpec.IntValue pathRecalcInterval;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // ---- Debug / testing ----
        builder.push("debug");
        godMode = builder
                .comment("When true, squires cannot die — health is clamped at 1 HP minimum. For testing.")
                .define("godMode", false);
        activityLogging = builder
                .comment("When true, squire AI logs state transitions and actions to logs/latest.log. Use /squire log to view in-game.")
                .define("activityLogging", true);
        builder.pop();

        // ---- Per-player limits ----
        builder.push("limits");
        maxSquiresPerPlayer = builder
                .comment("Maximum number of squires a single player can own.")
                .defineInRange("maxSquiresPerPlayer", 1, 1, 5);
        builder.pop();

        // ---- Movement / following ----
        builder.push("movement");
        followStartDistance = builder
                .comment("Distance (blocks) at which the squire begins following its owner.")
                .defineInRange("followStartDistance", 8.0, 2.0, 32.0);
        followStopDistance = builder
                .comment("Distance (blocks) at which the squire stops approaching its owner.")
                .defineInRange("followStopDistance", 4.0, 1.0, 8.0);
        sprintDistance = builder
                .comment("Distance (blocks) beyond which the squire sprints to catch up.")
                .defineInRange("sprintDistance", 12.0, 6.0, 48.0);
        builder.pop();

        // ---- Combat ----
        builder.push("combat");
        combatLeashDistance = builder
                .comment("Maximum distance (blocks) a squire will chase a target before giving up.")
                .defineInRange("combatLeashDistance", 16.0, 8.0, 64.0);
        aggroRange = builder
                .comment("Range (blocks) within which the squire detects hostile mobs.")
                .defineInRange("aggroRange", 12.0, 4.0, 32.0);
        builder.pop();

        // ---- Items ----
        builder.push("items");
        itemPickupRange = builder
                .comment("Range (blocks) within which the squire picks up item entities.")
                .defineInRange("itemPickupRange", 8.0, 2.0, 16.0);
        builder.pop();

        // ---- Survival ----
        builder.push("survival");
        eatHealthThreshold = builder
                .comment("Fraction of max HP below which the squire will eat food from inventory.")
                .defineInRange("eatHealthThreshold", 0.5, 0.1, 0.9);
        baseHealth = builder
                .comment("Base max health (HP) for squires. Vanilla player is 20.")
                .defineInRange("baseHealth", 20.0, 10.0, 100.0);
        naturalRegenRate = builder
                .comment("HP per second regenerated when food saturation is above 90%.")
                .defineInRange("naturalRegenRate", 0.5, 0.0, 5.0);
        builder.pop();

        // ---- Mining ----
        builder.push("mining");
        mineReach = builder
                .comment("Maximum distance (blocks) at which the squire can mine a block.")
                .defineInRange("mineReach", 4.0, 2.0, 8.0);
        breakSpeedMultiplier = builder
                .comment("Multiplier for block break speed. 1.0 = same as player.")
                .defineInRange("breakSpeedMultiplier", 1.0, 0.1, 3.0);
        miningSpeedPerLevel = builder
                .comment("Bonus mining speed per squire level. 0.0167 = +50% at Lv30. Set to 0 to disable.")
                .defineInRange("miningSpeedPerLevel", 0.0167, 0.0, 0.1);
        maxClearVolume = builder
                .comment("Maximum number of blocks allowed in a /squire clear command. Prevents accidental massive clears.")
                .defineInRange("maxClearVolume", 32768, 1, 1000000);
        builder.pop();

        // ---- Placing ----
        builder.push("placing");
        placeReach = builder
                .comment("Maximum distance (blocks) at which the squire can place a block.")
                .defineInRange("placeReach", 4.0, 2.0, 8.0);
        builder.pop();

        // ---- Progression ----
        builder.push("progression");
        xpPerKill = builder
                .comment("XP gained per mob kill.")
                .defineInRange("xpPerKill", 10, 1, 100);
        xpPerBlock = builder
                .comment("XP gained per block mined.")
                .defineInRange("xpPerBlock", 1, 1, 50);
        xpPerLevel = builder
                .comment("Base XP for scaling curve. Level = floor(sqrt(totalXP / base)). Lv1=100, Lv2=400, Lv10=10000.")
                .defineInRange("xpPerLevel", 100, 50, 1000);
        maxLevel = builder
                .comment("Maximum squire level.")
                .defineInRange("maxLevel", 30, 1, 50);
        healthPerLevel = builder
                .comment("Bonus max HP per level.")
                .defineInRange("healthPerLevel", 1.0, 0.0, 10.0);
        damagePerLevel = builder
                .comment("Bonus attack damage per level.")
                .defineInRange("damagePerLevel", 0.3, 0.0, 5.0);
        speedPerLevel = builder
                .comment("Bonus movement speed per level.")
                .defineInRange("speedPerLevel", 0.003, 0.0, 0.05);
        builder.pop();

        // ---- Ability unlock levels ----
        builder.push("abilities");
        abilityFireResLevel = builder
                .comment("Level at which squire gains permanent fire resistance.")
                .defineInRange("fireResistanceLevel", 5, 1, 50);
        abilityRangedLevel = builder
                .comment("Level at which squire can use bows for ranged combat.")
                .defineInRange("rangedCombatLevel", 10, 1, 50);
        abilityShieldLevel = builder
                .comment("Level at which squire actively blocks with shield.")
                .defineInRange("shieldBlockLevel", 15, 1, 50);
        abilityThornsLevel = builder
                .comment("Level at which squire reflects 20% melee damage back to attacker.")
                .defineInRange("thornsLevel", 20, 1, 50);
        abilityLifestealLevel = builder
                .comment("Level at which squire heals 10% of melee damage dealt.")
                .defineInRange("lifestealLevel", 25, 1, 50);
        abilityUndyingLevel = builder
                .comment("Level at which squire revives once on death (5-min cooldown).")
                .defineInRange("undyingLevel", 30, 1, 50);
        // Work abilities
        abilityAutoTorchLevel = builder
                .comment("Level at which squire places torches in dark areas while following.")
                .defineInRange("autoTorchLevel", 5, 1, 50);
        abilityVeinMineLevel = builder
                .comment("Level at which mining an ore block mines the connected vein.")
                .defineInRange("veinMineLevel", 15, 1, 50);
        abilityFortuneSenseLevel = builder
                .comment("Level at which squire prioritizes fortune tools on ore blocks.")
                .defineInRange("fortuneSenseLevel", 25, 1, 50);
        abilityChestDepositLevel = builder
                .comment("Level at which squire can deposit items into chests.")
                .defineInRange("chestDepositLevel", 20, 1, 50);
        abilityTirelessLevel = builder
                .comment("Level at which squire has no mining speed decay during long area clears.")
                .defineInRange("tirelessLevel", 30, 1, 50);
        builder.pop();

        // ---- Ranged combat ----
        builder.push("ranged");
        rangedOptimalRange = builder
                .comment("Optimal distance (blocks) for ranged attacks. Squire approaches/retreats to this range.")
                .defineInRange("optimalRange", 15.0, 6.0, 30.0);
        rangedCooldownTicks = builder
                .comment("Ticks between ranged attacks (20 ticks = 1 second).")
                .defineInRange("cooldownTicks", 30, 10, 100);
        rangedInaccuracy = builder
                .comment("Arrow inaccuracy. Lower = more accurate. Skeleton uses ~10 on easy, 2 on hard.")
                .defineInRange("inaccuracy", 6.0, 0.0, 20.0);
        builder.pop();

        // ---- Tick intervals ----
        builder.push("intervals");
        itemScanInterval = builder
                .comment("Ticks between item entity scans for pickup.")
                .defineInRange("itemScanInterval", 40, 10, 200);
        equipCheckInterval = builder
                .comment("Ticks between automatic equipment upgrade checks.")
                .defineInRange("equipCheckInterval", 60, 20, 200);
        pathRecalcInterval = builder
                .comment("Ticks between path recalculation during follow.")
                .defineInRange("pathRecalcInterval", 10, 1, 40);
        builder.pop();

        SPEC = builder.build();
    }

    private SquireConfig() {
        // No instances
    }
}
