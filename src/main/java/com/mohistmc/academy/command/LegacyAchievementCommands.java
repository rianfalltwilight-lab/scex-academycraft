package com.mohistmc.academy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative port of AcademyCraft 1.0.7's {@code /acach} command.
 *
 * <p>The old command accepted achievement ids such as {@code phase_liquid} and
 * {@code electromaster.lv1}, not resource locations.  Keep those ids stable so
 * old operator scripts remain usable while resolving them to the generated
 * {@code academy:legacy/...} advancement graph.</p>
 */
public final class LegacyAchievementCommands {
    private static final int REQUIRED_PERMISSION = 2;

    private LegacyAchievementCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var achievement = Commands.argument("achievement", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        LegacyAchievementIds.all(), builder))
                .executes(context -> grant(context,
                        context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> grant(context,
                                EntityArgument.getPlayer(context, "player"))));
        dispatcher.register(Commands.literal("acach")
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(context -> usage(context.getSource()))
                .then(achievement));
    }

    private static int usage(CommandSourceStack source) {
        source.sendFailure(Component.translatable("commands.academy.acach.usage"));
        return 0;
    }

    private static int grant(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        String supplied = StringArgumentType.getString(context, "achievement");
        String path = LegacyAchievementIds.toAdvancementPath(supplied);
        if (path == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.academy.acach.unknown", supplied));
            return 0;
        }

        var advancement = context.getSource().getServer().getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath("academy", "legacy/" + path));
        if (advancement == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.academy.acach.unknown", supplied));
            return 0;
        }

        // 1.0.7 reported success whenever the achievement id existed, including
        // when the player had already earned it. Preserve that observable result.
        player.getAdvancements().award(advancement, "earned");
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.academy.acach.success", supplied, player.getDisplayName()), false);
        return 1;
    }

}
