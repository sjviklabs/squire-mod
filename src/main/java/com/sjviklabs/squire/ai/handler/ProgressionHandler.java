package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.util.SquireAbilities;
import net.minecraft.core.Holder;
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

    /** Add XP from a kill. */
    public void addKillXP() {
        addXP(SquireConfig.xpPerKill.get());
    }

    /** Add XP from mining a block. */
    public void addMineXP() {
        addXP(SquireConfig.xpPerBlock.get());
    }

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

    private void onLevelUp() {
        if (squire.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, squire.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        // Sync level to client
        squire.setSquireLevel(currentLevel);
        // Heal to new max on level up
        squire.setHealth(squire.getMaxHealth());

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
