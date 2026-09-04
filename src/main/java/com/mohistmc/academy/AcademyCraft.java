package com.mohistmc.academy;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.crafting.AcademyRecipeSerializers;
import com.mohistmc.academy.crafting.AcademyRecipeTypes;
import com.mohistmc.academy.crafting.MFIFRecipes;
import com.mohistmc.academy.listener.ServerListener;
import com.mohistmc.academy.network.ConnectToNodePacket;
import com.mohistmc.academy.network.ConnectNodeToMatrixPacket;
import com.mohistmc.academy.network.AbilityInterfererConfigPacket;
import com.mohistmc.academy.network.AbilityInterfererStatePacket;
import com.mohistmc.academy.network.ChargingAckPacket;
import com.mohistmc.academy.network.CoinTossResultPacket;
import com.mohistmc.academy.network.TeleporterCriticalPacket;
import com.mohistmc.academy.network.TeleporterTrailPacket;
import com.mohistmc.academy.network.CloseDevLearningSessionPacket;
import com.mohistmc.academy.network.ConsoleCommandPacket;
import com.mohistmc.academy.network.DisconnectFromNodePacket;
import com.mohistmc.academy.network.DisconnectNodeFromMatrixPacket;
import com.mohistmc.academy.network.InitMatrixPacket;
import com.mohistmc.academy.network.MatrixNodesPacket;
import com.mohistmc.academy.network.MatrixNetworkListSyncPacket;
import com.mohistmc.academy.network.MatrixConfigPacket;
import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.network.LocationTeleportActionPacket;
import com.mohistmc.academy.network.LocationTeleportSyncPacket;
import com.mohistmc.academy.network.LocationConsentRequestPacket;
import com.mohistmc.academy.network.LocationConsentResponsePacket;
import com.mohistmc.academy.network.FlashingActionPacket;
import com.mohistmc.academy.network.FlashingStatePacket;
import com.mohistmc.academy.network.FreqTransmitterActionPacket;
import com.mohistmc.academy.network.FreqTransmitterStatePacket;
import com.mohistmc.academy.network.MetalFormerActionMessage;
import com.mohistmc.academy.network.MineDetectResultPacket;
import com.mohistmc.academy.network.NodeConfigPacket;
import com.mohistmc.academy.network.NodeListSyncPacket;
import com.mohistmc.academy.network.OpenDevGuiPacket;
import com.mohistmc.academy.network.OpenDevNetworkPacket;
import com.mohistmc.academy.network.OpenDevNetworkPagePacket;
import com.mohistmc.academy.network.DevLearningResultPacket;
import com.mohistmc.academy.network.RequestNodesPacket;
import com.mohistmc.academy.network.RequestMatrixNetworksPacket;
import com.mohistmc.academy.network.OpenTutorialGuiPacket;
import com.mohistmc.academy.network.SetSkillSlotPacket;
import com.mohistmc.academy.network.SkillKeyDownPacket;
import com.mohistmc.academy.network.SkillKeyUpPacket;
import com.mohistmc.academy.network.ChargingCancelPacket;
import com.mohistmc.academy.network.SwitchPresetPacket;
import com.mohistmc.academy.network.StartTerminalInstallPacket;
import com.mohistmc.academy.network.SyncAbilityDataPacket;
import com.mohistmc.academy.network.SyncChargingStatePacket;
import com.mohistmc.academy.network.SettingsConfigPacket;
import com.mohistmc.academy.network.ToggleAbilityPacket;
import com.mohistmc.academy.network.TutorialActivatedPacket;
import com.mohistmc.academy.network.UseSkillPacket;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.terminal.AppRegistry;
import com.mohistmc.academy.terminal.MediaTrackRegistry;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.AcademyParticles;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.worldgen.AcademyPlacementModifiers;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(AcademyCraft.MODID)
public class AcademyCraft {
    public static final String MODID = "academy";
    /** Strict and mandatory on both peers: NeoForge rejects any unequal protocol before play payloads. */
    /** Payload layout generation; bump whenever any play payload or synced data schema changes. */
    public static final String NETWORK_PROTOCOL = "academy-1.21.1-payload-v12-data-v4";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AcademyCraft(IEventBus modEventBus, ModContainer modContainer) {

        AcademyMenus.MENUS.register(modEventBus);
        AcademyItems.ITEMS.register(modEventBus);
        AcademyItems.TABS.register(modEventBus);
        AcademyBlocks.BLOCKS.register(modEventBus);
        AcademyFluids.FLUID_TYPES.register(modEventBus);
        AcademyFluids.FLUIDS.register(modEventBus);
        AcademyEntities.ENTITIES.register(modEventBus);
        AcademyParticles.PARTICLES.register(modEventBus);
        AcademyBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        AcademySounds.SOUND_EVENTS.register(modEventBus);
        AcademyAttachments.ATTACHMENT_TYPES.register(modEventBus);
        AcademyRecipeTypes.RECIPE_TYPES.register(modEventBus);
        AcademyRecipeSerializers.SERIALIZERS.register(modEventBus);
        AcademyPlacementModifiers.TYPES.register(modEventBus);

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.SERVER, ACConfig.Server.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ACConfig.Client.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerCapabilities);

        NeoForge.EVENT_BUS.register(new ServerListener());
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        // 金属成型机：侧面自动输入输出
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AcademyBlockEntities.METAL_FORMER.get(),
                (be, side) -> be.getHandlerForSide(side)
        );
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                AcademyBlockEntities.PHASE_GEN.get(), (be, side) -> be.getFluidTank());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                AcademyBlockEntities.IMAG_FUSOR.get(), (be, side) -> be.getFluidTank());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                AcademyBlockEntities.IMAG_FUSOR.get(), (be, side) -> be.getHandlerForSide(side));
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, context) -> new com.mohistmc.academy.capability.AcademyEnergyItemStorage(stack),
                AcademyItems.ENERGY_UNIT.get(), AcademyItems.DEVELOPER_PORTABLE.get(),
                AcademyItems.RAY_TWISTER.get(), AcademyItems.ENERGY_UNIT_GROUP.get(),
                AcademyItems.ELECTRICALIBUR.get(), AcademyItems.AVALON.get(),
                AcademyItems.LASOR_GUN.get(), AcademyItems.AIR_JET.get(),
                AcademyItems.TELEPORTER_DEVICE.get(), AcademyItems.DROP_ITEM_MAGNET.get(),
                AcademyItems.IMAG_HELMET.get(), AcademyItems.IMAG_CHESTPLATE.get(),
                AcademyItems.IMAG_LEGGINGS.get(), AcademyItems.IMAG_BOOTS.get());
        // Expose IF through the standard NeoForge energy capability so Jade and
        // automation mods see the same authoritative value as our machine GUI.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.DEV_NORMAL.get(),
                (be,side) -> new com.mohistmc.academy.capability.ForgeEnergyView(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.DEV_ADVANCED.get(),
                (be,side) -> new com.mohistmc.academy.capability.ForgeEnergyView(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.DEV_NORMAL_SUB.get(),
                (be,side) -> energyViewOfMain(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.DEV_ADVANCED_SUB.get(),
                (be,side) -> energyViewOfMain(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.SOLAR_GEN.get(),
                (be,side) -> new com.mohistmc.academy.capability.ForgeEnergyView(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.ABILITY_INTERFERER.get(),
                (be,side) -> new com.mohistmc.academy.capability.ForgeEnergyView(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.WINDGEN_BASE.get(),
                (be,side) -> new com.mohistmc.academy.capability.ForgeEnergyView(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.WIND_GEN_BASE_SUB.get(),
                (be, side) -> energyViewOfWindBase(be));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.RF_INPUT.get(),
                (be, side) -> be.externalEnergy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AcademyBlockEntities.RF_OUTPUT.get(),
                (be, side) -> be.externalEnergy());
    }

    private static net.neoforged.neoforge.energy.IEnergyStorage energyViewOfMain(
            net.minecraft.world.level.block.entity.BlockEntity subEntity) {
        if (!(subEntity instanceof com.mohistmc.academy.world.block.IDevSubStructure sub)
                || subEntity.getLevel() == null) return null;
        var mainEntity = com.mohistmc.academy.world.block.DevMachineSubBase.validatedMainForProxy(
                subEntity.getLevel(), subEntity.getBlockPos(), subEntity.getBlockState().getBlock(), sub);
        if (!(mainEntity instanceof com.mohistmc.academy.capability.IFEnergyStorage energy)) return null;
        return new com.mohistmc.academy.capability.ForgeEnergyView(energy);
    }

    /**
     * A wind base is two blocks tall in 1.0.7.  Expose the lower block's one
     * authoritative store when Jade or automation queries the visible upper
     * proxy, instead of presenting two unrelated machines (or no energy at
     * all) depending on the face being looked at.
     */
    private static net.neoforged.neoforge.energy.IEnergyStorage energyViewOfWindBase(
            net.minecraft.world.level.block.entity.BlockEntity subEntity) {
        if (!(subEntity instanceof com.mohistmc.academy.world.block.entity.WindGenBaseSubBlockEntity)
                || subEntity.getLevel() == null) return null;
        var main = subEntity.getLevel().getBlockEntity(subEntity.getBlockPos().below());
        if (!(main instanceof com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity energy)) {
            return null;
        }
        return new com.mohistmc.academy.capability.ForgeEnergyView(energy);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        SkillRegistry.init();
        AppRegistry.init();
        MediaTrackRegistry.init();
        MFIFRecipes.init();
        LOGGER.info("AcademyCraft Skill Registry initialized with {} skills", SkillRegistry.getAllSkills().size());
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MODID).versioned(NETWORK_PROTOCOL);
        registrar.playToServer(
                LearnSkillPacket.TYPE,
                LearnSkillPacket.STREAM_CODEC,
                LearnSkillPacket::handle
        );
        registrar.playToServer(AbilityInterfererConfigPacket.TYPE,
                AbilityInterfererConfigPacket.STREAM_CODEC, AbilityInterfererConfigPacket::handle);
        registrar.playToClient(AbilityInterfererStatePacket.TYPE,
                AbilityInterfererStatePacket.STREAM_CODEC, AbilityInterfererStatePacket::handle);
        registrar.playToClient(DevLearningResultPacket.TYPE,
                DevLearningResultPacket.STREAM_CODEC, DevLearningResultPacket::handle);
        registrar.playToServer(
                CloseDevLearningSessionPacket.TYPE,
                CloseDevLearningSessionPacket.STREAM_CODEC,
                CloseDevLearningSessionPacket::handle
        );
        registrar.playToServer(
                UseSkillPacket.TYPE,
                UseSkillPacket.STREAM_CODEC,
                UseSkillPacket::handle
        );
        registrar.playToServer(LocationTeleportActionPacket.TYPE, LocationTeleportActionPacket.STREAM_CODEC, LocationTeleportActionPacket::handle);
        registrar.playToClient(LocationTeleportSyncPacket.TYPE, LocationTeleportSyncPacket.STREAM_CODEC, LocationTeleportSyncPacket::handle);
        registrar.playToClient(LocationConsentRequestPacket.TYPE, LocationConsentRequestPacket.STREAM_CODEC, LocationConsentRequestPacket::handle);
        registrar.playToServer(LocationConsentResponsePacket.TYPE, LocationConsentResponsePacket.STREAM_CODEC, LocationConsentResponsePacket::handle);
        registrar.playToClient(MineDetectResultPacket.TYPE, MineDetectResultPacket.STREAM_CODEC, MineDetectResultPacket::handle);
        registrar.playToServer(FlashingActionPacket.TYPE, FlashingActionPacket.STREAM_CODEC, FlashingActionPacket::handle);
        registrar.playToClient(FlashingStatePacket.TYPE, FlashingStatePacket.STREAM_CODEC, FlashingStatePacket::handle);
        registrar.playToServer(
                SetSkillSlotPacket.TYPE,
                SetSkillSlotPacket.STREAM_CODEC,
                SetSkillSlotPacket::handle
        );
        registrar.playToServer(
                ToggleAbilityPacket.TYPE,
                ToggleAbilityPacket.STREAM_CODEC,
                ToggleAbilityPacket::handle
        );
        registrar.playToClient(
                SyncAbilityDataPacket.TYPE,
                SyncAbilityDataPacket.STREAM_CODEC,
                SyncAbilityDataPacket::handle
        );
        registrar.playToClient(
                OpenDevGuiPacket.TYPE,
                OpenDevGuiPacket.STREAM_CODEC,
                OpenDevGuiPacket::handle
        );
        registrar.playToClient(OpenDevNetworkPagePacket.TYPE,
                OpenDevNetworkPagePacket.STREAM_CODEC, OpenDevNetworkPagePacket::handle);
        registrar.playToServer(OpenDevNetworkPacket.TYPE,
                OpenDevNetworkPacket.STREAM_CODEC, OpenDevNetworkPacket::handle);
        registrar.playToClient(
                OpenTutorialGuiPacket.TYPE,
                OpenTutorialGuiPacket.STREAM_CODEC,
                OpenTutorialGuiPacket::handle
        );
        registrar.playToClient(TutorialActivatedPacket.TYPE,
                TutorialActivatedPacket.STREAM_CODEC, TutorialActivatedPacket::handle);
        registrar.playToClient(
                StartTerminalInstallPacket.TYPE,
                StartTerminalInstallPacket.STREAM_CODEC,
                StartTerminalInstallPacket::handle
        );
        registrar.playToServer(
                SkillKeyDownPacket.TYPE,
                SkillKeyDownPacket.STREAM_CODEC,
                SkillKeyDownPacket::handle
        );
        registrar.playToServer(
                SkillKeyUpPacket.TYPE,
                SkillKeyUpPacket.STREAM_CODEC,
                SkillKeyUpPacket::handle
        );
        registrar.playToServer(ChargingCancelPacket.TYPE, ChargingCancelPacket.STREAM_CODEC, ChargingCancelPacket::handle);
        registrar.playToServer(SwitchPresetPacket.TYPE, SwitchPresetPacket.STREAM_CODEC, SwitchPresetPacket::handle);
        registrar.playToServer(
                ChargingAckPacket.TYPE,
                ChargingAckPacket.STREAM_CODEC,
                ChargingAckPacket::handle
        );
        registrar.playToClient(
                SyncChargingStatePacket.TYPE,
                SyncChargingStatePacket.STREAM_CODEC,
                SyncChargingStatePacket::handle
        );
        registrar.playToServer(
                InitMatrixPacket.TYPE,
                InitMatrixPacket.STREAM_CODEC,
                InitMatrixPacket::handle
        );
        registrar.playToServer(MatrixNodesPacket.TYPE, MatrixNodesPacket.STREAM_CODEC, MatrixNodesPacket::handle);
        registrar.playToServer(MatrixConfigPacket.TYPE, MatrixConfigPacket.STREAM_CODEC, MatrixConfigPacket::handle);
        registrar.playToServer(FreqTransmitterActionPacket.TYPE,
                FreqTransmitterActionPacket.STREAM_CODEC, FreqTransmitterActionPacket::handle);
        registrar.playToClient(FreqTransmitterStatePacket.TYPE,
                FreqTransmitterStatePacket.STREAM_CODEC, FreqTransmitterStatePacket::handle);
        registrar.playToServer(
                RequestNodesPacket.TYPE,
                RequestNodesPacket.STREAM_CODEC,
                RequestNodesPacket::handle
        );
        registrar.playToClient(
                NodeListSyncPacket.TYPE,
                NodeListSyncPacket.STREAM_CODEC,
                NodeListSyncPacket::handle
        );
        registrar.playToServer(
                RequestMatrixNetworksPacket.TYPE,
                RequestMatrixNetworksPacket.STREAM_CODEC,
                RequestMatrixNetworksPacket::handle
        );
        registrar.playToClient(
                MatrixNetworkListSyncPacket.TYPE,
                MatrixNetworkListSyncPacket.STREAM_CODEC,
                MatrixNetworkListSyncPacket::handle
        );
        registrar.playToServer(
                ConnectNodeToMatrixPacket.TYPE,
                ConnectNodeToMatrixPacket.STREAM_CODEC,
                ConnectNodeToMatrixPacket::handle
        );
        registrar.playToServer(
                DisconnectNodeFromMatrixPacket.TYPE,
                DisconnectNodeFromMatrixPacket.STREAM_CODEC,
                DisconnectNodeFromMatrixPacket::handle
        );
        registrar.playToServer(
                ConnectToNodePacket.TYPE,
                ConnectToNodePacket.STREAM_CODEC,
                ConnectToNodePacket::handle
        );
        registrar.playToServer(
                DisconnectFromNodePacket.TYPE,
                DisconnectFromNodePacket.STREAM_CODEC,
                DisconnectFromNodePacket::handle
        );
        registrar.playToServer(
                ConsoleCommandPacket.TYPE,
                ConsoleCommandPacket.STREAM_CODEC,
                ConsoleCommandPacket::handle
        );
        registrar.playToServer(
                NodeConfigPacket.TYPE,
                NodeConfigPacket.STREAM_CODEC,
                NodeConfigPacket::handle
        );
        registrar.playToServer(
                MetalFormerActionMessage.TYPE,
                MetalFormerActionMessage.STREAM_CODEC,
                MetalFormerActionMessage::handle
        );
        registrar.playToServer(SettingsConfigPacket.TYPE,
                SettingsConfigPacket.STREAM_CODEC, SettingsConfigPacket::handle);
        registrar.playToClient(CoinTossResultPacket.TYPE,
                CoinTossResultPacket.STREAM_CODEC, CoinTossResultPacket::handle);
        registrar.playToClient(TeleporterCriticalPacket.TYPE,
                TeleporterCriticalPacket.STREAM_CODEC, TeleporterCriticalPacket::handle);
        registrar.playToClient(TeleporterTrailPacket.TYPE,
                TeleporterTrailPacket.STREAM_CODEC, TeleporterTrailPacket::handle);
    }
}
