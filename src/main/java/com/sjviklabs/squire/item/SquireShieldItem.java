package com.sjviklabs.squire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Custom shield with heraldry texture. Functionally identical to vanilla shield.
 * Subclassed for registration identity and future set bonus detection.
 */
public class SquireShieldItem extends ShieldItem {

    public SquireShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.squire.squire_shield.tooltip1").withStyle(ChatFormatting.GRAY));
    }
}
