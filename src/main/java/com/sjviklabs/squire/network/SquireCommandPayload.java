package com.sjviklabs.squire.network;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.ai.handler.ChestHandler;
import com.sjviklabs.squire.ai.handler.PatrolHandler;
import com.sjviklabs.squire.ai.statemachine.SquireAI;
import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.init.ModBlocks;
import com.sjviklabs.squire.inventory.SquireEquipmentContainer;
import com.sjviklabs.squire.inventory.SquireMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
/**
 * Client-to-server payload dispatching a radial menu command to a squire.
 *
 * Wire format: Two VarInts: command ID, then squire entity network ID.
 *
 * Command IDs:
 *   0 - Follow, 1 - Guard, 2 - Patrol, 3 - Stay,
 *   4 - Store,  5 - Fetch, 6 - Mount/Dismount, 7 - Open Inventory
 */
public record SquireCommandPayload(int commandId, int squireEntityId) implements CustomPacketPayload {

    // Command ID constants — match wedge order in SquireRadialScreen
    public static final int CMD_FOLLOW = 0;
    public static final int CMD_GUARD = 1;
    public static final int CMD_PATROL = 2;
    public static final int CMD_STAY = 3;
    public static final int CMD_STORE = 4;
    public static final int CMD_FETCH = 5;
    public static final int CMD_MOUNT = 6;
    public static final int CMD_INVENTORY = 7;

    public static final CustomPacketPayload.Type<SquireCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "squire_command"));

    public static final StreamCodec<ByteBuf, SquireCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SquireCommandPayload::commandId,
                    ByteBufCodecs.VAR_INT,
                    SquireCommandPayload::squireEntityId,
                    SquireCommandPayload::new
            );
    @Override
    public CustomPacketPayload.Type<SquireCommandPayload> type() {
        return TYPE;
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                TYPE,
                STREAM_CODEC,
                SquireCommandPayload::handle
        );
    }

    // ------------------------------------------------------------------
    // Server-side handler
    // ------------------------------------------------------------------

    private static void handle(SquireCommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

        Entity entity = serverPlayer.serverLevel().getEntity(payload.squireEntityId());
        if (!(entity instanceof SquireEntity squire)) return;
        if (!squire.isOwnedBy(serverPlayer)) return;
        SquireAI ai = squire.getSquireAI();

        switch (payload.commandId()) {
            case CMD_FOLLOW -> {
                squire.setSquireMode(SquireEntity.MODE_FOLLOW);
                serverPlayer.displayClientMessage(Component.literal("Squire: Follow"), true);
            }
            case CMD_GUARD -> {
                squire.setSquireMode(SquireEntity.MODE_GUARD);
                serverPlayer.displayClientMessage(Component.literal("Squire: Guard"), true);
            }
            case CMD_STAY -> {
                squire.setSquireMode(SquireEntity.MODE_STAY);
                serverPlayer.displayClientMessage(Component.literal("Squire: Stay"), true);
            }
            case CMD_PATROL -> {
                if (ai != null) {
                    // Find nearest signpost within 16 blocks of the player
                    ServerLevel level = serverPlayer.serverLevel();
                    BlockPos playerPos = serverPlayer.blockPosition();
                    BlockPos nearest = findNearestSignpost(level, playerPos, 16);
                    if (nearest != null) {
                        java.util.List<BlockPos> route = PatrolHandler.buildRouteFromSignpost(level, nearest);
                        if (!route.isEmpty()) {
                            if (route.size() == 1) {
                                ai.getPatrol().startGuard(route.get(0));
                            } else {
                                ai.getPatrol().startPatrol(route);
                            }
                            ai.getMachine().forceState(SquireAIState.PATROL_WALK);                            serverPlayer.displayClientMessage(
                                    Component.literal("Squire: Patrol (" + route.size() + " waypoints)"), true);
                        } else {
                            serverPlayer.displayClientMessage(
                                    Component.literal("Could not build route from signpost."), true);
                        }
                    } else {
                        serverPlayer.displayClientMessage(
                                Component.literal("No signpost found within 16 blocks."), true);
                    }
                }
            }
            case CMD_STORE -> {
                if (ai != null) {
                    if (ai.getChest().setTarget(null, ChestHandler.ChestAction.STORE, null)) {
                        serverPlayer.displayClientMessage(Component.literal("Squire: Store"), true);
                    } else {
                        serverPlayer.displayClientMessage(
                                Component.literal("No chest nearby or ability not unlocked."), true);
                    }
                }
            }
            case CMD_FETCH -> {
                if (ai != null) {
                    if (ai.getChest().setTarget(null, ChestHandler.ChestAction.FETCH, null)) {
                        serverPlayer.displayClientMessage(Component.literal("Squire: Fetch"), true);
                    } else {
                        serverPlayer.displayClientMessage(
                                Component.literal("No chest nearby or ability not unlocked."), true);
                    }
                }
            }            case CMD_MOUNT -> {
                if (ai != null) {
                    if (squire.isPassenger()) {
                        ai.getMount().orderDismount();
                        serverPlayer.displayClientMessage(Component.literal("Squire: Dismount"), true);
                    } else {
                        ai.getMount().orderMount();
                        serverPlayer.displayClientMessage(Component.literal("Squire: Mount"), true);
                    }
                }
            }
            case CMD_INVENTORY -> {
                SquireEquipmentContainer equipContainer = new SquireEquipmentContainer(squire);
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, playerInventory, p) ->
                                new SquireMenu(containerId, playerInventory, squire.getSquireInventory(),
                                        equipContainer, squire, squire.getId()),
                        Component.translatable("container.squire.inventory")
                ), buf -> {
                    buf.writeVarInt(squire.getId());
                    buf.writeVarInt(squire.getSquireLevel());
                    buf.writeVarInt(squire.getProgression().getTotalXP());
                    buf.writeFloat(squire.getHealth());
                    buf.writeFloat(squire.getMaxHealth());
                    buf.writeByte(squire.getSquireMode());
                });
            }
            default -> { /* unknown command — ignore */ }
        }
    }
    /**
     * Find the nearest signpost block within the given range of a position.
     */
    @javax.annotation.Nullable
    private static BlockPos findNearestSignpost(ServerLevel level, BlockPos center, int range) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (BlockPos check : BlockPos.betweenClosed(
                center.offset(-range, -range, -range),
                center.offset(range, range, range))) {
            if (level.getBlockState(check).is(ModBlocks.SIGNPOST.get())) {
                double dist = check.distSqr(center);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = check.immutable();
                }
            }
        }
        return nearest;
    }
}