package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.util.SquireAbilities;
import com.sjviklabs.squire.util.SquireEquipmentHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;

/**
 * Handles combat approach, attack cooldown, leash distance, and reach calculation.
 * One instance per squire, holds combat timing state.
 */
public class CombatHandler {

    private static final double RANGED_MIN_DIST = 4.0; // blocks; closer than this = switch to melee

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
        var log = squire.getActivityLog();
        if (log != null) {
            LivingEntity target = squire.getTarget();
            String targetName = target != null ? target.getType().toShortString() : "unknown";
            log.log("COMBAT", "Engaging " + targetName);
        }
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
        if (isLeashBreached(s)) {
            disengageCombat(s);
            return SquireAIState.IDLE;
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
            if (hitLanded) {
                // Melee hit sound — strong attack sweep
                s.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F,
                        s.level().getRandom().nextFloat() * 0.1F + 0.9F);

                // Lifesteal: heal 10% of damage dealt (level-gated)
                if (SquireAbilities.hasLifesteal(s)) {
                    float dmg = (float) s.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    float heal = dmg * 0.1F;
                    if (heal > 0) {
                        s.heal(heal);
                    }
                }

                var log = s.getActivityLog();
                if (!target.isAlive()) {
                    // Kill confirmation — crit sound + chat
                    s.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
                    if (s.getSquireAI() != null) s.getSquireAI().getChat().onKill();
                    s.getProgression().addKillXP();
                    if (log != null) {
                        log.log("COMBAT", "Killed " + target.getType().toShortString()
                                + " at " + target.blockPosition().toShortString());
                    }
                } else if (log != null) {
                    log.log("COMBAT", "Hit " + target.getType().toShortString()
                            + " (HP: " + String.format("%.1f", target.getHealth()) + ")");
                }
            }
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    /**
     * Per-tick ranged combat: maintain optimal distance, shoot on cooldown.
     * Returns COMBAT_APPROACH if target closes to melee range, IDLE if combat ends.
     */
    public SquireAIState tickRanged(SquireEntity s) {
        LivingEntity target = s.getTarget();
        if (target == null || !target.isAlive()) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }
        if (s.isOrderedToSit()) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        // No arrows left — fall back to melee and swap weapons
        if (!s.hasArrows()) {
            SquireEquipmentHelper.switchToMeleeLoadout(s);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Owner leash
        if (isLeashBreached(s)) {
            disengageCombat(s);
            return SquireAIState.IDLE;
        }

        double distSq = s.distanceToSqr(target);
        double optimalRange = SquireConfig.rangedOptimalRange.get();

        // Too close — switch to melee and immediately swap weapons
        if (distSq < RANGED_MIN_DIST * RANGED_MIN_DIST) {
            SquireEquipmentHelper.switchToMeleeLoadout(s);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Too far (>optimalRange + 5) — approach
        double maxRange = optimalRange + 5.0;
        if (distSq > maxRange * maxRange) {
            s.getNavigation().moveTo(target, 1.0D);
        } else if (distSq < (optimalRange - 3.0) * (optimalRange - 3.0)) {
            // Too close — actively retreat away from target
            double dx = s.getX() - target.getX();
            double dz = s.getZ() - target.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.01) {
                double retreatDist = 5.0;
                double rx = s.getX() + (dx / len) * retreatDist;
                double rz = s.getZ() + (dz / len) * retreatDist;
                s.getNavigation().moveTo(rx, s.getY(), rz, 1.3D);
            }
        } else {
            // In optimal range — stop and aim
            s.getNavigation().stop();
        }

        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Shoot on cooldown if line of sight
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        if (ticksUntilNextAttack <= 0 && s.getSensing().hasLineOfSight(target)) {
            float distanceFactor = (float) Math.sqrt(distSq) / 20.0F;
            s.performRangedAttack(target, Math.min(distanceFactor, 1.0F));
            ticksUntilNextAttack = SquireConfig.rangedCooldownTicks.get();

            var log = s.getActivityLog();
            if (log != null) {
                log.log("COMBAT", "Shot arrow at " + target.getType().toShortString()
                        + " (range: " + String.format("%.1f", Math.sqrt(distSq)) + ")");
            }
        }

        return SquireAIState.COMBAT_RANGED;
    }

    /**
     * Determine if we should use ranged combat mode.
     * Requires: ranged ability unlocked, arrows available, target beyond melee range.
     */
    public boolean shouldUseRanged() {
        if (!SquireAbilities.hasRangedCombat(squire) || !squire.hasArrows()) {
            return false;
        }
        // Don't enter ranged if target is already in melee range
        LivingEntity target = squire.getTarget();
        if (target != null) {
            double distSq = squire.distanceToSqr(target);
            if (distSq < RANGED_MIN_DIST * RANGED_MIN_DIST) {
                return false;
            }
        }
        return true;
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

    private boolean isLeashBreached(SquireEntity s) {
        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner == null) return false;
        double leash = SquireConfig.combatLeashDistance.get();
        return s.distanceToSqr(owner) > leash * leash;
    }

    private void disengageCombat(SquireEntity s) {
        s.setTarget(null);
        s.getNavigation().stop();
    }

    private double getAttackReachSq(LivingEntity target) {
        // Standard melee reach: 1.5 block minimum so the squire can land hits
        // without standing inside the target's hitbox
        double baseReach = 1.5;
        double width = squire.getBbWidth();
        double vanillaReach = Math.sqrt(width * 2.0D * width * 2.0D + target.getBbWidth());
        double reach = Math.max(baseReach, vanillaReach);
        return reach * reach;
    }
}
