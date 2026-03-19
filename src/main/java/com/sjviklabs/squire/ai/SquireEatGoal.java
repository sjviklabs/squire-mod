package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Eat food from inventory when health drops below a configurable threshold.
 * The squire stands still during the eating animation, then heals for the food's nutrition value.
 */
public class SquireEatGoal extends Goal {

    private final SquireEntity squire;

    /** Ticks between health checks when not eating. */
    private static final int CHECK_INTERVAL = 20;

    /** Duration of eating animation in ticks (~1.6 seconds, matching player eat time). */
    private static final int EAT_DURATION = 32;

    private int checkCooldown;
    private int eatingTicks;
    private int foodSlot = -1;
    private int nutritionValue;

    public SquireEatGoal(SquireEntity squire) {
        this.squire = squire;
        // Block movement while eating — squire stands still
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.squire.isOrderedToSit()) return false;

        if (--this.checkCooldown > 0) return false;
        this.checkCooldown = CHECK_INTERVAL;

        float healthRatio = this.squire.getHealth() / this.squire.getMaxHealth();
        if (healthRatio >= SquireConfig.eatHealthThreshold.get()) return false;

        return findBestFood();
    }

    @Override
    public boolean canContinueToUse() {
        // Continue until eating animation completes
        return this.eatingTicks > 0 && this.foodSlot >= 0;
    }

    @Override
    public void start() {
        this.eatingTicks = EAT_DURATION;
        this.squire.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.eatingTicks = 0;
        this.foodSlot = -1;
        this.nutritionValue = 0;
    }

    @Override
    public void tick() {
        // Stop moving while eating
        this.squire.getNavigation().stop();

        this.eatingTicks--;

        // Spawn eating particles every few ticks during the animation
        if (this.eatingTicks > 0 && this.eatingTicks % 4 == 0) {
            spawnEatingParticles();
        }

        // Eating complete — consume item and heal
        if (this.eatingTicks <= 0) {
            consumeFood();
        }
    }

    /**
     * Find the best (highest nutrition) food item in the squire's inventory.
     *
     * @return true if food was found
     */
    private boolean findBestFood() {
        SquireInventory inventory = this.squire.getSquireInventory();
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
            this.foodSlot = bestSlot;
            this.nutritionValue = bestNutrition;
            return true;
        }
        return false;
    }

    /**
     * Consume one food item from the selected slot and heal the squire.
     */
    private void consumeFood() {
        if (this.foodSlot < 0) return;

        SquireInventory inventory = this.squire.getSquireInventory();
        ItemStack foodStack = inventory.getItem(this.foodSlot);

        if (!foodStack.isEmpty()) {
            // Remove one item
            inventory.removeItem(this.foodSlot, 1);

            // Heal for the food's nutrition value
            this.squire.heal((float) this.nutritionValue);
        }

        this.foodSlot = -1;
    }

    /**
     * Spawn eating particles visible to nearby clients.
     * Uses ServerLevel.sendParticles() for reliable server-side particle broadcasting.
     */
    private void spawnEatingParticles() {
        if (this.foodSlot < 0) return;

        ItemStack foodStack = this.squire.getSquireInventory().getItem(this.foodSlot);
        if (foodStack.isEmpty()) return;

        if (this.squire.level() instanceof ServerLevel serverLevel) {
            ItemParticleOption particleData = new ItemParticleOption(ParticleTypes.ITEM, foodStack);
            double mouthY = this.squire.getY() + this.squire.getEyeHeight() - 0.1;

            serverLevel.sendParticles(
                    particleData,
                    this.squire.getX(),
                    mouthY,
                    this.squire.getZ(),
                    3,       // particle count
                    0.15,    // x spread
                    0.05,    // y spread
                    0.15,    // z spread
                    0.02     // speed
            );
        }
    }
}
