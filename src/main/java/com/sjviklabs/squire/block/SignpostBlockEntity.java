package com.sjviklabs.squire.block;

import com.sjviklabs.squire.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Stores patrol signpost configuration: mode, linked signpost, wait time, owner.
 */
public class SignpostBlockEntity extends BlockEntity {

    public enum PatrolMode { WAYPOINT, GUARD_POST, PERIMETER }

    private PatrolMode mode = PatrolMode.WAYPOINT;
    private UUID assignedOwner;
    private BlockPos linkedSignpost;
    private int waitTicks = 100; // 5 seconds default

    public SignpostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGNPOST.get(), pos, state);
    }

    // ---- Getters/Setters ----

    public PatrolMode getMode() { return mode; }
    public void setMode(PatrolMode mode) { this.mode = mode; }

    public UUID getAssignedOwner() { return assignedOwner; }
    public void setAssignedOwner(UUID owner) { this.assignedOwner = owner; }

    @Nullable
    public BlockPos getLinkedSignpost() { return linkedSignpost; }
    public void setLinkedSignpost(@Nullable BlockPos pos) { this.linkedSignpost = pos; }

    public int getWaitTicks() { return waitTicks; }
    public void setWaitTicks(int ticks) { this.waitTicks = ticks; }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Mode", mode.name());
        tag.putInt("WaitTicks", waitTicks);
        if (assignedOwner != null) {
            tag.putUUID("AssignedOwner", assignedOwner);
        }
        if (linkedSignpost != null) {
            tag.put("LinkedSignpost", NbtUtils.writeBlockPos(linkedSignpost));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Mode")) {
            try {
                mode = PatrolMode.valueOf(tag.getString("Mode"));
            } catch (IllegalArgumentException e) {
                mode = PatrolMode.WAYPOINT;
            }
        }
        if (tag.contains("WaitTicks")) {
            waitTicks = tag.getInt("WaitTicks");
        }
        if (tag.hasUUID("AssignedOwner")) {
            assignedOwner = tag.getUUID("AssignedOwner");
        }
        if (tag.contains("LinkedSignpost")) {
            NbtUtils.readBlockPos(tag, "LinkedSignpost").ifPresent(pos -> linkedSignpost = pos);
        }
    }

    // ---- Client sync ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
