package com.mohistmc.academy.skill;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.server.level.ServerPlayer;

/** Public runtime support: transformed Minecraft classes must not access a mixin's private nest. */
public final class AbilityDamageFrames {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private AbilityDamageFrames() {}

    public static final class Frame {
        public final ServerPlayer player;
        public final int containerDepth;
        public boolean vanillaWrites;
        public boolean acceptedBody;
        public float priorLastHurt;
        public int priorInvulnerabilityTicks;
        private Frame(ServerPlayer player, int depth) { this.player = player; this.containerDepth = depth; }
        public void captureVanillaWrites(float lastHurt, int invulnerabilityTicks) {
            vanillaWrites = true;
            priorLastHurt = lastHurt;
            priorInvulnerabilityTicks = invulnerabilityTicks;
        }
    }

    public static Frame enter(ServerPlayer player, int depth, net.minecraft.world.damagesource.DamageSource source) {
        var frame = new Frame(player, depth);
        FRAMES.get().push(frame);
        AcademyDamageHelper.bindDamageFrame(frame, source);
        return frame;
    }

    public static Frame current(ServerPlayer player) {
        Frame frame = FRAMES.get().peek();
        if (frame == null || frame.player != player) throw new IllegalStateException("missing scoped ability damage frame");
        return frame;
    }

    static Frame peek() { return FRAMES.get().peek(); }

    /** A later accepted nested hit owns its resulting hurt fields, even if an ancestor then reflects. */
    public static void completed(Frame frame, float lastHurt, int invulnerabilityTicks) {
        if (!frame.acceptedBody) return;
        for (Frame ancestor : FRAMES.get()) {
            if (ancestor != frame && ancestor.player == frame.player && ancestor.vanillaWrites) {
                ancestor.priorLastHurt = lastHurt;
                ancestor.priorInvulnerabilityTicks = invulnerabilityTicks;
            }
        }
    }
    public static void leave(Frame frame) {
        var frames = FRAMES.get();
        if (frames.pop() != frame) throw new IllegalStateException("out-of-order ability damage frame");
        if (frames.isEmpty()) FRAMES.remove();
    }
}
