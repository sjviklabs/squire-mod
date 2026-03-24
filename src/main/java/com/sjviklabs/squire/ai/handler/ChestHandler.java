package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import com.sjviklabs.squire.util.SquireAbilities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

import javax.annotation.Nullable;

/**
 * Handles chest deposit and fetch operations.
 * The squire walks to a chest, opens it (sound), transfers items, then returns to idle.
 */
public class ChestHandler {

    public enum ChestAction { STORE, FETCH }

    private final SquireEntity squire;
    private BlockPos targetChest;
    private ChestAction action;
    private String fetchFilter; // item ID substring for fetch, null = all

    public ChestHandler(SquireEntity squire) {
        this.squire = squire;
    }

    public boolean hasTarget() { return targetChest != null; }

    public void clearTarget() {
        targetChest = null;
        action = null;
        fetchFilter = null;
    }

    /**
     * Set a chest target. If pos is null, finds nearest container.
     */
    public boolean setTarget(@Nullable BlockPos pos, ChestAction action, @Nullable String filter) {
        if (!SquireAbilities.hasChestDeposit(squire)) return false;

        BlockPos chest = pos != null ? pos : findNearestChest();
        if (chest == null) return false;

        this.targetChest = chest;
        this.action = action;
        this.fetchFilter = filter;
        return true;
    }

    public SquireAIState tickApproach(SquireEntity s) {
        if (targetChest == null) return SquireAIState.IDLE;

        double distSq = s.distanceToSqr(
                targetChest.getX() + 0.5, targetChest.getY() + 0.5, targetChest.getZ() + 0.5);

        if (distSq <= 4.0) { // Within 2 blocks
            // Chest open sound
            s.playSound(SoundEvents.CHEST_OPEN, 0.5F, 1.0F);
            return SquireAIState.CHEST_INTERACT;
        }

        s.getNavigation().moveTo(
                targetChest.getX() + 0.5, targetChest.getY(), targetChest.getZ() + 0.5, 1.0);
        return SquireAIState.CHEST_APPROACH;
    }

    public SquireAIState tickInteract(SquireEntity s) {
        if (targetChest == null) return SquireAIState.IDLE;

        var blockEntity = s.level().getBlockEntity(targetChest);
        if (!(blockEntity instanceof BaseContainerBlockEntity container)) {
            clearTarget();
            return SquireAIState.IDLE;
        }

        if (action == ChestAction.STORE) {
            depositItems(s, container);
        } else {
            withdrawItems(s, container);
        }

        // Chest close sound
        s.playSound(SoundEvents.CHEST_CLOSE, 0.5F, 1.0F);

        var log = s.getActivityLog();
        if (log != null) {
            log.log("CHEST", (action == ChestAction.STORE ? "Deposited" : "Fetched")
                    + " items at " + targetChest.toShortString());
        }

        clearTarget();
        return SquireAIState.IDLE;
    }

    private void depositItems(SquireEntity s, BaseContainerBlockEntity container) {
        SquireInventory inv = s.getSquireInventory();
        // Deposit non-equipment items (skip armor slots 0-3 and weapon slots 4-5)
        for (int i = 6; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            // Try to insert into container
            for (int j = 0; j < container.getContainerSize(); j++) {
                ItemStack containerStack = container.getItem(j);
                if (containerStack.isEmpty()) {
                    container.setItem(j, stack.copy());
                    inv.setItem(i, ItemStack.EMPTY);
                    break;
                } else if (ItemStack.isSameItemSameComponents(containerStack, stack)
                        && containerStack.getCount() < containerStack.getMaxStackSize()) {
                    int space = containerStack.getMaxStackSize() - containerStack.getCount();
                    int transfer = Math.min(space, stack.getCount());
                    containerStack.grow(transfer);
                    stack.shrink(transfer);
                    if (stack.isEmpty()) {
                        inv.setItem(i, ItemStack.EMPTY);
                        break;
                    }
                }
            }
        }
    }

    private void withdrawItems(SquireEntity s, BaseContainerBlockEntity container) {
        SquireInventory inv = s.getSquireInventory();
        for (int j = 0; j < container.getContainerSize(); j++) {
            ItemStack containerStack = container.getItem(j);
            if (containerStack.isEmpty()) continue;

            // Filter check
            if (fetchFilter != null) {
                String itemId = containerStack.getItem().toString().toLowerCase();
                if (!itemId.contains(fetchFilter.toLowerCase())) continue;
            }

            // Try to insert into squire inventory (backpack slots only, 6+)
            for (int i = 6; i < inv.getContainerSize(); i++) {
                ItemStack invStack = inv.getItem(i);
                if (invStack.isEmpty()) {
                    inv.setItem(i, containerStack.copy());
                    container.setItem(j, ItemStack.EMPTY);
                    break;
                } else if (ItemStack.isSameItemSameComponents(invStack, containerStack)
                        && invStack.getCount() < invStack.getMaxStackSize()) {
                    int space = invStack.getMaxStackSize() - invStack.getCount();
                    int transfer = Math.min(space, containerStack.getCount());
                    invStack.grow(transfer);
                    containerStack.shrink(transfer);
                    if (containerStack.isEmpty()) {
                        container.setItem(j, ItemStack.EMPTY);
                        break;
                    }
                }
            }
        }
    }

    @Nullable
    private BlockPos findNearestChest() {
        int range = SquireConfig.chestSearchRange.get().intValue();
        BlockPos center = squire.blockPosition();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-range, -range, -range),
                center.offset(range, range, range))) {
            if (squire.level().getBlockEntity(pos) instanceof BaseContainerBlockEntity) {
                double dist = pos.distSqr(center);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = pos.immutable();
                }
            }
        }
        return closest;
    }
}
