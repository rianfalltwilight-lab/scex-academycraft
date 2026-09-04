package com.mohistmc.academy.api.event;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyObservableEventContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/", path));
    }

    @Test void publicEventsRetainLegacyFieldsAndModernTransitionContext() throws Exception {
        String ability=source("api/event/AbilityEvents.java");
        assertTrue(ability.contains("public final Player player"));
        assertTrue(ability.contains("oldCategory, category"));
        assertTrue(ability.contains("oldLevel, level"));
        assertTrue(ability.contains("Skill skill"));
        assertTrue(ability.contains("amount, oldExp, exp"));
        String wireless=source("api/event/WirelessEvents.java");
        assertTrue(wireless.contains("IWirelessMatrix matrix"));
        assertTrue(wireless.contains("IWirelessNode node"));
        assertTrue(wireless.contains("boolean needAuth"));
        assertTrue(wireless.contains("implements ICancellableEvent"));
    }

    @Test void cancellableTopologyIntentsAreObservedBeforeMutation() throws Exception {
        String wireless=source("energy/impl/WirelessSystem.java");
        int createPost=wireless.indexOf("NeoForge.EVENT_BUS.post(event);", wireless.indexOf("createNetwork"));
        int createMutation=wireless.indexOf("data.createNetwork", createPost);
        assertTrue(createPost>=0 && createPost<createMutation);
        int linkStart=wireless.indexOf("public static boolean linkNode");
        int linkPost=wireless.indexOf("NeoForge.EVENT_BUS.post(event);",linkStart);
        int linkMutation=wireless.indexOf("net.addNode",linkPost);
        assertTrue(linkPost<linkMutation);
        assertTrue(wireless.substring(linkPost,linkMutation).contains("event.isCanceled()"));
    }

    @Test void matterHarvestCancellationPrecedesWorldAndInventoryCommit() throws Exception {
        String unit=source("world/item/MatterUnitNone.java");
        int post=unit.indexOf("NeoForge.EVENT_BUS.post(harvest)");
        int cancel=unit.indexOf("harvest.isCanceled()",post);
        int block=unit.indexOf("level.setBlock",cancel);
        int inventory=unit.indexOf("stack.shrink",block);
        assertTrue(post>=0 && post<cancel && cancel<block && block<inventory);
    }

    @Test void terminalEventsOnlyFollowSuccessfulStateChanges() throws Exception {
        String terminal=source("world/item/TerminalInstaller.java");
        assertTrue(terminal.indexOf("data.setTerminalInstalled(true)") < terminal.indexOf("new TerminalEvents.Installed"));
        String app=source("world/item/BaseApp.java");
        assertTrue(app.indexOf("data.installApp(getAppId())") < app.indexOf("new TerminalEvents.AppInstalled"));
    }
}
