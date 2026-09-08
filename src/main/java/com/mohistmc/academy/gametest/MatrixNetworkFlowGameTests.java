package com.mohistmc.academy.gametest;
import com.mohistmc.academy.energy.impl.*;
import com.mohistmc.academy.network.*;
import com.mohistmc.academy.world.*;
import com.mohistmc.academy.world.block.entity.*;
import com.mohistmc.academy.world.menu.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class MatrixNetworkFlowGameTests {
    @GameTest(template="empty",timeoutTicks=120)
    public static void privateMatrixInitializesDiscoversTwoNodesAndTransfersBridgeEnergy(GameTestHelper h) {
        var level=h.getLevel(); var mp=h.absolutePos(new BlockPos(3,2,3));
        var aPos=mp.east(4);var bPos=mp.south(4);
        level.setBlock(mp,AcademyBlocks.MATRIX.get().defaultBlockState(),3);
        level.setBlock(aPos,AcademyBlocks.NODE_BASIC.get().defaultBlockState(),3);
        level.setBlock(bPos,AcademyBlocks.NODE_BASIC.get().defaultBlockState(),3);
        level.setBlock(aPos.east(),AcademyBlocks.RF_INPUT.get().defaultBlockState(),3);
        level.setBlock(bPos.south(),AcademyBlocks.RF_OUTPUT.get().defaultBlockState(),3);
        var matrix=(MatrixBlockEntity)level.getBlockEntity(mp);
        var a=(BaseNodeBlockEntity)level.getBlockEntity(aPos);var b=(BaseNodeBlockEntity)level.getBlockEntity(bPos);
        var input=(EnergyBridgeInputBlockEntity)level.getBlockEntity(aPos.east());
        var output=(EnergyBridgeOutputBlockEntity)level.getBlockEntity(bPos.south());
        ServerPlayer player=h.makeMockServerPlayerInLevel();
        player.setPos(mp.getX()+.5,mp.getY()+.5,mp.getZ()+.5);
        matrix.setOwnerUUID(player.getUUID());a.setOwnerUUID(player.getUUID());b.setOwnerUUID(player.getUUID());
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT,new ItemStack(AcademyItems.MAT_CORE_0.get()));
        for(int slot=0;slot<3;slot++)matrix.getItems().set(slot,new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(mp);
        MatrixMenu menu;try{menu=new MatrixMenu(40,player.getInventory(),buf);}finally{buf.release();}
        player.containerMenu=menu;
        InitMatrixPacket.handle(new InitMatrixPacket(menu.nextActionToken(),mp,"Matrix-123","matrix-secret"),context(player));
        require(matrix.isOperational(),"INIT did not establish operational network");
        var data=WiWorldData.get(level);var net=data.getNetwork(matrix);
        require(net!=null&&net.getPassword().equals("matrix-secret"),"private network absent");
        for(var node:new BaseNodeBlockEntity[]{a,b}) {
            var pos=node.getBlockPos();
            require(data.rangeSearch(pos.getX(),pos.getY(),pos.getZ(),node.getRange(),20).contains(net),"private matrix undiscoverable");
            player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            buf=new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
            NodeBasicMenu nm;try{nm=new NodeBasicMenu(41,player.getInventory(),buf);}finally{buf.release();}
            player.containerMenu=nm;
            ConnectNodeToMatrixPacket.handle(new ConnectNodeToMatrixPacket(nm.nextActionToken(),pos,mp,Optional.of("wrong")),context(player));
            require(data.getNetwork(node)==null,"wrong password accepted");
            ConnectNodeToMatrixPacket.handle(new ConnectNodeToMatrixPacket(nm.nextActionToken(),pos,mp,Optional.of("matrix-secret")),context(player));
            require(data.getNetwork(node)==net,"correct password did not join");
        }
        require(net.getLoad()==2,"matrix did not retain two nodes");
        require(WirelessSystem.linkGenerator(level,a,input,false,"")&&WirelessSystem.linkReceiver(level,b,output,false,""),"bridge links failed");
        require(input.receiveExternalFe(4000,false)==4000,"input FE fixture failed");
        h.runAfterDelay(80,()->{
            double stored=input.getStoredIf()+output.getStoredIf()+a.getEnergy()+b.getEnergy();
            CompoundTag snapshot=data.save(new CompoundTag(),level.registryAccess());
            double buffer=0;
            for(var tag:snapshot.getCompound("net").getList("networks",10)) {
                CompoundTag n=(CompoundTag)tag;CompoundTag m=n.getCompound("matrix");
                if(m.getInt("x")==mp.getX()&&m.getInt("y")==mp.getY()&&m.getInt("z")==mp.getZ())buffer=n.getDouble("buffer");
            }
            require(output.getStoredFe()>0,"no energy arrived through matrix at output bridge");
            require(Math.abs(stored+buffer-1000)<1e-6,"network energy not conserved: "+(stored+buffer));
            System.out.println("MATRIX_PRIVATE_FLOW_PASS nodes="+net.getLoad()+" outputFE="+output.getStoredFe()+" conservedIF="+(stored+buffer));
            // A persisted, charged buffer must feed empty nodes rather than strand its energy.
            // Invoke the real network tick without other world ticks changing this boundary fixture.
            try {
                var bufferField=net.getClass().getDeclaredField("buffer");bufferField.setAccessible(true);
                var tick=net.getClass().getDeclaredMethod("tick");tick.setAccessible(true);
                a.setEnergy(0);b.setEnergy(0);bufferField.setDouble(net,1000);
                for(int i=0;i<100;i++)tick.invoke(net);
                require(a.getEnergy()>0&&b.getEnergy()>0,"persisted buffer stranded at empty nodes");
                require(Math.abs(a.getEnergy()+b.getEnergy()+bufferField.getDouble(net)-1000)<1e-6,"buffer drain created or lost energy");
                a.setEnergy(a.getMaxEnergy());b.setEnergy(0);bufferField.setDouble(net,2000);
                double total=a.getEnergy()+2000;
                for(int i=0;i<100;i++) {
                    tick.invoke(net);
                    double pool=bufferField.getDouble(net);
                    require(pool>=0&&pool<=2000,"buffer overflow/underflow");
                    require(Math.abs(a.getEnergy()+b.getEnergy()+pool-total)<1e-6,"full buffer transfer not conserved");
                }
                System.out.println("MATRIX_BUFFER_BOUNDARIES_PASS empty-nodes/full-buffer ticks=200");
            } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            h.succeed();
        });
    }
    private static void require(boolean b,String message){if(!b)throw new net.minecraft.gametest.framework.GameTestAssertException(message);}
    private static IPayloadContext context(ServerPlayer p){return (IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),new Class<?>[]{IPayloadContext.class},(proxy,m,args)->{
        if(m.getName().equals("player"))return p;
        if(m.getName().equals("enqueueWork")){if(args[0] instanceof Runnable work){work.run();return CompletableFuture.completedFuture(null);}return CompletableFuture.completedFuture(((java.util.function.Supplier<?>)args[0]).get());}
        throw new UnsupportedOperationException(m.getName());
    });}
}
