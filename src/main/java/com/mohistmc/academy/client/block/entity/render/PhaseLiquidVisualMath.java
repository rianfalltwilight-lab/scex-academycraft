package com.mohistmc.academy.client.block.entity.render;

/** Pure legacy visual math kept separate from client renderer initialization. */
final class PhaseLiquidVisualMath {
    private PhaseLiquidVisualMath() {}

    static float distanceAlpha(double distanceSquared, double scale) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0
                || !Double.isFinite(scale) || scale < 0) {
            return 0.0f;
        }
        return (float) (1.0 / (1.0 + scale * Math.sqrt(distanceSquared)));
    }
}
