package com.mohistmc.academy.api;

import com.mohistmc.academy.energy.api.block.*;
import com.mohistmc.academy.energy.impl.*;
import java.util.Collection;
import net.minecraft.server.level.ServerLevel;

/**
 * Supported facade for integrations that used the 1.0.7 WirelessHelper.
 * The level is explicit to avoid unsafe tile casts and all mutations retain server authority/events.
 */
public final class WirelessHelper {
    private WirelessHelper() {}
    public static WirelessNet getWirelessNet(ServerLevel level,IWirelessMatrix matrix){
        WiWorldData data=WiWorldData.getNonCreate(level); return data==null?null:data.getNetwork(matrix);
    }
    public static WirelessNet getWirelessNet(ServerLevel level,IWirelessNode node){return WirelessSystem.getNetwork(level,node);}
    public static boolean isNodeLinked(ServerLevel level,IWirelessNode node){return getWirelessNet(level,node)!=null;}
    public static boolean isMatrixActive(ServerLevel level,IWirelessMatrix matrix){return getWirelessNet(level,matrix)!=null;}
    public static Collection<WirelessNet> getNetInRange(ServerLevel level,int x,int y,int z,double range,int max){
        if(!Double.isFinite(range)||range<0||max<=0)return java.util.List.of();
        return WiWorldData.get(level).rangeSearch(x,y,z,Math.min(range,256),Math.min(max,1024));
    }
    public static NodeConn getNodeConn(ServerLevel level,IWirelessNode node){return WirelessSystem.getNodeConnection(level,node);}
    public static NodeConn getNodeConn(ServerLevel level,IWirelessUser user){
        WiWorldData data=WiWorldData.getNonCreate(level);return data==null?null:data.getNodeConnection(user);
    }
    public static boolean isReceiverLinked(ServerLevel level,IWirelessReceiver receiver){return getNodeConn(level,receiver)!=null;}
    public static boolean isGeneratorLinked(ServerLevel level,IWirelessGenerator generator){return getNodeConn(level,generator)!=null;}
    public static boolean createNetwork(ServerLevel l,IWirelessMatrix m,String s,String p){return WirelessSystem.createNetwork(l,m,s,p);}
    public static boolean linkNode(ServerLevel l,IWirelessMatrix m,IWirelessNode n,String p){return WirelessSystem.linkNode(l,m,n,p);}
    public static boolean unlinkNode(ServerLevel l,IWirelessMatrix m,IWirelessNode n){return WirelessSystem.unlinkNode(l,m,n);}
}
