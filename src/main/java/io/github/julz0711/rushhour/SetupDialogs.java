package io.github.julz0711.rushhour;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.julz0711.rushhour.game.Objective.*;

/** The host's round setup, presented as labeled vanilla screens. */
final class SetupDialogs {
    private static final int WIDTH = 244;
    private static final int BUTTON_WIDTH = 116;
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .lifetime(Duration.ofMinutes(15))
            .build();

    private enum Section {
        FORMAT("Round format", "Classic gives each player or team its own target. Race shares a target. Bingo uses a 5×5 card."),
        WORLDS("Worlds", "Choose the dimensions in play. Fresh worlds are generated for the round. The recap world is where the results are shown."),
        RULES("Survival rules", "Set combat, inventory and skip rules. List starter items as MATERIAL:amount, separated by commas. Leave the kit blank for no items."),
        OBJECTIVES("Objectives", "Weights control how often each kind of objective appears. A weight of 0 turns that kind off. Use the catalog to choose individual targets."),
        SCORING("Scoring", "Use difficulty tiers to vary points and target frequency. A tier weight of 0 excludes that tier when tiers are on."),
        TIMING("Timing & joins", "Enter the round length as minutes:seconds or hours:minutes:seconds (5 minutes to 6 hours). Use 0 for no time limit.");

        private final String title;
        private final String description;

        Section(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private final RushHourPlugin plugin;
    private final Menus menus;

    SetupDialogs(RushHourPlugin plugin, Menus menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    void open(Player player) {
        open(player, null);
    }

    private void open(Player player, String notice) {
        menus.hostLobby(player);
        Settings settings = plugin.game().settings();
        List<DialogBody> body = new ArrayList<>(summary(settings));
        if (notice != null) body.add(line(notice, NamedTextColor.GREEN));

        List<ActionButton> actions = new ArrayList<>();
        for (Section section : Section.values()) {
            actions.add(button(player, section.title, NamedTextColor.WHITE,
                    response -> edit(player, section, null, null)));
        }
        actions.add(button(player, "Browse catalog", NamedTextColor.WHITE, response -> browse(player)));
        actions.add(button(player, "Review & start", NamedTextColor.GREEN, response -> review(player)));
        show(player, "Round setup", body, List.of(), actions, close(), 2);
    }

    void objectives(Player player) {
        edit(player, Section.OBJECTIVES, null, null);
    }

    private void browse(Player player) {
        player.closeDialog();
        menus.catalog(player, Type.ITEM, "", "", 0);
    }

    private void edit(Player player, Section section, DialogResponseView draft, String error) {
        menus.hostLobby(player);
        Form form = new Form(draft);
        switch (section) {
            case FORMAT -> {
                form.choice("game-type", "Round type", "CLASSIC", "Classic", "RACE", "Race", "BINGO", "Bingo");
                form.choice("mode", "Players", "SOLO", "Solo", "TEAMS", "Teams");
                form.slider("teams.count", "Number of teams", 2, 8);
                form.number("teams.size-limit", "Players per team (1–100)");
                form.number("bingo-row-bonus", "Bingo row bonus (0–1000 points)");
            }
            case WORLDS -> {
                form.dimensions();
                form.toggle("world.fresh", "Generate fresh round worlds");
                form.number("world.spread-radius", "Starting spread (0–2048 blocks)");
                form.string("world.lobby", "Recap world (blank for default)");
                form.string("world.seed", "Fresh world seed (blank for random)");
                form.slider("world.pregen-radius-chunks", "Fresh world preparation radius (chunks)", 0, 16);
            }
            case RULES -> {
                form.toggle("rules.keep-inventory", "Keep items on death");
                form.toggle("rules.pvp", "Allow player combat");
                form.choice("rules.death-penalty", "Death penalty", "NONE", "None", "POINT", "Lose a point", "TARGET", "Reroll target");
                form.slider("skip-tokens", "Skips per player or team", 0, 10);
                form.toggle("give-skipped-item", "Give the item when a target is skipped");
                form.toggle("chaos", "Randomize block and mob drops");
                form.kit();
            }
            case OBJECTIVES -> {
                form.number("target-weights.ITEM", "Find items");
                form.number("target-weights.MOB", "Defeat mobs");
                form.number("target-weights.ADVANCEMENT", "Earn advancements");
                form.number("target-weights.STRUCTURE", "Discover structures");
                form.number("target-weights.BIOME", "Visit biomes");
            }
            case SCORING -> {
                form.toggle("hidden-leaderboard", "Hide standings until the recap");
                form.toggle("difficulty.enabled", "Use difficulty tiers");
                form.toggle("difficulty.ramp", "Increase difficulty as the round progresses");
                for (Tier tier : Tier.values()) {
                    form.number("difficulty.weights." + tier, friendly(tier) + " frequency");
                    form.number("difficulty.points." + tier, friendly(tier) + " points per find");
                }
            }
            case TIMING -> {
                form.duration();
                form.slider("countdown-seconds", "Start countdown (seconds)", 0, 60);
                form.choice("late-join.mode", "Players joining mid-round", "SPECTATE", "Spectate", "PLAY", "Join the round");
                form.text("late-join.catch-up-points", "Catch-up points per minute (0–100)", 12,
                        plugin.getConfig().getString("late-join.catch-up-points", "0"), Double::parseDouble);
                form.number("late-join.idle-spectate-seconds", "Idle timeout (seconds, 0 for off)");
                form.slider("recap.beat-ticks", "Recap pace (ticks per result)", 2, 100);
            }
        }

        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(line(error, NamedTextColor.RED));
        body.add(line(section.description, NamedTextColor.GRAY));
        ActionButton save = button(player, "Save changes", NamedTextColor.GREEN, response -> {
            try {
                menus.saveSettings(form.read(response));
            } catch (IllegalArgumentException e) {
                edit(player, section, response, e.getMessage());
                return;
            } catch (IllegalStateException e) {
                edit(player, section, null, e.getMessage());
                return;
            }
            open(player, section.title + " saved.");
        });
        show(player, section.title, body, form.inputs, List.of(save),
                button(player, "Back without saving", NamedTextColor.WHITE, response -> open(player)), 1);
    }

    private void review(Player player) {
        review(player, null);
    }

    private void review(Player player, String startError) {
        menus.hostLobby(player);
        Settings settings = plugin.game().settings();
        var plan = plugin.game().startPlan();
        List<DialogBody> body = new ArrayList<>(summary(settings));
        body.add(line(plan.playerCount() + " eligible online · " + plan.pool().entries().size() + " available objectives",
                NamedTextColor.WHITE));
        body.add(line("Countdown: " + settings.countdown + " seconds · Skips: " + settings.skips, NamedTextColor.GRAY));
        if (startError != null && !startError.equals(plan.problem())) body.add(line(startError, NamedTextColor.RED));

        ActionButton primary;
        ActionButton back = button(player, "Back to setup", NamedTextColor.WHITE, response -> open(player));
        if (plan.problem() == null) {
            body.add(line("Players can keep playing until the countdown ends. Rush Hour will then give them their round inventory.",
                    NamedTextColor.GRAY));
            primary = button(player, "Start round", NamedTextColor.GREEN, response -> {
                try {
                    plugin.game().start();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    review(player, e.getMessage());
                }
            });
        } else {
            body.add(line(plan.problem(), NamedTextColor.RED));
            primary = button(player, "Check again", NamedTextColor.WHITE, response -> review(player));
        }
        show(player, "Review round", body, List.of(), List.of(primary), back, 1);
    }

    private static List<DialogBody> summary(Settings settings) {
        String format = settings.teams ? settings.teamCount + " teams · up to " + settings.teamLimit + " each" : "Solo";
        String duration = settings.deadline == 0 ? "No time limit" : GameManager.formatTime(settings.deadline);
        String worlds = settings.dimensions.stream().map(SetupDialogs::friendly).collect(Collectors.joining(", "));
        return List.of(
                line(friendly(settings.gameType) + " · " + format + " · " + duration, NamedTextColor.GOLD),
                line((settings.fresh ? "Fresh worlds" : "Existing worlds") + " · " + worlds, NamedTextColor.GRAY)
        );
    }

    private static DialogBody line(String message, NamedTextColor color) {
        return DialogBody.plainMessage(Component.text(message, color), WIDTH);
    }

    private ActionButton button(Player player, String label, NamedTextColor color, Consumer<DialogResponseView> action) {
        return ActionButton.create(Component.text(label, color), null, BUTTON_WIDTH,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(player.getUniqueId())) return;
                    try {
                        menus.hostLobby(actor);
                        action.accept(response);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        actor.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                    }
                }, CLICK_OPTIONS));
    }

    private static ActionButton close() {
        return ActionButton.create(Component.text("Close"), null, BUTTON_WIDTH, null);
    }

    private static void show(Player player, String title, List<DialogBody> body, List<DialogInput> inputs,
                             List<ActionButton> buttons, ActionButton exit, int columns) {
        player.closeInventory();
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Rush Hour · " + title, NamedTextColor.GOLD))
                        .body(body)
                        .inputs(inputs)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(buttons, exit, columns))));
    }

    private static String friendly(Enum<?> value) {
        String name = value.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
    }

    private record Field(Object original, Function<DialogResponseView, Object> read) { }

    private final class Form {
        private final DialogResponseView draft;
        private final List<DialogInput> inputs = new ArrayList<>();
        private final Map<String, Field> fields = new LinkedHashMap<>();

        Form(DialogResponseView draft) {
            this.draft = draft;
        }

        private void field(String path, Function<DialogResponseView, Object> read) {
            fields.put(path, new Field(plugin.getConfig().get(path), read));
        }

        void toggle(String path, String label) {
            String key = addToggle(label, plugin.getConfig().getBoolean(path));
            field(path, response -> readToggle(response, key, label));
        }

        private String addToggle(String label, boolean initial) {
            String key = inputKey();
            Boolean previous = draft == null ? null : draft.getBoolean(key);
            inputs.add(DialogInput.bool(key, Component.text(label), previous == null ? initial : previous, "true", "false"));
            return key;
        }

        private boolean readToggle(DialogResponseView response, String key, String label) {
            Boolean value = response.getBoolean(key);
            if (value == null) throw new IllegalArgumentException("Choose a value for " + label + ".");
            return value;
        }

        private String inputKey() {
            // Client input identifiers are separate from the dotted YAML paths.
            return "field_" + inputs.size();
        }

        void dimensions() {
            Map<Dimension, String> keys = new EnumMap<>(Dimension.class);
            for (Dimension dimension : Dimension.values()) {
                String key = addToggle("Play in " + friendly(dimension), plugin.game().settings().dimensions.contains(dimension));
                keys.put(dimension, key);
            }
            field("dimensions", response -> keys.entrySet().stream()
                    .filter(entry -> readToggle(response, entry.getValue(), friendly(entry.getKey())))
                    .map(entry -> entry.getKey().name())
                    .toList());
        }

        void slider(String path, String label, int min, int max) {
            String key = inputKey();
            Float previous = draft == null ? null : draft.getFloat(key);
            float initial = previous == null || !Float.isFinite(previous) ? plugin.getConfig().getInt(path) : Math.clamp(previous, min, max);
            inputs.add(DialogInput.numberRange(key, WIDTH, Component.text(label), "%s: %s", min, max, initial, 1f));
            field(path, response -> {
                Float value = response.getFloat(key);
                if (value == null || !Float.isFinite(value) || value < min || value > max || value != value.intValue()) {
                    throw new IllegalArgumentException(label + " must be a whole number from " + min + " to " + max + ".");
                }
                return value.intValue();
            });
        }

        void choice(String path, String label, String... choices) {
            String key = inputKey();
            String selected = draft == null ? null : draft.getText(key);
            String saved = plugin.getConfig().getString(path, choices[0]);
            List<OptionEntry> options = new ArrayList<>();
            for (int i = 0; i < choices.length; i += 2) {
                boolean initial = choices[i].equalsIgnoreCase(selected == null ? saved : selected);
                options.add(OptionEntry.create(choices[i], Component.text(choices[i + 1]), initial));
            }
            inputs.add(DialogInput.singleOption(key, WIDTH, options, Component.text(label), true));
            field(path, response -> {
                String value = response.getText(key);
                if (options.stream().noneMatch(option -> option.id().equals(value))) {
                    throw new IllegalArgumentException("Select a value for " + label.toLowerCase(Locale.ROOT) + ".");
                }
                return value;
            });
        }

        void number(String path, String label) {
            text(path, label, 10, plugin.getConfig().getString(path, "0"), Integer::parseInt);
        }

        void string(String path, String label) {
            text(path, label, 128, plugin.getConfig().getString(path, ""), value -> value);
        }

        void kit() {
            String items = String.join(", ", plugin.getConfig().getStringList("rules.starter-kit"));
            text("rules.starter-kit", "Starter kit", 2048, items,
                    value -> value.isBlank() ? List.of() : Arrays.stream(value.split(",", -1)).map(String::trim).toList());
        }

        void duration() {
            long seconds = plugin.game().settings().deadline;
            text("deadline-seconds", "Round length (m:ss, h:mm:ss or 0)", 8,
                    seconds == 0 ? "0" : GameManager.formatTime(seconds), SetupDialogs::parseDuration);
        }

        void text(String path, String label, int maxLength, String initial, Function<String, Object> parse) {
            String key = inputKey();
            String previous = draft == null ? null : draft.getText(key);
            inputs.add(DialogInput.text(key, WIDTH, Component.text(label), true, previous == null ? initial : previous,
                    Math.max(maxLength, initial.length()), null));
            field(path, response -> {
                String value = response.getText(key);
                if (value == null) throw new IllegalArgumentException("Enter a value for " + label + ".");
                try {
                    return parse.apply(value.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Enter a valid number for " + label + ".");
                }
            });
        }

        Map<String, Object> read(DialogResponseView response) {
            Map<String, Object> values = new LinkedHashMap<>();
            fields.forEach((path, field) -> {
                if (!Objects.equals(plugin.getConfig().get(path), field.original())) {
                    throw new IllegalStateException("These settings changed while this screen was open. Review them before saving.");
                }
                values.put(path, field.read().apply(response));
            });
            return values;
        }
    }

    private static long parseDuration(String value) {
        if (value.equals("0")) return 0;
        if (!value.matches("(?:[0-6]:[0-5]\\d|\\d{1,3}):[0-5]\\d")) {
            throw new IllegalArgumentException("Use m:ss or h:mm:ss for the round length, or 0 for no limit.");
        }
        long seconds = 0;
        for (String part : value.split(":")) seconds = seconds * 60 + Integer.parseInt(part);
        if (seconds < 300 || seconds > 21600) throw new IllegalArgumentException("Round length must be 5 minutes to 6 hours.");
        return seconds;
    }
}
