package com.mohistmc.academy.terminal;

import com.mohistmc.academy.AcademyCraft;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AppRegistry {

    public static final BuiltinApp SKILL_TREE = new BuiltinApp("skill_tree", "item.academy.app_skill_tree");
    public static final BuiltinApp FREQ_TRANSMITTER = new BuiltinApp("freq_transmitter", "item.academy.app_freq_transmitter");
    public static final BuiltinApp MEDIA_PLAYER = new BuiltinApp("media_player", "item.academy.app_media_player");
    // 1.0.7 deliberately changes the MisakaCloud tutorial icon whenever the
    // launcher entry is rebuilt: frame 0 = 20%, frame 1 = 10%, frame 2 = 70%.
    public static final BuiltinApp TUTORIAL = new BuiltinApp("tutorial", "item.academy.app_tutorial",
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                    "textures/guis/apps/tutorial/icon_0.png")) {
        @Override
        public ResourceLocation getIcon() {
            double random = ThreadLocalRandom.current().nextDouble();
            int frame = random < 0.2 ? 0 : random < 0.3 ? 1 : 2;
            return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                    "textures/guis/apps/tutorial/icon_" + frame + ".png");
        }
    };
    public static final BuiltinApp SETTINGS = new BuiltinApp("settings", "item.academy.app_settings");
    public static final BuiltinApp ABOUT = new BuiltinApp("about", "item.academy.app_about");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, TerminalApp> APPS = new LinkedHashMap<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        registerBuiltins();
        LOGGER.info("AcademyCraft AppRegistry initialized with {} apps", APPS.size());
    }

    public static void register(TerminalApp app) {
        APPS.put(app.getAppId(), app);
    }

    public static TerminalApp getApp(String appId) {
        return APPS.get(appId);
    }

    public static List<TerminalApp> getAllApps() {
        return List.copyOf(APPS.values());
    }

    public static boolean isRegistered(String appId) {
        return APPS.containsKey(appId);
    }

    public static void bindOpenAction(BuiltinApp app, Runnable action) {
        app.setOpenAction(action);
    }

    private static void registerBuiltins() {
        // AppAbout priority -2 and AppSettings priority -1 in 1.12.2.
        register(ABOUT);
        register(SETTINGS);
        register(SKILL_TREE);
        register(FREQ_TRANSMITTER);
        register(MEDIA_PLAYER);
        register(TUTORIAL);
    }
}
