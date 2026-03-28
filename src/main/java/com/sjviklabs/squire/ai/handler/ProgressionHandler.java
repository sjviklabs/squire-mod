package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireDataAttachment;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.util.SquireAbilities;
import com.sjviklabs.squire.util.SquireAdvancements;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Tracks XP, computes level, and applies per-level attribute bonuses.
 *
 * XP sources: kills ({@link #addKillXP()}) and mining ({@link #addMineXP()}).
 * Level = min(totalXP / xpPerLevel, maxLevel).
 * Per-level bonuses: +healthPerLevel HP, +damagePerLevel attack, +speedPerLevel move speed.
 *
 * Attribute modifiers use stable ResourceLocation IDs so they survive save/load.
 */
public class ProgressionHandler {

    private static final ResourceLocation LEVEL_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("squire", "level_health_bonus");
    private static final ResourceLocation LEVEL_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath("squire", "level_damage_bonus");
    private static final ResourceLocation LEVEL_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("squire", "level_speed_bonus");

    private final SquireEntity squire;
    private int totalXP;
    private int currentLevel;

    public ProgressionHandler(SquireEntity squire) {
        this.squire = squire;
    }

    public int getTotalXP() { return totalXP; }
    public int getCurrentLevel() { return currentLevel; }

    /** Restore progression from player attachment data (used on resummon). */
    public void setFromAttachment(int xp, int level) {
        this.totalXP = xp;
        this.currentLevel = level;
        applyModifiers();
        squire.setSquireLevel(level);
    }

    /** Add XP from a kill. */
    public void addKillXP() {
        addXP(SquireConfig.xpPerKill.get());
        // Grant first kill advancement to owner
        if (squire.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner) {
            SquireAdvancements.grantFirstKill(owner);
        }
    }

    /** Add XP from mining a block. */
    public void addMineXP() {
        addXP(SquireConfig.xpPerBlock.get());
    }

    /** Add XP from harvesting a mature crop. */
    public void addHarvestXP() { addXP(SquireConfig.xpPerHarvest.get()); }

    /** Add XP from catching a fish. */
    public void addFishXP() { addXP(SquireConfig.xpPerFish.get()); }

    /** Add XP when a patrol loop completes (squire cycles back to first waypoint). */
    public void addPatrolLoopXP() { addXP(SquireConfig.xpPerPatrolLoop.get()); }

    /** Add XP when a queued task completes. */
    public void addQueuedTaskXP() { addXP(SquireConfig.xpPerQueuedTask.get()); }

    /** Add XP from placing a block. */
    public void addPlaceXP() { addXP(SquireConfig.xpPerPlace.get()); }

    /** Add XP from chopping a log (awarded in addition to mine XP). */
    public void addChopXP() { addXP(SquireConfig.xpPerChop.get()); }

    private void addXP(int amount) {
        int oldLevel = currentLevel;
        this.totalXP += amount;
        recalculateLevel();
        if (currentLevel > oldLevel) {
            onLevelUp();
        }
    }

    /** Recalculate level from XP using scaling curve and update attribute modifiers. */
    private void recalculateLevel() {
        int baseXP = SquireConfig.xpPerLevel.get();
        int max = SquireConfig.maxLevel.get();
        this.currentLevel = SquireAbilities.calculateLevel(totalXP, baseXP, max);
        applyModifiers();
    }

    /** Levels that trigger milestone celebrations. */
    private static final java.util.Set<Integer> MILESTONE_LEVELS =
            java.util.Set.of(5, 10, 20, 30);

    private void onLevelUp() {
        if (squire.level() instanceof ServerLevel serverLevel) {
            boolean milestone = MILESTONE_LEVELS.contains(currentLevel);
            if (milestone) {
                // Challenge-complete fanfare + totem burst
                serverLevel.playSound(null, squire.blockPosition(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 1.0F, 1.0F);
                double x = squire.getX();
                double y = squire.getY() + 1.0;
                double z = squire.getZ();
                for (int i = 0; i < 50; i++) {
                    double ox = (squire.getRandom().nextDouble() - 0.5) * 2.0;
                    double oy = squire.getRandom().nextDouble() * 2.0;
                    double oz = (squire.getRandom().nextDouble() - 0.5) * 2.0;
                    serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            x, y, z, 1, ox, oy, oz, 0.1);
                }
            } else {
                // Normal level-up ding + enchant sparkle
                serverLevel.playSound(null, squire.blockPosition(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        squire.getX(), squire.getY() + 1.0, squire.getZ(),
                        20, 0.5, 0.5, 0.5, 0.1);
            }
        }
        // Sync level to client
        squire.setSquireLevel(currentLevel);
        // Heal to new max on level up
        squire.setHealth(squire.getMaxHealth());

        // Grant advancement + sync to player attachment
        if (squire.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner) {
            SquireAdvancements.grantLevel(owner, currentLevel);
            var data = owner.getData(SquireDataAttachment.SQUIRE_DATA.get());
            owner.setData(SquireDataAttachment.SQUIRE_DATA.get(),
                    data.withXP(totalXP, currentLevel));
        }

        // Chat line
        if (squire.getSquireAI() != null) {
            squire.getSquireAI().getChat().onLevelUp(currentLevel);
        }

        var log = squire.getActivityLog();
        if (log != null) {
            log.log("LEVEL", "Leveled up to Lv." + currentLevel
                    + " (" + totalXP + " XP, max HP " + String.format("%.0f", squire.getMaxHealth()) + ")");
        }
    }

    /** Apply (or update) attribute modifiers based on current level. */
    private void applyModifiers() {
        double healthBonus = currentLevel * SquireConfig.healthPerLevel.get();
        double damageBonus = currentLevel * SquireConfig.damagePerLevel.get();
        double speedBonus = currentLevel * SquireConfig.speedPerLevel.get();

        setModifier(Attributes.MAX_HEALTH, LEVEL_HEALTH_ID, healthBonus);
        setModifier(Attributes.ATTACK_DAMAGE, LEVEL_DAMAGE_ID, damageBonus);
        setModifier(Attributes.MOVEMENT_SPEED, LEVEL_SPEED_ID, speedBonus);
    }

    private void setModifier(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                             ResourceLocation id, double amount) {
        AttributeInstance instance = squire.getAttribute(attribute);
        if (instance == null) return;

        // Remove old modifier if present
        instance.removeModifier(id);

        if (amount > 0) {
            instance.addPermanentModifier(new AttributeModifier(
                    id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ---- NBT persistence ----

    public void save(CompoundTag tag) {
        CompoundTag prog = new CompoundTag();
        prog.putInt("TotalXP", totalXP);
        prog.putInt("Level", currentLevel);
        tag.put("SquireProgression", prog);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("SquireProgression")) {
            CompoundTag prog = tag.getCompound("SquireProgression");
            this.totalXP = prog.getInt("TotalXP");
            int savedLevel = prog.getInt("Level");
            // Recompute from XP+config using scaling curve
            int computed = SquireAbilities.calculateLevel(totalXP,
                    SquireConfig.xpPerLevel.get(), SquireConfig.maxLevel.get());
            this.currentLevel = Math.min(savedLevel, computed);
            applyModifiers();
            squire.setSquireLevel(currentLevel);
        }
    }
}
