# Squire Mod

A true player-like companion entity for Minecraft. Your squire walks, runs, swims, fights with weapons, wears armor, eats food, and follows your lead. No teleporting. No cheating. Just a loyal companion that does things the hard way.

**Platform:** NeoForge 1.21.1
**License:** MIT

## Features (Phase 1 — In Development)

- **Player-like movement** — walks, runs, sprints, swims. Never teleports.
- **Combat** — defends you with equipped weapons. Fights what you fight, fights what fights you.
- **Auto-equip** — picks up and equips the best armor, weapons, and shields from inventory.
- **Survival** — eats food when health is low.
- **Modes** — FOLLOW (default) and STAY (shift+right-click to toggle).
- **Recall** — right-click your badge to call the squire back.
- **Inventory** — 27-slot general inventory + armor/offhand slots. Right-click to open.
- **Death** — drops inventory + badge on death. Resummon with the badge.
- **Config** — every value is configurable for modpack authors.
- **Multiplayer** — one squire per player, admin commands for server management.

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1
2. Download the latest release from [CurseForge](#) or [Modrinth](#)
3. Place the JAR in your `mods/` folder
4. Launch the game

## Crafting

Craft a **Squire Badge** to summon your companion. Right-click with the badge to summon. Shift+right-click the squire to toggle FOLLOW/STAY.

## For Modpack Authors

All values are configurable via `squire-common.toml`:
- Max squires per player
- Follow/sprint/combat distances
- Health, regen, eat threshold
- Tick intervals for all AI systems

The squire implements `OwnableEntity` and uses `MobCategory.MISC` (does not affect mob caps).

## Roadmap

- **Phase 1:** Walk, fight, equip, follow (current)
- **Phase 2:** Block breaking/placing, tool selection, progression system
- **Phase 3:** Player commands ("mine here", "guard", "store items")
- **Phase 4:** Autonomous multi-step tasks, MineColonies-grade intelligence

See [docs/PHASE-ROADMAP.md](docs/PHASE-ROADMAP.md) for details.

## Development

```bash
# Build
./gradlew build

# Run client
./gradlew runClient

# Run server
./gradlew runServer
```

**IDE:** IntelliJ IDEA with [Minecraft Development Plugin](https://mcdev.io/)
**JDK:** Java 21

## Links

- [NeoForged Documentation](https://docs.neoforged.net/)
- [NeoForge Discord](https://discord.neoforged.net/)

## License

MIT License. See [LICENSE](LICENSE).
