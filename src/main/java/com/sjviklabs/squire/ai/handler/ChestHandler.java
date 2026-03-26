package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.compat.MineColoniesCompat;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import com.sjviklabs.squire.util.SquireAbilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

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
        if (blockEntity == null) {
            clearTarget();
            return SquireAIState.IDLE;
        }

        // Try standard container first, then fall back to IItemHandler capability.
        // This lets us interact with MineColonies warehouses and modded storage blocks.
        if (blockEntity instanceof BaseContainerBlockEntity container) {
            if (action == ChestAction.STORE) {
                depositItems(s, container);
            } else {
                withdrawItems(s, container);
            }
        } else {
            IItemHandler handler = s.level().getCapability(
                    Capabilities.ItemHandler.BLOCK, targetChest, null);
            if (handler != null) {
                if (action == ChestAction.STORE) {
                    depositItemsHandler(s, handler);
                } else {
                    withdrawItemsHandler(s, handler);
                }
            } else {
                clearTarget();
                return SquireAIState.IDLE;
            }
        }

        // Chest close sound
        s.playSound(SoundEvents.CHEST_CLOSE, 0.5F, 1.0F);

        var log = s.getActivityLog();
        if (log != null) {
            String blockName = MineColoniesCompat.isWarehouse(s.level(), targetChest)
                    ? "warehouse" : "chest";
            log.log("CHEST", (action == ChestAction.STORE ? "Deposited" : "Fetched")
                    + " items at " + blockName + " " + targetChest.toShortString());
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

    // ------------------------------------------------------------------
    // IItemHandler-based transfer (for modded storage: warehouses, etc.)
    // ------------------------------------------------------------------

    private void depositItemsHandler(SquireEntity s, IItemHandler handler) {
        SquireInventory inv = s.getSquireInventory();
        for (int i = 6; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            ItemStack toInsert = stack.copy();
            for (int j = 0; j < handler.getSlots(); j++) {
                toInsert = handler.insertItem(j, toInsert, false);
                if (toInsert.isEmpty()) break;
            }
            inv.setItem(i, toInsert);
        }
    }

    private void withdrawItemsHandler(SquireEntity s, IItemHandler handler) {
        SquireInventory inv = s.getSquireInventory();
        for (int j = 0; j < handler.getSlots(); j++) {
            ItemStack containerStack = handler.getStackInSlot(j);
            if (containerStack.isEmpty()) continue;

            if (fetchFilter != null) {
                String itemId = containerStack.getItem().toString().toLowerCase();
                if (!itemId.contains(fetchFilter.toLowerCase())) continue;
            }

            ItemStack extracted = handler.extractItem(j, containerStack.getCount(), false);
            if (extracted.isEmpty()) continue;

            ItemStack remainder = inv.addItem(extracted);
            if (!remainder.isEmpty()) {
                // Put back what didn't fit
                handler.insertItem(j, remainder, false);
                break; // Inventory full
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
            BlockEntity be = squire.level().getBlockEntity(pos);
            if (be == null) continue;

            // Standard containers (chests, barrels, etc.)
            boolean isContainer = be instanceof BaseContainerBlockEntity;
            // MineColonies warehouses and other modded storage via IItemHandler
            boolean isModdedStorage = !isContainer
                    && squire.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;

            if (isContainer || isModdedStorage) {
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
