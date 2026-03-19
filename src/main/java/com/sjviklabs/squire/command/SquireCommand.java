package com.sjviklabs.squire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sjviklabs.squire.ai.statemachine.SquireAI;
import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.init.ModEntities;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side commands for squire management.
 * <ul>
 *   <li>{@code /squire list}                    — list all active squires (op level 2)</li>
 *   <li>{@code /squire kill <player>}           — kill a player's squire (op level 2)</li>
 *   <li>{@code /squire limit <count>}           — set max squires per player at runtime (op level 3)</li>
 *   <li>{@code /squire info}                    — show your squire's state, level, XP (no op)</li>
 *   <li>{@code /squire mine <pos>}              — order squire to mine block at position (op level 2)</li>
 *   <li>{@code /squire place <pos> <block>}     — order squire to place block at position (op level 2)</li>
 *   <li>{@code /squire xp <amount>}             — grant XP to your squire (op level 2)</li>
 * </ul>
 */
@EventBusSubscriber
public class SquireCommand {

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
                .then(Commands.literal("xp")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                                .executes(ctx -> grantXP(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))
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
        float hp = squire.getHealth();
        float maxHp = squire.getMaxHealth();
        String pos = String.format("(%.0f, %.0f, %.0f)", squire.getX(), squire.getY(), squire.getZ());

        source.sendSuccess(() -> Component.literal(
                "Squire " + pos + " | HP: " + String.format("%.1f/%.1f", hp, maxHp) +
                " | Lv." + level + " (" + xp + " XP) | State: " + state), false);
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
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Find the first squire owned by the given player.
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
}
