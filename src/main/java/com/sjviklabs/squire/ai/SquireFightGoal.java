package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

public class SquireFightGoal extends TargetGoal {
    private final SquireEntity squire;
    private final double aggroRange = 12.0D;
    private LivingEntity targetMob;

    public SquireFightGoal(SquireEntity squire) {
        super(squire, false);
        this.squire = squire;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        Player owner = this.squire.getOwner();
        if (owner == null) return false;

        // Defend the owner — attack mobs targeting the owner
        LivingEntity ownerTarget = owner.getLastHurtMob();
        if (ownerTarget instanceof Monster && ownerTarget.isAlive()
                && this.squire.distanceTo(ownerTarget) < aggroRange) {
            this.targetMob = ownerTarget;
            return true;
        }

        // Also respond if owner is being attacked
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker instanceof Monster && attacker.isAlive()
                && this.squire.distanceTo(attacker) < aggroRange) {
            this.targetMob = attacker;
            return true;
        }

        // Find nearby hostile mobs threatening the owner
        List<Monster> nearbyMobs = this.squire.level().getEntitiesOfClass(
                Monster.class,
                this.squire.getBoundingBox().inflate(aggroRange),
                mob -> mob.isAlive() && mob.getTarget() == owner
        );

        if (!nearbyMobs.isEmpty()) {
            this.targetMob = nearbyMobs.get(0);
            return true;
        }

        return false;
    }

    @Override
    public void start() {
        this.squire.setTarget(this.targetMob);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetMob != null && this.targetMob.isAlive()
                && this.squire.distanceTo(this.targetMob) < aggroRange * 1.5;
    }

    @Override
    public void stop() {
        this.squire.setTarget(null);
        this.targetMob = null;
        super.stop();
    }
}
