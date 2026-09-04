package com.mohistmc.academy.skill;

import com.mohistmc.academy.skill.ability.MagManipEffect;
import com.mohistmc.academy.skill.ability.aerohand.AeroSeparatorEffect;
import com.mohistmc.academy.skill.ability.aerohand.AeroToggleEffect;
import com.mohistmc.academy.skill.ability.aerohand.AirBladeEffect;
import com.mohistmc.academy.skill.ability.aerohand.AirCoolingEffect;
import com.mohistmc.academy.skill.ability.aerohand.AirJetEffect;
import com.mohistmc.academy.skill.ability.aerohand.AirWallEffect;
import com.mohistmc.academy.skill.ability.aerohand.BomberLanceEffect;
import com.mohistmc.academy.skill.ability.aerohand.StormCoreEffect;
import com.mohistmc.academy.skill.ability.aerohand.VolcanicBallEffect;
import com.mohistmc.academy.skill.ability.electromaster.ArcGenEffect;
import com.mohistmc.academy.skill.ability.electromaster.BodyIntensifyEffect;
import com.mohistmc.academy.skill.ability.electromaster.ChargingEffect;
import com.mohistmc.academy.skill.ability.electromaster.MagMovementEffect;
import com.mohistmc.academy.skill.ability.electromaster.MineDetectEffect;
import com.mohistmc.academy.skill.ability.electromaster.RailgunEffect;
import com.mohistmc.academy.skill.ability.electromaster.ThunderBoltEffect;
import com.mohistmc.academy.skill.ability.electromaster.ThunderClapEffect;
import com.mohistmc.academy.skill.ability.meltdowner.ElectronBombEffect;
import com.mohistmc.academy.skill.ability.meltdowner.ElectronMissileEffect;
import com.mohistmc.academy.skill.ability.meltdowner.JetEngineEffect;
import com.mohistmc.academy.skill.ability.meltdowner.LightShieldEffect;
import com.mohistmc.academy.skill.ability.meltdowner.MeltdownerEffect;
import com.mohistmc.academy.skill.ability.meltdowner.MineRayBasicEffect;
import com.mohistmc.academy.skill.ability.meltdowner.MineRayExpertEffect;
import com.mohistmc.academy.skill.ability.meltdowner.MineRayLuckEffect;
import com.mohistmc.academy.skill.ability.meltdowner.RayBarrageEffect;
import com.mohistmc.academy.skill.ability.meltdowner.ScatterBombEffect;
import com.mohistmc.academy.skill.ability.telekinesis.CruiseBombEffect;
import com.mohistmc.academy.skill.ability.telekinesis.OverloadThinkingEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PaperDrillEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoNeedlingEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoSlamEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoThrowingEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisToggleEffect;
import com.mohistmc.academy.skill.ability.teleporter.FlashingEffect;
import com.mohistmc.academy.skill.ability.teleporter.FleshRippingEffect;
import com.mohistmc.academy.skill.ability.teleporter.LocationTeleportEffect;
import com.mohistmc.academy.skill.ability.teleporter.MarkTeleportEffect;
import com.mohistmc.academy.skill.ability.teleporter.PenetrateTeleportEffect;
import com.mohistmc.academy.skill.ability.teleporter.ShiftTpEffect;
import com.mohistmc.academy.skill.ability.teleporter.ThreateningTeleportEffect;
import com.mohistmc.academy.skill.ability.vecmanip.BloodRetroEffect;
import com.mohistmc.academy.skill.ability.vecmanip.DirBlastEffect;
import com.mohistmc.academy.skill.ability.vecmanip.DirShockEffect;
import com.mohistmc.academy.skill.ability.vecmanip.GroundShockEffect;
import com.mohistmc.academy.skill.ability.vecmanip.PlasmaCannonEffect;
import com.mohistmc.academy.skill.ability.vecmanip.StormWingEffect;
import com.mohistmc.academy.skill.ability.vecmanip.VecAccelEffect;
import com.mohistmc.academy.skill.ability.vecmanip.VecDeviationEffect;
import com.mohistmc.academy.skill.ability.vecmanip.VecReflectionEffect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SkillRegistry {

    private static final Map<String, Skill> SKILLS = new HashMap<>();
    private static final Map<String, SkillEffect> EFFECTS = new HashMap<>();
    private static final Map<AbilityCategory, List<Skill>> SKILLS_BY_CATEGORY = new HashMap<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        registerBuiltinEffects();
        registerElectromasterSkills();
        registerMeltdownerSkills();
        registerTeleporterSkills();
        registerVecmanipSkills();
        registerAerohandSkills();
        registerTelekinesisSkills();

        bindEffects();
        validateBindings();
    }

    public static void registerSkill(Skill skill) {
        // The three generic passive course ids are intentionally shared by all six trees.
        // SKILLS_BY_CATEGORY retains every tree node while SKILLS exposes their global identity.
        SKILLS.put(skill.getId(), skill);
        SKILLS_BY_CATEGORY.computeIfAbsent(skill.getCategory(), k -> new ArrayList<>()).add(skill);
        SkillEffect effect = EFFECTS.get(skill.getId());
        if (effect != null) {
            skill.setEffect(effect);
        }
    }

    public static void registerEffect(SkillEffect effect) {
        if (EFFECTS.containsKey(effect.getId())) {
            throw new IllegalStateException("Duplicate AcademyCraft skill effect id: " + effect.getId());
        }
        EFFECTS.put(effect.getId(), effect);
        Skill skill = SKILLS.get(effect.getId());
        if (skill != null) {
            skill.setEffect(effect);
        }
    }

    private static void registerBuiltinEffects() {
        registerEffect(new ArcGenEffect());
        registerEffect(new ChargingEffect());
        registerEffect(new MagMovementEffect());
        registerEffect(new MagManipEffect());
        registerEffect(new BodyIntensifyEffect());
        registerEffect(new MineDetectEffect());
        registerEffect(new ThunderBoltEffect());
        registerEffect(new RailgunEffect());
        registerEffect(new ThunderClapEffect());

        // Teleporter
        registerEffect(new ThreateningTeleportEffect());
        registerEffect(new PenetrateTeleportEffect());
        registerEffect(new MarkTeleportEffect());
        registerEffect(new LocationTeleportEffect());
        registerEffect(new FleshRippingEffect());
        registerEffect(new FlashingEffect());
        registerEffect(new ShiftTpEffect());

        // Meltdowner
        registerEffect(new ElectronBombEffect());
        registerEffect(new ScatterBombEffect());
        registerEffect(new LightShieldEffect());
        registerEffect(new MeltdownerEffect());
        registerEffect(new MineRayBasicEffect());
        registerEffect(new MineRayExpertEffect());
        registerEffect(new MineRayLuckEffect());
        registerEffect(new RayBarrageEffect());
        registerEffect(new JetEngineEffect());
        registerEffect(new ElectronMissileEffect());

        // Vecmanip
        registerEffect(new DirShockEffect());
        registerEffect(new GroundShockEffect());
        registerEffect(new VecAccelEffect());
        registerEffect(new VecDeviationEffect());
        registerEffect(new DirBlastEffect());
        registerEffect(new StormWingEffect());
        registerEffect(new BloodRetroEffect());
        registerEffect(new VecReflectionEffect());
        registerEffect(new PlasmaCannonEffect());

        // Aerohand
        registerEffect(new VolcanicBallEffect());
        registerEffect(new AirBladeEffect());
        registerEffect(new AirCoolingEffect());
        registerEffect(new AirJetEffect());
        registerEffect(new AirWallEffect());
        registerEffect(new BomberLanceEffect());
        registerEffect(new StormCoreEffect());
        registerEffect(new AeroSeparatorEffect());
        registerEffect(new AeroToggleEffect("offense_armour"));
        registerEffect(new AeroToggleEffect("flying"));

        // Telekinesis
        registerEffect(new PsychoThrowingEffect());
        registerEffect(new PsychoTransmissionEffect());
        registerEffect(new PsychoNeedlingEffect());
        registerEffect(new CruiseBombEffect());
        registerEffect(new OverloadThinkingEffect());
        registerEffect(new PsychoSlamEffect());
        registerEffect(new PaperDrillEffect());
        registerEffect(new TelekinesisToggleEffect("psycho_harden"));
        registerEffect(new TelekinesisToggleEffect("liquid_shadow"));
    }

    private static void bindEffects() {
        for (Map.Entry<String, SkillEffect> entry : EFFECTS.entrySet()) {
            Skill skill = SKILLS.get(entry.getKey());
            if (skill != null) {
                skill.setEffect(entry.getValue());
            }
        }
    }

    static void validateBindings() {
        List<String> missing = SKILLS.values().stream()
                .filter(skill -> skill.getType() == SkillType.ACTIVE && !skill.hasEffect())
                .map(Skill::getId).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Active AcademyCraft skills without behavior: " + missing);
        }
    }

    public static Skill getSkill(String id) {
        // Safe for category-unique ids. Authority paths which can receive one
        // of the three shared course ids must use getSkill(category, id).
        return SKILLS.get(id);
    }

    /**
     * Resolve a tree node by both category and id.  The three course passives
     * intentionally reuse their ids in every tree, so the legacy one-key map
     * is ambiguous and must not be used for learning/authority decisions.
     */
    public static Skill getSkill(AbilityCategory category, String id) {
        if (category == null || id == null) return null;
        for (Skill skill : getSkillsByCategory(category)) {
            if (skill.getId().equals(id)) return skill;
        }
        return null;
    }

    public static SkillEffect getEffect(String id) {
        return EFFECTS.get(id);
    }

    public static List<Skill> getSkillsByCategory(AbilityCategory category) {
        return SKILLS_BY_CATEGORY.getOrDefault(category, Collections.emptyList());
    }

    public static List<Skill> getAllSkills() {
        return new ArrayList<>(SKILLS.values());
    }

    public static List<Skill> getSkillsByLevel(AbilityCategory category, int level) {
        return getSkillsByCategory(category).stream()
                .filter(s -> s.getLevel() == level)
                .collect(Collectors.toList());
    }

    // ==================== 内置职业注册 ====================

    private static void registerElectromasterSkills() {
        AbilityCategory cat = AbilityCategory.ELECTROMASTER;

        // 电弧激发
        registerSkill(new Skill.Builder("arc_gen", cat, 1)
                .position(24, 46)
                .cpCost(10).overload(5).build());

        // 电流回冲
        registerSkill(new Skill.Builder("charging", cat, 1)
                .position(55, 18)
                .prereq("arc_gen", 0.3f)
                .cpCost(5).overload(10).build());

        // 电磁牵引
        registerSkill(new Skill.Builder("mag_movement", cat, 2)
                .position(137, 35)
                .prereq("arc_gen", 1.0f)
                .prereq("charging", 0.7f)
                .cpCost(8).overload(15).build());

        // 磁场控制
        registerSkill(new Skill.Builder("mag_manip", cat, 2)
                .position(204, 33)
                .prereq("mag_movement", 0.5f)
                .cpCost(15).overload(20).build());

        // 矿物探测
        registerSkill(new Skill.Builder("mine_detect", cat, 3)
                .position(225, 12)
                .prereq("mag_manip", 1.0f)
                .cpCost(20).overload(10).build());

        // 生物电强化
        registerSkill(new Skill.Builder("body_intensify", cat, 3)
                .position(97.1, 15)
                .prereq("arc_gen", 1.0f).prereq("charging", 1.0f)
                .cpCost(30).overload(40).build());

        // 雷击之枪
        registerSkill(new Skill.Builder("thunder_bolt", cat, 4)
                .position(86, 67)
                .prereq("arc_gen", 1.0f)
                .prereq("charging", 0.7f)
                .cpCost(40).overload(30).build());

        // 超电磁炮
        registerSkill(new Skill.Builder("railgun", cat, 4)
                .position(164, 59)
                .prereq("thunder_bolt", 0.3f).prereq("mag_manip", 1.0f)
                .cpCost(80).overload(60).build());

        // 终极落雷
        registerSkill(new Skill.Builder("thunder_clap", cat, 5)
                .position(204, 80)
                .prereq("thunder_bolt", 1.0f)
                .cpCost(100).overload(80).build());

        // 1.12.2 appends the three generic courses after every category skill.
        // Keep this order stable because the legacy /aim #index contract exposes it.
        // 大脑训练课程(被动)
        registerSkill(new Skill.Builder("brain_course", cat, 3)
                .position(30, 110)
                .type(SkillType.PASSIVE)
                .anyLevelPrereq(3).build());

        // 大脑训练课程(高级)(被动)
        registerSkill(new Skill.Builder("brain_course_advanced", cat, 4)
                .position(115, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course", 0.0f)
                .anyLevelPrereq(4).build());

        // 思维修养课程(被动)
        registerSkill(new Skill.Builder("mind_course", cat, 5)
                .position(205, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course_advanced", 0.0f)
                .anyLevelPrereq(5).build());
    }

    private static void registerMeltdownerSkills() {
        AbilityCategory cat = AbilityCategory.MELTDOWNER;

        // 电子弹
        registerSkill(new Skill.Builder("electron_bomb", cat, 1)
                .position(15, 45)
                .cpCost(5).overload(2).build());

        // 辐射强化(被动)
        registerSkill(new Skill.Builder("rad_intensify", cat, 1)
                .position(35, 75)
                .type(SkillType.PASSIVE)
                .prereq("electron_bomb", 0.5f).build());

        // 散射弹
        registerSkill(new Skill.Builder("scatter_bomb", cat, 2)
                .position(70, 50)
                .prereq("electron_bomb", 0.8f)
                .cpCost(25).overload(50).build());

        // 光盾
        registerSkill(new Skill.Builder("light_shield", cat, 2)
                .position(55, 15)
                .prereq("electron_bomb", 1.0f)
                .cpCost(40).overload(30).build());

        // 原子崩坏
        registerSkill(new Skill.Builder("meltdowner", cat, 3)
                .position(115, 40)
                .prereq("scatter_bomb", 0.8f).prereq("light_shield", 0.8f)
                .cpCost(60).overload(50).build());

        // 矿物射线(基础)
        registerSkill(new Skill.Builder("mine_ray_basic", cat, 3)
                .position(140, 70)
                .prereq("meltdowner", 0.3f)
                .cpCost(10).overload(5).build());

        // 射线弹幕
        registerSkill(new Skill.Builder("ray_barrage", cat, 4)
                .position(140, 10)
                .prereq("meltdowner", 0.5f)
                .cpCost(50).overload(40).build());

        // 喷射引擎
        registerSkill(new Skill.Builder("jet_engine", cat, 4)
                .position(170, 32)
                .prereq("meltdowner", 1.0f)
                .cpCost(35).overload(25).build());

        // 矿物射线(专家)
        registerSkill(new Skill.Builder("mine_ray_expert", cat, 4)
                .position(172, 70)
                .prereq("mine_ray_basic", 0.8f)
                .cpCost(12).overload(5).build());

        // 矿物射线(幸运)
        registerSkill(new Skill.Builder("mine_ray_luck", cat, 5)
                .position(205, 82)
                .prereq("mine_ray_expert", 1.0f)
                .cpCost(15).overload(5).build());

        // 电子导弹
        registerSkill(new Skill.Builder("electron_missile", cat, 5)
                .position(210, 35)
                .prereq("jet_engine", 0.3f)
                .cpCost(70).overload(60).build());

        // 1.12.2 generic-course append order (also the numeric /aim order).
        // 大脑训练课程(被动)
        registerSkill(new Skill.Builder("brain_course", cat, 3)
                .position(30, 110)
                .type(SkillType.PASSIVE)
                .anyLevelPrereq(3).build());

        // 大脑训练课程(高级)(被动)
        registerSkill(new Skill.Builder("brain_course_advanced", cat, 4)
                .position(115, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course", 0.0f)
                .anyLevelPrereq(4).build());

        // 思维修养课程(被动)
        registerSkill(new Skill.Builder("mind_course", cat, 5)
                .position(205, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course_advanced", 0.0f)
                .anyLevelPrereq(5).build());
    }

    private static void registerTeleporterSkills() {
        AbilityCategory cat = AbilityCategory.TELEPORTER;

        // 威胁传送
        registerSkill(new Skill.Builder("threatening_teleport", cat, 1)
                .position(14, 42)
                .cpCost(0).overload(0).build());

        // 维度折叠定理(被动)
        registerSkill(new Skill.Builder("dim_folding_theorem", cat, 1)
                .position(50, 75)
                .type(SkillType.PASSIVE)
                .prereq("threatening_teleport", 0.2f).build());

        // 穿透传送
        registerSkill(new Skill.Builder("penetrate_teleport", cat, 2)
                .position(60, 46)
                .prereq("threatening_teleport", 0.5f)
                .cpCost(0).overload(0).build());

        // 标记传送
        registerSkill(new Skill.Builder("mark_teleport", cat, 2)
                .position(70, 16)
                .prereq("threatening_teleport", 0.4f)
                .cpCost(0).overload(0).build());

        // 撕裂肉体
        registerSkill(new Skill.Builder("flesh_ripping", cat, 3)
                .position(130, 12)
                .prereq("mark_teleport", 0.5f).prereq("penetrate_teleport", 0.5f)
                .cpCost(0).overload(0).build());

        // 位置传送
        registerSkill(new Skill.Builder("location_teleport", cat, 3)
                .position(118, 50)
                .prereq("penetrate_teleport", 0.8f).prereq("mark_teleport", 0.8f)
                .cpCost(0).overload(0).build());

        // 位移传送
        registerSkill(new Skill.Builder("shift_tp", cat, 4)
                .position(175, 47)
                .prereq("location_teleport", 0.5f)
                .cpCost(0).overload(0).build());

        // 空间波动(被动)
        registerSkill(new Skill.Builder("space_fluct", cat, 4)
                .position(160, 80)
                .type(SkillType.PASSIVE)
                .prereq("shift_tp", 0.0f).build());

        // 闪烁
        registerSkill(new Skill.Builder("flashing", cat, 5)
                .position(220, 20)
                .prereq("shift_tp", 0.8f)
                .cpCost(0).overload(0).build());

        // 1.12.2 generic-course append order (also the numeric /aim order).
        // 大脑训练课程(被动)
        registerSkill(new Skill.Builder("brain_course", cat, 3)
                .position(30, 110)
                .type(SkillType.PASSIVE)
                .anyLevelPrereq(3).build());

        // 大脑训练课程(高级)(被动)
        registerSkill(new Skill.Builder("brain_course_advanced", cat, 4)
                .position(115, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course", 0.0f)
                .anyLevelPrereq(4).build());

        // 思维修养课程(被动)
        registerSkill(new Skill.Builder("mind_course", cat, 5)
                .position(205, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course_advanced", 0.0f)
                .anyLevelPrereq(5).build());
    }

    private static void registerVecmanipSkills() {
        AbilityCategory cat = AbilityCategory.VECMANIP;

        // 定向冲击
        registerSkill(new Skill.Builder("dir_shock", cat, 1)
                .position(16, 45)
                .cpCost(50).overload(18).build());

        // 地面冲击
        registerSkill(new Skill.Builder("ground_shock", cat, 1)
                .position(64, 85)
                .prereq("dir_shock", 0.0f)
                .cpCost(80).overload(15).build());

        // 矢量加速
        registerSkill(new Skill.Builder("vec_accel", cat, 2)
                .position(76, 40)
                .prereq("dir_shock", 0.0f)
                .cpCost(120).overload(30).build());

        // 矢量偏转(被动)
        registerSkill(new Skill.Builder("vec_deviation", cat, 2)
                .position(145, 53)
                .prereq("vec_accel", 0.0f)
                .type(SkillType.ACTIVE).cpCost(0).overload(0).build());

        // 定向爆破
        registerSkill(new Skill.Builder("dir_blast", cat, 3)
                .position(136, 80)
                .prereq("ground_shock", 0.0f)
                .cpCost(160).overload(50).build());

        // 风暴之翼
        registerSkill(new Skill.Builder("storm_wing", cat, 3)
                .position(130, 20)
                .prereq("vec_accel", 0.0f)
                .cpCost(40).overload(10).build());

        // 血液回流
        registerSkill(new Skill.Builder("blood_retro", cat, 4)
                .position(204, 83)
                .prereq("dir_blast", 0.0f)
                .cpCost(280).overload(55).build());

        // 矢量反射
        registerSkill(new Skill.Builder("vec_reflection", cat, 4)
                .position(210, 50)
                .prereq("vec_deviation", 0.0f)
                .cpCost(15).overload(350).build());

        // 等离子炮
        registerSkill(new Skill.Builder("plasma_cannon", cat, 5)
                .position(175, 14)
                .prereq("storm_wing", 0.0f)
                .cpCost(18).overload(500).build());

        // 1.12.2 generic-course append order (also the numeric /aim order).
        // 大脑训练课程(被动)
        registerSkill(new Skill.Builder("brain_course", cat, 3)
                .position(30, 110)
                .type(SkillType.PASSIVE)
                .anyLevelPrereq(3).build());

        // 大脑训练课程(高级)(被动)
        registerSkill(new Skill.Builder("brain_course_advanced", cat, 4)
                .position(115, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course", 0.0f)
                .anyLevelPrereq(4).build());

        // 思维修养课程(被动)
        registerSkill(new Skill.Builder("mind_course", cat, 5)
                .position(205, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course_advanced", 0.0f)
                .anyLevelPrereq(5).build());
    }

    private static void registerAerohandSkills() {
        AbilityCategory cat = AbilityCategory.AEROHAND;

        // 火山球
        registerSkill(new Skill.Builder("volcanic_ball", cat, 1)
                .position(20, 25)
                .cpCost(40).overload(40).build());

        // 上升气流(被动)
        registerSkill(new Skill.Builder("ascending_air", cat, 1)
                .position(30, 70)
                .type(SkillType.PASSIVE)
                .build());

        // 空气刃
        registerSkill(new Skill.Builder("air_blade", cat, 2)
                .position(65, 20)
                .prereq("volcanic_ball", 0.5f)
                .cpCost(100).overload(60).build());

        // 气流(被动)
        registerSkill(new Skill.Builder("airflow", cat, 2)
                .position(75, 85)
                .type(SkillType.PASSIVE)
                .prereq("ascending_air", 0.5f).build());

        // 空气冷却
        registerSkill(new Skill.Builder("air_cooling", cat, 3)
                .position(80, 55)
                .prereq("ascending_air", 0.0f)
                // Cooling must remain usable at maximum overload and must not
                // first heat the player it is meant to cool.
                .cpCost(400).overload(0).build());

        // 空气墙
        registerSkill(new Skill.Builder("air_wall", cat, 3)
                .position(110, 35)
                .prereq("air_blade", 0.5f)
                .cpCost(500).overload(90).build());

        // 空气喷射
        registerSkill(new Skill.Builder("air_jet", cat, 3)
                .position(120, 75)
                .prereq("airflow", 0.1f)
                .cpCost(200).overload(80).build());

        // 大脑训练课程(被动)
        registerSkill(new Skill.Builder("brain_course", cat, 3)
                .position(30, 110)
                .type(SkillType.PASSIVE)
                .anyLevelPrereq(3).build());

        // 攻击装甲（原版为按键开关的持续主动技能）
        registerSkill(new Skill.Builder("offense_armour", cat, 4)
                .position(160, 45)
                .prereq("air_wall", 1.0f).build());

        // 轰炸长矛
        registerSkill(new Skill.Builder("bomber_lance", cat, 4)
                .position(150, 10)
                .prereq("air_blade", 0.5f)
                .cpCost(600).overload(240).build());

        // 大脑训练课程(高级)(被动)
        registerSkill(new Skill.Builder("brain_course_advanced", cat, 4)
                .position(115, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course", 0.0f)
                .anyLevelPrereq(4).build());

        // 飞行（原版为按键开关的持续主动技能）
        registerSkill(new Skill.Builder("flying", cat, 4)
                .position(165, 85)
                .prereq("air_jet", 0.5f).build());

        // 风暴核心
        registerSkill(new Skill.Builder("storm_core", cat, 5)
                .position(205, 65)
                .prereq("flying", 0.5f)
                .cpCost(3000).overload(300).build());

        // 空气分离器
        registerSkill(new Skill.Builder("aero_separator", cat, 5)
                .position(200, 25)
                .prereq("bomber_lance", 0.5f)
                .cpCost(1200).overload(480).build());

        // 思维修养课程(被动)
        registerSkill(new Skill.Builder("mind_course", cat, 5)
                .position(205, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course_advanced", 0.0f)
                .anyLevelPrereq(5).build());
    }

    private static void registerTelekinesisSkills() {
        AbilityCategory cat = AbilityCategory.TELEKINESIS;

        // 念力投掷
        registerSkill(new Skill.Builder("psycho_throwing", cat, 1)
                .position(20, 25)
                .cpCost(400).overload(30).build());

        // 念力传输
        registerSkill(new Skill.Builder("psycho_transmission", cat, 1)
                .position(30, 65)
                .cpCost(0).overload(5).build());

        // 念力针
        registerSkill(new Skill.Builder("psycho_needling", cat, 2)
                .position(65, 20)
                .prereq("psycho_throwing", 0.5f)
                .cpCost(800).overload(20).build());

        // 绝缘(被动)
        registerSkill(new Skill.Builder("insulation", cat, 1)
                .position(70, 70)
                .type(SkillType.PASSIVE)
                .prereq("psycho_transmission", 0.0f).build());

        // 巡航炸弹
        registerSkill(new Skill.Builder("cruise_bomb", cat, 3)
                .position(110, 15)
                .prereq("psycho_needling", 0.5f)
                .cpCost(0).overload(0).build());

        // 过载思维
        registerSkill(new Skill.Builder("overload_thinking", cat, 3)
                .position(115, 55)
                .prereq("insulation", 0.0f)
                .cpCost(0).overload(0).build());

        // 完美纸张(被动)
        registerSkill(new Skill.Builder("perfect_paper", cat, 3)
                .position(120, 85)
                .type(SkillType.PASSIVE)
                .prereq("insulation", 0.0f).build());

        // 大脑训练课程(被动)
        registerSkill(new Skill.Builder("brain_course", cat, 3)
                .position(30, 110)
                .type(SkillType.PASSIVE)
                .anyLevelPrereq(3).build());

        // 念力猛击
        registerSkill(new Skill.Builder("psycho_slam", cat, 4)
                .position(160, 30)
                .prereq("cruise_bomb", 0.5f)
                .cpCost(3000).overload(180).build());

        // 念力硬化（原版为按键开关的持续主动技能）
        registerSkill(new Skill.Builder("psycho_harden", cat, 4)
                .position(165, 60)
                .prereq("overload_thinking", 0.5f).build());

        // 大脑训练课程(高级)(被动)
        registerSkill(new Skill.Builder("brain_course_advanced", cat, 4)
                .position(115, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course", 0.0f)
                .anyLevelPrereq(4).build());

        // 液态阴影（原版为按键开关的持续主动技能）
        registerSkill(new Skill.Builder("liquid_shadow", cat, 5)
                .position(190, 15)
                .prereq("cruise_bomb", 0.9f).build());

        // 纸张钻头
        registerSkill(new Skill.Builder("paper_drill", cat, 5)
                .position(205, 80)
                .prereq("perfect_paper", 0.5f)
                .cpCost(4000).overload(120).build());

        // 思维修养课程(被动)
        registerSkill(new Skill.Builder("mind_course", cat, 5)
                .position(205, 110)
                .type(SkillType.PASSIVE)
                .prereq("brain_course_advanced", 0.0f)
                .anyLevelPrereq(5).build());
    }

}
