package com.sjviklabs.squire.entity;

import com.sjviklabs.squire.ai.SquireEatGoal;
import com.sjviklabs.squire.ai.SquireFollowOwnerGoal;
import com.sjviklabs.squire.ai.SquireMeleeGoal;
import com.sjviklabs.squire.ai.SquirePickupGoal;
import com.sjviklabs.squire.ai.SquireSitGoal;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.init.ModItems;
import com.sjviklabs.squire.util.SquireEquipmentHelper;
import com.sjviklabs.squire.inventory.SquireEquipmentContainer;
import com.sjviklabs.squire.inventory.SquireInventory;
import com.sjviklabs.squire.inventory.SquireMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Core squire entity. Extends TamableAnimal for owner tracking, sitting, and
 * target-sharing out of the box. Delegates complex behaviour to external goal
 * classes and helpers -- this file stays lean.
 *
 * IMPORTANT: This entity NEVER teleports to its owner. We override
 * tryToTeleportToOwner() as a no-op so the vanilla teleport path never fires.
 */
public class SquireEntity extends TamableAnimal {

    // ---- Synched data keys ----
    // Mode byte: 0 = FOLLOW, 1 = STAY
    private static final EntityDataAccessor<Byte> SQUIRE_MODE =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IS_SPRINTING =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BOOLEAN);

    public static final byte MODE_FOLLOW = 0;
    public static final byte MODE_STAY = 1;

    // ---- Inventory ----
    private final SquireInventory inventory = new SquireInventory(this);

    // ---- Constructor ----
    public SquireEntity(EntityType<? extends SquireEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    // ================================================================
    // Attributes
    // ================================================================

    public static AttributeSupplier.Builder createAttributes() {
        // Use hardcoded defaults here — config isn't loaded during entity registration.
        // Config values are applied at runtime via attribute modifiers if needed.
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D);
    }

    // ================================================================
    // Synched entity data
    // ================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SQUIRE_MODE, MODE_FOLLOW);
        builder.define(IS_SPRINTING, false);
    }

    public byte getSquireMode() {
        return this.entityData.get(SQUIRE_MODE);
    }

    public void setSquireMode(byte mode) {
        this.entityData.set(SQUIRE_MODE, mode);
        this.setOrderedToSit(mode == MODE_STAY);
    }

    public boolean isSquireSprinting() {
        return this.entityData.get(IS_SPRINTING);
    }

    public void setSquireSprinting(boolean sprinting) {
        this.entityData.set(IS_SPRINTING, sprinting);
        this.setSprinting(sprinting);
    }

    // ================================================================
    // AI goals
    // ================================================================

    @Override
    protected void registerGoals() {
        // --- Behaviour goals (lower number = higher priority) ---
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SquireSitGoal(this));
        this.goalSelector.addGoal(2, new SquireMeleeGoal(this));
        this.goalSelector.addGoal(3, new SquireEatGoal(this));
        this.goalSelector.addGoal(4, new SquireFollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new SquirePickupGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // --- Target goals (no blanket monster aggro) ---
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    // ================================================================
    // Teleport prevention
    // ================================================================

    /**
     * Hard override: squires NEVER teleport to their owner. They walk, sprint,
     * or get left behind. This prevents the jarring vanilla pet-teleport and
     * keeps pathfinding deterministic for the player.
     */
    @Override
    public void tryToTeleportToOwner() {
        // Intentional no-op
    }

    // ================================================================
    // Breeding prevention
    // ================================================================

    @Override
    public boolean isFood(ItemStack stack) {
        return false; // Squires don't breed
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null; // Squires don't breed
    }

    // ================================================================
    // Interaction
    // ================================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.isTame() && this.isOwnedBy(player)) {
            if (player.isShiftKeyDown()) {
                // Shift+right-click: toggle FOLLOW / STAY
                byte current = getSquireMode();
                byte next = (current == MODE_FOLLOW) ? MODE_STAY : MODE_FOLLOW;
                setSquireMode(next);

                String modeKey = (next == MODE_STAY) ? "squire.mode.stay" : "squire.mode.follow";
                player.displayClientMessage(Component.translatable(modeKey), true);

                return InteractionResult.SUCCESS;
            } else {
                // Right-click: open inventory
                if (player instanceof ServerPlayer serverPlayer) {
                    SquireEquipmentContainer equipContainer = new SquireEquipmentContainer(this);
                    serverPlayer.openMenu(new SimpleMenuProvider(
                            (containerId, playerInventory, p) ->
                                    new SquireMenu(containerId, playerInventory, this.inventory,
                                            equipContainer, this, this.getId()),
                            Component.translatable("container.squire.inventory")
                    ), buf -> buf.writeVarInt(this.getId()));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    // ================================================================
    // Inventory
    // ================================================================

    public SquireInventory getSquireInventory() {
        return this.inventory;
    }

    // ================================================================
    // NBT persistence
    // ================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("SquireInventory", this.inventory.toTag(this.registryAccess()));
        tag.putByte("SquireMode", getSquireMode());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SquireInventory")) {
            this.inventory.fromTag(tag.getList("SquireInventory", 10), this.registryAccess());
        }
        if (tag.contains("SquireMode")) {
            setSquireMode(tag.getByte("SquireMode"));
        }
    }

    // ================================================================
    // Death
    // ================================================================

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            // Drop all inventory contents
            this.inventory.dropAll(this.level(), this.blockPosition());

            // Drop a squire badge so the player can resummon
            this.spawnAtLocation(new ItemStack(ModItems.SQUIRE_BADGE.get()));

            // Notify owner with death coordinates
            if (this.getOwner() instanceof ServerPlayer owner) {
                int x = this.blockPosition().getX();
                int y = this.blockPosition().getY();
                int z = this.blockPosition().getZ();
                owner.sendSystemMessage(Component.translatable("squire.death.message", x, y, z));
            }
        }
        super.die(source);
    }

    // ================================================================
    // Damage
    // ================================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Owner can never hurt their own squire (prevents accidental hits)
        if (source.getEntity() instanceof Player player && this.isOwnedBy(player)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    // ================================================================
    // Tick — periodic equipment check
    // ================================================================

    private int equipCheckTimer = 0;

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            if (++this.equipCheckTimer >= SquireConfig.equipCheckInterval.get()) {
                this.equipCheckTimer = 0;
                SquireEquipmentHelper.runFullEquipCheck(this);
            }
        }
    }

    // ================================================================
    // Despawn prevention
    // ================================================================

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ================================================================
    // Leash prevention
    // ================================================================

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ================================================================
    // Sounds (placeholders -- will be replaced with custom sounds)
    // ================================================================

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }
}
