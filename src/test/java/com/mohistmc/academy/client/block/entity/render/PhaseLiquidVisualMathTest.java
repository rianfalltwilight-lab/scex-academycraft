package com.mohistmc.academy.client.block.entity.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PhaseLiquidVisualMathTest {
    @Test
    void matchesLegacyCameraDistanceFade() {
        assertEquals(1.0f, PhaseLiquidVisualMath.distanceAlpha(0.0, 0.2), 0.00001f);
        assertEquals(0.5f, PhaseLiquidVisualMath.distanceAlpha(25.0, 0.2), 0.00001f);
        assertEquals(1.0f / 3.0f,
                PhaseLiquidVisualMath.distanceAlpha(100.0, 0.2), 0.00001f);
    }

    @Test
    void malformedDistancesFailInvisible() {
        assertEquals(0.0f, PhaseLiquidVisualMath.distanceAlpha(-1.0, 0.2));
        assertEquals(0.0f, PhaseLiquidVisualMath.distanceAlpha(Double.NaN, 0.2));
        assertEquals(0.0f, PhaseLiquidVisualMath.distanceAlpha(1.0, Double.POSITIVE_INFINITY));
    }
}
