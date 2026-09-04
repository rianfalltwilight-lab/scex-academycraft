package com.mohistmc.academy.listener;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.client.block.entity.model.CatEngineModel;
import com.mohistmc.academy.client.block.entity.render.CatEngineRender;
import com.mohistmc.academy.client.block.entity.render.MatrixRender;
import com.mohistmc.academy.client.block.entity.render.PhaseLiquidRender;
import com.mohistmc.academy.client.block.entity.render.WindGenFanRender;
import com.mohistmc.academy.client.block.gui.*;
import com.mohistmc.academy.client.entity.CoinRenderer;
import com.mohistmc.academy.client.particle.AcademyTextureParticle;
import com.mohistmc.academy.client.particle.SilbarnFragmentParticle;
import com.mohistmc.academy.client.render.MagManipBlockRenderer;
import com.mohistmc.academy.client.render.OreHighlightRenderer;
import com.mohistmc.academy.client.renderer.BloodSprayDecalRenderer;
import com.mohistmc.academy.client.renderer.BloodSplashRenderer;
import com.mohistmc.academy.client.renderer.LegacyFieldEffectRenderer;
import com.mohistmc.academy.client.renderer.MeltdownBeamRenderer;
import com.mohistmc.academy.client.renderer.MeltdownBarrageRenderer;
import com.mohistmc.academy.client.renderer.PlasmaOrbRenderer;
import com.mohistmc.academy.client.renderer.RailgunBeamRenderer;
import com.mohistmc.academy.client.renderer.ShieldEffectRenderer;
import com.mohistmc.academy.client.renderer.SilbarnRenderer;
import com.mohistmc.academy.client.renderer.StormWingVisualRenderer;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.AcademyParticles;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client registration events belong to the mod bus, not the runtime game bus. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModListener {
    private ClientModListener() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyInputHandler.register(event);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AcademyParticles.TELEPORT.get(),
                sprites -> new AcademyTextureParticle.Provider(sprites, 0xffffff,
                        .1f, .2f, 40, 40, .6f, .8f, 0, 20));
        event.registerSpriteSet(AcademyParticles.FORMULA.get(),
                sprites -> new AcademyTextureParticle.Provider(sprites, 0xdcdcdc,
                        1f, 1.7f, 30, 35, .6f, 1f, 2, 20));
        event.registerSpriteSet(AcademyParticles.MELTDOWN.get(),
                sprites -> new AcademyTextureParticle.Provider(sprites, 0xffffff,
                        .05f, .07f, 45, 75, .3f, .6f, 0, 20));
        event.registerSpriteSet(AcademyParticles.MELTDOWN_LUCK.get(),
                sprites -> new AcademyTextureParticle.Provider(sprites, 0xf1e5f7,
                        .05f, .07f, 45, 75, .3f, .6f, 0, 20));
        event.registerSpriteSet(AcademyParticles.SILBARN_FRAGMENT.get(), SilbarnFragmentParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            com.mohistmc.academy.client.gui.TutorialMdParser.clearCache();
            com.mohistmc.academy.tutorial.ACTutorial.clearContentCache();
            com.mohistmc.academy.utils.RenderUtils.clearTextureSizeCache();
            com.mohistmc.academy.client.media.ExternalMediaManager.invalidateForResourceReload();
        });
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(MatrixRender.SHIELD_MODEL);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(AcademyMenus.ABILITY_INTERFERER_MENU.get(), AbilityInterfererGui::new);
        event.register(AcademyMenus.WIND_BASE_MENU.get(), WindBaseGui::new);
        event.register(AcademyMenus.WIND_MAIN_MENU.get(), WindMainGui::new);
        event.register(AcademyMenus.NODE_BASIC.get(), NodeBasicGui::new);
        event.register(AcademyMenus.NODE_STANDARD_MENU.get(), NodeStandardGui::new);
        event.register(AcademyMenus.NODE_ADVANCED_MENU.get(), NodeAdvancedGui::new);
        event.register(AcademyMenus.IMAG_FUSOR_MENU.get(), ImagFusorGui::new);
        event.register(AcademyMenus.SOLAR_GEN_MENU.get(), SolarGenGui::new);
        event.register(AcademyMenus.ENERGY_BRIDGE_MENU.get(), EnergyBridgeGui::new);
        event.register(AcademyMenus.PHASE_GEN_MENU.get(), PhaseGenGui::new);
        event.register(AcademyMenus.MATRIX_MENU.get(), MatrixGui::new);
        event.register(AcademyMenus.METAL_FORMER_MENU.get(), MetalFomerGui::new);
        event.register(AcademyMenus.DEV_NORMAL_MENU.get(), DevNormalGui::new);
        event.register(AcademyMenus.DEV_ADVANCED_MENU.get(), DevAdvancedGui::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CatEngineModel.LAYER_LOCATION, CatEngineModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyBlockEntities.CAT_ENGINE.get(), CatEngineRender::new);
        event.registerBlockEntityRenderer(AcademyBlockEntities.MATRIX.get(), MatrixRender::new);
        event.registerBlockEntityRenderer(AcademyBlockEntities.PHASE_LIQUID.get(), PhaseLiquidRender::new);
        event.registerBlockEntityRenderer(AcademyBlockEntities.WINDGEN_FAN.get(), WindGenFanRender::new);
        event.registerEntityRenderer(AcademyEntities.COIN_ENTITY.get(), CoinRenderer::new);
        event.registerEntityRenderer(AcademyEntities.SILBARN.get(), SilbarnRenderer::new);
        event.registerEntityRenderer(AcademyEntities.MAG_HOOK.get(),
                context -> new ThrownItemRenderer<>(context, 1.0F, true));
        event.registerEntityRenderer(AcademyEntities.ORE_HIGHLIGHT.get(), OreHighlightRenderer::new);
        event.registerEntityRenderer(AcademyEntities.MAG_MANIP_BLOCK.get(), MagManipBlockRenderer::new);
        event.registerEntityRenderer(AcademyEntities.RAILGUN_BEAM.get(), RailgunBeamRenderer::new);
        event.registerEntityRenderer(AcademyEntities.MELTDOWN_BEAM.get(), MeltdownBeamRenderer::new);
        event.registerEntityRenderer(AcademyEntities.MELTDOWN_BARRAGE.get(), MeltdownBarrageRenderer::new);
        event.registerEntityRenderer(AcademyEntities.SHIELD_EFFECT.get(), ShieldEffectRenderer::new);
        event.registerEntityRenderer(AcademyEntities.PLASMA_ORB.get(), PlasmaOrbRenderer::new);
        event.registerEntityRenderer(AcademyEntities.MD_BALL.get(),
                com.mohistmc.academy.client.renderer.MdBallRenderer::new);
        event.registerEntityRenderer(AcademyEntities.BLOOD_SPRAY_DECAL.get(), BloodSprayDecalRenderer::new);
        event.registerEntityRenderer(AcademyEntities.BLOOD_SPLASH.get(), BloodSplashRenderer::new);
        event.registerEntityRenderer(AcademyEntities.LEGACY_FIELD_EFFECT.get(), LegacyFieldEffectRenderer::new);
        event.registerEntityRenderer(AcademyEntities.STORM_WING_VISUAL.get(), StormWingVisualRenderer::new);
    }
}
