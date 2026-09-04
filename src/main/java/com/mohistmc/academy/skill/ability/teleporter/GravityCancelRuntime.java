package com.mohistmc.academy.skill.ability.teleporter;
import com.mohistmc.academy.AcademyCraft;import java.util.*;import java.util.concurrent.ConcurrentHashMap;import net.minecraft.server.level.ServerPlayer;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.event.entity.player.PlayerEvent;import net.neoforged.neoforge.event.tick.PlayerTickEvent;import net.neoforged.neoforge.event.server.ServerStoppingEvent;
/** Forty-tick post-Flashing gravity cancellation, preserving pre-existing no-gravity ownership. */
@EventBusSubscriber(modid=AcademyCraft.MODID) final class GravityCancelRuntime{
 private record State(long until,boolean prior){}private static final Map<UUID,State>ACTIVE=new ConcurrentHashMap<>();private GravityCancelRuntime(){}
 static void start(ServerPlayer p){long until=p.serverLevel().getGameTime()+40;State old=ACTIVE.get(p.getUUID());ACTIVE.put(p.getUUID(),new State(Math.max(until,old==null?0:old.until()),old==null?p.isNoGravity():old.prior()));p.setNoGravity(true);}
 private static void stop(ServerPlayer p){State s=ACTIVE.remove(p.getUUID());if(s!=null&&p.isNoGravity()!=s.prior())p.setNoGravity(s.prior());}
 @SubscribeEvent static void tick(PlayerTickEvent.Post e){if(e.getEntity()instanceof ServerPlayer p){State s=ACTIVE.get(p.getUUID());if(s!=null){if(!p.isAlive()||com.mohistmc.academy.skill.AbilityInterferenceService.isInterfered(p)||p.serverLevel().getGameTime()>=s.until())stop(p);else{p.setNoGravity(true);p.fallDistance=0;}}}}
 @SubscribeEvent static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity()instanceof ServerPlayer p)stop(p);}@SubscribeEvent static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity()instanceof ServerPlayer p)stop(p);}@SubscribeEvent static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity()instanceof ServerPlayer p)stop(p);}
 @SubscribeEvent static void stopping(ServerStoppingEvent e){for(UUID id:List.copyOf(ACTIVE.keySet())){ServerPlayer p=e.getServer().getPlayerList().getPlayer(id);if(p!=null)stop(p);}ACTIVE.clear();}
}
