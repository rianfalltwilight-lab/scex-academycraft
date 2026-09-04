package com.mohistmc.academy.api.event;

import com.mohistmc.academy.energy.api.block.*;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Server-only wireless topology events. Intent events are cancellable and are posted before mutation. */
public final class WirelessEvents {
    private WirelessEvents() {}
    public abstract static class WirelessEvent extends Event { public final IWirelessTile tile; protected WirelessEvent(IWirelessTile t){tile=t;} }
    public static final class Create extends WirelessEvent implements ICancellableEvent {
        public final IWirelessMatrix matrix; public final String ssid, password;
        public Create(IWirelessMatrix m,String s,String p){super(m);matrix=m;ssid=s;password=p;}
    }
    public static final class Destroy extends WirelessEvent { public final IWirelessMatrix matrix; public Destroy(IWirelessMatrix m){super(m);matrix=m;} }
    public static final class ChangePassword extends WirelessEvent implements ICancellableEvent {
        public final IWirelessMatrix matrix; public final String password;
        public ChangePassword(IWirelessMatrix m,String p){super(m);matrix=m;password=p;}
    }
    public static final class LinkNode extends WirelessEvent implements ICancellableEvent {
        public final IWirelessNode node; public final IWirelessMatrix matrix; public final String password;
        public LinkNode(IWirelessNode n,IWirelessMatrix m,String p){super(n);node=n;matrix=m;password=p;}
    }
    public static final class UnlinkNode extends WirelessEvent { public final IWirelessNode node; public final IWirelessMatrix matrix; public UnlinkNode(IWirelessNode n,IWirelessMatrix m){super(n);node=n;matrix=m;} }
    public static final class LinkUser extends WirelessEvent implements ICancellableEvent {
        public final IWirelessUser user; public final IWirelessNode node; public final String password; public final boolean needAuth;
        public LinkUser(IWirelessUser u,IWirelessNode n,boolean a,String p){super(u);user=u;node=n;needAuth=a;password=p;}
    }
    public static final class UnlinkUser extends WirelessEvent { public final IWirelessUser user; public UnlinkUser(IWirelessUser u){super(u);user=u;} }
}
