package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

public class SquirePickupGoal extends Goal {
    private final SquireEntity squire;
    private final double speedModifier;
    private final double pickupRange;
    private ItemEntity targetItem;
    private int cooldown;

    public SquirePickupGoal(SquireEntity squire, double speedModifier, double pickupRange) {
        this.squire = squire;
        this.speedModifier = speedModifier;
        this.pickupRange = pickupRange;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.squire.getOwner() == null) return false;
        if (--this.cooldown > 0) return false;
        if (this.squire.getTarget() != null) return false; // Don't pick up items while fighting

        List<ItemEntity> items = this.squire.level().getEntitiesOfClass(
                ItemEntity.class,
                this.squire.getBoundingBox().inflate(pickupRange),
                item -> item.isAlive() && !item.hasPickUpDelay()
        );

        if (items.isEmpty()) {
            this.cooldown = 20; // Wait 1 second before scanning again
            return false;
        }

        // Find closest item
        this.targetItem = null;
        double closestDist = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            // Only pick up items the inventory can hold
            if (!this.squire.getSquireInventory().canAddItem(item.getItem())) continue;
            double dist = this.squire.distanceToSqr(item);
            if (dist < closestDist) {
                closestDist = dist;
                this.targetItem = item;
            }
        }

        return this.targetItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetItem != null && this.targetItem.isAlive()
                && this.squire.distanceToSqr(this.targetItem) < pickupRange * pickupRange * 4;
    }

    @Override
    public void tick() {
        if (this.targetItem == null) return;

        this.squire.getLookControl().setLookAt(this.targetItem);
        this.squire.getNavigation().moveTo(this.targetItem, this.speedModifier);

        // Pick up when close enough
        if (this.squire.distanceToSqr(this.targetItem) < 2.0D) {
            ItemStack remaining = this.squire.getSquireInventory().addItem(this.targetItem.getItem().copy());
            if (remaining.isEmpty()) {
                this.targetItem.discard();
            } else {
                this.targetItem.setItem(remaining);
            }
            this.targetItem = null;
        }
    }

    @Override
    public void stop() {
        this.targetItem = null;
        this.squire.getNavigation().stop();
    }
}
