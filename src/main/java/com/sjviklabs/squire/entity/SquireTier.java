package com.sjviklabs.squire.entity;

/**
 * Progression tiers that gate feature access. A squire earns its way
 * from servant to champion through XP from any activity.
 *
 * Tier boundaries are fixed (not config) because the entire progression
 * narrative depends on them. XP rates are config-tunable.
 *
 * Tier map:
 *   SERVANT    Lv 0-4   — farm, fish, follow
 *   APPRENTICE Lv 5-9   — fight, mine (patrol unlocks mid-tier at Lv8)
 *   SQUIRE     Lv 10-19 — ranged, area clear, chest interact, task queue
 *   KNIGHT     Lv 20-29 — mount, sweep
 *   CHAMPION   Lv 30    — mounted combat
 */
public enum SquireTier {
    SERVANT(0, 4, 9, "Servant"),
    APPRENTICE(5, 9, 18, "Apprentice"),
    SQUIRE(10, 19, 27, "Squire"),
    KNIGHT(20, 29, 36, "Knight"),
    CHAMPION(30, 30, 36, "Champion");

    private final int minLevel;
    private final int maxLevel;
    private final int backpackSlots;
    private final String displayName;

    SquireTier(int minLevel, int maxLevel, int backpackSlots, String displayName) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.backpackSlots = backpackSlots;
        this.displayName = displayName;
    }

    public int getMinLevel()      { return minLevel; }
    public int getMaxLevel()      { return maxLevel; }
    public int getBackpackSlots() { return backpackSlots; }
    public String getDisplayName() { return displayName; }

    // ---- Tier resolution ----

    /** Resolve tier from a squire's current level. */
    public static SquireTier forLevel(int level) {
        if (level >= 30) return CHAMPION;
        if (level >= 20) return KNIGHT;
        if (level >= 10) return SQUIRE;
        if (level >= 5)  return APPRENTICE;
        return SERVANT;
    }

    // ---- Feature gates ----

    /** Melee combat. Servant only swings defensively. */
    public boolean canFight() { return this.ordinal() >= APPRENTICE.ordinal(); }

    /** Mining and block breaking. */
    public boolean canMine() { return this.ordinal() >= APPRENTICE.ordinal(); }

    /** Ranged combat (bows). */
    public boolean canRanged() { return this.ordinal() >= SQUIRE.ordinal(); }

    /** Area clear. */
    public boolean canAreaClear() { return this.ordinal() >= SQUIRE.ordinal(); }

    /** Chest deposit/fetch. */
    public boolean canChestInteract() { return this.ordinal() >= SQUIRE.ordinal(); }

    /** Task queue. */
    public boolean canUseQueue() { return this.ordinal() >= SQUIRE.ordinal(); }

    /** Mounted movement (riding horses). */
    public boolean canMount() { return this.ordinal() >= KNIGHT.ordinal(); }

    /** Mounted combat (lance charge). Tier 4 exclusive. */
    public boolean canMountedCombat() { return this == CHAMPION; }

    /** Halberd sweep. */
    public boolean canSweep() { return this.ordinal() >= KNIGHT.ordinal(); }

    /** Farming — available from level 1. Core servant work. */
    public boolean canFarm() { return true; }

    /** Fishing — available from level 1. Core servant work. */
    public boolean canFish() { return true; }

    /**
     * Patrol — unlocks at Lv8, mid-Apprentice tier.
     * Use {@link #canPatrolAtLevel(int)} for exact level checks.
     * This instance method is safe for tier-level logic: SQUIRE+ always patrol.
     */
    public boolean canPatrol() { return this.ordinal() >= SQUIRE.ordinal(); }

    /**
     * Exact level check for patrol. Patrol unlocks at Lv8, which falls
     * inside Apprentice (Lv5-9) — a mid-tier gate that tier alone can't express.
     * Call this wherever the squire's actual level is available.
     */
    public static boolean canPatrolAtLevel(int level) { return level >= 8; }
}
