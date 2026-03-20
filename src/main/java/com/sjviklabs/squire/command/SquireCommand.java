package com.sjviklabs.squire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sjviklabs.squire.ai.SquireActivityLog;
import com.sjviklabs.squire.ai.statemachine.SquireAI;
import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.init.ModEntities;
import com.sjviklabs.squire.item.SquireLanceItem;
import com.sjviklabs.squire.util.SquireAbilities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side commands for squire management.
 * <ul>
 *   <li>{@code /squire list}                    — list all active squires (op level 2)</li>
 *   <li>{@code /squire kill <player>}           — kill a player's squire (op level 2)</li>
 *   <li>{@code /squire limit <count>}           — set max squires per player at runtime (op level 3)</li>
 *   <li>{@code /squire info}                    — show your squire's state, level, XP (no op)</li>
 *   <li>{@code /squire mine <pos>}              — order squire to mine block at position (op level 2)</li>
 *   <li>{@code /squire place <pos> <block>}     — order squire to place block at position (op level 2)</li>
 *   <li>{@code /squire clear <from> <to>}       — preview area clear with particle outline (op level 2)</li>
 *   <li>{@code /squire clear confirm}           — execute the previewed area clear</li>
 *   <li>{@code /squire clear cancel}            — cancel preview or stop active clear</li>
 *   <li>{@code /squire xp <amount>}             — grant XP to your squire (op level 2)</li>
 * </ul>
 */
@EventBusSubscriber
public class SquireCommand {

    /** Pending clear previews, keyed by player UUID. Auto-expires after 30 seconds. */
    private static final Map<UUID, PendingClear> pendingClears = new HashMap<>();
    private static final int PREVIEW_TIMEOUT_TICKS = 600; // 30 seconds

    private record PendingClear(BlockPos from, BlockPos to, int dx, int dy, int dz, long createdTick) {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("squire")
                .then(Commands.literal("list")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> listSquires(ctx.getSource()))
                )
                .then(Commands.literal("kill")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> killSquire(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                        )
                )
                .then(Commands.literal("limit")
                        .requires(src -> src.hasPermission(3))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> setLimit(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))
                        )
                )
                .then(Commands.literal("info")
                        .executes(ctx -> showInfo(ctx.getSource()))
                )
                .then(Commands.literal("mine")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> orderMine(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))
                        )
                )
                .then(Commands.literal("place")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("block", ResourceArgument.resource(event.getBuildContext(), Registries.ITEM))
                                        .executes(ctx -> orderPlace(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                ResourceArgument.getResource(ctx, "block", Registries.ITEM).value()))
                                )
                        )
                )
                .then(Commands.literal("clear")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> confirmClear(ctx.getSource()))
                        )
                        .then(Commands.literal("cancel")
                                .executes(ctx -> cancelClear(ctx.getSource()))
                        )
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> previewClear(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "to")))
                                )
                        )
                )
                .then(Commands.literal("xp")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                                .executes(ctx -> grantXP(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))
                        )
                )
                .then(Commands.literal("log")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> showLog(ctx.getSource(), 20))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> showLog(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))
                        )
                )
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder2) -> {
                                    builder2.suggest("follow");
                                    builder2.suggest("stay");
                                    builder2.suggest("guard");
                                    return builder2.buildFuture();
                                })
                                .executes(ctx -> setMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))
                        )
                )
        );
    }

    // ------------------------------------------------------------------
    // /squire list
    // ------------------------------------------------------------------

    private static int listSquires(CommandSourceStack source) {
        List<SquireEntity> squires = findAllSquires(source);

        if (squires.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active squires in any loaded dimension."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Active squires (" + squires.size() + "):"), false);
        for (SquireEntity squire : squires) {
            String ownerName = resolveOwnerName(squire, source);
            String pos = String.format("(%.0f, %.0f, %.0f)",
                    squire.getX(), squire.getY(), squire.getZ());
            String dim = squire.level().dimension().location().toString();
            source.sendSuccess(() -> Component.literal(
                    "  - Owner: " + ownerName + " | " + dim + " " + pos +
                    " | HP: " + String.format("%.1f", squire.getHealth())
            ), false);
        }

        return squires.size();
    }

    // ------------------------------------------------------------------
    // /squire kill <player>
    // ------------------------------------------------------------------

    private static int killSquire(CommandSourceStack source, ServerPlayer targetPlayer) {
        List<SquireEntity> squires = findAllSquires(source);
        int killed = 0;

        for (SquireEntity squire : squires) {
            if (targetPlayer.getUUID().equals(squire.getOwnerUUID())) {
                squire.kill();
                killed++;
            }
        }

        if (killed == 0) {
            source.sendFailure(Component.literal(
                    targetPlayer.getName().getString() + " has no active squires."));
            return 0;
        }

        int finalKilled = killed;
        source.sendSuccess(() -> Component.literal(
                "Killed " + finalKilled + " squire(s) belonging to " + targetPlayer.getName().getString() + "."), true);
        return killed;
    }

    // ------------------------------------------------------------------
    // /squire limit <count>
    // ------------------------------------------------------------------

    private static int setLimit(CommandSourceStack source, int count) {
        SquireConfig.maxSquiresPerPlayer.set(count);

        source.sendSuccess(() -> Component.literal(
                "Max squires per player set to " + count + "."), true);
        return count;
    }

    // ------------------------------------------------------------------
    // /squire info
    // ------------------------------------------------------------------

    private static int showInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        SquireEntity squire = findOwnedSquire(source, player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }
        SquireAI ai = squire.getSquireAI();
        String state = ai != null ? ai.getMachine().getCurrentState().name() : "INITIALIZING";
        int level = squire.getSquireLevel();
        int xp = squire.getProgression().getTotalXP();
        int nextLevelXP = SquireAbilities.xpForLevel(level + 1, SquireConfig.xpPerLevel.get());
        float hp = squire.getHealth();
        float maxHp = squire.getMaxHealth();
        String pos = String.format("(%.0f, %.0f, %.0f)", squire.getX(), squire.getY(), squire.getZ());

        String mode = SquireEntity.modeName(squire.getSquireMode());
        String clearStatus = "";
        if (ai != null && ai.getMining().isAreaClearing()) {
            clearStatus = " | Clearing: " + (ai.getMining().getQueueSize() + 1) + " blocks remaining";
        }
        String finalClearStatus = clearStatus;
        source.sendSuccess(() -> Component.literal(
                "Squire " + pos + " | HP: " + String.format("%.1f/%.1f", hp, maxHp) +
                " | Lv." + level + " (" + xp + "/" + nextLevelXP + " XP) | Mode: " + mode + " | State: " + state + finalClearStatus), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /squire mine <pos>
    // ------------------------------------------------------------------

    private static int orderMine(CommandSourceStack source, BlockPos pos) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        SquireEntity squire = findOwnedSquire(source, player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }
        SquireAI ai = squire.getSquireAI();
        if (ai == null) {
            source.sendFailure(Component.literal("Squire AI not initialized yet."));
            return 0;
        }
        ai.getMining().setTarget(pos);
        ai.getMachine().forceState(SquireAIState.MINING_APPROACH);
        source.sendSuccess(() -> Component.literal(
                "Squire ordered to mine block at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /squire place <pos> <block>
    // ------------------------------------------------------------------

    private static int orderPlace(CommandSourceStack source, BlockPos pos, Item item) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        if (!(item instanceof BlockItem blockItem)) {
            source.sendFailure(Component.literal("Item must be a block item."));
            return 0;
        }
        SquireEntity squire = findOwnedSquire(source, player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }
        SquireAI ai = squire.getSquireAI();
        if (ai == null) {
            source.sendFailure(Component.literal("Squire AI not initialized yet."));
            return 0;
        }
        if (!ai.getPlacing().setTarget(pos, blockItem)) {
            source.sendFailure(Component.literal("Squire doesn't have that block in inventory."));
            return 0;
        }
        ai.getMachine().forceState(SquireAIState.PLACING_APPROACH);
        source.sendSuccess(() -> Component.literal(
                "Squire ordered to place " + item.getDescription().getString() +
                " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /squire clear <from> <to>  — preview with particle outline
    // /squire clear confirm      — execute pending clear
    // /squire clear cancel       — cancel preview or active clear
    // ------------------------------------------------------------------

    private static int previewClear(CommandSourceStack source, BlockPos from, BlockPos to) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        return previewClearForPlayer(player, from, to);
    }

    /**
     * Core preview logic, usable from both the command and the lance item.
     * Returns volume on success, 0 on failure (with chat feedback to player).
     */
    public static int previewClearForPlayer(ServerPlayer player, BlockPos from, BlockPos to) {
        SquireEntity squire = findOwnedSquireByPlayer(player);
        if (squire == null) {
            player.displayClientMessage(Component.literal("You have no active squire."), true);
            return 0;
        }
        SquireAI ai = squire.getSquireAI();
        if (ai == null) {
            player.displayClientMessage(Component.literal("Squire AI not initialized yet."), true);
            return 0;
        }

        int dx = Math.abs(from.getX() - to.getX()) + 1;
        int dy = Math.abs(from.getY() - to.getY()) + 1;
        int dz = Math.abs(from.getZ() - to.getZ()) + 1;
        int volume = dx * dy * dz;
        int maxVolume = SquireConfig.maxClearVolume.get();
        if (volume > maxVolume) {
            player.displayClientMessage(Component.literal(
                    "Area too large: " + volume + " blocks (max " + maxVolume + ")."), true);
            return 0;
        }

        long tick = player.server.getTickCount();
        pendingClears.put(player.getUUID(), new PendingClear(from, to, dx, dy, dz, tick));

        if (player.level() instanceof ServerLevel level) {
            spawnOutlineParticles(level, from, to);
        }

        player.displayClientMessage(Component.literal(
                "Clear preview: " + dx + "x" + dy + "x" + dz + " (" + volume + " blocks). " +
                "/squire clear confirm to start, cancel to abort."), false);
        return volume;
    }

    private static int confirmClear(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        PendingClear pending = pendingClears.remove(player.getUUID());
        if (pending == null) {
            source.sendFailure(Component.literal("No pending clear to confirm. Use /squire clear <from> <to> or the lance first."));
            return 0;
        }
        SquireLanceItem.clearPositions(player.getUUID());
        SquireEntity squire = findOwnedSquire(source, player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }
        SquireAI ai = squire.getSquireAI();
        if (ai == null) {
            source.sendFailure(Component.literal("Squire AI not initialized yet."));
            return 0;
        }

        int queued = ai.getMining().setAreaTarget(pending.from(), pending.to());
        if (queued == 0) {
            source.sendSuccess(() -> Component.literal("No breakable blocks found in region."), false);
            return 0;
        }

        ai.getMachine().forceState(SquireAIState.MINING_APPROACH);

        var log = squire.getActivityLog();
        if (log != null) {
            log.log("CLEAR", "Starting area clear, " + queued + " blocks queued");
        }

        source.sendSuccess(() -> Component.literal(
                "Squire clearing area: " + queued + " blocks (" + pending.dx() + "x" + pending.dy() + "x" + pending.dz() + ")"), false);
        return queued;
    }

    private static int cancelClear(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        boolean hadPending = pendingClears.remove(player.getUUID()) != null;
        SquireLanceItem.clearPositions(player.getUUID());

        // Also stop active area clear if running
        SquireEntity squire = findOwnedSquire(source, player);
        boolean stoppedActive = false;
        if (squire != null) {
            SquireAI ai = squire.getSquireAI();
            if (ai != null && ai.getMining().isAreaClearing()) {
                int remaining = ai.getMining().getQueueSize() + 1;
                ai.getMining().clearTarget();
                ai.getMachine().forceState(SquireAIState.IDLE);
                stoppedActive = true;
                var log = squire.getActivityLog();
                if (log != null) {
                    log.log("CLEAR", "Area clear cancelled, " + remaining + " blocks remaining");
                }
            }
        }

        if (!hadPending && !stoppedActive) {
            source.sendFailure(Component.literal("No pending or active clear to cancel."));
            return 0;
        }

        String msg = stoppedActive ? "Active area clear stopped." : "Clear preview cancelled.";
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /squire xp <amount>
    // ------------------------------------------------------------------

    private static int grantXP(CommandSourceStack source, int amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        SquireEntity squire = findOwnedSquire(source, player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }
        for (int i = 0; i < amount; i++) {
            squire.getProgression().addMineXP();
        }
        source.sendSuccess(() -> Component.literal(
                "Granted " + amount + " XP to squire. Now Lv." +
                squire.getSquireLevel() + " (" + squire.getProgression().getTotalXP() + " XP)"), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // /squire log [count]
    // ------------------------------------------------------------------

    private static int showLog(CommandSourceStack source, int count) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        SquireEntity squire = findOwnedSquire(source, player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }
        SquireActivityLog log = squire.getActivityLog();
        if (log == null || log.size() == 0) {
            source.sendSuccess(() -> Component.literal("No activity logged yet."), false);
            return 0;
        }
        List<String> entries = log.getRecent(count);
        source.sendSuccess(() -> Component.literal("--- Squire Activity Log (last " + entries.size() + ") ---"), false);
        for (String entry : entries) {
            source.sendSuccess(() -> Component.literal(entry), false);
        }
        return entries.size();
    }

    // ------------------------------------------------------------------
    // /squire mode <follow|stay|guard>
    // ------------------------------------------------------------------

    private static int setMode(CommandSourceStack source, String modeName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        SquireEntity squire = findOwnedSquire(source, player);
        if (squire == null) {
            source.sendFailure(Component.literal("You have no active squire."));
            return 0;
        }
        byte mode = switch (modeName.toLowerCase()) {
            case "follow" -> SquireEntity.MODE_FOLLOW;
            case "stay" -> SquireEntity.MODE_STAY;
            case "guard" -> SquireEntity.MODE_GUARD;
            default -> -1;
        };
        if (mode < 0) {
            source.sendFailure(Component.literal("Unknown mode: " + modeName + ". Use follow, stay, or guard."));
            return 0;
        }
        squire.setSquireMode(mode);
        source.sendSuccess(() -> Component.literal("Squire mode set to: " + SquireEntity.modeName(mode)), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Find the first squire owned by the given player (command context).
     */
    private static SquireEntity findOwnedSquire(CommandSourceStack source, ServerPlayer player) {
        for (SquireEntity squire : findAllSquires(source)) {
            if (player.getUUID().equals(squire.getOwnerUUID())) {
                return squire;
            }
        }
        return null;
    }

    /**
     * Find the first squire owned by the given player (no command context needed).
     * Used by lance item and other non-command callers.
     */
    static SquireEntity findOwnedSquireByPlayer(ServerPlayer player) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (SquireEntity squire : level.getEntities(ModEntities.SQUIRE.get(), e -> true)) {
                if (squire.isAlive() && player.getUUID().equals(squire.getOwnerUUID())) {
                    return squire;
                }
            }
        }
        return null;
    }

    /**
     * Finds all loaded SquireEntity instances across every loaded ServerLevel.
     * Uses {@code getEntitiesOfClass} with a large AABB per level, which covers
     * all loaded chunks.
     */
    private static List<SquireEntity> findAllSquires(CommandSourceStack source) {
        List<SquireEntity> result = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            // getEntities with entity type filters all loaded entities of that type
            level.getEntities(ModEntities.SQUIRE.get(), entity -> true).forEach(result::add);
        }
        return result;
    }

    /**
     * Resolve the owner's display name. Falls back to UUID string if the player is offline.
     */
    private static String resolveOwnerName(SquireEntity squire, CommandSourceStack source) {
        if (squire.getOwnerUUID() == null) return "(unowned)";

        Player owner = source.getServer().getPlayerList().getPlayer(squire.getOwnerUUID());
        if (owner != null) {
            return owner.getName().getString();
        }
        return squire.getOwnerUUID().toString().substring(0, 8) + "...";
    }

    // ------------------------------------------------------------------
    // Tick handler — particle outline for pending clear previews
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        pendingClears.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (pendingClears.isEmpty()) return;

        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();

        // Only spawn particles once per second
        if (tick % 20 != 0) return;

        Iterator<Map.Entry<UUID, PendingClear>> it = pendingClears.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingClear> entry = it.next();
            PendingClear pending = entry.getValue();

            // Auto-expire after 30 seconds
            if (tick - pending.createdTick() > PREVIEW_TIMEOUT_TICKS) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    player.sendSystemMessage(Component.literal(
                            "Clear preview expired. Run /squire clear <from> <to> again to re-preview."));
                }
                it.remove();
                continue;
            }

            // Spawn outline particles in the player's level
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (player.level() instanceof ServerLevel level) {
                spawnOutlineParticles(level, pending.from(), pending.to());
            }
        }
    }

    /**
     * Spawn flame particles along the 12 edges of the bounding box defined by two corners.
     * Particles appear at the outer faces of the block region (block coords + 1 on max side).
     */
    private static void spawnOutlineParticles(ServerLevel level, BlockPos from, BlockPos to) {
        double x0 = Math.min(from.getX(), to.getX());
        double x1 = Math.max(from.getX(), to.getX()) + 1;
        double y0 = Math.min(from.getY(), to.getY());
        double y1 = Math.max(from.getY(), to.getY()) + 1;
        double z0 = Math.min(from.getZ(), to.getZ());
        double z1 = Math.max(from.getZ(), to.getZ()) + 1;

        // 4 edges along X
        for (double x = x0; x <= x1; x += 1.0) {
            level.sendParticles(ParticleTypes.FLAME, x, y0, z0, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x, y0, z1, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x, y1, z0, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x, y1, z1, 1, 0, 0, 0, 0);
        }
        // 4 edges along Y
        for (double y = y0; y <= y1; y += 1.0) {
            level.sendParticles(ParticleTypes.FLAME, x0, y, z0, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x0, y, z1, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x1, y, z0, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x1, y, z1, 1, 0, 0, 0, 0);
        }
        // 4 edges along Z
        for (double z = z0; z <= z1; z += 1.0) {
            level.sendParticles(ParticleTypes.FLAME, x0, y0, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x1, y0, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x0, y1, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, x1, y1, z, 1, 0, 0, 0, 0);
        }
    }
}
