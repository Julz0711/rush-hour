package io.github.julz0711.rushhour;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import static io.github.julz0711.rushhour.game.Objective.Type;

final class RushCommands {
    static void register(RushHourPlugin plugin, Commands registrar) {
        var root = Commands.literal("rush").requires(s -> s.getSender().hasPermission("rushhour.play") || s.getSender().hasPermission("rushhour.host"))
                .executes(c -> execute(c, sender -> sender.sendMessage(Component.text("Rush Hour · /rush setup · start · stop · catalog · team · top · skip · board · bp · spectate\nHost: pause · resume · addtime <seconds> · balance · recap speed|skip · set <setting> <value> · preset save|load <name> · reload", NamedTextColor.GOLD))));
        root.then(host("setup").executes(c -> execute(c, s -> plugin.menus().setup(player(s), 0))));
        root.then(host("start").executes(c -> execute(c, s -> plugin.game().start())));
        root.then(host("stop").executes(c -> execute(c, s -> plugin.game().stop())));
        root.then(host("pause").executes(c -> execute(c, s -> plugin.game().pause())));
        root.then(host("resume").executes(c -> execute(c, s -> plugin.game().resume())));
        root.then(host("balance").executes(c -> execute(c, s -> plugin.game().balance())));
        root.then(host("reload").executes(c -> execute(c, s -> {
            if (plugin.game().locked()) throw new IllegalStateException("Settings are locked");
            plugin.reloadConfig(); plugin.game().reloadSettings(); s.sendMessage(Component.text("Settings reloaded. Restart the server to reload items.yml and objectives.yml."));
        })));
        root.then(host("addtime").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 21600)).executes(c -> execute(c, s -> plugin.game().addTime(IntegerArgumentType.getInteger(c, "seconds"))))));
        root.then(host("recap").then(Commands.argument("action", StringArgumentType.word())
                .suggests((c, b) -> { b.suggest("speed"); b.suggest("skip"); return b.buildFuture(); })
                .executes(c -> execute(c, s -> plugin.game().recap(StringArgumentType.getString(c, "action"))))));
        root.then(host("catalog").executes(c -> execute(c, s -> plugin.menus().catalog(player(s), Type.ITEM, "", "", 0)))
                .then(Commands.argument("search", StringArgumentType.greedyString()).executes(c -> execute(c, s -> plugin.menus().catalog(player(s), Type.ITEM, StringArgumentType.getString(c, "search"), "", 0)))));
        root.then(host("set").then(Commands.argument("setting", StringArgumentType.word()).suggests((c, b) -> {
            plugin.getConfig().getValues(true).forEach((key, value) -> { if (!(value instanceof org.bukkit.configuration.ConfigurationSection) && key.startsWith(b.getRemaining())) b.suggest(key); });
            return b.buildFuture();
        }).then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> execute(c, s -> {
            plugin.menus().setFromText(StringArgumentType.getString(c, "setting"), StringArgumentType.getString(c, "value")); s.sendMessage(Component.text("Setting saved"));
        })))));
        var preset = host("preset");
        for (String action : List.of("save", "load")) preset.then(Commands.literal(action).then(Commands.argument("name", StringArgumentType.word()).executes(c -> execute(c, s -> {
            if (plugin.game().locked()) throw new IllegalStateException("Catalog is locked during rounds");
            plugin.catalog().preset(action, StringArgumentType.getString(c, "name")); s.sendMessage(Component.text("Preset " + action + " complete"));
        }))));
        root.then(preset);
        root.then(play("team").executes(c -> execute(c, s -> plugin.menus().teams(player(s))))
                .then(Commands.argument("number", IntegerArgumentType.integer(0, 8)).executes(c -> execute(c, s -> plugin.game().team(player(s), IntegerArgumentType.getInteger(c, "number"))))));
        root.then(play("top").executes(c -> execute(c, s -> plugin.game().top(player(s)))));
        root.then(play("board").executes(c -> execute(c, s -> plugin.menus().board(player(s)))));
        root.then(play("skip").executes(c -> execute(c, s -> {
            if (plugin.game().round() != null && plugin.game().round().mode() == io.github.julz0711.rushhour.game.Round.Mode.BINGO) plugin.menus().board(player(s));
            else plugin.game().skip(player(s), 0);
        })).then(Commands.argument("square", IntegerArgumentType.integer(1, 25)).executes(c -> execute(c, s -> plugin.game().skip(player(s), IntegerArgumentType.getInteger(c, "square") - 1)))));
        root.then(play("bp").executes(c -> execute(c, s -> plugin.game().backpack(player(s)))));
        root.then(play("spectate").executes(c -> execute(c, s -> plugin.menus().spectators(player(s)))));
        registrar.register(root.build(), "Rush Hour survival race");
        registrar.register(play("bp").executes(c -> execute(c, s -> plugin.game().backpack(player(s)))).build(), "Rush Hour team backpack");
    }
    private static LiteralArgumentBuilder<CommandSourceStack> host(String name) { return Commands.literal(name).requires(s -> s.getSender().hasPermission("rushhour.host")); }
    private static LiteralArgumentBuilder<CommandSourceStack> play(String name) { return Commands.literal(name).requires(s -> s.getSender().hasPermission("rushhour.play")); }
    private static Player player(CommandSender sender) { if (sender instanceof Player p) return p; throw new IllegalStateException("Run this command in game"); }
    @FunctionalInterface private interface Action { void run(CommandSender sender) throws Exception; }
    private static int execute(CommandContext<CommandSourceStack> context, Action action) {
        CommandSender sender = context.getSource().getSender();
        try { action.run(sender); return 1; }
        catch (Exception e) { sender.sendMessage(Component.text(e.getMessage() == null ? "Rush Hour action failed; check the server log" : e.getMessage(), NamedTextColor.RED)); return 0; }
    }
}
