package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireInventory;
import com.sjviklabs.squire.util.SquireAbilities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Handles automatic torch placement when the squire is in a dark area.
 * Requires the AUTO_TORCH ability (level-gated) and torches in inventory.
 *
 * Called periodically from IDLE and FOLLOWING_OWNER states — not a full
 * state machine state, just a utility check that runs in one tick.
 */
public class TorchHandler {

    private final SquireEntity squire;
    private int cooldown;

    public TorchHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /**
     * Check if the squire should place a torch and do it.
     * Returns true if a torch was placed this tick.
     */
    public boolean tryPlaceTorch() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        // Ability gate
        if (!SquireAbilities.hasAutoTorch(squire)) return false;

        // Check light level at squire's feet
        BlockPos feetPos = squire.blockPosition();
        if (!(squire.level() instanceof ServerLevel serverLevel)) return false;

        int blockLight = serverLevel.getBrightness(LightLayer.BLOCK, feetPos);
        if (blockLight > SquireConfig.torchLightThreshold.get()) return false;

        // Find a torch in inventory
        int torchSlot = findTorchSlot();
        if (torchSlot < 0) return false;

        // Find a valid placement position (feet or adjacent)
        BlockPos placePos = findPlaceablePos(serverLevel, feetPos);
        if (placePos == null) return false;

        // Place the torch
        serverLevel.setBlock(placePos, Blocks.TORCH.defaultBlockState(), 3);
        squire.getSquireInventory().removeItem(torchSlot, 1);

        // Torch place sound
        serverLevel.playSound(null, placePos,
                SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

        cooldown = SquireConfig.torchCooldownTicks.get();

        var log = squire.getActivityLog();
        if (log != null) {
            log.log("TORCH", "Placed torch at " + placePos.toShortString()
                    + " (light was " + blockLight + ")");
        }

        return true;
    }

    /**
     * Find first torch stack in squire inventory.
     * Returns slot index or -1 if no torches found.
     */
    private int findTorchSlot() {
        SquireInventory inv = squire.getSquireInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TORCH) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find a valid position to place a torch near the squire's feet.
     * Checks feet position first, then adjacent blocks on the same Y level.
     */
    private BlockPos findPlaceablePos(ServerLevel level, BlockPos feetPos) {
        // Try feet position first
        if (canPlaceTorch(level, feetPos)) return feetPos;

        // Try adjacent positions (N, S, E, W)
        for (BlockPos offset : new BlockPos[]{
                feetPos.north(), feetPos.south(), feetPos.east(), feetPos.west()}) {
            if (canPlaceTorch(level, offset)) return offset;
        }

        return null;
    }

    /**
     * Check if a torch can be placed at this position:
     * - Block at pos must be air (replaceable)
     * - Block below must be solid (torch needs support)
     * - No existing torch already there
     */
    private boolean canPlaceTorch(ServerLevel level, BlockPos pos) {
        BlockState atPos = level.getBlockState(pos);
        if (!atPos.isAir()) return false;

        BlockState below = level.getBlockState(pos.below());
        if (!below.isSolid()) return false;

        // Don't place next to an existing torch (prevent clusters)
        for (BlockPos neighbor : new BlockPos[]{
                pos.north(), pos.south(), pos.east(), pos.west(),
                pos.above(), pos.below()}) {
            if (level.getBlockState(neighbor).getBlock() instanceof TorchBlock) {
                return false;
            }
        }

        return true;
    }
}
