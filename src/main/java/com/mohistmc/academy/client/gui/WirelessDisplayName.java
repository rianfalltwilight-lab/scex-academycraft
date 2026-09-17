package com.mohistmc.academy.client.gui;

import net.minecraft.network.chat.Component;

/** Localize the legacy placeholder for display only; identity and edit buffers stay literal. */
public final class WirelessDisplayName {
    private WirelessDisplayName() {}

    public static String display(String name) {
        return "Unnamed".equals(name)
                ? Component.translatable("gui.academy.wireless.unnamed").getString() : name;
    }
}
