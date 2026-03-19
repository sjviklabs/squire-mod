# Code Review: Pre-Refactor Audit

Reviewed 2026-03-18. All existing source files audited before Phase 1 rewrite.

## What's Solid (Keep)

- **Registration pattern** — DeferredRegister with separate init classes (ModEntities, ModItems, ModMenuTypes). Clean, idiomatic NeoForge.
- **SquireMenu shift-click** — `quickMoveStack()` correctly handles bidirectional item transfer.
- **Inventory serialization** — `toTag()`/`fromTag()` with slot-indexed ListTag is correct and handles sparse inventories.
- **Package structure** — logical separation (entity, ai, inventory, item, init, network, client).
- **ClientSetup** — proper `@EventBusSubscriber` with `Dist.CLIENT` and `Bus.MOD`.

## Bugs (7 Found)

| # | File | Line | Severity | Issue |
|---|------|------|----------|-------|
| 1 | `SquireEntity.java` | 65 | High | `NearestAttackableTargetGoal<Monster>` aggros ALL monsters including modded neutrals. Will cause friendly fire with other mods. |
| 2 | `SquireBadgeItem.java` | 29 | Medium | `context.getPlayer()` can return null (dispenser, fake player). NPE crash. |
| 3 | `SquireInventory.java` | 83 | High | `stillValid()` always returns `true`. Dupe exploit: open inventory, kill squire, keep shift-clicking items out of dead entity's container. |
| 4 | `SquireFollowGoal.java` | 62-70 | Critical | Teleport uses `moveTo()` at owner's Y with random X/Z offset. Can place squire inside walls, in lava, or in the void. Also violates core design (no teleporting). |
| 5 | `ModEntities.java` | 21 | Medium | `MobCategory.CREATURE` counts against passive mob cap. Squires will prevent cows/pigs from spawning. Should be `MobCategory.MISC`. |
| 6 | `SquireEntity.java` | — | Medium | Owner UUID is never synced to client via `SynchedEntityData`. Client-side code can't determine ownership. |
| 7 | `neoforge.mods.toml` | all | Low | Was default MDK template with example comments. **Fixed in Step 0.** |

## Redundancy & Waste

### SquireInventory reinvents SimpleContainer (167 lines → ~30)

`SquireInventory` is a hand-rolled `Container` implementation. Vanilla's `SimpleContainer` already provides:
- `getItem()`, `setItem()`, `removeItem()`, `removeItemNoUpdate()`
- `isEmpty()`, `clearContent()`, `getContainerSize()`
- `setChanged()` with listener support

Only 3 methods are custom: `canAddItem()`, `addItem()`, `dropAll()`.

**Fix:** Extend `SimpleContainer`, add the 3 custom methods. Delete ~130 lines.

### Owner tracking is hand-rolled (~30 lines → 0)

`SquireEntity` manually manages:
- `private UUID ownerUUID` field
- `setOwnerUUID()`, `getOwnerUUID()`, `getOwner()`, `isOwner()`
- NBT save/load for owner UUID

`TamableAnimal` provides all of this built-in: `getOwnerUUID()`, `setOwnerUUID()`, `getOwner()`, `isOwnedBy()`, automatic NBT persistence.

**Fix:** Extend `TamableAnimal` instead of `PathfinderMob`. Delete all owner management code.

### Magic numbers scattered across 6 files

| Constant | Where Used | Value |
|----------|-----------|-------|
| Inventory size | SquireEntity, SquireInventory, SquireMenu | `27` |
| Aggro range | SquireFightGoal | `12.0D` |
| Pickup range | SquireEntity goal registration | `8.0D` |
| Teleport distance | SquireFollowGoal | `24.0F` |
| Follow start distance | SquireEntity goal registration | `6.0F` |
| Follow stop distance | SquireEntity goal registration | `2.0F` |
| Walk speed | SquireEntity goal registration | `1.0D` |
| Attack speed | SquireEntity goal registration | `1.2D` |

**Fix:** Create `SquireConfig.java` using `ModConfigSpec`. All constants in one file, configurable by server operators.

### No entity validation in menu

`SquireMenu.stillValid()` → `SquireInventory.stillValid()` → always `true`.

The menu holds a `squireEntityId` but never uses it to check if the entity is alive. Combined with the always-true `stillValid()`, this creates a dupe vector.

**Fix:** `stillValid()` should resolve the entity by ID and check `isAlive()`.

### Badge has no limit enforcement

`SquireBadgeItem.useOn()` creates a new squire every time. No check for:
- Existing squire already alive
- Max squires per player
- Null player (dispenser/fake player)

**Fix:** Query world for existing squires owned by this player. Enforce config limit.

## Recommended File Structure (Post-Refactor)

```
com.sjviklabs.squire/
  SquireMod.java                    — entry point, register all deferred registers + config
  config/
    SquireConfig.java                — ModConfigSpec, ALL tunable constants
  entity/
    SquireEntity.java                — extends TamableAnimal, lean (suppress teleport/breeding)
  ai/
    SquireFollowOwnerGoal.java       — walk/sprint, speed match, no teleport, doors/ladders
    SquireMeleeGoal.java             — player-like combat, weapon cooldown, owner leash
    SquirePickupGoal.java            — item scan, auto-equip trigger on pickup
    SquireEatGoal.java               — eat food when health < threshold
    SquireSitGoal.java               — STAY mode (extends SitGoal)
  inventory/
    SquireInventory.java             — extends SimpleContainer + canAddItem/addItem/dropAll
    SquireMenu.java                  — armor/offhand slots, entity-alive validation
    SquireScreen.java                — custom texture with armor slot layout
  item/
    SquireBadgeItem.java             — limit check, null safety, recall mechanic
  command/
    SquireCommand.java               — /squire list, kill, limit
  network/
    SquireModePayload.java           — client→server mode toggle
  util/
    SquireEquipmentHelper.java       — armor/weapon/shield comparison, auto-equip logic
  client/
    SquireRenderer.java              — HumanoidMobRenderer with PlayerModel
  init/
    ModEntities.java                 — MobCategory.MISC, player-sized hitbox
    ModItems.java                    — (unchanged)
    ModMenuTypes.java                — (unchanged)
```

## Refactor Order

1. **SquireConfig.java** — constants centralized first, everything references it
2. **SquireEntity.java** — TamableAnimal rewrite, lean core, SynchedEntityData
3. **SquireInventory.java** — extend SimpleContainer, fix stillValid()
4. **AI goals** — new goal files, delete old SquireFightGoal/SquireFollowGoal
5. **SquireBadgeItem.java** — limit enforcement, recall, null safety
6. **SquireMenu/SquireScreen** — armor slots, entity validation
7. **Client** — SquireRenderer, texture
8. **SquireCommand, SquireModePayload** — admin tools and networking
