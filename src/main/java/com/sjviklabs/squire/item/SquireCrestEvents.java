package com.sjviklabs.squire.item;

import com.sjviklabs.squire.SquireMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Game bus events for the Squire's Crest.
 * Handles left-click block (pos2 selection) and logout cleanup.
 */
@EventBusSubscriber(modid = SquireMod.MODID)
public class SquireCrestEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity().getMainHandItem().getItem() instanceof SquireCrestItem)) return;
        if (!event.getEntity().isShiftKeyDown()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);

        BlockPos pos = event.getPos();
        SquireCrestItem.setPos2(player.getUUID(), pos);

        player.displayClientMessage(
                Component.literal("Pos2 set: (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"), true);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SquireCrestItem.clearPositions(event.getEntity().getUUID());
    }
}