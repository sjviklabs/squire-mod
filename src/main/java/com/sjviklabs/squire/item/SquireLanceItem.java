package com.sjviklabs.squire.item;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.command.SquireCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Squire's Lance — dual-purpose item.
 * <ul>
 *   <li><b>Sneaking + right-click block:</b> set pos1 (area selection)</li>
 *   <li><b>Sneaking + left-click block:</b> set pos2 (area selection)</li>
 *   <li><b>Sneaking + right-click air:</b> trigger clear preview</li>
 *   <li><b>Normal use:</b> melee weapon with extended 4.5 block reach</li>
 * </ul>
 * When mounted on a horse at sprint speed, deals bonus charge damage.
 */
public class SquireLanceItem extends Item {

    private static final Map<UUID, BlockPos> pos1Map = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2Map = new HashMap<>();

    /** Creates the weapon attribute modifiers for the lance. */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 5.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.2,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "lance_reach"),
                                1.5, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public SquireLanceItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.squire.squire_lance.tooltip1").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.squire.squire_lance.tooltip2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("item.squire.squire_lance.tooltip3").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        // Only do area selection when sneaking
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;

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

        // Only do area selection when sneaking
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);

        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        return triggerPreview(serverPlayer, stack);
    }

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

    /**
     * Calculate bonus charge damage when wielder is mounted on a galloping horse.
     * Returns 0 if not mounted or not moving fast enough.
     */
    public static float getLanceChargeBonus(LivingEntity wielder) {
        if (!(wielder.getVehicle() instanceof AbstractHorse horse)) return 0f;
        double speed = horse.getDeltaMovement().horizontalDistance();
        if (speed < 0.1) return 0f;
        // ~2-6 bonus damage at gallop speeds
        return (float) (speed * 10.0);
    }

    /**
     * Check if the wielder is mounted (for reach extension in CombatHandler).
     */
    public static boolean isWielderMounted(LivingEntity wielder) {
        return wielder.getVehicle() instanceof AbstractHorse;
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
