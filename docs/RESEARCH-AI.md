# Research: NPC AI Architecture (MineColonies Deep Dive)

Research conducted 2026-03-18 for Squire Mod Phase 1 planning.

## MineColonies Citizen AI — How It Works

MineColonies (`ldtteam/minecolonies` on GitHub) is the gold standard for smart Minecraft NPCs. Their citizens do NOT use vanilla Goals.

### Core: Tick-Rate State Machine

The brain is `TickRateStateMachine<IAIState>`, initialized in `AbstractAISkeleton`.

**148 distinct states** defined in the `AIWorkerState` enum — from `IDLE` to `BAKER_KNEADING` to `NETHER_OPENPORTAL`.

**Transitions** are `AITarget` objects combining:
- Source state
- Boolean condition predicate
- Action function (returns next state)
- **Tick rate** (how often to check)

**Priority evaluation each tick:**
1. AI-blocking transitions (highest)
2. Event transitions (external triggers)
3. State-blocking transitions
4. Current state transitions (normal behavior)

**Key optimization:** Each transition has its own countdown timer. A transition with tick rate 100 only checks every 100 ticks. This is how they run 100+ citizens without killing TPS.

**Server lag compensation:** Static `slownessFactor` adjusts tick timings when server TPS drops below 20.

### AI Class Hierarchy

```
AbstractAISkeleton          — owns the TickRateStateMachine, tick() loop
  └─ AbstractEntityAIBasic  — inventory mgmt, item requests, tool handling
       └─ AbstractEntityAIInteract   — block breaking/interaction
            └─ AbstractEntityAISkill  — XP and skill leveling
                 └─ AbstractEntityAICrafting  — recipe/crafting logic
                 └─ AbstractEntityAIStructure — building/placing structures
                 └─ AbstractEntityAIUsesFurnace — smelting logic
```

Each concrete worker extends the appropriate abstract class and registers additional AITargets for job-specific states.

### Minimal AI (Survival Needs)

Separate from job AI, every citizen runs survival needs at higher priority:
- `EntityAIEatTask` — hunger/eating
- `EntityAISleep` — rest
- `EntityAISickTask` — illness
- `EntityAICitizenAvoidEntity` — fleeing danger
- `EntityAICitizenWander` — idle wandering
- `EntityAIMournCitizen` — mourning dead citizens

Priority: sickness > raid response > sleep > eating > mourning > rain avoidance > work/idle

### Entity Composition (Handler Pattern)

Citizens are composed via handler subsystems (`citizenhandlers/`):

| Handler | Responsibility |
|---------|---------------|
| CitizenJobHandler | Job assignment, switching |
| CitizenInventoryHandler | Inventory management |
| CitizenFoodHandler | Hunger, food consumption |
| CitizenSleepHandler | Sleep cycles |
| CitizenSkillHandler | Skill progression |
| CitizenExperienceHandler | XP and leveling |
| CitizenHappinessHandler | Satisfaction/morale |
| CitizenDiseaseHandler | Illness mechanics |
| CitizenColonyHandler | Colony membership |

### Custom Pathfinding (Async A*)

MineColonies completely replaces vanilla pathfinding:

**Threading:** Dedicated thread pool (1-N daemon threads, queue capacity 10,000 jobs). `AbstractPathJob` implements `Callable<Path>` — fully async. Uses `ChunkCache` for thread-safe world reads.

**Algorithm:** Priority-queue A* with:
- `Int2ObjectOpenHashMap<MNode>` for O(1) node lookup
- Max node cap of 5,000 (dynamically scaled by distance)
- Dynamic heuristic rebalancing mid-search

**3D terrain handling:**
- Ladder climbing (directional facing)
- Water/swimming (entry cost, diving cost)
- Door/trapdoor awareness (direction-aware)
- Stair recognition (reduced jump penalties)
- Rail transit (citizens can ride minecarts)
- Cubic drop cost (`dropCost * |dY|^3`) penalizes large falls

**17 specialized path jobs:** PathJobMoveToLocation, PathJobFindTree, PathJobFindWater, PathJobMoveAwayFromLocation, PathJobRaiderPathing, PathJobRandomPos, PathJobSignConnection, etc.

### Item Knowledge: Request System

The request system (`api.colony.requestsystem.*`) is a full logistics layer:

**Flow for a worker needing items:**
1. Worker AI hits `NEEDS_ITEM` state
2. Calls `checkIfRequestForItemExistOrCreate()` — checks own inventory → building storage → creates request
3. Request enters resolver chain:
   - BuildingRequestResolver → check citizen's own building
   - WarehouseRequestResolver → check colony warehouses
   - PrivateWorkerCraftingRequestResolver → can citizen craft it?
   - PublicWorkerCraftingRequestResolver → can another citizen craft it?
   - DeliverymenRequestResolver → dispatch deliveryman
   - StandardPlayerRequestResolver → ask the player
4. Tool selection: `holdEfficientTool(BlockState, BlockPos)` queries best tool for target block

### Comparable Mods

| Mod | AI Architecture | Pathfinding | Intelligence |
|-----|----------------|-------------|-------------|
| **MineColonies** | Custom tick-rate state machine, 148 states | Custom async A* thread pool | Full logistics chain, job hierarchy |
| **Taterzens** | Server-side NPCs, Architectury | Vanilla or basic custom | Scriptable, GUI-based |
| **Custom NPCs** | JavaScript scripting engine | Vanilla pathfinding | Script-driven, dialogue trees |
| **Citizens (Bukkit)** | Plugin-based, trait system | Vanilla pathfinding | Command-driven, trait-based |

## Key Takeaways for Squire Mod

1. **Use a tick-rate-gated state machine** (Phase 2) — vanilla Goals won't scale to complex behaviors
2. **Layer AI via class hierarchy** — base skeleton → basic needs → interaction → job-specific
3. **Separate survival from work** — eat/sleep/flee at higher priority than task AI
4. **Async pathfinding is non-negotiable** for server performance (Phase 4)
5. **Item knowledge via typed queries** — don't hardcode, query tool efficiency against block state
6. **Compose entities with handlers** — don't put everything in one god class
7. **States are cheap, transitions are the logic** — 148 states is fine, the predicates do the work
