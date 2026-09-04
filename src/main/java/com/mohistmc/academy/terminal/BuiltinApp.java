package com.mohistmc.academy.terminal;

import net.minecraft.resources.ResourceLocation;

public class BuiltinApp implements TerminalApp {

    private final String appId;
    private final String nameKey;
    private final ResourceLocation icon;
    private Runnable openAction;

    public BuiltinApp(String appId, String nameKey) {
        this.appId = appId;
        this.nameKey = nameKey;
        this.icon = TerminalApp.super.getIcon();
    }

    public BuiltinApp(String appId, String nameKey, ResourceLocation icon) {
        this.appId = appId;
        this.nameKey = nameKey;
        this.icon = icon;
    }

    public void setOpenAction(Runnable action) {
        this.openAction = action;
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public String getNameKey() {
        return nameKey;
    }

    @Override
    public ResourceLocation getIcon() {
        return icon;
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void open() {
        if (openAction != null) {
            openAction.run();
        }
    }
}
