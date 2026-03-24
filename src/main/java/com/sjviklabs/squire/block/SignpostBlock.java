package com.sjviklabs.squire.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Patrol signpost block. Places facing the player, right-click to configure.
 * Links to other signposts to form patrol routes.
 */
public class SignpostBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<SignpostBlock> CODEC = simpleCodec(SignpostBlock::new);

    public SignpostBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignpostBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SignpostBlockEntity signpost) {
            // Show current config via chat (simple approach — GUI can come later)
            String mode = signpost.getMode().name();
            BlockPos linked = signpost.getLinkedSignpost();
            String linkStr = linked != null ? linked.toShortString() : "none";
            int wait = signpost.getWaitTicks();

            player.sendSystemMessage(Component.literal(
                    "Signpost — Mode: " + mode + " | Linked: " + linkStr + " | Wait: " + (wait / 20) + "s"));

            // Shift+right-click: cycle mode
            if (player.isShiftKeyDown()) {
                SignpostBlockEntity.PatrolMode[] modes = SignpostBlockEntity.PatrolMode.values();
                int nextIdx = (signpost.getMode().ordinal() + 1) % modes.length;
                signpost.setMode(modes[nextIdx]);
                signpost.setChanged();
                player.sendSystemMessage(Component.literal("Mode set to: " + modes[nextIdx].name()));
            }
        }
        return InteractionResult.CONSUME;
    }
}
