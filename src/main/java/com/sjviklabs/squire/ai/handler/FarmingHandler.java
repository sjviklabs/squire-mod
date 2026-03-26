package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Farming behavior: till dirt, plant seeds, wait for growth, harvest mature crops, replant.
 *
 * Command: /squire farm <pos1> <pos2> defines rectangular area.
 * Stop: /squire farm stop
 *
 * Workflow: scan area → approach → till/plant/harvest → move to next → repeat
 * Supports: wheat, potatoes, carrots, beetroot
 */
public class FarmingHandler {

    private final SquireEntity squire;

    @Nullable private BlockPos cornerA;
    @Nullable private BlockPos cornerB;
    @Nullable private BlockPos currentTarget;
    private FarmAction currentAction;
    private int actionTicksRemaining;
    private int scanCooldown;
    private boolean farming;

    private enum FarmAction { TILL, PLANT, HARVEST }

    public FarmingHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ---- Public API ----

    /** Set the farming area. Called by /squire farm command. */
    public void setArea(BlockPos pos1, BlockPos pos2) {
        this.cornerA = pos1;
        this.cornerB = pos2;
        this.farming = true;
        this.currentTarget = null;
        this.scanCooldown = 0;
    }

    /** Stop farming and clear area. */
    public void stop() {
        this.farming = false;
        this.cornerA = null;
        this.cornerB = null;
        this.currentTarget = null;
        squire.getNavigation().stop();
    }

    public boolean isFarming() { return farming; }
    public boolean hasTarget() { return currentTarget != null; }

    /** Get the farm area size in blocks, or 0 if no area set. */
    public int getAreaSize() {
        if (cornerA == null || cornerB == null) return 0;
        int dx = Math.abs(cornerA.getX() - cornerB.getX()) + 1;
        int dz = Math.abs(cornerA.getZ() - cornerB.getZ()) + 1;
        return dx * dz;
    }

    // ---- State machine ticks ----

    /** FARM_APPROACH: walk to current target block. */
    public SquireAIState tickApproach(SquireEntity s) {
        if (currentTarget == null) return SquireAIState.IDLE;

        double distSq = s.distanceToSqr(currentTarget.getX() + 0.5,
                currentTarget.getY(), currentTarget.getZ() + 0.5);
        double reach = SquireConfig.farmReach.get();

        if (distSq <= reach * reach) {
            actionTicksRemaining = SquireConfig.farmTicksPerBlock.get();
            return SquireAIState.FARM_WORK;
        }

        // Path to target
        s.getNavigation().moveTo(currentTarget.getX() + 0.5,
                currentTarget.getY(), currentTarget.getZ() + 0.5, 1.0);
        return SquireAIState.FARM_APPROACH;
    }

    /** FARM_WORK: perform the action (till, plant, or harvest). */
    public SquireAIState tickWork(SquireEntity s) {
        if (currentTarget == null) return SquireAIState.IDLE;
        if (s.level().isClientSide) return SquireAIState.FARM_WORK;

        // Face the target block
        s.getLookControl().setLookAt(currentTarget.getX() + 0.5,
                currentTarget.getY() + 0.5, currentTarget.getZ() + 0.5);

        actionTicksRemaining--;
        if (actionTicksRemaining > 0) {
            return SquireAIState.FARM_WORK;
        }

        // Perform action
        ServerLevel level = (ServerLevel) s.level();
        boolean success = switch (currentAction) {
            case TILL -> performTill(s, level, currentTarget);
            case PLANT -> performPlant(s, level, currentTarget);
            case HARVEST -> performHarvest(s, level, currentTarget);
        };

        if (success) {
            s.swing(InteractionHand.MAIN_HAND);
            var log = s.getActivityLog();
            if (log != null) {
                log.log("FARM", currentAction + " at " + currentTarget.toShortString());
            }
        }

        // Find next task
        currentTarget = null;
        return SquireAIState.FARM_SCAN;
    }

    /** FARM_SCAN: scan the area for the next block to work on. */
    public SquireAIState tickScan(SquireEntity s) {
        if (!farming || cornerA == null || cornerB == null) return SquireAIState.IDLE;
        if (s.level().isClientSide) return SquireAIState.FARM_SCAN;

        scanCooldown--;
        if (scanCooldown > 0) return SquireAIState.FARM_SCAN;
        scanCooldown = SquireConfig.farmScanInterval.get();

        // Scan area for work
        BlockPos found = findNextTask(s);
        if (found != null) {
            currentTarget = found;
            return SquireAIState.FARM_APPROACH;
        }

        // Nothing to do right now, stay in scan (crops may still be growing)
        return SquireAIState.FARM_SCAN;
    }

    // ---- Internal logic ----

    /** Scan the farm area for the next block needing work. Returns null if nothing to do. */
    @Nullable
    private BlockPos findNextTask(SquireEntity s) {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        int y = cornerA.getY();

        // Priority: harvest mature > till dirt > plant seeds
        List<BlockPos> tillable = new ArrayList<>();
        List<BlockPos> plantable = new ArrayList<>();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = s.level().getBlockState(pos);

                // Mature crop — harvest immediately (highest priority)
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    double dist = s.distanceToSqr(x + 0.5, y, z + 0.5);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = pos;
                        currentAction = FarmAction.HARVEST;
                    }
                    continue;
                }

                // Dirt/grass — needs tilling
                if (isTillable(state)) {
                    tillable.add(pos);
                    continue;
                }

                // Farmland with nothing planted — needs planting
                if (state.getBlock() instanceof FarmBlock) {
                    BlockState above = s.level().getBlockState(pos.above());
                    if (above.isAir() && hasSeedsInInventory(s)) {
                        plantable.add(pos);
                    }
                }
            }
        }

        // If a mature crop was found, use it (closest)
        if (closest != null) return closest;

        // Otherwise, till dirt first
        if (!tillable.isEmpty() && hasHoeInInventory(s)) {
            currentAction = FarmAction.TILL;
            return findClosest(s, tillable);
        }

        // Then plant seeds
        if (!plantable.isEmpty()) {
            currentAction = FarmAction.PLANT;
            return findClosest(s, plantable);
        }

        return null;
    }

    private boolean performTill(SquireEntity s, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isTillable(state)) return false;

        level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean performPlant(SquireEntity s, ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos);
        if (!(below.getBlock() instanceof FarmBlock)) return false;

        BlockPos plantPos = pos.above();
        if (!level.getBlockState(plantPos).isAir()) return false;

        // Find seeds in inventory and plant
        ItemStack seeds = findSeeds(s);
        if (seeds.isEmpty()) return false;

        Block cropBlock = getCropForSeed(seeds);
        if (cropBlock == null) return false;

        level.setBlock(plantPos, cropBlock.defaultBlockState(), Block.UPDATE_ALL);
        seeds.shrink(1);
        level.playSound(null, plantPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean performHarvest(SquireEntity s, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return false;

        // Break the crop — drops items naturally
        level.destroyBlock(pos, true, s);
        return true;
    }

    // ---- Helpers ----

    private static boolean isTillable(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.DIRT || block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT_PATH || block == Blocks.COARSE_DIRT;
    }

    private boolean hasHoeInInventory(SquireEntity s) {
        for (int i = 0; i < s.getSquireInventory().getContainerSize(); i++) {
            if (s.getSquireInventory().getItem(i).getItem() instanceof HoeItem) return true;
        }
        return s.getMainHandItem().getItem() instanceof HoeItem;
    }

    private boolean hasSeedsInInventory(SquireEntity s) {
        return !findSeeds(s).isEmpty();
    }

    private ItemStack findSeeds(SquireEntity s) {
        for (int i = 0; i < s.getSquireInventory().getContainerSize(); i++) {
            ItemStack stack = s.getSquireInventory().getItem(i);
            if (isSeedItem(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isSeedItem(ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS) || stack.is(Items.POTATO)
                || stack.is(Items.CARROT) || stack.is(Items.BEETROOT_SEEDS);
    }

    @Nullable
    private static Block getCropForSeed(ItemStack seeds) {
        if (seeds.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT;
        if (seeds.is(Items.POTATO)) return Blocks.POTATOES;
        if (seeds.is(Items.CARROT)) return Blocks.CARROTS;
        if (seeds.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS;
        return null;
    }

    @Nullable
    private BlockPos findClosest(SquireEntity s, List<BlockPos> positions) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            double dist = s.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }
}