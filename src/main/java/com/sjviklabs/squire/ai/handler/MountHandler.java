package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.item.SquireLanceItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Handles horse finding, mounting, dismounting, and mounted navigation.
 * The squire stores a horse UUID in NBT for persistence across restarts.
 * While mounted, the squire drives the horse's navigation instead of its own.
 */
public class MountHandler {

    private final SquireEntity squire;
    private UUID horseUUID;
    private int searchCooldown;

    public MountHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ---- Public API ----

    public UUID getHorseUUID() { return horseUUID; }
    public void setHorseUUID(UUID uuid) { this.horseUUID = uuid; }

    public boolean isMounted() {
        return squire.isPassenger() && squire.getVehicle() instanceof AbstractHorse;
    }

    /**
     * Order the squire to mount the nearest saddled horse, or its assigned horse.
     * Returns true if a valid horse was found.
     */
    public boolean orderMount() {
        AbstractHorse horse = findAssignedHorse();
        if (horse == null) {
            horse = findNearestSaddledHorse();
        }
        if (horse != null) {
            horseUUID = horse.getUUID();
            return true;
        }
        return false;
    }

    /** Order the squire to dismount. */
    public void orderDismount() {
        if (isMounted()) {
            squire.stopRiding();
        }
        horseUUID = null;
    }

    /** Whether the squire should try to auto-mount (has assigned horse nearby). */
    public boolean shouldAutoMount() {
        if (!SquireConfig.autoMountEnabled.get()) return false;
        if (horseUUID == null) return false;
        if (squire.isOrderedToSit()) return false;
        if (isMounted()) return false;
        return findAssignedHorse() != null;
    }

    /** Start walking toward the horse to mount it. */
    public void startApproach() {
        // Navigation will happen in tickApproach
    }

    /**
     * Tick while approaching horse to mount.
     * Returns MOUNTED_IDLE once mounted, IDLE if horse lost.
     */
    public SquireAIState tickApproach(SquireEntity s) {
        AbstractHorse horse = findAssignedHorse();
        if (horse == null) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        double distSq = s.distanceToSqr(horse);
        if (distSq <= 4.0) { // Within 2 blocks — mount
            s.startRiding(horse, true);
            s.getNavigation().stop();
            return SquireAIState.MOUNTED_IDLE;
        }

        // Walk toward horse
        s.getNavigation().moveTo(horse, 1.2);
        return SquireAIState.MOUNTING;
    }

    /**
     * Tick while mounted and following owner. Drives the horse's navigation.
     */
    public SquireAIState tickMountedFollow(SquireEntity s) {
        if (!isMounted()) return SquireAIState.IDLE;

        AbstractHorse horse = (AbstractHorse) s.getVehicle();
        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner == null) return SquireAIState.MOUNTED_IDLE;

        double distSq = s.distanceToSqr(owner);
        double stopDist = SquireConfig.followStopDistance.get();
        double startDist = SquireConfig.followStartDistance.get();

        if (distSq < stopDist * stopDist) {
            horse.getNavigation().stop();
            return SquireAIState.MOUNTED_IDLE;
        }

        // Sprint if far away
        double speed = distSq > startDist * startDist * 4 ? 1.4 : 1.0;
        horse.getNavigation().moveTo(owner, speed);

        return SquireAIState.MOUNTED_FOLLOW;
    }

    /**
     * Tick while mounted in combat. Drives horse toward target,
     * delegates actual attacking to CombatHandler.
     */
    public SquireAIState tickMountedCombat(SquireEntity s) {
        if (!isMounted()) return SquireAIState.COMBAT_APPROACH; // Fell off, continue on foot

        LivingEntity target = s.getTarget();
        if (target == null || !target.isAlive()) {
            return SquireAIState.MOUNTED_IDLE;
        }

        AbstractHorse horse = (AbstractHorse) s.getVehicle();
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Drive horse toward target
        horse.getNavigation().moveTo(target, 1.4);

        // Check attack reach (extended for mounted lance)
        double distSq = s.distanceToSqr(target);
        double reach = getMountedReach(s, target);

        if (distSq <= reach * reach) {
            // Delegate to combat handler's tick for the actual attack
            // CombatHandler will detect mounted state for lance charge
            return s.getSquireAI().getCombat().tick(s);
        }

        return SquireAIState.MOUNTED_COMBAT;
    }

    // ---- Horse death handling ----

    /** Called when the ridden horse dies. Squire dismounts and continues on foot. */
    public void onHorseDied() {
        horseUUID = null;
    }

    // ---- Private helpers ----

    @Nullable
    private AbstractHorse findAssignedHorse() {
        if (horseUUID == null) return null;
        double range = SquireConfig.horseSearchRange.get();
        AABB searchBox = squire.getBoundingBox().inflate(range);
        List<AbstractHorse> horses = squire.level().getEntitiesOfClass(
                AbstractHorse.class, searchBox,
                h -> h.getUUID().equals(horseUUID) && h.isAlive());
        return horses.isEmpty() ? null : horses.get(0);
    }

    @Nullable
    private AbstractHorse findNearestSaddledHorse() {
        double range = SquireConfig.horseSearchRange.get();
        AABB searchBox = squire.getBoundingBox().inflate(range);
        Player owner = squire.getOwner() instanceof Player p ? p : null;

        List<AbstractHorse> horses = squire.level().getEntitiesOfClass(
                AbstractHorse.class, searchBox,
                h -> h.isAlive() && h.isSaddled() && !h.isVehicle()
                        && h.isTamed()
                        && (owner == null || h.getOwnerUUID() == null
                            || h.getOwnerUUID().equals(owner.getUUID())));

        if (horses.isEmpty()) return null;

        // Return closest
        AbstractHorse closest = null;
        double closestDist = Double.MAX_VALUE;
        for (AbstractHorse h : horses) {
            double dist = squire.distanceToSqr(h);
            if (dist < closestDist) {
                closestDist = dist;
                closest = h;
            }
        }
        return closest;
    }

    private double getMountedReach(SquireEntity s, LivingEntity target) {
        if (s.getMainHandItem().getItem() instanceof SquireLanceItem) {
            return 6.0; // Mounted lance reach
        }
        // Default mounted melee reach
        double width = s.getBbWidth();
        return Math.sqrt(width * 2.0 * width * 2.0 + target.getBbWidth()) + 1.0;
    }
}
