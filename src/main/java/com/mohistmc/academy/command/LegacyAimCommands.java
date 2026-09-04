package com.mohistmc.academy.command;

import com.mohistmc.academy.advancement.LegacyAdvancementBridge;
import com.mohistmc.academy.api.event.AbilityEvents;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Server-authoritative port of AcademyCraft 1.0.7's /aim and /aimp command
 * library. All state changes are committed on the server attachment, publish
 * the matching public lifecycle events, and are synchronised immediately.
 */
public final class LegacyAimCommands {
    private static final int REQUIRED_PERMISSION = 2;

    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(value -> Component.translatable(
                    "commands.academy.aim.error.unknown_category", value));
    private static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    AbilityCategory.all().stream().map(AbilityCategory::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    SkillRegistry.getAllSkills().stream().map(Skill::getId).distinct().sorted(), builder);

    private LegacyAimCommands() {}

    @FunctionalInterface
    private interface PlayerResolver {
        Collection<ServerPlayer> resolve(CommandContext<CommandSourceStack> context)
                throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var self = Commands.literal("aim").requires(source -> source.hasPermission(REQUIRED_PERMISSION));
        attachActions(self, context -> List.of(context.getSource().getPlayerOrException()), false);
        dispatcher.register(self);

        var targets = Commands.argument("targets", EntityArgument.players());
        attachActions(targets, context -> EntityArgument.getPlayers(context, "targets"), true);
        dispatcher.register(Commands.literal("aimp")
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(context -> help(context.getSource(), true))
                .then(targets));
    }

    private static void attachActions(ArgumentBuilder<CommandSourceStack, ?> root,
                                      PlayerResolver players, boolean targeted) {
        root.executes(context -> help(context.getSource(), targeted));
        root.then(Commands.literal("help").executes(context -> help(context.getSource(), targeted)));
        root.then(Commands.literal("?").executes(context -> help(context.getSource(), targeted)));
        root.then(Commands.literal("info").executes(context -> info(context, players)));

        var devMode = Commands.literal("devmode")
                .executes(context -> setDevMode(context, players, null));
        devMode.then(Commands.literal("on").executes(context -> setDevMode(context, players, true)));
        devMode.then(Commands.literal("off").executes(context -> setDevMode(context, players, false)));
        root.then(devMode);
        root.then(Commands.literal("cheats_on")
                .executes(context -> setDevMode(context, players, true)));
        root.then(Commands.literal("cheats_off")
                .executes(context -> setDevMode(context, players, false)));

        var category = Commands.literal("cat")
                .executes(context -> showCategory(context, players));
        category.then(Commands.argument("category", StringArgumentType.word())
                .suggests(CATEGORY_SUGGESTIONS)
                .executes(context -> setCategory(context, players,
                        StringArgumentType.getString(context, "category"))));
        for (int index = 0; index < AbilityCategory.all().size(); index++) {
            String indexedCategory = "#" + index;
            category.then(Commands.literal(indexedCategory)
                    .executes(context -> setCategory(context, players, indexedCategory)));
        }
        root.then(category);
        root.then(Commands.literal("catlist").executes(LegacyAimCommands::listCategories));

        var learn = Commands.literal("learn");
        learn.then(Commands.argument("skill", StringArgumentType.word())
                .suggests(SKILL_SUGGESTIONS)
                .executes(context -> learn(context, players,
                        StringArgumentType.getString(context, "skill"))));
        var unlearn = Commands.literal("unlearn");
        unlearn.then(Commands.argument("skill", StringArgumentType.word())
                .suggests(SKILL_SUGGESTIONS)
                .executes(context -> unlearn(context, players,
                        StringArgumentType.getString(context, "skill"))));
        int maxSkillCount = AbilityCategory.all().stream()
                .mapToInt(categoryEntry -> SkillRegistry.getSkillsByCategory(categoryEntry).size())
                .max().orElse(0);
        for (int index = 0; index < maxSkillCount; index++) {
            String indexedSkill = "#" + index;
            learn.then(Commands.literal(indexedSkill)
                    .executes(context -> learn(context, players, indexedSkill)));
            unlearn.then(Commands.literal(indexedSkill)
                    .executes(context -> unlearn(context, players, indexedSkill)));
        }
        root.then(learn);
        root.then(unlearn);
        root.then(Commands.literal("learn_all").executes(context -> learnAll(context, players)));
        root.then(Commands.literal("learned").executes(context -> listLearned(context, players)));
        root.then(Commands.literal("skills").executes(context -> listSkills(context, players)));

        var level = Commands.literal("level").executes(context -> showLevel(context, players));
        level.then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                .executes(context -> setLevel(context, players,
                        IntegerArgumentType.getInteger(context, "level"))));
        root.then(level);

        var exp = Commands.literal("exp");
        var expSkill = Commands.argument("skill", StringArgumentType.word())
                .suggests(SKILL_SUGGESTIONS)
                .executes(context -> showExp(context, players,
                        StringArgumentType.getString(context, "skill")));
        expSkill.then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                .executes(context -> setExp(context, players,
                        StringArgumentType.getString(context, "skill"),
                        FloatArgumentType.getFloat(context, "value"))));
        exp.then(expSkill);
        for (int index = 0; index < maxSkillCount; index++) {
            String indexedSkill = "#" + index;
            var indexedExp = Commands.literal(indexedSkill)
                    .executes(context -> showExp(context, players, indexedSkill));
            indexedExp.then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                    .executes(context -> setExp(context, players, indexedSkill,
                            FloatArgumentType.getFloat(context, "value"))));
            exp.then(indexedExp);
        }
        root.then(exp);

        root.then(Commands.literal("fullcp").executes(context -> fillCp(context, players)));
        root.then(Commands.literal("cd_clear").executes(context -> clearCooldowns(context, players)));
        root.then(Commands.literal("maxout").executes(context -> maxOut(context, players)));
        root.then(Commands.literal("reset").executes(context -> reset(context, players)));
    }

    private static int help(CommandSourceStack source, boolean targeted) {
        source.sendSuccess(() -> Component.translatable("commands.academy.aim.help.header"), false);
        String prefix = targeted ? "/aimp <targets> " : "/aim ";
        helpLine(source, prefix + "info", "commands.academy.aim.help.info");
        helpLine(source, prefix + "cat [category] | catlist", "commands.academy.aim.help.category");
        helpLine(source, prefix + "learn <skill> | unlearn <skill>", "commands.academy.aim.help.learn");
        helpLine(source, prefix + "learn_all | learned | skills", "commands.academy.aim.help.skills");
        helpLine(source, prefix + "level [1..5]", "commands.academy.aim.help.level");
        helpLine(source, prefix + "exp <skill> [0..1]", "commands.academy.aim.help.exp");
        helpLine(source, prefix + "fullcp | cd_clear | maxout", "commands.academy.aim.help.resources");
        helpLine(source, prefix + "devmode [on|off]", "commands.academy.aim.help.devmode");
        helpLine(source, prefix + "reset", "commands.academy.aim.help.reset");
        return 1;
    }

    private static void helpLine(CommandSourceStack source, String syntax, String descriptionKey) {
        source.sendSuccess(() -> Component.literal(syntax + " - ")
                .append(Component.translatable(descriptionKey)), false);
    }

    private static int info(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = resolver.resolve(context);
        for (ServerPlayer player : players) {
            PlayerAbilityData data = data(player);
            Component ability = data.hasAbility()
                    ? Component.translatable(data.getCurrentAbility().getTranslationKey())
                    : Component.translatable("commands.academy.aim.none");
            Component devMode = Component.translatable(data.isDevMode()
                    ? "commands.academy.aim.state.on" : "commands.academy.aim.state.off");
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.academy.aim.info", player.getDisplayName(), ability,
                    data.getPlayerLevel(), (int) data.getCurrentCp(), (int) data.getMaxCp(),
                    (int) data.getCurrentOverload(), (int) data.getMaxOverload(),
                    devMode, data.getLearnedSkills().size()), false);
        }
        return players.size();
    }

    private static int setDevMode(CommandContext<CommandSourceStack> context,
                                  PlayerResolver resolver, Boolean requested)
            throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            boolean enabled = requested == null ? !data.isDevMode() : requested;
            if (data.isDevMode() != enabled) {
                data.setDevMode(enabled);
                data.syncTo(player);
                changed++;
            }
            send(context.getSource(), "commands.academy.aim.devmode", player.getDisplayName(), enabled);
        }
        return changed;
    }

    private static int showCategory(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = resolver.resolve(context);
        for (ServerPlayer player : players) {
            PlayerAbilityData data = data(player);
            Component category = data.hasAbility()
                    ? Component.translatable(data.getCurrentAbility().getTranslationKey())
                    : Component.translatable("commands.academy.aim.none");
            send(context.getSource(), "commands.academy.aim.category.current",
                    player.getDisplayName(), category);
        }
        return players.size();
    }

    private static int setCategory(CommandContext<CommandSourceStack> context, PlayerResolver resolver,
                                   String raw) throws CommandSyntaxException {
        AbilityCategory category = resolveCategory(raw);
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            AbilityCategory oldCategory = data.getCurrentAbility();
            if (category.equals(oldCategory)) {
                send(context.getSource(), "commands.academy.aim.category.unchanged",
                        player.getDisplayName(), Component.translatable(category.getTranslationKey()));
                continue;
            }
            int oldLevel = data.getPlayerLevel();
            boolean wasActive = data.isAbilityActive();
            data.reset();
            data.clearCooldowns();
            data.setCurrentAbility(category);
            data.setPlayerLevel(1);
            if (wasActive) NeoForge.EVENT_BUS.post(new AbilityEvents.Deactivate(player));
            NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory, category));
            if (oldLevel != 1) NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, 1));
            LegacyAdvancementBridge.levels(player, data);
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.category.set", player.getDisplayName(),
                    Component.translatable(category.getTranslationKey()));
            changed++;
        }
        return changed;
    }

    private static int listCategories(CommandContext<CommandSourceStack> context) {
        int index = 0;
        for (AbilityCategory category : AbilityCategory.all()) {
            int listedIndex = index++;
            context.getSource().sendSuccess(() -> Component.literal("#" + listedIndex + " "
                    + category.id() + " - ").append(Component.translatable(category.getTranslationKey())), false);
        }
        return index;
    }

    private static int learn(CommandContext<CommandSourceStack> context, PlayerResolver resolver,
                             String raw) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            Skill skill = resolveSkill(data, raw);
            if (skill == null) {
                sendFailure(context.getSource(), "commands.academy.aim.error.unknown_skill_for_player",
                        player.getDisplayName(), raw);
                continue;
            }
            if (data.getLearnedSkills().contains(skill.getId())) {
                send(context.getSource(), "commands.academy.aim.skill.already_learned",
                        player.getDisplayName(), Component.translatable(skill.getTranslationKey()));
                continue;
            }
            learnSkill(player, data, skill);
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.skill.learned", player.getDisplayName(),
                    Component.translatable(skill.getTranslationKey()));
            changed++;
        }
        return changed;
    }

    private static int unlearn(CommandContext<CommandSourceStack> context, PlayerResolver resolver,
                               String raw) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            Skill skill = resolveSkill(data, raw);
            if (skill == null) {
                sendFailure(context.getSource(), "commands.academy.aim.error.unknown_skill_for_player",
                        player.getDisplayName(), raw);
                continue;
            }
            List<int[]> boundSlots = new ArrayList<>();
            for (int preset = 0; preset < PlayerAbilityData.PRESET_COUNT; preset++) {
                for (int slot = 0; slot < com.mohistmc.academy.skill.SkillPreset.SLOT_COUNT; slot++) {
                    if (skill.getId().equals(data.getSlotSkillId(preset, slot))) {
                        boundSlots.add(new int[]{preset, slot});
                    }
                }
            }
            if (!data.unlearnSkill(skill.getId())) {
                send(context.getSource(), "commands.academy.aim.skill.not_learned",
                        player.getDisplayName(), Component.translatable(skill.getTranslationKey()));
                continue;
            }
            for (int[] bound : boundSlots) {
                NeoForge.EVENT_BUS.post(new AbilityEvents.PresetUpdated(player,
                        bound[0], bound[1], skill.getId(), null));
            }
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.skill.unlearned", player.getDisplayName(),
                    Component.translatable(skill.getTranslationKey()));
            changed++;
        }
        return changed;
    }

    private static int learnAll(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            int learned = 0;
            for (Skill skill : SkillRegistry.getSkillsByCategory(data.getCurrentAbility())) {
                if (!data.getLearnedSkills().contains(skill.getId())) {
                    learnSkill(player, data, skill);
                    learned++;
                }
            }
            if (learned > 0) {
                data.syncTo(player);
                changed++;
            }
            send(context.getSource(), "commands.academy.aim.skill.learned_all",
                    player.getDisplayName(), learned);
        }
        return changed;
    }

    private static int listLearned(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = resolver.resolve(context);
        for (ServerPlayer player : players) {
            PlayerAbilityData data = data(player);
            List<String> learned = new ArrayList<>(data.getLearnedSkills());
            learned.sort(String::compareTo);
            send(context.getSource(), "commands.academy.aim.skill.learned_list",
                    player.getDisplayName(), learned.isEmpty() ? "-" : String.join(", ", learned));
        }
        return players.size();
    }

    private static int listSkills(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        int listed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            send(context.getSource(), "commands.academy.aim.skill.list_header", player.getDisplayName(),
                    Component.translatable(data.getCurrentAbility().getTranslationKey()));
            List<Skill> skills = SkillRegistry.getSkillsByCategory(data.getCurrentAbility());
            for (int index = 0; index < skills.size(); index++) {
                Skill skill = skills.get(index);
                String state = data.getLearnedSkills().contains(skill.getId()) ? "*" : "-";
                int listedIndex = index;
                context.getSource().sendSuccess(() -> Component.literal(state + " #" + listedIndex + " "
                        + skill.getId() + " Lv." + skill.getLevel() + " - ")
                        .append(Component.translatable(skill.getTranslationKey())), false);
                listed++;
            }
        }
        return listed;
    }

    private static int showLevel(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = resolver.resolve(context);
        for (ServerPlayer player : players) {
            send(context.getSource(), "commands.academy.aim.level.current",
                    player.getDisplayName(), data(player).getPlayerLevel());
        }
        return players.size();
    }

    private static int setLevel(CommandContext<CommandSourceStack> context, PlayerResolver resolver,
                                int level) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            int oldLevel = data.getPlayerLevel();
            if (oldLevel == level) {
                send(context.getSource(), "commands.academy.aim.level.unchanged",
                        player.getDisplayName(), level);
                continue;
            }
            data.setPlayerLevel(level);
            NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, level));
            LegacyAdvancementBridge.levels(player, data);
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.level.set",
                    player.getDisplayName(), oldLevel, level);
            changed++;
        }
        return changed;
    }

    private static int showExp(CommandContext<CommandSourceStack> context, PlayerResolver resolver,
                               String raw) throws CommandSyntaxException {
        Collection<ServerPlayer> players = resolver.resolve(context);
        int shown = 0;
        for (ServerPlayer player : players) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            Skill skill = resolveSkill(data, raw);
            if (skill == null) {
                sendFailure(context.getSource(), "commands.academy.aim.error.unknown_skill_for_player",
                        player.getDisplayName(), raw);
                continue;
            }
            send(context.getSource(), "commands.academy.aim.exp.current", player.getDisplayName(),
                    Component.translatable(skill.getTranslationKey()),
                    formatPercent(data.getProficiency(skill.getId())));
            shown++;
        }
        return shown;
    }

    private static int setExp(CommandContext<CommandSourceStack> context, PlayerResolver resolver,
                              String raw, float value) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            Skill skill = resolveSkill(data, raw);
            if (skill == null) {
                sendFailure(context.getSource(), "commands.academy.aim.error.unknown_skill_for_player",
                        player.getDisplayName(), raw);
                continue;
            }
            if (!data.getLearnedSkills().contains(skill.getId())) {
                send(context.getSource(), "commands.academy.aim.skill.not_learned",
                        player.getDisplayName(), Component.translatable(skill.getTranslationKey()));
                continue;
            }
            float oldExp = data.getProficiency(skill.getId());
            if (Float.compare(oldExp, value) == 0) continue;
            data.setProficiency(skill.getId(), value);
            NeoForge.EVENT_BUS.post(new AbilityEvents.SkillExpChanged(player, skill, oldExp, value));
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.exp.set", player.getDisplayName(),
                    Component.translatable(skill.getTranslationKey()),
                    formatPercent(oldExp), formatPercent(value));
            changed++;
        }
        return changed;
    }

    private static int fillCp(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            boolean different = Float.compare(data.getCurrentCp(), data.getMaxCp()) != 0
                    || Float.compare(data.getCurrentOverload(), 0.0f) != 0;
            data.setCurrentCp(data.getMaxCp());
            data.setCurrentOverload(0.0f);
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.fullcp", player.getDisplayName());
            if (different) changed++;
        }
        return changed;
    }

    private static int clearCooldowns(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            int count = data.clearCooldowns();
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.cooldown.cleared",
                    player.getDisplayName(), count);
            if (count > 0) changed++;
        }
        return changed;
    }

    private static int maxOut(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        int changedPlayers = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(context.getSource(), player, data)) continue;
            int level = data.getPlayerLevel();
            boolean changed = level < 5 && data.getLevelProgress() < 1.0f;
            data.maxOutLevelProgress();
            if (changed) {
                data.syncTo(player);
                changedPlayers++;
            }
            send(context.getSource(), "commands.academy.aim.maxout",
                    player.getDisplayName(), level);
        }
        return changedPlayers;
    }

    private static int reset(CommandContext<CommandSourceStack> context, PlayerResolver resolver)
            throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : resolver.resolve(context)) {
            PlayerAbilityData data = data(player);
            AbilityCategory oldCategory = data.getCurrentAbility();
            int oldLevel = data.getPlayerLevel();
            boolean wasActive = data.isAbilityActive();
            boolean hadData = data.hasAbility() || !data.getLearnedSkills().isEmpty() || oldLevel != 0;
            data.reset();
            data.clearCooldowns();
            if (wasActive) NeoForge.EVENT_BUS.post(new AbilityEvents.Deactivate(player));
            if (oldCategory != null) NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory, null));
            if (oldLevel != 0) NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, 0));
            data.syncTo(player);
            send(context.getSource(), "commands.academy.aim.reset", player.getDisplayName());
            if (hadData) changed++;
        }
        return changed;
    }

    private static void learnSkill(ServerPlayer player, PlayerAbilityData data, Skill skill) {
        data.learnSkill(skill.getId());
        NeoForge.EVENT_BUS.post(new AbilityEvents.SkillLearned(player, skill));
        LegacyAdvancementBridge.learned(player, data, skill);
    }

    private static String formatPercent(float value) {
        return String.format(Locale.ROOT, "%.1f", value * 100.0f);
    }

    private static PlayerAbilityData data(ServerPlayer player) {
        return player.getData(AcademyAttachments.PLAYER_ABILITY);
    }

    private static boolean requireAbility(CommandSourceStack source, ServerPlayer player,
                                          PlayerAbilityData data) {
        if (data.hasAbility()) return true;
        sendFailure(source, "commands.academy.aim.error.no_ability", player.getDisplayName());
        return false;
    }

    private static AbilityCategory resolveCategory(String raw) throws CommandSyntaxException {
        String normalized = normalizeIndexToken(raw);
        AbilityCategory direct = AbilityCategory.fromId(normalized);
        if (direct != null) return direct;
        try {
            int index = Integer.parseInt(normalized);
            List<AbilityCategory> categories = new ArrayList<>(AbilityCategory.all());
            if (index >= 0 && index < categories.size()) return categories.get(index);
        } catch (NumberFormatException ignored) {
        }
        throw UNKNOWN_CATEGORY.create(raw);
    }

    private static Skill resolveSkill(PlayerAbilityData data, String raw) {
        if (!data.hasAbility()) return null;
        List<Skill> skills = SkillRegistry.getSkillsByCategory(data.getCurrentAbility());
        String normalized = normalizeIndexToken(raw);
        for (Skill skill : skills) {
            if (skill.getId().equalsIgnoreCase(normalized)) return skill;
        }
        try {
            int index = Integer.parseInt(normalized);
            if (index >= 0 && index < skills.size()) return skills.get(index);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static String normalizeIndexToken(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        return normalized.startsWith("#") ? normalized.substring(1) : normalized;
    }

    private static void send(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args), false);
    }

    private static void sendFailure(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable(key, args));
    }
}
