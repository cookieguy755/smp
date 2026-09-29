package dev.gems;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Registriert /gem als echten Paper-Brigadier-Command (mit Tab-Completion und
 * Argument-Vorschlägen), statt über den veralteten "commands:"-Block in der
 * plugin.yml - der wird von Paper 26.x beim Laden von Plugins ignoriert.
 */
public class GemCommand {

    private final GemsPlugin plugin;

    public GemCommand(GemsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final SuggestionProvider<CommandSourceStack> GEM_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (GemType type : GemType.values()) {
            String name = type.name().toLowerCase();
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("gem")
                .executes(this::help)
                .then(Commands.literal("info")
                        .executes(this::info))
                .then(Commands.literal("give")
                        .requires(src -> src.getSender().hasPermission("gems.admin"))
                        .then(Commands.argument("spieler", ArgumentTypes.player())
                                .then(Commands.argument("gem", StringArgumentType.word())
                                        .suggests(GEM_SUGGESTIONS)
                                        .executes(this::give))))
                .then(Commands.literal("setlevel")
                        .requires(src -> src.getSender().hasPermission("gems.admin"))
                        .then(Commands.argument("spieler", ArgumentTypes.player())
                                .then(Commands.argument("gem", StringArgumentType.word())
                                        .suggests(GEM_SUGGESTIONS)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(this::setLevel)))))
                .then(Commands.literal("addxp")
                        .requires(src -> src.getSender().hasPermission("gems.admin"))
                        .then(Commands.argument("spieler", ArgumentTypes.player())
                                .then(Commands.argument("gem", StringArgumentType.word())
                                        .suggests(GEM_SUGGESTIONS)
                                        .then(Commands.argument("xp", IntegerArgumentType.integer(1))
                                                .executes(this::addXp)))))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("gems.admin"))
                        .executes(this::reload))
                .build();
    }

    // ------------------------------------------------------------------

    private void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    private Player resolveSinglePlayer(CommandContext<CommandSourceStack> ctx, String argName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument(argName, PlayerSelectorArgumentResolver.class);
        List<Player> players = resolver.resolve(ctx.getSource());
        return players.isEmpty() ? null : players.get(0);
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getSender();
        msg(s, "/gem info", NamedTextColor.YELLOW);
        if (s.hasPermission("gems.admin")) {
            msg(s, "/gem give <Spieler> <Gem>", NamedTextColor.YELLOW);
            msg(s, "/gem setlevel <Spieler> <Gem> <Level>", NamedTextColor.YELLOW);
            msg(s, "/gem addxp <Spieler> <Gem> <XP>", NamedTextColor.YELLOW);
            msg(s, "/gem reload", NamedTextColor.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            msg(sender, "Nur für Spieler.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        msg(player, "=== Deine Gems ===", NamedTextColor.GOLD);
        UUID id = player.getUniqueId();
        for (GemType type : GemType.values()) {
            int level = plugin.getPlayerData().getLevel(id, type);
            int xp = plugin.getPlayerData().getXp(id, type);
            int needed = plugin.getLevelManager().xpNeeded(type, level);
            String progress = needed > 0 ? xp + "/" + needed + " XP" : "MAX";
            String rareTag = type.isRare() ? " ✦" : "";
            player.sendMessage(Component.text(type.getDisplayName() + rareTag + ": ", type.getColor())
                    .append(Component.text("Level " + level + " (" + progress + ")", NamedTextColor.WHITE))
                    .append(Component.text("  [" + type.getAbility1() + " / " + type.getAbility2() + "]",
                            NamedTextColor.GRAY)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Player target = resolveSinglePlayer(ctx, "spieler");
        GemType type = GemType.fromString(StringArgumentType.getString(ctx, "gem"));
        if (target == null || type == null) {
            msg(sender, "Spieler oder Gem nicht gefunden.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        GemListener.giveItems(target, List.of(plugin.getGemManager().createGem(type)));
        msg(sender, target.getName() + " hat das " + type.getDisplayName() + " erhalten.", NamedTextColor.GREEN);
        return Command.SINGLE_SUCCESS;
    }

    private int setLevel(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Player target = resolveSinglePlayer(ctx, "spieler");
        GemType type = GemType.fromString(StringArgumentType.getString(ctx, "gem"));
        int level = IntegerArgumentType.getInteger(ctx, "level");
        if (target == null || type == null) {
            msg(sender, "Spieler oder Gem nicht gefunden.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        plugin.getLevelManager().setLevel(target.getUniqueId(), type, level);
        msg(sender, type.getDisplayName() + " von " + target.getName() + " ist jetzt Level "
                + plugin.getPlayerData().getLevel(target.getUniqueId(), type) + ".", NamedTextColor.GREEN);
        return Command.SINGLE_SUCCESS;
    }

    private int addXp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Player target = resolveSinglePlayer(ctx, "spieler");
        GemType type = GemType.fromString(StringArgumentType.getString(ctx, "gem"));
        int xp = IntegerArgumentType.getInteger(ctx, "xp");
        if (target == null || type == null) {
            msg(sender, "Spieler oder Gem nicht gefunden.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        plugin.getLevelManager().addXp(target, type, xp);
        msg(sender, xp + " XP für " + type.getDisplayName() + " an " + target.getName() + " vergeben.",
                NamedTextColor.GREEN);
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        plugin.reloadConfig();
        plugin.getAmethystRecipe().register();
        msg(ctx.getSource().getSender(), "Konfiguration neu geladen.", NamedTextColor.GREEN);
        return Command.SINGLE_SUCCESS;
    }
}
