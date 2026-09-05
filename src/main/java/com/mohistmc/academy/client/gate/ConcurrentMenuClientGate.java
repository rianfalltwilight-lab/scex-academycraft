package com.mohistmc.academy.client.gate;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.block.gui.BaseNodeGui;
import com.mohistmc.academy.gametest.ConcurrentGateState;
import com.mohistmc.academy.gametest.ConcurrentMenuServerGate;
import com.mohistmc.academy.network.ConnectToNodePacket;
import com.mohistmc.academy.network.NodeConfigPacket;
import com.mohistmc.academy.world.menu.AcademyMenu;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import java.nio.file.Files;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicitly enabled real-client adversarial input and rendering driver. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class ConcurrentMenuClientGate {
    private static int lastStage=-1, age, ticks;
    private static boolean acted, captured, inventoryRequested, nameDraftPrepared, finished;
    private static volatile boolean screenshotDone;
    private static NodeConfigPacket stale;
    private ConcurrentMenuClientGate() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ConcurrentGateState.enabled() || finished) return;
        String role=System.getProperty("academy.concurrentRole","");
        if (!role.equals("a") && !role.equals("b")) return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if (!ConcurrentGateState.read("server-result.txt").isBlank()) {
                finished=true; mc.stop(); return;
            }
            if (++ticks>20*300) throw new IllegalStateException("client timeout");
            if(mc.player==null || mc.level==null || mc.getOverlay()!=null) return;
            String raw=ConcurrentGateState.read("stage.txt");
            if(raw.isBlank()) return;
            int stage;
            try { stage=Integer.parseInt(raw); } catch(NumberFormatException pendingWrite) { return; }
            if(stage!=lastStage) { lastStage=stage;age=0;acted=false;captured=false;screenshotDone=false;inventoryRequested=false;nameDraftPrepared=false; }
            if(ConcurrentGateState.read(role+"-ack.txt").equals(raw)) return;
            if(++age>20*60) throw new IllegalStateException("stage timeout "+stage+" screen="+mc.screen);
            if(!acted) {
                switch(stage) {
                    case 1 -> {
                        if(!(mc.player.containerMenu instanceof BaseNodeMenu menu) || !menu.actionSessionReady()
                                || !(mc.screen instanceof BaseNodeGui<?> gui) || age<15) return;
                        if(stale==null) stale=new NodeConfigPacket(menu.nextActionToken(),menu.pos,Optional.of("DelayedOld"),Optional.empty());
                        if(role.equals("a") && !gui.renameNodeForVisualGate("Concurrent-A")) return;
                        acted=true;
                    }
                    case 2 -> {
                        if(!(mc.player.containerMenu instanceof BaseNodeMenu menu) || !menu.actionSessionReady()
                                || !(mc.screen instanceof BaseNodeGui<?> gui) || age<10) return;
                        if(role.equals("b")) {
                            if(!nameDraftPrepared) {
                                if(!"Concurrent-A".equals(gui.displayedNodeNameForVisualGate())) return;
                                if(!gui.draftNodeNameForVisualGate("Unsent-B")) return;
                                nameDraftPrepared=true;
                            }
                            if(!gui.setPasswordForVisualGate("safe-pass")) return;
                        } else if(!"Concurrent-A".equals(gui.displayedNodeNameForVisualGate())) return;
                        acted=true;
                    }
                    case 3 -> {
                        if(mc.player.containerMenu!=mc.player.inventoryMenu || age<10) return;
                        PacketDistributor.sendToServer(stale); acted=true;
                    }
                    case 4 -> {
                        if(!(mc.player.containerMenu instanceof BaseNodeMenu menu) || !menu.actionSessionReady() || age<10) return;
                        PacketDistributor.sendToServer(stale); acted=true;
                    }
                    case 5 -> {
                        if(!(mc.player.containerMenu instanceof BaseNodeMenu menu) || !menu.actionSessionReady()) return;
                        if(role.equals("a")) {
                            var low=menu.nextActionToken(); var high=menu.nextActionToken();
                            PacketDistributor.sendToServer(new NodeConfigPacket(high,menu.pos,Optional.of("OrderHigh"),Optional.empty()));
                            PacketDistributor.sendToServer(new NodeConfigPacket(low,menu.pos,Optional.of("OrderLow"),Optional.empty()));
                            PacketDistributor.sendToServer(new NodeConfigPacket(high,menu.pos,Optional.of("BadReplay"),Optional.empty()));
                        }
                        acted=true;
                    }
                    case 6 -> {
                        if(!(mc.player.containerMenu instanceof BaseNodeMenu menu) || !menu.actionSessionReady() || age<10) return;
                        if(role.equals("b")) PacketDistributor.sendToServer(new NodeConfigPacket(menu.nextActionToken(),menu.pos,Optional.of("Unauthorized"),Optional.empty()));
                        acted=true;
                    }
                    case 7,8 -> {
                        if(!(mc.player.containerMenu instanceof AcademyMenu menu) || !menu.pos.equals(ConcurrentMenuServerGate.PHASE)
                                || !menu.actionSessionReady() || age<15) return;
                        if(stage==7 && role.equals("b") || stage==8 && role.equals("a"))
                            PacketDistributor.sendToServer(new ConnectToNodePacket(menu.nextActionToken(),menu.pos,
                                    ConcurrentMenuServerGate.NODE,Optional.of(stage==7?"wrong-pass":"safe-pass")));
                        acted=true;
                    }
                    case 9 -> {
                        if(age<15) return;
                        if(!inventoryRequested) {
                            if(mc.player.containerMenu!=mc.player.inventoryMenu) return;
                            KeyMapping.click(mc.options.keyInventory.getKey());inventoryRequested=true;return;
                        }
                        if(role.equals("a") ? !(mc.screen instanceof CreativeModeInventoryScreen) : !(mc.screen instanceof InventoryScreen)) return;
                        acted=true;
                    }
                    default -> throw new IllegalStateException("unknown stage "+stage);
                }
            }
            if(age<25) return;
            String expectedDisplay = switch(stage) {
                case 2 -> role.equals("b") ? "Unsent-B" : "Concurrent-A";
                case 4 -> "Reopened";
                case 5,6 -> "OrderHigh";
                default -> null;
            };
            if(expectedDisplay!=null && (!(mc.screen instanceof BaseNodeGui<?> gui)
                    || !expectedDisplay.equals(gui.displayedNodeNameForVisualGate()))) return;
            String name="concurrent-"+role+"-stage-"+stage+".png";
            if(!captured) {
                captured=true;
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->screenshotDone=true);
                return;
            }
            if(!screenshotDone) return;
            if(!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)))
                throw new IllegalStateException("screenshot was not saved: "+name);
            ConcurrentGateState.write(role+"-ack.txt",raw);
        } catch(Throwable failure) {
            ConcurrentGateState.write(role+"-failure.txt","stage="+lastStage+"\nreason="+failure);
            finished=true; mc.stop();
        }
    }
}
