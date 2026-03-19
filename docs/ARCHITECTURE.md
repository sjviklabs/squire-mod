# Squire Mod — Architecture Decisions

## Entity Base Class: TamableAnimal

**Decision:** `SquireEntity extends TamableAnimal` (not `PathfinderMob`, not `FakePlayer`)

### Why not FakePlayer?

FakePlayer is a ServerPlayer subclass designed for automation (block breaking/placing on behalf of machines). It fights the engine:
- No visible model or client-side presence
- Requires a GameProfile and fake network connection
- Issues with keepalive packets and chunk loading
- It's a hack, not a foundation for a visible companion

### Why not PathfinderMob (original scaffolding)?

PathfinderMob works but misses infrastructure that TamableAnimal provides for free:
- No built-in owner tracking
- No sitting/standing state machine
- No `OwnableEntity` interface (other mods can't recognize it)
- No vanilla `OwnerHurtByTargetGoal` / `OwnerHurtTargetGoal`
- Have to build all owner-relationship features from scratch

### Why TamableAnimal?

Gives us for free:
- **Owner UUID tracking + persistence** — built-in `getOwnerUUID()`, `setOwnerUUID()`
- **`OwnableEntity` interface** — other mods (Jade, WAILA, etc.) recognize it as a pet
- **Sitting/standing** — our STAY/FOLLOW modes use the existing sit state machine
- **Combat targeting** — `OwnerHurtByTargetGoal` and `OwnerHurtTargetGoal` handle "defend owner" and "attack what owner attacks" with zero custom code
- **`isTame()` / `tame()` lifecycle** — clean ownership model

**What we suppress:**
- `tryToTeleportToOwner()` → override as no-op (NEVER teleport, core design requirement)
- `isFood()` → return false (no breeding)
- Don't register `BreedGoal`

The player model rendering is independent of entity class hierarchy — `HumanoidMobRenderer` with `PlayerModel` works on any `LivingEntity`.

## AI Architecture: Vanilla Goals (Phase 1) → Tick-Rate State Machine (Phase 2)

### Phase 1: Vanilla Goal System

Using vanilla Goal system for initial release. Simpler, proven, well-documented. Goals are ordered by priority with proper Flag sets to prevent conflicts.

**Known limitations:**
- Goals can only do one thing at a time (no parallel behaviors)
- No built-in tick-rate throttling per goal
- Complex multi-step tasks are awkward to express
- Will be outgrown when we add mining, placing, crafting

### Phase 2+: MineColonies-Inspired State Machine

MineColonies uses a custom `TickRateStateMachine` with 148 states. Key patterns to adopt:
- **Per-transition tick rates** — combat checks every tick, idle wander every 60 ticks
- **Priority layers** — survival > combat > commands > passive tasks
- **Handler composition** — inventory handler, food handler, skill handler (not one god class)
- **Async pathfinding** — path calculations on thread pool, not server thread

We'll refactor to this when adding block breaking/placing (Phase 2).

## Performance Budget

Target: 20 players with squires on a modded server without TPS impact.

| System | Tick Rate | Budget |
|--------|-----------|--------|
| Path recalculation | Every 10 ticks | Vanilla pathfinding, capped at 32 blocks |
| Item entity scan | Every 40 ticks | 8-block AABB, only when not in combat |
| Equipment check | Every 60 ticks | Pure inventory comparison, cheap |
| Eat check | Every 20 ticks | Simple health threshold, near-zero cost |
| Combat | Every tick (when active) | Only when target exists |
| Follow | Every tick (when active) | `requiresUpdateEveryTick()` for responsiveness |

## Config-First Design

Every magic number comes from `ModConfigSpec`. This is mandatory for modpack adoption. Server operators must be able to tune:
- Max squires per player
- All distance thresholds (follow, sprint, combat leash, aggro range)
- Health, regen rates, eat threshold
- Tick intervals for all periodic systems
