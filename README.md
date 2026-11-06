# Rush Hour

A server-side survival race for **Paper 26.3 and Java 25**. Find items, kill mobs, earn advancements, visit structures, or reach biomes before the deadline. Vanilla clients can join without a mod or resource pack.

**0.1.1 alpha** includes the PRD's three modes and five objective types. Automated tests cover the game engine, catalog filtering, player recovery, and a complete mocked round. A real multiplayer playtest is still required before a public release.

## Install and play

1. Build with Java 25: `./gradlew build` (Windows: `.\gradlew.bat build`).
2. Replace the previous Rush Hour JAR in your Paper server's `plugins/` folder with `build/libs/RushHour-0.1.1.jar` and restart.
3. Give the host `rushhour.host` (operators have it automatically). Players receive `rushhour.play` by default.
4. Join the server and run `/rush setup`. Choose your settings, then select **Review & start** followed by **Start round**.

Use a dedicated minigame server. The plugin manages one round for all online players with the play permission. Pre-game inventories (including cursor and ender chest), location, game mode, experience, health, food, flight, potion effects, respawn point, tab presentation, and advancement criteria are saved and restored. Ender chests start empty during the round. Recap books are added after restoration. Survival world block/entity changes persist unless you enable a fresh world.

The default is a 60-minute solo Classic round, items only, 3 skips, all three dimensions, and an easy-to-hard difficulty ramp. If your server has disabled the Nether or End, deselect those dimensions before starting or enable fresh worlds.

## Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/rush setup` | `rushhour.host` | Review the round and edit grouped settings in labeled dialogs |
| `/rush catalog [search]` | `rushhour.host` | Browse objective tabs, filter categories, toggle targets |
| `/rush start`, `/rush stop` | `rushhour.host` | Prepare/start, or finish/cancel a round |
| `/rush pause`, `/rush resume` | `rushhour.host` | Freeze players and the round clock |
| `/rush addtime <seconds>` | `rushhour.host` | Add 1–21600 seconds to a timed round |
| `/rush balance` | `rushhour.host` | Auto-balance online players into teams |
| `/rush recap speed`, `/rush recap skip` | `rushhour.host` | Speed up or finish the reveal |
| `/rush set <setting> <value>` | `rushhour.host` | Edit a config value; lists use commas, `empty` clears strings/lists |
| `/rush preset save\|load <name>` | `rushhour.host` | Save/load the complete catalog configuration |
| `/rush reload` | `rushhour.host` | Reload config while in the lobby |
| `/rush team [0–8]` | `rushhour.play` | Open the picker or choose a team; 0 leaves |
| `/rush skip [1–25]` | `rushhour.play` | Spend a token; bingo opens the board if no square is specified |
| `/rush board` | `rushhour.play` | Show the target or 5×5 bingo card |
| `/rush top` | `rushhour.play` | Standings, unavailable during hidden rounds |
| `/bp`, `/rush bp` | `rushhour.play` | Open the shared team backpack |
| `/rush spectate` | `rushhour.play` | Spectator player selector |

The setup dashboard shows the round type, player format, time limit, and selected worlds. Each settings screen has **Save changes** and **Back without saving**. **Review & start** shows eligible players and available objectives and flags anything blocking the start. In the catalog, click to toggle one target and shift-click to toggle its category. Creative-only entries are disabled by default but can be deliberately enabled by a host.

Players can move, mine, build, and manage their inventories as usual between rounds and during world preparation and the countdown. Rush Hour saves their state when the round begins and restores it afterward.

## Modes, teams, and objectives

- **Classic:** each solo player or team has its own target and token bank. Targets do not repeat until the pool cycles; immediate repeats are avoided when there is more than one target.
- **Same-item race (`RACE`):** everyone has the same target. The first confirmed completion scores and advances everyone's target. A skip also advances the shared target and spends only the requesting player's/team's token.
- **Bingo:** a unique 25-objective card per player/team. Each square scores once. Rows, columns, and both diagonals receive the configured line bonus once each. Completing the card ends the round. Skipping replaces an unfinished square, so it needs a spare objective outside the card.
- **Teams:** 2–8 teams, a size limit, shared scores/skips/objectives, and a 54-slot backpack. Unassigned players are assigned to the smallest team at start. Teammates spawn together.
- **Skip Tokens:** The Barrier stack in your hotbar shows how many skips remain. Hold it and right-click to reroll, or pick a square in Bingo. Team members see the same shared balance. Tokens can move around your inventory and stay bound to you during the round.
- **Objectives:** item possession (confirmed after inventory transactions and once per second), killing blows, newly completed advancements, generated structure bounding boxes, and the player's current 3D biome. No NMS is used.

With a hidden leaderboard, shared tab rows show `?`, only the viewer's own target appears in the private tab footer, and score messages are hidden. Everyone receives the last-to-first find reveal at the end. Score ties use the order in which that score was reached. The recap has titles, sounds, a temporary display-entity podium, harmless winner fireworks, written timeline books, and durable YAML results.

## Data and configuration

`plugins/RushHour/config.yml` stores rules. `items.yml` contains a Paper 26.3 Material snapshot with categories, tiers, and dimension routes; non-items are filtered at runtime. `objectives.yml` contains the other objective definitions. Edit these data files with the server stopped; restart to reload them. Unknown registry keys are skipped with a warning. Newly introduced items without metadata are visible under `unreviewed` and disabled until reviewed or explicitly toggled.

Routes are alternatives: `[OVERWORLD, NETHER]` means either dimension works. `[OVERWORLD+NETHER]` requires both. A conservative `OVERWORLD+NETHER+END` route is used for items whose isolated-dimension acquisition has not been confirmed; these remain available in all-dimension rounds. The generated baseline needs a manual loot/recipe review on every Minecraft update. Dimension filtering does not prove that a particular structure or biome exists within a custom world's border. Use the catalog to remove unavailable targets for custom generators, superflat worlds, disabled structure generation, or unusual datapacks.

Bundled presets are `easy`, `no-end`, and `wood-age`. Whitelist mode admits only explicitly enabled objectives. Explicit per-target overrides take priority over category/default settings. Weights select a target type, then a difficulty tier, then a target, so a large item catalog does not drown out the mob weight. Set a type/tier weight to 0 to disable it.

Chaos replaces each block-drop stack and each mob-drop stack with one random enabled item. Disabling the ITEM target weight does not remove catalog items from chaos. Death penalties are `NONE`, `POINT`, or `TARGET`; a target penalty rerolls the active Classic/Race target and does not spend a skip. Bingo keeps the card on death. Late players can spectate or play with a configurable catch-up score capped at the leader; a late teammate joins an existing team if it has capacity. Idle elimination is optional.

## Worlds and recovery

Fresh worlds use a random or fixed seed. Only selected dimensions are created. Spawn chunks are prepared asynchronously before the countdown, players are spread safely, and portals stay in that round's worlds. A last-resort obsidian landing platform is placed if no safe spawn can be found. The lobby stays in the configured persistent world. Existing world PvP/keep-inventory rules are restored after the round.

Only temporary directories created by Rush Hour, with an ownership marker and a checked `rushhour_<uuid>_<dimension>` name under the server's world container, are eligible for deletion. Occupied worlds are retained. Player recovery files live in `plugins/RushHour/recovery/` and are removed after restoration. Offline players recover when they next join. After an abrupt process crash, temporary worlds may remain on disk; retain the recovery folder and let players reconnect before removing abandoned temporary worlds. A crashed round is cancelled rather than resumed.

## Development and verification

Run `./gradlew clean build`. The test suite contains server-independent rule tests plus MockBukkit integration tests. MockBukkit does not enumerate vanilla advancements or asynchronously generate terrain, so the fixture provides an empty advancement set and immediate mock chunk loads. Actual advancement progress, portal travel, generation, structure bounds, and multiplayer timing need a Paper playtest.

On Windows, `powershell -File tools/dev-server.ps1` builds the plugin, downloads a stable Paper 26.3 server, and prepares a localhost-only server under ignored `run/`. Read Minecraft's EULA and set `run/eula.txt` to `eula=true` if you agree, then use `tools/dev-server.ps1 -Start`. Use Java 25 in `JAVA_HOME` and `PATH`. On other platforms, use a normal Paper installation and copy the built JAR into `plugins/`.

See [the playtest checklist](docs/PLAYTEST.md). Main-thread scheduler use is deliberate; **Folia is not supported** in this version.

## Build and publishing

GitHub Actions runs the build/tests on pushes and pull requests and uploads the plugin JAR and test report. Publication to Modrinth/Hangar uses the separate, manually dispatched **Publish plugin** workflow. First create the platform project pages, check the Rush Hour name, and create Hangar's `Alpha` channel. Configure:

| Platform | Repository variable | Repository secret |
| --- | --- | --- |
| Modrinth | `MODRINTH_PROJECT_ID` | `MODRINTH_TOKEN` |
| Hangar | `HANGAR_PROJECT` | `HANGAR_API_TOKEN` with `create_version` |

Update `version` and `CHANGELOG.md`, playtest the exact build, then run the workflow on that revision. Optionally protect the `release` GitHub environment. Ordinary pushes do not publish platform versions. A platform name or project has not been reserved by this source implementation.

Built against the [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/), [Paper command lifecycle](https://docs.papermc.io/paper/dev/command-api/basics/registration/), [Hangar publisher](https://docs.papermc.io/misc/hangar-publishing/), and [Modrinth Minotaur](https://github.com/modrinth/minotaur). Licensed under MIT.
