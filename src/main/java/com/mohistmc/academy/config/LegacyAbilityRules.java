package com.mohistmc.academy.config;

import java.util.List;

/** Pure, reload-safe interpretation of the 1.0.7 ability tuning matrix. */
public final class LegacyAbilityRules {
    private LegacyAbilityRules() {}
    public static final Snapshot DEFAULTS = new Snapshot(15, 1, 32, 1, .0025f, .0058f, 1);
    public record Snapshot(int cpDelay, float cpSpeed, int overloadDelay, float overloadSpeed,
                           float maxCpGrowth, float maxOverloadGrowth, float proficiencyGrowth) {}
    public record SkillTuning(boolean enabled, float damage, float cp, float overload, float exp,
                              boolean destroyBlocks) {}

    public static SkillTuning parseSkill(String skillId, List<? extends String> entries) {
        boolean enabled=true, destroy=true; float damage=1, cp=1, overload=1, exp=1;
        String prefix=skillId+".";
        for (String raw : entries) {
            if (raw == null || !raw.startsWith(prefix)) continue;
            int eq=raw.indexOf('=',prefix.length()); if(eq<0) continue;
            String key=raw.substring(prefix.length(),eq).strip(), value=raw.substring(eq+1).strip();
            try {
                switch(key) {
                    case "enabled" -> enabled=Boolean.parseBoolean(value);
                    case "destroy_blocks" -> destroy=Boolean.parseBoolean(value);
                    case "damage" -> damage=positive(value,damage);
                    case "cp" -> cp=positive(value,cp);
                    case "overload" -> overload=positive(value,overload);
                    case "exp" -> exp=positive(value,exp);
                }
            } catch (RuntimeException ignored) { }
        }
        return new SkillTuning(enabled,damage,cp,overload,exp,destroy);
    }
    private static float positive(String text,float fallback){float v=Float.parseFloat(text);return Float.isFinite(v)&&v>=0?v:fallback;}
    public static float cpRecovery(float current, float max, float personalRate, Snapshot rules) {
        if (!Float.isFinite(current) || !Float.isFinite(max) || max <= 0 || !Float.isFinite(personalRate)) return 0;
        return rules.cpSpeed() * .0003f * max * personalRate * (1f + Math.clamp(current / max, 0, 1));
    }
    public static float overloadRecovery(float current, float max, Snapshot rules) {
        if (!Float.isFinite(current) || !Float.isFinite(max) || max <= 0) return 0;
        return rules.overloadSpeed() * Math.max(.002f * max,
                .007f * max * (1f - .25f * Math.clamp(current / max, 0, 1)));
    }
}
