# Paper 26.3 playtest

Use a disposable server with two or more vanilla clients. Automated tests are complementary to these checks.

- [ ] Start with ordinary inventories, experience, effects, a bed spawn, and advancement progress; confirm restoration after recap, cancel, restart, and reconnect.
- [ ] Edit every setup page; verify invalid numbers cannot be saved, chat prompts time out, and a stale menu cannot change an active round.
- [ ] Catalog tabs/search/category toggles/presets/whitelist work; inspect the enabled pool for all-dimension, Overworld-only, Nether-only, and End-only rounds.
- [ ] Collect targets by pickup, shift-craft, smelting, trade, buckets, offhand and chest transfers; cancel a transaction and verify it cannot award an item still outside the inventory.
- [ ] Try simultaneous finds in Race, a shared team target, shared skip tokens, and a backpack withdrawal.
- [ ] Complete bingo rows, columns and diagonals, skip a square, and finish the whole card.
- [ ] Test newly earned advancements; ensure pre-round criteria are reset during the game and restored afterwards.
- [ ] Confirm mob killing blows, 3D cave biomes, and structure bounding boxes, with excluded dimensions and optional bosses.
- [ ] Pause with an inventory open; try movement, combat, drops, crafting, backpack use and item pickup. Confirm resume and add-time behavior at the deadline.
- [ ] Keep a hidden round open on both clients: tab rows/footer, chat, `/rush top`, menus, death and reconnect must not leak scores or another team's target.
- [ ] Test Nether/End portal entry and exit in fresh and existing worlds; disabled dimensions stay blocked. Check death/bed/anchor respawns.
- [ ] Verify chaos drops and all death penalties; join late in both modes; trigger idle spectating and follow a player.
- [ ] Finish a hidden and visible round; verify reveal order, score timing, fast/skip controls, books, summary, harmless fireworks and podium cleanup.
- [ ] Start/cancel multiple fresh seeded rounds, including cancelling during chunk preparation; confirm the lobby and unrelated worlds are retained.
- [ ] Stop/restart while a player is offline; confirm recovery files restore their original state on return.

Before public release, review all item routes against 26.3 loot tables and recipes, review rare objective tiers, check Modrinth/Hangar name availability, and configure the publishing project IDs/tokens.
