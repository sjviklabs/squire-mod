package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.item.SquireLanceItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Handles horse finding, mounting, dismounting, and mounted navigation.
 *
 * NeoForge 1.21.1 horse movement with NPC riders:
 * - AbstractHorse.getControllingPassenger() only returns Player instances.
 * - AbstractHorse.travel() overwrites any delta movement we set each tick.
 * - The horse's goal system can also zero out its own movement input.
 * - Solution: call horse.move(MoverType.SELF, vec) directly each tick to
 *   physically push the horse with collision detection. This bypasses
 *   travel() and the goal system entirely.
 */
public class MountHandler {

    private final SquireEntity squire;
    private UUID horseUUID;

    public MountHandler(SquireEntity squire) {
        this.squire = squire;
    }

    // ---- Public API ----

    public UUID getHorseUUID() { return horseUUID; }
    public void setHorseUUID(UUID uuid) { this.horseUUID = uuid; }

    public boolean isMounted() {
        return squire.isPassenger() && squire.getVehicle() instanceof AbstractHorse;
    }

    public boolean orderMount() {
        AbstractHorse horse = findAssignedHorse();
        if (horse == null) {
            horse = findNearestSaddledHorse();
        }
        if (horse != null) {
            horseUUID = horse.getUUID();
            return true;
        }
        return false;
    }

    public void orderDismount() {
        if (isMounted()) {
            squire.stopRiding();
        }
        horseUUID = null;
    }

    public boolean shouldAutoMount() {
        if (!SquireConfig.autoMountEnabled.get()) return false;
        if (horseUUID == null) return false;
        if (squire.isOrderedToSit()) return false;
        if (isMounted()) return false;
        return findAssignedHorse() != null;
    }

    public void startApproach() {}

    public SquireAIState tickApproach(SquireEntity s) {
        AbstractHorse horse = findAssignedHorse();
        if (horse == null) {
            s.getNavigation().stop();
            return SquireAIState.IDLE;
        }

        double distSq = s.distanceToSqr(horse);
        if (distSq <= 4.0) {
            s.startRiding(horse, true);
            s.getNavigation().stop();
            horse.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0, false, false));
            return SquireAIState.MOUNTED_IDLE;
        }

        s.getNavigation().moveTo(horse, 1.2);
        return SquireAIState.MOUNTING;
    }

    public SquireAIState tickMountedFollow(SquireEntity s) {
        if (!isMounted()) return SquireAIState.IDLE;

        Player owner = s.getOwner() instanceof Player p ? p : null;
        if (owner == null) return SquireAIState.MOUNTED_IDLE;

        AbstractHorse horse = (AbstractHorse) s.getVehicle();
        double distSq = s.distanceToSqr(owner);
        double stopDist = SquireConfig.followStopDistance.get();
        double startDist = SquireConfig.followStartDistance.get();

        if (distSq < stopDist * stopDist) {
            return SquireAIState.MOUNTED_IDLE;
        }

        // Sprint if far, trot if close
        float speedMult = distSq > startDist * startDist * 4 ? 1.0F : 0.5F;
        driveHorseToward(s, horse, owner.getX(), owner.getY(), owner.getZ(), speedMult);

        // Keep Speed buff active
        if (!horse.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            horse.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0, false, false));
        }

        return SquireAIState.MOUNTED_FOLLOW;
    }

    public SquireAIState tickMountedCombat(SquireEntity s) {
        if (!isMounted()) return SquireAIState.COMBAT_APPROACH;

        LivingEntity target = s.getTarget();
        if (target == null || !target.isAlive()) {
            return SquireAIState.MOUNTED_IDLE;
        }

        AbstractHorse horse = (AbstractHorse) s.getVehicle();
        s.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSq = s.distanceToSqr(target);
        double reach = getMountedReach(s, target);

        if (distSq <= reach * reach) {
            driveHorseToward(s, horse, target.getX(), target.getY(), target.getZ(), 0.3F);
            return s.getSquireAI().getCombat().tick(s);
        }

        // Charge at full speed
        driveHorseToward(s, horse, target.getX(), target.getY(), target.getZ(), 1.0F);
        return SquireAIState.MOUNTED_COMBAT;
    }

    public void onHorseDied() {
        horseUUID = null;
    }

    // ---- Horse driving ----

    /**
     * Drive the horse toward a target position using horse.move(MoverType.SELF, vec).
     * This directly changes the horse's position with collision detection, bypassing
     * the travel() system and goal-based movement entirely.
     */
    private void driveHorseToward(SquireEntity s, AbstractHorse horse,
                                   double targetX, double targetY, double targetZ,
                                   float speedMult) {
        double dx = targetX - horse.getX();
        double dz = targetZ - horse.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 1.5) return;

        // Rotate horse to face target — smooth turn
        float targetYRot = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        float currentYRot = horse.getYRot();
        float diff = targetYRot - currentYRot;
        while (diff > 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        float maxTurn = 12.0F;
        float smoothed = currentYRot + Math.max(-maxTurn, Math.min(maxTurn, diff));

        horse.setYRot(smoothed);
        horse.yBodyRot = smoothed;
        horse.yHeadRot = smoothed;
        s.setYRot(smoothed);
        s.yBodyRot = smoothed;

        // Move speed: horse attribute * multiplier. Typical horse speed is 0.1125-0.3375.
        // At multiplier 3.5 with speedMult 1.0: 0.39-1.18 blocks/tick = good gallop.
        double horseSpeed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double moveSpeed = horseSpeed * speedMult * 3.5;

        // Direction unit vector
        double nx = dx / dist;
        double nz = dz / dist;

        // Directly move the horse with collision handling
        Vec3 movement = new Vec3(nx * moveSpeed, 0, nz * moveSpeed);
        horse.move(MoverType.SELF, movement);

        // Step-up: if horse hit a wall and is on ground, hop up one block
        if (horse.horizontalCollision && horse.onGround()) {
            horse.move(MoverType.SELF, new Vec3(0, 0.6, 0));
            // Try forward again after stepping up
            horse.move(MoverType.SELF, movement);
        }

        // Apply gravity if not on ground
        if (!horse.onGround()) {
            horse.move(MoverType.SELF, new Vec3(0, -0.08, 0));
        }

        // Sync to clients
        horse.hasImpulse = true;

        // Animate legs
        horse.walkAnimation.setSpeed(Math.min((float) moveSpeed * 8.0F, 1.5F));
    }

    // ---- Private helpers ----

    @Nullable
    private AbstractHorse findAssignedHorse() {
        if (horseUUID == null) return null;
        double range = SquireConfig.horseSearchRange.get();
        AABB searchBox = squire.getBoundingBox().inflate(range);
        List<AbstractHorse> horses = squire.level().getEntitiesOfClass(
                AbstractHorse.class, searchBox,
                h -> h.getUUID().equals(horseUUID) && h.isAlive());
        return horses.isEmpty() ? null : horses.get(0);
    }

    @Nullable
    private AbstractHorse findNearestSaddledHorse() {
        double range = SquireConfig.horseSearchRange.get();
        AABB searchBox = squire.getBoundingBox().inflate(range);
        Player owner = squire.getOwner() instanceof Player p ? p : null;

        List<AbstractHorse> horses = squire.level().getEntitiesOfClass(
                AbstractHorse.class, searchBox,
                h -> h.isAlive() && h.isSaddled() && !h.isVehicle()
                        && h.isTamed()
                        && (owner == null || h.getOwnerUUID() == null
                            || h.getOwnerUUID().equals(owner.getUUID())));

        if (horses.isEmpty()) return null;

        AbstractHorse closest = null;
        double closestDist = Double.MAX_VALUE;
        for (AbstractHorse h : horses) {
            double d = squire.distanceToSqr(h);
            if (d < closestDist) {
                closestDist = d;
                closest = h;
            }
        }
        return closest;
    }

    private double getMountedReach(SquireEntity s, LivingEntity target) {
        if (s.getMainHandItem().getItem() instanceof SquireLanceItem) {
            return 6.0;
        }
        return 2.5;
    }
}
