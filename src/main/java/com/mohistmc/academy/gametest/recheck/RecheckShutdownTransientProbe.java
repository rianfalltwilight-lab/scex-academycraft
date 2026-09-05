package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.ability.meltdowner.JetEngineRuntime;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Optional bounded shutdown-save probe. Direct runtime setup is not a full skill activation/payment test. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class RecheckShutdownTransientProbe {
    private static final float PRIOR_WALK_SPEED = .123F;
    private static MinecraftServer armedServer;
    private static Path coordination, playerDat;
    private static UUID playerId;
    private RecheckShutdownTransientProbe() {}

    /** Call on stage 13's server tick immediately before halt; no later test tick may expire the 16-tick context. */
    public static void armJet(ServerPlayer player) {
        if (!enabled()) throw new IllegalStateException("shutdown probe requires explicit server recheck gate");
        Path root = RecheckSessionState.root(); // Requires the isolated acceptance marker.
        if (armedServer != null) throw new IllegalStateException("shutdown jet probe already armed");
        if (player == null || !player.server.isSameThread() || !player.isAlive() || player.isFakePlayer()
                || !player.getGameProfile().getName().equals("AcademyGateB")
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player)
            throw new IllegalStateException("shutdown jet probe requires the current real AcademyGateB on the server tick");
        try {
            if (Files.exists(root.resolve("jet-arm.txt")) || Files.exists(root.resolve("jet-save-result.txt")))
                throw new IllegalStateException("shutdown probe requires a new evidence target");
            Path dataRoot = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toAbsolutePath().normalize();
            Path actualFile = dataRoot.resolve(player.getStringUUID() + ".dat").normalize();
            if (!actualFile.startsWith(dataRoot)) throw new IllegalStateException("player NBT escaped its world playerdata path");
            float original = player.getAbilities().getWalkingSpeed();
            player.getAbilities().setWalkingSpeed(PRIOR_WALK_SPEED);
            Method start = JetEngineRuntime.class.getDeclaredMethod("start", ServerPlayer.class, Vec3.class, float.class);
            start.setAccessible(true);
            start.invoke(null, player, player.position().add(.25, 0, 0), 0F);
            float active = player.getAbilities().getWalkingSpeed();
            if (Math.abs(active - .07F) > 1e-6F) throw new IllegalStateException("real Jet start did not set .07 walking speed: " + active);
            armedServer = player.server; coordination = root; playerDat = actualFile; playerId = player.getUUID();
            String evidence = "status=ARMED\nplayer=" + playerId + "\noriginal=" + original + "\nprior=" + PRIOR_WALK_SPEED
                    + "\nactive=" + active + "\ncontextLifetimeTicks=16\nserverTick=" + player.server.getTickCount()
                    + "\nplayerdata=" + actualFile + "\nsetup=direct-real-JetEngineRuntime.start\nfullSkillResourcePaymentTest=false\n";
            Files.writeString(root.resolve("jet-arm.txt"), evidence, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            LogUtils.getLogger().info("RECHECK_JET_SHUTDOWN_ARMED player={} prior={} active={} directRuntimeOnly=true", playerId, PRIOR_WALK_SPEED, active);
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new IllegalStateException("could not arm real Jet shutdown probe", failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopped(ServerStoppedEvent event) {
        if (!enabled() || armedServer == null || event.getServer() != armedServer) return;
        String result;
        try {
            if (!Files.isRegularFile(coordination.resolve("ISOLATED-ACCEPTANCE"))) throw new IllegalStateException("isolated marker missing at final read");
            if (!Files.isRegularFile(playerDat)) throw new IllegalStateException("actual saved player NBT missing: " + playerDat);
            var saved = NbtIo.readCompressed(playerDat, NbtAccounter.unlimitedHeap());
            var abilities = saved.getCompound("abilities");
            if (!abilities.contains("walkSpeed", Tag.TAG_FLOAT)) throw new IllegalStateException("actual abilities.walkSpeed float missing");
            float speed = abilities.getFloat("walkSpeed");
            boolean pass = Math.abs(speed - PRIOR_WALK_SPEED) < 1e-6F;
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(playerDat)));
            result = "status=" + (pass ? "PASS" : "FAIL") + "\nplayer=" + playerId + "\nsaved=" + speed
                    + "\nprior=" + PRIOR_WALK_SPEED + "\nplayerdata=" + playerDat + "\nplayerdataBytes=" + Files.size(playerDat)
                    + "\nplayerdataSHA256=" + sha + "\nnbtKey=abilities.walkSpeed\nobservedAt=ServerStoppedEvent\nfullSkillResourcePaymentTest=false\n";
        } catch (Throwable failure) {
            result = "status=FAIL\nplayer=" + playerId + "\nprior=" + PRIOR_WALK_SPEED + "\nerror=" + failure + "\n";
        }
        try {
            Files.writeString(coordination.resolve("jet-save-result.txt"), result, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            LogUtils.getLogger().info("RECHECK_JET_SHUTDOWN_RESULT {}", result.replace('\n', ' '));
        } catch (Throwable failure) {
            // Never throw from this post-save observer or prevent normal server shutdown.
            LogUtils.getLogger().error("Could not record isolated Jet final playerdata result: {}", result, failure);
        } finally {
            armedServer = null; coordination = null; playerDat = null; playerId = null;
        }
    }

    private static boolean enabled() {
        return RecheckSessionState.enabled() && RecheckSessionState.role().equals("server");
    }
}
