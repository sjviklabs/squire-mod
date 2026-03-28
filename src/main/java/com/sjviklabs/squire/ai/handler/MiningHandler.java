package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.util.SquireEquipmentHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;
import java.util.LinkedList;

/**
 * Handles block breaking with proper break speed calculation, crack animation,
 * tool selection, arm swing, and sound effects.
 *
 * Single block: setTarget() → MINING_APPROACH → MINING_BREAK → drops → IDLE
 * Area clear:   setAreaTarget() → queues blocks top-down → chains through queue → IDLE when empty
 *
 * Break speed formula matches vanilla player mining:
 *   progress_per_tick = tool.getDestroySpeed(state) / (block.getDestroySpeed() * 30)
 *   (multiplied by config breakSpeedMultiplier)
 */
public class MiningHandler {

    private final SquireEntity squire;

    @Nullable private BlockPos targetPos;
    private float breakProgress;    // 0.0 to 1.0
    private int lastCrackStage;     // 0-9 for destroy progress overlay
    private final LinkedList<BlockPos> blockQueue = new LinkedList<>();
    private boolean areaClearing = false;
    private int approachTicks;          // ticks spent trying to reach current target
    private double lastApproachDistSq;  // distance last time we checked for progress
    private int stuckTicks;             // ticks with no progress toward target
    private static final int STUCK_TIMEOUT = 100;  // 5 seconds with no progress = stuck

    public MiningHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /** Set the block to mine. Call before entering MINING_APPROACH. */
    public void setTarget(BlockPos pos) {
        this.targetPos = pos;
        this.breakProgress = 0.0F;
        this.lastCrackStage = -1;
        this.approachTicks = 0;
        this.stuckTicks = 0;
        this.lastApproachDistSq = Double.MAX_VALUE;
    }

    /**
     * Set an area to clear. Populates the queue top-down (Y descending) so the
     * squire doesn't undercut itself. Skips air, unbreakable, and fluid blocks.
     *
     * @return number of blocks queued
     */
    public int setAreaTarget(BlockPos from, BlockPos to) {
        blockQueue.clear();
        areaClearing = true;

        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = squire.level().getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(squire.level(), pos) < 0) continue;
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty() && state.getBlock().defaultBlockState().isAir()) continue;
                    blockQueue.add(pos);
                }
            }
        }

        // Pop the first block as the immediate target
        if (!blockQueue.isEmpty()) {
            setTarget(blockQueue.poll());
        } else {
            areaClearing = false;
        }

        return blockQueue.size() + (targetPos != null ? 1 : 0);
    }

    /** Clear target, reset progress, and wipe the area queue. */
    public void clearTarget() {
        if (targetPos != null && squire.level() instanceof ServerLevel serverLevel) {
            // Remove crack overlay
            serverLevel.destroyBlockProgress(squire.getId(), targetPos, -1);
        }
        targetPos = null;
        breakProgress = 0.0F;
        lastCrackStage = -1;
        blockQueue.clear();
        areaClearing = false;
    }

    public boolean hasTarget() {
        return targetPos != null;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    /** Whether the squire is currently processing an area clear. */
    public boolean isAreaClearing() {
        return areaClearing;
    }

    /** Number of blocks remaining in the queue (excluding current target). */
    public int getQueueSize() {
        return blockQueue.size();
    }

    /** Whether the squire is close enough to mine the target. */
    public boolean isInRange() {
        if (targetPos == null) return false;
        double reach = SquireConfig.mineReach.get();
        return squire.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5) <= reach * reach;
    }

    /**
     * MINING_APPROACH tick: path toward target block.
     * Returns MINING_BREAK when in range, IDLE if target invalid.
     */
    public SquireAIState tickApproach(SquireEntity s) {
        if (targetPos == null) return SquireAIState.IDLE;

        BlockState state = s.level().getBlockState(targetPos);
        if (state.isAir() || state.getDestroySpeed(s.level(), targetPos) < 0) {
            // Block invalid — if area clearing, skip to next valid block
            if (areaClearing) {
                BlockPos next = popNextValid(s);
                if (next != null) {
                    setTarget(next);
                    return SquireAIState.MINING_APPROACH;
                }
                finalizeAreaClear(s);
            }
            clearTarget();
            return SquireAIState.IDLE;
        }

        // Look at and path toward target
        s.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        s.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0D);

        if (isInRange()) {
            s.getNavigation().stop();
            // Select best tool before starting to break
            SquireEquipmentHelper.selectBestTool(s, state);
            return SquireAIState.MINING_BREAK;
        }

        // Stuck detection: if not making progress toward target, count up
        approachTicks++;
        double distSq = s.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq < lastApproachDistSq - 0.1) {
            // Making progress — reset stuck counter
            stuckTicks = 0;
            lastApproachDistSq = distSq;
        } else {
            stuckTicks++;
        }

        if (stuckTicks >= STUCK_TIMEOUT) {
            var log = s.getActivityLog();
            if (log != null) {
                log.log("MINE", "Can't reach block at " + targetPos.toShortString() + ", skipping");
            }
            // Area clearing: skip to next reachable block
            if (areaClearing) {
                BlockPos next = popNextValid(s);
                if (next != null) {
                    setTarget(next);
                    return SquireAIState.MINING_APPROACH;
                }
                finalizeAreaClear(s);
            }
            clearTarget();
            return SquireAIState.IDLE;
        }

        return SquireAIState.MINING_APPROACH;
    }

    /**
     * MINING_BREAK tick: accumulate break progress, show crack animation, break when done.
     * Returns IDLE when block is broken or target becomes invalid.
     */
    public SquireAIState tickBreak(SquireEntity s) {
        if (targetPos == null) return SquireAIState.IDLE;

        if (!(s.level() instanceof ServerLevel serverLevel)) return SquireAIState.IDLE;

        BlockState state = serverLevel.getBlockState(targetPos);
        if (state.isAir()) {
            clearTarget();
            return SquireAIState.IDLE;
        }

        // Unbreakable blocks (bedrock, etc.)
        float destroySpeed = state.getDestroySpeed(serverLevel, targetPos);
        if (destroySpeed < 0) {
            clearTarget();
            return SquireAIState.IDLE;
        }

        // Check still in range
        if (!isInRange()) {
            return SquireAIState.MINING_APPROACH;
        }

        // Look at target
        s.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        // Arm swing animation
        s.swing(InteractionHand.MAIN_HAND);

        // Calculate break speed — matches vanilla player mining formula:
        //   speed = tool.getDestroySpeed(state)  (1.0 for bare hand / wrong tool)
        //   speed += efficiency bonus             (level^2 + 1 if efficiency present)
        //   progress = speed / (hardness * divisor) where divisor = 30 (correct tool) or 100 (wrong tool)
        ItemStack tool = s.getMainHandItem();
        float toolSpeed = tool.getDestroySpeed(state);

        // Add efficiency enchantment bonus: level^2 + 1 (matches vanilla)
        if (toolSpeed > 1.0F) {
            int efficiencyLevel = getEfficiencyLevel(tool);
            if (efficiencyLevel > 0) {
                toolSpeed += (float) (efficiencyLevel * efficiencyLevel + 1);
            }
        }

        // destroySpeed of 0 means instant break (like tall grass)
        float progressPerTick;
        if (destroySpeed == 0) {
            progressPerTick = 1.0F; // instant
        } else {
            // Vanilla uses /30 when the tool can harvest the block, /100 otherwise
            boolean canHarvest = tool.isCorrectToolForDrops(state) || state.requiresCorrectToolForDrops() == false;
            float divisor = canHarvest ? 30.0F : 100.0F;
            // Level bonus: 1.0 + (level * miningSpeedPerLevel). At default 0.0167, Lv30 = +50%.
            float levelBonus = 1.0F + (s.getSquireLevel() * SquireConfig.miningSpeedPerLevel.get().floatValue());
            progressPerTick = (toolSpeed / (destroySpeed * divisor))
                    * SquireConfig.breakSpeedMultiplier.get().floatValue()
                    * levelBonus;
        }

        breakProgress += progressPerTick;

        // Update crack overlay (0-9 stages)
        int crackStage = (int) (breakProgress * 10.0F);
        crackStage = Math.min(crackStage, 9);
        if (crackStage != lastCrackStage) {
            serverLevel.destroyBlockProgress(s.getId(), targetPos, crackStage);
            lastCrackStage = crackStage;
        }

        // Block broken
        if (breakProgress >= 1.0F) {
            // Play break sound
            SoundType soundType = state.getSoundType();
            serverLevel.playSound(null, targetPos, soundType.getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, soundType.getPitch());

            // Destroy block with drops
            String blockName = state.getBlock().getName().getString();
            String posStr = targetPos.toShortString();
            serverLevel.destroyBlock(targetPos, true, s);

            // Award mining XP
            s.getProgression().addMineXP();

            // Bonus: wood chopping gets chop XP in addition to mine XP
            if (state.is(net.minecraft.tags.BlockTags.LOGS)) {
                s.getProgression().addChopXP();
            }

            var log = s.getActivityLog();
            if (log != null) {
                log.log("MINE", "Broke " + blockName + " at " + posStr);
            }

            // Clear crack overlay
            serverLevel.destroyBlockProgress(s.getId(), targetPos, -1);

            // Re-equip best weapon after mining
            SquireEquipmentHelper.runFullEquipCheck(s);

            targetPos = null;
            breakProgress = 0.0F;
            lastCrackStage = -1;

            // Area clearing: chain to next block in queue
            if (areaClearing) {
                BlockPos next = popNextValid(s);
                if (next != null) {
                    setTarget(next);
                    return SquireAIState.MINING_APPROACH;
                }
                finalizeAreaClear(s);
            }

            return SquireAIState.IDLE;
        }

        return SquireAIState.MINING_BREAK;
    }

    /** Mark area clear as finished and log completion. */
    private void finalizeAreaClear(SquireEntity s) {
        areaClearing = false;
        var log = s.getActivityLog();
        if (log != null) {
            log.log("CLEAR", "Area clear complete");
        }
    }

    /**
     * Extract the efficiency enchantment level from an item stack.
     * Returns 0 if not present.
     */
    private int getEfficiencyLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            // Check by key — Enchantments.EFFICIENCY is a ResourceKey<Enchantment>
            if (holder.is(Enchantments.EFFICIENCY)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    /**
     * Pop blocks from the queue until we find one that is still valid
     * (non-air, breakable, not pure fluid). Returns null if queue is exhausted.
     */
    @Nullable
    private BlockPos popNextValid(SquireEntity s) {
        while (!blockQueue.isEmpty()) {
            BlockPos candidate = blockQueue.poll();
            BlockState state = s.level().getBlockState(candidate);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(s.level(), candidate) < 0) continue;
            return candidate;
        }
        return null;
    }
}
