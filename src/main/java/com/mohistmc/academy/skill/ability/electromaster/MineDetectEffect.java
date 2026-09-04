package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.network.MineDetectResultPacket;
import com.mohistmc.academy.network.SafePayloadSender;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademySounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import com.mohistmc.academy.AcademyCraft;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 矿物探测 —— 致盲玩家，高亮显示周围矿物位置 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public class MineDetectEffect implements SkillEffect {

    private static final int BLIND_TIME = 100;
    // Final 1.12.2 commit 22b5be7b raised WorldUtils' deterministic result limit to 8400.
    private static final int MAX_ORES = 8400;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private record Session(float range, boolean advanced, long expiresAt, long nextScanAt,
                           List<MineDetectResultPacket.Entry> lastResults) {}

    @Override
    public String getId() {
        return "mine_detect";
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        return DynamicSkillRules.canPay(data, getId(), lerpf(1500, 1000, exp), lerpf(200, 180, exp));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency(getId());
        float cp = lerpf(1500, 1000, proficiency);
        float overload = lerpf(200, 180, proficiency);
        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;
        float range = ElectromasterRules.mineDetectRange(proficiency);
        boolean advanced = proficiency > 0.5f && data.getPlayerLevel() >= 4;

        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TIME, 0));

        ServerLevel level = player.serverLevel();
        AcademySounds.playSound(level, player.getX(), player.getY(), player.getZ(),
                AcademySounds.EM_MINEDETECT, SoundSource.PLAYERS, 0.5f, 1.0f);

        List<MineDetectResultPacket.Entry> results = scanSnapshot(player, range, advanced);
        sendSnapshot(player, range, results);
        long now = level.getGameTime();
        SESSIONS.put(player.getUUID(),new Session(range,advanced,now+BLIND_TIME,now+5,results));
        if (!data.isDevMode()) DynamicSkillRules.addExp(player,data, getId(), 0.008f);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        Session s=SESSIONS.get(player.getUUID());if(s==null)return;
        long now=player.serverLevel().getGameTime();
        if(!player.isAlive()||now>=s.expiresAt
                ||AbilityInterferenceService.isInterfered(player)){
            SESSIONS.remove(player.getUUID());
            SafePayloadSender.send(player,new MineDetectResultPacket(List.of(),0));
            return;
        }
        if(now>=s.nextScanAt){
            List<MineDetectResultPacket.Entry> results=scanSnapshot(player,s.range,s.advanced);
            if(!results.equals(s.lastResults)) sendSnapshot(player,s.range,results);
            SESSIONS.put(player.getUUID(),new Session(s.range,s.advanced,s.expiresAt,now+5,results));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){SESSIONS.clear();}

    private static List<MineDetectResultPacket.Entry> scanSnapshot(ServerPlayer player,float range,boolean advanced){
        ServerLevel level=player.serverLevel();
        List<MineDetectResultPacket.Entry> results = new ArrayList<>();
        double centerX=player.getX(),centerY=player.getY(),centerZ=player.getZ();
        int minX=Mth.floor(centerX-range),minY=Mth.floor(centerY-range),minZ=Mth.floor(centerZ-range);
        int maxX=Mth.ceil(centerX+range),maxY=Mth.ceil(centerY+range),maxZ=Mth.ceil(centerZ+range);
        double rangeSq=range*range;BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();

        // Preserve WorldUtils' x/y/z iteration and its exact player-coordinate sphere.
        for (int x=minX;x<=maxX&&results.size()<MAX_ORES;x++) {
            for (int y=minY;y<=maxY&&results.size()<MAX_ORES;y++) {
                for (int z=minZ;z<=maxZ&&results.size()<MAX_ORES;z++) {
                    double dx=x-centerX,dy=y-centerY,dz=z-centerZ;
                    if (dx*dx+dy*dy+dz*dz>rangeSq) continue;
                    pos.set(x,y,z);
                    // Detection must never force-load chunks around a player.
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (isOre(state)) {
                        int harvestLevel = advanced ? getHarvestLevel(state) : 0;
                        results.add(new MineDetectResultPacket.Entry(pos.immutable(), harvestLevel));
                    }
                }
            }
        }
        return List.copyOf(results);
    }

    private static void sendSnapshot(ServerPlayer player,float range,List<MineDetectResultPacket.Entry> results){
        SafePayloadSender.send(player, new MineDetectResultPacket(results, range));
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(900, 400, proficiency);
    }

    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(AcademyBlocks.CRYSTAL_ORE.get())
                || state.is(AcademyBlocks.IMAGSIL_ORE.get())
                || state.is(AcademyBlocks.RESO_ORE.get());
    }

    /**
     * 获取矿石的挖掘等级索引（用于颜色区分）
     */
    private static int getHarvestLevel(BlockState state) {
        return ElectromasterRules.mineDetectColorLevel(
                state.is(BlockTags.INCORRECT_FOR_WOODEN_TOOL),
                state.is(BlockTags.INCORRECT_FOR_STONE_TOOL));
    }
}
