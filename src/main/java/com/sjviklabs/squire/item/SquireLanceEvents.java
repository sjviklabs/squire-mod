package com.sjviklabs.squire.item;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.init.ModItems;
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
 * Game bus events for the Squire's Lance.
 * Handles left-click block (pos2 selection) and logout cleanup.
 */
@EventBusSubscriber(modid = SquireMod.MODID)
public class SquireLanceEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity().getMainHandItem().getItem() instanceof SquireLanceItem)) return;

        // Only intercept for area selection when sneaking — normal left-click = melee attack
        if (!event.getEntity().isShiftKeyDown()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // Client side: cancel to prevent break animation during area select
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);

        BlockPos pos = event.getPos();
        SquireLanceItem.setPos2(player.getUUID(), pos);

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
        SquireLanceItem.clearPositions(event.getEntity().getUUID());
    }
}
