package com.sjviklabs.squire.item;

import com.sjviklabs.squire.command.SquireCommand;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.util.SquireAdvancements;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Squire's Crest — the one item that controls your squire.
 * <ul>
 *   <li><b>Right-click block:</b> summon a new squire (consumes item)</li>
 *   <li><b>Right-click air:</b> recall existing squire to you</li>
 *   <li><b>Sneak + right-click block:</b> set pos1 for area selection</li>
 *   <li><b>Sneak + right-click air:</b> trigger clear preview (pos1 + pos2)</li>
 * </ul>
 * Enforces max squire limit per player. Null-safe for fake players/dispensers.
 */
public class SquireCrestItem extends Item {
    // ---- Area selection state (migrated from SquireLanceItem) ----
    private static final Map<UUID, BlockPos> pos1Map = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2Map = new HashMap<>();

    public SquireCrestItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.squire.squire_badge.tooltip1")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.squire.squire_badge.tooltip2")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("item.squire.squire_badge.tooltip3")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        // Sneak + right-click block = set pos1 for area selection
        if (player.isShiftKeyDown()) {
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

        // Normal right-click block = summon squire
        ServerLevel serverLevel = (ServerLevel) level;
        int activeCount = countPlayerSquires(serverLevel, serverPlayer);
        int maxSquires = SquireConfig.maxSquiresPerPlayer.get();
        if (activeCount >= maxSquires) {
            serverPlayer.displayClientMessage(
                    Component.translatable("squire.crest.limit", maxSquires), true);
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        SquireEntity squire = ModEntities.SQUIRE.get().create(serverLevel);
        if (squire == null) return InteractionResult.FAIL;

        squire.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, serverPlayer.getYRot(), 0);
        squire.tame(serverPlayer);
        squire.setCustomName(Component.literal("Squire"));
        serverLevel.addFreshEntity(squire);

        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                10, 0.5, 0.5, 0.5, 0.02);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.2F);

        context.getItemInHand().shrink(1);

        // Grant advancement
        SquireAdvancements.grantSummon(serverPlayer);

        return InteractionResult.CONSUME;
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        // Sneak + right-click air = trigger clear preview
        if (player.isShiftKeyDown()) {
            return triggerPreview(serverPlayer, stack);
        }

        // Normal right-click air = recall squire
        ServerLevel serverLevel = serverPlayer.serverLevel();
        SquireEntity squire = findPlayerSquire(serverLevel, serverPlayer);

        if (squire == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("squire.crest.no_squire"), true);
            return InteractionResultHolder.fail(stack);
        }

        if (squire.getSquireMode() == SquireEntity.MODE_STAY) {
            squire.setSquireMode(SquireEntity.MODE_FOLLOW);
        }
        squire.getNavigation().moveTo(serverPlayer, 1.3D);
        squire.setSprinting(true);

        serverPlayer.displayClientMessage(
                Component.translatable("squire.crest.recall"), true);
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.8F, 1.4F);

        return InteractionResultHolder.success(stack);
    }

    // ---- Area selection preview (migrated from SquireLanceItem) ----

    private InteractionResultHolder<ItemStack> triggerPreview(ServerPlayer player, ItemStack stack) {
        UUID uuid = player.getUUID();
        BlockPos p1 = pos1Map.get(uuid);
        BlockPos p2 = pos2Map.get(uuid);

        if (p1 == null || p2 == null) {
            String missing = p1 == null && p2 == null ? "pos1 and pos2"
                    : p1 == null ? "pos1 (sneak+right-click a block)" : "pos2 (sneak+left-click a block)";
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
    // ---- Static accessors for events and command cleanup ----

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

    // ---- Squire lookup helpers ----

    private int countPlayerSquires(ServerLevel level, ServerPlayer player) {
        int count = 0;
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            for (SquireEntity squire : serverLevel.getEntities(ModEntities.SQUIRE.get(), e -> true)) {
                if (squire.isAlive() && squire.isOwnedBy(player)) {
                    count++;
                }
            }
        }
        return count;
    }
    private SquireEntity findPlayerSquire(ServerLevel level, ServerPlayer player) {
        for (SquireEntity squire : level.getEntities(ModEntities.SQUIRE.get(), e -> true)) {
            if (squire.isAlive() && squire.isOwnedBy(player)) {
                return squire;
            }
        }
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            if (serverLevel == level) continue;
            for (SquireEntity squire : serverLevel.getEntities(ModEntities.SQUIRE.get(), e -> true)) {
                if (squire.isAlive() && squire.isOwnedBy(player)) {
                    return squire;
                }
            }
        }
        return null;
    }
}