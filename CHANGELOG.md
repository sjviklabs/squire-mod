# Changelog

All notable changes to the Squire Mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
