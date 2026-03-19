# Research: NeoForge Modding Toolchain

Research conducted 2026-03-18 for Squire Mod Phase 1 planning.

## IDE: IntelliJ IDEA Community

**Winner by consensus.** NeoForge officially supports IntelliJ and Eclipse. GitHub discussion poll confirmed IntelliJ dominant among mod developers.

**Essential plugin:** Minecraft Development Plugin (mcdev) — NeoForge templates, registry inspections, mixin support.

**Alternatives:**
- Eclipse — lighter on RAM, but no multi-runtime classpath support
- VS Code — works but needs manual `gradlew genVSCodeRuns`, no mcdev equivalent

## JDK: Java 21

Required by NeoForge 1.21.x. Any distribution works (Temurin, Microsoft OpenJDK).

**Hot-reload:** JetBrains Runtime (JBR) has built-in DCEVM. Run with `-XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=fatjar` to hot-swap class schema changes in debug mode without restart.

Resource changes (JSON, textures, lang): F3+T in-game to reload resource packs.

## Build System: ModDevGradle

NeoForge offers two Gradle plugins:

| Plugin | Best For |
|--------|---------|
| **ModDevGradle (MDG)** | Single-version mods, simpler builds. Our choice. |
| NeoGradle | Multi-version projects. More complex. |

**Getting started:**
1. NeoForge Mod Generator (neoforged.net/mod-generator/) — or use existing MDK
2. Open in IntelliJ, let Gradle sync (first run decompiles MC — can take a while)
3. Run configs auto-generated: `gradlew runClient` / `gradlew runServer`

**Key build files:**
- `build.gradle` — plugin config, dependencies, property injection
- `gradle.properties` — mod ID, version, NeoForge version, group
- `settings.gradle` — project name, plugin repositories

## Project Structure

Standard NeoForge 1.21.x layout:

```
squire-mod/
  build.gradle
  settings.gradle
  gradle.properties
  src/
    main/
      java/com/sjviklabs/squire/
        SquireMod.java              # @Mod entry point
        entity/                     # Entity classes
        ai/                         # AI goals
        inventory/                  # Menu, screen, inventory
        item/                       # Items (badge)
        init/                       # DeferredRegister classes
        config/                     # ModConfigSpec
        command/                    # Admin commands
        network/                    # Custom payloads
        client/                     # Client-only (renderers, screens)
        util/                       # Helpers
      resources/
        META-INF/neoforge.mods.toml
        assets/squire/
          blockstates/
          models/item/
          textures/entity/
          textures/item/
          lang/en_us.json
        data/squire/
          recipes/
          tags/
    generated/                      # Datagen output
```

## Registration System

`DeferredRegister` pattern with specialized variants:
- `DeferredRegister.Blocks` / `.Items` / `.Entities`
- All registers call `.register(modEventBus)` in mod constructor

## Key NeoForge APIs for Entity Mods

### Entity Hierarchy
`Entity` → `LivingEntity` → `Mob` → `PathfinderMob` → `Animal` → `TamableAnimal`

### AI Goals
- `goalSelector.addGoal(priority, goal)` / `targetSelector.addGoal(priority, goal)`
- Built-in: `MeleeAttackGoal`, `FollowOwnerGoal`, `LookAtPlayerGoal`, `FloatGoal`, etc.
- 1.21 changes: `FollowOwnerGoal` no longer takes fly boolean

### Capabilities (NeoForge)
- `Capabilities.ItemHandler.ENTITY` — entity inventory
- Register via `RegisterCapabilitiesEvent`

### Networking
- `CustomPacketPayload` + `StreamCodec` for serialization
- Register via `RegisterPayloadHandlersEvent`
- Send via `PacketDistributor`
- Limits: 1 MiB server→client, 32 KiB client→server

### Entity Data Sync
- `SynchedEntityData` — auto-sync parameters to tracking clients
- `CompoundTag` via `addAdditionalSaveData()` / `readAdditionalSaveData()` for persistence

### Events
- `EntityJoinLevelEvent`, `EntityLeaveLevelEvent`
- `LivingDeathEvent`, `LivingDamageEvent`, `LivingHurtEvent`
- `RegisterCommandsEvent` for commands

## Learning Resources

### Tier 1 (Official)
- NeoForged Documentation (docs.neoforged.net)
- NeoForge Discord
- NeoForge GitHub + MDK repos

### Tier 2 (Tutorials)
- **Kaupenjoe** — most comprehensive NeoForge 1.21.x series, 60+ lectures, covers custom entities
- **McJty** — veteran modder, NeoForge porting tutorials
- DeepWiki NeoForge documentation

### Tier 3 (Reference)
- NeoForge Javadocs (community-maintained)
- Decompiled MC source (from MDK setup)

**Warning:** Anything referencing `net.minecraftforge` packages is old Forge, not NeoForge. NeoForge uses `net.neoforged`. Split happened late 2023.
