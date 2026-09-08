package com.mohistmc.academy.client.gate;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.block.gui.BaseNodeGui;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.gametest.NodeConfigGateState;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.menu.AcademyMenu;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Opt-in two-socket-client probe. Inputs use real screen hit boxes, charTyped and Enter.
 * Reflection reads layout/state only, except stage 12's explicitly stale client BE fixture.
 * Missing new menu accessors fail their case instead of making the 0.0.19 baseline uncompilable.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class NodeConfigClientGate {
    private static final BlockPos NODE = new BlockPos(0, 81, 0);
    private static final BlockPos PHASE = new BlockPos(3, 81, 0);
    private static final String FIRST_NAME = "123", SECOND_NAME = "节点-乙20";
    private static final String FIRST_PASS = "口令甲20", SECOND_PASS = "口令乙20", FINAL_PASS = "口令终20";
    private static int currentStage = -1, age, actionAt, actionStep, captureAge, stoppedWait, stage12Renders;
    private static boolean acted, completed, captureStarted, markerChecked, disabled, finished;
    private static boolean staleMirrorInjected, missingStopReported;
    private static volatile int screenshotCompletedStage = -1;
    private static int passwordStep, passwordAge;
    private static String failure, lastObservation = "", screenshotName;
    private NodeConfigClientGate() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (disabled || finished || !Boolean.getBoolean("academy.nodeConfigGate") || !NodeConfigGateState.enabled()) return;
        String role = System.getProperty("academy.nodeConfigRole", "");
        boolean restart = Boolean.getBoolean("academy.nodeConfigRestart");
        if (!role.equals("a") && !role.equals("b") || restart && !role.equals("a")) return;
        Minecraft mc = Minecraft.getInstance();
        // An invalid root never creates coordination data or enables an acceptance probe.
        if (!markerChecked) {
            try {
                if (!Files.isRegularFile(NodeConfigGateState.root().resolve("ISOLATED-ACCEPTANCE")))
                    throw new IllegalStateException("ISOLATED-ACCEPTANCE marker missing");
                markerChecked = true;
                log(role, "START restart=" + restart + " gameDirectory=" + mc.gameDirectory);
            } catch (Throwable invalidRoot) {
                disabled = true;
                System.err.println("[NodeConfigClientGate] disabled: " + invalidRoot);
                return;
            }
        }
        try {
            String resultFile = restart ? "restart-result.txt" : "server-result.txt";
            String stoppedFile = restart ? "restart-stopped.txt" : "server-stopped.txt";
            if (!NodeConfigGateState.read(resultFile).isBlank()) {
                // Do not logout on result: the real server must save and complete stop first.
                if (!NodeConfigGateState.read(stoppedFile).isBlank()) {
                    log(role, "STOP observed=" + stoppedFile + "; exiting after server save");
                    finished = true;
                    mc.stop();
                } else if (++stoppedWait > 1200 && !missingStopReported) {
                    missingStopReported = true;
                    NodeConfigGateState.append(failureFile(role), "STOP marker timeout; client remains connected until marker\n");
                }
                return;
            }
            String raw = NodeConfigGateState.read("stage.txt").trim();
            int stage;
            try { stage = Integer.parseInt(raw); } catch (NumberFormatException notReady) { return; }
            if (stage < 1 || stage > 14 || restart && stage != 14 || !restart && stage == 14) return;
            if (stage != currentStage) begin(role, stage);
            if (NodeConfigGateState.read(role + "-ack.txt").trim().equals(raw)) return;
            ++age;
            if (stage == 12 && role.equals("a")) maintainStaleMirror(mc);
            if (!completed) {
                try {
                    if (mc.player != null && mc.level != null && mc.getOverlay() == null && age >= 5) {
                        boolean valid = role.equals("a") ? ownerStep(mc, stage) : deviceStep(mc, stage);
                        lastObservation = describe(mc, role);
                        if (valid && age >= 30 && (!acted || age - actionAt >= 25)
                                && (stage != 12 || !role.equals("a") || stage12Renders >= 3)) completed = true;
                    }
                    if (!completed && age >= 120)
                        throw new IllegalStateException("120 tick stage timeout; inputAttempted=" + acted + "; " + lastObservation);
                } catch (Throwable caseFailure) {
                    failure = caseFailure.toString();
                    completed = true;
                }
            }
            if (completed && age >= 30) finishStage(mc, role, raw);
        } catch (Throwable infrastructureFailure) {
            // Case-level faults must not terminate the server or prevent later stages.
            NodeConfigGateState.append(failureFile(role), "stage=" + currentStage + " infrastructure=" + infrastructureFailure + "\n");
            if (currentStage > 0) NodeConfigGateState.write(role + "-ack.txt", Integer.toString(currentStage));
        }
    }

    private static void begin(String role, int stage) {
        currentStage = stage; passwordStep = 0; passwordAge = 0; age = 0; actionAt = 0; actionStep = 0; captureAge = 0; stage12Renders = 0;
        acted = false; completed = false; captureStarted = false; staleMirrorInjected = false;
        screenshotCompletedStage = -1; failure = null; lastObservation = "waiting for screen";
        screenshotName = "node-config-" + role + "-stage-" + String.format("%02d", stage) + ".png";
        log(role, "BEGIN stage=" + stage);
    }

    /** Hold an artificial old mirror through actual render frames, including screenshot completion. */
    private static boolean maintainStaleMirror(Minecraft mc) throws Exception {
        if (mc.level == null || !(mc.level.getBlockEntity(NODE) instanceof BaseNodeBlockEntity node)) return false;
        setField(node, "nodeName", "Unnamed");
        Field revision = optionalField(node.getClass(), "configRevision");
        if (revision != null) {
            if (revision.getType() == long.class) revision.setLong(node, 0L);
            else if (revision.getType() == int.class) revision.setInt(node, 0);
            else throw new IllegalStateException("unexpected configRevision field type " + revision.getType());
        }
        if (!staleMirrorInjected) {
            staleMirrorInjected = true;
            log("a", "stage=12 start holding ONLY client BE name=Unnamed/revision=0 each tick and Render.Pre through screenshot; artificial stale-mirror fixture");
        }
        return true;
    }
    private static boolean staleRenderActive() {
        return markerChecked && !finished && !disabled && currentStage == 12
                && Boolean.getBoolean("academy.nodeConfigGate") && System.getProperty("academy.nodeConfigRole", "").equals("a")
                && !NodeConfigGateState.read("a-ack.txt").trim().equals("12");
    }
    @SubscribeEvent public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!staleRenderActive() || !(event.getScreen() instanceof BaseNodeGui<?>)) return;
        try { maintainStaleMirror(Minecraft.getInstance()); }
        catch (Exception fixtureFailure) { failure = "stale mirror render fixture: " + fixtureFailure; completed = true; }
    }
    @SubscribeEvent public static void afterRender(ScreenEvent.Render.Post event) {
        if (staleRenderActive() && event.getScreen() instanceof BaseNodeGui<?>) ++stage12Renders;
    }
    private static boolean ackSettled(BaseNodeGui<?> gui, boolean name) throws Exception {
        Field pending = optionalField(gui.getClass(), name ? "pendingNameToken" : "pendingPasswordToken");
        // The baseline predates receipts. Record unavailable in evidence without inventing an auth failure.
        return pending == null || pending.get(gui) == null
                && !Boolean.TRUE.equals(field(gui, name ? "nodeNameEdited" : "passwordEdited"));
    }
    private static String ackDescription(BaseNodeGui<?> gui, boolean name) throws Exception {
        Field pending = optionalField(gui.getClass(), name ? "pendingNameToken" : "pendingPasswordToken");
        return pending == null ? "UNAVAILABLE" : "pending=" + (pending.get(gui) != null)
                + ",dirty=" + field(gui, name ? "nodeNameEdited" : "passwordEdited");
    }
    private static boolean ownerStep(Minecraft mc, int stage) throws Exception {
        if (!(mc.screen instanceof BaseNodeGui<?> gui) || !(mc.player.containerMenu instanceof BaseNodeMenu menu)
                || !NODE.equals(menu.pos) || gui.getMenu() != menu || !menu.actionSessionReady()) return false;
        if (!acted) {
            switch (stage) {
                case 1 -> edit(gui, menu, true, FIRST_NAME);
                case 2 -> edit(gui, menu, false, FIRST_PASS);
                case 5 -> edit(gui, menu, true, SECOND_NAME);
                case 6 -> edit(gui, menu, false, SECOND_PASS);
                case 10 -> edit(gui, menu, false, "");
                case 13 -> edit(gui, menu, false, FINAL_PASS);
                case 12 -> { if (!maintainStaleMirror(mc)) return false; }
                default -> { }
            }
            acted = true; actionAt = age;
        }
        if ((stage == 1 || stage == 5) && !ackSettled(gui, true)) return false;
        if ((stage == 2 || stage == 6 || stage == 10 || stage == 13) && !ackSettled(gui, false)) return false;
        String expected = stage >= 5 ? SECOND_NAME : FIRST_NAME;
        if (!expected.equals(menu.getCurrentNodeName()) || !expected.equals(field(gui, "nodeNameInput").toString())) return false;
        return switch (stage) {
            case 2, 6, 13, 14 -> hasPassword(menu);
            case 10 -> !hasPassword(menu);
            default -> true;
        };
    }

    private static boolean deviceStep(Minecraft mc, int stage) throws Exception {
        if (!(mc.screen instanceof AcademyBaseUI<?> gui) || !(mc.player.containerMenu instanceof AcademyMenu menu)
                || !PHASE.equals(menu.pos) || gui.getMenu() != menu || !menu.actionSessionReady()) return false;
        if (stage <= 2) return true;
        if (!Boolean.TRUE.equals(field(gui, "panelActive"))) {
            // Stage 3 opens the actual wireless selector once. Later stages must retain this page.
            if (stage != 3) throw new IllegalStateException("device wireless page unexpectedly closed");
            gui.mouseClicked(number(invoke(gui, "getSidebarLeft")) + 9.0,
                    number(invoke(gui, "getSidebarTop")) + 29.0, 0);
            return false;
        }
        NodeView node = nodeView(gui);
        if (node == null || !Boolean.TRUE.equals(field(gui, "nodeResponseReceived"))) return false;
        String expected = stage >= 5 ? SECOND_NAME : FIRST_NAME;
        boolean nameMatches = expected.equals(node.name);
        boolean connected = isConnected(gui, node);
        switch (stage) {
            case 3, 4, 8, 9 -> {
                if (!node.needAuth) return false;
                if (!acted) {
                    // A wrong-password attempt has an intentional 20 tick authentication backoff.
                    if (age < 30) return false;
                    if (connected) throw new IllegalStateException("connect precondition: device already connected");
                    String pass = stage == 3 ? "wrong" : stage == 8 ? FIRST_PASS : stage == 4 ? FIRST_PASS : SECOND_PASS;
                    if (!connect(gui, menu, node, pass)) return false;
                    acted = true; actionAt = age;
                    return false;
                }
                if (age - actionAt < 25 || !Boolean.TRUE.equals(field(gui, "nodeResponseReceived"))) return false;
                return nameMatches && (stage == 3 || stage == 8 ? !connected : connected);
            }
            case 7 -> {
                if (!acted) {
                    if (!connected) throw new IllegalStateException("disconnect precondition: device is not connected");
                    disconnect(gui, menu); acted = true; actionAt = age; return false;
                }
                return nameMatches && !connected;
            }
            case 11 -> {
                if (node.needAuth) return false;
                if (actionStep == 0) {
                    if (!connected) throw new IllegalStateException("public reconnect precondition: device is not connected");
                    disconnect(gui, menu); acted = true; actionAt = age; actionStep = 1; return false;
                }
                if (actionStep == 1) {
                    if (connected || age - actionAt < 15) return false;
                    if (!connect(gui, menu, node, null)) return false;
                    actionAt = age; actionStep = 2; return false;
                }
                return nameMatches && connected;
            }
            case 5, 6 -> { return nameMatches && connected && node.needAuth; }
            case 10 -> { return nameMatches && connected && !node.needAuth; }
            case 12 -> { return nameMatches; }
            case 13 -> { return nameMatches && connected && node.needAuth; }
            default -> throw new IllegalStateException("unexpected device stage " + stage);
        }
    }

    private static void edit(BaseNodeGui<?> gui, BaseNodeMenu menu, boolean name, String value) throws Exception {
        int panelX = number(field(gui, "infoPanelX"));
        if (panelX == Integer.MIN_VALUE) throw new IllegalStateException("node property panel has not rendered");
        int top = number(field(gui, "topPos"));
        float row = ((Number) field(gui, name ? "nameRowY" : "passwordRowY")).floatValue();
        String focus = name ? "NAME" : "PASSWORD";
        gui.mouseClicked(panelX + 50.0, top + InfoArea.Y + Math.round(row) + 4.0, 0);
        check(focus.equals(field(gui, "editFocus").toString()), "actual property click did not focus " + focus);
        StringBuilder input = (StringBuilder) field(gui, name ? "nodeNameInput" : "nodePasswordInput");
        for (int i = 0; !input.isEmpty() && i < 128; i++) gui.keyPressed(259, 0, 0);
        check(input.isEmpty(), "Backspace did not clear property");
        for (int i = 0; i < value.length(); i++) check(gui.charTyped(value.charAt(i), 0), "charTyped rejected index " + i);
        check(input.toString().equals(value), "draft differs before Enter");
        long before = outgoing(menu);
        check(gui.keyPressed(257, 0, 0), "Enter not consumed");
        check(outgoing(menu) == before + 1, "Enter did not send exactly one real menu action");
        log("a", "stage=" + currentStage + " actual Enter property=" + focus + " chars=" + value.length()
                + " sequence=" + (before + 1) + " menu=" + menu.containerId);
    }

    private static boolean connect(AcademyBaseUI<?> gui, AcademyMenu menu, NodeView target, String password) throws Exception {
        List<?> nodes = (List<?>) field(gui, "serverNodes");
        int active = number(field(gui, "activeNode")), rank = 0;
        for (int i = 0; i < target.index; i++) if (i != active) ++rank;
        int offset = number(field(gui, "nodePageOffset"));
        int left = number(field(gui, "leftPos")), top = number(field(gui, "topPos"));
        if (rank < offset || rank >= offset + 8) {
            gui.mouseClicked(left + 160.0, top + (rank < offset ? 70.0 : 161.0), 0);
            return false;
        }
        check(target.index < nodes.size(), "node disappeared before click");
        long before = outgoing(menu);
        if (!target.needAuth || passwordStep == 0) {
            gui.mouseClicked(left + 143.0, top + 70.0 + (rank - offset) * 13, 0);
            if (target.needAuth) { passwordStep=1;passwordAge=age;return false; }
        }
        if (target.needAuth) {
            check(password != null, "password required for protected row");
            check(number(field(gui, "waitPass")) == target.index, "actual connection icon did not open target password input");
            if(age-passwordAge<8)return false;
            if(passwordStep==1){
                Screenshot.grab(Minecraft.getInstance().gameDirectory,"password-empty-stage-"+currentStage+".png",Minecraft.getInstance().getMainRenderTarget(),message->{});
                for (int i = 0; i < password.length(); i++) check(gui.charTyped(password.charAt(i), 0), "password charTyped rejected");
                passwordStep=2;passwordAge=age;return false;
            }
            Screenshot.grab(Minecraft.getInstance().gameDirectory,"password-filled-stage-"+currentStage+".png",Minecraft.getInstance().getMainRenderTarget(),message->{});
            check(gui.keyPressed(257, 0, 0), "connect Enter not consumed");
        } else check(password == null, "protected test unexpectedly used a public node");
        check(outgoing(menu) == before + 1, "connection UI did not send exactly one real action");
        log("b", "stage=" + currentStage + " actual connect " + (target.needAuth ? "password Enter" : "public icon")
                + " node=" + target.pos + " sequence=" + (before + 1));
        return true;
    }

    private static void disconnect(AcademyBaseUI<?> gui, AcademyMenu menu) throws Exception {
        long before = outgoing(menu);
        gui.mouseClicked(number(field(gui, "leftPos")) + 149.0, number(field(gui, "topPos")) + 44.0, 0);
        check(outgoing(menu) == before + 1, "disconnect icon did not send exactly one real action");
        log("b", "stage=" + currentStage + " actual disconnect icon sequence=" + (before + 1));
    }

    private record NodeView(int index, String name, boolean needAuth, BlockPos pos) {}
    private static NodeView nodeView(AcademyBaseUI<?> gui) throws Exception {
        List<?> nodes = (List<?>) field(gui, "serverNodes");
        for (int i = 0; i < nodes.size(); i++) {
            Object node = nodes.get(i);
            if (NODE.equals(invoke(node, "pos"))) return new NodeView(i, (String) invoke(node, "name"),
                    (Boolean) invoke(node, "needAuth"), NODE);
        }
        return null;
    }
    private static boolean isConnected(AcademyBaseUI<?> gui, NodeView node) throws Exception {
        return node != null && number(field(gui, "activeNode")) == node.index;
    }
    private static boolean hasPassword(BaseNodeMenu menu) throws Exception {
        Object configured = invoke(menu, "hasPasswordConfigured");
        if (!(configured instanceof Boolean value)) throw new IllegalStateException("hasPasswordConfigured is not boolean");
        return value;
    }
    private static long outgoing(AcademyMenu menu) throws Exception {
        return ((Number) field(field(menu, "actionSession"), "outgoingSequence")).longValue();
    }

    private static String describe(Minecraft mc, String role) throws Exception {
        if (mc.screen == null || mc.player == null) return "screen=" + mc.screen;
        String base = "screen=" + mc.screen.getClass().getSimpleName() + " menu=" + mc.player.containerMenu.containerId;
        if (role.equals("a") && mc.screen instanceof BaseNodeGui<?> gui && mc.player.containerMenu instanceof BaseNodeMenu menu) {
            String mirror = mc.level.getBlockEntity(NODE) instanceof BaseNodeBlockEntity node ? node.getNodeName() : "absent";
            return base + " opening=" + menu.getInitialNodeName() + " menuName=" + menu.getCurrentNodeName()
                    + " displayed=" + field(gui, "nodeNameInput") + " mirror=" + mirror
                    + " hasPassword=" + optionalInvoke(menu, "hasPasswordConfigured")
                    + " revision=" + optionalInvoke(menu, "getCurrentConfigRevision")
                    + " nameACK=" + ackDescription(gui, true) + " passwordACK=" + ackDescription(gui, false)
                    + " staleRenderFrames=" + stage12Renders;
        }
        if (mc.screen instanceof AcademyBaseUI<?> gui) {
            NodeView node = nodeView(gui);
            return base + " node=" + node + " connected=" + isConnected(gui, node)
                    + " response=" + field(gui, "nodeResponseReceived");
        }
        return base;
    }

    private static void finishStage(Minecraft mc, String role, String raw) throws Exception {
        if (!captureStarted) {
            captureStarted = true; captureAge = age;
            int capturedStage = currentStage;
            Path original = mc.gameDirectory.toPath().resolve("screenshots").resolve(screenshotName);
            check(!Files.exists(original), "refusing existing screenshot " + original);
            Screenshot.grab(mc.gameDirectory, screenshotName, mc.getMainRenderTarget(), message -> {
                if (currentStage == capturedStage) screenshotCompletedStage = capturedStage;
            });
            return;
        }
        Path original = mc.gameDirectory.toPath().resolve("screenshots").resolve(screenshotName);
        if (screenshotCompletedStage != currentStage || !Files.isRegularFile(original)) {
            if (age - captureAge <= 240) return;
            failure = (failure == null ? "" : failure + "; ") + "screenshot missing after 240 extra ticks";
        } else {
            Path evidence = NodeConfigGateState.root().resolve("screenshots");
            Files.createDirectories(evidence);
            Path copy = evidence.resolve(screenshotName);
            check(!Files.exists(copy), "refusing existing evidence screenshot " + copy);
            Files.copy(original, copy);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(copy)));
            log(role, "stage=" + currentStage + " screenshot=" + copy + " bytes=" + Files.size(copy) + " sha256=" + hash);
        }
        try { lastObservation = describe(mc, role); } catch (Exception ignored) { }
        String result = "stage=" + currentStage + " " + (failure == null ? "PASS" : "FAIL")
                + " ticks=" + age + " inputAttempted=" + acted + " " + lastObservation
                + (failure == null ? "" : " reason=" + failure);
        log(role, result);
        if (failure != null) NodeConfigGateState.append(failureFile(role), result + "\n");
        NodeConfigGateState.write(role + "-ack.txt", raw);
    }

    private static Object field(Object receiver, String name) throws Exception {
        Field field = optionalField(receiver.getClass(), name);
        if (field == null) throw new NoSuchFieldException(receiver.getClass().getName() + "." + name);
        return field.get(receiver);
    }
    private static Field optionalField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field found = current.getDeclaredField(name); found.setAccessible(true); return found; }
            catch (NoSuchFieldException ignored) { }
        }
        return null;
    }
    private static void setField(Object receiver, String name, Object value) throws Exception {
        Field target = optionalField(receiver.getClass(), name);
        if (target == null) throw new NoSuchFieldException(name);
        target.set(receiver, value);
    }
    private static Object invoke(Object receiver, String name) throws Exception {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { Method method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(receiver); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(receiver.getClass().getName() + "." + name);
    }
    private static String optionalInvoke(Object receiver, String name) {
        try { return String.valueOf(invoke(receiver, name)); } catch (Exception absent) { return "UNAVAILABLE(" + name + ")"; }
    }
    private static int number(Object value) { return ((Number) value).intValue(); }
    private static void check(boolean accepted, String reason) { if (!accepted) throw new IllegalStateException(reason); }
    private static String failureFile(String role) { return role + (Boolean.getBoolean("academy.nodeConfigRestart") ? "-restart" : "") + "-failures.txt"; }
    private static void log(String role, String value) { NodeConfigGateState.append(role + "-evidence.txt", value + "\n"); }
}