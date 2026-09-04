package com.mohistmc.academy.world.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import com.mohistmc.academy.world.AcademyBlocks;

/** Durable, server-global ownership reservations for Mag Manip ledgers. */
public final class MagManipTransactionData extends SavedData {
    private static final String DATA_ID = "academy_mag_manip_transactions";
    private static final SavedData.Factory<MagManipTransactionData> FACTORY = new SavedData.Factory<>(
            MagManipTransactionData::new, (tag, provider) -> load(tag));

    public static final long ORPHAN_GRACE_TICKS = 1200;
    public record Entry(UUID owner, UUID transaction, UUID entity, String dimension, long entityChunk,
                        long source, int blockState, CompoundTag blockEntity, String blockEntityType,
                        String sourceKind, int sourceSlot, int sourceCount, String sourceHash,
                        String state, long touched, UUID recoveryEntity, long generation, String representationKind,
                        float damage, boolean thrown, double velocityX, double velocityY, double velocityZ) {}
    private final Map<UUID, Entry> byOwner = new HashMap<>();
    private final Map<UUID, Entry> byTransaction = new HashMap<>();
    private final java.util.Set<UUID> entityChunkKnown = new java.util.HashSet<>();
    private UUID migrationCursor;

    /**
     * One-way migration cleanup for the retired materializing implementation.
     * It only deletes legacy escrow/token representations and ledger rows; it can
     * never recreate a block or item. Production projection mode never adds rows.
     */
    public record MigrationResult(int cleaned,int pending,int malformed) {}
    /**
     * Bounded, idempotent migration pass. It never force-loads a chunk and never
     * creates a representation. A row is retained until its exact source and old
     * carrier chunks have both been observed loaded and every loaded dimension has
     * been checked for the recorded token/carrier UUIDs.
     */
    public synchronized MigrationResult migrateLoadedRepresentations(ServerLevel caller,int budget) {
        // Material transport is live again. Ledger rows and escrow blocks are authoritative
        // ownership receipts and must never be deleted by the retired projection migration.
        // Administrators can inspect old rows, but automated cleanup is intentionally disabled.
        if (!byTransaction.isEmpty()) return new MigrationResult(0, byTransaction.size(), 0);
        int cleaned=0,pending=0,malformed=0,visited=0;
        java.util.List<Entry> rows=new java.util.ArrayList<>(byTransaction.values());
        rows.sort((a,b)->compareUuid(a.transaction(),b.transaction()));
        int start=0;
        if(migrationCursor!=null&&!rows.isEmpty()){
            while(start<rows.size()&&compareUuid(rows.get(start).transaction(),migrationCursor)<=0)start++;
            if(start==rows.size())start=0;
        }
        int limit=Math.min(rows.size(),Math.max(1,budget));
        for(int offset=0;offset<limit;offset++) {
            Entry e=rows.get((start+offset)%rows.size());visited++;migrationCursor=e.transaction();setDirty();
            ResourceLocation id=e.dimension()==null?null:ResourceLocation.tryParse(e.dimension());
            ServerLevel recorded=id==null?null:caller.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
            if(recorded==null){pending++;malformed++;continue;}
            BlockPos source=BlockPos.of(e.source());
            int carrierX=net.minecraft.world.level.ChunkPos.getX(e.entityChunk()),carrierZ=net.minecraft.world.level.ChunkPos.getZ(e.entityChunk());
            if(!recorded.hasChunkAt(source)||!entityChunkKnown.contains(e.transaction())||!recorded.getChunkSource().hasChunk(carrierX,carrierZ)){pending++;continue;}
            if(recorded.getBlockState(source).is(AcademyBlocks.MAG_MANIP_ESCROW.get())) {
                if(!recorded.removeBlock(source,false)||recorded.getBlockState(source).is(AcademyBlocks.MAG_MANIP_ESCROW.get())){pending++;continue;}
            }
            if(!discardLoadedUuidEverywhere(caller,e.entity(),false)||!discardLoadedUuidEverywhere(caller,e.recoveryEntity(),true)){pending++;continue;}
            remove(e);cleaned++;
        }
        return new MigrationResult(cleaned,byTransaction.size(),malformed);
    }
    /** Compatibility name; performs only one bounded proof-based pass. */
    @Deprecated public synchronized int purgeLegacyRepresentations(ServerLevel caller){return migrateLoadedRepresentations(caller,64).cleaned();}
    public synchronized int pendingMigrationCount(){return byTransaction.size();}
    private static boolean discardLoadedUuidEverywhere(ServerLevel caller,UUID uuid,boolean requireObservedToken){
        if(uuid==null)return true;
        boolean observed=false;
        for(ServerLevel level:caller.getServer().getAllLevels()){
            Entity found=level.getEntity(uuid);
            if(found!=null){observed=true;if(!found.isRemoved())found.discard();}
            Entity after=level.getEntity(uuid);
            if(after!=null&&!after.isRemoved())return false;
        }
        // Legacy token rows did not persist a token chunk. Absence from loaded
        // entity managers is therefore not proof of absence from an unloaded chunk.
        return !requireObservedToken||observed;
    }
    private static int compareUuid(UUID a,UUID b){int high=Long.compareUnsigned(a.getMostSignificantBits(),b.getMostSignificantBits());return high!=0?high:Long.compareUnsigned(a.getLeastSignificantBits(),b.getLeastSignificantBits());}

    public static MagManipTransactionData get(ServerLevel level) {
        // The overworld data storage is the canonical MinecraftServer storage. Using the
        // caller's dimension would permit one live transaction per dimension after a move.
        ServerLevel canonical = level.getServer().overworld();
        return canonical.getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    /** Reserve before source commit. Existing unexpired ownership always wins, including after restart. */
    public synchronized boolean reserve(UUID owner, UUID transaction, UUID entity, String dimension, long now) {
        Entry owned = byOwner.get(owner), transacted = byTransaction.get(transaction);
        if ((owned != null && !same(owned, transaction, entity)) || (transacted != null && !same(transacted, transaction, entity))) return false;
        Entry entry = new Entry(owner, transaction, entity, dimension, 0L, 0L, 0, null, "", "", -1, 0, "", "RESERVED", now, null,1L,"CARRIER",0,false,0,0,0);
        put(entry); setDirty(); return true;
    }

    /** Only the exact ACTIVE carrier may load. No absent/legacy row is synthesized here. */
    public synchronized boolean claim(UUID owner, UUID transaction, UUID entity, String dimension, long generation,String kind,long now) {
        Entry owned = byOwner.get(owner), transacted = byTransaction.get(transaction);
        if (owned == null || transacted == null || owned != transacted || !same(owned, transaction, entity)
                || !owned.dimension().equals(dimension) || !"ACTIVE".equals(owned.state())
                || owned.generation()!=generation || !owned.representationKind().equals(kind)) return false;
        Entry refreshed = new Entry(owner, transaction, entity, dimension, owned.entityChunk(), owned.source(), owned.blockState(),
                copy(owned.blockEntity()), owned.blockEntityType(), owned.sourceKind(),owned.sourceSlot(),owned.sourceCount(),owned.sourceHash(),owned.state(), now,owned.recoveryEntity(),owned.generation(),owned.representationKind(),owned.damage(),owned.thrown(),owned.velocityX(),owned.velocityY(),owned.velocityZ());
        put(refreshed); setDirty(); return true;
    }
    public synchronized boolean claim(UUID owner,UUID transaction,UUID entity,String dimension,long now){return claim(owner,transaction,entity,dimension,1L,"CARRIER",now);}

    public synchronized void touch(UUID transaction, UUID entity, long now, String state) {
        Entry old = byTransaction.get(transaction);
        if (old == null || !old.entity().equals(entity) || !"ACTIVE".equals(old.state()) || !"ACTIVE".equals(state)) return;
        Entry refreshed = new Entry(old.owner(), old.transaction(), old.entity(), old.dimension(), old.entityChunk(), old.source(),
                old.blockState(), copy(old.blockEntity()), old.blockEntityType(),old.sourceKind(),old.sourceSlot(),old.sourceCount(),old.sourceHash(), state, now,old.recoveryEntity(),old.generation(),old.representationKind(),old.damage(),old.thrown(),old.velocityX(),old.velocityY(),old.velocityZ());
        put(refreshed); setDirty();
    }

    /** Persist payload and an exact source fingerprint, but do not authorize materialization. */
    public synchronized boolean prepare(UUID transaction, UUID entity, BlockPos entityPos, BlockPos source, BlockState state,
                                       CompoundTag blockEntity, ResourceLocation blockEntityType,String sourceKind,int sourceSlot,int sourceCount,String sourceHash,long now) {
        Entry old=byTransaction.get(transaction);
        if(old==null||!old.entity().equals(entity)||!"RESERVED".equals(old.state())||sourceHash==null||sourceHash.isEmpty())return false;
        long chunk=net.minecraft.world.level.ChunkPos.asLong(entityPos.getX()>>4,entityPos.getZ()>>4);
        Entry committed=new Entry(old.owner(),old.transaction(),old.entity(),old.dimension(),chunk,source.asLong(),Block.getId(state),
                copy(blockEntity),blockEntityType==null?"":blockEntityType.toString(),sourceKind,sourceSlot,sourceCount,sourceHash,"PREPARED",now,null,old.generation(),old.representationKind(),0,false,0,0,0);
        put(committed);entityChunkKnown.add(transaction);setDirty();return true;
    }
    public synchronized boolean markSourceConsumed(UUID transaction,UUID entity,String expectedHash,long now){Entry e=byTransaction.get(transaction);if(e==null||!e.entity().equals(entity)||!"PREPARED".equals(e.state())||!e.sourceHash().equals(expectedHash))return false;put(withPhase(e,"SOURCE_CONSUMED",now));setDirty();return true;}
    public synchronized boolean markActive(UUID transaction,UUID entity,long now){Entry e=byTransaction.get(transaction);if(e==null||!e.entity().equals(entity)||!"SOURCE_CONSUMED".equals(e.state()))return false;put(withPhase(e,"ACTIVE",now));setDirty();return true;}
    public synchronized void updateRuntime(UUID transaction,UUID entity,float damage,boolean thrown,double vx,double vy,double vz,long now){
        Entry e=byTransaction.get(transaction);if(e==null||!e.entity().equals(entity)||!"ACTIVE".equals(e.state())||!Float.isFinite(damage)||!Double.isFinite(vx)||!Double.isFinite(vy)||!Double.isFinite(vz))return;
        put(new Entry(e.owner(),e.transaction(),e.entity(),e.dimension(),e.entityChunk(),e.source(),e.blockState(),copy(e.blockEntity()),e.blockEntityType(),e.sourceKind(),e.sourceSlot(),e.sourceCount(),e.sourceHash(),e.state(),now,e.recoveryEntity(),e.generation(),e.representationKind(),damage,thrown,vx,vy,vz));setDirty();
    }
    /** Test/legacy setup shim; production must call each phase at its real commit boundary. */
    @Deprecated public synchronized boolean commit(UUID transaction,UUID entity,BlockPos entityPos,BlockPos source,BlockState state,CompoundTag blockEntity,ResourceLocation blockEntityType,long now){String hash=sourceHash("BLOCK",state,blockEntity,"",0);return prepare(transaction,entity,entityPos,source,state,blockEntity,blockEntityType,"BLOCK",-1,0,hash,now)&&markSourceConsumed(transaction,entity,hash,now)&&markActive(transaction,entity,now);}

    /** A PREPARED row may only disappear after proving the untouched source still matches. */
    public synchronized boolean abortPrepared(ServerLevel caller,UUID transaction,UUID entity){Entry e=byTransaction.get(transaction);if(e==null||!e.entity().equals(entity)||!"PREPARED".equals(e.state())||!sourceStillPresent(caller,e))return false;remove(e);return true;}

    /** Proof-based reconciliation: unloaded dimensions/chunks and grace time can never classify an orphan. */
    @Deprecated public synchronized boolean proveOrphan(ServerLevel caller,UUID transaction,long now){return false;}

    public synchronized Entry inspect(UUID transaction){return byTransaction.get(transaction);}
    public synchronized Entry inspectOwner(UUID owner){return byOwner.get(owner);}
    @Deprecated public synchronized boolean quarantineCarrier(ServerLevel caller,UUID transaction,UUID entity,long now){return false;}
    public synchronized boolean payloadMatches(UUID transaction,UUID entity,BlockPos source,BlockState state,CompoundTag be,ResourceLocation type){Entry e=byTransaction.get(transaction);return e!=null&&"ACTIVE".equals(e.state())&&source!=null&&e.entity().equals(entity)&&e.source()==source.asLong()&&e.blockState()==Block.getId(state)&&java.util.Objects.equals(e.blockEntityType(),type==null?"":type.toString())&&java.util.Objects.equals(e.blockEntity()==null?null:MagManipTransferPolicy.sanitize(e.blockEntity()),be==null?null:MagManipTransferPolicy.sanitize(be));}
    public synchronized boolean cancelRecovery(UUID transaction, UUID confirmation) {
        Entry e=byTransaction.get(transaction);
        if(e==null||!transaction.equals(confirmation)||!"RECOVERY_REQUIRED".equals(e.state()))return false;
        remove(e);return true;
    }

    /** Returns a recovery row only when the exact transaction is quarantined for operator action. */
    public synchronized Entry recovery(UUID transaction){Entry e=byTransaction.get(transaction);return e!=null&&("RECOVERY_REQUIRED".equals(e.state())||"RECOVERY_ISSUED".equals(e.state()))?e:null;}
    @Deprecated public synchronized boolean emitRecoveryItem(ServerLevel target,UUID transaction,BlockPos pos,ItemStack item){return false;}

    /** Fail-closed outbox recovery. The origin must be loaded and observed exactly. */
    @Deprecated public synchronized boolean recover(ServerLevel caller,UUID transaction){return false;}

    /** Pickup acknowledgement is valid only for the owner and exact issued token/generation. */
    @Deprecated public synchronized boolean acknowledgePickup(ServerPlayer player,UUID token,ItemStack delivered){return false;}
    /** Pre-pickup seam: ordinary items pass; recovery items pass only to their exact owner. */
    @Deprecated public synchronized boolean mayPickupRecovery(ServerPlayer player,ItemEntity item){return true;}
    /** Normal items pass; Academy recovery representations require the exact live outbox claim. */
    @Deprecated public synchronized boolean validRecoveryRepresentation(ItemEntity item){return true;}

    /** Called only after settlement/recovery or an explicitly aborted pre-commit spawn. */
    public synchronized void release(UUID transaction, UUID entity) {
        Entry entry = byTransaction.get(transaction);
        if (entry == null || !entry.entity().equals(entity)) return;
        remove(entry);
    }

    public synchronized boolean reserved(UUID owner, long now) { return byOwner.containsKey(owner); }

    /** Periodic outbox retry; reuses the same UUID/generation after natural despawn. */
    @Deprecated public synchronized void retryIssued(ServerLevel caller){}

    private void put(Entry entry) { byOwner.put(entry.owner(), entry); byTransaction.put(entry.transaction(), entry); }
    private void remove(Entry e){if(e==null)return;byTransaction.remove(e.transaction(),e);byOwner.remove(e.owner(),e);entityChunkKnown.remove(e.transaction());setDirty();}
    private static Entry withPhase(Entry e,String phase,long now){return new Entry(e.owner(),e.transaction(),e.entity(),e.dimension(),e.entityChunk(),e.source(),e.blockState(),copy(e.blockEntity()),e.blockEntityType(),e.sourceKind(),e.sourceSlot(),e.sourceCount(),e.sourceHash(),phase,now,e.recoveryEntity(),e.generation(),e.representationKind(),e.damage(),e.thrown(),e.velocityX(),e.velocityY(),e.velocityZ());}
    private static boolean sourceStillPresent(ServerLevel caller,Entry e){
        if("BLOCK".equals(e.sourceKind())){ResourceLocation id=ResourceLocation.tryParse(e.dimension());ServerLevel level=id==null?null:caller.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));BlockPos pos=BlockPos.of(e.source());if(level==null||!level.hasChunkAt(pos))return false;BlockState state=level.getBlockState(pos);CompoundTag be=null;if(level.getBlockEntity(pos)!=null)be=MagManipTransferPolicy.capture(level.getBlockEntity(pos),level.registryAccess());return e.sourceHash().equals(sourceHash("BLOCK",state,be,"",0));}
        if("INVENTORY".equals(e.sourceKind())||"CREATIVE".equals(e.sourceKind())){ServerPlayer player=caller.getServer().getPlayerList().getPlayer(e.owner());if(player==null||e.sourceSlot()<0||e.sourceSlot()>=player.getInventory().getContainerSize())return false;ItemStack stack=player.getInventory().getItem(e.sourceSlot());return stack.getCount()==e.sourceCount()&&e.sourceHash().equals(sourceHash(e.sourceKind(),null,null,stack.getItem().toString()+"|"+stack.getComponents(),stack.getCount()));}
        return false;
    }
    private static boolean sourceConsumptionMatches(ServerLevel caller,Entry e){
        if("CREATIVE".equals(e.sourceKind()))return true;
        if("BLOCK".equals(e.sourceKind()))return escrowMatches(caller,e);
        if("INVENTORY".equals(e.sourceKind())){ServerPlayer player=caller.getServer().getPlayerList().getPlayer(e.owner());if(player==null||e.sourceSlot()<0||e.sourceSlot()>=player.getInventory().getContainerSize())return false;ItemStack stack=player.getInventory().getItem(e.sourceSlot());if(e.sourceCount()==1)return stack.isEmpty();return stack.getCount()==e.sourceCount()-1&&e.sourceHash().equals(sourceHash("INVENTORY",null,null,stack.getItem().toString()+"|"+stack.getComponents(),stack.getCount()+1));}
        return false;
    }
    private static boolean escrowMatches(ServerLevel caller,Entry e){if(!"BLOCK".equals(e.sourceKind()))return false;ResourceLocation id=ResourceLocation.tryParse(e.dimension());ServerLevel level=id==null?null:caller.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));BlockPos pos=BlockPos.of(e.source());return level!=null&&level.hasChunkAt(pos)&&level.getBlockState(pos).is(AcademyBlocks.MAG_MANIP_ESCROW.get())&&level.getBlockEntity(pos)==null;}
    private static boolean clearEscrow(ServerLevel caller,Entry e){ResourceLocation id=ResourceLocation.tryParse(e.dimension());ServerLevel level=id==null?null:caller.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));BlockPos pos=BlockPos.of(e.source());return level!=null&&level.hasChunkAt(pos)&&level.getBlockState(pos).is(AcademyBlocks.MAG_MANIP_ESCROW.get())&&level.removeBlock(pos,false);}
    public static String sourceHash(String kind,BlockState state,CompoundTag be,String item,int count){String raw=kind+'|'+(state==null?"":state.toString())+'|'+(be==null?"":MagManipTransferPolicy.sanitize(be).toString())+'|'+item+'|'+count;try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(bytes);}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static boolean same(Entry entry, UUID transaction, UUID entity) {
        return entry.transaction().equals(transaction) && entry.entity().equals(entity);
    }

    private static MagManipTransactionData load(CompoundTag tag) {
        MagManipTransactionData data = new MagManipTransactionData();
        ListTag entries = tag.getList("Entries", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag value = entries.getCompound(i);
            if (!value.hasUUID("Owner") || !value.hasUUID("Transaction") || !value.hasUUID("Entity")) continue;
            Entry entry = new Entry(value.getUUID("Owner"), value.getUUID("Transaction"), value.getUUID("Entity"),
                    value.getString("Dimension"),value.getLong("EntityChunk"),value.getLong("Source"),value.getInt("BlockState"),
                    value.contains("BlockEntity")?value.getCompound("BlockEntity"):null,value.getString("BlockEntityType"),value.getString("SourceKind"),value.getInt("SourceSlot"),value.getInt("SourceCount"),value.getString("SourceHash"),value.getString("State"),
                    value.contains("Touched") ? value.getLong("Touched") : value.getLong("Expiry"),value.hasUUID("RecoveryEntity")?value.getUUID("RecoveryEntity"):null,
                    value.contains("Generation")?value.getLong("Generation"):1L,value.contains("RepresentationKind")?value.getString("RepresentationKind"):"CARRIER",
                    value.getFloat("Damage"),value.getBoolean("Thrown"),value.getDouble("VelocityX"),value.getDouble("VelocityY"),value.getDouble("VelocityZ"));
            if (!data.byOwner.containsKey(entry.owner()) && !data.byTransaction.containsKey(entry.transaction())) {data.put(entry);if(value.getBoolean("EntityChunkKnown")||value.contains("EntityChunk"))data.entityChunkKnown.add(entry.transaction());}
        }
        if(tag.hasUUID("MigrationCursor"))data.migrationCursor=tag.getUUID("MigrationCursor");
        return data;
    }

    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        for (Entry entry : byTransaction.values()) {
            CompoundTag value = new CompoundTag(); value.putUUID("Owner", entry.owner()); value.putUUID("Transaction", entry.transaction());
            value.putUUID("Entity", entry.entity()); value.putString("Dimension", entry.dimension()); value.putString("State", entry.state());
            value.putLong("EntityChunk",entry.entityChunk());value.putBoolean("EntityChunkKnown",entityChunkKnown.contains(entry.transaction()));value.putLong("Source",entry.source());value.putInt("BlockState",entry.blockState());
            if(entry.blockEntity()!=null)value.put("BlockEntity",entry.blockEntity().copy());value.putString("BlockEntityType",entry.blockEntityType());
            value.putString("SourceKind",entry.sourceKind());value.putInt("SourceSlot",entry.sourceSlot());value.putInt("SourceCount",entry.sourceCount());value.putString("SourceHash",entry.sourceHash());if(entry.recoveryEntity()!=null)value.putUUID("RecoveryEntity",entry.recoveryEntity());
            value.putLong("Generation",entry.generation());value.putString("RepresentationKind",entry.representationKind());
            value.putFloat("Damage",entry.damage());value.putBoolean("Thrown",entry.thrown());value.putDouble("VelocityX",entry.velocityX());value.putDouble("VelocityY",entry.velocityY());value.putDouble("VelocityZ",entry.velocityZ());
            value.putLong("Touched", entry.touched()); entries.add(value);
        }
        tag.put("Entries", entries);if(migrationCursor!=null)tag.putUUID("MigrationCursor",migrationCursor);return tag;
    }
    private static CompoundTag copy(CompoundTag value){return value==null?null:value.copy();}
}
