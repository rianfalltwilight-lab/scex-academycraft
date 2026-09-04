package com.mohistmc.academy.terminal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.NotNull;

public class TerminalDataCodec implements IAttachmentSerializer<CompoundTag, TerminalData> {
    public static final int DATA_VERSION = 1;

    @Override
    public @NotNull TerminalData read(@NotNull IAttachmentHolder holder, CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        tag = migrate(tag);
        TerminalData data = new TerminalData();
        data.setInstalled(tag.getBoolean("installed"));

        if (tag.contains("apps")) {
            ListTag list = tag.getList("apps", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.installApp(list.getString(i));
            }
        }

        return data;
    }

    @Override
    public CompoundTag write(TerminalData data, HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", DATA_VERSION);
        tag.putBoolean("installed", data.isInstalled());

        ListTag appList = new ListTag();
        for (String appId : data.getInstalledApps()) {
            appList.add(StringTag.valueOf(appId));
        }
        tag.put("apps", appList);

        return tag;
    }

    /** Semantic import of the unversioned legacy field; intentionally idempotent. */
    public static CompoundTag migrate(CompoundTag input) {
        CompoundTag out = input.copy();
        int version = out.contains("data_version") ? out.getInt("data_version") : 0;
        if (version < 1) {
            if (!out.contains("installed") && out.contains("isInstalled")) {
                out.putBoolean("installed", out.getBoolean("isInstalled"));
            }
            // A separate extractor may normalize the legacy BitSet to stable app IDs.
            // Unknown raw bit indices fail closed rather than installing the wrong app.
            if (!out.contains("apps") && out.contains("legacy_app_ids", Tag.TAG_LIST)) {
                out.put("apps", out.getList("legacy_app_ids", Tag.TAG_STRING).copy());
            }
            out.putInt("data_version", 1);
        }
        return out;
    }
}
