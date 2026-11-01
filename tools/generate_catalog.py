"""Regenerate the bundled review baseline from javap's Material constants.

Usage: py tools/generate_catalog.py path/to/materials.txt
Unknown acquisition routes are deliberately restricted to all three dimensions.
Review this baseline against vanilla loot/recipes before each Minecraft update.
"""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[1]
names = re.findall(r'public static final org\.bukkit\.Material ([A-Z0-9_]+);', pathlib.Path(sys.argv[1]).read_text(encoding='utf-8-sig'))
blocked = set('BEDROCK BARRIER LIGHT COMMAND_BLOCK CHAIN_COMMAND_BLOCK REPEATING_COMMAND_BLOCK COMMAND_BLOCK_MINECART STRUCTURE_BLOCK STRUCTURE_VOID JIGSAW DEBUG_STICK KNOWLEDGE_BOOK REINFORCED_DEEPSLATE BUDDING_AMETHYST PETRIFIED_OAK_SLAB PLAYER_HEAD END_PORTAL_FRAME SPAWNER TRIAL_SPAWNER VAULT TEST_BLOCK TEST_INSTANCE_BLOCK SUSPICIOUS_SAND SUSPICIOUS_GRAVEL FARMLAND DIRT_PATH FROGSPAWN INFESTED_STONE INFESTED_COBBLESTONE INFESTED_STONE_BRICKS INFESTED_MOSSY_STONE_BRICKS INFESTED_CRACKED_STONE_BRICKS INFESTED_CHISELED_STONE_BRICKS INFESTED_DEEPSLATE'.split())
blocked.add('CHORUS_PLANT') # Breaks into chorus fruit, even with Silk Touch.
easy = set('STICK CRAFTING_TABLE FURNACE CHEST BARREL TORCH CHARCOAL COAL STONE COBBLESTONE COBBLED_DEEPSLATE DIRT COARSE_DIRT ROOTED_DIRT GRASS_BLOCK SAND RED_SAND GRAVEL FLINT CLAY CLAY_BALL BRICK BRICKS GLASS GLASS_PANE BOWL LADDER LEVER STONE_BUTTON STONE_PRESSURE_PLATE COBBLESTONE_SLAB COBBLESTONE_STAIRS COBBLESTONE_WALL STONE_SLAB STONE_STAIRS ANDESITE DIORITE GRANITE TUFF CALCITE MOSS_BLOCK MOSS_CARPET VINE WHEAT WHEAT_SEEDS BREAD APPLE POTATO CARROT BEETROOT BEETROOT_SEEDS BEETROOT_SOUP MELON MELON_SLICE MELON_SEEDS PUMPKIN PUMPKIN_SEEDS SUGAR SUGAR_CANE PAPER BOOK EGG FEATHER LEATHER BEEF COOKED_BEEF PORKCHOP COOKED_PORKCHOP CHICKEN COOKED_CHICKEN MUTTON COOKED_MUTTON RABBIT COOKED_RABBIT RABBIT_HIDE ROTTEN_FLESH BONE BONE_MEAL STRING SPIDER_EYE GUNPOWDER INK_SAC COD SALMON COOKED_COD COOKED_SALMON KELP DRIED_KELP SEAGRASS DANDELION POPPY BLUE_ORCHID ALLIUM AZURE_BLUET OXEYE_DAISY CORNFLOWER LILY_OF_THE_VALLEY SUNFLOWER LILAC ROSE_BUSH PEONY BROWN_MUSHROOM RED_MUSHROOM MUSHROOM_STEW SWEET_BERRIES GLOW_BERRIES BAMBOO CACTUS DEAD_BUSH FERN LARGE_FERN SHORT_GRASS TALL_GRASS SNOW SNOW_BLOCK SNOWBALL ICE PACKED_ICE BLUE_ICE HAY_BLOCK PUMPKIN_PIE FLOWER_POT'.split())
ow = set('IRON_INGOT IRON_NUGGET RAW_IRON IRON_BLOCK IRON_ORE DEEPSLATE_IRON_ORE COPPER_INGOT RAW_COPPER COPPER_BLOCK COPPER_ORE DEEPSLATE_COPPER_ORE GOLD_ORE DEEPSLATE_GOLD_ORE RAW_GOLD EMERALD EMERALD_BLOCK EMERALD_ORE DEEPSLATE_EMERALD_ORE DIAMOND DIAMOND_BLOCK DIAMOND_ORE DEEPSLATE_DIAMOND_ORE LAPIS_LAZULI LAPIS_BLOCK LAPIS_ORE DEEPSLATE_LAPIS_ORE REDSTONE REDSTONE_BLOCK REDSTONE_ORE DEEPSLATE_REDSTONE_ORE AMETHYST_SHARD AMETHYST_BLOCK AMETHYST_CLUSTER SMALL_AMETHYST_BUD MEDIUM_AMETHYST_BUD LARGE_AMETHYST_BUD HONEYCOMB HONEY_BLOCK HONEYCOMB_BLOCK SLIME_BALL SLIME_BLOCK RABBIT_FOOT TURTLE_SCUTE TURTLE_HELMET ARMADILLO_SCUTE WOLF_ARMOR NAUTILUS_SHELL HEART_OF_THE_SEA PRISMARINE_SHARD PRISMARINE_CRYSTALS PRISMARINE PRISMARINE_BRICKS DARK_PRISMARINE SEA_LANTERN SPONGE WET_SPONGE TRIDENT TOTEM_OF_UNDYING ENCHANTED_GOLDEN_APPLE ENCHANTED_BOOK NAME_TAG SADDLE COBWEB RAIL POWERED_RAIL DETECTOR_RAIL ACTIVATOR_RAIL MINECART CHEST_MINECART HOPPER_MINECART TNT TNT_MINECART HOPPER PISTON STICKY_PISTON DISPENSER DROPPER NOTE_BLOCK JUKEBOX CAULDRON ANVIL CHIPPED_ANVIL DAMAGED_ANVIL SMITHING_TABLE LOOM STONECUTTER BLAST_FURNACE SMOKER GRINDSTONE LECTERN FLETCHING_TABLE CARTOGRAPHY_TABLE TARGET COMPOSTER BELL LANTERN CAMPFIRE CHAIN IRON_BARS BUCKET WATER_BUCKET LAVA_BUCKET MILK_BUCKET POWDER_SNOW_BUCKET COD_BUCKET SALMON_BUCKET TROPICAL_FISH_BUCKET PUFFERFISH_BUCKET AXOLOTL_BUCKET TADPOLE_BUCKET SHEARS FLINT_AND_STEEL FISHING_ROD BRUSH SPYGLASS COMPASS CLOCK MAP FILLED_MAP SHIELD BOW CROSSBOW ARROW TIPPED_ARROW FIREWORK_ROCKET FIREWORK_STAR FIRE_CHARGE BUNDLE PAINTING ITEM_FRAME GLOW_ITEM_FRAME GLOW_INK_SAC GLOW_LICHEN BOOKSHELF CHISELED_BOOKSHELF LEAD FROGLIGHT OCHRE_FROGLIGHT VERDANT_FROGLIGHT PEARLESCENT_FROGLIGHT SCULK SCULK_VEIN SCULK_SENSOR CALIBRATED_SCULK_SENSOR SCULK_CATALYST SCULK_SHRIEKER ECHO_SHARD DISC_FRAGMENT_5 RECOVERY_COMPASS TRIAL_KEY OMINOUS_TRIAL_KEY OMINOUS_BOTTLE WIND_CHARGE BREEZE_ROD HEAVY_CORE MACE SNIFFER_EGG TORCHFLOWER_SEEDS TORCHFLOWER PITCHER_POD PITCHER_PLANT RESIN_CLUMP RESIN_BLOCK RESIN_BRICK RESIN_BRICKS EYEBLOSSOM OPEN_EYEBLOSSOM CLOSED_EYEBLOSSOM'.split())
nether = set('NETHERRACK SOUL_SAND SOUL_SOIL BASALT SMOOTH_BASALT POLISHED_BASALT BLACKSTONE GILDED_BLACKSTONE GLOWSTONE GLOWSTONE_DUST QUARTZ NETHER_QUARTZ_ORE NETHER_GOLD_ORE NETHER_BRICK NETHER_BRICKS RED_NETHER_BRICKS NETHER_WART NETHER_WART_BLOCK WARPED_WART_BLOCK NETHER_SPROUTS TWISTING_VINES WEEPING_VINES SHROOMLIGHT MAGMA_BLOCK MAGMA_CREAM BLAZE_ROD BLAZE_POWDER GHAST_TEAR ANCIENT_DEBRIS NETHERITE_SCRAP CRYING_OBSIDIAN SOUL_TORCH SOUL_LANTERN SOUL_CAMPFIRE NETHERITE_UPGRADE_SMITHING_TEMPLATE SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE RIB_ARMOR_TRIM_SMITHING_TEMPLATE WITHER_SKELETON_SKULL'.split())
end = set('END_STONE END_STONE_BRICKS CHORUS_FRUIT POPPED_CHORUS_FRUIT CHORUS_FLOWER PURPUR_BLOCK PURPUR_PILLAR PURPUR_SLAB PURPUR_STAIRS END_ROD SHULKER_SHELL ELYTRA DRAGON_HEAD DRAGON_EGG DRAGON_BREATH SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE'.split())
woods = 'OAK SPRUCE BIRCH JUNGLE ACACIA DARK_OAK MANGROVE CHERRY PALE_OAK'.split()
colors = 'WHITE ORANGE MAGENTA LIGHT_BLUE YELLOW LIME PINK GRAY LIGHT_GRAY CYAN PURPLE BLUE BROWN GREEN RED BLACK'.split()
hard = set('DIAMOND_BLOCK NETHERITE_SCRAP ANCIENT_DEBRIS BLAZE_ROD GHAST_TEAR TRIDENT TOTEM_OF_UNDYING HEART_OF_THE_SEA WET_SPONGE SPONGE ECHO_SHARD RECOVERY_COMPASS HEAVY_CORE MACE ELYTRA SHULKER_SHELL DRAGON_HEAD DRAGON_BREATH'.split())
insane = set('DRAGON_EGG NETHER_STAR BEACON NETHERITE_BLOCK'.split())
lines = ['# Minecraft 26.3 / Paper build 230 Material snapshot.', '# routes are alternative acquisition paths; + means all dimensions on that path are required.', '# A conservative all-dimension route is used where the exact path needs review.', '# Creative-only items are visible but disabled; a host can explicitly override them.', 'items:']
count = 0
for name in sorted(set(names)):
    if name.startswith('LEGACY_'):
        continue
    category, tier, routes = 'misc', 'MEDIUM', ['OVERWORLD+NETHER+END']
    enabled = name not in blocked and not name.endswith('_SPAWN_EGG')
    if name in easy:
        category, tier, routes = 'nature', 'EASY', ['OVERWORLD']
    if name in ow:
        category, routes = 'overworld', ['OVERWORLD']
    if any(name.startswith(w + '_') or name.startswith('STRIPPED_' + w + '_') for w in woods) or name.startswith('BAMBOO_') or name.startswith('MANGROVE_'):
        category, tier, routes = 'wood', 'EASY', ['OVERWORLD']
    if name.startswith(('CRIMSON_', 'WARPED_', 'STRIPPED_CRIMSON_', 'STRIPPED_WARPED_')) or name in nether or name.startswith(('BLACKSTONE_', 'POLISHED_BLACKSTONE_', 'NETHER_BRICK_', 'RED_NETHER_BRICK_', 'QUARTZ_', 'SMOOTH_QUARTZ_', 'CHISELED_QUARTZ_')):
        category, tier, routes = 'nether', 'MEDIUM', ['NETHER']
    if name in end or name.startswith('END_STONE_'):
        category, tier, routes = 'end', 'HARD', ['END']
    if any(name.startswith(c + '_') for c in colors):
        category, routes = 'colored', ['OVERWORLD']
        if name.endswith('_SHULKER_BOX'):
            routes = ['OVERWORLD+END']
    if name.endswith(('_STAINED_GLASS', '_STAINED_GLASS_PANE', '_TERRACOTTA', '_CONCRETE', '_CONCRETE_POWDER', '_WOOL', '_CARPET', '_BED', '_BANNER', '_CANDLE')):
        category, routes = 'colored', ['OVERWORLD']
    if name.endswith(('_PICKAXE', '_AXE', '_SHOVEL', '_HOE', '_SWORD', '_HELMET', '_CHESTPLATE', '_LEGGINGS', '_BOOTS')):
        category, routes = 'equipment', ['OVERWORLD']
        if name.startswith('WOODEN_') or name.startswith('STONE_'):
            tier = 'EASY'
        if name.startswith('NETHERITE_'):
            tier, routes = 'INSANE', ['OVERWORLD+NETHER']
    if name in {'GOLD_INGOT', 'GOLD_NUGGET', 'GOLD_BLOCK', 'OBSIDIAN', 'ENDER_PEARL', 'STRING', 'LEATHER', 'BONE', 'BONE_MEAL', 'GRAVEL', 'BROWN_MUSHROOM', 'RED_MUSHROOM', 'MUSHROOM_STEW'}:
        routes = ['OVERWORLD', 'NETHER']
    if name in {'STICK', 'CRAFTING_TABLE', 'CHEST', 'BOWL', 'LADDER', 'BARREL', 'WOODEN_PICKAXE', 'WOODEN_AXE', 'WOODEN_SHOVEL', 'WOODEN_HOE', 'WOODEN_SWORD'}:
        routes = ['OVERWORLD', 'NETHER']
    if name in {'IRON_INGOT', 'GOLD_INGOT', 'DIAMOND', 'IRON_PICKAXE', 'DIAMOND_PICKAXE', 'IRON_SWORD', 'DIAMOND_SWORD', 'IRON_HELMET', 'IRON_CHESTPLATE', 'IRON_LEGGINGS', 'IRON_BOOTS', 'DIAMOND_HELMET', 'DIAMOND_CHESTPLATE', 'DIAMOND_LEGGINGS', 'DIAMOND_BOOTS', 'SADDLE'}:
        routes += ['END']
    if name in {'ENDER_EYE', 'BREWING_STAND', 'ENCHANTING_TABLE', 'END_CRYSTAL', 'BEACON', 'NETHER_STAR', 'CONDUIT', 'RESPAWN_ANCHOR', 'OBSERVER', 'COMPARATOR', 'DAYLIGHT_DETECTOR', 'POTION', 'SPLASH_POTION', 'LINGERING_POTION', 'SPECTRAL_ARROW', 'TIPPED_ARROW', 'SHULKER_BOX'}:
        routes = ['OVERWORLD+NETHER+END']
    # Several blocks require silk touch; unobtainable infested blocks stay blocked.
    if name in hard or name.endswith(('_ORE', '_SMITHING_TEMPLATE')) or name.startswith('MUSIC_DISC_'):
        tier = 'HARD'
    if name in insane or name.startswith('NETHERITE_'):
        tier = 'INSANE'
    if name.endswith('_SPAWN_EGG') or name in blocked:
        category, tier, routes = 'unobtainable', 'INSANE', ['OVERWORLD', 'NETHER', 'END']
    lines += [f'  {name.lower()}:', f'    category: {category}', f'    tier: {tier}', f"    routes: [{', '.join(routes)}]", f'    enabled: {str(enabled).lower()}']
    count += 1
(root / 'src/main/resources/items.yml').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'Wrote {count} Material definitions')

targets = {k: {} for k in ['MOB', 'ADVANCEMENT', 'STRUCTURE', 'BIOME']}
def add(kind, keys, route='OVERWORLD', tier='MEDIUM', enabled=True):
    for key in keys.split():
        targets[kind][key] = dict(route=route, tier=tier, enabled=enabled)
add('MOB', 'chicken cow pig sheep zombie skeleton spider creeper drowned squid glow_squid cod salmon rabbit slime silverfish husk stray witch pillager', tier='EASY')
add('MOB', 'guardian elder_guardian evoker ravager breeze phantom cave_spider', tier='HARD')
add('MOB', 'blaze ghast magma_cube wither_skeleton piglin piglin_brute hoglin zoglin strider', 'NETHER', 'HARD')
add('MOB', 'enderman', 'OVERWORLD', 'MEDIUM')
add('MOB', 'shulker', 'END', 'HARD')
add('MOB', 'ender_dragon', 'END', 'INSANE', False)
add('MOB', 'wither', 'OVERWORLD+NETHER', 'INSANE', False)
add('MOB', 'warden illusioner giant zombie_horse', 'OVERWORLD', 'INSANE', False)
add('ADVANCEMENT', 'story/mine_stone story/upgrade_tools story/smelt_iron story/obtain_armor story/lava_bucket story/iron_tools story/deflect_arrow story/form_obsidian story/mine_diamond story/shiny_gear story/enchant_item husbandry/plant_seed husbandry/breed_an_animal husbandry/tame_an_animal adventure/kill_a_mob', tier='EASY')
add('ADVANCEMENT', 'story/enter_the_nether story/follow_ender_eye', 'OVERWORLD+NETHER', 'HARD')
add('ADVANCEMENT', 'nether/find_fortress nether/obtain_blaze_rod nether/obtain_ancient_debris nether/find_bastion nether/distract_piglin', 'NETHER', 'HARD')
add('ADVANCEMENT', 'end/enter_end_gateway end/find_end_city end/elytra', 'END', 'INSANE')
add('ADVANCEMENT', 'adventure/adventuring_time adventure/kill_all_mobs husbandry/balanced_diet husbandry/bred_all_animals', 'OVERWORLD+NETHER+END', 'INSANE', False)
add('STRUCTURE', 'village_plains village_desert village_savanna village_snowy village_taiga ruined_portal mineshaft shipwreck shipwreck_beached buried_treasure ocean_ruin_cold ocean_ruin_warm desert_pyramid jungle_pyramid igloo pillager_outpost swamp_hut', tier='MEDIUM')
add('STRUCTURE', 'monument mansion ancient_city trial_chambers stronghold trail_ruins', tier='HARD')
add('STRUCTURE', 'fortress bastion_remnant ruined_portal_nether', 'NETHER', 'HARD')
add('STRUCTURE', 'end_city', 'END', 'HARD')
add('BIOME', 'plains sunflower_plains forest flower_forest birch_forest old_growth_birch_forest dark_forest taiga snowy_taiga old_growth_pine_taiga old_growth_spruce_taiga savanna savanna_plateau windswept_savanna jungle sparse_jungle bamboo_jungle desert badlands eroded_badlands wooded_badlands meadow cherry_grove grove snowy_slopes frozen_peaks jagged_peaks stony_peaks windswept_hills windswept_gravelly_hills windswept_forest beach snowy_beach stony_shore river frozen_river swamp mangrove_swamp ocean deep_ocean cold_ocean deep_cold_ocean frozen_ocean deep_frozen_ocean lukewarm_ocean deep_lukewarm_ocean warm_ocean snowy_plains ice_spikes dripstone_caves lush_caves pale_garden', tier='MEDIUM')
add('BIOME', 'mushroom_fields deep_dark', tier='HARD')
add('BIOME', 'nether_wastes soul_sand_valley crimson_forest warped_forest basalt_deltas', 'NETHER', 'MEDIUM')
add('BIOME', 'the_end end_highlands end_midlands small_end_islands end_barrens', 'END', 'MEDIUM')
out = ['# Versioned vanilla objective definitions. Unknown keys are skipped with a warning.']
for kind, entries in targets.items():
    out.append(kind + ':')
    for key, spec in entries.items():
        out += [f'  {key}:', f'    tier: {spec["tier"]}', f'    routes: [{spec["route"]}]', f'    enabled: {str(spec["enabled"]).lower()}']
(root / 'src/main/resources/objectives.yml').write_text('\n'.join(out) + '\n', encoding='utf-8')

presets = root / 'src/main/resources/presets'
presets.mkdir(exist_ok=True)
(presets / 'easy.yml').write_text('catalog:\n  whitelist: true\n  overrides:\n' + ''.join(f'    ITEM:{x.lower()}: true\n' for x in sorted(easy | {'OAK_LOG', 'OAK_PLANKS', 'OAK_SAPLING', 'STONE_PICKAXE'})), encoding='utf-8')
(presets / 'wood-age.yml').write_text('catalog:\n  whitelist: true\n  overrides:\n' + ''.join(f'    ITEM:{x.lower()}: true\n' for x in sorted(set(names)) if any(x.startswith(w + '_') for w in woods) and not x.endswith('_WALL_SIGN')), encoding='utf-8')
(presets / 'no-end.yml').write_text('catalog:\n  whitelist: false\n  categories:\n    end: false\n  overrides:\n' + ''.join(f'    ITEM:{x.lower()}: false\n' for x in sorted(set(names)) if 'SHULKER' in x or x in {'END_CRYSTAL', 'DRAGON_BREATH', 'DRAGON_EGG', 'DRAGON_HEAD', 'ELYTRA'}) + ''.join(f'    {kind}:{key}: false\n' for kind, entries in targets.items() for key, spec in entries.items() if 'END' in spec['route']), encoding='utf-8')
