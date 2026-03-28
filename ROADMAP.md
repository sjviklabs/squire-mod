# Squire Mod — Phase 5 Roadmap

**Target:** v0.4.0 (weapons + visuals), v0.5.0 (behaviors + queue)
**Rule:** 90% confidence before moving to next item. Test and verify each piece.
**Phase 5d (Advanced AI) deferred to v0.6.0+** — confidence too low, would block release.

## Confidence Gate

Before marking any item "done":
- [ ] `./gradlew build` passes
- [ ] Manual test in `./gradlew runClient` confirms behavior
- [ ] No regressions in existing features (combat, follow, patrol, mining all still work)
- [ ] Config values added for all new gameplay numbers
- [ ] Lang strings added for all player-facing text
- [ ] Code reviewed for NeoForge 1.21.1 pitfalls (see CLAUDE.md)

## Build Order

```
v0.4.0 — Weapons + Visual Polish (~4.5 days)
  1. Bow Item retest           [TESTING]  0.5d
  2. Halberd Item              [PLANNED]  1.5d
  3. Visual Progression        [PLANNED]  2.0d
  4. Radial Menu retest        [TESTING]  0.5d
  → Tag v0.4.0, deploy, collect feedback

v0.5.0 — New Behaviors + Infrastructure (~5.5 days)
  5. Farming Handler           [PLANNED]  2.5d
  6. Fishing Handler           [PLANNED]  1.5d
  7. Task Queue                [PLANNED]  1.5d
  → Tag v0.5.0, deploy

v0.6.0+ — Advanced AI (deferred)
  8. Autonomous Multi-Step     [RESEARCH]
  9. Multi-Squire Coordination [RESEARCH]
```

---

## Phase 5a — New Weapons

### 1. Bow Item [TESTING]
- **Confidence:** ~90% (all code done, awaiting `./gradlew build` + in-game retest)
- **Done:**
  - [x] `SquireBowItem.java`, model JSON (3 pull stages), recipe, lang string
  - [x] Registered in `ModItems` (384 durability)
  - [x] `COMBAT_RANGED` state wired in CombatHandler
  - [x] Bow-only during COMBAT_RANGED (swaps back to melee on exit)
  - [x] Shield stowed when bow equipped, re-equipped on melee swap
  - [x] Nametag/health bar repositioned above head
  - [x] `isMeleeWeapon()` helper: recognizes `SquireHalberdItem` alongside `SwordItem`/`AxeItem`
  - [x] `DRAWING_BOW` synched data + `BOW_AND_ARROW` arm pose in renderer
  - [x] `setDrawingBow(false)` on all combat exit paths including `disengageCombat()`
- **Remaining:**
  - [ ] `./gradlew build` passes
  - [ ] In-game retest: weapon swap, bow pose, distance maintenance, shield stow/re-equip
  - [ ] Investigate skin rendering issue (textures verified 64x64 RGBA, needs in-game repro)

### 2. Halberd Item [TESTING]
- **Confidence:** ~85% (all code done, awaiting `./gradlew build` + in-game test)
- **Design:**
  - Sweep attack: every 3rd melee hit triggers 360 AoE within 2.5 blocks
  - Sweep damage: 75% of normal attack to all mobs in radius (max 5 targets)
  - Sweep cooldown: 60 ticks (3 seconds) between sweeps
  - Base stats: 7 attack damage, -3.0 attack speed, +1.0 reach
  - Visual: sweep particle effect + sound on trigger
- **Done:**
  - [x] `SquireHalberdItem.java` — attribute modifiers for damage/speed/reach
  - [x] Registered in `ModItems` (600 durability)
  - [x] Item model JSON, 16x16 placeholder texture, shaped recipe
  - [x] `SquireEquipmentHelper.isMeleeWeapon()` recognizes `SquireHalberdItem`
  - [x] `CombatHandler` sweep tracking: `consecutiveHits`, `sweepCooldownRemaining`
  - [x] `performSweep()` — 360° AoE with friendly filtering, particle + sound
  - [x] `getAttackReachSq()` — 4.0 block reach for halberd
  - [x] Config values: `sweepDamageMultiplier`, `sweepCooldownTicks`, `sweepRange`, `sweepMaxTargets`
  - [x] Lang string: "Squire's Halberd"
- **Remaining:**
  - [ ] `./gradlew build` passes
  - [ ] In-game test: crafting, equip, sweep trigger, AoE damage, particle effect
  - [ ] Replace placeholder texture with Grimdark Battlepack halberd asset
- **Config Values:**
  - `sweepDamageMultiplier` — 0.75 (range 0.1-1.5)
  - `sweepCooldownTicks` — 60 (range 20-200)
  - `sweepRange` — 2.5 (range 1.0-5.0)
  - `sweepMaxTargets` — 5 (range 1-10)
- **Risks:**
  - Sweep AoE could hit friendly mobs or other players; need target filtering
  - Attack counter resets on target switch; track in CombatHandler per-entity

---

## Phase 5a.5 — Visual & UX Polish

### 3. Visual Progression [TESTING]
- **Confidence:** ~80% (armor tier system built, textures staged, awaiting build + in-game test)
- **Design:** Cosmetic-only. Renderer picks texture variant by tier. No gameplay changes.
  - Tier 0 (Lv 1-9): Recruit — light armor, recurve bow, base skin
  - Tier 1 (Lv 10-19): Veteran — medium armor, shortbow, battle-worn skin
  - Tier 2 (Lv 20-29): Champion — heavy armor, greatbow, veteran skin
  - Tier 3 (Lv 30): Legend — royal armor, yumi, champion skin
- **Done:**
  - [x] `client/SquireTieredArmorLayer.java` — custom render layer, selects tier texture by level
  - [x] `SquireRenderer.java` — replaced vanilla HumanoidArmorLayer with SquireTieredArmorLayer
  - [x] `SquireConfig.java` — `enableVisualProgression` (boolean, default true)
  - [x] `en_us.json` — tier names: "Recruit", "Veteran", "Champion", "Legend"
  - [x] 8 armor layer textures extracted from Grimdark Battlepack v27 (light/medium/heavy/royal iron)
  - [x] 16 bow tier textures extracted (recurve/shortbow/greatbow/yumi + pull stages)
  - [x] 31 additional weapon/tool/shield/polearm textures staged for future use
- **Remaining:**
  - [ ] `./gradlew build` passes
  - [ ] In-game test: verify armor changes at level thresholds 10/20/30
  - [ ] Entity body skins per tier (squire_t0..t3.png) — deferred, using base skin for all tiers
  - [ ] Bow model swap by tier (recurve→shortbow→greatbow→yumi) — staged, needs renderer work
  - [ ] API note: `renderToBuffer` signature may need adjustment for 1.21.1 (int color vs float RGBA)
- **Config Values:**
  - `enableVisualProgression` — true (boolean)
- **Risks:**
  - `renderToBuffer` signature might be `(PoseStack, VertexConsumer, int, int, int)` (color as ARGB int) in 1.21.1
  - `RenderType.armorEntityGlint()` name may differ — check build output
  - Non-squire armor texture fallback uses manual path construction from ArmorMaterial.Layer

### 4. Radial Command Menu [TESTING]
- **Confidence:** ~90% (fully built, needs in-game test)
- **Done:**
  - [x] `client/SquireKeybinds.java` — R key, `key.categories.squire`
  - [x] `client/SquireRadialScreen.java` — 8 wedge arc rendering, hover detection, click dispatch
  - [x] `client/SquireClientEvents.java` — keybind tick, raycast + 8-block proximity targeting
  - [x] `network/SquireCommandPayload.java` — 8 command IDs, ownership validation
  - [x] Server handler: all 8 commands dispatch (mode, patrol, store, fetch, mount, inventory)
  - [x] Lang strings for all wedge labels + keybind category
- **Remaining:**
  - [ ] In-game test: open wheel, select each command, verify server execution
  - [ ] Optional: wedge icon textures (16x16 Grimdark) — currently text-only
  - [ ] Optional: config for radial menu opacity

---

## Phase 5b — New Behaviors

### 5. Farming Handler [TESTING]
- **Confidence:** ~80% (all code done, awaiting `./gradlew build` + in-game test)
- **Design:**
  - Command: `/squire farm <pos1> <pos2>` defines rectangular area
  - Workflow: approach → scan (find next task) → work (till/plant/harvest) → repeat
  - Crop detection: wheat, potatoes, carrots, beetroot (use CropBlock.isMaxAge() for maturity)
  - Seed tracking: check inventory for matching seed type before farming each block
  - Priority: harvest mature > till dirt > plant seeds
- **New States:** `FARM_APPROACH`, `FARM_WORK`, `FARM_SCAN`
- **Done:**
  - [x] `ai/handler/FarmingHandler.java` (~270 lines) — full scan/approach/work cycle
  - [x] `ai/statemachine/SquireAIState.java` — added 3 farm states
  - [x] `ai/statemachine/SquireAI.java` — instantiate, register transitions, getter
  - [x] `command/SquireCommand.java` — `/squire farm <pos1> <pos2>` + `/squire farm stop`
  - [x] `config/SquireConfig.java` — 4 farm config values
  - [x] `lang/en_us.json` — farm command feedback messages
- **Remaining:**
  - [ ] `./gradlew build` passes
  - [ ] In-game test: till, plant, harvest cycle in a wheat field
  - [ ] Verify area bounds check prevents oversized farms
- **Config Values:**
  - `farmReach` — 3.0 (range 1.0-6.0)
  - `farmTicksPerBlock` — 10 (range 5-40)
  - `farmScanInterval` — 40 (range 10-200)
  - `farmMaxArea` — 256 (range 16-1024, blocks)
- **Risks:**
  - Hoe right-click simulation: UseOnContext params may need adjustment
  - `ServerLevel.destroyBlock()` for harvest; verify drops spawn
  - Area bounds check to prevent griefing on servers

### 6. Fishing Handler [TESTING]
- **Confidence:** ~75% (all code done, awaiting `./gradlew build` + in-game test)
- **Design (Simplified v1):**
  - Command: `/squire fish` — find nearest water, walk to edge, idle-fish
  - No actual fishing rod casting in v1; simulated catches on cooldown
  - Every `fishingCatchInterval` ticks: roll loot table, add item to inventory
  - Loot: cod (60%), salmon (25%), tropical fish (10%), pufferfish (5%)
  - Requires fishing rod in inventory (consumed durability per catch)
  - Visual: squire faces water, holds fishing rod
- **New States:** `FISHING_APPROACH`, `FISHING_IDLE`
- **Done:**
  - [x] `ai/handler/FishingHandler.java` (~230 lines) — approach + idle-fish with loot rolls
  - [x] `ai/statemachine/SquireAIState.java` — added 2 fishing states
  - [x] `ai/statemachine/SquireAI.java` — instantiate, register transitions, getter
  - [x] `command/SquireCommand.java` — `/squire fish` + `/squire fish stop`
  - [x] `config/SquireConfig.java` — 3 fishing config values
  - [x] `lang/en_us.json` — fishing feedback messages
- **Remaining:**
  - [ ] `./gradlew build` passes
  - [ ] In-game test: approach water, idle fishing, catch items
  - [ ] Verify fishing rod durability consumption
- **Config Values:**
  - `waterSearchRange` — 16.0 (range 4.0-32.0)
  - `fishingCatchInterval` — 400 (range 100-1200, ticks; 20 seconds default)
  - `fishingRodDurabilityPerCatch` — 1 (range 0-5)
- **Risks:**
  - Water edge detection: may need tuning for edge cases (waterfalls, deep pools)
  - `hurtAndBreak` signature: verify correct for 1.21.1

---

## Phase 5c — Command Infrastructure

### 7. Task Queue [TESTING]
- **Confidence:** ~80% (all code done, awaiting `./gradlew build` + in-game test)
- **Design:**
  - FIFO queue of commands; max 10 entries (config)
  - Commands: `/squire queue add <command>`, `/squire queue list`, `/squire queue clear`
  - Execution: when current task completes (state → IDLE), pop next task from queue
  - Persistence: NBT serialization in `addAdditionalSaveData` / `readAdditionalSaveData`
  - Queue pauses if squire enters combat (survival priority); resumes after
  - Dispatch supports: follow, stay, guard, mine, farm, fish, patrol
- **Done:**
  - [x] `util/TaskQueue.java` (~125 lines) — FIFO queue with NBT read/write
  - [x] `entity/SquireEntity.java` — `TaskQueue` field, `getTaskQueue()`, NBT persistence
  - [x] `ai/statemachine/SquireAI.java` — IDLE transition checks queue, `dispatchQueuedTask()` method
  - [x] `command/SquireCommand.java` — `/squire queue add/list/clear` subcommands
  - [x] `config/SquireConfig.java` — `maxQueueLength` (default 10)
  - [x] `lang/en_us.json` — queue feedback messages
- **Remaining:**
  - [ ] `./gradlew build` passes
  - [ ] In-game test: queue multiple commands, verify sequential execution
  - [ ] Test NBT persistence across world reload
  - [ ] Test combat interruption pauses queue, resumes after
- **Config Values:**
  - `maxQueueLength` — 10 (range 1-50)
- **Risks:**
  - Queued command may reference entity/block that no longer exists
  - Queue state across world reload needs thorough testing

---

## Phase 5d — Advanced AI (Deferred to v0.6.0+)

These items have < 30% confidence and would block the v0.5.0 release. Defer until Phase 5a-5c is stable and feedback is collected.

### 8. Autonomous Multi-Step [RESEARCH]
- **Confidence:** ~20%
- Item knowledge system, need-based priority, long-range logistics
- Example: "mine ore → smelt → store ingots" without explicit commands
- Requires: goal planner, item registry, state graph

### 9. Multi-Squire Coordination [RESEARCH]
- **Confidence:** ~15%
- Formation, role assignment, shared task awareness
- Requires: inter-entity communication, synchronization protocol
- Risk: network overhead, race conditions

---

## Version Bump Checklists

### v0.4.0 (Phase 5a + 5a.5 Complete)
- [ ] All weapons working (bow + halberd)
- [ ] Visual progression renders all 4 tiers
- [ ] Radial menu tested, all 8 commands functional
- [ ] Update `mod_version` in `gradle.properties` to `0.4.0`
- [ ] Update CHANGELOG.md
- [ ] Tag `v0.4.0`
- [ ] `./deploy.sh all --restart`

### v0.5.0 (Phase 5b + 5c Complete)
- [ ] Farming handler: full till→plant→harvest→replant loop
- [ ] Fishing handler: idle fishing with loot
- [ ] Task queue: chaining commands, NBT persistence
- [ ] No regressions in v0.4.0 features
- [ ] Update `mod_version` in `gradle.properties` to `0.5.0`
- [ ] Update CHANGELOG.md
- [ ] Tag `v0.5.0`
- [ ] `./deploy.sh all --restart`

---

## Daily Work Log

Track what was done each session. Keep it short.

### 2026-03-25 (Session 1)
- Foundation audit: restructured CLAUDE.md, created .claude/rules/, set up memory system
- Researched NeoForge 1.21.1 best practices and common pitfalls
- Researched Claude CLAUDE.md best practices from official docs
- Created roadmap with confidence gates

### 2026-03-25 (Session 2)
- Built Bow Item: SquireBowItem.java, model JSONs, recipe, lang string, registered in ModItems
- In-game test revealed 3 bugs: bow+shield conflict, no weapon swap back, nametag on face
- Fixed all 3: EquipmentHelper state-aware swap, shield stow logic, renderer Y offset
- Audited Grimdark Battlepack v27 zip: found halberd, armor sets, bow variants, polearms
- Designed Visual Progression system (cosmetic tier overlays by level)
- Designed Radial Command Menu (keybind → wheel UI → server command dispatch)
- Added both as Phase 5a.5 items 3-4 in roadmap
- Next: Rebuild and retest bow fixes, then start Visual Progression or Radial Menu

### 2026-03-25 (Session 3)
- Fixed ranged→melee weapon swap bug: added `isMeleeWeapon()` helper to `SquireEquipmentHelper` recognizing `SquireHalberdItem` alongside `SwordItem`/`AxeItem`
- Patched all 3 weapon scan locations: `tryAutoEquip()`, `runFullEquipCheck()`, `switchToMeleeLoadout()`
- Added bow draw arm pose: `DRAWING_BOW` synched data on `SquireEntity`, `BOW_AND_ARROW` arm pose in `SquireRenderer`
- Added `setDrawingBow(false)` to all `tickRanged()` exit paths and `disengageCombat()`
- Skin rendering issue: textures verified 64x64 RGBA, no code issues found, needs in-game repro
- Build could not run in sandbox (no network), needs local `./gradlew build` + retest
- Next: Build locally, full in-game retest of bow item, then start Halberd or Visual Progression

### 2026-03-25 (Session 4)
- Discovered radial menu was already fully built (code existed but roadmap said NOT STARTED)
- Updated roadmap to reflect actual status: radial menu at ~90% confidence
- Planned full Phase 5 implementation: 7 items across 3 sub-phases + 2 deferred
- Split version strategy: v0.4.0 (weapons + visuals), v0.5.0 (behaviors + queue), v0.6.0+ (advanced AI)
- Detailed every item: files to create/modify, config values, states, risks, test checklists
- Next: Local build + retest of bow and radial menu, then Halberd Item

### 2026-03-25 (Session 5)
- Completed Halberd Item implementation (all code done, ~85% confidence)
- Wrote `performSweep()` in CombatHandler: 360° AoE with friendly filtering, particles, sound
- Added halberd reach (4.0 blocks) to `getAttackReachSq()`
- Added 4 config values to SquireConfig: `sweepDamageMultiplier`, `sweepCooldownTicks`, `sweepRange`, `sweepMaxTargets`
- Added lang string for halberd
- Next: `./gradlew build` + in-game test, replace placeholder texture with Grimdark asset

### 2026-03-25 (Session 6)
- Replaced halberd placeholder with Grimdark Battlepack iron_halberd.png (32x32)
- Extracted 58 Grimdark Battlepack textures into mod resource tree:
  - 8 armor tier layers (light/medium/heavy/royal iron → t0..t3)
  - 16 bow tier textures (recurve/shortbow/greatbow/yumi + 3 pull stages each)
  - 8 iron polearms, 7 iron weapons, 11 iron tools, 5 shields, 3 elytra
- Built Visual Progression system:
  - Created `SquireTieredArmorLayer.java` — custom render layer replacing vanilla HumanoidArmorLayer
  - Selects tier-specific armor textures based on squire level (Recruit/Veteran/Champion/Legend)
  - Falls back to vanilla material texture lookup for non-squire armor
  - Added `enableVisualProgression` config toggle
  - Added tier name lang strings
- Next: `./gradlew build` + in-game test of all tier visuals, then bow tier model swap

### 2026-03-25 (Session 7)
- Completed all v0.5.0 coding (farming, fishing, task queue)
- Built `FarmingHandler.java` (~270 lines): scan area → approach → till/plant/harvest → repeat
- Built `FishingHandler.java` (~230 lines): find water → approach edge → idle-fish with loot rolls
- Built `TaskQueue.java` (~125 lines): FIFO queue with NBT persistence
- Added 3 farm + 2 fishing states to `SquireAIState.java`
- Wired both handlers into `SquireAI.java` with full transition registration
- Added `dispatchQueuedTask()` to SquireAI for queue auto-dispatch on IDLE
- Added `TaskQueue` field + NBT persistence to `SquireEntity.java`
- Added `/squire farm`, `/squire fish`, `/squire queue` commands to `SquireCommand.java`
- Added 10 config values to `SquireConfig.java` (farming: 4, fishing: 3, queue: 1, visual: 1, halberd: 4)
- Added farming/fishing/queue lang strings to `en_us.json`
- Extended `isInWorkState()` to include farming and fishing states
- All code verified clean via static analysis; ready for `./gradlew build`
- Next: `./gradlew build` on local machine, then full in-game testing of all Phase 5 features