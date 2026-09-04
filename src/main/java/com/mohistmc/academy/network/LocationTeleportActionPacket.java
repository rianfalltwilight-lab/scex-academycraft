package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.PlayerAbilityData.TeleportLocation;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LocationTeleportActionPacket(int action, int index, String name) implements CustomPacketPayload {
    public static final int QUERY=0, ADD=1, REMOVE=2, PERFORM=3;
    private static final int MAX_DESTINATION_CHUNKS = 16;
    private static final TicketType<UUID> LOCATION_TELEPORT_TICKET =
            TicketType.create("academy_location_teleport", Comparator.<UUID>naturalOrder(), 20);
    public static final Type<LocationTeleportActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"location_teleport_action"));
    // Saved labels remain capped at 32; consent carries a canonical 36-character UUID.
    private static final StreamCodec<ByteBuf,String> NAME = ByteBufCodecs.stringUtf8(36);
    public static final StreamCodec<ByteBuf,LocationTeleportActionPacket> STREAM_CODEC=StreamCodec.ofMember(
            (p,b)->{b.writeByte(p.action);b.writeByte(p.index);NAME.encode(b,p.name);},
            b->new LocationTeleportActionPacket(b.readUnsignedByte(),b.readByte(),NAME.decode(b)));
    private record CastConsent(UUID caster,long nonce,LocationConsentScope scope,long expiry,java.util.Set<UUID> passengers,
                               java.util.Set<UUID> accepted){}
    private static final Map<UUID,CastConsent> CASTS = new ConcurrentHashMap<>();
    private static final Map<UUID,CastConsent> REQUESTS = new ConcurrentHashMap<>();
    public static void forgetPlayer(UUID playerId){CastConsent cast=CASTS.remove(playerId);if(cast!=null)cast.passengers.forEach(REQUESTS::remove);CastConsent request=REQUESTS.remove(playerId);if(request!=null)CASTS.remove(request.caster,request);CASTS.entrySet().removeIf(e->{boolean hit=e.getValue().passengers.contains(playerId);if(hit)e.getValue().passengers.forEach(REQUESTS::remove);return hit;});}
    public static void clearAll(){CASTS.clear();REQUESTS.clear();}
    public static void answerConsent(ServerPlayer passenger,long nonce,boolean accepted){
        CastConsent cast=REQUESTS.remove(passenger.getUUID());long now=passenger.serverLevel().getGameTime();
        if(cast==null||cast.nonce!=nonce||cast.expiry<now||!cast.passengers.contains(passenger.getUUID())||CASTS.get(cast.caster)!=cast)return;
        if(!accepted){CASTS.remove(cast.caster,cast);cast.passengers.forEach(REQUESTS::remove);return;}
        cast.accepted.add(passenger.getUUID());
    }
    public static void clearExpired(ServerPlayer player){long now=player.serverLevel().getGameTime();CastConsent own=CASTS.get(player.getUUID());if(own!=null&&own.expiry<now){CASTS.remove(player.getUUID(),own);own.passengers.forEach(REQUESTS::remove);}CastConsent request=REQUESTS.get(player.getUUID());if(request!=null&&request.expiry<now){REQUESTS.remove(player.getUUID(),request);CASTS.remove(request.caster,request);request.passengers.forEach(REQUESTS::remove);}}
    private static void cancel(CastConsent cast){if(cast==null)return;CASTS.remove(cast.caster,cast);cast.passengers.forEach(id->REQUESTS.remove(id,cast));}
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(LocationTeleportActionPacket p, IPayloadContext c){c.enqueueWork(()->{
        if (!(c.player() instanceof ServerPlayer player) || p.action<QUERY || p.action>PERFORM) return;
        long now=player.serverLevel().getGameTime();
        if(!PayloadRateLimiter.allow(player.getUUID(),"location_teleport",now,10,5))return;
        PlayerAbilityData d=player.getData(AcademyAttachments.PLAYER_ABILITY);
        Skill skill=SkillRegistry.getSkill("location_teleport");
        if(!d.isAbilityActive()||skill==null||!d.hasLearnedSkill(skill.getId()))return;
        if(p.action==PERFORM&&AbilityInterferenceService.isInterfered(player)){
            forgetPlayer(player.getUUID());
            AbilityInterferenceService.notifyBlocked(player);
            return;
        }
        boolean changed=false;
        if(p.action==ADD){
            String n=p.name.strip(); if(n.isEmpty()||n.length()>32)return;
            changed=d.addTeleportLocation(new TeleportLocation(n,player.level().dimension().location().toString(),player.getX(),player.getY(),player.getZ()));
        } else if(p.action==REMOVE){changed=d.removeTeleportLocation(p.index);
        } else if(p.action==PERFORM){changed=perform(player,d,skill,p.index);}
        if(p.action==QUERY||changed)SafePayloadSender.send(player,new LocationTeleportSyncPacket(d.getTeleportLocations()));
        if(changed)d.syncTo(player);
    });}
    private static boolean perform(ServerPlayer p,PlayerAbilityData d,Skill skill,int index){
        if(index<0||index>=d.getTeleportLocations().size()||d.isOnCooldown(skill.getId()))return false;
        TeleportLocation l=PlayerAbilityData.sanitizeLocation(d.getTeleportLocations().get(index)); if(l==null)return false;
        ResourceLocation id=ResourceLocation.tryParse(l.dimension()); if(id==null)return false;
        ServerLevel target=p.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,id)); if(target==null)return false;
        boolean cross=target!=p.serverLevel(); float exp=d.getProficiency(skill.getId()); if(cross&&exp<=.8f)return false;
        Vec3 dest=new Vec3(l.x(),l.y(),l.z());
        if(dest.y<target.getMinBuildHeight()||dest.y>=target.getMaxBuildHeight()
                ||!target.getWorldBorder().isWithinBounds(dest))return false;
        // 1.0.7 teleported every nearby living entity whose width^2*height was
        // below 80, not just the caster's mount graph. Keep that gameplay while
        // retaining a hard workload cap and explicit consent for other players.
        java.util.LinkedHashSet<Entity> graph=new java.util.LinkedHashSet<>();graph.add(p);
        AABB nearby=new AABB(p.getX()-5,p.getY()-5,p.getZ()-5,p.getX()+5,p.getY()+5,p.getZ()+5);
        for(LivingEntity entity:p.serverLevel().getEntitiesOfClass(LivingEntity.class,nearby,
                entity->entity!=p&&entity.isAlive()&&p.distanceToSqr(entity)<=25
                        &&entity.getBbWidth()*entity.getBbWidth()*entity.getBbHeight()<80f)){
            if(graph.size()>=64)return false;
            graph.add(entity);
        }
        long gameTime=p.serverLevel().getGameTime();
        java.util.Set<UUID> passengers=java.util.concurrent.ConcurrentHashMap.newKeySet();
        for(Entity e:graph)if(e instanceof ServerPlayer other&&other!=p)passengers.add(other.getUUID());
        LocationConsentScope scope=scope(p,index,l,graph);
        if(!passengers.isEmpty()){
            CastConsent cast=CASTS.get(p.getUUID());
            if(cast==null||cast.expiry<gameTime||!cast.scope.equals(scope)||!cast.passengers.equals(passengers)){
                cancel(cast);
                long nonce;do nonce=ThreadLocalRandom.current().nextLong();while(nonce==0);
                cast=new CastConsent(p.getUUID(),nonce,scope,gameTime+200,java.util.Set.copyOf(passengers),java.util.concurrent.ConcurrentHashMap.newKeySet());
                CASTS.put(p.getUUID(),cast);
                for(UUID id2:passengers){ServerPlayer other=p.server.getPlayerList().getPlayer(id2);if(other==null){CASTS.remove(p.getUUID(),cast);return false;}REQUESTS.put(id2,cast);SafePayloadSender.send(other,new LocationConsentRequestPacket(p.getUUID(),p.getGameProfile().getName(),nonce,index,l.dimension(),cast.expiry));}
                return false;
            }
            if(!cast.accepted.containsAll(passengers))return false;
            // Recompute immediately at the authorization/commit boundary. No accepted nonce can
            // survive a destination edit, entity add/remove/type/owner change, or riding reparent.
            if(!cast.scope.equals(scope(p,index,l,graph))){cancel(cast);return false;}
            // One shot: consume before any fallible destination hook/move, so failure cannot reuse it.
            CASTS.remove(p.getUUID(),cast);cast.passengers.forEach(REQUESTS::remove);
        }
        Vec3 origin=p.position();
        java.util.List<Move> moves=new java.util.ArrayList<>(graph.size());
        for(Entity e:graph)moves.add(new Move(e,dest.add(e.position().subtract(origin)),
                (ServerLevel)e.level(),e.position(),e.getVehicle()));
        java.util.LinkedHashSet<LocationTeleportChunkPlan.Chunk> destinationChunks=new java.util.LinkedHashSet<>();
        // Validate the complete destination geometry before loading, resource debit, or movement.
        for(Move m:moves){
            BlockPos mp=BlockPos.containing(m.destination);
            AABB box=m.entity.getBoundingBox().move(m.destination.subtract(m.entity.position()));
            if(!finite(box)||box.minY<target.getMinBuildHeight()||box.maxY>target.getMaxBuildHeight()
                    ||!insideBorder(target.getWorldBorder(),box)||!target.mayInteract(p,mp)
                    ||!LocationTeleportChunkPlan.addBox(destinationChunks,box.minX,box.minZ,box.maxX,box.maxZ,
                    MAX_DESTINATION_CHUNKS))return false;
        }
        float distance=(float)Math.min(800,Math.sqrt(p.distanceToSqr(dest)));
        float cp=com.mohistmc.academy.config.DynamicSkillRules.cp(skill.getId(),(200-50*exp)*(cross?2:1)*Math.max(8,(float)Math.sqrt(distance)));
        float overload=com.mohistmc.academy.config.DynamicSkillRules.overload(skill.getId(),240);
        if(!com.mohistmc.academy.config.DynamicSkillRules.enabled(skill.getId()))return false;
        // 1.0.7's UI preflight checked CP only; performWithForce then clamped
        // overload instead of rejecting a valid saved-location cast.
        if(!d.isDevMode()&&d.getCurrentCp()<cp)return false;
        UUID ticketKey=UUID.randomUUID();
        java.util.ArrayList<ChunkPos> ticketed=new java.util.ArrayList<>(destinationChunks.size());
        try {
            // Previously visited saved locations may be unloaded. Load only the finite, fully
            // validated destination set, keep it resident through commit, and release every ticket.
            for(LocationTeleportChunkPlan.Chunk chunk:destinationChunks){
                ChunkPos cpPos=new ChunkPos(chunk.x(),chunk.z());
                target.getChunkSource().addRegionTicket(LOCATION_TELEPORT_TICKET,cpPos,1,ticketKey);
                ticketed.add(cpPos);
                if(target.getChunkSource().getChunk(chunk.x(),chunk.z(),ChunkStatus.FULL,true)==null)return false;
            }
            // Collision is checked only after all chunks intersecting every target AABB are FULL.
            for(Move m:moves){
                AABB box=m.entity.getBoundingBox().move(m.destination.subtract(m.entity.position()));
                if(!target.noCollision(m.entity,box))return false;
            }
            boolean committed=commitMoves(moves,target);
            if(!committed)return false;
            com.mohistmc.academy.advancement.LegacyAdvancementBridge.teleported(p);
            if(!d.consumeDynamicForced(cp,overload))return false;
            // Final 1.12.2 commit 071b5db8 moved this measurement before the
            // entity graph, restoring the intended long-distance reward.
            com.mohistmc.academy.config.DynamicSkillRules.addExp(
                    p,d,skill.getId(),legacyProficiencyIncrement(distance));
            d.setCooldown(skill.getId(),(int)(30-10*d.getProficiency(skill.getId())));
            return true;
        } finally {
            for(ChunkPos chunk:ticketed)
                target.getChunkSource().removeRegionTicket(LOCATION_TELEPORT_TICKET,chunk,1,ticketKey);
        }
    }
    static float legacyProficiencyIncrement(float preTeleportDistance){
        return preTeleportDistance>=200f?.03f:.015f;
    }
    private static boolean finite(AABB box){return Double.isFinite(box.minX)&&Double.isFinite(box.minY)&&Double.isFinite(box.minZ)
            &&Double.isFinite(box.maxX)&&Double.isFinite(box.maxY)&&Double.isFinite(box.maxZ);}
    private static boolean insideBorder(WorldBorder border,AABB box){return box.minX>=border.getMinX()&&box.maxX<=border.getMaxX()
            &&box.minZ>=border.getMinZ()&&box.maxZ<=border.getMaxZ();}
    static LocationConsentScope scope(ServerPlayer caster,int index,TeleportLocation location,java.util.Set<Entity> graph){
        java.util.Set<LocationConsentScope.EntitySnapshot> entities=new java.util.HashSet<>();
        java.util.Set<LocationConsentScope.RidingEdge> edges=new java.util.HashSet<>();
        for(Entity entity:graph){
            UUID owner=entity instanceof TamableAnimal tame?tame.getOwnerUUID():null;
            ResourceLocation type=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            entities.add(new LocationConsentScope.EntitySnapshot(entity.getUUID(),type==null?"":type.toString(),owner));
            Entity vehicle=entity.getVehicle();
            if(vehicle!=null&&graph.contains(vehicle))edges.add(new LocationConsentScope.RidingEdge(entity.getUUID(),vehicle.getUUID()));
        }
        return new LocationConsentScope(caster.getUUID(),LocationConsentScope.LocationSnapshot.of(index,location.name(),
                location.dimension(),location.x(),location.y(),location.z()),entities,edges);
    }
    /**
     * Commit the already validated graph while following the replacement entity
     * created by vanilla for a non-player cross-dimension teleport.  Keeping the
     * stale origin instance here makes verification fail and makes rollback leave
     * the real destination clone behind.
     */
    private static boolean commitMoves(java.util.List<Move> moves,ServerLevel target){
        // Entity.changeDimension recursively transfers passengers. Detach the
        // whole validated graph first so each journal entry moves exactly once;
        // the saved UUID edges are restored only after commit/rollback.
        for(Move move:moves)if(move.entity.isPassenger())move.entity.stopRiding();
        return TeleportTransaction.commit(moves,m->{
            if(!move(m,target,m.destination))return false;
            m.entity.fallDistance=0;
            return true;
        },m->move(m,m.originLevel,m.origin),()->restoreRiding(moves,target),()->restoreRiding(moves,null));
    }

    /** Package-private real-entity seam used by the cross-dimension GameTest. */
    static boolean commitEntityGraph(java.util.Collection<? extends Entity> graph,
                                     ServerLevel target,Vec3 anchorOrigin,Vec3 destination){
        return commitEntityGraphDetailed(graph,target,anchorOrigin,destination).committed();
    }

    /** Returns the journal's actual replacement handles for strict GameTest diagnostics. */
    static GraphCommitResult commitEntityGraphDetailed(java.util.Collection<? extends Entity> graph,
                                                        ServerLevel target,Vec3 anchorOrigin,Vec3 destination){
        java.util.List<Move> moves=new java.util.ArrayList<>(graph.size());
        for(Entity entity:graph){
            if(!(entity.level() instanceof ServerLevel originLevel))
                return new GraphCommitResult(false,java.util.List.of());
            moves.add(new Move(entity,destination.add(entity.position().subtract(anchorOrigin)),
                    originLevel,entity.position(),entity.getVehicle()));
        }
        boolean committed=commitMoves(moves,target);
        return new GraphCommitResult(committed,moves.stream().map(move->move.entity).toList());
    }

    record GraphCommitResult(boolean committed,java.util.List<Entity> trackedEntities){}

    /** Rebuild only relationships whose two endpoints completed in the same level. */
    private static void restoreRiding(java.util.List<Move> moves,ServerLevel requiredLevel){
        java.util.Map<UUID,Entity> moved=new java.util.HashMap<>();
        for(Move m:moves)if(!m.entity.isRemoved())moved.put(m.id,m.entity);
        for(Move m:moves){
            Entity vehicle=m.vehicleId==null?null:moved.get(m.vehicleId);
            if(vehicle!=null&&!m.entity.isRemoved()&&!vehicle.isRemoved()
                    &&m.entity.level()==vehicle.level()
                    &&(requiredLevel==null||m.entity.level()==requiredLevel))
                m.entity.startRiding(vehicle,true);
        }
    }

    /** Teleport and replace the journal handle with the actual post-teleport entity. */
    private static boolean move(Move move,ServerLevel level,Vec3 at){
        Entity before=move.entity;
        boolean accepted;
        if(before instanceof ServerPlayer player){
            accepted=player.teleportTo(level,at.x,at.y,at.z,java.util.Set.of(),
                    player.getYRot(),player.getXRot());
        }else if(before.level()!=level){
            Entity replacement=moveNonPlayerAcrossDimensions(before,level,at);
            accepted=replacement!=null;
            if(replacement!=null)move.entity=replacement;
        }else{
            before.teleportTo(at.x,at.y,at.z);
            accepted=true;
        }

        if(move.entity==before){
            Entity actual=level.getEntity(move.id);
            if(actual!=null){
                move.entity=actual;
            }else if(before.level() instanceof ServerLevel currentLevel){
                Entity unchanged=currentLevel.getEntity(move.id);
                if(unchanged!=null)move.entity=unchanged;
            }
        }
        return accepted&&!move.entity.isRemoved()&&move.entity.level()==level
                &&move.entity.position().distanceToSqr(at)<=.25;
    }

    /**
     * Vanilla's {@code changeDimension} removes the source before calling the
     * void {@code addDuringTeleport}. If a NeoForge join hook rejects the target
     * add, it can therefore return a live but unregistered replacement and lose
     * the source. Build the same NBT-preserving replacement explicitly, commit
     * it through the boolean add boundary, and only then retire the source.
     */
    private static Entity moveNonPlayerAcrossDimensions(Entity before,ServerLevel target,Vec3 at){
        if(!net.neoforged.neoforge.common.CommonHooks.onTravelToDimension(
                before,target.dimension()))return null;
        Entity replacement=before.getType().create(target);
        if(replacement==null)return null;
        replacement.restoreFrom(before);
        replacement.moveTo(at.x,at.y,at.z,before.getYRot(),before.getXRot());
        replacement.setYHeadRot(before.getYHeadRot());
        replacement.setDeltaMovement(Vec3.ZERO);
        if(!replacement.getUUID().equals(before.getUUID())
                || !target.addWithUUID(replacement)
                || !replacement.isAddedToLevel()){
            replacement.discard();
            return null;
        }
        before.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        if(before instanceof Leashable leashable)leashable.dropLeash(true,false);
        return replacement;
    }

    private static final class Move{
        private Entity entity;
        private final UUID id;
        private final Vec3 destination;
        private final ServerLevel originLevel;
        private final Vec3 origin;
        private final UUID vehicleId;

        private Move(Entity entity,Vec3 destination,ServerLevel originLevel,Vec3 origin,Entity vehicle){
            this.entity=entity;
            this.id=entity.getUUID();
            this.destination=destination;
            this.originLevel=originLevel;
            this.origin=origin;
            this.vehicleId=vehicle==null?null:vehicle.getUUID();
        }
    }
}
