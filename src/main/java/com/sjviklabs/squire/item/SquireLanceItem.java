package com.sjviklabs.squire.item;

import com.sjviklabs.squire.command.SquireCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Squire's Lance — area selection tool for clear commands.
 * <ul>
 *   <li>Right-click block: set pos1</li>
 *   <li>Left-click block: set pos2 (handled in {@link SquireLanceEvents})</li>
 *   <li>Shift+right-click air: trigger clear preview</li>
 *   <li>Right-click air (no shift): show current pos1/pos2 status</li>
 * </ul>
 */
public class SquireLanceItem extends Item {

    private static final Map<UUID, BlockPos> pos1Map = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2Map = new HashMap<>();

    public SquireLanceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();
        pos1Map.put(serverPlayer.getUUID(), pos);

        serverPlayer.displayClientMessage(
                Component.literal("Pos1 set: (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"), true);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.02);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        if (serverPlayer.isShiftKeyDown()) {
            return triggerPreview(serverPlayer, stack);
        } else {
            return showStatus(serverPlayer, stack);
        }
    }

    private InteractionResultHolder<ItemStack> triggerPreview(ServerPlayer player, ItemStack stack) {
        UUID uuid = player.getUUID();
        BlockPos p1 = pos1Map.get(uuid);
        BlockPos p2 = pos2Map.get(uuid);

        if (p1 == null || p2 == null) {
            String missing = p1 == null && p2 == null ? "pos1 and pos2"
                    : p1 == null ? "pos1 (right-click a block)" : "pos2 (left-click a block)";
            player.displayClientMessage(
                    Component.literal("Set " + missing + " first."), true);
            return InteractionResultHolder.fail(stack);
        }

        int result = SquireCommand.previewClearForPlayer(player, p1, p2);
        if (result > 0) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    private InteractionResultHolder<ItemStack> showStatus(ServerPlayer player, ItemStack stack) {
        UUID uuid = player.getUUID();
        BlockPos p1 = pos1Map.get(uuid);
        BlockPos p2 = pos2Map.get(uuid);

        String status;
        if (p1 == null && p2 == null) {
            status = "No positions set. Right-click block = pos1, left-click block = pos2.";
        } else if (p1 != null && p2 == null) {
            status = "Pos1: (" + p1.getX() + ", " + p1.getY() + ", " + p1.getZ() + ") | Pos2: not set";
        } else if (p1 == null) {
            status = "Pos1: not set | Pos2: (" + p2.getX() + ", " + p2.getY() + ", " + p2.getZ() + ")";
        } else {
            status = "Pos1: (" + p1.getX() + ", " + p1.getY() + ", " + p1.getZ() + ") | Pos2: (" +
                    p2.getX() + ", " + p2.getY() + ", " + p2.getZ() + ") — Shift+right-click air to preview";
        }

        player.displayClientMessage(Component.literal(status), true);
        return InteractionResultHolder.success(stack);
    }

    // -- Static accessors for events and command cleanup --

    public static void setPos2(UUID uuid, BlockPos pos) {
        pos2Map.put(uuid, pos);
    }

    public static BlockPos getPos1(UUID uuid) {
        return pos1Map.get(uuid);
    }

    public static BlockPos getPos2(UUID uuid) {
        return pos2Map.get(uuid);
    }

    public static void clearPositions(UUID uuid) {
        pos1Map.remove(uuid);
        pos2Map.remove(uuid);
    }
}
