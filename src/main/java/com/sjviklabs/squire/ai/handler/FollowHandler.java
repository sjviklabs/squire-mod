package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;

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

        // Teleport to owner if too far away (like tamed wolves)
        if (distSq > 24.0 * 24.0) {
            if (tryTeleportToOwner(s, owner)) {
                return SquireAIState.FOLLOWING_OWNER;
            }
        }

        // Jump boost when owner is 2+ blocks above — helps with stairs, hills, cliffs
        double heightDiff = owner.getY() - s.getY();
        if (heightDiff > 1.5 && distSq < 20.0 * 20.0) {
            if (!s.hasEffect(MobEffects.JUMP)) {
                s.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false));
            }
        }

        double sprintThresh = SquireConfig.sprintDistance.get();
        boolean shouldSprint = distSq > sprintThresh * sprintThresh || owner.isSprinting();
        s.setSquireSprinting(shouldSprint);

        double speed = shouldSprint ? 1.3D : 1.0D;
        s.getNavigation().moveTo(owner, speed);

        return SquireAIState.FOLLOWING_OWNER;
    }

    /**
     * Teleport squire near the owner, trying random offsets within 4 blocks.
     * Mirrors vanilla FollowOwnerGoal teleport logic.
     */
    private boolean tryTeleportToOwner(SquireEntity s, Player owner) {
        BlockPos ownerPos = owner.blockPosition();

        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = Mth.randomBetweenInclusive(s.getRandom(), -3, 3);
            int dz = Mth.randomBetweenInclusive(s.getRandom(), -3, 3);
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;

            BlockPos target = ownerPos.offset(dx, 0, dz);

            // Try to find solid ground within 2 blocks vertically
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos check = target.offset(0, dy, 0);
                BlockState below = s.level().getBlockState(check.below());
                BlockState at = s.level().getBlockState(check);
                BlockState above = s.level().getBlockState(check.above());

                if (below.isSolid() && at.isAir() && above.isAir()) {
                    s.moveTo(check.getX() + 0.5, check.getY(), check.getZ() + 0.5,
                            s.getYRot(), s.getXRot());
                    s.getNavigation().stop();
                    var log = s.getActivityLog();
                    if (log != null) {
                        log.log("FOLLOW", "Teleported to owner at " + check.toShortString());
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
