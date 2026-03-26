package com.sjviklabs.squire.entity;

import com.sjviklabs.squire.ai.SquireActivityLog;
import com.sjviklabs.squire.ai.handler.ProgressionHandler;
import com.sjviklabs.squire.ai.statemachine.SquireAI;
import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.compat.MineColoniesCompat;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
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
        // Proactive aggro — engage any mob implementing Enemy (Monster, Slime, MagmaCube,
        // Phantom, Ghast, Shulker, etc.) without waiting to be hit first.
        // MineColonies compat: also excludes colonists from targeting, includes raiders.
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                (entity) -> {
                    if (MineColoniesCompat.isFriendly(entity)) return false;
                    return entity instanceof Enemy || MineColoniesCompat.isRaider(entity);
                }));

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
        // Guard: only fire if holding a bow. Prevents AbstractArrow crash on invalid weapon.
        if (!(weapon.getItem() instanceof BowItem)) return;
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
            this.moveRelative(0.14F, travelVector); // Faster than default 0.02
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.78D)); // Less drag
            if (this.horizontalCollision && this.onClimbable()) {
                this.setDeltaMovement(this.getDeltaMovement().x, 0.3D, this.getDeltaMovement().z);
            }
            // Swim upward if submerged — stay at surface
            if (this.isUnderWater()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.04, 0));
            }
        } else {
            super.travel(travelVector);
        }
    }


    // ================================================================
    // Tick — periodic equipment check
    // ================================================================

    private int equipCheckTimer = 0;

    // ---- Safety rails: stuck detection ----
    private double lastSafeX, lastSafeY, lastSafeZ;
    private int stuckTicks = 0;
    private static final int STUCK_CHECK_INTERVAL = 100; // 5 seconds
    private static final int STUCK_JUMP_THRESHOLD = 200; // 10 seconds
    private static final int STUCK_TELEPORT_THRESHOLD = 400; // 20 seconds

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

            // ---- Safety rails ----
            tickSafetyRails();

            // MineColonies raid defense: boost follow range during active raids
            // so squire detects raiders from further away. Check every 2 seconds.
            if (this.tickCount % 40 == 0 && MineColoniesCompat.isActive()) {
                double raidRange = MineColoniesCompat.getRaidAggroRange(this);
                double currentFollow = this.getAttributeValue(Attributes.FOLLOW_RANGE);
                double baseFollow = 32.0; // matches createAttributes()
                if (raidRange > SquireConfig.aggroRange.get() && currentFollow <= baseFollow) {
                    // Raid active — temporarily boost follow range
                    this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(raidRange + 16.0);
                    if (this.activityLog != null) {
                        this.activityLog.log("RAID", "Colony raid detected! Boosting aggro range.");
                    }
                } else if (raidRange <= SquireConfig.aggroRange.get() && currentFollow > baseFollow) {
                    // Raid over — restore normal range
                    this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(baseFollow);
                    if (this.activityLog != null) {
                        this.activityLog.log("RAID", "Raid over. Returning to normal patrol.");
                    }
                }
            }

            // Passive item pickup: absorb items within reach without changing AI state.
            // This runs every tick so the squire grabs loot it walks over, like a player.
            pickUpNearbyItems();

            if (++this.equipCheckTimer >= SquireConfig.equipCheckInterval.get()) {
                this.equipCheckTimer = 0;
                // Skip equip check while mining/placing — prevents weapon overwriting the
                // selected tool mid-break, which causes the wrong item to render in hand.
                if (!isInWorkState()) {
                    SquireEquipmentHelper.tryCraftBasicGear(this);
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

    /**
     * Passively pick up item entities within 1.5 blocks. Runs every tick in aiStep()
     * so the squire collects loot it walks over regardless of AI state.
     * Skips items with a pickup delay, items that won't fit in inventory, and junk.
     */
    private void pickUpNearbyItems() {
        if (this.isOrderedToSit()) return;

        double range = 1.5;
        var box = this.getBoundingBox().inflate(range);
        var items = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, box,
                item -> item.isAlive() && !item.hasPickUpDelay());

        for (var itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;
            if (!this.inventory.canAddItem(stack)) continue;

            ItemStack original = stack.copy();
            ItemStack remainder = this.inventory.addItem(stack.copy());

            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }

            SquireEquipmentHelper.tryAutoEquip(this, original);

            if (this.activityLog != null) {
                this.activityLog.log("ITEM", "Picked up " + original.getCount()
                        + "x " + original.getHoverName().getString());
            }
        }
    }

    // ================================================================
    // Safety rails — stuck detection, drowning, fall, combat retreat
    // ================================================================

    private void tickSafetyRails() {
        tickStuckDetection();
        tickDrowningProtection();
        tickFallProtection();
        tickCombatRetreat();
    }

    /**
     * Track squire position. If it hasn't moved significantly while the navigation
     * system thinks it should be pathing, escalate: try jumping, then emergency teleport.
     */
    private void tickStuckDetection() {
        if (this.isOrderedToSit() || this.isPassenger()) {
            stuckTicks = 0;
            return;
        }

        if (this.tickCount % STUCK_CHECK_INTERVAL == 0) {
            double dx = this.getX() - lastSafeX;
            double dy = this.getY() - lastSafeY;
            double dz = this.getZ() - lastSafeZ;
            double movedSq = dx * dx + dy * dy + dz * dz;

            boolean isPathing = this.getNavigation().isInProgress();

            if (movedSq < 1.0 && isPathing) {
                // Squire is trying to move but stuck
                stuckTicks += STUCK_CHECK_INTERVAL;

                if (stuckTicks >= STUCK_TELEPORT_THRESHOLD) {
                    // Emergency teleport to owner
                    Player owner = this.getOwner() instanceof Player p ? p : null;
                    if (owner != null) {
                        teleportToSafeSpot(owner);
                        stuckTicks = 0;
                        if (this.activityLog != null) {
                            this.activityLog.log("SAFETY", "Emergency teleport — stuck for "
                                    + (STUCK_TELEPORT_THRESHOLD / 20) + " seconds");
                        }
                    }
                } else if (stuckTicks >= STUCK_JUMP_THRESHOLD) {
                    // Try jumping to unstick
                    if (this.onGround()) {
                        this.jumpFromGround();
                    }
                }
            } else {
                stuckTicks = 0;
            }

            lastSafeX = this.getX();
            lastSafeY = this.getY();
            lastSafeZ = this.getZ();
        }
    }

    /**
     * Prevent drowning: apply Water Breathing when submerged. Actively swim upward
     * when underwater and not sitting. Boost swim speed to keep up with owner.
     */
    private void tickDrowningProtection() {
        if (this.isUnderWater()) {
            // Water breathing — prevents drowning damage
            if (!this.hasEffect(MobEffects.WATER_BREATHING)) {
                this.addEffect(new MobEffectInstance(
                        MobEffects.WATER_BREATHING, 600, 0, false, false));
            }

            // Actively swim upward if submerged and not sitting
            if (!this.isOrderedToSit()) {
                // Swim toward surface
                net.minecraft.world.phys.Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x, Math.max(motion.y, 0.04), motion.z);
            }
        }

        // Boost swim speed when in water (not just submerged)
        if (this.isInWater() && !this.isOrderedToSit()) {
            if (!this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                // Light dolphin's grace effect for swim speed
                this.addEffect(new MobEffectInstance(
                        MobEffects.DOLPHINS_GRACE, 100, 0, false, false));
            }
        }
    }

    /**
     * Prevent lethal falls: apply Slow Falling when falling fast or near void.
     * Kill the squire cleanly (with drops) before reaching void death zone.
     */
    private void tickFallProtection() {
        // Void protection: if below Y=-50, emergency teleport to owner
        if (this.getY() < -50.0) {
            Player owner = this.getOwner() instanceof Player p ? p : null;
            if (owner != null && owner.getY() > -50.0) {
                teleportToSafeSpot(owner);
                if (this.activityLog != null) {
                    this.activityLog.log("SAFETY", "Rescued from void");
                }
            }
            return;
        }

        // Slow falling when dropping fast (fall speed > 0.5 blocks/tick)
        if (!this.onGround() && this.getDeltaMovement().y < -0.5 && !this.isInWater()) {
            if (!this.hasEffect(MobEffects.SLOW_FALLING)) {
                this.addEffect(new MobEffectInstance(
                        MobEffects.SLOW_FALLING, 200, 0, false, false));
            }
        }
    }

    /**
     * Combat retreat: if health is below 20% and no food in inventory, flee toward owner.
     * The FLEEING state already exists in the enum — this activates it.
     */
    private void tickCombatRetreat() {
        if (this.squireAI == null) return;
        SquireAIState state = this.squireAI.getMachine().getCurrentState();

        // Only retreat from active combat states
        boolean inCombat = state == SquireAIState.COMBAT_APPROACH
                || state == SquireAIState.COMBAT_ATTACK
                || state == SquireAIState.COMBAT_RANGED;
        if (!inCombat) return;

        float hpRatio = this.getHealth() / this.getMaxHealth();
        if (hpRatio > 0.2F) return; // Above 20%, keep fighting

        // Check if squire has food to heal
        boolean hasFood = false;
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                hasFood = true;
                break;
            }
        }

        if (!hasFood) {
            // No food, critically low — flee toward owner
            Player owner = this.getOwner() instanceof Player p ? p : null;
            if (owner != null) {
                this.setTarget(null); // Drop combat target
                this.getNavigation().moveTo(owner, 1.4); // Sprint away
                this.setSquireSprinting(true);
                if (this.activityLog != null && this.tickCount % 60 == 0) {
                    this.activityLog.log("SAFETY", "Retreating — critically low HP, no food!");
                }
            }
        }
    }

    /**
     * Emergency teleport to a safe spot near a target entity.
     * Tries random offsets, finds solid ground. Used by stuck detection and void rescue.
     */
    private void teleportToSafeSpot(Player target) {
        net.minecraft.core.BlockPos targetPos = target.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = net.minecraft.util.Mth.randomBetweenInclusive(this.getRandom(), -3, 3);
            int dz = net.minecraft.util.Mth.randomBetweenInclusive(this.getRandom(), -3, 3);
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;

            net.minecraft.core.BlockPos check = targetPos.offset(dx, 0, dz);
            for (int dy = -2; dy <= 2; dy++) {
                net.minecraft.core.BlockPos pos = check.offset(0, dy, 0);
                if (this.level().getBlockState(pos.below()).isSolid()
                        && this.level().getBlockState(pos).isAir()
                        && this.level().getBlockState(pos.above()).isAir()) {
                    this.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                            this.getYRot(), this.getXRot());
                    this.getNavigation().stop();
                    return;
                }
            }
        }
        // Fallback: teleport directly to target
        this.moveTo(target.getX(), target.getY(), target.getZ(),
                this.getYRot(), this.getXRot());
        this.getNavigation().stop();
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
