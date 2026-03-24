# Squire Mod

A true player-like companion for Minecraft. Your squire walks, runs, swims, fights with weapons, wears armor, eats food, levels up, and follows your lead. No teleporting. No cheating. Just a loyal companion that does things the hard way.

**Platform:** NeoForge 1.21.1 | **Java:** 21 | **License:** MIT

## Features

### Core

| Feature | Description |
|---------|-------------|
| **Movement** | Walks, runs, sprints, swims. Never teleports. |
| **Combat** | Melee + ranged (bows). Fights what you fight, fights what fights you. Auto-targets hostiles near you. |
| **Auto-equip** | Picks up and equips the best armor, weapons, shields from inventory. |
| **Survival** | Eats food when health drops below threshold. |
| **Modes** | Follow / Guard / Stay. Shift+right-click to cycle. |
| **Inventory** | 27-slot general + armor/offhand. Shift+right-click to open. |
| **Death** | Drops inventory + badge. Death message with coordinates. Resummon with the badge. |
| **Recall** | Right-click your Squire's Crest to call the squire back instantly. |

### Progression (30 Levels)

XP from combat kills and block mining. Quadratic scaling curve.

| Level | Ability Unlocked |
|-------|-----------------|
| 5 | Fire Resistance |
| 10 | Ranged Combat (bows) |
| 15 | Shield Blocking |
| 20 | Thorns Reflection |
| 25 | Lifesteal |
| 30 | Undying (revive on death) |

Each level also grants bonus health, damage, and movement speed.

### Building & Mining

| Feature | Description |
|---------|-------------|
| **Single mine** | `/squire mine <pos>` — break one block |
| **Area clear** | `/squire clear <from> <to>` — preview with particles, confirm or cancel |
| **Block place** | `/squire place <pos> <block>` — place from inventory |
| **Chunk loading** | Auto-loads chunks during area clear while owner is online |

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/squire info` | Show squire status (health, level, mode, position) |
| `/squire mode <follow\|guard\|stay>` | Set behavior mode |
| `/squire mine <x y z>` | Mine a single block |
| `/squire place <x y z> <block>` | Place a block from inventory |
| `/squire clear <x1 y1 z1> <x2 y2 z2>` | Preview area clear (confirm/cancel after) |
| `/squire log [count]` | View recent activity log |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/squire list` | List all squires on the server |
| `/squire kill` | Kill your squire |
| `/squire xp <amount>` | Grant XP (debug/testing) |
| `/squire limit <n>` | Set max squires per player |

## Items

| Item | How to Get | Use |
|------|-----------|-----|
| **Squire's Crest** | Craft | Right-click to summon/recall your squire |
| **Squire's Lance** | Craft | Area selection tool for clear commands |

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1
2. Download the latest release from [GitHub Releases](https://github.com/sjviklabs/squire-mod/releases)
3. Drop the JAR in your `mods/` folder
4. Launch the game

## Configuration

All values tunable via `squire-common.toml` (30+ settings):

| Category | Examples |
|----------|---------|
| **Limits** | maxSquiresPerPlayer |
| **Movement** | followStartDistance, followStopDistance, sprintDistance |
| **Combat** | aggroRange, rangedOptimalRange, combatLeashDistance |
| **Survival** | eatHealthThreshold, baseHealth, naturalRegenRate |
| **Mining** | mineReach, breakSpeedMultiplier, maxClearVolume |
| **Progression** | xpPerKill, xpPerBlock, xpPerLevel, maxLevel |
| **Abilities** | Individual unlock levels for all 6 abilities |
| **Debug** | godMode, activityLogging |

The squire uses `MobCategory.MISC` and does not affect mob caps.

## Development

```bash
# Build the mod
./gradlew build

# Run dev client
./gradlew runClient

# Run dev server
./gradlew runServer

# Deploy to servers + local client
./deploy.sh --restart
```

**IDE:** IntelliJ IDEA with [Minecraft Development Plugin](https://mcdev.io/) | **JDK:** Java 21

## Roadmap

- [x] **Phase 1** — Walk, fight, equip, follow
- [x] **Phase 2** — Mining, placing, progression, abilities, state machine
- [ ] **Phase 3** — Container interaction, farming, task queue
- [ ] **Phase 4** — Autonomous multi-step tasks, async pathfinding

See [docs/PHASE-ROADMAP.md](docs/PHASE-ROADMAP.md) for details.

## Links

- [NeoForged Documentation](https://docs.neoforged.net/)
- [NeoForge Discord](https://discord.neoforged.net/)

## License

MIT License. See [LICENSE](LICENSE).
