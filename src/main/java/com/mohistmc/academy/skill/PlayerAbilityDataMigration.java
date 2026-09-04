package com.mohistmc.academy.skill;

import com.mohistmc.academy.tutorial.TutorialUnlocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Idempotent semantic importer for unversioned AcademyCraft player data.
 * This does not claim that a 1.12.2 world can be opened directly by 1.21.1; callers must
 * first extract the legacy player compounds. Unknown fields remain intact.
 */
public final class PlayerAbilityDataMigration {
    private PlayerAbilityDataMigration() {}

    /** Exact Category.addSkill order at the final official 1.12.2 commit, including the three generic courses appended last. */
    private static final List<List<String>> LEGACY_ORDER=List.of(
        List.of("arc_gen","charging","mag_movement","mag_manip","mine_detect","body_intensify","thunder_bolt","railgun","thunder_clap","brain_course","brain_course_advanced","mind_course"),
        List.of("electron_bomb","rad_intensify","scatter_bomb","light_shield","meltdowner","mine_ray_basic","ray_barrage","jet_engine","mine_ray_expert","mine_ray_luck","electron_missile","brain_course","brain_course_advanced","mind_course"),
        List.of("threatening_teleport","dim_folding_theorem","penetrate_teleport","mark_teleport","flesh_ripping","location_teleport","shift_tp","space_fluct","flashing","brain_course","brain_course_advanced","mind_course"),
        List.of("dir_shock","ground_shock","vec_accel","vec_deviation","dir_blast","storm_wing","blood_retro","vec_reflection","plasma_cannon","brain_course","brain_course_advanced","mind_course"));

    public static CompoundTag migrate(CompoundTag input) {
        CompoundTag out = input.copy();
        int version = out.contains("data_version") ? out.getInt("data_version") : 0;
        if (version < 1) {
            aliasBoolean(out, "activated", "ability_active");
            if (!out.contains("ability") && out.contains("catID")) {
                String id = switch (out.getInt("catID")) {
                    case 0 -> "electromaster"; case 1 -> "meltdowner";
                    case 2 -> "teleporter"; case 3 -> "vecmanip"; default -> "";
                };
                if (!id.isEmpty()) out.putString("ability", id);
            }
            aliasFloat(out, "curCP", "cp");
            aliasFloat(out, "maxCP", "max_cp");
            aliasFloat(out, "curOverload", "overload");
            aliasFloat(out, "maxOverload", "max_overload");
            aliasInt(out, "presetID", "current_preset");
            // Importer-normalized stable forms. Raw 1.12.2 BitSet/float[] values are
            // deliberately not guessed because their indices depend on registry order.
            if (!out.contains("learned") && out.contains("legacy_learned_ids", Tag.TAG_LIST))
                out.put("learned", out.getList("legacy_learned_ids", Tag.TAG_STRING).copy());
            if (!out.contains("proficiency") && out.contains("legacy_skill_exps", Tag.TAG_COMPOUND))
                out.put("proficiency", out.getCompound("legacy_skill_exps").copy());
            importRaw112(out);
            out.putInt("data_version", 1);
        }
        if (version < 2) {
            if (!out.contains("activated_tutorials", Tag.TAG_LIST)) {
                Set<String> activated = new LinkedHashSet<>();
                ListTag obtained = out.getList("obtained", Tag.TAG_STRING);
                for (int i = 0; i < obtained.size(); i++) {
                    String tutorialId = TutorialUnlocks.tutorialForItem(obtained.getString(i));
                    if (tutorialId != null) activated.add(tutorialId);
                }
                ListTag tutorials = new ListTag();
                activated.forEach(id -> tutorials.add(StringTag.valueOf(id)));
                out.put("activated_tutorials", tutorials);
            }
            out.putInt("data_version", 2);
        }
        if (version < 3) {
            // Final 1.12.2 NBTS11n used this exact field name. Rebuilt v1/v2
            // saves did not track the gauge and therefore safely begin at zero.
            aliasFloat(out, "expAddedThisLevel", "level_progress_exp");
            out.putInt("data_version", 3);
        }
        if (version < 4) {
            boolean brain = learned(out, "brain_course");
            boolean advanced = learned(out, "brain_course_advanced");
            if (!out.contains("usage_max_cp")) {
                if (out.contains("addMaxCP")) {
                    out.putFloat("usage_max_cp", LegacyResourceProgression.finiteNonNegative(out.getFloat("addMaxCP")));
                } else if (out.contains("max_cp")) {
                    // Rebuilt v1-v3 stored a fixed 2000 base plus its then-current
                    // (+1000/+1000) course bonuses directly in max_cp.
                    out.putFloat("usage_max_cp", LegacyResourceProgression.importedRebuiltUsageCp(
                            out.getFloat("max_cp"), brain, advanced));
                } else {
                    out.putFloat("usage_max_cp", 0);
                }
            }
            if (!out.contains("usage_max_overload")) {
                if (out.contains("addMaxOverload")) {
                    out.putFloat("usage_max_overload", LegacyResourceProgression.finiteNonNegative(
                            out.getFloat("addMaxOverload")));
                } else if (out.contains("max_overload")) {
                    // Rebuilt v1-v3 used a fixed 500 base and +100 for the
                    // advanced course. The level table is applied after import.
                    out.putFloat("usage_max_overload", LegacyResourceProgression.importedRebuiltUsageOverload(
                            out.getFloat("max_overload"), advanced));
                } else {
                    out.putFloat("usage_max_overload", 0);
                }
            }
            out.putInt("data_version", 4);
        }
        return out;
    }

    private static boolean learned(CompoundTag tag, String skillId) {
        if (!tag.contains("learned", Tag.TAG_LIST)) return false;
        ListTag list = tag.getList("learned", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (skillId.equals(list.getString(i))) return true;
        return false;
    }

    /** Imports raw final-1.12.2 BitSet/float[] only when category and array shapes are provably valid. */
    private static void importRaw112(CompoundTag out) {
        int cat=out.contains("catID")?out.getInt("catID"):-1;if(cat<0||cat>=LEGACY_ORDER.size())return;
        List<String> order=LEGACY_ORDER.get(cat);BitSet learned=null;
        if(out.contains("learnedSkills",Tag.TAG_LONG_ARRAY))learned=BitSet.valueOf(out.getLongArray("learnedSkills"));
        else if(out.contains("learnedSkills",Tag.TAG_BYTE_ARRAY))learned=BitSet.valueOf(out.getByteArray("learnedSkills"));
        else if(out.contains("learnedSkills",Tag.TAG_INT_ARRAY)){learned=new BitSet();int[] words=out.getIntArray("learnedSkills");for(int w=0;w<words.length;w++)for(int b=0;b<32;b++)if((words[w]&(1<<b))!=0)learned.set(w*32+b);}
        if(!out.contains("learned")&&learned!=null){ListTag ids=new ListTag();for(int i=learned.nextSetBit(0);i>=0&&i<order.size();i=learned.nextSetBit(i+1))ids.add(StringTag.valueOf(order.get(i)));out.put("learned",ids);}
        if(!out.contains("proficiency")&&out.contains("skillExps",Tag.TAG_LIST)){ListTag values=out.getList("skillExps",Tag.TAG_FLOAT);if(values.size()>=order.size()){CompoundTag exps=new CompoundTag();for(int i=0;i<order.size();i++){float value=values.getFloat(i);if(Float.isFinite(value))exps.putFloat(order.get(i),Math.clamp(value,0,1));}out.put("proficiency",exps);}}
    }

    private static void aliasBoolean(CompoundTag tag, String oldKey, String newKey) {
        if (!tag.contains(newKey) && tag.contains(oldKey)) tag.putBoolean(newKey, tag.getBoolean(oldKey));
    }
    private static void aliasInt(CompoundTag tag, String oldKey, String newKey) {
        if (!tag.contains(newKey) && tag.contains(oldKey)) tag.putInt(newKey, tag.getInt(oldKey));
    }
    private static void aliasFloat(CompoundTag tag, String oldKey, String newKey) {
        if (!tag.contains(newKey) && tag.contains(oldKey)) tag.putFloat(newKey, tag.getFloat(oldKey));
    }
}
