package com.mohistmc.academy.client.gate;
import com.mohistmc.academy.world.*;
import com.mohistmc.academy.world.block.Matrix;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.client.block.entity.render.MatrixRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector4f;
import java.nio.file.*;
import java.util.Set;

/** Default-off visual probe in a dedicated copied singleplayer fixture world. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class MatrixVisualGate {
 private static int ticks,stage,age;
 private static volatile boolean ready,captured;
 private static boolean setup,positioned,finished;
 private static final Direction[] FACES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
 private static BlockPos position(int i){return new BlockPos(40+i*7,90,40);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){
  if(!Boolean.getBoolean("academy.matrixVisualGate")||finished)return;
  Minecraft mc=Minecraft.getInstance();
  try{
   if(++ticks>4000)throw new IllegalStateException("matrix visual timeout");
   if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
   if(!setup){setup=true;mc.getSingleplayerServer().execute(()->{
    var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());level.setDayTime(6000);
    player.setGameMode(GameType.SPECTATOR);
    for(int i=0;i<4;i++){
     BlockPos p=position(i);var state=AcademyBlocks.MATRIX.get().defaultBlockState().setValue(Matrix.FACING,FACES[i]);
     for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlock(p.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
     level.setBlock(p,state,3);((Matrix)AcademyBlocks.MATRIX.get()).createStructure(level,p,state);
     var matrix=(MatrixBlockEntity)level.getBlockEntity(p);matrix.setOwnerUUID(player.getUUID());
     matrix.getItems().set(MatrixBlockEntity.CORE_SLOT,new ItemStack(AcademyItems.MAT_CORE_0.get()));
     for(int slot=0;slot<3;slot++)matrix.getItems().set(slot,new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
     matrix.setChanged();level.sendBlockUpdated(p,state,state,3);
    }
    ready=true;
   });return;}
   if(!ready)return;
   if(stage>=8){log(mc,"DONE screenshots=8; real placed fixture/core/plates; no survival-placement claim");finished=true;mc.stop();return;}
   int face=stage/2;BlockPos p=position(face);
   if(!positioned){positioned=true;age=0;captured=false;
    Vector4f center=new Vector4f(0,1,0,1);BlockModelRotation.by(0,face*90).getRotation().blockCenterToCorner().transformPosition(center);
    double x=p.getX()+center.x,z=p.getZ()+center.z;
    mc.getSingleplayerServer().execute(()->{
      var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
      player.teleportTo(mc.getSingleplayerServer().overworld(),x+4,93,z+4,Set.of(),135,25);
    });return;
   }
   if(++age<45)return;
   if(!(mc.level.getBlockEntity(p) instanceof MatrixBlockEntity matrix)||matrix.getItems().get(0).isEmpty())return;
   if(age==45){
    var model=mc.getModelManager().getModel(MatrixRender.SHIELD_MODEL);
    if(model==mc.getModelManager().getMissingModel())throw new IllegalStateException("shield model missing");
    var m=MatrixRender.class.getDeclaredMethod("facingDegrees",Direction.class);m.setAccessible(true);
    float angle=(float)m.invoke(null,FACES[face]);PoseStack pose=new PoseStack();pose.rotateAround(Axis.YP.rotationDegrees(angle),.5f,.5f,.5f);
    Vector4f actual=new Vector4f(0,0,0,1).mul(pose.last().pose());Vector4f expected=new Vector4f(0,0,0,1);
    BlockModelRotation.by(0,face*90).getRotation().blockCenterToCorner().transformPosition(expected);
    log(mc,"facing="+FACES[face]+" sample="+(stage%2)+" orbitOriginError="+actual.distance(expected)+" clientComponents=4");
    String file="matrix-"+FACES[face]+"-"+(stage%2)+".png";
    Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),message->{captured=true;});
   }
   if(captured&&age>65){stage++;positioned=false;}
  }catch(Throwable failure){log(mc,"FAIL "+failure);finished=true;mc.stop();}
 }
 private static void log(Minecraft mc,String text){try{Files.writeString(mc.gameDirectory.toPath().resolve("matrix-visual-result.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException(e);}}
}
