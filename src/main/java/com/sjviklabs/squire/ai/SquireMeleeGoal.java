package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Custom melee combat goal with weapon-speed-based cooldown and owner leash distance.
 * Does not extend MeleeAttackGoal — full control over attack timing and leash behavior.
 */
public class SquireMeleeGoal extends Goal {

    private final SquireEntity squire;
    private LivingEntity target;
    private int attackCooldown;
    private int ticksUntilNextAttack;

    /** Squared attack reach — approximately arm's length (2.0 blocks). */
    private static final double ATTACK_REACH_SQ = 4.0D;

    public SquireMeleeGoal(SquireEntity squire) {
        this.squire = squire;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.squire.isOrderedToSit()) return false;

        LivingEntity currentTarget = this.squire.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) return false;

        this.target = currentTarget;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.squire.isOrderedToSit()) return false;

        // Owner leash check — disengage if too far from owner
        Player owner = this.squire.getOwner() instanceof Player p ? p : null;
        if (owner != null) {
            double leashDist = SquireConfig.combatLeashDistance.get();
            if (this.squire.distanceToSqr(owner) > leashDist * leashDist) {
                return false;
            }
        }

        // Stay engaged as long as target is within follow range
        double followRange = this.squire.getAttributeValue(Attributes.FOLLOW_RANGE);
        return this.squire.distanceToSqr(this.target) <= followRange * followRange;
    }

    @Override
    public void start() {
        this.ticksUntilNextAttack = 0;
        this.recalculateCooldown();
    }

    @Override
    public void stop() {
        this.target = null;
        this.squire.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        // Always look at and path toward the target
        this.squire.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        this.squire.getNavigation().moveTo(this.target, 1.2D);

        this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);

        // Check if in attack range
        double distSq = this.squire.distanceToSqr(
                this.target.getX(), this.target.getBoundingBox().minY, this.target.getZ());
        double reach = getAttackReachSq(this.target);

        if (distSq <= reach && this.ticksUntilNextAttack <= 0) {
            performAttack();
            this.recalculateCooldown();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * Execute the attack: swing arm, deal attribute-based damage, reset cooldown.
     */
    private void performAttack() {
        // Arm swing animation
        this.squire.swing(InteractionHand.MAIN_HAND);

        // Damage is sourced from the ATTACK_DAMAGE attribute, which is modified
        // automatically when holding a weapon via attribute modifiers.
        this.squire.doHurtTarget(this.target);
    }

    /**
     * Recalculate the cooldown in ticks based on the entity's attack speed attribute.
     * Attack speed is in attacks/second; cooldown = 20 ticks / attacks_per_second.
     */
    private void recalculateCooldown() {
        double attackSpeed = this.squire.getAttributeValue(Attributes.ATTACK_SPEED);
        // Clamp to sane range: at least 0.5 attacks/sec, at most 4 attacks/sec
        attackSpeed = Math.max(0.5D, Math.min(4.0D, attackSpeed));
        this.attackCooldown = (int) Math.ceil(20.0D / attackSpeed);
        this.ticksUntilNextAttack = this.attackCooldown;
    }

    /**
     * Calculate attack reach squared, accounting for entity widths.
     * Mirrors vanilla MeleeAttackGoal reach calculation.
     */
    private double getAttackReachSq(LivingEntity target) {
        double width = this.squire.getBbWidth();
        return width * 2.0D * width * 2.0D + target.getBbWidth();
    }
}
