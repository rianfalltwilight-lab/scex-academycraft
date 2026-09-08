package com.mohistmc.academy.skill.ability.electromaster;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 0.0.20 integer output baseline, scaled once by 5/2 in receiver units.
 * A session retains at most half a native unit, never rejected whole energy.
 * Release/abort or a change of receiver units discards this sub-unit remainder.
 */
public final class ChargingOutput {
    private int halfUnit;
    private int unitsPerIf = 1;

    public int preview(float proficiency, int units) {
        if (units != 1 && units != 4) throw new IllegalArgumentException("IF or FE only");
        float bounded = Float.isFinite(proficiency) ? Math.clamp(proficiency, 0f, 1f) : 0f;
        int baseline = (int) lerpf(15, 35, bounded);
        return (baseline * units * 5 + (unitsPerIf == units ? halfUnit : 0)) / 2;
    }

    public void commit(float proficiency, int units) {
        int request = preview(proficiency, units);
        float bounded = Float.isFinite(proficiency) ? Math.clamp(proficiency, 0f, 1f) : 0f;
        int baseline = (int) lerpf(15, 35, bounded);
        halfUnit = baseline * units * 5 + (unitsPerIf == units ? halfUnit : 0) - request * 2;
        unitsPerIf = units;
    }
}
