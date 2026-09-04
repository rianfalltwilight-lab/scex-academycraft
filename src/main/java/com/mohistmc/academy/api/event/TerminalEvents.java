package com.mohistmc.academy.api.event;

import com.mohistmc.academy.terminal.TerminalApp;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

public final class TerminalEvents {
    private TerminalEvents() {}
    public static final class Installed extends Event { public final Player player; public Installed(Player p){player=p;} }
    public static final class AppInstalled extends Event { public final Player player; public final TerminalApp app; public AppInstalled(Player p,TerminalApp a){player=p;app=a;} }
}
