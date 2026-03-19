package com.sjviklabs.squire.inventory;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 27-slot inventory for a SquireEntity. Extends SimpleContainer to inherit
 * standard slot management, listener support, and stacked-contents tracking.
 * <p>
 * Custom methods handle item merging, bulk drop on death, and slot-indexed
 * NBT serialization that preserves empty-slot gaps (vanilla SimpleContainer's
 * {@code createTag/fromTag} does not preserve slot indices).
 */
public class SquireInventory extends SimpleContainer {

    private static final int SIZE = 27;

    @Nullable
    private final SquireEntity owner;

    /**
     * Primary constructor for use by SquireEntity.
     *
     * @param owner the entity that owns this inventory
     */
    public SquireInventory(SquireEntity owner) {
        super(SIZE);
        this.owner = owner;
    }

    /**
     * Fallback constructor for client-side or test contexts where no owner exists.
     * {@link #stillValid(Player)} will always return false when created this way.
     */
    public SquireInventory(int size) {
        super(size);
        this.owner = null;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        if (this.owner == null) {
            return false;
        }
        return this.owner.isAlive() && this.owner.distanceTo(player) < 8.0;
    }

    // ------------------------------------------------------------------
    // Item insertion (merge-first, then empty slots)
    // ------------------------------------------------------------------

    /**
     * Check whether the inventory has room for at least one unit of {@code stack}.
     * Delegates to the parent implementation which already checks same-type merges
     * and empty slots.
     */
    @Override
    public boolean canAddItem(ItemStack stack) {
        return super.canAddItem(stack);
    }

    /**
     * Try to add {@code stack} to the inventory, merging with existing stacks first,
     * then filling empty slots. Returns the remainder that could not be inserted
     * (or {@link ItemStack#EMPTY} if everything fit).
     * <p>
     * Delegates to the parent {@link SimpleContainer#addItem} which already
     * implements merge-first + empty-slot logic.
     */
    @Override
    public ItemStack addItem(ItemStack stack) {
        return super.addItem(stack);
    }

    // ------------------------------------------------------------------
    // Bulk drop (death / despawn)
    // ------------------------------------------------------------------

    /**
     * Spawns {@link ItemEntity} instances for every non-empty slot, then clears
     * the inventory. Intended to be called server-side on entity death.
     */
    public void dropAll(Level level, BlockPos pos) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack stack = this.getItem(i);
            if (!stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(
                        level,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        stack.copy()
                ));
            }
        }
        this.clearContent();
    }

    // ------------------------------------------------------------------
    // NBT — slot-indexed format (preserves empty gaps, unlike vanilla)
    // ------------------------------------------------------------------

    /**
     * Serialize to a {@link ListTag} where each compound carries a {@code Slot}
     * byte so empty slots in the middle are preserved on reload.
     */
    public ListTag toTag(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack stack = this.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                list.add(stack.save(registries, itemTag));
            }
        }
        return list;
    }

    /**
     * Deserialize from the slot-indexed {@link ListTag} produced by {@link #toTag}.
     */
    public void fromTag(ListTag list, HolderLookup.Provider registries) {
        this.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < this.getContainerSize()) {
                ItemStack.parse(registries, itemTag).ifPresent(stack -> this.setItem(slot, stack));
            }
        }
    }
}
