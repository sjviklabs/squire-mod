package com.sjviklabs.squire.item;

import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SquireBadgeItem extends Item {

    public SquireBadgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

            SquireEntity squire = ModEntities.SQUIRE.get().create(serverLevel);
            if (squire != null) {
                squire.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                squire.tame(context.getPlayer());
                squire.setCustomName(Component.literal("Squire"));
                serverLevel.addFreshEntity(squire);

                // Consume the badge
                context.getItemInHand().shrink(1);

                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
