package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders the squire as a player-like humanoid with full armor and held-item support.
 * Uses PlayerModel for walk cycle, arm swing, swimming animations out of the box.
 */
public class SquireRenderer extends HumanoidMobRenderer<SquireEntity, PlayerModel<SquireEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/entity/squire.png");
    private static final ResourceLocation TEXTURE_SLIM =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/entity/squire_slim.png");

    private final PlayerModel<SquireEntity> wideModel;
    private final PlayerModel<SquireEntity> slimModel;

    public SquireRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        // Armor layers — inner (leggings) and outer (helmet, chestplate, boots)
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));

        // Held items (main hand weapon, offhand shield)
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));

        // Backpack — tier-scaled box on the back
        this.addLayer(new BackpackLayer(this));
    }

    @Override
    public void render(SquireEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Swap model based on appearance setting
        this.model = entity.isSlimModel() ? slimModel : wideModel;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SquireEntity entity) {
        return entity.isSlimModel() ? TEXTURE_SLIM : TEXTURE;
    }

    // Always show name + health bar for tamed squires (vanilla only shows on crosshair hover)
    @Override
    protected boolean shouldShowName(SquireEntity entity) {
        return entity.isTame();
    }

    @Override
    protected void renderNameTag(SquireEntity entity, Component displayName, PoseStack poseStack,
                                  MultiBufferSource bufferSource, int packedLight, float partialTick) {
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
        renderHealthBar(entity, poseStack, bufferSource, packedLight, partialTick);
    }

    private void renderHealthBar(SquireEntity entity, PoseStack poseStack,
                                  MultiBufferSource bufferSource, int packedLight, float partialTick) {
        Vec3 namePos = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
        if (namePos == null) return;

        float healthPct = entity.getHealth() / entity.getMaxHealth();

        poseStack.pushPose();
        poseStack.translate(namePos.x, namePos.y + 0.5, namePos.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        // Position the bar just below the name text (Y+ = down in screen space due to -0.025 Y scale)
        poseStack.translate(0.0, 12.0, 0.0);

        Matrix4f matrix = poseStack.last().pose();
        float barHalfWidth = 20.0F;
        float barHeight = 3.0F;
        float filledWidth = barHalfWidth * 2.0F * healthPct;

        VertexConsumer consumer = bufferSource.getBuffer(SquireRenderTypes.HEALTH_BAR);

        // Background (dark gray)
        drawQuad(consumer, matrix, -barHalfWidth, 0, barHalfWidth, barHeight, 0.2F, 0.2F, 0.2F, 0.6F);

        // Health fill (green > yellow > red based on %)
        float r, g;
        if (healthPct > 0.5F) {
            r = 1.0F - (healthPct - 0.5F) * 2.0F;
            g = 1.0F;
        } else {
            r = 1.0F;
            g = healthPct * 2.0F;
        }
        drawQuad(consumer, matrix, -barHalfWidth, 0, -barHalfWidth + filledWidth, barHeight, r, g, 0.0F, 0.8F);

        // Level text below health bar
        int level = entity.getSquireLevel();
        if (level > 0) {
            Component levelText = Component.literal("Lv. " + level);
            Font font = this.getFont();
            float textX = -font.width(levelText) / 2.0F;
            font.drawInBatch(levelText, textX, barHeight + 2.0F, 0xFFFFFFFF,
                    false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
        }

        poseStack.popPose();
    }

    private static void drawQuad(VertexConsumer consumer, Matrix4f matrix,
                                  float x1, float y1, float x2, float y2,
                                  float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a);
        consumer.addVertex(matrix, x1, y2, 0.0F).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y1, 0.0F).setColor(r, g, b, a);
    }
}
