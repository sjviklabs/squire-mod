package com.sjviklabs.squire.item;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Squire Badge item. Right-click ground to summon a new squire.
 * Right-click air to recall an existing squire (sprint-pathfind to owner).
 * Enforces max squire limit per player. Null-safe for fake players/dispensers.
 */
public class SquireBadgeItem extends Item {

    public SquireBadgeItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // Right-click on block: summon squire
    // ------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (level.isClientSide) return InteractionResult.sidedSuccess(true);

        // Null safety — dispensers and fake players don't get squires
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        ServerLevel serverLevel = (ServerLevel) level;

        // Check squire limit
        int activeCount = countPlayerSquires(serverLevel, serverPlayer);
        int maxSquires = SquireConfig.maxSquiresPerPlayer.get();
        if (activeCount >= maxSquires) {
            serverPlayer.displayClientMessage(
                    Component.translatable("squire.badge.limit", maxSquires), true);
            return InteractionResult.FAIL;
        }

        // Spawn at clicked position
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        SquireEntity squire = ModEntities.SQUIRE.get().create(serverLevel);
        if (squire == null) return InteractionResult.FAIL;

        squire.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, serverPlayer.getYRot(), 0);
        squire.tame(serverPlayer);
        squire.setCustomName(Component.literal("Squire"));
        serverLevel.addFreshEntity(squire);

        // Summon effects
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                10, 0.5, 0.5, 0.5, 0.02);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.2F);

        // Consume the badge
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------
    // Right-click in air: recall existing squire
    // ------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        ServerLevel serverLevel = serverPlayer.serverLevel();
        SquireEntity squire = findPlayerSquire(serverLevel, serverPlayer);

        if (squire == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("squire.badge.no_squire"), true);
            return InteractionResultHolder.fail(stack);
        }

        // If squire is in STAY mode, switch to FOLLOW first
        if (squire.getSquireMode() == SquireEntity.MODE_STAY) {
            squire.setSquireMode(SquireEntity.MODE_FOLLOW);
        }

        // Force aggressive pathfinding toward owner
        squire.getNavigation().moveTo(serverPlayer, 1.3D);
        squire.setSprinting(true);

        serverPlayer.displayClientMessage(
                Component.translatable("squire.badge.recall"), true);
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.8F, 1.4F);

        return InteractionResultHolder.success(stack);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Count how many living squires this player owns across all dimensions.
     */
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

    /**
     * Find the first living squire owned by this player (same dimension first).
     */
    private SquireEntity findPlayerSquire(ServerLevel level, ServerPlayer player) {
        // Check current dimension first
        for (SquireEntity squire : level.getEntities(ModEntities.SQUIRE.get(), e -> true)) {
            if (squire.isAlive() && squire.isOwnedBy(player)) {
                return squire;
            }
        }
        // Check other dimensions
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
