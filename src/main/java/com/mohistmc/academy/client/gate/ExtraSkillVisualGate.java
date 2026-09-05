package com.mohistmc.academy.client.gate;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.gametest.ExtraSkillGateFixture;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Default-inert isolated client gate for the 23 Extra skills, plus ordinary throwing ammunition.
 * Active skills use production KeyInputHandler edges. Four passives use explicitly labeled
 * server environments. Learned level/proficiency are test fixtures, not survival progression.
 * Captures require subsequent visual inspection; successful file writes do not establish fidelity.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class ExtraSkillVisualGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private enum Stage { WAIT_WORLD, PREPARE, WAIT_PREPARE, SYNC, WAIT_ARM, DOWN, OBSERVE, OFF_DOWN, WAIT_OFF, WAIT_FINISH, FINISHED }
    private static Stage stage = Stage.WAIT_WORLD;
    private static int ticks, age, worldTicks, index, activatedAt = -1;
    private static long began = System.nanoTime();
    private static volatile boolean busy;
    private static volatile String failure, serverLine;
    private static boolean early, middle, burst, end;
    private static String pendingCapture;
    private static volatile boolean captured;
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private ExtraSkillVisualGate() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ExtraSkillGateFixture.PROPERTY) || stage == Stage.FINISHED) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++; age++;
        try {
            if (failure != null) throw new IllegalStateException(failure);
            finishCapture(mc);
            if (stage == Stage.WAIT_WORLD) {
                if ((System.nanoTime() - began) / 1_000_000 > 180_000) throw new IllegalStateException("integrated world startup timeout");
                if (mc.player == null || mc.level == null || mc.getOverlay() != null || mc.getSingleplayerServer() == null) return;
                if (++worldTicks < 120) return; // Exceed actual fresh-player damage protection before any scenario.
                if (mc.screen != null) mc.player.closeContainer();
                evidence("fixture=isolated integrated survival player; level5/prelearned skills; devMode=false; not an unlock/progression proof");
                evidence("input=programmatically injected KeyMapping state handled by normal client key edges, not physical keyboard operation");
                enter(Stage.PREPARE); return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null)
                throw new IllegalStateException("integrated player/world disappeared");
            if (age > 20 * 35) throw new IllegalStateException("stage timeout " + stage + " case=" + id() + " observed=" + ExtraSkillGateFixture.observation());
            // Slam captures its launch direction on the server. Looking at the already launched
            // target afterward changes only the observation, and keeps its real lift/fall in frame.
            if (id().equals("psycho_slam") && (stage == Stage.OBSERVE || stage == Stage.WAIT_OFF)) {
                var observed = ExtraSkillGateFixture.observation();
                if (observed != null && observed.activated() && observed.targetCenter() != null) {
                    var delta = observed.targetCenter().subtract(mc.player.getEyePosition());
                    mc.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                    mc.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
                }
            }
            // Observe even while the ordinary 3-tick press is down. Waiting until OBSERVE
            // missed short ray visuals; one client tick after confirmation permits a rendered frame.
            if (stage == Stage.DOWN || stage == Stage.OBSERVE) {
                var current = ExtraSkillGateFixture.observation();
                if (current != null && current.testCase().equals(id()) && current.activated()) {
                    if (activatedAt < 0) activatedAt = ticks;
                    if (!early && ticks > activatedAt && pendingCapture == null) {
                        early = true; capture(mc, "early", current.metrics());
                    }
                }
            }
            switch (stage) {
                case PREPARE -> {
                    release(mc);
                    if (mc.screen != null) mc.player.closeContainer();
                    early = middle = burst = end = false; activatedAt = -1; serverLine = null;
                    mc.options.setCameraType(selfView(id()) ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
                    server(mc, player -> ExtraSkillGateFixture.prepare(player, id())); enter(Stage.WAIT_PREPARE);
                }
                case WAIT_PREPARE -> { if (!busy) enter(Stage.SYNC); }
                case SYNC -> {
                    var data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
                    String skill = ExtraSkillGateFixture.skill(id());
                    if (age < 20 || mc.screen != null || mc.getOverlay() != null
                            || data.getCurrentAbility() != ExtraSkillGateFixture.category(id())
                            || !data.isAbilityActive() || data.isDevMode() || !data.hasLearnedSkill(skill)
                            || !ExtraSkillGateFixture.passive(id()) && !skill.equals(data.getSlotSkillId(0, slot()))) return;
                    mc.player.setYRot(0); mc.player.setXRot(id().equals("air_jet") ? -20 : 0);
                    evidence("SYNC " + id() + " category=" + data.getCurrentAbility() + " level=" + data.getPlayerLevel()
                            + " slot=" + (ExtraSkillGateFixture.passive(id()) ? "passive" : slot()) + " skill=" + skill);
                    server(mc, ExtraSkillGateFixture::arm); enter(Stage.WAIT_ARM);
                }
                case WAIT_ARM -> {
                    if (busy) return;
                    if (ExtraSkillGateFixture.passive(id())) enter(Stage.OBSERVE);
                    else { key().setDown(true); enter(Stage.DOWN); }
                }
                case DOWN -> {
                    if (age < 3) return;
                    // Paper Drill stays held through paid server acknowledgement and sampled pulses.
                    if (!id().equals("paper_drill")) key().setDown(false);
                    enter(Stage.OBSERVE);
                }
                case OBSERVE -> {
                    var observed = ExtraSkillGateFixture.observation();
                    if (observed == null || !observed.testCase().equals(id())) return;
                    if (observed.failure() != null) throw new IllegalStateException(observed.failure());
                    if (id().equals("flying") && observed.sustained()) mc.options.keyJump.setDown(true);

                    boolean extended = ExtraSkillGateFixture.duration(id()) >= 35 || ExtraSkillGateFixture.toggle(id()) || id().equals("paper_drill");
                    if (early && extended && !middle && observed.age() >= (id().equals("paper_drill") ? 15 : 25) && pendingCapture == null) {
                        middle = true; capture(mc, "middle", observed.metrics());
                    }
                    if (id().equals("storm_core") && !burst && observed.age() >= 84 && pendingCapture == null) {
                        burst = true; capture(mc, "burst", observed.metrics());
                    }
                    if (!observed.activated() || !early || observed.age() < ExtraSkillGateFixture.duration(id()) || pendingCapture != null) return;
                    if (id().equals("paper_drill")) {
                        require(observed.charging(), "Paper Drill ended before the requested key release");
                        key().setDown(false); enter(Stage.WAIT_OFF);
                    } else if (ExtraSkillGateFixture.toggle(id())) {
                        mc.options.keyJump.setDown(false); key().setDown(true); enter(Stage.OFF_DOWN);
                    } else enter(Stage.WAIT_OFF);
                }
                case OFF_DOWN -> { if (age >= 3) { key().setDown(false); enter(Stage.WAIT_OFF); } }
                case WAIT_OFF -> {
                    if (age < 5) return;
                    var observed = ExtraSkillGateFixture.observation();
                    if (observed == null || observed.failure() != null) throw new IllegalStateException("observation failed: " + observed);
                    if (ExtraSkillGateFixture.toggle(id()) && observed.sustained() || id().equals("paper_drill") && observed.charging()) return;
                    if (!end && pendingCapture == null) {
                        end = true; capture(mc, "end", observed.metrics()); return;
                    }
                    if (pendingCapture != null) return;
                    server(mc, player -> serverLine = ExtraSkillGateFixture.finish(player)); enter(Stage.WAIT_FINISH);
                }
                case WAIT_FINISH -> {
                    if (busy) return;
                    require(serverLine != null && serverLine.startsWith("PASS "), "server verdict absent");
                    evidence(serverLine); index++;
                    if (index == ExtraSkillGateFixture.CASES.size()) {
                        release(mc); writeResult(mc, "PASS", null); stage = Stage.FINISHED; mc.stop();
                    } else enter(Stage.PREPARE);
                }
                default -> { }
            }
        } catch (Throwable problem) {
            LOGGER.error("Extra skill visual gate failed at {} / {}", stage, index, problem);
            release(mc); writeResult(mc, "FAIL", problem.toString()); stage = Stage.FINISHED; mc.stop();
        }
    }
    private static String id() { return ExtraSkillGateFixture.CASES.get(index); }
    private static int slot() { return id().equals("paper_drill") ? 0 : 2; }
    private static KeyMapping key() { return slot() == 0 ? KeyInputHandler.SKILL_1 : KeyInputHandler.SKILL_3; }
    private static boolean selfView(String id) {
        return List.of("ascending_air", "airflow", "air_cooling", "air_jet", "offense_armour", "flying",
                "aero_separator", "insulation", "overload_thinking", "perfect_paper", "psycho_harden", "liquid_shadow").contains(id);
    }
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        require(!busy, "overlapping fixture operations"); busy = true;
        UUID uuid = mc.player.getUUID(); var integrated = mc.getSingleplayerServer();
        require(integrated != null, "no integrated server");
        integrated.execute(() -> {
            try {
                ServerPlayer player = integrated.getPlayerList().getPlayer(uuid);
                require(player != null, "server player disappeared"); action.accept(player);
            } catch (Throwable problem) { failure = problem.toString(); LOGGER.error("Extra fixture operation failed", problem); }
            finally { busy = false; }
        });
    }
    private static void capture(Minecraft mc, String moment, String metrics) throws Exception {
        String name = String.format(java.util.Locale.ROOT, "extra-%02d-%s-%s.png", index + 1, id(), moment);
        Path expected = mc.gameDirectory.toPath().resolve("screenshots").resolve(name);
        require(!Files.exists(expected), "capture path exists; use a fresh isolated game directory: " + name);
        pendingCapture = name; captured = false;
        evidence("CAPTURE_REQUEST " + name + " clientTick=" + ticks + " " + metrics);
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> captured = true);
    }
    private static void finishCapture(Minecraft mc) {
        if (pendingCapture == null || !captured) return;
        Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve(pendingCapture);
        require(Files.isRegularFile(file), "screenshot callback completed without file: " + pendingCapture);
        CAPTURES.add(pendingCapture); pendingCapture = null;
    }
    private static void enter(Stage next) { stage = next; age = 0; LOGGER.info("Extra skill gate -> {} case {}", next, index); }
    private static void evidence(String line) { EVIDENCE.add(line); LOGGER.info("Extra skill gate evidence: {}", line); }
    private static void release(Minecraft mc) {
        KeyInputHandler.SKILL_1.setDown(false); KeyInputHandler.SKILL_3.setDown(false);
        if (mc.options != null) mc.options.keyJump.setDown(false);
    }
    private static void writeResult(Minecraft mc, String status, String reason) {
        try {
            List<String> lines = new ArrayList<>(); lines.add("status=" + status);
            lines.add("scope=real client input/environment server assertions and rendered frame capture");
            lines.add("visualInspection=REQUIRED; screenshots alone do not prove visible effects or original-animation fidelity");
            lines.add("progression=PRELEARNED FIXTURE; no survival unlock proof");
            lines.add("completedCases=" + index + "/" + ExtraSkillGateFixture.CASES.size());
            lines.add("stage=" + stage); if (reason != null) lines.add("reason=" + reason);
            lines.add("screenshots=" + CAPTURES.size()); lines.addAll(EVIDENCE);
            Files.write(mc.gameDirectory.toPath().resolve("academy-extra-skill-gate-result.txt"), lines, StandardCharsets.UTF_8);
        } catch (Exception writeFailure) { LOGGER.error("Unable to write Extra gate result", writeFailure); }
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
}