# Squire Mod

NeoForge 1.21.1 companion entity mod. The squire walks, runs, fights, mines, patrols on horseback, levels up, and carries gear. **It never teleports.**

- **Mod ID:** `squire` | **Package:** `com.sjviklabs.squire` | **Java:** 21+
- **Version:** See `gradle.properties` -> `mod_version` (currently v0.3.0)
- **Repo:** github.com/sjviklabs/squire-mod | **License:** MIT

## Build & Test

```bash
./gradlew build          # Produces build/libs/squire-*.jar
./gradlew runClient      # Dev client with hot reload
./gradlew runServer      # Headless dev server
./gradlew runData        # Generate recipes, loot tables, tags
```

Always use `./gradlew`, never system Gradle.

## Architecture Rules

These are non-negotiable. Violating any of these is a build-breaking mistake.

1. **NEVER teleport the squire.** Walking/sprinting only. Core design principle.
2. **NEVER use vanilla Goals.** All AI runs through `SquireAI` tick-rate state machine. No `GoalSelector`, no `Goal` subclasses.
3. **NEVER hardcode gameplay numbers.** Everything goes in `SquireConfig`.
4. **NEVER put behavior logic in `SquireEntity`.** New behavior = new handler + new states.
5. **NEVER use Fabric, Architectury, or multiloader patterns.** NeoForge-only.
6. **NEVER use deprecated 1.19/1.20 methods.** Check NeoForge 1.21.1 mappings. State uncertainty explicitly rather than guessing.
7. **NEVER add dependencies** beyond NeoForge and Minecraft without asking.
8. **NEVER store squire state in world data.** Entity NBT for persistence, `SynchedEntityData` for client sync.

## How to Add a New Behavior

1. Create `XxxHandler.java` in `ai/handler/` (stateful, one instance per entity, NOT singleton)
2. Add new states to `SquireAIState` enum (states are priority-layered: Survival > Combat > Mount > Follow > Work > Utility)
3. Register the handler in `SquireAI`
4. Add config values to `SquireConfig`
5. Add lang strings to `en_us.json`
6. Test: `./gradlew build` must pass, then `./gradlew runClient` for manual verification

## Coding Standards

- **Java 21** features OK: records, pattern matching, sealed classes
- **Package:** `com.sjviklabs.squire` — never change root package
- **Registration:** `DeferredRegister` via init classes (`ModItems`, `ModBlocks`, `ModEntities`, `ModBlockEntities`)
- **Config:** All gameplay numbers in `SquireConfig` via `ModConfigSpec`. IMPORTANT: default values MUST pass their own validation or you get infinite correction loops.
- **No magic numbers.** Named constants or config values only.
- **No `static` mutable state** outside registries
- **Lang strings** in `en_us.json` for all player-facing text
- **Textures:** 16x16 item icons, 128x64 armor layers. Grimdark Battlepack style.
- **Side safety:** Always check `this.level().isClientSide` before server-only logic in tick methods.

### File Naming

| Type | Pattern | Example |
|------|---------|---------|
| Entity | `[Name]Entity` | `SquireEntity` |
| Handler | `[Name]Handler` | `CombatHandler` |
| Item | `Squire[Name]Item` | `SquireShieldItem` |
| Block | `[Name]Block` | `SignpostBlock` |
| Screen/Menu | `[Name]Screen`, `[Name]Menu` | `SquireScreen` |
| Registry | `Mod[Type]s` | `ModItems`, `ModBlocks` |

### Git Conventions

- Conventional commits: `feat:`, `fix:`, `art:`, `docs:`, `refactor:`, `chore:`
- Trunk-based on `main`. Tags `v0.X.0` trigger release workflow.
- NEVER force-push main, commit secrets, or skip CI
- Co-author: `Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>`

## NeoForge 1.21.1 Pitfalls

These are common mistakes that waste hours. Read before writing code.

- `SynchedEntityData.defineId()` MUST use the owning entity class as first param. Wrong class = silent sync failure.
- Config default values must pass their own validation function. Bad defaults cause infinite correction cycles every 2 seconds.
- Use `RegisterSpawnPlacementsEvent`, not deprecated `SpawnPlacements.register()`.
- Network payloads: client-bound max 1 MiB, server-bound max 32 KiB.
- Entity spawn packets can't create different client-side classes in 1.21.1. Use `IEntityWithComplexSpawn` + custom payloads.
- Never share the same `ItemStack` instance across multiple inventory slots. Causes duplication on reload.
- NeoForge 1.21.1 is no longer actively maintained. Document any workarounds for known issues.

## Data Persistence Pattern

| Data Type | Mechanism | Example |
|-----------|-----------|---------|
| Survives restart | `addAdditionalSaveData` / `readAdditionalSaveData` (NBT) | inventory, level, mode, horse UUID, patrol route |
| Client display | `SynchedEntityData` | mode, level, appearance, sprinting |
| Gameplay tuning | `SquireConfig` (`squire-common.toml`) | all numeric values, toggles |

## Deploy

```bash
./deploy.sh                            # MineColonies + local client
./deploy.sh --restart                  # Same + restart server
./deploy.sh mcdimensions --restart     # MC Dimensions
./deploy.sh stoneblock4 --restart      # StoneBlock 4
./deploy.sh all --restart              # All servers
```

SSH alias `minecraft` resolves to the server host. See `deploy.sh` for full details.

## When Stuck

If a 1.21.1 API has changed from what you expect:
1. Check [NeoForge docs](https://docs.neoforged.net/)
2. Check [Parchment mappings](https://github.com/ParchmentMC/Parchment) (current: 2024.11.17)
3. State the uncertainty explicitly. Never silently use a deprecated method.

## Current Phase: 5 — Autonomous Intelligence

See @ROADMAP.md for the full Phase 5 plan with confidence gates.