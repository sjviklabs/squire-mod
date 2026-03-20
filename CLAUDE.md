# Squire Mod — Project Instructions

## What This Is
NeoForge 1.21.1 mod (Minecraft Java Edition). A loyal companion entity that follows, fights, mines, carries gear, and levels up. Target server: StoneBlock 4 + MineColonies on LXC 111.

## Build & Run

```bash
# Build (produces build/libs/squire-*.jar)
./gradlew build

# Run dev client
./gradlew runClient

# Run dev server (headless)
./gradlew runServer

# Data generation
./gradlew runData
```

## Architecture

### Source Layout
- `entity/` — SquireEntity (extends TamableAnimal, implements RangedAttackMob)
- `ai/statemachine/` — SquireAI tick-rate state machine, SquireAIState enum
- `ai/handler/` — CombatHandler, MiningHandler, FollowHandler, ProgressionHandler, PlacingHandler, EatingHandler
- `command/` — SquireCommand (all /squire subcommands)
- `config/` — SquireConfig (NeoForge ModConfigSpec)
- `util/` — SquireAbilities (level-gated checks), SquireChunkLoader, SquireEquipmentHelper
- `inventory/` — SquireInventory, SquireEquipmentContainer
- `init/` — ModEntities, ModItems registry
- `client/` — SquireRenderer, SquireModel, health bar overlay

### AI State Machine
Priority-based transitions. Higher priority (lower number) preempts lower. States: IDLE, FOLLOWING_OWNER, COMBAT_APPROACH, COMBAT_RANGED, MINING_APPROACH, MINING_BREAK, PLACING_APPROACH, PLACING_PLACE, EATING.

### Key Patterns
- Handlers are stateful (one instance per squire), not singletons
- Config values read via `SquireConfig.<field>.get()` — always hot-reloadable
- Entity synced data for client-visible state (mode, level, sprinting)
- NBT save/load for persistence (inventory, progression, mode)
- Activity log for in-game debugging (`/squire log`)

## Commands
All under `/squire`. Most require op level 2.
- `info` — status display (no op required)
- `mine <pos>` — single block mining
- `place <pos> <block>` — place from inventory
- `clear <from> <to>` — preview area clear (confirm/cancel workflow)
- `mode <follow|stay|guard>` — set behavior mode
- `xp <amount>` — grant XP (debug)
- `log [count]` — show activity log

## Conventions
- **Commits**: conventional commits (`feat:`, `fix:`, `docs:`, `refactor:`)
- **Versioning**: semver in `gradle.properties` → `mod_version`. Tag format: `v0.2.0`
- **Release**: push a tag → GitHub Actions builds + creates release with jar
- **Deploy**: manual dispatch workflow or `scp` to LXC 111
- **Config**: all tunable values in SquireConfig, never hardcode gameplay numbers
- **No Docker**: server runs on bare LXC, systemd-managed

## Deploy to Server

```bash
# Build + deploy (uses SSH config alias "minecraft" for LXC 111)
./deploy.sh            # deploy only, no restart
./deploy.sh --restart  # deploy and restart server
```

GitHub Actions deploy workflow also available (requires self-hosted runner on LAN).

## Version History
- v0.2.0 — Abilities, ranged combat, guard mode, area clearing, chunk loading
- v0.1.0 — Initial scaffold, entity, badge, basic AI
