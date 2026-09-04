package com.mohistmc.academy.client.render;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.ChargingHudOverlay;
import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.teleporter.FlashingTargeting;
import com.mohistmc.academy.world.entity.CoinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;

/**
 * Client-only reconstruction of legacy aiming performances.  It never chooses a
 * target, deals damage or sends an activation packet: the server remains the
 * sole authority and this class merely mirrors the latest accepted charge state.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class LegacyAbilityPresentationRenderer {
    private static long lastTeleportMarkerParticleTick = Long.MIN_VALUE;
    private static long railgunBurstStartTick = Long.MIN_VALUE;
    private static boolean railgunWasCharging;
    private static final ResourceLocation GLOW_LINE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/glow_line.png");
    private LegacyAbilityPresentationRenderer() {}

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        updateRailgunBurstClock(mc);
        if (ChargingHudOverlay.isCharging("mark_teleport")) renderTeleportMark(event, mc);
        if (ChargingHudOverlay.isCharging("penetrate_teleport")) renderPenetrateMark(event, mc);
        if (ChargingHudOverlay.isCharging("flesh_ripping")) renderFleshMarker(event, mc);
        if (ChargingHudOverlay.isCharging("threatening_teleport")) renderThreateningMarker(event, mc);
        if (ChargingHudOverlay.isCharging("shift_tp")) renderShiftMarker(event, mc);
        if (KeyInputHandler.isFlashingActive() && KeyInputHandler.getFlashingHeldDirection() >= 0)
            renderFlashingMark(event, mc);
        if (ChargingHudOverlay.isCharging("jet_engine")) renderJetDestination(event, mc);
        if (ChargingHudOverlay.isCharging("thunder_clap")) renderThunderClap(event, mc);
        if (ChargingHudOverlay.isCharging("vec_accel")) renderVecAccelTrajectory(event, mc);
    }

    private static void renderTeleportMark(RenderLevelStageEvent event, Minecraft mc) {
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        float exp = data.getProficiency("mark_teleport");
        double cap = Math.min(25 + 35 * exp, data.isDevMode() ? Double.MAX_VALUE
                : data.getCurrentCp() / Math.max(.0001, 12 - 8 * exp));
        double distance = Math.max(2, Math.min(cap, (25 + 35 * exp) * ChargingHudOverlay.chargeProgress()));
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 mark = FlashingTargeting.destination(mc.player,mc.player.getEyePosition(partial),
                mc.player.getViewVector(partial),distance);
        if(mark==null)return;
        int frame = (mc.player.tickCount / 2) & 7;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                "textures/effects/tp_mark/" + frame + ".png");
        renderMarkModel(event, mark, texture, mc, 0xE8FFFFFF);
        emitTeleportMarkerParticle(mc,mark);
    }

    private static void renderPenetrateMark(RenderLevelStageEvent event, Minecraft mc) {
        PlayerAbilityData data=mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        float exp=data.getProficiency("penetrate_teleport");
        double max=KeyInputHandler.getPenetrateDistance(data);
        Vec3 dir=mc.player.getLookAngle().normalize(),cursor=mc.player.position();
        int stage=0,counter=0;double travelled=0;
        while(travelled<=max){boolean free=clientHasPlace(mc, cursor);if(stage==0){if(!free)stage=1;}
            else if(stage==1){if(free)stage=2;}else if(!free||++counter>4)break;travelled+=.8;cursor=cursor.add(dir.scale(.8));}
        int frame=(mc.player.tickCount/2)&7;
        ResourceLocation texture=ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"textures/effects/tp_mark/"+frame+".png");
        Vec3 marker=cursor.add(0,mc.player.getEyeHeight(),0);
        renderMarkModel(event,marker,texture,mc,stage==1?0xE8FF3333:0xE8FFFFFF);
        if(stage!=1)emitTeleportMarkerParticle(mc,marker);
    }

    private static void emitTeleportMarkerParticle(Minecraft mc,Vec3 marker){
        long tick=mc.level.getGameTime();if(tick==lastTeleportMarkerParticleTick)return;
        lastTeleportMarkerParticleTick=tick;if(mc.level.random.nextDouble()>=.4)return;
        mc.level.addParticle(com.mohistmc.academy.world.AcademyParticles.TELEPORT.get(),
                marker.x+mc.level.random.nextDouble()*2-1,
                marker.y+.2+mc.level.random.nextDouble()*1.4-1.6,
                marker.z+mc.level.random.nextDouble()*2-1,
                (mc.level.random.nextDouble()*2-1)*.03,
                mc.level.random.nextDouble()*.05,
                (mc.level.random.nextDouble()*2-1)*.03);
    }

    private static void renderFleshMarker(RenderLevelStageEvent event, Minecraft mc) {
        PlayerAbilityData data=mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        TargetPreview target=target(mc,6+8*data.getProficiency("flesh_ripping"));
        if(target.entity==null)renderCornerMarker(event,target.position,1,1,.29f,.29f,.29f,.63f);
        else renderCornerMarker(event,target.entity.position(),target.entity.getBbWidth()*1.2f,
                target.entity.getBbHeight()*1.2f,.73f,.10f,.10f,.71f);
    }

    private static void renderThreateningMarker(RenderLevelStageEvent event, Minecraft mc) {
        PlayerAbilityData data=mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        TargetPreview target=target(mc,8+7*data.getProficiency("threatening_teleport"),true);
        Vec3 pos=target.entity==null?target.position:target.entity.position();
        float width=target.entity==null?.5f:target.entity.getBbWidth(),height=target.entity==null?.5f:target.entity.getBbHeight();
        renderCornerMarker(event,pos,width,height,target.entity==null?.73f:.70f,
                target.entity==null?.73f:.13f,target.entity==null?.73f:.16f,.73f);
    }

    private static void renderShiftMarker(RenderLevelStageEvent event, Minecraft mc) {
        if(!(mc.player.getMainHandItem().getItem() instanceof BlockItem))return;
        PlayerAbilityData data=mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        double range=25+10*data.getProficiency("shift_tp");
        Vec3 start=mc.player.getEyePosition(),end=start.add(mc.player.getLookAngle().scale(range));
        BlockHitResult hit=mc.level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        BlockPos target=hit.getType()==HitResult.Type.MISS?BlockPos.containing(end)
                :(mc.level.getBlockState(hit.getBlockPos()).canBeReplaced()?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection()));
        Vec3 lineEnd=Vec3.atCenterOf(target);
        renderCornerMarker(event,lineEnd.add(0,-.5,0),1.2f,1.2f,.55f,.55f,.55f,.71f);
        int shown=0;
        for(Entity entity:mc.level.getEntities(mc.player,new AABB(mc.player.position(),lineEnd).inflate(1),
                entity->entity!=mc.player&&entity.isAlive()
                        &&(entity instanceof LivingEntity||entity instanceof EnderDragonPart))){
            if(entity.getBoundingBox().clip(mc.player.position(),lineEnd).isEmpty())continue;
            renderCornerMarker(event,entity.position(),entity.getBbWidth(),entity.getBbHeight(),.92f,.32f,.32f,.71f);
            if(++shown>=8)break;
        }
    }

    private static void renderFlashingMark(RenderLevelStageEvent event, Minecraft mc) {
        PlayerAbilityData data=mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        Vec3 direction=FlashingTargeting.direction(mc.player,KeyInputHandler.getFlashingHeldDirection());
        Vec3 mark=FlashingTargeting.destination(mc.player,direction,12+6*data.getProficiency("flashing"));
        if(mark==null)return;
        int frame=(mc.player.tickCount/2)&7;
        ResourceLocation texture=ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"textures/effects/tp_mark/"+frame+".png");
        renderMarkModel(event,mark,texture,mc,0xE8FFFFFF);
    }

    /** First-person 1.0.7 ParabolaEffect: 20 ms integration and the official glow line. */
    private static void renderVecAccelTrajectory(RenderLevelStageEvent event, Minecraft mc) {
        if (!mc.options.getCameraType().isFirstPerson()) return;
        PlayerAbilityData data=mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        float exp=data.getProficiency("vec_accel");
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double progress=Math.clamp(ChargingHudOverlay.chargeProgress(),0,1);
        double speed=Math.sin(.4+.6*progress)*2.5;
        float pitch=net.minecraft.util.Mth.lerp(partial,mc.player.xRotO,mc.player.getXRot())-10;
        float yaw=net.minecraft.util.Mth.lerp(partial,mc.player.yRotO,mc.player.getYRot());
        Vec3 velocity=Vec3.directionFromRotation(pitch,yaw).scale(speed);
        Vec3 look=mc.player.getViewVector(partial);
        Vec3 side=new Vec3(-look.z,0,look.x);
        if(side.lengthSqr()<1e-8)side=new Vec3(1,0,0);else side=side.normalize();
        Vec3 position=mc.player.getEyePosition(partial).add(side.scale(-.08))
                .add(0,-.04,0).subtract(look.scale(.12));
        HitResult ground=mc.level.clip(new ClipContext(mc.player.position(),mc.player.position().add(0,-2,0),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        boolean available=exp>.5f||ground.getType()==HitResult.Type.BLOCK;

        Vec3 camera=event.getCamera().getPosition();PoseStack pose=event.getPoseStack();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        RenderType type=RenderType.entityTranslucentEmissive(GLOW_LINE);
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        VertexConsumer out=buffers.getBuffer(type);Matrix4f matrix=pose.last().pose();
        for(int i=1;i<100;i++){
            Vec3 previous=position;
            velocity=velocity.scale(.98);
            position=position.add(velocity.scale(.02));
            velocity=velocity.add(0,-1.9*.02,0);
            float alpha=Math.max(0,.7f*(1-i*.03f));if(alpha<=0)break;
            trajectoryQuad(out,matrix,previous,position,camera,.02f,
                    available?1f:1f,available?1f:.2f,available?1f:.2f,alpha);
        }
        buffers.endBatch(type);pose.popPose();
    }

    private static void trajectoryQuad(VertexConsumer out,Matrix4f matrix,Vec3 from,Vec3 to,Vec3 camera,
                                       float width,float r,float g,float b,float alpha){
        Vec3 direction=to.subtract(from);if(direction.lengthSqr()<1e-10)return;
        Vec3 side=direction.normalize().cross(camera.subtract(from).normalize());
        if(side.lengthSqr()<1e-8)side=direction.normalize().cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1e-8)side=new Vec3(1,0,0);side=side.normalize().scale(width);
        trajectoryVertex(out,matrix,from.add(side),0,0,r,g,b,alpha);
        trajectoryVertex(out,matrix,from.subtract(side),0,1,r,g,b,alpha);
        trajectoryVertex(out,matrix,to.subtract(side),1,1,r,g,b,alpha);
        trajectoryVertex(out,matrix,to.add(side),1,0,r,g,b,alpha);
    }

    private static void trajectoryVertex(VertexConsumer out,Matrix4f matrix,Vec3 point,float u,float v,
                                         float r,float g,float b,float alpha){
        out.addVertex(matrix,(float)point.x,(float)point.y,(float)point.z).setColor(r,g,b,alpha)
                .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0,0xF0).setNormal(0,1,0);
    }

    private record TargetPreview(Vec3 position, Entity entity) {}

    private static TargetPreview target(Minecraft mc,double range){return target(mc,range,false);}
    private static TargetPreview target(Minecraft mc,double range,boolean entitiesIgnoreWalls){
        Vec3 start=mc.player.getEyePosition(),intended=start.add(mc.player.getLookAngle().scale(range));
        HitResult wall=mc.level.clip(new ClipContext(start,intended,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        Vec3 end=wall.getType()==HitResult.Type.MISS?intended:wall.getLocation(),entityEnd=entitiesIgnoreWalls?intended:end,point=null;Entity closest=null;double best=Double.MAX_VALUE;
        for(Entity entity:mc.level.getEntities(mc.player,new AABB(start,entityEnd).inflate(1),
                entity->entity!=mc.player&&entity.isAlive()&&entity.isPickable()
                        &&(entity instanceof LivingEntity||entity instanceof EnderDragonPart))){
            var clip=entity.getBoundingBox().inflate(.3).clip(start,entityEnd);
            if(clip.isPresent()&&start.distanceToSqr(clip.get())<best){best=start.distanceToSqr(clip.get());point=clip.get();closest=entity;}
        }
        return new TargetPreview(point==null?end:point,closest);
    }

    private static boolean clientHasPlace(Minecraft mc,Vec3 point){
        BlockPos feet=new BlockPos((int)point.x,(int)point.y,(int)point.z);
        return mc.level.getBlockState(feet).getCollisionShape(mc.level,feet).isEmpty()
                &&mc.level.getBlockState(feet.above()).getCollisionShape(mc.level,feet.above()).isEmpty();
    }

    private static void renderJetDestination(RenderLevelStageEvent event, Minecraft mc) {
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 from=mc.player.getEyePosition(partial),look=mc.player.getViewVector(partial);
        Vec3 intended=from.add(look.scale(12));
        HitResult hit=mc.level.clip(new ClipContext(from,intended,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        Vec3 mark=hit.getType()==HitResult.Type.MISS?intended:hit.getLocation().subtract(look.scale(.6));
        ResourceLocation ripple=ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"textures/effects/ripple.png");
        float pulse=.42f+(float)Math.sin((mc.player.tickCount+partial)*.22)*.06f;
        billboard(event,mark,ripple,pulse,.72f,.2f,1f,.2f);
    }

    private static void renderThunderClap(RenderLevelStageEvent event, Minecraft mc) {
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 from=mc.player.getEyePosition(partial),look=mc.player.getViewVector(partial);
        Vec3 intended=from.add(look.scale(40));
        HitResult hit=mc.level.clip(new ClipContext(from,intended,ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,mc.player));
        Vec3 mark=hit.getType()==HitResult.Type.MISS?intended:hit.getLocation();
        ResourceLocation ripple=ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                "textures/effects/ripple.png");
        float pulse=.72f+(float)Math.sin((mc.player.tickCount+partial)*.30)*.10f;
        billboard(event,mark,ripple,pulse,.70f,.80f,.86f,1f);

        Vec3 center=mc.player.getPosition(partial).add(0,mc.player.getBbHeight()*.55,0);
        double phase=(mc.player.tickCount+partial)*.18;
        for(int ring=0;ring<3;ring++){
            double radius=.65+ring*.18;
            for(int i=0;i<16;i++){
                double a0=phase+ring*.8+Math.PI*2*i/16;
                double a1=phase+ring*.8+Math.PI*2*(i+1)/16;
                beam(event,center.add(Math.cos(a0)*radius,(ring-1)*.25,Math.sin(a0)*radius),
                        center.add(Math.cos(a1)*radius,(ring-1)*.25,Math.sin(a1)*radius),.012f,.75f);
            }
        }
    }

    private static void renderMarkModel(RenderLevelStageEvent event, Vec3 mark,
                                        ResourceLocation texture, Minecraft mc, int color) {
        if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer)) return;
        Vec3 camera=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();
        pose.pushPose();
        pose.translate(mark.x-camera.x,mark.y-camera.y,mark.z-camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(180f-mc.player.getYRot()));
        // Living models use inverted render-space axes. Reuse the already animated
        // local PlayerModel, but bind the old tp_mark atlas instead of the skin.
        pose.scale(-1f,-1f,1f);
        pose.translate(0,-1.501,0);
        RenderType type=RenderType.entityTranslucentEmissive(texture);
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        renderer.getModel().renderToBuffer(pose,buffers.getBuffer(type),0xF000F0,
                OverlayTexture.NO_OVERLAY,color);
        buffers.endBatch(type);
        pose.popPose();
    }

    /**
     * Reconstructs the old WaveEffectUI as continuous geometry instead of vanilla
     * spark/end-rod particles.  This is deliberately presentation-only: the ring
     * radius and reticle never participate in server target selection.
     */
    private static void renderVectorField(RenderLevelStageEvent event, Minecraft mc, boolean reflection) {
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 center = mc.player.getPosition(partial).add(0, mc.player.getBbHeight() * .62, 0);
        double pulse = 1.0 + Math.sin((mc.player.tickCount + partial) * .22) * .08;
        double radius = (reflection ? 1.1 : .65) * pulse;
        int segments = 32;
        double phase = (mc.player.tickCount + partial) * .035;
        for (int ring = 0; ring < (reflection ? 3 : 2); ring++) {
            double y = (ring - (reflection ? 1 : .5)) * .20;
            double r = radius * (1.0 - ring * .09);
            for (int i = 0; i < segments; i++) {
                double a0 = phase + Math.PI * 2 * i / segments;
                double a1 = phase + Math.PI * 2 * (i + 1) / segments;
                Vec3 p0 = center.add(Math.cos(a0) * r, y, Math.sin(a0) * r);
                Vec3 p1 = center.add(Math.cos(a1) * r, y, Math.sin(a1) * r);
                beam(event, p0, p1, .010f, reflection ? .72f : .52f);
            }
        }
        if (reflection) {
            Vec3 aim = mc.player.getEyePosition(partial).add(mc.player.getViewVector(partial).scale(4));
            Vec3 right = mc.player.getViewVector(partial).cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1.0e-5) right = new Vec3(1, 0, 0);
            right = right.normalize().scale(.22);
            Vec3 up = right.cross(mc.player.getViewVector(partial)).normalize().scale(.22);
            beam(event, aim.subtract(right), aim.add(right), .012f, .78f);
            beam(event, aim.subtract(up), aim.add(up), .012f, .78f);
        }
    }

    @SubscribeEvent
    public static void animateRailgunHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        updateRailgunBurstClock(mc);
        if (ChargingHudOverlay.isCharging("railgun")) {
            float progress = ChargingHudOverlay.chargeProgress();
            float recoilTension = progress * progress;
            PoseStack pose = event.getPoseStack();
            // Applied before vanilla renders the held item/arm. This preserves other
            // render layers and recreates the old lowered-then-braced firing posture.
            pose.translate(-0.08 * recoilTension, 0.055 * recoilTension, -0.12 * recoilTension);
            pose.mulPose(Axis.XP.rotationDegrees(-11f * recoilTension));
            pose.mulPose(Axis.ZP.rotationDegrees(-5f * recoilTension));
        }
        float age = railgunBurstAge(mc.player, event.getPartialTick(), true);
        if (age < 0 || age >= 32) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(.26, -.12, -.24);
        pose.scale(.4f, .4f, 1f);
        renderRailgunBurst(pose, event.getMultiBufferSource(), age);
        pose.popPose();
    }

    /** Third-person counterpart of the old PlayerRenderHook used for coin tosses. */
    @SubscribeEvent
    public static void renderRailgunBurstOnPlayer(RenderPlayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (event.getEntity() == mc.player && mc.options.getCameraType().isFirstPerson())) return;
        float age = railgunBurstAge(event.getEntity(), event.getPartialTick(), event.getEntity() == mc.player);
        if (age < 0 || age >= 32) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, .2, -1);
        pose.mulPose(Axis.XP.rotationDegrees(-event.getEntity().getXRot()));
        pose.scale(.55f, .55f, 1f);
        renderRailgunBurst(pose, event.getMultiBufferSource(), age);
        pose.popPose();
    }

    private static void updateRailgunBurstClock(Minecraft mc) {
        boolean charging = ChargingHudOverlay.isCharging("railgun");
        long now = mc.level.getGameTime();
        if (railgunBurstStartTick > now) railgunBurstStartTick = Long.MIN_VALUE;
        if (charging && !railgunWasCharging) railgunBurstStartTick = now;
        railgunWasCharging = charging;
    }

    /** 40 images at 40 ms each are 32 Minecraft ticks. */
    private static float railgunBurstAge(Player player, float partial, boolean includeLocalCharge) {
        float best = Float.POSITIVE_INFINITY;
        for (CoinEntity coin : player.level().getEntitiesOfClass(CoinEntity.class,
                player.getBoundingBox().inflate(2, 12, 2),
                candidate -> candidate.isAlive() && candidate.getThrower() == player)) {
            float age = coin.tickCount + partial;
            if (age >= 0 && age < best) best = age;
        }
        if (includeLocalCharge && railgunBurstStartTick != Long.MIN_VALUE) {
            float age = player.level().getGameTime() + partial - railgunBurstStartTick;
            if (age >= 0 && age < 32) best = Math.min(best, age);
        }
        return Float.isFinite(best) ? best : -1;
    }

    private static void renderRailgunBurst(PoseStack pose, MultiBufferSource buffers, float age) {
        int frame = Math.max(0, Math.min(39, (int) (age * 1.25f)));
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                "textures/effects/arc_burst/" + frame + ".png");
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        Matrix4f matrix = pose.last().pose();
        vertex(out, matrix, -1, -1, 0, 1, 1, 1, 1, 1);
        vertex(out, matrix, -1,  1, 0, 0, 1, 1, 1, 1);
        vertex(out, matrix,  1,  1, 1, 0, 1, 1, 1, 1);
        vertex(out, matrix,  1, -1, 1, 1, 1, 1, 1, 1);
    }

    /** Eight three-axis corner glyphs copied from 1.0.7 RenderMarker's geometry. */
    private static void renderCornerMarker(RenderLevelStageEvent event,Vec3 base,float width,float height,
                                           float r,float g,float b,float alpha){
        Minecraft mc=Minecraft.getInstance();Vec3 camera=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        RenderType type=RenderType.lightning();MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        VertexConsumer vc=buffers.getBuffer(type);Matrix4f matrix=pose.last().pose();
        double bob=.05*Math.sin((mc.player.tickCount+event.getPartialTick().getGameTimeDeltaPartialTick(false))*.05);
        double minX=base.x-width/2,minZ=base.z-width/2,minY=base.y+bob,len=.2*width;
        for(int xi=0;xi<2;xi++)for(int yi=0;yi<2;yi++)for(int zi=0;zi<2;zi++){
            Vec3 corner=new Vec3(minX+xi*width,minY+yi*height,minZ+zi*width);
            lineQuad(vc,matrix,corner,corner.add(xi==0?len:-len,0,0),camera,.012f,r,g,b,alpha);
            lineQuad(vc,matrix,corner,corner.add(0,yi==0?len:-len,0),camera,.012f,r,g,b,alpha);
            lineQuad(vc,matrix,corner,corner.add(0,0,zi==0?len:-len),camera,.012f,r,g,b,alpha);
        }
        buffers.endBatch(type);pose.popPose();
    }

    private static void lineQuad(VertexConsumer vc,Matrix4f matrix,Vec3 from,Vec3 to,Vec3 camera,
                                 float width,float r,float g,float b,float alpha){
        Vec3 direction=to.subtract(from);if(direction.lengthSqr()<1e-8)return;
        Vec3 side=direction.normalize().cross(camera.subtract(from).normalize());
        if(side.lengthSqr()<1e-5)side=direction.normalize().cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1e-5)side=new Vec3(1,0,0);side=side.normalize().scale(width);
        lineVertex(vc,matrix,from.add(side),r,g,b,alpha);
        lineVertex(vc,matrix,from.subtract(side),r,g,b,alpha);
        lineVertex(vc,matrix,to.subtract(side),r,g,b,alpha);
        lineVertex(vc,matrix,to.add(side),r,g,b,alpha);
    }

    private static void billboard(RenderLevelStageEvent event, Vec3 world, ResourceLocation texture,
                                  float half, float alpha, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        RenderType type = RenderType.entityTranslucentEmissive(texture);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        Matrix4f matrix = pose.last().pose();
        vertex(vc, matrix, -half, -half, 0, 1, r,g,b,alpha);
        vertex(vc, matrix, -half,  half, 0, 0, r,g,b,alpha);
        vertex(vc, matrix,  half,  half, 1, 0, r,g,b,alpha);
        vertex(vc, matrix,  half, -half, 1, 1, r,g,b,alpha);
        buffers.endBatch(type);
        pose.popPose();
    }

    private static void beam(RenderLevelStageEvent event, Vec3 from, Vec3 to, float width, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        Vec3 direction = to.subtract(from);
        Vec3 side = direction.normalize().cross(camera.subtract(from).normalize());
        if (side.lengthSqr() < 1.0e-5) side = direction.normalize().cross(new Vec3(0, 1, 0));
        side = side.normalize().scale(width);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(from.x-camera.x, from.y-camera.y, from.z-camera.z);
        RenderType type = RenderType.lightning();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        Vec3 end = direction;
        lineVertex(vc,m,side,0.20f,1f,0.48f,alpha);
        lineVertex(vc,m,side.scale(-1),0.20f,1f,0.48f,alpha);
        lineVertex(vc,m,end.add(side.scale(-1)),0.20f,1f,0.48f,0f);
        lineVertex(vc,m,end.add(side),0.20f,1f,0.48f,0f);
        buffers.endBatch(type);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x,float y,float u,float v,
                               float r,float g,float b,float a) {
        vc.addVertex(m,x,y,0).setColor(r,g,b,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setUv2(0xF0,0xF0).setNormal(0,0,1);
    }

    private static void lineVertex(VertexConsumer vc, Matrix4f m, Vec3 p,float r,float g,float b,float a) {
        vc.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,a);
    }
}
