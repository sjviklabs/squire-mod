package com.sjviklabs.squire.ai.statemachine;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import com.sjviklabs.squire.util.SquireEquipmentHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Registers all AI transitions for the squire, bridging entity to tick-rate
 * state machine. Replaces vanilla Goal-based AI for all custom behaviors.
 *
 * Target selection still uses vanilla targetSelector goals (OwnerHurtByTargetGoal,
 * OwnerHurtTargetGoal, HurtByTargetGoal) — they set getTarget() which this
 * class reads. FloatGoal is also kept in the vanilla system.
 *
 * Priority layers: survival (1-9) > combat (10-19) > eating (20-29) >
 * follow (30-39) > pickup (40-49) > cosmetic (50+)
 */
public class SquireAI {

    private final SquireEntity squire;
    private final TickRateStateMachine machine;

    // -- Combat state --
    private int ticksUntilNextAttack;
    private int attackCooldown;
    private static final double ATTACK_REACH_SQ = 4.0D;

    // -- Eating state --
    private int eatingTicks;
    private int foodSlot = -1;
    private int nutritionValue;
    private static final int EAT_DURATION = 32;

    // -- Follow state --
    private int pathRecalcTimer;

    // -- Pickup state --
    private ItemEntity targetItem;
    private static final double PICKUP_DIST_SQ = 4.0D;

    public SquireAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine();
        registerTransitions();
    }

    public TickRateStateMachine getMachine() {
        return machine;
    }

    public void tick() {
        machine.tick(squire);
    }

    // ================================================================
    // Transition registration
    // ================================================================

    private void registerTransitions() {
        registerSittingTransitions();
        registerCombatTransitions();
        registerEatingTransitions();
        registerFollowTransitions();
        registerPickupTransitions();
        registerIdleTransitions();
    }

    // ---- Sitting (global, priority 1) ----

    private void registerSittingTransitions() {
        // Any state → SITTING when ordered to sit
        machine.addTransition(new AITransition(
                null,
                () -> machine.getCurrentState() != SquireAIState.SITTING
                        && squire.isTame()
                        && squire.isOrderedToSit()
                        && !squire.isInWaterOrBubble(),
                s -> {
                    s.getNavigation().stop();
                    s.setInSittingPose(true);
                    return SquireAIState.SITTING;
                },
                1, 1
        ));

        // SITTING → IDLE when un-sat
        machine.addTransition(new AITransition(
                SquireAIState.SITTING,
                () -> !squire.isOrderedToSit(),
                s -> {
                    s.setInSittingPose(false);
                    return SquireAIState.IDLE;
                },
                1, 1
        ));
    }

    // ---- Combat (global, priority 10) ----

    private void registerCombatTransitions() {
        // Any non-combat state → COMBAT_APPROACH when target acquired
        machine.addTransition(new AITransition(
                null,
                () -> {
                    SquireAIState state = machine.getCurrentState();
                    if (state == SquireAIState.COMBAT_APPROACH || state == SquireAIState.COMBAT_ATTACK)
                        return false;
                    if (squire.isOrderedToSit()) return false;
                    LivingEntity target = squire.getTarget();
                    return target != null && target.isAlive();
                },
                s -> {
                    s.setSquireSprinting(false);
                    ticksUntilNextAttack = 0;
                    recalculateAttackCooldown();
                    return SquireAIState.COMBAT_APPROACH;
                },
                1, 10
        ));

        // COMBAT_APPROACH: path + attack loop
        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_APPROACH,
                () -> true,
                this::tickCombat,
                1, 10
        ));
    }

    private SquireAIState tickCombat(SquireEntity s) {
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
            s.doHurtTarget(target);
            recalculateAttackCooldown();
        }

        return SquireAIState.COMBAT_APPROACH;
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

    // ---- Eating (global, priority 20) ----

    private void registerEatingTransitions() {
        // Any state → EATING when low health and food available (check every 20 ticks)
        machine.addTransition(new AITransition(
                null,
                () -> {
                    if (machine.getCurrentState() == SquireAIState.EATING) return false;
                    if (squire.isOrderedToSit()) return false;
                    float ratio = squire.getHealth() / squire.getMaxHealth();
                    if (ratio >= SquireConfig.eatHealthThreshold.get()) return false;
                    return findBestFood();
                },
                s -> {
                    eatingTicks = EAT_DURATION;
                    s.getNavigation().stop();
                    return SquireAIState.EATING;
                },
                20, 20
        ));

        // EATING: done → IDLE
        machine.addTransition(new AITransition(
                SquireAIState.EATING,
                () -> eatingTicks <= 0 || foodSlot < 0,
                s -> {
                    foodSlot = -1;
                    nutritionValue = 0;
                    return SquireAIState.IDLE;
                },
                1, 19
        ));

        // EATING: tick animation + consume
        machine.addTransition(new AITransition(
                SquireAIState.EATING,
                () -> eatingTicks > 0 && foodSlot >= 0,
                this::tickEating,
                1, 20
        ));
    }

    private SquireAIState tickEating(SquireEntity s) {
        s.getNavigation().stop();
        eatingTicks--;

        // Eating particles every 4 ticks
        if (eatingTicks > 0 && eatingTicks % 4 == 0) {
            spawnEatingParticles(s);
        }

        // Consume when done
        if (eatingTicks <= 0) {
            consumeFood(s);
        }

        return SquireAIState.EATING;
    }

    private boolean findBestFood() {
        SquireInventory inventory = squire.getSquireInventory();
        int bestSlot = -1;
        int bestNutrition = 0;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            int nutrition = food.nutrition();
            if (nutrition > bestNutrition) {
                bestNutrition = nutrition;
                bestSlot = i;
            }
        }

        if (bestSlot >= 0) {
            foodSlot = bestSlot;
            nutritionValue = bestNutrition;
            return true;
        }
        return false;
    }

    private void consumeFood(SquireEntity s) {
        if (foodSlot < 0) return;
        SquireInventory inv = s.getSquireInventory();
        ItemStack stack = inv.getItem(foodSlot);
        if (!stack.isEmpty()) {
            inv.removeItem(foodSlot, 1);
            s.heal((float) nutritionValue);
        }
        foodSlot = -1;
    }

    private void spawnEatingParticles(SquireEntity s) {
        if (foodSlot < 0) return;
        ItemStack stack = s.getSquireInventory().getItem(foodSlot);
        if (stack.isEmpty()) return;

        if (s.level() instanceof ServerLevel serverLevel) {
            ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, stack);
            double mouthY = s.getY() + s.getEyeHeight() - 0.1;
            serverLevel.sendParticles(particle,
                    s.getX(), mouthY, s.getZ(),
                    3, 0.15, 0.05, 0.15, 0.02);
        }
    }

    // ---- Follow (priority 30) ----

    private void registerFollowTransitions() {
        // IDLE → FOLLOWING_OWNER when owner is far (check every 10 ticks)
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                () -> {
                    if (squire.isOrderedToSit()) return false;
                    Player owner = squire.getOwner() instanceof Player p ? p : null;
                    if (owner == null || !owner.isAlive() || owner.isSpectator()) return false;
                    double dist = SquireConfig.followStartDistance.get();
                    return squire.distanceToSqr(owner) >= dist * dist;
                },
                s -> {
                    pathRecalcTimer = 0; // path immediately on first tick
                    if (s.getNavigation() instanceof GroundPathNavigation groundNav) {
                        groundNav.setCanOpenDoors(true);
                        groundNav.setCanPassDoors(true);
                    }
                    return SquireAIState.FOLLOWING_OWNER;
                },
                10, 30
        ));

        // FOLLOWING_OWNER → IDLE when close enough or invalid
        machine.addTransition(new AITransition(
                SquireAIState.FOLLOWING_OWNER,
                () -> {
                    if (squire.isOrderedToSit()) return true;
                    Player owner = squire.getOwner() instanceof Player p ? p : null;
                    if (owner == null || !owner.isAlive() || owner.isSpectator()) return true;
                    double dist = SquireConfig.followStopDistance.get();
                    return squire.distanceToSqr(owner) <= dist * dist;
                },
                s -> {
                    s.setSquireSprinting(false);
                    s.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                1, 29
        ));

        // FOLLOWING_OWNER: tick — path and sprint
        machine.addTransition(new AITransition(
                SquireAIState.FOLLOWING_OWNER,
                () -> {
                    Player owner = squire.getOwner() instanceof Player p ? p : null;
                    return owner != null && owner.isAlive() && !squire.isOrderedToSit();
                },
                this::tickFollow,
                1, 30
        ));
    }

    private SquireAIState tickFollow(SquireEntity s) {
        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner == null) return SquireAIState.IDLE;

        // Look at owner every tick (smooth head tracking)
        s.getLookControl().setLookAt(owner, 10.0F, (float) s.getMaxHeadXRot());

        // Throttle path recalculation to match original SquireFollowOwnerGoal
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

    // ---- Pickup (priority 40) ----

    private void registerPickupTransitions() {
        // IDLE → PICKING_UP_ITEM when items nearby (check every 40 ticks)
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                () -> {
                    if (squire.isOrderedToSit()) return false;
                    if (squire.getTarget() != null) return false;
                    return findClosestItem();
                },
                s -> {
                    if (targetItem != null) {
                        s.getNavigation().moveTo(targetItem, 1.0D);
                    }
                    return SquireAIState.PICKING_UP_ITEM;
                },
                40, 40
        ));

        // PICKING_UP_ITEM → IDLE when target lost
        machine.addTransition(new AITransition(
                SquireAIState.PICKING_UP_ITEM,
                () -> targetItem == null || !targetItem.isAlive(),
                s -> {
                    targetItem = null;
                    s.getNavigation().stop();
                    return SquireAIState.IDLE;
                },
                1, 39
        ));

        // PICKING_UP_ITEM: tick — walk and grab
        machine.addTransition(new AITransition(
                SquireAIState.PICKING_UP_ITEM,
                () -> targetItem != null && targetItem.isAlive(),
                this::tickPickup,
                1, 40
        ));
    }

    private boolean findClosestItem() {
        double range = SquireConfig.itemPickupRange.get();
        AABB box = squire.getBoundingBox().inflate(range);

        List<ItemEntity> items = squire.level().getEntitiesOfClass(
                ItemEntity.class, box,
                item -> item.isAlive() && !item.hasPickUpDelay()
        );
        if (items.isEmpty()) return false;

        ItemEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            if (!squire.getSquireInventory().canAddItem(item.getItem())) continue;
            double dist = squire.distanceToSqr(item);
            if (dist < closestDist) {
                closestDist = dist;
                closest = item;
            }
        }
        targetItem = closest;
        return closest != null;
    }

    private SquireAIState tickPickup(SquireEntity s) {
        if (targetItem == null) return SquireAIState.IDLE;

        s.getLookControl().setLookAt(targetItem);
        s.getNavigation().moveTo(targetItem, 1.0D);

        if (s.distanceToSqr(targetItem) < PICKUP_DIST_SQ) {
            pickUpItem(s);
            return SquireAIState.IDLE;
        }

        // Give up if item drifted too far
        double maxRange = SquireConfig.itemPickupRange.get() * 2.0;
        if (s.distanceToSqr(targetItem) > maxRange * maxRange) {
            targetItem = null;
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        return SquireAIState.PICKING_UP_ITEM;
    }

    private void pickUpItem(SquireEntity s) {
        if (targetItem == null || !targetItem.isAlive()) return;

        ItemStack stack = targetItem.getItem().copy();
        ItemStack remainder = s.getSquireInventory().addItem(stack);

        if (remainder.isEmpty()) {
            targetItem.discard();
        } else {
            targetItem.setItem(remainder);
        }

        SquireEquipmentHelper.tryAutoEquip(s, stack);
        targetItem = null;
    }

    // ---- Idle cosmetics (priority 50) ----

    private void registerIdleTransitions() {
        // IDLE: periodically look at nearest player (cosmetic, stays in IDLE)
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                () -> !squire.isOrderedToSit(),
                s -> {
                    Player player = s.level().getNearestPlayer(s, 8.0);
                    if (player != null) {
                        s.getLookControl().setLookAt(player, 10.0F,
                                (float) s.getMaxHeadXRot());
                    }
                    return SquireAIState.IDLE;
                },
                20, 50
        ));
    }
}
