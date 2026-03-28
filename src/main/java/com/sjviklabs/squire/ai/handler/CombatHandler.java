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
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;

/**
 * Handles combat approach, attack cooldown, leash distance, and reach calculation.
 * Selects a tactic based on mob type and drives behavior per-tick accordingly.
 * One instance per squire, holds combat timing and tactic state.
 */
public class CombatHandler {

    private static final double RANGED_MIN_DIST = 4.0; // blocks; closer than this = switch to melee

    // ---- Tactic enum ----

    public enum CombatTactic {
        AGGRESSIVE,  // zombies, spiders, husks, hoglin — close fast, circle-strafe, reactive shield
        CAUTIOUS,    // skeletons, strays, pillagers — shield up while approaching, sprint to close gap
        EVASIVE,     // witches, evokers, blazes — prefer ranged, hit-and-run if melee, dodge
        EXPLOSIVE,   // creepers — ranged only if possible, flee if swelling
        PASSIVE,     // endermen — don't look at it, only fight if attacked first
        DEFAULT      // unknown/modded mobs — current basic melee behavior
    }

    private final SquireEntity squire;
    private int ticksUntilNextAttack;
    private int attackCooldown;

    // ---- Tactic state ----
    private CombatTactic currentTactic = CombatTactic.DEFAULT;
    private int shieldUpTicks;
    private int strafeDirection;  // 1 or -1
    private int strafeTicks;      // countdown for current strafe movement
    private int hitAndRunCooldown; // ticks before re-engaging after hit-and-run
    private int dodgeCooldown;     // ticks until next evasive sidestep
    private int swingWindowTicks;  // ticks remaining in cautious swing window
    private boolean lastHitLanded;

    public CombatHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /** Called when entering combat. Resets attack cooldown and selects tactic. */
    public void start() {
        squire.setSquireSprinting(false);
        ticksUntilNextAttack = 0;
        recalculateAttackCooldown();
        resetTacticState();

        LivingEntity target = squire.getTarget();
        if (target != null) {
            this.currentTactic = selectTactic(target);
        }

        var log = squire.getActivityLog();
        if (log != null) {
            String targetName = target != null ? target.getType().toShortString() : "unknown";
            log.log("COMBAT", "Engaging " + targetName + " [" + currentTactic + "]");
        }

        // Fire tactic-specific chat line
        fireTacticChat();
    }

    // ---- Tactic selection ----

    private CombatTactic selectTactic(LivingEntity target) {
        if (target instanceof Creeper) return CombatTactic.EXPLOSIVE;
        if (target instanceof EnderMan) return CombatTactic.PASSIVE;
        if (target instanceof Witch || target instanceof Evoker || target instanceof Blaze)
            return CombatTactic.EVASIVE;
        if (target instanceof AbstractSkeleton || target instanceof Pillager)
            return CombatTactic.CAUTIOUS;
        if (target instanceof Zombie || target instanceof Spider || target instanceof Hoglin)
            return CombatTactic.AGGRESSIVE;
        return CombatTactic.DEFAULT;
    }

    private void resetTacticState() {
        shieldUpTicks = 0;
        strafeDirection = 0;
        strafeTicks = 0;
        hitAndRunCooldown = 0;
        dodgeCooldown = 0;
        swingWindowTicks = 0;
        lastHitLanded = false;
    }

    private void fireTacticChat() {
        if (squire.getSquireAI() == null) return;
        ChatHandler chat = squire.getSquireAI().getChat();
        if (chat == null) return;

        String key = "squire.combat.tactic." + currentTactic.name().toLowerCase();
        chat.sayTranslatable(key);
    }

    // ---- Shield management (called every tick during combat) ----

    private void updateShield(SquireEntity s) {
        if (!SquireAbilities.hasShieldBlock(s)) return;
        if (!SquireEquipmentHelper.isShield(s.getOffhandItem())) return;

        if (shieldUpTicks > 0) {
            if (shieldUpTicks < Integer.MAX_VALUE) shieldUpTicks--;
            if (!s.isUsingItem()) {
                s.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else {
            if (s.isUsingItem() && s.getUsedItemHand() == InteractionHand.OFF_HAND) {
                s.stopUsingItem();
            }
        }
    }

    /**
     * Called when the squire takes damage. Triggers reactive shield for
     * AGGRESSIVE and DEFAULT tactics.
     */
    public void onDamageTaken() {
        if (currentTactic == CombatTactic.AGGRESSIVE || currentTactic == CombatTactic.DEFAULT) {
            shieldUpTicks = SquireConfig.shieldBlockDuration.get();
        }
    }

    /** Current tactic, exposed for debug/logging. */
    public CombatTactic getCurrentTactic() {
        return currentTactic;
    }

    // ================================================================
    // Per-tick melee combat — dispatches to tactic-specific behavior
    // ================================================================

    /**
     * Per-tick combat logic: delegates to tactic-specific method.
     * Returns IDLE if combat should end, COMBAT_APPROACH to continue.
     */
    public SquireAIState tick(SquireEntity s) {
        LivingEntity target = s.getTarget();
        if (target == null || !target.isAlive()) {
            s.getNavigation().stop();
            shieldUpTicks = 0;
            return SquireAIState.IDLE;
        }
        if (s.isOrderedToSit()) {
            s.getNavigation().stop();
            shieldUpTicks = 0;
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
            shieldUpTicks = 0;
            return SquireAIState.IDLE;
        }

        // Update shield state every tick
        updateShield(s);

        // Attack cooldown
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);

        // Dispatch to tactic
        return switch (currentTactic) {
            case AGGRESSIVE -> tickAggressive(s, target);
            case CAUTIOUS -> tickCautious(s, target);
            case EVASIVE -> tickEvasive(s, target);
            case EXPLOSIVE -> tickExplosive(s, target);
            case PASSIVE -> tickPassive(s, target);
            default -> tickDefault(s, target);
        };
    }

    // ---- AGGRESSIVE: sprint in, swing, circle-strafe after hit, reactive shield ----

    private SquireAIState tickAggressive(SquireEntity s, LivingEntity target) {
        // Circle-strafe if active
        if (strafeTicks > 0) {
            strafeTicks--;
            movePerpendicularToTarget(s, target);
            // Still try to attack during strafe if in range
            tryMeleeAttack(s, target);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Look + sprint toward target
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);
        s.getNavigation().moveTo(target, 1.2D);

        if (tryMeleeAttack(s, target) && lastHitLanded) {
            // Start circle-strafe after landing a hit
            int baseDuration = SquireConfig.circleStrafeDuration.get();
            strafeDirection = s.getRandom().nextBoolean() ? 1 : -1;
            strafeTicks = baseDuration + s.getRandom().nextInt(6);
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- CAUTIOUS: shield up while approaching, lower to swing, sprint to close ----

    private SquireAIState tickCautious(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSq = s.distanceToSqr(target);
        double reach = getAttackReachSq(target);

        // Sprint to close gap
        s.getNavigation().moveTo(target, 1.3D);

        // If in swing window, try to attack then re-raise shield
        if (swingWindowTicks > 0) {
            swingWindowTicks--;
            tryMeleeAttack(s, target);
            if (swingWindowTicks <= 0) {
                // Swing window closed — re-raise shield
                shieldUpTicks = Integer.MAX_VALUE;
            }
            return SquireAIState.COMBAT_APPROACH;
        }

        // Shield up while approaching
        if (SquireAbilities.hasShieldBlock(s) && SquireEquipmentHelper.isShield(s.getOffhandItem())) {
            shieldUpTicks = Integer.MAX_VALUE;
        }

        // In melee range and attack ready — lower shield to swing
        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            shieldUpTicks = 0; // Lower shield
            swingWindowTicks = 5; // 5 ticks to swing
            tryMeleeAttack(s, target);
        }

        // If health < 30% and has ranged capability: retreat to ranged
        if (s.getHealth() / s.getMaxHealth() < 0.3F
                && SquireAbilities.hasRangedCombat(s) && s.hasArrows()) {
            SquireEquipmentHelper.switchToMeleeLoadout(s); // will be swapped to ranged by state machine
            shieldUpTicks = 0;
            return SquireAIState.COMBAT_RANGED;
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- EVASIVE: prefer ranged, hit-and-run if melee, periodic dodge ----

    private SquireAIState tickEvasive(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Prefer ranged if possible
        if (SquireAbilities.hasRangedCombat(s) && s.hasArrows()) {
            shieldUpTicks = 0;
            return SquireAIState.COMBAT_RANGED;
        }

        // No ranged — hit-and-run melee
        double distSq = s.distanceToSqr(target);
        double reach = getAttackReachSq(target);

        // Dodge: sidestep every N ticks
        if (dodgeCooldown > 0) {
            dodgeCooldown--;
        } else {
            dodgeCooldown = SquireConfig.evasiveDodgeInterval.get().intValue();
            strafeDirection = s.getRandom().nextBoolean() ? 1 : -1;
            movePerpendicularToTarget(s, target);
        }

        // Hit-and-run cooldown: retreat phase
        if (hitAndRunCooldown > 0) {
            hitAndRunCooldown--;
            // Shield up while retreating
            if (SquireAbilities.hasShieldBlock(s) && SquireEquipmentHelper.isShield(s.getOffhandItem())) {
                shieldUpTicks = SquireConfig.shieldBlockDuration.get();
            }
            // Move away from target
            retreatFromTarget(s, target, 5.0);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Sprint in to attack
        s.getNavigation().moveTo(target, 1.3D);

        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            tryMeleeAttack(s, target);
            // After 1-2 hits, sprint away
            hitAndRunCooldown = SquireConfig.hitAndRunRetreatTicks.get();
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- EXPLOSIVE: ranged if possible, hit-and-run if not, flee if swelling ----

    private SquireAIState tickExplosive(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // If creeper is swelling and close, disengage — let DangerHandler take over
        if (target instanceof Creeper creeper) {
            double distSq = s.distanceToSqr(target);
            if (creeper.getSwellDir() > 0 && distSq < 36.0) { // 6 blocks squared
                disengageCombat(s);
                shieldUpTicks = 0;
                return SquireAIState.IDLE;
            }
        }

        // Force ranged if available — maintain distance
        if (SquireAbilities.hasRangedCombat(s) && s.hasArrows()) {
            shieldUpTicks = 0;
            return SquireAIState.COMBAT_RANGED;
        }

        // No ranged — hit-and-run: sprint in, hit once, sprint away immediately
        double distSq = s.distanceToSqr(target);
        double reach = getAttackReachSq(target);

        if (hitAndRunCooldown > 0) {
            hitAndRunCooldown--;
            retreatFromTarget(s, target, 8.0);
            return SquireAIState.COMBAT_APPROACH;
        }

        // Sprint in
        s.getNavigation().moveTo(target, 1.4D);

        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            tryMeleeAttack(s, target);
            // Immediately retreat after one hit
            hitAndRunCooldown = SquireConfig.hitAndRunRetreatTicks.get();
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- PASSIVE: don't look at enderman, fight only if provoked ----

    private SquireAIState tickPassive(SquireEntity s, LivingEntity target) {
        // Only fight if the enderman is attacking us or our owner
        boolean provoked = false;
        if (s.getLastHurtByMob() == target) provoked = true;
        if (s.getOwner() instanceof Player owner) {
            if (target.getLastHurtByMob() == owner || owner.getLastHurtByMob() == target) {
                provoked = true;
            }
        }

        if (!provoked) {
            // Don't engage — avoid eye contact, stand still
            s.getNavigation().stop();
            return SquireAIState.COMBAT_APPROACH;
        }

        // Provoked: fight like AGGRESSIVE but look at feet instead of eyes
        s.getLookControl().setLookAt(target.getX(), target.getY() - 1.5, target.getZ(), 30.0F, 30.0F);
        s.getNavigation().moveTo(target, 1.2D);

        // Circle-strafe if active
        if (strafeTicks > 0) {
            strafeTicks--;
            movePerpendicularToTarget(s, target);
            tryMeleeAttack(s, target);
            return SquireAIState.COMBAT_APPROACH;
        }

        if (tryMeleeAttack(s, target) && lastHitLanded) {
            int baseDuration = SquireConfig.circleStrafeDuration.get();
            strafeDirection = s.getRandom().nextBoolean() ? 1 : -1;
            strafeTicks = baseDuration + s.getRandom().nextInt(6);
        }

        return SquireAIState.COMBAT_APPROACH;
    }

    // ---- DEFAULT: basic approach + swing + reactive shield ----

    private SquireAIState tickDefault(SquireEntity s, LivingEntity target) {
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);
        s.getNavigation().moveTo(target, 1.2D);
        tryMeleeAttack(s, target);
        return SquireAIState.COMBAT_APPROACH;
    }

    // ================================================================
    // Melee attack helper — shared by all tactics
    // ================================================================

    /**
     * Attempts a melee attack if in range and cooldown is ready.
     * Returns true if an attack was attempted, sets lastHitLanded.
     */
    private boolean tryMeleeAttack(SquireEntity s, LivingEntity target) {
        double distSq = s.distanceToSqr(
                target.getX(), target.getBoundingBox().minY, target.getZ());
        double reach = getAttackReachSq(target);

        if (distSq <= reach && ticksUntilNextAttack <= 0) {
            s.swing(InteractionHand.MAIN_HAND);
            boolean hitLanded = s.doHurtTarget(target);
            this.lastHitLanded = hitLanded;
            recalculateAttackCooldown();
            if (hitLanded) {
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
            return true;
        }
        return false;
    }

    // ================================================================
    // Movement helpers
    // ================================================================

    /** Move perpendicular to the target (circle-strafe). */
    private void movePerpendicularToTarget(SquireEntity s, LivingEntity target) {
        double dx = target.getX() - s.getX();
        double dz = target.getZ() - s.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.01) {
            double perpX = -dz / len * strafeDirection;
            double perpZ = dx / len * strafeDirection;
            double strafeX = s.getX() + perpX * 2.0;
            double strafeZ = s.getZ() + perpZ * 2.0;
            s.getNavigation().moveTo(strafeX, s.getY(), strafeZ, 1.0D);
        }
    }

    /** Move away from target by retreatDist blocks. */
    private void retreatFromTarget(SquireEntity s, LivingEntity target, double retreatDist) {
        double dx = s.getX() - target.getX();
        double dz = s.getZ() - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.01) {
            double rx = s.getX() + (dx / len) * retreatDist;
            double rz = s.getZ() + (dz / len) * retreatDist;
            s.getNavigation().moveTo(rx, s.getY(), rz, 1.3D);
        }
    }

    // ================================================================
    // Ranged combat (unchanged — tactics that want ranged return COMBAT_RANGED)
    // ================================================================

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

        // EXPLOSIVE tactic: maintain extra distance (>8 blocks minimum)
        double minRangedDist = RANGED_MIN_DIST;
        if (currentTactic == CombatTactic.EXPLOSIVE) {
            minRangedDist = 8.0;
        }

        // Too close — switch to melee (or keep distance for explosive)
        if (distSq < minRangedDist * minRangedDist) {
            if (currentTactic == CombatTactic.EXPLOSIVE) {
                // Don't switch to melee for creepers — retreat instead
                retreatFromTarget(s, target, 8.0);
            } else {
                SquireEquipmentHelper.switchToMeleeLoadout(s);
                return SquireAIState.COMBAT_APPROACH;
            }
        }

        // Too far (>optimalRange + 5) — approach
        double maxRange = optimalRange + 5.0;
        if (distSq > maxRange * maxRange) {
            s.getNavigation().moveTo(target, 1.0D);
        } else if (distSq < (optimalRange - 3.0) * (optimalRange - 3.0)) {
            // Too close — actively retreat away from target
            retreatFromTarget(s, target, 5.0);
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

    // ================================================================
    // Ranged decision
    // ================================================================

    /**
     * Determine if we should use ranged combat mode.
     * Requires: ranged ability unlocked, arrows available, target beyond melee range.
     */
    public boolean shouldUseRanged() {
        if (!SquireAbilities.hasRangedCombat(squire) || !squire.hasArrows()) {
            return false;
        }
        LivingEntity target = squire.getTarget();
        if (target != null) {
            // EVASIVE and EXPLOSIVE tactics prefer ranged when available
            CombatTactic tactic = selectTactic(target);
            if (tactic == CombatTactic.EVASIVE || tactic == CombatTactic.EXPLOSIVE) {
                return true;
            }
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

    // ================================================================
    // Internal helpers
    // ================================================================

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
        shieldUpTicks = 0;
    }

    private double getAttackReachSq(LivingEntity target) {
        double baseReach = 1.5;
        double width = squire.getBbWidth();
        double vanillaReach = Math.sqrt(width * 2.0D * width * 2.0D + target.getBbWidth());
        double reach = Math.max(baseReach, vanillaReach);
        return reach * reach;
    }
}
