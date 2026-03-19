package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;

/**
 * Handles following the owner: path recalculation, sprint matching, door navigation.
 * One instance per squire, holds path recalc timer.
 */
public class FollowHandler {

    private final SquireEntity squire;
    private int pathRecalcTimer;

    public FollowHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /** Whether the squire should start following (owner is far enough). */
    public boolean shouldFollow() {
        if (squire.isOrderedToSit()) return false;
        Player owner = squire.getOwner() instanceof Player p ? p : null;
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return false;
        double dist = SquireConfig.followStartDistance.get();
        return squire.distanceToSqr(owner) >= dist * dist;
    }

    /** Whether the squire should stop following (close enough or invalid). */
    public boolean shouldStop() {
        if (squire.isOrderedToSit()) return true;
        Player owner = squire.getOwner() instanceof Player p ? p : null;
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return true;
        double dist = SquireConfig.followStopDistance.get();
        return squire.distanceToSqr(owner) <= dist * dist;
    }

    /** Called when entering FOLLOWING_OWNER state. */
    public void start() {
        pathRecalcTimer = 0;
        if (squire.getNavigation() instanceof GroundPathNavigation groundNav) {
            groundNav.setCanOpenDoors(true);
            groundNav.setCanPassDoors(true);
        }
    }

    /** Called when exiting FOLLOWING_OWNER state. */
    public void stop() {
        squire.setSquireSprinting(false);
        squire.getNavigation().stop();
        if (squire.getNavigation() instanceof GroundPathNavigation groundNav) {
            groundNav.setCanOpenDoors(false);
            groundNav.setCanPassDoors(false);
        }
    }

    /** Per-tick follow logic with throttled path recalculation. */
    public SquireAIState tick(SquireEntity s) {
        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner == null) return SquireAIState.IDLE;

        // Look at owner every tick (smooth head tracking)
        s.getLookControl().setLookAt(owner, 10.0F, (float) s.getMaxHeadXRot());

        // Throttle path recalculation
        if (--pathRecalcTimer > 0) return SquireAIState.FOLLOWING_OWNER;
        pathRecalcTimer = SquireConfig.pathRecalcInterval.get();

        double distSq = s.distanceToSqr(owner);
        double sprintThresh = SquireConfig.sprintDistance.get();
        boolean shouldSprint = distSq > sprintThresh * sprintThresh || owner.isSprinting();
        s.setSquireSprinting(shouldSprint);

        double speed = shouldSprint ? 1.3D : 1.0D;
        s.getNavigation().moveTo(owner, speed);

        return SquireAIState.FOLLOWING_OWNER;
    }
}
