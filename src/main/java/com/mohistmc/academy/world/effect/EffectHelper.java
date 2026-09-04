package com.mohistmc.academy.world.effect;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import com.mohistmc.academy.entity.MeltdownBeamEntity;
import com.mohistmc.academy.entity.MeltdownBarrageEntity;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.entity.LegacyFieldEffectEntity;
import com.mohistmc.academy.entity.BloodSplashEntity;
import com.mohistmc.academy.world.AcademyParticles;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Side-safe visual dispatch. The server emits vanilla particles to tracking clients;
 * no class in this package references net.minecraft.client or a client-only entity.
 */
public final class EffectHelper {
    private EffectHelper() {}

    private static void burst(Level level, ParticleOptions particle, double x, double y, double z,
                              int count, double spread, double speed) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(particle, x, y, z, Math.max(1, count), spread, spread, spread, speed);
        }
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f,
                ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), scale);
    }

    public static void glowBurst(Level l,double x,double y,double z,int n,double size,int color,int life,double spread){
        burst(l, dust(color, (float)Math.max(.25, size)), x,y,z,n,spread,.02);
    }
    public static void smokeBurst(Level l,double x,double y,double z,int n,double spread){burst(l,ParticleTypes.SMOKE,x,y,z,n,spread,.02);}
    private static void field(Level l,double x,double y,double z,int kind,double size,int color,Vec3 direction){field(l,x,y,z,kind,size,color,direction,kind==LegacyFieldEffectEntity.INTENSIFY?15:12);}
    private static void field(Level l,double x,double y,double z,int kind,double size,int color,Vec3 direction,int lifetime){if(l instanceof ServerLevel s){LegacyFieldEffectEntity e=new LegacyFieldEffectEntity(AcademyEntities.LEGACY_FIELD_EFFECT.get(),s).configure(kind,(float)size,color,direction,lifetime);e.setPos(x,y,z);s.addFreshEntity(e);}}
    public static void arcSpark(Level l,double x,double y,double z,int n,double spread){field(l,x,y,z,LegacyFieldEffectEntity.ARC,Math.max(.25,spread*2),0x99ddff,new Vec3(0,1,0));}
    public static void arcSpark(Level l,double x,double y,double z,int n,double spread,int lifetime){field(l,x,y,z,LegacyFieldEffectEntity.ARC,Math.max(.25,spread*2),0x99ddff,new Vec3(0,1,0),lifetime);}
    public static void lightningBurst(Level l,double x,double y,double z){field(l,x,y,z,LegacyFieldEffectEntity.ARC,1.45,0xddeeff,new Vec3(0,1,0));}
    public static void intensifyActivation(Level l,double x,double y,double z){field(l,x,y,z,LegacyFieldEffectEntity.INTENSIFY,1,0x99ddff,new Vec3(0,1,0));}
    public static void meltdownBurst(Level l,double x,double y,double z,int n,double spread){burst(l,AcademyParticles.MELTDOWN.get(),x,y,z,n,spread,.04);}
    public static void meltdownMovingParticle(Level l,Vec3 position,Vec3 velocity){if(l instanceof ServerLevel s)s.sendParticles(AcademyParticles.MELTDOWN.get(),position.x,position.y,position.z,0,velocity.x,velocity.y,velocity.z,1.0);}
    public static void raySpark(Level l,double x,double y,double z,int n){burst(l,AcademyParticles.MELTDOWN.get(),x,y,z,n,.1,.02);}
    public static void mdRay(Level l, Vec3 from, Vec3 to){
        mdRay(l, from, to, 50);
    }
    public static void mdRay(Level l, Vec3 from, Vec3 to, int lifetime){
        mdRay(l, from, to, lifetime, MeltdownBeamEntity.MAIN);
    }
    public static void mdRaySmall(Level l, Vec3 from, Vec3 to){
        mdRay(l, from, to, 14, MeltdownBeamEntity.SMALL);
    }
    public static void mdRaySmall(Level l, Vec3 from, Vec3 to, int lifetime){
        mdRay(l, from, to, lifetime, MeltdownBeamEntity.SMALL);
    }
    public static void barragePreRay(Level l, Vec3 from, Vec3 to, int lifetime){
        mdRay(l, from, to, lifetime, MeltdownBeamEntity.BARRAGE_PRE);
    }
    public static void mineRay(Level l, Vec3 from, Vec3 to, String skillId){
        mineRay(l, from, to, skillId, 6);
    }
    public static void mineRay(Level l, Vec3 from, Vec3 to, String skillId, int lifetime){
        int variant = "mine_ray_luck".equals(skillId) ? MeltdownBeamEntity.LUCK
                : "mine_ray_expert".equals(skillId) ? MeltdownBeamEntity.EXPERT : MeltdownBeamEntity.SMALL;
        mdRay(l, from, to, lifetime, variant);
    }
    public static UUID startFollowingMineRay(ServerPlayer player, String skillId){
        int variant = "mine_ray_luck".equals(skillId) ? MeltdownBeamEntity.LUCK
                : "mine_ray_expert".equals(skillId) ? MeltdownBeamEntity.EXPERT : MeltdownBeamEntity.SMALL;
        MeltdownBeamEntity beam=new MeltdownBeamEntity(AcademyEntities.MELTDOWN_BEAM.get(),player.serverLevel());
        beam.setFollowingPlayer(player.getUUID(),variant);beam.setPos(player.getEyePosition());
        return player.serverLevel().addFreshEntity(beam)?beam.getUUID():null;
    }
    private static void mdRay(Level l, Vec3 from, Vec3 to, int lifetime, int variant){
        if(!(l instanceof ServerLevel s))return;
        Vec3 delta=to.subtract(from);double len=delta.length();if(len<1.0e-4)return;
        MeltdownBeamEntity beam=new MeltdownBeamEntity(AcademyEntities.MELTDOWN_BEAM.get(),s);
        beam.setPos(from.x,from.y,from.z);beam.setBeam(from,delta.scale(1d/len),len,lifetime,variant);s.addFreshEntity(beam);
    }
    public static void teleportBurst(Level l,double x,double y,double z,int n){burst(l,AcademyParticles.TELEPORT.get(),x,y,z,n,.35,.12);}
    public static void shockwaveRing(Level l,double x,double y,double z,int rings,double size){
        field(l,x,y,z,LegacyFieldEffectEntity.SHOCKWAVE,size,0xe8f8ff,new Vec3(0,1,0));
    }
    public static void bloodSplash(Level level,double x,double y,double z,int count,double spread){
        if(!(level instanceof ServerLevel server))return;
        for(int i=0;i<Math.max(1,count);i++){
            double ox=(server.random.nextDouble()*2-1)*spread;
            double oy=(server.random.nextDouble()*2-1)*spread;
            double oz=(server.random.nextDouble()*2-1)*spread;
            bloodSplash(server,x+ox,y+oy,z+oz,.8f+server.random.nextFloat()*.5f);
        }
    }
    public static void bloodSplash(Level level,double x,double y,double z,float scale){
        if(!(level instanceof ServerLevel server))return;
        BloodSplashEntity splash=new BloodSplashEntity(AcademyEntities.BLOOD_SPLASH.get(),server).configure(scale);
        splash.setPos(x,y,z);server.addFreshEntity(splash);
    }
    public static void windBurst(Level l,double x,double y,double z,int n,double spread){field(l,x,y,z,LegacyFieldEffectEntity.WIND,Math.max(.35,spread),0xddeeff,new Vec3(0,1,0));}
    public static void psychoBurst(Level l,double x,double y,double z,int n,double spread){field(l,x,y,z,LegacyFieldEffectEntity.PSYCHO,Math.max(.45,spread),0xee66ff,new Vec3(0,1,0));}
    public static void electricTether(Level l,Vec3 from,Vec3 to){Vec3 delta=to.subtract(from);if(delta.lengthSqr()>1.0e-6)field(l,from.x,from.y,from.z,LegacyFieldEffectEntity.TETHER,delta.length(),0x99ddff,delta);}
    public static void electricTether(Level l,Vec3 from,Vec3 to,int lifetime){Vec3 delta=to.subtract(from);if(delta.lengthSqr()>1.0e-6)field(l,from.x,from.y,from.z,LegacyFieldEffectEntity.TETHER,delta.length(),0x99ddff,delta,lifetime);}

    /** Synchronized tapered geometry matching the old Jet Engine silhouette. */
    public static void jetMesh(Level level, Vec3 previous, Vec3 current) {
        Vec3 travel = current.subtract(previous);
        if (travel.lengthSqr() < 1.0e-6) return;
        field(level,current.x,current.y,current.z,LegacyFieldEffectEntity.JET,1.1,0x55ff88,travel);
    }

    /**
     * The old implementation rendered 25..30 rays in one entity specifically
     * to avoid the entity/draw-call cost of spawning every ray independently.
     */
    public static void barrageFan(Level level, Vec3 origin, float yaw, float pitch) {
        if (!(level instanceof ServerLevel server)) return;
        MeltdownBarrageEntity barrage = new MeltdownBarrageEntity(AcademyEntities.MELTDOWN_BARRAGE.get(), server)
                .configure(yaw, pitch, server.getRandom().nextInt(), 25 + server.getRandom().nextInt(5));
        barrage.setPos(origin.x, origin.y, origin.z);
        server.addFreshEntity(barrage);
    }

    /** Layered expanding arcs replacing the removed 1.7 immediate-mode wave mesh. */
    public static void waveMesh(Level level, Vec3 origin, Vec3 forward, double range) {
        field(level,origin.x,origin.y,origin.z,LegacyFieldEffectEntity.WAVE,range,0xf4fbff,forward);
    }

    /** Official glow-circle WaveEffect used by VecManip hits and blast releases. */
    public static void waveRings(Level level, Vec3 origin, Vec3 forward, int rings, double size) {
        if (!(level instanceof ServerLevel server)) return;
        LegacyFieldEffectEntity effect = new LegacyFieldEffectEntity(
                AcademyEntities.LEGACY_FIELD_EFFECT.get(), server)
                .configureWave((float) size, forward, rings);
        effect.setPos(origin);
        server.addFreshEntity(effect);
    }

    /** Directional droplets plus the existing impact burst, using exact velocity packets. */
    public static void bloodSpray(Level level, Vec3 origin, Vec3 direction) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 f=direction.lengthSqr()<1.0e-6?new Vec3(0,.2,1):direction.normalize();
        for(int i=0;i<18;i++){
            double phase=(i*2.399963229728653)%6.283185307179586;
            double radial=.035+(i%5)*.012;
            Vec3 side=new Vec3(Math.cos(phase)*radial,(i%4)*.012,Math.sin(phase)*radial);
            Vec3 velocity=f.scale(.13+(i%6)*.018).add(side);
            server.sendParticles(dust(0xaa1010,.62f),origin.x,origin.y,origin.z,0,
                    velocity.x,velocity.y,velocity.z,1.0);
        }
    }
}
