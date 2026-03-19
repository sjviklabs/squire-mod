package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Handles eating: food search, eating animation, particle effects, consumption.
 * One instance per squire, holds eating timing state.
 */
public class SurvivalHandler {

    private final SquireEntity squire;

    private int eatingTicks;
    private int foodSlot = -1;
    private int nutritionValue;

    private static final int EAT_DURATION = 32;

    public SurvivalHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /** Check if eating should begin: low health and food available. */
    public boolean shouldEat() {
        if (squire.isOrderedToSit()) return false;
        float ratio = squire.getHealth() / squire.getMaxHealth();
        if (ratio >= SquireConfig.eatHealthThreshold.get()) return false;
        return findBestFood();
    }

    /** Called when entering EATING state. */
    public void startEating() {
        eatingTicks = EAT_DURATION;
        squire.getNavigation().stop();
    }

    /** Whether the eating animation is still active. */
    public boolean isEating() {
        return eatingTicks > 0 && foodSlot >= 0;
    }

    /** Per-tick eating logic. */
    public SquireAIState tick(SquireEntity s) {
        s.getNavigation().stop();
        eatingTicks--;

        if (eatingTicks > 0 && eatingTicks % 4 == 0) {
            spawnEatingParticles(s);
        }

        if (eatingTicks <= 0) {
            consumeFood(s);
        }

        return SquireAIState.EATING;
    }

    /** Reset eating state (called on exit or interruption). */
    public void reset() {
        foodSlot = -1;
        nutritionValue = 0;
        eatingTicks = 0;
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
}
