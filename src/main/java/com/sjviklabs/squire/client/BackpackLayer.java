package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.inventory.SquireMenu;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import org.joml.Matrix4f;

/**
 * Renders a backpack on the squire's back that scales with backpack tier.
 * Tier 0 (Satchel): small belt pouch. Tier 3 (War Chest): large pack with bedroll.
 *
 * Uses a simple textured box attached to the body part's transform.
 */
public class BackpackLayer extends RenderLayer<SquireEntity, PlayerModel<SquireEntity>> {

    private static final ResourceLocation BACKPACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/entity/backpack.png");

    // Box dimensions per tier [width, height, depth] in model units (1/16 of a block)
    private static final float[][] TIER_SIZES = {
            {4, 3, 2},   // Tier 0: Satchel — small belt pouch
            {6, 5, 3},   // Tier 1: Pack — medium bag
            {7, 7, 4},   // Tier 2: Knapsack — full backpack
            {8, 9, 5},   // Tier 3: War Chest — large chest-pack
    };

    // Vertical offset per tier (how far down from body center)
    private static final float[] TIER_Y_OFFSET = {2, 0, -1, -2};

    // Color tint per tier (leather → iron → gold → diamond feel)
    private static final int[] TIER_COLORS = {
            0xFF8B6F47, // Tier 0: leather brown
            0xFF6B6B6B, // Tier 1: iron grey
            0xFF4A7A4A, // Tier 2: green knapsack
            0xFF4A6A8A, // Tier 3: steel blue
    };

    public BackpackLayer(RenderLayerParent<SquireEntity, PlayerModel<SquireEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       SquireEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;

        int tier = SquireMenu.tierFromLevel(entity.getSquireLevel());
        float[] size = TIER_SIZES[tier];
        int color = TIER_COLORS[tier];

        poseStack.pushPose();

        // Translate to body part and rotate with it
        this.getParentModel().body.translateAndRotate(poseStack);

        // Position on back (positive Z = behind the player model)
        // Body pivot is at center-top of body, Y goes down
        float yOffset = TIER_Y_OFFSET[tier];
        poseStack.translate(0.0F, yOffset / 16.0F, 0.175F); // slightly behind the body surface

        // Scale from model units to world units
        float w = size[0] / 16.0F;
        float h = size[1] / 16.0F;
        float d = size[2] / 16.0F;

        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(BACKPACK_TEXTURE));
        Pose pose = poseStack.last();

        // Draw a simple box (6 faces)
        drawBox(consumer, pose, -w/2, 0, 0, w/2, h, d, r, g, b, packedLight);

        poseStack.popPose();
    }

    private static void drawBox(VertexConsumer consumer, Pose pose,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float r, float g, float b, int light) {
        int overlay = OverlayTexture.NO_OVERLAY;
        Matrix4f mat = pose.pose();

        // Front face (Z+)
        quad(consumer, mat, pose, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, 0,0,1, r,g,b, light, overlay);
        // Back face (Z-)
        quad(consumer, mat, pose, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, 0,0,-1, r*0.7f,g*0.7f,b*0.7f, light, overlay);
        // Top face (Y+)
        quad(consumer, mat, pose, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, 0,1,0, r*0.9f,g*0.9f,b*0.9f, light, overlay);
        // Bottom face (Y-)
        quad(consumer, mat, pose, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1, 0,-1,0, r*0.6f,g*0.6f,b*0.6f, light, overlay);
        // Right face (X+)
        quad(consumer, mat, pose, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, 1,0,0, r*0.8f,g*0.8f,b*0.8f, light, overlay);
        // Left face (X-)
        quad(consumer, mat, pose, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, -1,0,0, r*0.8f,g*0.8f,b*0.8f, light, overlay);
    }

    private static void quad(VertexConsumer consumer, Matrix4f mat, Pose pose,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float nx, float ny, float nz,
                              float r, float g, float b,
                              int light, int overlay) {
        vertex(consumer, mat, pose, x0, y0, z0, 0, 0, nx, ny, nz, r, g, b, light, overlay);
        vertex(consumer, mat, pose, x1, y1, z1, 1, 0, nx, ny, nz, r, g, b, light, overlay);
        vertex(consumer, mat, pose, x2, y2, z2, 1, 1, nx, ny, nz, r, g, b, light, overlay);
        vertex(consumer, mat, pose, x3, y3, z3, 0, 1, nx, ny, nz, r, g, b, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f mat, Pose pose,
                                float x, float y, float z, float u, float v,
                                float nx, float ny, float nz,
                                float r, float g, float b, int light, int overlay) {
        consumer.addVertex(mat, x, y, z)
                .setColor(r, g, b, 1.0F)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
