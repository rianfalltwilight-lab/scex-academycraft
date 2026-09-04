package com.mohistmc.academy.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/**
 * AcademyCraft 配置系统(基于 NeoForge ModConfigSpec)
 */
@EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
public final class ACConfig {

    private ACConfig() {}

    // ==================== 服务端配置 ====================

    public static final class Server {
        public static final ModConfigSpec SPEC;
        public static final ModConfigSpec.DoubleValue SKILL_DAMAGE_MULTIPLIER;
        public static final ModConfigSpec.BooleanValue PVP_ENABLED;
        public static final ModConfigSpec.IntValue CP_RECOVER_COOLDOWN;
        public static final ModConfigSpec.DoubleValue CP_RECOVER_SPEED;
        public static final ModConfigSpec.IntValue OVERLOAD_RECOVER_COOLDOWN;
        public static final ModConfigSpec.DoubleValue OVERLOAD_RECOVER_SPEED;
        public static final ModConfigSpec.DoubleValue MAX_CP_INCREASE_RATE;
        public static final ModConfigSpec.DoubleValue MAX_OVERLOAD_INCREASE_RATE;
        public static final ModConfigSpec.DoubleValue PROFICIENCY_INCREASE_RATE;
        public static final ModConfigSpec.BooleanValue GENERATE_ORES;
        public static final ModConfigSpec.BooleanValue GENERATE_PHASE_LIQUID;
        public static final ModConfigSpec.BooleanValue GIVE_CLOUD_TERMINAL;
        public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS;
        public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_DESTRUCTION_DIMENSION_ALLOWLIST;
        public static final ModConfigSpec.ConfigValue<List<? extends String>> NORMAL_METAL_BLOCKS;
        public static final ModConfigSpec.ConfigValue<List<? extends String>> WEAK_METAL_BLOCKS;
        public static final ModConfigSpec.ConfigValue<List<? extends String>> METAL_ENTITIES;
        public static final List<String> DEFAULT_NORMAL_METAL_BLOCKS = List.of(
                "rail", "iron_bars", "iron_block", "iron_door", "activator_rail",
                "detector_rail", "golden_rail", "sticky_piston", "piston");
        public static final List<String> DEFAULT_WEAK_METAL_BLOCKS = List.of(
                "dispenser", "hopper", "iron_ore", "deepslate_iron_ore");
        public static final List<String> DEFAULT_METAL_ENTITIES = List.of(
                "MinecartRideable", "MinecartChest", "MinecartFurnace", "MinecartTNT",
                "MinecartHopper", "MinecartSpawner", "MinecartCommandBlock",
                "academy-craft.ac_Entity_EntityMagHook", "VillagerGolem");
        /** Entries use skill.property=value, e.g. railgun.enabled=false. */
        public static final ModConfigSpec.ConfigValue<List<? extends String>> SKILL_OVERRIDES;

        static {
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

            builder.push("skill");
            SKILL_DAMAGE_MULTIPLIER = builder
                    .comment("Global skill damage multiplier. 1.0 = default.")
                    .defineInRange("skillDamageMultiplier", 1.0, 0.1, 10.0);
            PVP_ENABLED = builder
                    .comment("Whether AcademyCraft skill damage is effective on players (legacy generic.attackPlayer).")
                    .define("pvpEnabled", true);
            CP_RECOVER_COOLDOWN = builder.defineInRange("cpRecoverCooldown", 15, 0, 1200);
            CP_RECOVER_SPEED = builder.defineInRange("cpRecoverSpeed", 1.0, 0.0, 100.0);
            OVERLOAD_RECOVER_COOLDOWN = builder.defineInRange("overloadRecoverCooldown", 32, 0, 1200);
            OVERLOAD_RECOVER_SPEED = builder.defineInRange("overloadRecoverSpeed", 1.0, 0.0, 100.0);
            MAX_CP_INCREASE_RATE = builder.defineInRange("maxCpIncreaseRate", 0.0025, 0.0, 1.0);
            MAX_OVERLOAD_INCREASE_RATE = builder.defineInRange("maxOverloadIncreaseRate", 0.0058, 0.0, 1.0);
            PROFICIENCY_INCREASE_RATE = builder.defineInRange("proficiencyIncreaseRate", 1.0, 0.0, 100.0);
            SKILL_OVERRIDES = builder.comment("Per-skill: skill.property=value; properties enabled, damage, cp, overload, exp, destroy_blocks")
                    .defineListAllowEmpty("overrides", List.of(), () -> "", value -> value instanceof String);
            builder.pop();

            builder.push("ability");
            NORMAL_METAL_BLOCKS = builder
                    .comment("Normal metal block ids or #block_tags used by Electromaster. Legacy short ids are accepted.")
                    .defineList("normalMetalBlocks", DEFAULT_NORMAL_METAL_BLOCKS, () -> "rail",
                            ACConfig::validRegistryListEntry);
            WEAK_METAL_BLOCKS = builder
                    .comment("Weak metal block ids or #block_tags, available to Mag Movement at sufficient proficiency.")
                    .defineList("weakMetalBlocks", DEFAULT_WEAK_METAL_BLOCKS, () -> "dispenser",
                            ACConfig::validRegistryListEntry);
            METAL_ENTITIES = builder
                    .comment("Metal entity type ids or #entity_type tags. AcademyCraft 1.0.7 entity names are accepted.")
                    .defineList("metalEntities", DEFAULT_METAL_ENTITIES, () -> "MinecartRideable",
                            ACConfig::validRegistryListEntry);
            builder.pop();

            builder.push("generic");
            GENERATE_ORES = builder
                    .comment("Whether AcademyCraft ores generate in the Overworld (legacy generic.genOres).")
                    .define("genOres", true);
            GENERATE_PHASE_LIQUID = builder
                    .comment("Whether phase-liquid lakes generate in the Overworld (legacy generic.genPhaseLiquid).")
                    .define("genPhaseLiquid", true);
            GIVE_CLOUD_TERMINAL = builder
                    .comment("Whether a player receives the tutorial terminal item once on first login (legacy generic.giveCloudTerminal).")
                    .define("giveCloudTerminal", true);
            DESTROY_BLOCKS = builder
                    .comment("Whether AcademyCraft skills may modify or destroy blocks (legacy generic.destroyBlocks).")
                    .define("destroyBlocks", true);
            BLOCK_DESTRUCTION_DIMENSION_ALLOWLIST = builder
                    .comment("Dimension ids where skill block modification remains enabled when destroyBlocks is false. "
                            + "Also accepts legacy ids 0, -1 and 1 for overworld, nether and end.")
                    .defineListAllowEmpty("worldsWhitelistedDestroyingBlocks", List.of(), () -> "",
                            value -> value instanceof String text && LegacyDimensionAllowlist.validEntry(text));
            builder.pop();

            SPEC = builder.build();
        }

        public static double damageMul() { return SKILL_DAMAGE_MULTIPLIER.get(); }
        public static boolean pvpEnabled() {
            try { return PVP_ENABLED.get(); }
            catch (IllegalStateException unloaded) { return true; }
        }
        public static LegacyAbilityRules.Snapshot legacyRules() {
            try {
                return new LegacyAbilityRules.Snapshot(CP_RECOVER_COOLDOWN.get(), CP_RECOVER_SPEED.get().floatValue(),
                        OVERLOAD_RECOVER_COOLDOWN.get(), OVERLOAD_RECOVER_SPEED.get().floatValue(),
                        MAX_CP_INCREASE_RATE.get().floatValue(), MAX_OVERLOAD_INCREASE_RATE.get().floatValue(),
                        PROFICIENCY_INCREASE_RATE.get().floatValue());
            } catch (IllegalStateException unloaded) { return LegacyAbilityRules.DEFAULTS; }
        }
        public static LegacyAbilityRules.SkillTuning skill(String id) {
            try { return LegacyAbilityRules.parseSkill(id, SKILL_OVERRIDES.get()); }
            catch (IllegalStateException unloaded) { return LegacyAbilityRules.parseSkill(id, List.of()); }
        }
        public static boolean generateOres() {
            try { return GENERATE_ORES.get(); }
            catch (IllegalStateException unloaded) { return true; }
        }
        public static boolean generatePhaseLiquid() {
            try { return GENERATE_PHASE_LIQUID.get(); }
            catch (IllegalStateException unloaded) { return true; }
        }
        public static boolean giveCloudTerminal() {
            try { return GIVE_CLOUD_TERMINAL.get(); }
            catch (IllegalStateException unloaded) { return true; }
        }
        public static List<? extends String> normalMetalBlocks() {
            try { return NORMAL_METAL_BLOCKS.get(); }
            catch (IllegalStateException unloaded) { return DEFAULT_NORMAL_METAL_BLOCKS; }
        }
        public static List<? extends String> weakMetalBlocks() {
            try { return WEAK_METAL_BLOCKS.get(); }
            catch (IllegalStateException unloaded) { return DEFAULT_WEAK_METAL_BLOCKS; }
        }
        public static List<? extends String> metalEntities() {
            try { return METAL_ENTITIES.get(); }
            catch (IllegalStateException unloaded) { return DEFAULT_METAL_ENTITIES; }
        }
        public static boolean mayDestroyBlocks(net.minecraft.world.level.Level level) {
            try {
                if (DESTROY_BLOCKS.get()) return true;
                if (level == null) return false;
                String dimension = level.dimension().location().toString();
                return LegacyDimensionAllowlist.contains(dimension, BLOCK_DESTRUCTION_DIMENSION_ALLOWLIST.get());
            } catch (IllegalStateException unloaded) {
                return true;
            }
        }

    }

    private static boolean validRegistryListEntry(Object value) {
        if (!(value instanceof String text)) return false;
        text = text.strip();
        if (text.isEmpty() || text.length() > 128 || text.chars().anyMatch(Character::isWhitespace)) return false;
        return true;
    }

    // ==================== 客户端配置 ====================

    public static final class Client {
        public static final ModConfigSpec SPEC;
        public static final ModConfigSpec.BooleanValue SHOW_HUD;
        public static final ModConfigSpec.BooleanValue SHOW_CP_BAR;
        public static final ModConfigSpec.IntValue CP_BAR_X;
        public static final ModConfigSpec.IntValue CP_BAR_Y;
        public static final ModConfigSpec.IntValue KEY_HINT_X;
        public static final ModConfigSpec.IntValue KEY_HINT_Y;
        public static final ModConfigSpec.BooleanValue AUTO_AVOID_JADE;
        public static final ModConfigSpec.BooleanValue SHOW_CHARGING_HUD;
        public static final ModConfigSpec.BooleanValue SHOW_KEY_HINTS;
        public static final ModConfigSpec.BooleanValue ENABLE_SKILL_SOUNDS;
        public static final ModConfigSpec.DoubleValue MEDIA_PLAYER_VOLUME;
        public static final ModConfigSpec.BooleanValue HEADS_OR_TAILS;

        static {
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

            builder.push("hud");
            SHOW_HUD = builder
                    .comment("Show main HUD overlay.")
                    .define("showHud", true);
            SHOW_CP_BAR = builder
                    .comment("Show CP bar overlay.")
                    .define("showCpBar", true);
            CP_BAR_X = builder
                    .comment("CP bar horizontal offset from its legacy top-right anchor.")
                    .defineInRange("cpBarX", 0, -1000, 1000);
            CP_BAR_Y = builder
                    .comment("CP bar top coordinate. The in-game HUD editor updates this value.")
                    .defineInRange("cpBarY", 30, 0, 500);
            KEY_HINT_X = builder
                    .comment("Ability key-hint horizontal offset from its right-side anchor.")
                    .defineInRange("keyHintX", 0, -1000, 1000);
            KEY_HINT_Y = builder
                    .comment("Ability key-hint vertical offset from screen centre.")
                    .defineInRange("keyHintY", 0, -1000, 1000);
            AUTO_AVOID_JADE = builder
                    .comment("Move the CP bar away from Jade's default tooltip area unless a HUD editor is open.")
                    .define("autoAvoidJade", true);
            SHOW_CHARGING_HUD = builder
                    .comment("Show charging progress HUD.")
                    .define("showChargingHud", true);
            SHOW_KEY_HINTS = builder
                    .comment("Show key binding hints.")
                    .define("showKeyHints", true);
            builder.pop();

            builder.push("generic");
            HEADS_OR_TAILS = builder
                    .comment("Show a heads-or-tails message after a normal coin toss (legacy generic.headsOrTails).")
                    .define("headsOrTails", false);
            builder.pop();

            builder.push("audio");
            ENABLE_SKILL_SOUNDS = builder
                    .comment("Enable skill sound effects.")
                    .define("enableSkillSounds", true);
            MEDIA_PLAYER_VOLUME = builder
                    .comment("Media Player volume (legacy media_player.volume).")
                    .defineInRange("mediaPlayerVolume", 1.0, 0.0, 1.0);
            builder.pop();

            SPEC = builder.build();
        }

        public static boolean showHud() { return booleanOr(SHOW_HUD, true); }
        public static boolean showCpBar() { return booleanOr(SHOW_CP_BAR, true); }
        public static int cpBarX() { return intOr(CP_BAR_X, 0); }
        public static int cpBarY() { return intOr(CP_BAR_Y, 30); }
        public static int keyHintX() { return intOr(KEY_HINT_X, 0); }
        public static int keyHintY() { return intOr(KEY_HINT_Y, 0); }
        public static boolean autoAvoidJade() { return booleanOr(AUTO_AVOID_JADE, true); }
        public static boolean showChargingHud() { return booleanOr(SHOW_CHARGING_HUD, true); }
        public static boolean showKeyHints() { return booleanOr(SHOW_KEY_HINTS, true); }
        public static boolean enableSkillSounds() { return booleanOr(ENABLE_SKILL_SOUNDS, true); }
        public static float mediaPlayerVolume() {
            try { return MEDIA_PLAYER_VOLUME.get().floatValue(); }
            catch (IllegalStateException unloaded) { return 1.0f; }
        }
        public static boolean headsOrTails() {
            try { return HEADS_OR_TAILS.get(); }
            catch (IllegalStateException unloaded) { return false; }
        }

        private static boolean booleanOr(ModConfigSpec.BooleanValue value, boolean fallback) {
            try { return value.get(); }
            catch (IllegalStateException unloaded) { return fallback; }
        }

        private static int intOr(ModConfigSpec.IntValue value, int fallback) {
            try { return value.get(); }
            catch (IllegalStateException unloaded) { return fallback; }
        }
    }

    // ==================== 事件处理 ====================

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
    }
}
