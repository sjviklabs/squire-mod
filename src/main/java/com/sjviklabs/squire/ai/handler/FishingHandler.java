package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * Simplified fishing behavior (v1): walk to water edge, idle-fish with simulated catches.
 *
 * Command: /squire fish — find nearest water, walk to edge
 * Stop: /squire fish stop
 *
 * No actual bobber casting. Every N ticks: roll loot, add to inventory.
 * Requires fishing rod in inventory (loses durability per catch).
 * Loot: cod (60%), salmon (25%), tropical fish (10%), pufferfish (5%)
 */
public class FishingHandler {

    private final SquireEntity squire;

    @Nullable private BlockPos waterEdgePos;  // block the squire stands on
    @Nullable private BlockPos waterPos;      // water block squire faces
    private boolean fishing;
    private int catchCooldown;

    public FishingHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ---- Public API ----

    /** Start fishing. Searches for nearby water automatically. */
    public boolean startFishing() {
        BlockPos water = findNearbyWater();
        if (water == null) return false;

        BlockPos edge = findWaterEdge(water);
        if (edge == null) return false;

        this.waterPos = water;
        this.waterEdgePos = edge;
        this.fishing = true;
        this.catchCooldown = SquireConfig.fishingCatchInterval.get();
        return true;
    }

    /** Stop fishing. */
    public void stop() {
        this.fishing = false;
        this.waterEdgePos = null;
        this.waterPos = null;
        squire.getNavigation().stop();
    }

    public boolean isFishing() { return fishing; }
    public boolean hasTarget() { return waterEdgePos != null; }

    // ---- State machine ticks ----

    /** FISHING_APPROACH: walk to the water's edge. */
    public SquireAIState tickApproach(SquireEntity s) {
        if (waterEdgePos == null) return SquireAIState.IDLE;

        double distSq = s.distanceToSqr(waterEdgePos.getX() + 0.5,
                waterEdgePos.getY(), waterEdgePos.getZ() + 0.5);

        if (distSq <= 4.0) { // within 2 blocks
            s.getNavigation().stop();
            return SquireAIState.FISHING_IDLE;
        }

        s.getNavigation().moveTo(waterEdgePos.getX() + 0.5,
                waterEdgePos.getY(), waterEdgePos.getZ() + 0.5, 1.0);
        return SquireAIState.FISHING_APPROACH;
    }

    /** FISHING_IDLE: face water and periodically catch fish. */
    public SquireAIState tickIdle(SquireEntity s) {
        if (!fishing || waterPos == null) return SquireAIState.IDLE;
        if (s.level().isClientSide) return SquireAIState.FISHING_IDLE;

        // Face the water
        s.getLookControl().setLookAt(waterPos.getX() + 0.5,
                waterPos.getY() + 0.5, waterPos.getZ() + 0.5);

        // Check fishing rod still available
        if (!hasFishingRod(s)) {
            stop();
            return SquireAIState.IDLE;
        }

        catchCooldown--;
        if (catchCooldown <= 0) {
            catchCooldown = SquireConfig.fishingCatchInterval.get();
            performCatch(s);
        }

        return SquireAIState.FISHING_IDLE;
    }

    // ---- Internal logic ----

    private void performCatch(SquireEntity s) {
        ServerLevel level = (ServerLevel) s.level();

        // Roll loot
        ItemStack loot = rollLoot(s);

        // Add to inventory
        boolean added = false;
        for (int i = 0; i < s.getSquireInventory().getContainerSize(); i++) {
            ItemStack slot = s.getSquireInventory().getItem(i);
            if (slot.isEmpty()) {
                s.getSquireInventory().setItem(i, loot);
                added = true;
                break;
            }
            if (ItemStack.isSameItemSameComponents(slot, loot)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(loot.getCount());
                added = true;
                break;
            }
        }

        if (!added) {
            // Inventory full — drop at feet
            s.spawnAtLocation(loot);
        }

        // Consume rod durability
        damageFishingRod(s);

        // Award XP for the catch
        s.getProgression().addFishXP();

        // Effects
        level.playSound(null, s.blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.NEUTRAL, 0.5F, 1.0F);

        var log = s.getActivityLog();
        if (log != null) {
            log.log("FISH", "Caught " + loot.getHoverName().getString());
        }
    }

    private ItemStack rollLoot(SquireEntity s) {
        float roll = s.getRandom().nextFloat();
        if (roll < 0.60F) return new ItemStack(Items.COD);
        if (roll < 0.85F) return new ItemStack(Items.SALMON);
        if (roll < 0.95F) return new ItemStack(Items.TROPICAL_FISH);
        return new ItemStack(Items.PUFFERFISH);
    }

    private boolean hasFishingRod(SquireEntity s) {
        if (s.getMainHandItem().getItem() instanceof FishingRodItem) return true;
        for (int i = 0; i < s.getSquireInventory().getContainerSize(); i++) {
            if (s.getSquireInventory().getItem(i).getItem() instanceof FishingRodItem) return true;
        }
        return false;
    }

    private void damageFishingRod(SquireEntity s) {
        int dmg = SquireConfig.fishingRodDurabilityPerCatch.get();
        if (dmg <= 0) return;

        // Try mainhand first
        ItemStack mainHand = s.getMainHandItem();
        if (mainHand.getItem() instanceof FishingRodItem) {
            mainHand.hurtAndBreak(dmg, s, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            return;
        }

        // Find rod in inventory
        for (int i = 0; i < s.getSquireInventory().getContainerSize(); i++) {
            ItemStack stack = s.getSquireInventory().getItem(i);
            if (stack.getItem() instanceof FishingRodItem) {
                // Damage by shrinking durability manually since it's not equipped
                if (stack.isDamageableItem()) {
                    stack.setDamageValue(stack.getDamageValue() + dmg);
                    if (stack.getDamageValue() >= stack.getMaxDamage()) {
                        s.getSquireInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                return;
            }
        }
    }

    // ---- Water finding ----

    /** Search for a water block within config range. */
    @Nullable
    private BlockPos findNearbyWater() {
        int range = SquireConfig.waterSearchRange.get().intValue();
        BlockPos center = squire.blockPosition();

        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int y = -4; y <= 4; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (squire.level().getFluidState(pos).is(Fluids.WATER)) {
                        double dist = center.distSqr(pos);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    /** Find a solid block adjacent to water that the squire can stand on. */
    @Nullable
    private BlockPos findWaterEdge(BlockPos water) {
        BlockPos[] adjacents = {
                water.north(), water.south(), water.east(), water.west()
        };

        for (BlockPos adj : adjacents) {
            BlockState state = squire.level().getBlockState(adj);
            BlockState above = squire.level().getBlockState(adj.above());
            // Solid ground with air above = standable
            if (state.isSolid() && !above.isSolid()) {
                return adj;
            }
        }

        // Check one block up (water at ground level, edge one higher)
        for (BlockPos adj : adjacents) {
            BlockPos up = adj.above();
            BlockState state = squire.level().getBlockState(up);
            BlockState stateAbove = squire.level().getBlockState(up.above());
            if (state.isSolid() && !stateAbove.isSolid()) {
                return up;
            }
        }

        return null;
    }
}