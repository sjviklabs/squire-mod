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

    // --- Tick intervals ---
    public static final ModConfigSpec.IntValue itemScanInterval;
    public static final ModConfigSpec.IntValue equipCheckInterval;
    public static final ModConfigSpec.IntValue pathRecalcInterval;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
                .defineInRange("followStartDistance", 6.0, 2.0, 32.0);
        followStopDistance = builder
                .comment("Distance (blocks) at which the squire stops approaching its owner.")
                .defineInRange("followStopDistance", 2.0, 1.0, 8.0);
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
