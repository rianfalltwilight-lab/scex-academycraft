package com.mohistmc.academy.terminal;

import com.mohistmc.academy.AcademyCraft;
import net.minecraft.resources.ResourceLocation;

public interface TerminalApp {

    String getAppId();

    default String getNameKey() {
        return "app." + getAppId();
    }

    default ResourceLocation getIcon() {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/apps/" + getAppId() + "/icon.png");
    }

    default boolean isBuiltIn() {
        return false;
    }

    /** Client code installs and invokes the action; the common API stays distribution-neutral. */
    void open();
}
