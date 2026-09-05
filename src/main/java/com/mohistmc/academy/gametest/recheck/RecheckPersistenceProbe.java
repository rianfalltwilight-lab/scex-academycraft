package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Default-off real socket gate probe. Snapshots actual logout state, never invents a stable CP value before a cast. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class RecheckPersistenceProbe {
    private static final Map<UUID, String> EARLY_LOGIN = new HashMap<>();
    private RecheckPersistenceProbe() {}

    public static PlayerAbilityData seedData(ServerPlayer player) {
        PlayerAbilityData data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.AEROHAND);
        data.setPlayerLevel(5);
        data.setLevelProgressExp(35.25F);
        data.learnSkill("air_blade"); data.learnSkill("flying"); data.learnSkill("brain_course");
        data.setProficiency("air_blade", .375F); data.setProficiency("flying", .5F);
        data.setUsageResourceGrowth(33.25F, 17.5F);
        data.setCurrentCp(5000F); data.setCurrentOverload(17.25F);
        data.setAbilityActive(true); data.setDevMode(false);
        data.setCurrentPreset(2); data.setSlot(2, 0, "flying"); data.setSlot(2, 1, "air_blade");
        data.setSlot(0, 3, "air_blade"); data.setCooldown("air_blade", 3000);
        data.setTerminalInstalled(true); data.installApp("recheck_persistent_app");
        data.addLoadedMedia("recheck_persistent_media");
        data.markObtained("academy:reso_crystal"); data.activateTutorial("recheck_persistent_tutorial");
        data.setMisakaId(234567); data.setTutorialItemGranted(true);
        data.addTeleportLocation(new PlayerAbilityData.TeleportLocation("RecheckHome", "minecraft:overworld", 1.25, 80.5, -3.75));
        CompoundTag tag = PlayerAbilityDataCodec.INSTANCE.write(data, player.registryAccess());
        tag.putInt("cp_recovery_delay", 20000); tag.putInt("overload_recovery_delay", 20000);
        data = PlayerAbilityDataCodec.INSTANCE.read(player, tag, player.registryAccess());
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    public static void prepare(ServerPlayer player) {
        ensure(isTarget(player), "probe can only prepare the isolated gate player");
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(false);
        PlayerAbilityData data = seedData(player);
        player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        player.experienceLevel = 13; player.totalExperience = 257; player.experienceProgress = .25F;
        EARLY_LOGIN.remove(player.getUUID());
        data.syncTo(player);
        append(player, "PREPARE preset=2 slot0=flying slot1=air_blade cp=" + data.getCurrentCp() + " ol=" + data.getCurrentOverload());
    }

    public static void verifyReconnect(ServerPlayer player) { verify(player, "RECONNECT"); }
    public static void verifyRestart(ServerPlayer player) { verify(player, "RESTART"); }
    private static void verify(ServerPlayer player, String phase) {
        String result = EARLY_LOGIN.get(player.getUUID());
        ensure(result != null && result.startsWith("PASS"), phase + " early PlayerLoggedIn evidence missing or failed: " + result);
        ensure(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 7, "persistent inventory mismatch");
        ensure(player.experienceLevel == 13 && player.totalExperience == 257, "persistent XP mismatch");
        assertNoTransientSession(player);
        append(player, phase + "_PASS " + result);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isTarget(player)) return;
        // NeoForge PlayerList.remove invokes all logout listeners before its real PlayerDataStorage.save.
        try {
            CompoundTag expected = snapshot(player);
            NbtIo.writeCompressed(expected, expectedPath(player));
            append(player, "LOGOUT_BEFORE_DISK_SAVE " + summary(player));
        } catch (Exception error) { throw new IllegalStateException("isolated persistence snapshot failed", error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if (!Boolean.getBoolean("academy.recheckSessionGate")) return;
        // MinecraftServer.runServer fires ServerStopping before stopServer saves all players.
        // A normal stop can save before any logout callback; the earlier reconnect snapshot is stale.
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!isTarget(player)) continue;
            try {
                NbtIo.writeCompressed(snapshot(player), expectedPath(player));
                append(player, "SERVER_STOPPING_BEFORE_SAVE " + summary(player));
            } catch (Exception error) { throw new IllegalStateException("isolated final-save snapshot failed", error); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isTarget(player)) return;
        try {
            Path path = expectedPath(player);
            if (!Files.isRegularFile(path)) { append(player, "FIRST_LOGIN no prior logout snapshot"); return; }
            CompoundTag expected = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            CompoundTag current = snapshot(player);
            ensure(expected.getCompound("attachment").equals(current.getCompound("attachment")),
                    "attachment changed across disk load before first player tick; expected=" + expected.getCompound("attachment") + " actual=" + current.getCompound("attachment"));
            assertNoTransientSession(player);
            Path playerDat = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
            ensure(Files.isRegularFile(playerDat) && Files.size(playerDat) > 0, "real playerdata file missing");
            String result = "PASS before-first-tick " + summary(player) + " playerdataBytes=" + Files.size(playerDat);
            EARLY_LOGIN.put(player.getUUID(), result); append(player, "LOGIN_" + result);
        } catch (Exception | AssertionError error) {
            EARLY_LOGIN.put(player.getUUID(), "FAIL " + error);
            append(player, "LOGIN_FAIL " + error);
        }
    }

    public static CompoundTag attachment(ServerPlayer player) {
        return PlayerAbilityDataCodec.INSTANCE.write(player.getData(AcademyAttachments.PLAYER_ABILITY), player.registryAccess());
    }
    private static CompoundTag snapshot(ServerPlayer player) {
        CompoundTag tag = new CompoundTag(); tag.put("attachment", attachment(player));
        tag.putBoolean("mayfly", player.getAbilities().mayfly); tag.putBoolean("flying", player.getAbilities().flying);
        tag.putBoolean("noGravity", player.isNoGravity()); return tag;
    }
    private static void assertNoTransientSession(ServerPlayer player) {
        ensure(!SkillChargingManager.isCharging(player.getUUID()), "charging session leaked");
        ensure(!AeroPassiveRuntime.isFlyingActive(player) && !AeroPassiveRuntime.isOffenseArmourEngaged(player), "Aero paid session leaked");
        ensure(!TelekinesisPassiveHandler.isHardened(player), "hardening session leaked");
        ensure(!player.getAbilities().mayfly && !player.getAbilities().flying && !player.isNoGravity(), "survival flight/gravity flags leaked: " + summary(player));
    }
    private static String summary(ServerPlayer player) {
        PlayerAbilityData d = player.getData(AcademyAttachments.PLAYER_ABILITY);
        return "uuid=" + player.getUUID() + " cp=" + d.getCurrentCp() + " ol=" + d.getCurrentOverload()
                + " learned=" + d.getLearnedSkills().size() + " cooldown=" + d.getCooldownTicks("air_blade")
                + " mayfly=" + player.getAbilities().mayfly + " flying=" + player.getAbilities().flying + " noGravity=" + player.isNoGravity();
    }
    private static boolean isTarget(ServerPlayer player) {
        return Boolean.getBoolean("academy.recheckSessionGate") && player.getGameProfile().getName().equals(System.getProperty("academy.recheckPersistencePlayer", "AcademyGateA"));
    }
    private static Path root() throws java.io.IOException {
        String configured = System.getProperty("academy.recheckSessionRoot");
        ensure(configured != null && !configured.isBlank(), "missing recheck root");
        Path path = Path.of(configured).toAbsolutePath().normalize();
        ensure(Files.isRegularFile(path.resolve("ISOLATED-ACCEPTANCE")), "missing isolated acceptance marker");
        Path output = path.resolve("persistence"); Files.createDirectories(output); return output;
    }
    private static Path expectedPath(ServerPlayer player) throws java.io.IOException { return root().resolve("expected-" + player.getUUID() + ".nbt"); }
    private static void append(ServerPlayer player, String line) {
        try { Files.writeString(root().resolve("player-reload.log"), line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static void ensure(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}