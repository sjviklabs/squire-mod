package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Handles combat approach, attack cooldown, leash distance, and reach calculation.
 * One instance per squire, holds combat timing state.
 */
public class CombatHandler {

    private final SquireEntity squire;
    private int ticksUntilNextAttack;
    private int attackCooldown;

    public CombatHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /** Called when entering combat. Resets attack cooldown. */
    public void start() {
        squire.setSquireSprinting(false);
        ticksUntilNextAttack = 0;
        recalculateAttackCooldown();
    }

    /**
     * Per-tick combat logic: path toward target, attack when in range.
     * Returns IDLE if combat should end, COMBAT_APPROACH to continue.
     */
    public SquireAIState tick(SquireEntity s) {
        LivingEntity target = s.getTarget();
        if (target == null || !target.isAlive()) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }
        if (s.isOrderedToSit()) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        // Owner leash — disengage if too far from owner
        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner != null) {
            double leash = SquireConfig.combatLeashDistance.get();
            if (s.distanceToSqr(owner) > leash * leash) {
                s.setTarget(null);
                s.getNavigation().stop();
                return SquireAIState.IDLE;
            }
        }

        // Follow range — give up if target too far
        double followRange = s.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (s.distanceToSqr(target) > followRange * followRange) {
            s.setTarget(null);
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        // Look + path toward target
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);
        s.getNavigation().moveTo(target, 1.2D);

        // Attack cooldown
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);

        // Range check — attack if close enough and cooldown ready
        double distSq = s.distanceToSqr(
                target.getX(), target.getBoundingBox().minY, target.getZ());
        double reach = getAttackReachSq(target);

        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            s.swing(InteractionHand.MAIN_HAND);
            boolean hitLanded = s.doHurtTarget(target);
            recalculateAttackCooldown();
            if (hitLanded && !target.isAlive()) {
                s.getProgression().addKillXP();
                var log = s.getActivityLog();
                if (log != null) {
                    log.log("COMBAT", "Killed " + target.getType().toShortString()
                            + " at " + target.blockPosition().toShortString());
                }
            }
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    /** Whether the squire has a valid combat target. */
    public boolean hasTarget() {
        LivingEntity target = squire.getTarget();
        return target != null && target.isAlive();
    }

    private void recalculateAttackCooldown() {
        double speed = squire.getAttributeValue(Attributes.ATTACK_SPEED);
        speed = Math.max(0.5D, Math.min(4.0D, speed));
        attackCooldown = (int) Math.ceil(20.0D / speed);
        ticksUntilNextAttack = attackCooldown;
    }

    private double getAttackReachSq(LivingEntity target) {
        double width = squire.getBbWidth();
        return width * 2.0D * width * 2.0D + target.getBbWidth();
    }
}
