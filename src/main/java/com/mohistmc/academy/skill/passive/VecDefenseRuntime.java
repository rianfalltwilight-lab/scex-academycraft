package com.mohistmc.academy.skill.passive;
import java.util.Map; import java.util.Set; import java.util.UUID; import java.util.concurrent.ConcurrentHashMap;
public final class VecDefenseRuntime {
 public enum Mode { DEVIATION, REFLECTION } private static final Map<UUID,Context> ACTIVE=new ConcurrentHashMap<>(); private VecDefenseRuntime(){}
 public static void start(UUID p,Mode m){start(p,m,0);} public static void start(UUID p,Mode m,float overloadFloor){ACTIVE.put(p,new Context(m,overloadFloor));} public static void stop(UUID p){ACTIVE.remove(p);}
 public static void clear(){ACTIVE.clear();}
 public static boolean active(UUID p,Mode m){Context c=ACTIVE.get(p);return c!=null&&c.mode==m;}
 public static float overloadFloor(UUID p){Context c=ACTIVE.get(p);return c==null?0:c.overloadFloor;}
 public static boolean visit(UUID p,UUID e){Context c=ACTIVE.get(p);return c!=null&&c.visited.add(e);}
 public static void maintained(UUID p,long tick){Context c=ACTIVE.get(p);if(c!=null)c.lastPaidTick=tick;}
 public static boolean maintainedThisTick(UUID p,long tick){Context c=ACTIVE.get(p);return c!=null&&c.lastPaidTick==tick;}
 private static final class Context { final Mode mode; final float overloadFloor; final Set<UUID> visited=ConcurrentHashMap.newKeySet(); volatile long lastPaidTick=Long.MIN_VALUE; Context(Mode m,float overloadFloor){mode=m;this.overloadFloor=overloadFloor;} }
}
