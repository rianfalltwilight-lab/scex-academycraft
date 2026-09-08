package com.mohistmc.academy.client.block.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatrixFacingTransformTest {
    @Test void dynamicOrbitOriginAndVerticesMatchActualBakedBlockRotationForEveryFacing() throws Exception {
        var method=MatrixRender.class.getDeclaredMethod("facingDegrees",Direction.class);
        method.setAccessible(true);
        Direction[] faces={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        for(int i=0;i<faces.length;i++) {
            float degrees=(float)method.invoke(null,faces[i]);
            PoseStack pose=new PoseStack();
            pose.rotateAround(Axis.YP.rotationDegrees(degrees),.5f,.5f,.5f);
            var baked=BlockModelRotation.by(0,i*90).getRotation().blockCenterToCorner();
            for(float[] xyz:new float[][]{{0,0,0},{.7382f,.2455f,-.9843f},{1,1,1},{-.7f,1.2f,.9f}}) {
                Vector4f expected=new Vector4f(xyz[0],xyz[1],xyz[2],1);
                baked.transformPosition(expected);
                Vector4f actual=new Vector4f(xyz[0],xyz[1],xyz[2],1).mul(pose.last().pose());
                assertEquals(expected.x,actual.x,1e-5,faces[i]+" x");
                assertEquals(expected.y,actual.y,1e-5,faces[i]+" y");
                assertEquals(expected.z,actual.z,1e-5,faces[i]+" z");
            }
        }
    }
}
