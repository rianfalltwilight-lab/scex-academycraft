package com.mohistmc.academy.client.gate;

import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.gametest.recheck.RecheckSessionState;
import com.mohistmc.academy.skill.AcademyAttachments;
import java.nio.file.Files;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;

/** Default-off gate assertion of actual S2C attachment after reconnect/restart. */
public final class RecheckClientPersistenceProbe {
    private RecheckClientPersistenceProbe() {}
    public static void verify(String phase) {
        if (!RecheckSessionState.enabled() || !RecheckSessionState.role().equals("a"))
            throw new IllegalStateException("client persistence probe is isolated A only");
        var player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("missing real client player");
        var path = RecheckSessionState.root().resolve("persistence/expected-" + player.getUUID() + ".nbt");
        try {
            if (!Files.isRegularFile(path)) throw new IllegalStateException("missing authoritative logout snapshot");
            CompoundTag expected = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound("attachment");
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            CompoundTag current = data.toSyncTag();
            int stableFields = 0;
            for (String key : current.getAllKeys()) {
                // Resource regeneration and cooldowns continue normally after login; server early-login
                // probe independently requires an exact disk round trip before the first player tick.
                if (key.equals("cp") || key.equals("overload") || key.equals("cooldowns")) continue;
                require(expected.contains(key) && Objects.equals(expected.get(key), current.get(key)),
                        "S2C field " + key + " differs: expected=" + expected.get(key) + " current=" + current.get(key));
                stableFields++;
            }
            require(stableFields >= 20, "sync attachment unexpectedly incomplete");
            require(Float.isFinite(data.getCurrentCp()) && data.getCurrentCp() >= 0 && data.getCurrentCp() <= data.getMaxCp(), "client CP invalid");
            require(Float.isFinite(data.getCurrentOverload()) && data.getCurrentOverload() >= 0 && data.getCurrentOverload() <= data.getMaxOverload(), "client overload invalid");
            int savedCooldown = expected.getCompound("cooldowns").getInt("air_blade");
            int actualCooldown = data.getCooldownTicks("air_blade");
            require(savedCooldown == 0 ? actualCooldown == 0 : actualCooldown > 0 && actualCooldown <= savedCooldown,
                    "client air_blade cooldown missing or increased");
            require(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 7, "client inventory mismatch");
            require(player.experienceLevel == 13 && player.totalExperience == 257, "client XP mismatch");
            require(!player.getAbilities().mayfly && !player.getAbilities().flying && !player.isNoGravity(), "client survival flight/gravity leaked");
            for (int slot = 0; slot < KeyInputHandler.getSkillKeys().length; slot++)
                require(!KeyInputHandler.isSkillHeld(slot), "client held skill leaked at slot=" + slot);
            RecheckSessionState.append("a-evidence.txt", phase + " CLIENT_PERSISTENCE_PASS uuid=" + player.getUUID()
                    + " stableS2CFields=" + stableFields + " cp=" + data.getCurrentCp() + " ol=" + data.getCurrentOverload()
                    + " airBladeCooldown=" + actualCooldown + " savedCooldown=" + savedCooldown
                    + " inventory=diamond7 xp=13/257 transientFlags=false; CP/OL allow normal post-login regeneration");
        } catch (java.io.IOException error) { throw new IllegalStateException("client persistence evidence read failed", error); }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}