package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireMenu;
import com.sjviklabs.squire.item.SquireArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * Custom armor render layer that selects tier-appropriate textures for squire armor
 * based on the squire's level. Non-squire armor falls back to vanilla texture lookup.
 *
 * Replaces vanilla HumanoidArmorLayer in SquireRenderer.
 *
 * Tier mapping (from SquireMenu.tierFromLevel):
 *   Tier 0 (Lv 1-9):  Recruit  — light armor (squire_layer_*_t0.png)
 *   Tier 1 (Lv 10-19): Veteran  — medium armor (squire_layer_*_t1.png)
 *   Tier 2 (Lv 20-29): Champion — heavy armor  (squire_layer_*_t2.png)
 *   Tier 3 (Lv 30):    Legend   — royal armor  (squire_layer_*_t3.png)
 */
public class SquireTieredArmorLayer extends RenderLayer<SquireEntity, PlayerModel<SquireEntity>> {

    private final HumanoidModel<SquireEntity> innerModel;  // leggings
    private final HumanoidModel<SquireEntity> outerModel;  // helmet, chestplate, boots

    /** Outer armor textures (layer_1) per tier: helmet, chestplate, boots. */
    private static final ResourceLocation[] TIER_OUTER = {
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t0.png"),
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t1.png"),
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t2.png"),
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t3.png"),
    };

    /** Inner armor textures (layer_2) per tier: leggings. */
    private static final ResourceLocation[] TIER_INNER = {
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t0.png"),
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t1.png"),
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t2.png"),
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t3.png"),
    };

    /** Default (non-tiered) textures — fallback when visual progression is disabled. */
    private static final ResourceLocation DEFAULT_OUTER =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1.png");
    private static final ResourceLocation DEFAULT_INNER =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2.png");

    public SquireTieredArmorLayer(
            RenderLayerParent<SquireEntity, PlayerModel<SquireEntity>> renderer,
            HumanoidModel<SquireEntity> innerModel,
            HumanoidModel<SquireEntity> outerModel) {
        super(renderer);
        this.innerModel = innerModel;
        this.outerModel = outerModel;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       SquireEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        renderSlot(poseStack, bufferSource, packedLight, entity, EquipmentSlot.HEAD);
        renderSlot(poseStack, bufferSource, packedLight, entity, EquipmentSlot.CHEST);
        renderSlot(poseStack, bufferSource, packedLight, entity, EquipmentSlot.LEGS);
        renderSlot(poseStack, bufferSource, packedLight, entity, EquipmentSlot.FEET);
    }

    private void renderSlot(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                            SquireEntity entity, EquipmentSlot slot) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) return;

        boolean isInner = (slot == EquipmentSlot.LEGS);
        HumanoidModel<SquireEntity> armorModel = isInner ? innerModel : outerModel;

        // Copy pose from parent model to armor model
        getParentModel().copyPropertiesTo(armorModel);
        setPartVisibility(armorModel, slot);

        // Resolve texture
        ResourceLocation texture = resolveTexture(entity, stack, isInner);

        // Render armor piece
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.armorCutoutNoCull(texture));
        armorModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);

        // Enchantment glint overlay
        if (stack.hasFoil()) {
            armorModel.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.armorEntityGlint()),
                    packedLight, OverlayTexture.NO_OVERLAY);
        }
    }

    /**
     * Resolves the armor texture for a given item.
     * - SquireArmorItem: tier-based texture (or default if progression disabled)
     * - Other armor: vanilla material texture lookup
     */
    private ResourceLocation resolveTexture(SquireEntity entity, ItemStack stack, boolean isInner) {
        if (stack.getItem() instanceof SquireArmorItem) {
            if (SquireConfig.enableVisualProgression.get()) {
                int tier = SquireMenu.tierFromLevel(entity.getSquireLevel());
                return isInner ? TIER_INNER[tier] : TIER_OUTER[tier];
            }
            return isInner ? DEFAULT_INNER : DEFAULT_OUTER;
        }

        // Non-squire armor: look up from ArmorMaterial layers
        if (stack.getItem() instanceof ArmorItem armorItem) {
            ArmorMaterial material = armorItem.getMaterial().value();
            if (!material.layers().isEmpty()) {
                ArmorMaterial.Layer layer = material.layers().getFirst();
                // ArmorMaterial.Layer.texture(boolean innerModel) returns the full RL
                return layer.texture(isInner);
            }
        }

        // Absolute fallback
        return isInner ? DEFAULT_INNER : DEFAULT_OUTER;
    }

    /** Sets which model parts are visible for a given equipment slot. */
    private static void setPartVisibility(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> { /* No-op for non-armor slots */ }
        }
    }
}