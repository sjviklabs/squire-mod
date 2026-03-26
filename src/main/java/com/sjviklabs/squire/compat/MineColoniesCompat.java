package com.sjviklabs.squire.compat;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft compatibility layer for MineColonies integration.
 * All checks are runtime-safe — if MineColonies is not installed,
 * every method returns a safe default (false/empty). No hard dependency.
 * * Detection uses entity class names (string comparison) to avoid compile-time
 * references to MineColonies classes. This is intentionally loose coupling.
 *
 * Hooks:
 * - Raid detection: identifies MineColonies raider mobs by class hierarchy
 * - Warehouse detection: identifies warehouse block entities for item storage
 * - Colonist detection: identifies friendly colonist entities to avoid targeting
 * - Colony awareness: checks if MineColonies mod is present and active
 */
public final class MineColoniesCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(MineColoniesCompat.class);
    private static final String MINECOLONIES_MODID = "minecolonies";

    /** Cached mod presence check — evaluated once on first call. */
    private static Boolean modPresent = null;

    private MineColoniesCompat() {}

    // ------------------------------------------------------------------
    // Mod detection
    // ------------------------------------------------------------------
    /**
     * @return true if MineColonies is installed and integration is enabled in config
     */
    public static boolean isActive() {
        if (!SquireConfig.minecoloniesIntegration.get()) return false;
        if (modPresent == null) {
            modPresent = ModList.get().isLoaded(MINECOLONIES_MODID);
            if (modPresent) {
                LOGGER.info("Squire: MineColonies detected — enabling integration hooks");
            }
        }
        return modPresent;
    }

    // ------------------------------------------------------------------
    // Raider detection
    // ------------------------------------------------------------------

    /**
     * MineColonies raiders extend AbstractEntityMinecoloniesMob which extends Monster.
     * Their class names contain "Barbarian", "Pirate", "Egyptian", "Viking", "Druid",
     * "Amazon", "Norsemen" etc. We check the class hierarchy for the MineColonies
     * package prefix to catch all raider types without enumerating them.
     *
     * @return true if the entity is a MineColonies raider mob
     */
    public static boolean isRaider(Entity entity) {
        if (!isActive()) return false;
        return isFromPackage(entity, "com.minecolonies.core.entity.mobs");
    }

    /**
     * @return true if the entity is a MineColonies colonist (citizen)
     */
    public static boolean isColonist(Entity entity) {
        if (!isActive()) return false;
        return isFromPackage(entity, "com.minecolonies.core.entity.citizen");
    }
    /**
     * Check if a raider mob is nearby, indicating an active raid.
     * Scans entities within the given range for MineColonies raiders.
     *
     * @return true if at least one raider is within range of the squire
     */
    public static boolean isRaidActive(SquireEntity squire, double range) {
        if (!isActive()) return false;
        var box = squire.getBoundingBox().inflate(range);
        var entities = squire.level().getEntities(squire, box,
                e -> e instanceof LivingEntity && isRaider(e));
        return !entities.isEmpty();
    }

    /**
     * Get the aggro range boost during an active raid. When raiders are
     * detected nearby, the squire extends its detection range to better
     * defend the colony perimeter.
     *
     * @return boosted aggro range during raids, or the normal range if no raid
     */
    public static double getRaidAggroRange(SquireEntity squire) {
        double normalRange = SquireConfig.aggroRange.get();
        if (!isActive()) return normalRange;
        double raidCheckRange = normalRange * 2.0;
        if (isRaidActive(squire, raidCheckRange)) {
            return normalRange * SquireConfig.minecoloniesRaidAggroMultiplier.get();
        }
        return normalRange;
    }

    // ------------------------------------------------------------------
    // Warehouse detection
    // ------------------------------------------------------------------

    /**
     * Check if the block entity at the given position is a MineColonies warehouse.
     * MineColonies warehouses use TileEntityWareHouse which implements standard
     * IItemHandler via NeoForge capabilities, so the squire's existing chest
     * interaction logic works once we identify the block.
     */
    public static boolean isWarehouse(Level level, BlockPos pos) {
        if (!isActive()) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return isFromPackage(be, "com.minecolonies.core.tileentities");
    }
    // ------------------------------------------------------------------
    // Friendly-fire prevention
    // ------------------------------------------------------------------

    /**
     * Check if an entity should be protected from squire attacks.
     * Prevents squires from targeting MineColonies colonists, guards, visitors, etc.
     *
     * @return true if the squire should NOT attack this entity
     */
    public static boolean isFriendly(Entity entity) {
        if (!isActive()) return false;
        // Colonists, guards, visitors, and other MineColonies citizens
        return isColonist(entity);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Walk the class hierarchy checking if any class in the chain belongs
     * to the given package prefix. This catches all subclasses without
     * needing to enumerate specific class names.
     */
    private static boolean isFromPackage(Object obj, String packagePrefix) {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            String name = clazz.getName();
            if (name.startsWith(packagePrefix)) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}