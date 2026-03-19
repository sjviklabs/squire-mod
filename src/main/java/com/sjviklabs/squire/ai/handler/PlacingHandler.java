package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Handles block placement from inventory at a target position.
 *
 * Flow: setTarget(pos, blockItem) → PLACING_APPROACH → PLACING_BLOCK → IDLE
 *
 * Validates: target is air/replaceable, squire has the block item, within reach.
 */
public class PlacingHandler {

    private final SquireEntity squire;

    @Nullable private BlockPos targetPos;
    @Nullable private BlockItem targetBlockItem;
    private int inventorySlot = -1;

    public PlacingHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /**
     * Set the block to place and its target position.
     * Finds the item in inventory. Returns false if item not found.
     */
    public boolean setTarget(BlockPos pos, BlockItem blockItem) {
        int slot = findBlockInInventory(blockItem);
        if (slot < 0) return false;
        this.targetPos = pos;
        this.targetBlockItem = blockItem;
        this.inventorySlot = slot;
        return true;
    }

    public void clearTarget() {
        targetPos = null;
        targetBlockItem = null;
        inventorySlot = -1;
    }

    public boolean hasTarget() {
        return targetPos != null && targetBlockItem != null && inventorySlot >= 0;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    /** Whether the squire is close enough to place at the target. */
    public boolean isInRange() {
        if (targetPos == null) return false;
        double reach = SquireConfig.placeReach.get();
        return squire.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5) <= reach * reach;
    }

    /**
     * PLACING_APPROACH tick: path toward target position.
     */
    public SquireAIState tickApproach(SquireEntity s) {
        if (!hasTarget()) return SquireAIState.IDLE;

        BlockState state = s.level().getBlockState(targetPos);
        if (!state.isAir() && !state.canBeReplaced()) {
            clearTarget();
            return SquireAIState.IDLE;
        }

        s.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        s.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0D);

        if (isInRange()) {
            s.getNavigation().stop();
            return SquireAIState.PLACING_BLOCK;
        }

        return SquireAIState.PLACING_APPROACH;
    }

    /**
     * PLACING_BLOCK tick: place the block and consume the item.
     */
    public SquireAIState tickPlace(SquireEntity s) {
        if (!hasTarget()) return SquireAIState.IDLE;
        if (!(s.level() instanceof ServerLevel serverLevel)) return SquireAIState.IDLE;

        // Re-validate
        BlockState existing = serverLevel.getBlockState(targetPos);
        if (!existing.isAir() && !existing.canBeReplaced()) {
            clearTarget();
            return SquireAIState.IDLE;
        }

        // Verify item still in inventory
        SquireInventory inv = s.getSquireInventory();
        ItemStack stack = inv.getItem(inventorySlot);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi) || bi != targetBlockItem) {
            // Item moved or consumed — try to find it again
            int newSlot = findBlockInInventory(targetBlockItem);
            if (newSlot < 0) {
                clearTarget();
                return SquireAIState.IDLE;
            }
            inventorySlot = newSlot;
            stack = inv.getItem(inventorySlot);
        }

        // Place the block
        Block block = targetBlockItem.getBlock();
        BlockState placeState = block.defaultBlockState();
        serverLevel.setBlock(targetPos, placeState, Block.UPDATE_ALL);

        // Play place sound
        SoundType soundType = placeState.getSoundType();
        serverLevel.playSound(null, targetPos, soundType.getPlaceSound(),
                SoundSource.BLOCKS, 1.0F, soundType.getPitch());

        // Arm swing
        s.swing(InteractionHand.MAIN_HAND);

        // Consume one item
        inv.removeItem(inventorySlot, 1);

        clearTarget();
        return SquireAIState.IDLE;
    }

    private int findBlockInInventory(BlockItem blockItem) {
        if (blockItem == null) return -1;
        SquireInventory inv = squire.getSquireInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == blockItem) {
                return i;
            }
        }
        return -1;
    }
}
