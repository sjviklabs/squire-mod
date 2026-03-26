package com.sjviklabs.squire.compat;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central mod compatibility registry. Detects installed mods at runtime
 * and provides hooks that handlers can query. No hard dependencies.
 *
 * Supported mods:
 * - Waystones: detect player teleport, emergency-teleport squire to follow
 * - Gravestone mods: detect gravestone blocks for item retrieval
 * - Farmer's Delight: knife weapons recognized as melee
 * - Jade/WTHIT: tooltip data (see JadeCompat)
 * - MineColonies: raid defense, warehouse, colonist protection (see MineColoniesCompat)
 */
public final class ModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModCompat.class);

    // Mod IDs
    private static final String WAYSTONES = "waystones";
    private static final String GRAVESTONE = "gravestone";
    private static final String TOMBSTONE = "tombstone";
    private static final String FARMERS_DELIGHT = "farmersdelight";
    private static final String JADE = "jade";
    private static final String WTHIT = "wthit";

    // Cached presence flags (null = not checked yet)
    private static Boolean hasWaystones;
    private static Boolean hasGravestone;
    private static Boolean hasFarmersDelight;
    private static Boolean hasJade;

    private ModCompat() {}

    // ------------------------------------------------------------------
    // Mod detection
    // ------------------------------------------------------------------

    public static boolean hasWaystones() {
        if (hasWaystones == null) {
            hasWaystones = ModList.get().isLoaded(WAYSTONES);
            if (hasWaystones) LOGGER.info("Squire: Waystones detected");
        }
        return hasWaystones;
    }

    public static boolean hasGravestone() {
        if (hasGravestone == null) {
            hasGravestone = ModList.get().isLoaded(GRAVESTONE)
                    || ModList.get().isLoaded(TOMBSTONE);
            if (hasGravestone) LOGGER.info("Squire: Gravestone mod detected");
        }
        return hasGravestone;
    }

    public static boolean hasFarmersDelight() {
        if (hasFarmersDelight == null) {
            hasFarmersDelight = ModList.get().isLoaded(FARMERS_DELIGHT);
            if (hasFarmersDelight) LOGGER.info("Squire: Farmer's Delight detected");
        }
        return hasFarmersDelight;
    }

    public static boolean hasJade() {
        if (hasJade == null) {
            hasJade = ModList.get().isLoaded(JADE) || ModList.get().isLoaded(WTHIT);
            if (hasJade) LOGGER.info("Squire: Jade/WTHIT detected");
        }
        return hasJade;
    }

    // ------------------------------------------------------------------
    // Waystones: detect player dimension/position change
    // ------------------------------------------------------------------

    /**
     * Called from SquireEntity tick. If the owner has moved more than 100 blocks
     * in a single tick (likely a teleport via waystone/command), emergency-teleport
     * the squire to follow. This covers Waystones, /tp, and any teleport mod.
     *
     * @param squire the squire entity
     * @param owner the owner player
     * @param lastOwnerX previous tick owner X
     * @param lastOwnerZ previous tick owner Z
     * @return true if a teleport catch-up was triggered
     */
    public static boolean checkOwnerTeleport(SquireEntity squire, Player owner,
                                              double lastOwnerX, double lastOwnerZ) {
        double dx = owner.getX() - lastOwnerX;
        double dz = owner.getZ() - lastOwnerZ;
        double distSq = dx * dx + dz * dz;

        // 100 blocks in one tick = definite teleport (not walking/sprinting)
        if (distSq > 100.0 * 100.0) {
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Gravestone: detect gravestone block entities
    // ------------------------------------------------------------------

    /**
     * Check if a block at the given position is a gravestone/tombstone.
     * Uses class name inspection like MineColoniesCompat.
     */
    public static boolean isGravestone(Level level, BlockPos pos) {
        if (!hasGravestone()) return false;
        var be = level.getBlockEntity(pos);
        if (be == null) return false;
        String className = be.getClass().getName().toLowerCase();
        return className.contains("grave") || className.contains("tombstone");
    }

    // ------------------------------------------------------------------
    // Farmer's Delight: weapon recognition
    // ------------------------------------------------------------------

    /**
     * Check if an item is a Farmer's Delight knife (usable as melee weapon).
     * FD knives have attack damage attributes but don't extend SwordItem.
     */
    public static boolean isFDKnife(net.minecraft.world.item.ItemStack stack) {
        if (!hasFarmersDelight()) return false;
        String className = stack.getItem().getClass().getName().toLowerCase();
        return className.contains("knife");
    }

    /**
     * Log all detected mod integrations at startup.
     */
    public static void logDetectedMods() {
        LOGGER.info("Squire mod compatibility scan:");
        LOGGER.info("  MineColonies: {}", MineColoniesCompat.isActive() ? "ACTIVE" : "not found");
        LOGGER.info("  Waystones: {}", hasWaystones() ? "detected" : "not found");
        LOGGER.info("  Gravestone: {}", hasGravestone() ? "detected" : "not found");
        LOGGER.info("  Farmer's Delight: {}", hasFarmersDelight() ? "detected" : "not found");
        LOGGER.info("  Jade/WTHIT: {}", hasJade() ? "detected" : "not found");
    }
}