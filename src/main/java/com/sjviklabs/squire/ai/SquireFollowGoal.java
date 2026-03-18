package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class SquireFollowGoal extends Goal {
    private final SquireEntity squire;
    private final double speedModifier;
    private final float stopDistance;
    private final float startDistance;
    private final float teleportDistance = 24.0F;
    private Player owner;
    private int timeToRecalcPath;

    public SquireFollowGoal(SquireEntity squire, double speedModifier, float startDistance, float stopDistance) {
        this.squire = squire;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.owner = this.squire.getOwner();
        if (this.owner == null) return false;
        if (this.squire.distanceTo(this.owner) < this.startDistance) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.owner == null || !this.owner.isAlive()) return false;
        return this.squire.distanceTo(this.owner) > this.stopDistance;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.squire.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.squire.getLookControl().setLookAt(this.owner, 10.0F, (float) this.squire.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;

            double distance = this.squire.distanceToSqr(this.owner);

            // Teleport if too far away
            if (distance > this.teleportDistance * this.teleportDistance) {
                this.squire.moveTo(
                        this.owner.getX() + (this.squire.getRandom().nextDouble() - 0.5) * 2,
                        this.owner.getY(),
                        this.owner.getZ() + (this.squire.getRandom().nextDouble() - 0.5) * 2,
                        this.squire.getYRot(),
                        this.squire.getXRot()
                );
                this.squire.getNavigation().stop();
                return;
            }

            PathNavigation navigation = this.squire.getNavigation();
            navigation.moveTo(this.owner, this.speedModifier);
        }
    }
}
