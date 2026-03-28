package com.sjviklabs.squire.block;

import com.mojang.serialization.MapCodec;
import com.sjviklabs.squire.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Patrol signpost block. Places facing the player, right-click to configure.
 * Links to other signposts to form patrol routes.
 */
public class SignpostBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<SignpostBlock> CODEC = simpleCodec(SignpostBlock::new);

    /** Tracks which signpost each player last clicked with the crest (for linking). */
    private static final Map<UUID, BlockPos> PENDING_LINKS = new HashMap<>();

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
            // Shift+right-click: cycle mode
            if (player.isShiftKeyDown()) {
                SignpostBlockEntity.PatrolMode[] modes = SignpostBlockEntity.PatrolMode.values();
                int nextIdx = (signpost.getMode().ordinal() + 1) % modes.length;
                signpost.setMode(modes[nextIdx]);
                signpost.setChanged();
                player.sendSystemMessage(Component.literal("Signpost mode: " + modes[nextIdx].name()));
                return InteractionResult.CONSUME;
            }

            // Right-click with crest: link signposts
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand.is(ModItems.SQUIRE_CREST.get())) {
                return handleCrestLink(player, pos, signpost);
            }

            // Plain right-click: show info
            String mode = signpost.getMode().name();
            BlockPos linked = signpost.getLinkedSignpost();
            String linkStr = linked != null ? linked.toShortString() : "none";
            int wait = signpost.getWaitTicks();
            player.sendSystemMessage(Component.literal(
                    "Signpost — Mode: " + mode + " | Next: " + linkStr + " | Wait: " + (wait / 20) + "s"));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Crest-based linking: click first signpost, then click second signpost to link them.
     * First signpost's "next" is set to the second signpost's position.
     */
    private InteractionResult handleCrestLink(Player player, BlockPos pos, SignpostBlockEntity signpost) {
        UUID playerId = player.getUUID();
        BlockPos pending = PENDING_LINKS.get(playerId);

        if (pending == null || pending.equals(pos)) {
            // First click (or clicked same post again) — mark as pending
            PENDING_LINKS.put(playerId, pos);
            player.sendSystemMessage(Component.literal(
                    "Signpost selected at " + pos.toShortString() + ". Right-click another signpost with crest to link."));
            return InteractionResult.CONSUME;
        }

        // Second click — link the pending signpost to this one
        Level level = player.level();
        BlockEntity fromBE = level.getBlockEntity(pending);
        if (fromBE instanceof SignpostBlockEntity fromSignpost) {
            fromSignpost.setLinkedSignpost(pos);
            fromSignpost.setChanged();
            player.sendSystemMessage(Component.literal(
                    "Linked: " + pending.toShortString() + " → " + pos.toShortString()));
        } else {
            player.sendSystemMessage(Component.literal("Previous signpost no longer exists."));
        }

        PENDING_LINKS.remove(playerId);
        return InteractionResult.CONSUME;
    }
}
