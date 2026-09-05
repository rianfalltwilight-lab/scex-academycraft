package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.PlayerAbilityDataCodec;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;

/** Uses production login/remove and real compressed playerdata; EmbeddedChannel is not a real socket client. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class RecheckPlayerPersistenceGameTests {
    private RecheckPlayerPersistenceGameTests() {}
    @GameTest(template = "empty")
    public static void realLogoutAndSameUuidLoginReloadFullAttachmentAndInventory(GameTestHelper helper) {
        GameProfile profile; CompoundTag expected; ServerPlayer oldPlayer; PlayerAbilityData oldData;
        try (var first = RecheckPlayers.connect(helper)) {
            oldPlayer = first.player(); profile = oldPlayer.getGameProfile();
            oldData = RecheckPersistenceProbe.seedData(oldPlayer);
            oldPlayer.getInventory().clearContent(); oldPlayer.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            oldPlayer.experienceLevel = 13; oldPlayer.totalExperience = 257;
            expected = RecheckPersistenceProbe.attachment(oldPlayer);
        }
        Path disk = playerFile(helper, profile);
        helper.assertTrue(Files.isRegularFile(disk), "PlayerList.remove did not create a real .dat");
        try (var second = RecheckPlayers.connect(helper, profile)) {
            ServerPlayer player = second.player();
            helper.assertTrue(player != oldPlayer && player.getData(AcademyAttachments.PLAYER_ABILITY) != oldData, "must create a fresh player and attachment");
            helper.assertTrue(expected.equals(RecheckPersistenceProbe.attachment(player)), "full persistent attachment changed: " + RecheckPersistenceProbe.attachment(player));
            helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 7, "inventory did not persist");
            helper.assertTrue(player.experienceLevel == 13 && player.totalExperience == 257, "XP did not persist");
            LogUtils.getLogger().info("RECHECK_PLAYER_DISK_PASS sameUuid={} freshInstances=true attachmentKeys={}", profile.getId(), expected.getAllKeys());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void successiveSavesReplacePrimaryAndCorruptPrimaryFallsBackToDatOld(GameTestHelper helper) throws Exception {
        GameProfile profile; CompoundTag expectedOld;
        try (var first = RecheckPlayers.connect(helper)) {
            profile = first.player().getGameProfile(); RecheckPersistenceProbe.seedData(first.player());
            first.player().getData(AcademyAttachments.PLAYER_ABILITY).setCurrentCp(321.125F);
            expectedOld = RecheckPersistenceProbe.attachment(first.player());
        }
        try (var second = RecheckPlayers.connect(helper, profile)) {
            helper.assertTrue(expectedOld.equals(RecheckPersistenceProbe.attachment(second.player())), "initial disk reload failed");
            second.player().getData(AcademyAttachments.PLAYER_ABILITY).setCurrentCp(456.25F);
        }
        Path primary = playerFile(helper, profile);
        Path backup = primary.resolveSibling(profile.getId() + ".dat_old");
        helper.assertTrue(Files.isRegularFile(backup), "real save did not retain .dat_old");
        CompoundTag saved = NbtIo.readCompressed(primary, NbtAccounter.unlimitedHeap());
        helper.assertTrue(saved.getUUID("UUID").equals(profile.getId()), "wrong player's primary file");
        // Deliberately corrupt only this test-created random UUID's primary file.
        Files.write(primary, new byte[] {1, 2, 3, 4}, StandardOpenOption.TRUNCATE_EXISTING);
        try (var third = RecheckPlayers.connect(helper, profile)) {
            helper.assertTrue(expectedOld.equals(RecheckPersistenceProbe.attachment(third.player())), "corrupted primary did not reload exact previous attachment");
            LogUtils.getLogger().info("RECHECK_PLAYER_BACKUP_PASS uuid={} previousCp=321.125 primaryCp=456.25 fallback=dat_old", profile.getId());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoRealPlayerFilesCannotShareAbilityOrInventoryState(GameTestHelper helper) {
        GameProfile a, b;
        try (var first = RecheckPlayers.connect(helper); var second = RecheckPlayers.connect(helper)) {
            a = first.player().getGameProfile(); b = second.player().getGameProfile();
            RecheckPersistenceProbe.seedData(first.player()).setMisakaId(111);
            RecheckPersistenceProbe.seedData(second.player()).setMisakaId(222);
            first.player().getInventory().clearContent(); second.player().getInventory().clearContent();
            first.player().getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            second.player().getInventory().setItem(0, new ItemStack(Items.EMERALD, 11));
        }
        try (var first = RecheckPlayers.connect(helper, a); var second = RecheckPlayers.connect(helper, b)) {
            helper.assertTrue(first.player().getData(AcademyAttachments.PLAYER_ABILITY).getMisakaId() == 111, "A attachment became B");
            helper.assertTrue(second.player().getData(AcademyAttachments.PLAYER_ABILITY).getMisakaId() == 222, "B attachment became A");
            helper.assertTrue(first.player().getInventory().getItem(0).is(Items.DIAMOND) && first.player().getInventory().getItem(0).getCount() == 7, "A item changed");
            helper.assertTrue(second.player().getInventory().getItem(0).is(Items.EMERALD) && second.player().getInventory().getItem(0).getCount() == 11, "B item changed");
            LogUtils.getLogger().info("RECHECK_PLAYER_ISOLATION_PASS uuidA={} uuidB={}", a.getId(), b.getId());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedSavedAttachmentBoundsSurviveRealDiskAndNewPlayerLoad(GameTestHelper helper) {
        GameProfile profile;
        try (var first = RecheckPlayers.connect(helper)) {
            ServerPlayer player = first.player(); profile = player.getGameProfile();
            RecheckPersistenceProbe.seedData(player);
            CompoundTag tag = RecheckPersistenceProbe.attachment(player);
            tag.putFloat("cp", Float.NaN); tag.putFloat("overload", Float.POSITIVE_INFINITY);
            tag.putFloat("usage_max_cp", Float.POSITIVE_INFINITY); tag.putFloat("usage_max_overload", -100F);
            tag.putInt("current_preset", Integer.MAX_VALUE);
            CompoundTag cds = tag.getCompound("cooldowns"); cds.putInt("air_blade", Integer.MAX_VALUE);
            player.setData(AcademyAttachments.PLAYER_ABILITY, PlayerAbilityDataCodec.INSTANCE.read(player, tag, player.registryAccess()));
        }
        try (var second = RecheckPlayers.connect(helper, profile)) {
            PlayerAbilityData d = second.player().getData(AcademyAttachments.PLAYER_ABILITY);
            helper.assertTrue(Float.isFinite(d.getCurrentCp()) && d.getCurrentCp() >= 0 && d.getCurrentCp() <= d.getMaxCp(), "nonfinite/out-of-range CP survived");
            helper.assertTrue(Float.isFinite(d.getCurrentOverload()) && d.getCurrentOverload() >= 0 && d.getCurrentOverload() <= d.getMaxOverload(), "nonfinite/out-of-range overload survived");
            helper.assertTrue(d.getCurrentPresetIndex() >= 0 && d.getCurrentPresetIndex() < PlayerAbilityData.PRESET_COUNT, "invalid preset survived");
            helper.assertTrue(d.getCooldownTicks("air_blade") == 72000, "cooldown cap not stable over disk");
            LogUtils.getLogger().info("RECHECK_PLAYER_BOUNDS_PASS uuid={} cp={} ol={} preset={} cooldown={}", profile.getId(), d.getCurrentCp(), d.getCurrentOverload(), d.getCurrentPresetIndex(), d.getCooldownTicks("air_blade"));
        }
        helper.succeed();
    }

    private static Path playerFile(GameTestHelper helper, GameProfile profile) {
        Path root = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).toAbsolutePath().normalize();
        Path file = root.resolve(profile.getId() + ".dat").normalize();
        if (!file.startsWith(root)) throw new IllegalStateException("test file escaped playerdata");
        return file;
    }
}