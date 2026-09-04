package com.mohistmc.academy.client;

import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.LocationTeleportGui;
import com.mohistmc.academy.client.gui.FreqTransmitterGui;
import com.mohistmc.academy.client.gui.SkillTreeGui;
import com.mohistmc.academy.client.gui.TutorialAppGui;
import com.mohistmc.academy.network.*;
import com.mohistmc.academy.world.block.DevMachineType;
import net.minecraft.client.Minecraft;

/** Client-only endpoint for S2C payloads; common payload codecs remain dedicated-server loadable. */
public final class ClientPacketBridge {
    private static final FlashingEpochState FLASHING = new FlashingEpochState();
    private static MineDetectResultPacket mineDetectSnapshot = new MineDetectResultPacket(java.util.List.of(), 0);
    private ClientPacketBridge() {}
    public static long flashingEpoch() { return FLASHING.epoch(); }
    public static void resetClientSession() {
        FLASHING.reset();
        mineDetectSnapshot = new MineDetectResultPacket(java.util.List.of(), 0);
        FreqTransmitterGui.resetClientSession();
    }
    public static void openDev(OpenDevGuiPacket p) { Minecraft.getInstance().setScreen(new SkillTreeGui(false, false,
            DevMachineType.fromOrdinal(p.typeOrdinal()), p.energy(), p.maxEnergy(), p.mainPos().orElse(null),
            p.nonce(), p.nodeName())); }
    public static void openDevNetworkPage(OpenDevNetworkPagePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.mohistmc.academy.client.block.gui.DevNormalGui gui) {
            gui.openNetworkPage(p.pos(), p.containerId());
        } else if (mc.screen instanceof com.mohistmc.academy.client.block.gui.DevAdvancedGui gui) {
            gui.openNetworkPage(p.pos(), p.containerId());
        }
    }
    public static void openTutorial() { Minecraft.getInstance().setScreen(new TutorialAppGui()); }
    public static void tutorialActivated(String tutorialId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || tutorialId == null || tutorialId.length() > TutorialActivatedPacket.MAX_ID_LENGTH) return;
        if (com.mohistmc.academy.tutorial.TutorialRegistry.enumeration().isEmpty()) {
            com.mohistmc.academy.tutorial.TutorialInit.init();
        }
        com.mohistmc.academy.tutorial.ACTutorial tutorial;
        try {
            tutorial = com.mohistmc.academy.tutorial.TutorialRegistry.getTutorial(tutorialId);
        } catch (RuntimeException unknownId) {
            return;
        }
        var icon = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                com.mohistmc.academy.AcademyCraft.MODID, "textures/tutorial/update_notify.png");
        com.mohistmc.academy.client.gui.NotifyOverlay.notify(
                net.minecraft.network.chat.Component.translatable("ac.tutorial.update").getString(),
                tutorial.getTitle(), icon);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new com.mohistmc.academy.api.event.TutorialEvents.Activated(mc.player, tutorialId));
    }
    public static void locationTeleport(LocationTeleportSyncPacket p) { LocationTeleportGui.accept(p.locations()); }
    public static void locationConsent(LocationConsentRequestPacket p) { Minecraft.getInstance().setScreen(new com.mohistmc.academy.client.gui.LocationConsentScreen(p)); }
    public static void freqTransmitter(FreqTransmitterStatePacket p) { FreqTransmitterGui.acceptServerState(p); }
    public static void nodeList(NodeListSyncPacket p) { AcademyBaseUI.receiveNodeList(p.data()); }
    public static void matrixNetworkList(MatrixNetworkListSyncPacket p) {
        AcademyBaseUI.receiveMatrixNetworkList(p.data());
    }
    public static void abilityInterferer(AbilityInterfererStatePacket p) {
        com.mohistmc.academy.client.block.gui.AbilityInterfererGui.acceptServerState(p);
    }
    public static void mineDetect(MineDetectResultPacket p) {
        if(Minecraft.getInstance().level==null)return;
        mineDetectSnapshot = p;
    }
    public static MineDetectResultPacket mineDetectSnapshot() { return mineDetectSnapshot; }
    public static void startTerminalInstall() { TerminalInstallProgress.start(); }
    public static void coinTossResult(byte side) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && com.mohistmc.academy.config.ACConfig.Client.headsOrTails()) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ac.headsOrTails." + side), false);
        }
    }
    public static void teleporterCritical(TeleporterCriticalPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var target = mc.level.getEntity(packet.targetEntityId());
        if (target == null || !target.isAlive()) return;
        int count = 5 + mc.level.random.nextInt(4);
        while (count-- > 0) {
            double angle = mc.level.random.nextDouble() * Math.PI * 2;
            double radius = target.getBbWidth() * (.5 + mc.level.random.nextDouble() * .2);
            double height = target.getBbHeight() * mc.level.random.nextDouble();
            mc.level.addParticle(com.mohistmc.academy.world.AcademyParticles.FORMULA.get(),
                    target.getX() + radius * Math.sin(angle), target.getY() + height,
                    target.getZ() + radius * Math.cos(angle),
                    (mc.level.random.nextDouble() * 2 - 1) * .03,
                    (mc.level.random.nextDouble() * 2 - 1) * .03,
                    (mc.level.random.nextDouble() * 2 - 1) * .03);
        }
    }
    public static void teleporterTrail(TeleporterTrailPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(
                packet.startX(), packet.startY(), packet.startZ());
        net.minecraft.world.phys.Vec3 delta = new net.minecraft.world.phys.Vec3(
                packet.endX()-packet.startX(),packet.endY()-packet.startY(),packet.endZ()-packet.startZ());
        double distance=delta.length();if(distance<1.0e-6||distance>128)return;
        net.minecraft.world.phys.Vec3 direction=delta.scale(1/distance),cursor=start;
        double move=1,travelled=move;int points=0;
        while(travelled<=distance&&points++<128){
            cursor=cursor.add(direction.scale(move));
            double horizontal=packet.kind()==TeleporterTrailPacket.SHIFT?.05:.02;
            mc.level.addParticle(com.mohistmc.academy.world.AcademyParticles.TELEPORT.get(),
                    cursor.x,cursor.y,cursor.z,
                    (mc.level.random.nextDouble()*2-1)*horizontal,
                    -.02+mc.level.random.nextDouble()*.07,
                    (mc.level.random.nextDouble()*2-1)*horizontal);
            move=packet.kind()==TeleporterTrailPacket.SHIFT
                    ?.6+mc.level.random.nextDouble()*.4:1+mc.level.random.nextDouble();
            travelled+=move;
        }
    }
    public static void charging(SyncChargingStatePacket p) {
        boolean applied=KeyInputHandler.acceptChargingState(p.slotIndex(), p.skillId(), p.epoch(), p.generation(), p.accepted(), p.ticks());
        if (applied) ChargingHudOverlay.setChargingState(p.accepted() ? p.ticks() : -1, p.maxTicks(), p.skillId());
    }
    public static void flashing(FlashingStatePacket p) {
        boolean wasActive = FLASHING.active();
        if (FLASHING.accept(p.active(), p.epoch()) || wasActive != FLASHING.active())
            KeyInputHandler.setFlashingActive(FLASHING.active());
    }
}
