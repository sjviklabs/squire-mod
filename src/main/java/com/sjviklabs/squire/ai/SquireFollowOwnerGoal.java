package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Custom follow-owner goal with sprint matching and no teleportation.
 * Replaces vanilla FollowOwnerGoal for full control over pathfinding behavior.
 */
public class SquireFollowOwnerGoal extends Goal {

    private final SquireEntity squire;
    private Player owner;
    private int timeToRecalcPath;

    public SquireFollowOwnerGoal(SquireEntity squire) {
        this.squire = squire;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player player = this.squire.getOwner() instanceof Player p ? p : null;
        if (player == null) return false;
        if (!player.isAlive()) return false;
        if (player.isSpectator()) return false;
        if (this.squire.isOrderedToSit()) return false;
        if (this.squire.distanceToSqr(player) < square(SquireConfig.followStartDistance.get())) return false;

        this.owner = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.owner == null || !this.owner.isAlive()) return false;
        if (this.owner.isSpectator()) return false;
        if (this.squire.isOrderedToSit()) return false;
        return this.squire.distanceToSqr(this.owner) > square(SquireConfig.followStopDistance.get());
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;

        if (this.squire.getNavigation() instanceof GroundPathNavigation groundNav) {
            groundNav.setCanOpenDoors(true);
            groundNav.setCanPassDoors(true);
        }
    }

    @Override
    public void stop() {
        this.owner = null;
        this.squire.setSprinting(false);
        this.squire.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.owner == null) return;

        // Always look at owner
        this.squire.getLookControl().setLookAt(this.owner, 10.0F, (float) this.squire.getMaxHeadXRot());

        if (--this.timeToRecalcPath > 0) return;
        this.timeToRecalcPath = SquireConfig.pathRecalcInterval.get();

        double distSq = this.squire.distanceToSqr(this.owner);
        double sprintThreshold = SquireConfig.sprintDistance.get();
        boolean shouldSprint = distSq > square(sprintThreshold) || this.owner.isSprinting();

        // Set sprint state on the entity
        this.squire.setSprinting(shouldSprint);

        // Calculate speed: base speed with 1.3x multiplier when sprinting
        double speed = shouldSprint ? 1.3D : 1.0D;

        this.squire.getNavigation().moveTo(this.owner, speed);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static double square(double value) {
        return value * value;
    }
}
