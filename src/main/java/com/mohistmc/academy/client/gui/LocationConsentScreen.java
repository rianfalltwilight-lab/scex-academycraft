package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.network.LocationConsentRequestPacket;
import com.mohistmc.academy.network.LocationConsentResponsePacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LocationConsentScreen extends Screen {
    private final LocationConsentRequestPacket request;
    private boolean answered;
    public LocationConsentScreen(LocationConsentRequestPacket request){super(Component.literal("Location Teleport request"));this.request=request;}
    protected void init(){
        addRenderableWidget(Button.builder(Component.literal("Accept"),b->answer(true)).bounds(width/2-105,height/2+20,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Decline"),b->answer(false)).bounds(width/2+5,height/2+20,100,20).build());
    }
    public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);g.drawCenteredString(font,request.casterName()+" wants to teleport your riding group",width/2,height/2-20,0xffffff);g.drawCenteredString(font,"Destination: "+request.dimension(),width/2,height/2-7,0xaaaaaa);}
    private void answer(boolean accepted){if(answered)return;answered=true;PacketDistributor.sendToServer(new LocationConsentResponsePacket(request.nonce(),accepted));onClose();}
    public void onClose(){if(!answered){answered=true;PacketDistributor.sendToServer(new LocationConsentResponsePacket(request.nonce(),false));}super.onClose();}
}
