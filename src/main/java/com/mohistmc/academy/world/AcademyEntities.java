package com.mohistmc.academy.world;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.MeltdownBeamEntity;
import com.mohistmc.academy.entity.ShieldEffectEntity;
import com.mohistmc.academy.entity.RailgunBeamEntity;
import com.mohistmc.academy.entity.PlasmaOrbEntity;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.entity.MeltdownBarrageEntity;
import com.mohistmc.academy.entity.BloodSprayDecalEntity;
import com.mohistmc.academy.entity.BloodSplashEntity;
import com.mohistmc.academy.entity.LegacyFieldEffectEntity;
import com.mohistmc.academy.entity.StormWingVisualEntity;
import com.mohistmc.academy.world.entity.CoinEntity;
import com.mohistmc.academy.world.entity.OreHighlightEntity;
import com.mohistmc.academy.world.entity.MagManipBlockEntity;
import com.mohistmc.academy.world.entity.EntitySilbarn;
import com.mohistmc.academy.world.entity.EntityMagHook;
import com.mohistmc.academy.world.entity.ExtraPaperPlaneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AcademyEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AcademyCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CoinEntity>> COIN_ENTITY = ENTITIES.register("coin_entity",
            () -> EntityType.Builder.of(CoinEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.1F) // 硬币的大小
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("coin_entity"));

    public static final DeferredHolder<EntityType<?>, EntityType<EntitySilbarn>> SILBARN = ENTITIES.register("silbarn",
            () -> EntityType.Builder.of(EntitySilbarn::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .build("silbarn"));

    public static final DeferredHolder<EntityType<?>, EntityType<EntityMagHook>> MAG_HOOK = ENTITIES.register("mag_hook_projectile",
            () -> EntityType.Builder.of(EntityMagHook::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("mag_hook_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<ExtraPaperPlaneEntity>> PAPER_PLANE = ENTITIES.register("paper_plane",
            () -> EntityType.Builder.of(ExtraPaperPlaneEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("paper_plane"));

    public static final DeferredHolder<EntityType<?>, EntityType<OreHighlightEntity>> ORE_HIGHLIGHT = ENTITIES.register("ore_highlight",
            () -> EntityType.Builder.of(OreHighlightEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(64)
                    .updateInterval(20)
                    .build("ore_highlight"));
    public static final DeferredHolder<EntityType<?>, EntityType<MagManipBlockEntity>> MAG_MANIP_BLOCK = ENTITIES.register("mag_manip_block",
            () -> EntityType.Builder.of(MagManipBlockEntity::new, MobCategory.MISC).sized(1,1).clientTrackingRange(64).updateInterval(1).build("mag_manip_block"));

    public static final DeferredHolder<EntityType<?>, EntityType<RailgunBeamEntity>> RAILGUN_BEAM = ENTITIES.register("railgun_beam",
            () -> EntityType.Builder.of(RailgunBeamEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .clientTrackingRange(64)
            .updateInterval(1)
            .noSummon()
            .build("railgun_beam"));

    public static final DeferredHolder<EntityType<?>, EntityType<MeltdownBeamEntity>> MELTDOWN_BEAM = ENTITIES.register("meltdown_beam",
            () -> EntityType.Builder.of(MeltdownBeamEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .clientTrackingRange(64)
            .updateInterval(1)
            .noSummon()
            .build("meltdown_beam"));

    public static final DeferredHolder<EntityType<?>, EntityType<MeltdownBarrageEntity>> MELTDOWN_BARRAGE = ENTITIES.register("meltdown_barrage",
            () -> EntityType.Builder.of(MeltdownBarrageEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSummon()
                    .noSave()
                    .build("meltdown_barrage"));

    public static final DeferredHolder<EntityType<?>, EntityType<ShieldEffectEntity>> SHIELD_EFFECT = ENTITIES.register("shield_effect",
            () -> EntityType.Builder.of(ShieldEffectEntity::new, MobCategory.MISC)
            .sized(2.0f, 2.0f)
            .clientTrackingRange(64)
            .updateInterval(1)
            .noSummon()
            .build("shield_effect"));
    public static final DeferredHolder<EntityType<?>, EntityType<PlasmaOrbEntity>> PLASMA_ORB = ENTITIES.register("plasma_orb",
            () -> EntityType.Builder.of(PlasmaOrbEntity::new, MobCategory.MISC).sized(2f,2f)
                    .clientTrackingRange(128).updateInterval(1).noSummon().build("plasma_orb"));
    public static final DeferredHolder<EntityType<?>, EntityType<MdBallEntity>> MD_BALL = ENTITIES.register("md_ball",
            () -> EntityType.Builder.of(MdBallEntity::new, MobCategory.MISC).sized(.35f,.35f)
                    .clientTrackingRange(64).updateInterval(1).noSummon().build("md_ball"));
    public static final DeferredHolder<EntityType<?>, EntityType<BloodSprayDecalEntity>> BLOOD_SPRAY_DECAL = ENTITIES.register("blood_spray_decal",
            () -> EntityType.Builder.of(BloodSprayDecalEntity::new, MobCategory.MISC).sized(1.2f,.02f)
                    .clientTrackingRange(48).updateInterval(20).noSummon().build("blood_spray_decal"));
    public static final DeferredHolder<EntityType<?>, EntityType<BloodSplashEntity>> BLOOD_SPLASH = ENTITIES.register("blood_splash",
            () -> EntityType.Builder.of(BloodSplashEntity::new, MobCategory.MISC).sized(.1f,.1f)
                    .clientTrackingRange(48).updateInterval(1).noSummon().noSave().build("blood_splash"));
    public static final DeferredHolder<EntityType<?>, EntityType<LegacyFieldEffectEntity>> LEGACY_FIELD_EFFECT = ENTITIES.register("legacy_field_effect",
            () -> EntityType.Builder.of(LegacyFieldEffectEntity::new, MobCategory.MISC).sized(.1f,.1f)
                    .clientTrackingRange(64).updateInterval(1).noSummon().build("legacy_field_effect"));
    public static final DeferredHolder<EntityType<?>, EntityType<StormWingVisualEntity>> STORM_WING_VISUAL = ENTITIES.register("storm_wing_visual",
            () -> EntityType.Builder.of(StormWingVisualEntity::new, MobCategory.MISC).sized(.1f,.1f)
                    .clientTrackingRange(64).updateInterval(1).noSummon().noSave().build("storm_wing_visual"));
}

