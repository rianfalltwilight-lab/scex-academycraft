package com.mohistmc.academy.client.gate;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.client.SkillInputClientState;
import com.mohistmc.academy.gametest.recheck.RecheckSessionState;
import com.mohistmc.academy.network.*;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Normal key edges use the production input handler. Explicit attack stages send existing payloads over C2S. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class RecheckSessionClientGate {
    private static int previous = -1, age, ticks, actedAt, stoppingWait;
    private static boolean acted, captured, connecting, finished, restartVerified;
    private static volatile boolean screenshotDone;
    private static NodeConfigPacket oldMenuPacket;
    private RecheckSessionClientGate() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!RecheckSessionState.enabled() || finished) return;
        String role = RecheckSessionState.role();
        if (!role.equals("a") && !role.equals("b")) return;
        var mc = Minecraft.getInstance();
        try {
            boolean restart = Boolean.getBoolean("academy.recheckSessionRestart");
            if (!RecheckSessionState.read(restart ? "restart-result.txt" : "server-result.txt").isBlank()) {
                releaseKeys();
                // A result is written before halt. Do not race an explicit client logout
                // against the server's player save and accidentally mask missing cleanup.
                if (!RecheckSessionState.read(restart ? "restart-stopped.txt" : "server-stopped.txt").isBlank()) {
                    log(role, "server stopped marker observed; client exits after save");
                    finished = true; mc.stop(); return;
                }
                if (++stoppingWait > 20 * 60) throw new IllegalStateException("server stopped marker missing after result");
                return;
            }
            if (++ticks > 20 * 480) throw new IllegalStateException("client overall timeout");
            if (restart) {
                if (mc.player != null && mc.level != null && mc.player.tickCount > 20 && !restartVerified) {
                    if (role.equals("a")) RecheckClientPersistenceProbe.verify("RESTART");
                    restartVerified = true; RecheckSessionState.write(role + "-restart-ready.txt", "ready");
                }
                return;
            }
            String raw = RecheckSessionState.read("stage.txt"); if (raw.isBlank()) return;
            int stage = Integer.parseInt(raw);
            if (stage != previous) { previous = stage; age = 0; actedAt = 0; acted = false; captured = false; screenshotDone = false; connecting = false; }
            if (++age > 20 * 120) throw new IllegalStateException("client stage timeout " + stage + " screen=" + mc.screen);
            if (RecheckSessionState.ack(role, stage)) return;
            if (stage == 11 && role.equals("a") && mc.player == null) {
                if (!connecting && age > 60) {
                    String address = System.getProperty("academy.recheckSessionAddress", "localhost:25619");
                    ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                            new ServerData("Isolated recheck", address, ServerData.Type.OTHER), false, null);
                    connecting = true;
                    log(role, "same-JVM reconnect requested after local reset held=" + KeyInputHandler.isSkillHeld(2));
                }
                return;
            }
            if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            if (!acted) {
                switch (stage) {
                    case 1 -> {
                        if (!(mc.player.containerMenu instanceof BaseNodeMenu menu) || !menu.actionSessionReady()) return;
                        oldMenuPacket = new NodeConfigPacket(menu.nextActionToken(), menu.pos, Optional.of("Recheck-old-login"), Optional.empty());
                        log(role, "captured first-login menu session=" + oldMenuPacket.actionToken().session()); acted = true;
                    }
                    case 2, 5 -> {
                        if (!SkillInputClientState.ready() || mc.screen != null || !"paper_drill".equals(mc.player.getData(AcademyAttachments.PLAYER_ABILITY).getCurrentPreset().getSlot(2)) || age < 15) return;
                        KeyInputHandler.getSkillKeys()[2].setDown(true);
                        log(role, "normal KeyMapping press slot=2"); acted = true;
                    }
                    case 3 -> { KeyInputHandler.getSkillKeys()[2].setDown(false); log(role, "normal KeyMapping release slot=2"); acted = true; }
                    case 4 -> {
                        if (role.equals("a")) {
                            long generation = state("first-a")[1];
                            PacketDistributor.sendToServer(new SkillKeyDownPacket(2, token("first-a")));
                            PacketDistributor.sendToServer(new SkillKeyDownPacket(2, token("first-a")));
                        } else PacketDistributor.sendToServer(new SkillKeyDownPacket(2, new SkillInputToken(token("first-b").session(), -1)));
                        PacketDistributor.sendToServer(new SkillKeyDownPacket(-1, SkillInputClientState.next()));
                        PacketDistributor.sendToServer(new SkillKeyDownPacket(Integer.MAX_VALUE, new SkillInputToken(SkillInputClientState.next().session(), Long.MAX_VALUE)));
                        log(role, "adversarial repeated/negative-generation/invalid-slot C2S sent"); acted = true;
                    }
                    case 6 -> {
                        if (role.equals("a")) {
                            PacketDistributor.sendToServer(new SkillKeyUpPacket(2, "paper_drill", state("second-b")[0]));
                            PacketDistributor.sendToServer(new SkillKeyUpPacket(2, "paper_drill", state("first-a")[0]));
                            PacketDistributor.sendToServer(new SkillKeyUpPacket(-1, "paper_drill", state("second-a")[0]));
                            log(role, "foreign B epoch / completed A epoch / wrong slot release C2S sent");
                        }
                        acted = true;
                    }
                    case 7 -> {
                        releaseKeys();
                        if (role.equals("a") && (mc.level.dimension() != Level.NETHER || mc.screen != null)) return;
                        if (KeyInputHandler.isSkillHeld(2)) return;
                        log(role, "dimension=" + mc.level.dimension().location() + " local charging=false"); acted = true;
                    }
                    case 8 -> {
                        if (role.equals("a")) {
                            PacketDistributor.sendToServer(new SkillKeyDownPacket(2, token("second-a")));
                            PacketDistributor.sendToServer(new SkillKeyUpPacket(2, "paper_drill", state("second-a")[0]));
                            log(role, "old overworld press/release replayed in Nether");
                        }
                        acted = true;
                    }
                    case 9 -> {
                        if (role.equals("a")) {
                            if (!SkillInputClientState.ready() || mc.level.dimension() != Level.OVERWORLD || mc.screen != null || age < 20
                                    || !"flying".equals(mc.player.getData(AcademyAttachments.PLAYER_ABILITY).getCurrentPreset().getSlot(0))) return;
                            KeyInputHandler.getSkillKeys()[0].setDown(true); log(role, "normal flying press slot=0");
                        }
                        acted = true;
                    }
                    case 10 -> {
                        releaseKeys();
                        if (role.equals("a")) {
                            log(role, "normal disconnect requested UUID=" + mc.player.getUUID());
                            RecheckSessionState.write(role + "-ack.txt", raw);
                            mc.disconnect(new TitleScreen(), false); return;
                        }
                        acted = true;
                    }
                    case 11 -> {
                        if (mc.player.tickCount < 20 || mc.screen != null) return;
                        if (role.equals("a")) RecheckClientPersistenceProbe.verify("RECONNECT");
                        log(role, "rejoined UUID=" + mc.player.getUUID() + " local charging=" + KeyInputHandler.isSkillHeld(2)
                                + " mayfly=" + mc.player.getAbilities().mayfly + " flying=" + mc.player.getAbilities().flying);
                        if (KeyInputHandler.isSkillHeld(2)) throw new IllegalStateException("local charging survived reconnect");
                        acted = true;
                    }
                    case 12 -> {
                        if (!(mc.player.containerMenu instanceof BaseNodeMenu menu) || !menu.actionSessionReady()) return;
                        if (role.equals("a")) {
                            var low = menu.nextActionToken(); var high = menu.nextActionToken();
                            PacketDistributor.sendToServer(new NodeConfigPacket(high, menu.pos, Optional.of("Recheck-high"), Optional.empty()));
                            PacketDistributor.sendToServer(new NodeConfigPacket(low, menu.pos, Optional.of("Recheck-low"), Optional.empty()));
                            PacketDistributor.sendToServer(new NodeConfigPacket(high, menu.pos, Optional.of("Recheck-duplicate"), Optional.empty()));
                            PacketDistributor.sendToServer(new NodeConfigPacket(new MenuActionToken(menu.containerId, UUID.randomUUID(), Long.MAX_VALUE),
                                    menu.pos, Optional.of("Recheck-forged"), Optional.empty()));
                            PacketDistributor.sendToServer(oldMenuPacket);
                        } else PacketDistributor.sendToServer(new NodeConfigPacket(menu.nextActionToken(), menu.pos, Optional.of("Recheck-nonowner"), Optional.empty()));
                        log(role, "real C2S reconnect-menu replay/order/owner attack sent"); acted = true;
                    }
                    case 13 -> {
                        if (role.equals("a")) {
                            if (age < 20 || mc.screen != null || mc.player.getData(AcademyAttachments.PLAYER_ABILITY).getCurrentCp() < 4500) return;
                            KeyInputHandler.getSkillKeys()[0].setDown(true); log(role, "normal flying press before server stop slot=0");
                        } else releaseKeys();
                        acted = true;
                    }
                    default -> throw new IllegalStateException("unknown stage " + stage);
                }
            }
            if (acted && actedAt == 0) actedAt = age;
            if ((stage == 9 || stage == 13) && role.equals("a") && age >= actedAt + 3) KeyInputHandler.getSkillKeys()[0].setDown(false);
            int settle = stage == 2 || stage == 5 ? 35 : stage == 9 || stage == 13 ? 35 : stage == 3 || stage == 12 ? 25 : 15;
            if (age < settle || !acted) return;
            if ((stage == 2 || stage == 5) && age < actedAt + 20 || (stage == 9 || stage == 13) && age < actedAt + 15) return;
            if (stage == 2 || stage == 5) { if (!KeyInputHandler.isSkillHeld(2)) return; }
            if (!captured && (stage == 2 || stage == 3 || stage == 7 || stage == 9 || stage == 11 || stage == 12 || stage == 13)) {
                captured = true;
                String name = String.format("%02d-%s.png", stage, role);
                Screenshot.grab(RecheckSessionState.root().toFile(), name, mc.getMainRenderTarget(), message -> screenshotDone = true);
                return;
            }
            if (captured && !screenshotDone) return;
            RecheckSessionState.write(role + "-ack.txt", raw);
        } catch (Throwable error) {
            RecheckSessionState.write(role + "-failure.txt", "stage=" + previous + "\nreason=" + error);
            finished = true; releaseKeys(); mc.stop();
        }
    }
    private static SkillInputToken token(String name) { var fields = RecheckSessionState.read(name + ".txt").split(","); return new SkillInputToken(UUID.fromString(fields[2]), Long.parseLong(fields[1])); }
    private static long[] state(String name) { var fields = RecheckSessionState.read(name + ".txt").split(","); return new long[]{Long.parseLong(fields[0]), Long.parseLong(fields[1])}; }
    private static void releaseKeys() { for (var key : KeyInputHandler.getSkillKeys()) key.setDown(false); }
    private static void log(String role, String value) { RecheckSessionState.append(role + "-evidence.txt", "stage=" + previous + " " + value); }
}