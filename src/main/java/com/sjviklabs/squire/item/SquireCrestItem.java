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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Squire's Crest item. Right-click ground to summon a new squire.
 * Right-click air to recall an existing squire (sprint-pathfind to owner).
 * Enforces max squire limit per player. Null-safe for fake players/dispensers.
 */
public class SquireCrestItem extends Item {

    public SquireCrestItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.squire.squire_badge.tooltip1").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.squire.squire_badge.tooltip2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

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
        return InteractionResult.CONSUME;
    }

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
