# Changelog

All notable changes to the Squire Mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-03-27

### Added
- **Servant-to-Knight progression:** 5 tiers (Servant → Apprentice → Squire → Knight → Champion) gate feature access by level
- **Player capability:** Squire identity (XP, level, name, appearance) persists via player attachment — survives death, crest loss, server restarts
- **Feature toggles:** 14 config toggles to enable/disable any major system
- **Classic mode:** Config option to unlock all features at Lv1 (pre-v0.5.0 behavior)
- **Equal XP from all activities:** Farming, fishing, patrol, placing, and chopping now award XP alongside kills and mining
- **Milestone celebrations:** Totem particles + challenge sound at tier-up levels (5, 10, 20, 30); enchantment particles for normal levels
- **Death XP penalty:** Configurable % XP loss on death (default 10%)
- **Crosshair-targeted mining:** `/squire mine` with no args mines the block you're looking at
- **Ladder climbing:** Squire follows you up ladders and vines
- **Boat riding:** Squire boards boats as passenger when owner is in a boat
- **Unit test suite:** JUnit 5 tests for progression math, tier gates, AI states (35 tests)
- **NeoForge GameTest:** In-world tests for spawn behavior and tier verification
- **Danger avoidance handler:** Proactive flee from creepers and explosive threats
- **Combat retreat:** Ranged combat backing away when too close to target

### Changed
- **Crest persists after use:** No longer consumed on summon or dropped on death
- **Tier-gated combat:** Servants flee hostiles, defensive swings only if cornered. Melee at Lv5, ranged at Lv10
- **Mounted combat is endgame:** Horse riding at Lv25, mounted combat at Lv30 (was: available immediately)
- **Progression syncs to player:** Level-ups update player attachment in real-time

### Fixed
- Previously uncommitted Phase 5 code (farming, fishing, task queue, danger handler) now committed and build-verified

## [0.4.0] - 2026-03-25

### Added
- **Squire's Crest** — replaces Badge as summoning item; persists after use, includes area selection (shift+use)
- **Squire's Guidebook** — in-game reference for commands and progression
- **Visual progression** — tiered armor textures (Recruit/Veteran/Champion/Legend) based on squire level
- **Radial command menu** — R key opens 8-wedge wheel (Follow, Guard, Patrol, Stay, Store, Fetch, Mount, Inventory)
- **Farming handler** — `/squire farm <from> <to>` for till, plant, harvest loop
- **Fishing handler** — `/squire fish` for simulated idle fishing with loot rolls
- **Task queue** — `/squire queue add/list/clear` for chaining commands
- **MineColonies soft integration** — raid defense, warehouse access, colonist protection
- **Mod compatibility framework** — Waystones teleport detection, Farmer's Delight knife support, Jade/WTHIT overlay
- **Safety rails** — stuck detection, drowning protection, swim boost, fall/void rescue

### Changed
- Lance merged into Crest (lance item removed)
- Halberd sweep attack designed (code staged, item not yet registered)
- Bow combat improvements (shield stow, draw pose, weapon swap fixes)

## [0.3.0] - 2026-03-18

### Added
- **Signpost patrol system** — craftable signpost blocks form waypoint chains for patrol routes
- **Mounted combat** — MountHandler finds saddled horses, persists horse UUID, mounted movement and combat
- **Squire's Shield** — custom shield item, functionally identical to vanilla
- **Squire's Armor set** — 4-piece set (helmet, chestplate, leggings, boots), 18 defense, 1.5 toughness
- **Torch handler** — auto-places torches in dark areas (ability-gated)
- **Chat handler** — contextual flavor text for combat, kills, mining, eating, idle
- **Item handler** — pickup nearby items, junk filtering, auto-store when full
- **Chest handler** — store/fetch items from nearby containers

### Changed
- Entity dimensions to full Steve size (1.8h × 0.6w)
- Armor renders via HumanoidArmorLayer

## [0.2.0] - 2026-03-20

### Added
- **Ability system** — six level-gated abilities: fire resistance (Lv5), ranged combat (Lv10), shield block (Lv15), thorns (Lv20), lifesteal (Lv25), undying (Lv30)
- **Ranged combat** — squire uses bows at optimal distance, switches to melee when targets close in
- **Guard mode** — three-mode cycle (Follow/Guard/Stay) via right-click or `/squire mode` command
- **Area clearing** — `/squire clear <from> <to>` with particle preview, confirm/cancel workflow, top-down block queue
- **Chunk loading** — force-loads squire chunks during area clear while owner is online
- **Progression rework** — scaling XP curve (quadratic), max level 30, rebalanced per-level bonuses
- **Activity logging** — per-action logs viewable with `/squire log`
- **Health bar + level display** — custom nameplate renderer showing HP and level
- **Block placement** — PlacingHandler for placing blocks from inventory
- **Proactive aggro** — squire auto-targets hostile mobs near owner

### Changed
- State machine architecture — tick-rate priority system replacing vanilla Goal AI
- Handler extraction — CombatHandler, MiningHandler, FollowHandler, ProgressionHandler as separate classes
- Shift+right-click now opens inventory (was mode toggle)
- Follow distances rebalanced (start: 8, stop: 4 blocks)

### Fixed
- Entity ID vs UUID key for chunk loader maps (prevents chunk leak on world reload)
- Per-tick HashSet allocation in chunk loader (cached positions)
- Combat leash and disengage logic extracted to shared helpers
- Progression NBT level persistence and dedup sync
- Squire survivability improvements (godMode, hurt override)

## [0.1.0] - 2026-03-14

### Added
- Project scaffolding for NeoForge 1.21.1
- Basic SquireEntity with 27-slot inventory
- Squire Badge item for summoning
- Follow, fight, and item pickup AI goals
- Equipment slots in menu and death message with coordinates
- CI/CD pipeline (GitHub Actions)
