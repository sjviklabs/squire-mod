package com.sjviklabs.squire.entity;

import com.sjviklabs.squire.ai.SquireActivityLog;
import com.sjviklabs.squire.ai.handler.ProgressionHandler;
import com.sjviklabs.squire.ai.statemachine.SquireAI;
import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.init.ModItems;
import com.sjviklabs.squire.util.SquireAbilities;
import com.sjviklabs.squire.util.SquireChunkLoader;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
public class SquireEntity extends TamableAnimal implements RangedAttackMob {

    // ---- Synched data keys ----
    // Mode byte: 0 = FOLLOW, 1 = STAY
    private static final EntityDataAccessor<Byte> SQUIRE_MODE =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IS_SPRINTING =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SQUIRE_LEVEL =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.INT);
    // Appearance: false = wide arms (Steve/male), true = slim arms (Alex/female)
    private static final EntityDataAccessor<Boolean> SLIM_MODEL =
            SynchedEntityData.defineId(SquireEntity.class, EntityDataSerializers.BOOLEAN);

    public static final byte MODE_FOLLOW = 0;
    public static final byte MODE_STAY = 1;
    public static final byte MODE_GUARD = 2;

    // ---- Inventory ----
    private final SquireInventory inventory = new SquireInventory(this);

    // ---- Progression ----
    private final ProgressionHandler progression = new ProgressionHandler(this);

    // ---- AI ----
    private SquireAI squireAI;
    private SquireActivityLog activityLog;

    // ---- Undying ability cooldown (ticks until revive is available again) ----
    private int undyingCooldown = 0;

    // ---- Horse UUID (loaded from NBT, applied to MountHandler when AI initializes) ----
    private java.util.UUID pendingHorseUUID;

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
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
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
        builder.define(SLIM_MODEL, false);
    }

    public byte getSquireMode() {
        return this.entityData.get(SQUIRE_MODE);
    }

    public void setSquireMode(byte mode) {
        this.entityData.set(SQUIRE_MODE, mode);
        // STAY = sit down, no combat. GUARD = stand, fight, but don't follow.
        this.setOrderedToSit(mode == MODE_STAY);
    }

    /** Whether squire should follow owner (false for STAY and GUARD). */
    public boolean shouldFollowOwner() {
        byte mode = getSquireMode();
        return mode == MODE_FOLLOW;
    }

    /** Human-readable mode name. */
    public static String modeName(byte mode) {
        return switch (mode) {
            case MODE_FOLLOW -> "Follow";
            case MODE_STAY -> "Stay";
            case MODE_GUARD -> "Guard";
            default -> "Unknown";
        };
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

    /** Whether this squire uses the slim (Alex/female) arm model. */
    public boolean isSlimModel() {
        return this.entityData.get(SLIM_MODEL);
    }

    public void setSlimModel(boolean slim) {
        this.entityData.set(SLIM_MODEL, slim);
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
        // Proactive aggro — engage hostile mobs within range without waiting to be hit
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, true));

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
                // Shift+right-click: open inventory
                if (player instanceof ServerPlayer serverPlayer) {
                    SquireEquipmentContainer equipContainer = new SquireEquipmentContainer(this);
                    serverPlayer.openMenu(new SimpleMenuProvider(
                            (containerId, playerInventory, p) ->
                                    new SquireMenu(containerId, playerInventory, this.inventory,
                                            equipContainer, this, this.getId()),
                            Component.translatable("container.squire.inventory")
                    ), buf -> {
                        buf.writeVarInt(this.getId());
                        buf.writeVarInt(this.getSquireLevel());
                        buf.writeVarInt(this.getProgression().getTotalXP());
                        buf.writeFloat(this.getHealth());
                        buf.writeFloat(this.getMaxHealth());
                        buf.writeByte(this.getSquireMode());
                    });
                }
                return InteractionResult.SUCCESS;
            } else {
                // Right-click (empty hand): cycle modes FOLLOW → GUARD → STAY → FOLLOW
                byte current = getSquireMode();
                byte next = (byte) ((current + 1) % 3);
                setSquireMode(next);

                player.displayClientMessage(Component.literal("Squire: " + modeName(next)), true);

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
        tag.putBoolean("SlimModel", isSlimModel());
        tag.putInt("UndyingCooldown", this.undyingCooldown);
        if (this.pendingHorseUUID != null) {
            tag.putUUID("HorseUUID", this.pendingHorseUUID);
        } else if (this.squireAI != null && this.squireAI.getMount().getHorseUUID() != null) {
            tag.putUUID("HorseUUID", this.squireAI.getMount().getHorseUUID());
        }
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
        if (tag.contains("SlimModel")) {
            setSlimModel(tag.getBoolean("SlimModel"));
        }
        if (tag.contains("UndyingCooldown")) {
            this.undyingCooldown = tag.getInt("UndyingCooldown");
        }
        if (tag.hasUUID("HorseUUID")) {
            this.pendingHorseUUID = tag.getUUID("HorseUUID");
        }
        this.progression.load(tag);
    }

    // ================================================================
    // Death
    // ================================================================

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            // Undying: revive once at 50% HP, then 5-min cooldown (level-gated)
            if (SquireAbilities.hasUndying(this) && this.undyingCooldown <= 0) {
                this.setHealth(this.getMaxHealth() * 0.5F);
                this.undyingCooldown = 6000; // 5 minutes (300s * 20 ticks)
                this.removeAllEffects();
                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true));
                this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1, false, true));
                var log = this.getActivityLog();
                if (log != null) {
                    log.log("UNDYING", "Revived at 50% HP! Cooldown: 5 minutes");
                }
                if (this.getOwner() instanceof ServerPlayer owner) {
                    owner.sendSystemMessage(Component.literal("Your squire cheated death! (5-min cooldown)"));
                }
                return; // Cancel death
            }

            // Drop all inventory contents
            this.inventory.dropAll(this.level(), this.blockPosition());

            // Drop a squire crest so the player can resummon
            this.spawnAtLocation(new ItemStack(ModItems.SQUIRE_CREST.get()));

            // Release any force-loaded chunks
            SquireChunkLoader.release(this);

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
        // God mode: cap incoming damage so health never drops below 1 HP
        if (SquireConfig.godMode.get()) {
            float maxDamage = this.getHealth() - 1.0F;
            if (maxDamage <= 0.0F) {
                return super.hurt(source, 0.0F);
            }
            amount = Math.min(amount, maxDamage);
        }

        boolean wasHurt = super.hurt(source, amount);

        // Thorns: reflect 20% melee damage back to attacker (level-gated)
        if (wasHurt && SquireAbilities.hasThorns(this)
                && source.getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof Player p && this.isOwnedBy(p))) {
            float reflected = amount * 0.2F;
            if (reflected > 0) {
                attacker.hurt(this.damageSources().thorns(this), reflected);
            }
        }

        return wasHurt;
    }

    // ================================================================
    // Ranged combat — RangedAttackMob implementation
    // ================================================================

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack arrowStack = findArrowInInventory();
        if (arrowStack.isEmpty()) return;

        ItemStack weapon = this.getMainHandItem();
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, distanceFactor, weapon);

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float inaccuracy = SquireConfig.rangedInaccuracy.get().floatValue();
        arrow.shoot(dx, dy + dist * 0.2, dz, 1.6F, inaccuracy);
        this.level().addFreshEntity(arrow);

        // Consume one arrow
        arrowStack.shrink(1);

        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /** Find arrow stack in squire inventory. */
    public ItemStack findArrowInInventory() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.ARROW) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Check if squire has a bow in mainhand. */
    public boolean hasBowEquipped() {
        return this.getMainHandItem().getItem() instanceof BowItem;
    }

    /** Check if squire has arrows available. */
    public boolean hasArrows() {
        return !findArrowInInventory().isEmpty();
    }

    // ================================================================
    // Swimming — boost speed in water to keep up with player
    // ================================================================

    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        // Don't apply travel when riding — let the horse handle movement
        if (this.isPassenger()) {
            super.travel(travelVector);
            return;
        }
        if (this.isInWater() && this.isEffectiveAi()) {
            this.moveRelative(0.12F, travelVector);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.72D));
            if (this.horizontalCollision && this.onClimbable()) {
                this.setDeltaMovement(this.getDeltaMovement().x, 0.3D, this.getDeltaMovement().z);
            }
        } else {
            super.travel(travelVector);
        }
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
                // Apply pending horse UUID from NBT
                if (this.pendingHorseUUID != null) {
                    this.squireAI.getMount().setHorseUUID(this.pendingHorseUUID);
                }
            }
            this.squireAI.tick();

            // Chunk loading: keep squire's chunk loaded during area clear if owner is online
            SquireChunkLoader.tick(this);

            // Fire resistance (level-gated)
            if (SquireAbilities.hasFireResistance(this)) {
                if (!this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                    // 12 seconds, re-applied every tick cycle so it never expires
                    this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0, false, false));
                }
            }

            // Shield blocking (level-gated) — raise shield when targeted by ranged mob
            if (SquireAbilities.hasShieldBlock(this)
                    && SquireEquipmentHelper.isShield(this.getOffhandItem())) {
                LivingEntity target = this.getTarget();
                boolean shouldBlock = target != null && target.isAlive()
                        && target instanceof net.minecraft.world.entity.monster.RangedAttackMob
                        && this.distanceToSqr(target) > 16.0; // >4 blocks away
                if (shouldBlock && !this.isUsingItem()) {
                    this.startUsingItem(InteractionHand.OFF_HAND);
                } else if (!shouldBlock && this.isUsingItem()
                        && this.getUsedItemHand() == InteractionHand.OFF_HAND) {
                    this.stopUsingItem();
                }
            }

            // Tick undying cooldown
            if (this.undyingCooldown > 0) {
                this.undyingCooldown--;
            }

            // Squire armor set bonus: Regen I every 10 seconds when all 4 pieces equipped
            if (this.tickCount % 200 == 0
                    && this.getHealth() < this.getMaxHealth()
                    && com.sjviklabs.squire.item.SquireArmorItem.isFullSquireArmor(this)) {
                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, false));
            }

            if (++this.equipCheckTimer >= SquireConfig.equipCheckInterval.get()) {
                this.equipCheckTimer = 0;
                // Skip equip check while mining/placing — prevents weapon overwriting the
                // selected tool mid-break, which causes the wrong item to render in hand.
                if (!isInWorkState()) {
                    SquireEquipmentHelper.runFullEquipCheck(this);
                }
            }
        }
    }

    /**
     * Whether the squire is currently in a work state (mining or placing).
     * Used to suppress auto-equip checks that would overwrite the selected tool.
     */
    public boolean isInWorkState() {
        if (this.squireAI == null) return false;
        SquireAIState state = this.squireAI.getMachine().getCurrentState();
        return state == SquireAIState.MINING_APPROACH || state == SquireAIState.MINING_BREAK
                || state == SquireAIState.PLACING_APPROACH || state == SquireAIState.PLACING_BLOCK;
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
        // ~25% chance to make a sound when vanilla calls this (avg every 80 ticks)
        // Uses villager ambient sound — a subtle "hmm" that feels human
        if (this.getRandom().nextFloat() < 0.25F) {
            return SoundEvents.VILLAGER_AMBIENT;
        }
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F; // Quieter than default — squire shouldn't be louder than mobs
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
