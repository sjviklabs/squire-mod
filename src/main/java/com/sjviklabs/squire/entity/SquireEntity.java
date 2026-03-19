package com.sjviklabs.squire.entity;

import com.sjviklabs.squire.ai.SquireActivityLog;
import com.sjviklabs.squire.ai.handler.ProgressionHandler;
import com.sjviklabs.squire.ai.statemachine.SquireAI;
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
import net.minecraft.sounds.SoundEvents;
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
    private static final EntityDataAccessor<Integer> SQUIRE_LEVEL =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.INT);

    public static final byte MODE_FOLLOW = 0;
    public static final byte MODE_STAY = 1;

    // ---- Inventory ----
    private final SquireInventory inventory = new SquireInventory(this);

    // ---- Progression ----
    private final ProgressionHandler progression = new ProgressionHandler(this);

    // ---- AI ----
    private SquireAI squireAI;
    private SquireActivityLog activityLog;

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
        builder.define(SQUIRE_LEVEL, 0);
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

    public int getSquireLevel() {
        return this.entityData.get(SQUIRE_LEVEL);
    }

    public void setSquireLevel(int level) {
        this.entityData.set(SQUIRE_LEVEL, level);
    }

    // ================================================================
    // AI goals
    // ================================================================

    @Override
    protected void registerGoals() {
        // FloatGoal stays in vanilla system — handles swimming/not drowning
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Target selection stays in vanilla system — sets getTarget() for state machine
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));

        // All behavior goals (sit, combat, eat, follow, pickup, look) are now
        // handled by SquireAI's tick-rate state machine. See aiStep().
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

    public ProgressionHandler getProgression() {
        return this.progression;
    }

    // ================================================================
    // NBT persistence
    // ================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("SquireInventory", this.inventory.toTag(this.registryAccess()));
        tag.putByte("SquireMode", getSquireMode());
        this.progression.save(tag);
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
        this.progression.load(tag);
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
        // God mode: take damage but never die (clamp at 1 HP)
        if (SquireConfig.godMode.get()) {
            float newHealth = this.getHealth() - amount;
            if (newHealth < 1.0F) {
                this.setHealth(1.0F);
                return true; // still show hurt animation
            }
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
            // Lazy init — can't create in constructor because registerGoals()
            // runs during super() before our fields are initialized
            if (this.squireAI == null) {
                this.activityLog = new SquireActivityLog(this);
                this.squireAI = new SquireAI(this);
            }
            this.squireAI.tick();

            if (++this.equipCheckTimer >= SquireConfig.equipCheckInterval.get()) {
                this.equipCheckTimer = 0;
                SquireEquipmentHelper.runFullEquipCheck(this);
            }
        }
    }

    /** Accessor for debug/admin commands. Null before first server-side aiStep(). */
    @Nullable
    public SquireAI getSquireAI() {
        return this.squireAI;
    }

    /** Activity log for debugging. Null before first server-side aiStep(). */
    @Nullable
    public SquireActivityLog getActivityLog() {
        return this.activityLog;
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
    // Sounds — use player sounds so squire feels human
    // ================================================================

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null; // Intentionally silent
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}
