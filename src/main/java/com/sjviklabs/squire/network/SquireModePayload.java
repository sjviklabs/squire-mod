package com.sjviklabs.squire.network;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Client-to-server payload that toggles a squire between sit (stay) and follow mode.
 * <p>
 * Sent from the client when the player presses the mode-toggle keybind or UI button.
 * The server verifies the sender is the squire's owner before toggling.
 *
 * <h3>Wire format</h3>
 * Single {@code int} — the squire's entity network ID.
 */
public record SquireModePayload(int squireEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SquireModePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "squire_mode"));

    public static final StreamCodec<ByteBuf, SquireModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SquireModePayload::squireEntityId,
                    SquireModePayload::new
            );

    @Override
    public CustomPacketPayload.Type<SquireModePayload> type() {
        return TYPE;
    }

    // ------------------------------------------------------------------
    // Registration — call from mod constructor or event subscriber
    // ------------------------------------------------------------------

    /**
     * Register this payload on the NeoForge networking pipeline.
     * Must be called inside a {@code @SubscribeEvent} handler for
     * {@link RegisterPayloadHandlersEvent} on the MOD event bus.
     *
     * <pre>{@code
     * @SubscribeEvent
     * public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
     *     SquireModePayload.register(event);
     * }
     * }</pre>
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                TYPE,
                STREAM_CODEC,
                SquireModePayload::handle
        );
    }

    // ------------------------------------------------------------------
    // Server-side handler
    // ------------------------------------------------------------------

    /**
     * Process the toggle on the server. Runs on the main thread (default for
     * PayloadRegistrar). Validates:
     * <ol>
     *   <li>Sender is a ServerPlayer</li>
     *   <li>Entity ID resolves to a SquireEntity</li>
     *   <li>Sender is the squire's owner</li>
     * </ol>
     * Then toggles the squire's sit/follow state via {@code setOrderedToSit}.
     */
    private static void handle(SquireModePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

        Entity entity = serverPlayer.serverLevel().getEntity(payload.squireEntityId());
        if (!(entity instanceof SquireEntity squire)) return;

        // Only the owner can toggle mode
        if (!squire.isOwnedBy(serverPlayer)) return;

        // Toggle mode using the squire's own mode system, which syncs
        // both the custom SQUIRE_MODE data accessor and TamableAnimal's orderedToSit
        byte current = squire.getSquireMode();
        byte next = (current == SquireEntity.MODE_FOLLOW)
                ? SquireEntity.MODE_STAY
                : SquireEntity.MODE_FOLLOW;
        squire.setSquireMode(next);
    }
}
