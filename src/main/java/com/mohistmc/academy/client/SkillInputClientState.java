package com.mohistmc.academy.client;
import com.mohistmc.academy.network.RequestSkillInputSessionPacket;
import com.mohistmc.academy.network.SkillInputToken;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
/** Session readiness is separate from the process-wide request generation, so old S2C acks cannot alias after rejoin. */
public final class SkillInputClientState {
    private static UUID session, lastSession;
    private static net.minecraft.network.Connection connection;
    private static long revision;
    private static long sequence;
    private static int retryTicks;
    private SkillInputClientState() {}
    public static void clear() { session = null; retryTicks = 0; }
    public static boolean ready() { return session != null; }
    public static void tick() {
        if (!ready() && retryTicks-- <= 0) { retryTicks = 20; PacketDistributor.sendToServer(RequestSkillInputSessionPacket.INSTANCE); }
    }
    public static void accept(net.minecraft.network.Connection source, UUID next, long nextRevision,
            net.minecraft.resources.ResourceLocation dimension) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getConnection() == null || mc.getConnection().getConnection() != source || mc.level == null
                || !mc.level.dimension().location().equals(dimension)) return;
        if (source != connection) { connection = source; revision = 0; lastSession = null; session = null; }
        if (next == null || next.equals(SkillInputToken.ABSENT) || nextRevision <= 0 || nextRevision < revision
                || nextRevision == revision && !next.equals(lastSession)) return;
        if (!next.equals(session)) KeyInputHandler.resetClientSession();
        session = next; lastSession = next; revision = nextRevision;
    }    public static SkillInputToken next() {
        if (!ready()) throw new IllegalStateException("skill input session not ready");
        if (sequence == Long.MAX_VALUE) throw new IllegalStateException("skill input sequence exhausted");
        return new SkillInputToken(session, ++sequence);
    }
}