package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.util.SquireEquipmentHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Handles item pickup: scanning for nearby items, walking to them, picking up.
 * One instance per squire, holds target item reference.
 */
public class ItemHandler {

    private final SquireEntity squire;
    private ItemEntity targetItem;

    private static final double PICKUP_DIST_SQ = 4.0D;

    public ItemHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /** Scan for nearby items the inventory can hold. */
    public boolean findClosestItem() {
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

    /** Whether there's a valid pickup target. */
    public boolean hasTarget() {
        return targetItem != null && targetItem.isAlive();
    }

    /** Called when entering PICKING_UP_ITEM state. */
    public void start() {
        if (targetItem != null) {
            squire.getNavigation().moveTo(targetItem, 1.0D);
        }
    }

    /** Clear target and stop navigation. */
    public void stop() {
        targetItem = null;
        squire.getNavigation().stop();
    }

    /** Per-tick pickup logic: walk toward item, pick up when close. */
    public SquireAIState tick(SquireEntity s) {
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
            stop();
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
}
