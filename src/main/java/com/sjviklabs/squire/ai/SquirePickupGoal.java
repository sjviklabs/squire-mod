package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.util.SquireEquipmentHelper;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * Scan for dropped items, walk to them, pick them up into inventory,
 * and trigger auto-equip evaluation on each pickup.
 */
public class SquirePickupGoal extends Goal {

    private final SquireEntity squire;
    private ItemEntity targetItem;
    private int scanCooldown;

    /** Pick up when within this distance (squared). */
    private static final double PICKUP_DIST_SQ = 4.0D; // 2.0 blocks squared

    public SquirePickupGoal(SquireEntity squire) {
        this.squire = squire;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.squire.isOrderedToSit()) return false;
        if (this.squire.getTarget() != null) return false;

        if (--this.scanCooldown > 0) return false;
        this.scanCooldown = SquireConfig.itemScanInterval.get();

        return findClosestItem();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetItem == null || !this.targetItem.isAlive()) return false;
        if (this.squire.isOrderedToSit()) return false;
        if (this.squire.getTarget() != null) return false;

        // Give up if the item drifted too far (2x pickup range)
        double maxRange = SquireConfig.itemPickupRange.get() * 2.0;
        return this.squire.distanceToSqr(this.targetItem) < maxRange * maxRange;
    }

    @Override
    public void start() {
        if (this.targetItem != null) {
            this.squire.getNavigation().moveTo(this.targetItem, 1.0D);
        }
    }

    @Override
    public void stop() {
        this.targetItem = null;
        this.squire.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetItem == null) return;

        this.squire.getLookControl().setLookAt(this.targetItem);
        this.squire.getNavigation().moveTo(this.targetItem, 1.0D);

        // Check if close enough to pick up
        if (this.squire.distanceToSqr(this.targetItem) < PICKUP_DIST_SQ) {
            pickUpItem();
        }
    }

    /**
     * Find the closest item entity within pickup range that the inventory can hold.
     *
     * @return true if a valid target was found
     */
    private boolean findClosestItem() {
        double range = SquireConfig.itemPickupRange.get();
        AABB searchBox = this.squire.getBoundingBox().inflate(range);

        List<ItemEntity> items = this.squire.level().getEntitiesOfClass(
                ItemEntity.class,
                searchBox,
                item -> item.isAlive() && !item.hasPickUpDelay()
        );

        if (items.isEmpty()) return false;

        ItemEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (ItemEntity item : items) {
            if (!this.squire.getSquireInventory().canAddItem(item.getItem())) continue;

            double distSq = this.squire.distanceToSqr(item);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = item;
            }
        }

        this.targetItem = closest;
        return closest != null;
    }

    /**
     * Pick up the target item into inventory. Any remainder stays on the ground.
     * Triggers auto-equip evaluation after a successful pickup.
     */
    private void pickUpItem() {
        if (this.targetItem == null || !this.targetItem.isAlive()) return;

        ItemStack stack = this.targetItem.getItem().copy();
        ItemStack remainder = this.squire.getSquireInventory().addItem(stack);

        if (remainder.isEmpty()) {
            this.targetItem.discard();
        } else {
            this.targetItem.setItem(remainder);
        }

        // Trigger auto-equip for the picked up item (armor, weapons, shields)
        SquireEquipmentHelper.tryAutoEquip(this.squire, stack);

        this.targetItem = null;
    }
}
