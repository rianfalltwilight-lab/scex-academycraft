package com.mohistmc.academy.client.block.entity.render;

import com.mohistmc.academy.world.block.WindGenFan;
import com.mohistmc.academy.world.block.entity.WindGenFanBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

public class WindGenFanRender implements BlockEntityRenderer<WindGenFanBlockEntity> {

    /**
     * The converted 1.21 OBJ moved every legacy fan vertex by (+0.5,+0.5,+0.3).
     * The 1.0.7 renderer then placed the unconverted model at z=0.82 from the
     * main tile.  This renderer lives on the forward structural proxy at z=1,
     * so moving the converted model 0.48 back towards the main block restores
     * the old hub position exactly: 1 + 0.30 - 0.48 = 0.82.
     */
    static final float LEGACY_PROXY_BACK_OFFSET = 0.48f;
    static final float CONVERTED_MODEL_PIVOT_OFFSET = 0.20f;

    public WindGenFanRender(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(WindGenFanBlockEntity blockEntity) {
        // The legacy fan spans a 13x13 plane around its one-block proxy.
        return true;
    }

    @Override
    public void render(WindGenFanBlockEntity p_112307_, float p_112308_, PoseStack p_112309_, MultiBufferSource p_112310_, int p_112311_, int p_112312_) {
        if (p_112307_.getLevel() == null) return;
        var mainPos = WindGenFan.findMain(p_112307_.getLevel(), p_112307_.getBlockPos());
        if (mainPos == null
                || !(p_112307_.getLevel().getBlockEntity(mainPos) instanceof WindGenMainBlockEntity main)
                || !main.shouldRenderFanAt(p_112307_.getBlockPos())) return;
        var blockState = p_112307_.getBlockState();
        Direction facing = blockState.getValue(WindGenFan.FACING);
        float angle = (float) (((p_112307_.getLevel().getGameTime() + p_112308_) * 3.0) % 360.0);

        p_112309_.pushPose();

        // The dynamic renderer is attached to the forward proxy rather than
        // the legacy main tile.  Bring the complete 13x13 mesh back to the
        // original z=0.82 position before applying its spin.
        p_112309_.translate(
                -facing.getStepX() * LEGACY_PROXY_BACK_OFFSET,
                0.0,
                -facing.getStepZ() * LEGACY_PROXY_BACK_OFFSET);

        float pivotX = 0.5f - facing.getStepX() * CONVERTED_MODEL_PIVOT_OFFSET;
        float pivotZ = 0.5f - facing.getStepZ() * CONVERTED_MODEL_PIVOT_OFFSET;

        // RenderWindGenMain in 1.0.7 always spun around local -Z.  In world
        // space that is the axis pointing from the visible fan back towards
        // its main block.  Opposite facings therefore require opposite axis
        // signs; using one sign per plane reverses WEST and SOUTH.
        Axis spinAxis = switch (facing) {
            case EAST -> Axis.XN;
            case WEST -> Axis.XP;
            case SOUTH -> Axis.ZN;
            case NORTH -> Axis.ZP;
            default -> Axis.ZP;
        };
        p_112309_.rotateAround(spinAxis.rotationDegrees(angle), pivotX, 0.5f, pivotZ);

        // 手动渲染BakedModel，避免与默认渲染重叠
        BakedModel bakedModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);
        RandomSource random = RandomSource.create();
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            quads.addAll(bakedModel.getQuads(blockState, direction, random));
        }
        quads.addAll(bakedModel.getQuads(blockState, null, random));

        var consumer = p_112310_.getBuffer(RenderType.cutout());
        for (BakedQuad quad : quads) {
            consumer.putBulkData(p_112309_.last(), quad, 1.0f, 1.0f, 1.0f, 1.0f, p_112311_, p_112312_);
        }

        p_112309_.popPose();

    }
}
