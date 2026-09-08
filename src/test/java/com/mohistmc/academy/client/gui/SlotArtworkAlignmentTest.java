package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.world.menu.AcademyMenu;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Measure the shipped PNG's frame, independent of legacy coordinate comments. */
class SlotArtworkAlignmentTest {
    @Test void playerInventoryItemsAreCenteredOnTheShippedArtwork() throws Exception {
        var texture=ImageIO.read(Path.of("src/main/resources/assets/academy/textures/guis/ui/ui_inventory.png").toFile());
        assertEquals(352,texture.getWidth());
        for(int row=0;row<4;row++) {
            int slotY=row==3?AcademyMenu.HOTBAR_Y:AcademyMenu.INV_Y+18*row;
            for(int col=0;col<9;col++) {
                int first=-1,last=-1;
                // Inspect the two opaque vertical frame edges at the slot's centre.
                for(int x=14+36*col;x<50+36*col;x++) {
                    if((texture.getRGB(x,slotY*2+16)>>>24)>=240){if(first<0)first=x;last=x;}
                }
                assertTrue(first>=0&&last>first,"Missing frame at "+col+","+row);
                double frameCenter=(first+last+1)/4.0;
                assertEquals(frameCenter,AcademyMenu.INV_X+18*col+8,0.5,"Inventory frame "+col+","+row);
            }
        }
    }
}

