package com.mohistmc.academy.skill;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.NotNull;

public class PlayerAbilityDataCodec implements IAttachmentSerializer<CompoundTag, PlayerAbilityData> {

    public static final PlayerAbilityDataCodec INSTANCE = new PlayerAbilityDataCodec();
    public static final int DATA_VERSION = 4;

    @Override
    public @NotNull PlayerAbilityData read(@NotNull IAttachmentHolder holder, CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        tag = PlayerAbilityDataMigration.migrate(tag);
        PlayerAbilityData data = new PlayerAbilityData();

        if (tag.contains("ability")) {
            String abilityId = tag.getString("ability");
            AbilityCategory cat = AbilityCategory.fromId(abilityId);
            if (cat != null) data.setCurrentAbility(cat);
        }

        data.setPlayerLevel(tag.getInt("level"));
        if (tag.contains("level_progress_exp")) data.setLevelProgressExp(tag.getFloat("level_progress_exp"));

        if (tag.contains("learned")) {
            ListTag list = tag.getList("learned", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.learnSkill(list.getString(i));
            }
        }

        data.setUsageResourceGrowth(tag.getFloat("usage_max_cp"), tag.getFloat("usage_max_overload"));
        data.setCurrentCp(tag.getFloat("cp"));
        data.setCurrentOverload(tag.getFloat("overload"));

        if (tag.contains("obtained")) {
            ListTag list = tag.getList("obtained", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.markObtained(list.getString(i));
            }
        }

        if (tag.contains("activated_tutorials")) {
            ListTag list = tag.getList("activated_tutorials", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) data.activateTutorial(list.getString(i));
        }

        if (tag.contains("proficiency")) {
            CompoundTag profTag = tag.getCompound("proficiency");
            for (String key : profTag.getAllKeys()) {
                data.setProficiency(key, profTag.getFloat(key));
            }
        }

        if (tag.contains("current_preset")) {
            data.setCurrentPreset(tag.getInt("current_preset"));
        }
        if (tag.contains("presets")) {
            CompoundTag presetsTag = tag.getCompound("presets");
            for (int p = 0; p < PlayerAbilityData.PRESET_COUNT; p++) {
                String presetKey = "preset_" + p;
                if (presetsTag.contains(presetKey)) {
                    CompoundTag presetTag = presetsTag.getCompound(presetKey);
                    for (int s = 0; s < SkillPreset.SLOT_COUNT; s++) {
                        String slotKey = "slot_" + s;
                        if (presetTag.contains(slotKey)) {
                            data.setSlot(p, s, presetTag.getString(slotKey));
                        }
                    }
                }
            }
        }

        if (tag.contains("ability_active")) {
            data.setAbilityActive(tag.getBoolean("ability_active"));
        }

        if (tag.contains("terminal_installed")) {
            data.setTerminalInstalled(tag.getBoolean("terminal_installed"));
        }
        if (tag.contains("installed_apps")) {
            ListTag appList = tag.getList("installed_apps", Tag.TAG_STRING);
            for (int i = 0; i < appList.size(); i++) {
                data.installApp(appList.getString(i));
            }
        }

        if (tag.contains("loaded_media")) {
            ListTag mediaList = tag.getList("loaded_media", Tag.TAG_STRING);
            for (int i = 0; i < mediaList.size(); i++) {
                data.addLoadedMedia(mediaList.getString(i));
            }
        }

        if (tag.contains("misaka_id")) {
            data.setMisakaId(tag.getInt("misaka_id"));
        }
        if (tag.contains("tutorial_item_granted")) {
            data.setTutorialItemGranted(tag.getBoolean("tutorial_item_granted"));
        } else if (data.hasObtained("academy:tutorial")) {
            // Rebuilt worlds created before this flag already recorded a
            // picked-up tutorial item.  Do not grant a migration duplicate.
            data.setTutorialItemGranted(true);
        }

        if (tag.contains("dev_mode")) {
            data.setDevMode(tag.getBoolean("dev_mode"));
        }

        if (tag.contains("cooldowns")) {
            CompoundTag cdTag = tag.getCompound("cooldowns");
            for (String key : cdTag.getAllKeys()) {
                int cd = cdTag.getInt(key);
                if (cd > 0) data.setCooldown(key, cd);
            }
        }
        data.setRecoveryDelays(tag.getInt("cp_recovery_delay"), tag.getInt("overload_recovery_delay"));
        if (tag.contains("teleport_locations")) {
            ListTag locations = tag.getList("teleport_locations", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(locations.size(), PlayerAbilityData.MAX_TELEPORT_LOCATIONS); i++) {
                CompoundTag loc = locations.getCompound(i);
                data.addTeleportLocation(new PlayerAbilityData.TeleportLocation(loc.getString("name"),
                        loc.getString("dimension"), loc.getDouble("x"), loc.getDouble("y"), loc.getDouble("z")));
            }
        }

        data.sanitizeResources();
        return data;
    }

    @Override
    public CompoundTag write(PlayerAbilityData data, HolderLookup.@NotNull Provider provider) {
        data.sanitizeForSerialization();
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", DATA_VERSION);

        if (data.hasAbility()) {
            tag.putString("ability", data.getCurrentAbility().id());
        }

        tag.putInt("level", data.getPlayerLevel());
        tag.putFloat("level_progress_exp", data.getLevelProgressExp());
        tag.putFloat("cp", data.getCurrentCp());
        tag.putFloat("max_cp", data.getMaxCp());
        tag.putFloat("overload", data.getCurrentOverload());
        tag.putFloat("max_overload", data.getMaxOverload());
        tag.putFloat("usage_max_cp", data.getUsageMaxCp());
        tag.putFloat("usage_max_overload", data.getUsageMaxOverload());
        tag.putFloat("cp_regen", data.getCpRegenRate());

        ListTag learnedList = new ListTag();
        for (String skillId : data.getLearnedSkills()) {
            learnedList.add(net.minecraft.nbt.StringTag.valueOf(skillId));
        }
        tag.put("learned", learnedList);

        ListTag obtainedList = new ListTag();
        for (String itemId : data.getObtainedItems()) {
            obtainedList.add(net.minecraft.nbt.StringTag.valueOf(itemId));
        }
        tag.put("obtained", obtainedList);

        ListTag activatedTutorialList = new ListTag();
        for (String tutorialId : data.getActivatedTutorials()) {
            activatedTutorialList.add(net.minecraft.nbt.StringTag.valueOf(tutorialId));
        }
        tag.put("activated_tutorials", activatedTutorialList);

        CompoundTag profTag = new CompoundTag();
        for (var entry : data.getSkillProficiencies().entrySet()) {
            profTag.putFloat(entry.getKey(), entry.getValue());
        }
        tag.put("proficiency", profTag);

        tag.putInt("current_preset", data.getCurrentPresetIndex());
        CompoundTag presetsTag = new CompoundTag();
        for (int p = 0; p < PlayerAbilityData.PRESET_COUNT; p++) {
            CompoundTag presetTag = new CompoundTag();
            SkillPreset preset = data.getPreset(p);
            for (int s = 0; s < SkillPreset.SLOT_COUNT; s++) {
                String skillId = preset.getSlot(s);
                if (skillId != null) {
                    presetTag.putString("slot_" + s, skillId);
                }
            }
            presetsTag.put("preset_" + p, presetTag);
        }
        tag.put("presets", presetsTag);

        tag.putBoolean("ability_active", data.isAbilityActive());

        tag.putBoolean("terminal_installed", data.isTerminalInstalled());
        ListTag appList = new ListTag();
        for (String appId : data.getInstalledApps()) {
            appList.add(net.minecraft.nbt.StringTag.valueOf(appId));
        }
        tag.put("installed_apps", appList);

        ListTag mediaList = new ListTag();
        for (String mediaId : data.getLoadedMedia()) {
            mediaList.add(net.minecraft.nbt.StringTag.valueOf(mediaId));
        }
        tag.put("loaded_media", mediaList);

        tag.putInt("misaka_id", data.getMisakaId());
        tag.putBoolean("tutorial_item_granted", data.isTutorialItemGranted());

        tag.putBoolean("dev_mode", data.isDevMode());

        CompoundTag cdTag = new CompoundTag();
        data.cooldowns.forEach((skillId, ticks) -> {
            if (ticks > 0) cdTag.putInt(skillId, ticks);
        });
        tag.put("cooldowns", cdTag);
        tag.putInt("cp_recovery_delay", data.cpRecoveryDelay());
        tag.putInt("overload_recovery_delay", data.overloadRecoveryDelay());

        ListTag locations = new ListTag();
        for (PlayerAbilityData.TeleportLocation location : data.getTeleportLocations()) {
            CompoundTag loc = new CompoundTag();
            loc.putString("name", location.name()); loc.putString("dimension", location.dimension());
            loc.putDouble("x", location.x()); loc.putDouble("y", location.y()); loc.putDouble("z", location.z());
            locations.add(loc);
        }
        tag.put("teleport_locations", locations);

        return tag;
    }
}
