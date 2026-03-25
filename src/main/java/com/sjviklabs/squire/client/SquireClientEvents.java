package com.sjviklabs.squire.client;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Client-side event handlers for squire interactions.
 * Registered on the GAME event bus (NeoForge runtime events).
 */
@EventBusSubscriber(modid = SquireMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SquireClientEvents {

    /**
     * Each client tick, consume the keybind press and open the radial screen.
     * Once open, the screen handles its own close via keyReleased/mouseClicked.
     * We do NOT try to close the screen from here — that causes flicker because
     * isDown() reports false for a frame when a Screen captures input.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // Don't open while any screen is showing

        // consumeClick() returns true once per key press (rising edge, no repeat)
        while (SquireKeybinds.RADIAL_MENU.consumeClick()) {
            SquireEntity target = findTargetSquire(mc);
            if (target != null) {
                mc.setScreen(new SquireRadialScreen(target));
            }
        }
    }

    /**
     * Find the squire the player is looking at, or the nearest owned squire within 8 blocks.
     * Prefers crosshair-targeted squire over proximity.
     */
    private static SquireEntity findTargetSquire(Minecraft mc) {
        // First check crosshair hit
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof SquireEntity squire && squire.isOwnedBy(mc.player)) {
                return squire;
            }
        }

        // Fallback: find nearest owned squire within 8 blocks
        AABB searchBox = mc.player.getBoundingBox().inflate(8.0);
        List<SquireEntity> nearby = mc.level.getEntitiesOfClass(SquireEntity.class, searchBox,
                s -> s.isOwnedBy(mc.player));

        if (nearby.isEmpty()) return null;

        SquireEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (SquireEntity s : nearby) {
            double dist = mc.player.distanceToSqr(s);
            if (dist < closestDist) {
                closestDist = dist;
                closest = s;
            }
        }
        return closest;
    }
}