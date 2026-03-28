# Squire Mod v0.5.0 Rebuild — Design Spec

**Date:** 2026-03-27
**Goal:** Stabilize, restructure progression, add regression tests, ship a publishable mod.
**Scope:** Everything needed to go from current state to CurseForge/Modrinth release.
**Out of scope:** Autonomous multi-step AI (5c), multi-squire coordination (5d). Parked for v1.0+.

---

## 1. Progression Redesign: Servant to Knight

The squire no longer spawns combat-ready. It starts as a servant and earns its way to knighthood.

### Tier Progression

| Tier | Title | Levels | Unlocks |
|------|-------|--------|---------|
| 0 | Servant | 1-4 | Follow, carry items (9-slot satchel), eat to survive, chop wood, farm, fish. **Flees hostiles. Defensive swings only if cornered.** |
| 1 | Apprentice | 5-9 | Melee combat, auto-torch (Lv5), block placing, mining (Lv5), foot patrol (Lv8), 18-slot pack |
| 2 | Squire | 10-19 | Ranged combat/bows (Lv10), area clear (Lv10), chest deposit/fetch (Lv12), task queue (Lv10), shield blocking (Lv15), vein mining (Lv15), 27-slot knapsack |
| 3 | Knight | 20-29 | Halberd sweep (Lv20), fire resistance (Lv20), thorns (Lv22), lifesteal (Lv25), fortune sense (Lv25), mounted movement (Lv25), 36-slot war chest |
| 4 | Champion | 30 | Mounted combat, undying (revive), tireless, full champion armor visuals |

### XP Sources — All Equal

Every activity feeds progression at comparable rates. No activity is "better" for leveling.

| Activity | XP per action | Config key |
|----------|--------------|------------|
| Kill mob | 10 | xpPerKill |
| Mine block | 1 | xpPerBlock |
| Harvest crop | 2 | xpPerHarvest (NEW) |
| Catch fish | 3 | xpPerFish (NEW) |
| Complete patrol loop | 5 | xpPerPatrolLoop (NEW) |
| Complete queued task | 2 | xpPerQueuedTask (NEW) |
| Place block | 1 | xpPerPlace (NEW) |
| Chop wood (log block) | 1 | xpPerChop (NEW) |

Values are starting points. Tuned via config, testable.

### XP Curve

Keep quadratic: `Level = floor(sqrt(totalXP / xpPerLevel))`. Current `xpPerLevel = 100`.

With more XP sources, the curve may need flattening. Starting with existing values; adjust after playtesting. Config-driven so server operators can tune.

### Level-Up Celebration

| Level type | Effect |
|-----------|--------|
| Normal level-up | `PLAYER_LEVELUP` sound + chat line + full heal (existing) + enchantment table particles (NEW) |
| Tier milestone (5, 10, 20, 30) | Totem of Undying particle effect + `UI_TOAST_CHALLENGE_COMPLETE` sound + chat announcement visible to nearby players (NEW) |

### Death & Respawn

- Squire dies: drops inventory (gear) on ground. Progression (XP, level, name, appearance) is NOT lost.
- Player resummons with any crest. Same squire, same level, empty inventory.
- Optional: small XP penalty on death (10% of current level's XP). Config toggle: `deathXPPenalty` (default true), `deathXPPenaltyPercent` (default 0.10).

---

## 2. Crest Redesign

The crest is a tool, not an identity. The squire's soul lives on the player.

### Changes

- **Remove `shrink(1)` on summon.** Crest stays in inventory after use.
- **Remove crest drop on squire death.** Crest is not tied to the entity.
- **Crest is craftable and replaceable.** Lose it, craft another. Progression is safe.
- **No squire UUID stored on crest.** It's a generic summoning tool.

### Squire Identity: Player Capability (NeoForge Attachment)

Squire data attaches to the player entity via NeoForge's `AttachmentType` system.

**Stored on player:**
- Total XP, current level
- Custom name
- Appearance (male/female)
- Squire UUID (if alive in world — for lookup)

**Stored on entity (as today):**
- Inventory contents
- Current mode, patrol route, mount UUID
- AI state, handler state

**On squire death:** Entity drops inventory. Player attachment retains XP/level/name/appearance. Squire UUID cleared.

**On resummon:** New entity created, player attachment data applied (level, name, appearance). Empty inventory. Squire UUID updated on attachment.

**On player death:** Attachment persists (it's on the player, survives respawn).

---

## 3. Feature Toggles

Config-driven on/off for every major feature group. Handler skips registration when disabled. Commands return "feature disabled" message. Items/blocks still exist for world safety.

```toml
[features]
enableCombat = true
enableMining = true
enableProgression = true
enablePersonality = true
enableBackpack = true
enableCustomArmor = true
enableMounting = true
enablePatrol = true
enableChestInteraction = true
enableFarming = true
enableFishing = true
enableTaskQueue = true
enableDangerAvoidance = true
enableVisualProgression = true
```

Follow, survival, and eating are always on. Core to not dying.

---

## 4. Core Movement Parity

The squire should access anything the player can. Not level-gated — these are movement basics.

| Movement | Current | Action |
|----------|---------|--------|
| Ladder/vine climbing | Missing | Add climb logic in entity tick — detect climbable block, apply upward movement |
| Boat riding | Missing | Enter boat as passenger when owner enters boat |
| Portal following | Missing | Follow owner through Nether/End portals |
| Walking, sprinting, swimming, jumping, doors | Working | No changes |
| Minecarts | Missing | Defer — edge case |
| Crawling (1-block gaps) | Missing | Defer — vanilla doesn't support for mobs |

---

## 5. Mining UX Improvements

Current friction: typing coordinates or multi-step crest ritual. Goal: point and click.

### Crosshair-targeted commands

- `/squire mine` (no coordinates) — raycast from player's crosshair, mine the block you're looking at
- `/squire chop` — same raycast, mine tree trunk + connected logs above
- Radial menu "Mine" wedge — sends crosshair-targeted mine command
- Radial menu "Chop" wedge — sends crosshair-targeted chop command

### Simplified area clear

- Crest click 1: set corner 1 (existing sneak+right-click)
- Crest click 2: set corner 2 (existing sneak+left-click)
- Radial menu "Clear" or automatic preview on second click
- Confirm via radial menu or click, not chat command

### Coordinate commands remain as fallback

`/squire mine <x y z>` and `/squire clear <pos1> <pos2>` still work for precision/automation. The crosshair and radial menu are the primary interface.

---

## 6. Test Framework

### Layer 1: JUnit Unit Tests

No Minecraft, no world. Pure logic. Runs in ~2 seconds via `./gradlew test`.

| Test Class | Coverage |
|-----------|---------|
| SquireConfigTest | All defaults pass validation, feature toggles exist and default true |
| ProgressionTest | XP curve math, level thresholds 1-30, all ability unlock gates |
| EquipmentHelperTest | Weapon ranking, armor comparison, isMeleeWeapon, tool selection |
| TaskQueueTest | FIFO ordering, max length, NBT round-trip |
| AIStateTest | State enum completeness, priority layer assignment |
| TierTest (NEW) | Tier boundaries (0-4), correct unlocks per tier, level-gate enforcement |

### Layer 2: NeoForge GameTest

Headless server, real world. Runs via `./gradlew gameTestServer`.

| Test Class | What it proves |
|-----------|---------------|
| SpawnTests | Crest spawns squire, owner set, crest persists in inventory |
| FollowTests | Squire walks to owner, stops at threshold |
| CombatTests | Squire targets hostile, attacks, deals damage (only if Lv5+) |
| MiningTests | Crosshair mine breaks block, drops item (only if Lv5+) |
| EquipTests | Auto-equip best weapon from inventory |
| EatingTests | Eats food below health threshold |
| ProgressionTests | XP gain from multiple sources, level-up fires, tier transitions |
| PatrolTests | Two signposts, squire walks between them |
| ChestTests | Store command moves items to chest |
| FarmingTests | Harvest mature crop, collect drops |
| FishingTests | Enter fishing state near water |
| QueueTests | Sequential command execution |
| ToggleTests | Disabled feature returns error, handler doesn't fire |
| MovementTests | Ladder climbing, boat entry |
| CrestTests | Resummon after death restores level/name, crest not consumed |

### File Structure

```
src/
├── test/java/com/sjviklabs/squire/     # JUnit (no Minecraft)
│   ├── config/SquireConfigTest.java
│   ├── progression/ProgressionTest.java
│   ├── progression/TierTest.java
│   ├── equipment/EquipmentHelperTest.java
│   ├── queue/TaskQueueTest.java
│   └── ai/AIStateTest.java
└── main/java/com/sjviklabs/squire/
    └── test/                            # GameTest (NeoForge, in main sourceSet)
        ├── SquireGameTests.java
        ├── SpawnTests.java
        ├── CombatTests.java
        ├── WorkTests.java
        ├── ProgressionTests.java
        ├── MovementTests.java
        └── FeatureToggleTests.java
```

### CI Integration

GitHub Actions already runs `./gradlew build`. Add JUnit to the build task. GameTest runs via `./gradlew gameTestServer` as a separate CI step. Both must pass for green build.

---

## 7. Release Artifacts

### Local rollback

`releases/` folder (gitignored) with JAR copies after each tagged build. Quick rollback: grab old JAR, deploy.

### GitHub Releases

CI tags trigger release workflow with JAR attached. Permanent archive.

---

## 8. Backward Compatibility

Existing squires in the world need migration:

- **First load after update:** If squire entity exists but player has no capability attachment, copy entity's XP/level/name to player attachment. One-time migration.
- **Config toggle `classicMode`:** When true, all features unlocked at Lv1 (current behavior). Default false. Escape hatch for existing players who don't want the progression rework.

---

## 9. Road to Publish

| Version | Contents |
|---------|----------|
| v0.5.0 | Progression redesign, crest rework, feature toggles, test framework, movement parity, mining UX, stabilize all Phase 5 code |
| v0.9.0 | Community polish — guidebook pages, remaining placeholder art, README for players, screenshots |
| v1.0.0 | CurseForge + Modrinth listing, GitHub Release, community announcement |

5c (autonomous AI) and 5d (multi-squire) are v1.1.0+ after user feedback.
