package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Handles fleeing from explosive/dangerous mobs (creepers, charged creepers, etc.).
 * Scans for nearby threats and runs away when one is detected about to explode.
 * One instance per squire, holds flee target and cooldown state.
 */
public class DangerHandler {

    private final SquireEntity squire;
    private LivingEntity threat;
    private int fleeTicksRemaining;
    private int scanCooldown;

    public DangerHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /**
     * Scan for nearby explosive threats. Returns true if the squire should flee.
     * Called from state machine condition checks.
     */
    public boolean shouldFlee() {
        if (scanCooldown > 0) {
            scanCooldown--;
            return threat != null && threat.isAlive();
        }
        scanCooldown = SquireConfig.dangerScanInterval.get();

        double range = SquireConfig.dangerFleeRange.get();
        AABB scanBox = squire.getBoundingBox().inflate(range);

        List<Creeper> creepers = squire.level().getEntitiesOfClass(
                Creeper.class, scanBox,
                creeper -> creeper.isAlive() && isAboutToExplode(creeper)
        );

        if (!creepers.isEmpty()) {
            // Find closest threat
            Creeper closest = null;
            double closestDist = Double.MAX_VALUE;
            for (Creeper creeper : creepers) {
                double dist = squire.distanceToSqr(creeper);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = creeper;
                }
            }
            threat = closest;
            return true;
        }

        threat = null;
        return false;
    }

    /** Called when entering FLEEING state. */
    public void start() {
        fleeTicksRemaining = SquireConfig.dangerFleeTicks.get();
        var log = squire.getActivityLog();
        if (log != null && threat != null) {
            log.log("DANGER", "Fleeing from " + threat.getType().toShortString()
                    + " at " + threat.blockPosition().toShortString());
        }
    }

    /** Called when exiting FLEEING state. */
    public void stop() {
        threat = null;
        fleeTicksRemaining = 0;
        squire.getNavigation().stop();
    }

    /** Per-tick flee logic: run in opposite direction from threat. */
    public SquireAIState tick(SquireEntity s) {
        fleeTicksRemaining--;

        if (fleeTicksRemaining <= 0 || threat == null || !threat.isAlive()) {
            stop();
            return SquireAIState.IDLE;
        }

        // Calculate flee direction: directly away from threat
        double dx = s.getX() - threat.getX();
        double dz = s.getZ() - threat.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len > 0.01) {
            double fleeDist = SquireConfig.dangerFleeRange.get();
            double targetX = s.getX() + (dx / len) * fleeDist;
            double targetZ = s.getZ() + (dz / len) * fleeDist;
            s.getNavigation().moveTo(targetX, s.getY(), targetZ, 1.5D);
            s.setSquireSprinting(true);
        }

        // If a creeper is swelling nearby, keep looking at it (awareness)
        s.getLookControl().setLookAt(threat, 30.0F, 30.0F);

        // Re-check: if threat stopped being dangerous, we can stop fleeing early
        if (threat instanceof Creeper creeper && !isAboutToExplode(creeper)) {
            double distSq = s.distanceToSqr(threat);
            double safeRange = SquireConfig.dangerFleeRange.get();
            if (distSq > safeRange * safeRange) {
                stop();
                return SquireAIState.IDLE;
            }
        }

        return SquireAIState.FLEEING;
    }

    /** Whether there's an active threat to flee from. */
    public boolean hasThreat() {
        return threat != null && threat.isAlive();
    }

    /**
     * Check if a creeper is about to explode (swell level > 0 means it started fusing,
     * or it's within close range which means it will start fusing soon).
     */
    private boolean isAboutToExplode(Creeper creeper) {
        // getSwellDir() > 0 means the creeper is actively fusing
        if (creeper.getSwellDir() > 0) return true;

        // Also flee proactively if creeper is close and targeting us or our owner
        double distSq = squire.distanceToSqr(creeper);
        double proactiveRange = SquireConfig.dangerProactiveRange.get();
        if (distSq < proactiveRange * proactiveRange) {
            LivingEntity creeperTarget = creeper.getTarget();
            if (creeperTarget != null) {
                // Flee if creeper is targeting us, our owner, or another player nearby
                if (creeperTarget == squire) return true;
                if (creeperTarget == squire.getOwner()) return true;
            }
        }
        return false;
    }
}
