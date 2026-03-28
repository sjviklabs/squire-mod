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

    // --- Chat lines ---
    public static final ModConfigSpec.BooleanValue chatLinesEnabled;

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
    public static final ModConfigSpec.IntValue clearConfirmThreshold;

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

    // --- Auto-torch ---
    public static final ModConfigSpec.IntValue torchLightThreshold;
    public static final ModConfigSpec.IntValue torchCooldownTicks;

    // --- Mount ---
    public static final ModConfigSpec.DoubleValue horseSearchRange;
    public static final ModConfigSpec.BooleanValue autoMountEnabled;

    // --- Chest interaction ---
    public static final ModConfigSpec.DoubleValue chestSearchRange;

    // --- Patrol ---
    public static final ModConfigSpec.IntValue patrolDefaultWait;
    public static final ModConfigSpec.IntValue patrolMaxRouteLength;

    // --- Combat tactics ---
    public static final ModConfigSpec.IntValue shieldBlockDuration;
    public static final ModConfigSpec.IntValue circleStrafeDuration;
    public static final ModConfigSpec.IntValue hitAndRunRetreatTicks;
    public static final ModConfigSpec.DoubleValue evasiveDodgeInterval;

    // --- Ranged combat ---
    public static final ModConfigSpec.DoubleValue rangedOptimalRange;
    public static final ModConfigSpec.IntValue rangedCooldownTicks;
    public static final ModConfigSpec.DoubleValue rangedInaccuracy;

    // --- MineColonies integration ---
    public static final ModConfigSpec.BooleanValue minecoloniesIntegration;
    public static final ModConfigSpec.DoubleValue minecoloniesRaidAggroMultiplier;

    // --- Auto-crafting ---
    public static final ModConfigSpec.BooleanValue autoCraftEnabled;

    // --- Tick intervals ---
    public static final ModConfigSpec.IntValue itemScanInterval;
    public static final ModConfigSpec.IntValue equipCheckInterval;
    public static final ModConfigSpec.IntValue pathRecalcInterval;

    // --- Danger avoidance ---
    public static final ModConfigSpec.DoubleValue dangerFleeRange;
    public static final ModConfigSpec.DoubleValue dangerProactiveRange;
    public static final ModConfigSpec.IntValue dangerFleeTicks;
    public static final ModConfigSpec.IntValue dangerScanInterval;

    // --- Halberd sweep ---
    public static final ModConfigSpec.DoubleValue sweepDamageMultiplier;
    public static final ModConfigSpec.IntValue sweepCooldownTicks;
    public static final ModConfigSpec.DoubleValue sweepRange;
    public static final ModConfigSpec.IntValue sweepMaxTargets;

    // --- Visual progression ---
    public static final ModConfigSpec.BooleanValue enableVisualProgression;

    // --- Farming ---
    public static final ModConfigSpec.DoubleValue farmReach;
    public static final ModConfigSpec.IntValue farmTicksPerBlock;
    public static final ModConfigSpec.IntValue farmScanInterval;
    public static final ModConfigSpec.IntValue farmMaxArea;

    // --- Fishing ---
    public static final ModConfigSpec.DoubleValue waterSearchRange;
    public static final ModConfigSpec.IntValue fishingCatchInterval;
    public static final ModConfigSpec.IntValue fishingRodDurabilityPerCatch;

    // --- Task queue ---
    public static final ModConfigSpec.IntValue maxQueueLength;

    // --- Feature toggles ---
    public static final ModConfigSpec.BooleanValue enableCombat;
    public static final ModConfigSpec.BooleanValue enableMining;
    public static final ModConfigSpec.BooleanValue enableProgression;
    public static final ModConfigSpec.BooleanValue enablePersonality;
    public static final ModConfigSpec.BooleanValue enableBackpack;
    public static final ModConfigSpec.BooleanValue enableCustomArmor;
    public static final ModConfigSpec.BooleanValue enableMounting;
    public static final ModConfigSpec.BooleanValue enablePatrol;
    public static final ModConfigSpec.BooleanValue enableChestInteraction;
    public static final ModConfigSpec.BooleanValue enableFarming;
    public static final ModConfigSpec.BooleanValue enableFishing;
    public static final ModConfigSpec.BooleanValue enableTaskQueue;
    public static final ModConfigSpec.BooleanValue enableDangerAvoidance;
    public static final ModConfigSpec.BooleanValue classicMode;

    // --- New XP sources ---
    public static final ModConfigSpec.IntValue xpPerHarvest;
    public static final ModConfigSpec.IntValue xpPerFish;
    public static final ModConfigSpec.IntValue xpPerPatrolLoop;
    public static final ModConfigSpec.IntValue xpPerQueuedTask;
    public static final ModConfigSpec.IntValue xpPerPlace;
    public static final ModConfigSpec.IntValue xpPerChop;

    // --- Death penalty ---
    public static final ModConfigSpec.BooleanValue deathXPPenalty;
    public static final ModConfigSpec.DoubleValue deathXPPenaltyPercent;

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

        // ---- Chat lines ----
        builder.push("chat");
        chatLinesEnabled = builder
                .comment("When true, squires say contextual flavor text (combat, mining, idle). Owner-only visibility.")
                .define("chatLinesEnabled", true);
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
                .comment("Maximum number of blocks allowed in a /squire mine area command. Prevents accidental massive clears.")
                .defineInRange("maxClearVolume", 32768, 1, 1000000);
        clearConfirmThreshold = builder
                .comment("Area clears above this block count require /squire mine confirm. Below this, clearing starts immediately.")
                .defineInRange("clearConfirmThreshold", 500, 1, 100000);
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
        xpPerHarvest = builder
                .comment("XP gained per crop harvested.")
                .defineInRange("xpPerHarvest", 2, 1, 50);
        xpPerFish = builder
                .comment("XP gained per fish caught.")
                .defineInRange("xpPerFish", 3, 1, 50);
        xpPerPatrolLoop = builder
                .comment("XP gained per patrol loop completed.")
                .defineInRange("xpPerPatrolLoop", 5, 1, 100);
        xpPerQueuedTask = builder
                .comment("XP gained per queued task completed.")
                .defineInRange("xpPerQueuedTask", 2, 1, 50);
        xpPerPlace = builder
                .comment("XP gained per block placed.")
                .defineInRange("xpPerPlace", 1, 1, 50);
        xpPerChop = builder
                .comment("XP gained per log block chopped.")
                .defineInRange("xpPerChop", 1, 1, 50);
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

        // ---- Auto-torch ----
        builder.push("autoTorch");
        torchLightThreshold = builder
                .comment("Squire places a torch when block light at feet is at or below this value. 7 = hostile mob spawn threshold.")
                .defineInRange("lightThreshold", 7, 0, 15);
        torchCooldownTicks = builder
                .comment("Minimum ticks between torch placements (prevents spam). 60 = 3 seconds.")
                .defineInRange("cooldownTicks", 60, 20, 200);
        builder.pop();

        // ---- Combat tactics ----
        builder.push("combatTactics");
        shieldBlockDuration = builder
                .comment("Ticks to hold shield up after reactive block trigger (AGGRESSIVE/DEFAULT).")
                .defineInRange("shieldBlockDuration", 10, 5, 40);
        circleStrafeDuration = builder
                .comment("Base ticks for circle-strafe movement after landing a hit (AGGRESSIVE).")
                .defineInRange("circleStrafeDuration", 12, 5, 30);
        hitAndRunRetreatTicks = builder
                .comment("Ticks spent retreating during hit-and-run (EVASIVE/EXPLOSIVE).")
                .defineInRange("hitAndRunRetreatTicks", 40, 10, 100);
        evasiveDodgeInterval = builder
                .comment("Ticks between evasive sidestep dodges (EVASIVE).")
                .defineInRange("evasiveDodgeInterval", 40.0, 10.0, 100.0);
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

        // ---- Mount ----
        builder.push("mount");
        horseSearchRange = builder
                .comment("Range (blocks) to search for saddled horses when mounting.")
                .defineInRange("horseSearchRange", 16.0, 4.0, 64.0);
        autoMountEnabled = builder
                .comment("When true, squire auto-mounts its assigned horse when idle nearby.")
                .define("autoMountEnabled", true);
        builder.pop();

        // ---- Chest interaction ----
        builder.push("chest");
        chestSearchRange = builder
                .comment("Range (blocks) to search for nearest chest when no position specified.")
                .defineInRange("searchRange", 8.0, 2.0, 32.0);
        builder.pop();

        // ---- Patrol ----
        builder.push("patrol");
        patrolDefaultWait = builder
                .comment("Default ticks to wait at each patrol signpost before moving on. 100 = 5 seconds.")
                .defineInRange("defaultWaitTicks", 100, 20, 600);
        patrolMaxRouteLength = builder
                .comment("Maximum number of signposts in a patrol route.")
                .defineInRange("maxRouteLength", 20, 2, 50);
        builder.pop();

        // ---- MineColonies integration ----
        builder.push("minecolonies");
        minecoloniesIntegration = builder
                .comment("When true and MineColonies is installed, enables raid defense, warehouse access, and colonist protection.")
                .define("enabled", true);
        minecoloniesRaidAggroMultiplier = builder
                .comment("Aggro range multiplier during active MineColonies raids. 2.0 = double normal range.")
                .defineInRange("raidAggroMultiplier", 2.0, 1.0, 5.0);
        builder.pop();

        // ---- Auto-crafting ----
        builder.push("autoCraft");
        autoCraftEnabled = builder
                .comment("When true, squires craft a wooden sword and shield from inventory materials if unarmed.")
                .define("enabled", true);
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

        // ---- Danger avoidance ----
        builder.push("danger");
        dangerFleeRange = builder
                .comment("Range in blocks to detect and flee from explosive threats.")
                .defineInRange("dangerFleeRange", 10.0, 4.0, 20.0);
        dangerProactiveRange = builder
                .comment("Range in blocks to proactively flee from creepers targeting squire or owner.")
                .defineInRange("dangerProactiveRange", 6.0, 3.0, 12.0);
        dangerFleeTicks = builder
                .comment("Ticks to spend fleeing from a detected threat.")
                .defineInRange("dangerFleeTicks", 60, 20, 200);
        dangerScanInterval = builder
                .comment("Ticks between danger scans for nearby explosive mobs.")
                .defineInRange("dangerScanInterval", 5, 1, 20);
        builder.pop();

        // ---- Halberd sweep ----
        builder.push("halberd");
        sweepDamageMultiplier = builder
                .comment("Damage multiplier for halberd sweep AoE (fraction of base damage).")
                .defineInRange("sweepDamageMultiplier", 0.75, 0.1, 1.5);
        sweepCooldownTicks = builder
                .comment("Cooldown ticks between halberd sweeps.")
                .defineInRange("sweepCooldownTicks", 60, 20, 200);
        sweepRange = builder
                .comment("Range in blocks for halberd sweep AoE.")
                .defineInRange("sweepRange", 2.5, 1.0, 5.0);
        sweepMaxTargets = builder
                .comment("Maximum number of targets hit by halberd sweep.")
                .defineInRange("sweepMaxTargets", 5, 1, 10);
        builder.pop();

        // ---- Visual progression ----
        builder.push("visual");
        enableVisualProgression = builder
                .comment("Enable tier-based visual armor changes by squire level.")
                .define("enableVisualProgression", true);
        builder.pop();

        // ---- Farming ----
        builder.push("farming");
        farmReach = builder
                .comment("Block reach for farming actions.")
                .defineInRange("farmReach", 3.0, 1.0, 6.0);
        farmTicksPerBlock = builder
                .comment("Ticks spent per farming action (till/plant/harvest).")
                .defineInRange("farmTicksPerBlock", 10, 5, 40);
        farmScanInterval = builder
                .comment("Ticks between farm area scans for new tasks.")
                .defineInRange("farmScanInterval", 40, 10, 200);
        farmMaxArea = builder
                .comment("Maximum farm area in blocks (width * length).")
                .defineInRange("farmMaxArea", 256, 16, 1024);
        builder.pop();

        // ---- Fishing ----
        builder.push("fishing");
        waterSearchRange = builder
                .comment("Search range in blocks for finding nearby water.")
                .defineInRange("waterSearchRange", 16.0, 4.0, 32.0);
        fishingCatchInterval = builder
                .comment("Ticks between simulated fish catches.")
                .defineInRange("fishingCatchInterval", 400, 100, 1200);
        fishingRodDurabilityPerCatch = builder
                .comment("Durability consumed per catch from fishing rod.")
                .defineInRange("fishingRodDurabilityPerCatch", 1, 0, 5);
        builder.pop();

        // ---- Task queue ----
        builder.push("taskQueue");
        maxQueueLength = builder
                .comment("Maximum number of tasks in the command queue.")
                .defineInRange("maxQueueLength", 10, 1, 50);
        builder.pop();

        // ---- Feature toggles ----
        builder.push("features");
        enableCombat = builder
                .comment("Enable combat behavior (melee + ranged). When false, squire flees all hostiles.")
                .define("enableCombat", true);
        enableMining = builder
                .comment("Enable mining and area clear commands.")
                .define("enableMining", true);
        enableProgression = builder
                .comment("Enable XP and leveling system.")
                .define("enableProgression", true);
        enablePersonality = builder
                .comment("Enable chat lines, idle behaviors, and ambient sounds.")
                .define("enablePersonality", true);
        enableBackpack = builder
                .comment("Enable tiered backpack system. When false, squire has fixed 9-slot inventory.")
                .define("enableBackpack", true);
        enableCustomArmor = builder
                .comment("Enable custom squire armor set bonus.")
                .define("enableCustomArmor", true);
        enableMounting = builder
                .comment("Enable horse mounting and mounted combat.")
                .define("enableMounting", true);
        enablePatrol = builder
                .comment("Enable signpost patrol system.")
                .define("enablePatrol", true);
        enableChestInteraction = builder
                .comment("Enable chest store/fetch commands.")
                .define("enableChestInteraction", true);
        enableFarming = builder
                .comment("Enable farming handler (till/plant/harvest).")
                .define("enableFarming", true);
        enableFishing = builder
                .comment("Enable fishing handler.")
                .define("enableFishing", true);
        enableTaskQueue = builder
                .comment("Enable command queuing system.")
                .define("enableTaskQueue", true);
        enableDangerAvoidance = builder
                .comment("Enable proactive danger avoidance (flee creepers).")
                .define("enableDangerAvoidance", true);
        classicMode = builder
                .comment("Classic mode: all features unlocked at Lv1 (ignores tier progression). For players who prefer the pre-v0.5.0 behavior.")
                .define("classicMode", false);
        builder.pop();

        // ---- Death penalty ----
        builder.push("death");
        deathXPPenalty = builder
                .comment("When true, squire loses a percentage of current level XP on death.")
                .define("deathXPPenalty", true);
        deathXPPenaltyPercent = builder
                .comment("Fraction of current level XP lost on death. 0.10 = 10%.")
                .defineInRange("deathXPPenaltyPercent", 0.10, 0.0, 0.5);
        builder.pop();

        SPEC = builder.build();
    }

    private SquireConfig() {
        // No instances
    }
}
