package com.mohistmc.academy.client.block.entity.render;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.Matrix;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Restores the dynamic constraint plates from AcademyCraft 1.12.2's
 * {@code RenderMatrix}.  The normal block model deliberately contains only
 * the legacy Main/Core groups; one Shield group is rendered three times here
 * so the plates can orbit and float instead of being baked into the world.
 */
public final class MatrixRender implements BlockEntityRenderer<MatrixBlockEntity> {
    public static final ModelResourceLocation SHIELD_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "block/matrix_shield"));

    private static final int PLATE_COUNT = 3;
    private static final float FLOAT_HEIGHT = 0.1f;
    private static final double HEIGHT_PHASE_OFFSET = 40.0;

    public MatrixRender(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(MatrixBlockEntity blockEntity) {
        // The matrix model spans the complete 2x2x2 proxy structure.
        return true;
    }

    @Override
    public void render(MatrixBlockEntity matrix, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (matrix.getLevel() == null || !hasThreeConstraintPlates(matrix)) return;

        var modelManager = Minecraft.getInstance().getModelManager();
        BakedModel shield = modelManager.getModel(SHIELD_MODEL);
        if (shield == modelManager.getMissingModel()) return;

        // GameTimer.getTime() in 1.12.2 was millisecond based.  Deriving the
        // same unit from level time keeps clients deterministic and preserves
        // the original phase=(time/20) and sin(time/900) motion.
        double timeMillis = (matrix.getLevel().getGameTime() + partialTick) * 50.0;
        float orbitPhase = (float) ((timeMillis / 20.0) % 360.0);
        float facing = facingDegrees(matrix.getBlockState().getValue(Matrix.FACING));

        List<BakedQuad> quads = collectQuads(shield);
        var consumer = buffers.getBuffer(RenderType.cutout());
        for (int plate = 0; plate < PLATE_COUNT; plate++) {
            poseStack.pushPose();

            // Match the y rotations from blockstates/matrix.json before the
            // legacy per-plate orbit around the OBJ origin.
            if (facing != 0) {
                poseStack.rotateAround(Axis.YP.rotationDegrees(facing), 0.5f, 0.5f, 0.5f);
            }
            double floatOffset = FLOAT_HEIGHT
                    * Math.sin(timeMillis / 900.0 + HEIGHT_PHASE_OFFSET * plate);
            poseStack.translate(0.0, floatOffset, 0.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(orbitPhase + 120.0f * plate));

            for (BakedQuad quad : quads) {
                consumer.putBulkData(poseStack.last(), quad, 1.0f, 1.0f, 1.0f, 1.0f,
                        packedLight, packedOverlay);
            }
            poseStack.popPose();
        }
    }

    private static boolean hasThreeConstraintPlates(MatrixBlockEntity matrix) {
        var items = matrix.getItems();
        // RenderMatrix in the final 1.12.2 source only exposes the three
        // orbiting plates when all three plates and a valid core are present.
        // This deliberately does not depend on the one-shot initialized flag:
        // the renderer follows the installed materials, as the original did.
        return items.size() >= 4
                && matrix.initializationCoreLevel() >= 0
                && items.get(MatrixBlockEntity.PLATE_SLOT_0).is(AcademyItems.CONSTRAINT_PLATE.get())
                && items.get(MatrixBlockEntity.PLATE_SLOT_1).is(AcademyItems.CONSTRAINT_PLATE.get())
                && items.get(MatrixBlockEntity.PLATE_SLOT_2).is(AcademyItems.CONSTRAINT_PLATE.get());
    }

    private static List<BakedQuad> collectQuads(BakedModel model) {
        RandomSource random = RandomSource.create(0L);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(null, direction, random));
        }
        quads.addAll(model.getQuads(null, null, random));
        return quads;
    }

    private static float facingDegrees(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
    }
}
