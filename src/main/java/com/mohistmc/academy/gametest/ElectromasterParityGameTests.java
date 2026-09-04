package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.entity.CoinEntity;
import com.mohistmc.academy.world.entity.MagManipBlockEntity;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.item.Coin;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ElectromasterParityGameTests {
    private ElectromasterParityGameTests() {}

    @GameTest(template="empty",timeoutTicks=40)
    public static void coinTossUsesLegacyLaunchGravityAndPlayerTracking(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(3,3,3)).getCenter());
        player.setDeltaMovement(new Vec3(.25,.08,-.2));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AcademyItems.COIN.get()));
        ((Coin)AcademyItems.COIN.get()).use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        CoinEntity coin=CoinEntity.getPlayerCoinInAir(player);
        if(coin==null){helper.fail("coin toss did not create the server-authoritative entity");return;}
        if(Math.abs(coin.getY()-player.getY())>1.0e-6
                ||Math.abs(coin.getDeltaMovement().y-.08-.92)>1.0e-6
                ||coin.getLifetime()!=120){helper.fail("coin launch did not match 1.0.7 kinematics");return;}

        player.setPos(player.getX()+1,player.getY(),player.getZ()-1);
        coin.tick();
        if(Math.abs(coin.getX()-player.getX())>1.0e-6||Math.abs(coin.getZ()-player.getZ())>1.0e-6){
            helper.fail("airborne coin stopped following its thrower's X/Z position");return;
        }
        if(Math.abs(coin.getDeltaMovement().y-.94)>1.0e-6){
            helper.fail("coin gravity was "+coin.getDeltaMovement().y()+", expected 0.94 after one tick");return;
        }
        helper.succeed();
    }

    @SuppressWarnings("deprecation")
    @GameTest(template="empty",timeoutTicks=40)
    public static void magManipHeldMaterialEasesAndDropPreservesMomentum(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(3,3,3)).getCenter());
        player.setYRot(0);player.setXRot(0);
        MagManipBlockEntity carrier=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        carrier.initializeProjection(player,Blocks.IRON_BLOCK.defaultBlockState(),10);
        carrier.setPos(player.getX()-4,player.getY(),player.getZ()-4);
        Vec3 before=carrier.position();
        Vec3 target=player.getEyePosition().add(player.getLookAngle().scale(2)).add(0,-.1,0);
        double beforeDistance=before.distanceTo(target);
        carrier.hold(player);
        double travel=carrier.position().distanceTo(before);
        if(!(travel>0&&travel<=.200001)||carrier.position().distanceTo(target)>=beforeDistance){
            helper.fail("held Mag Manip material teleported or failed to ease toward its anchor");return;
        }
        Vec3 heldVelocity=carrier.getDeltaMovement();
        carrier.dropFromHold(player);
        if(carrier.getDeltaMovement().distanceToSqr(heldVelocity)>1.0e-12){
            helper.fail("aborting Mag Manip erased the carrier's legacy release momentum");return;
        }
        helper.succeed();
    }
}
