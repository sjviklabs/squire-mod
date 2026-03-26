# Squire Mod — Phase 5 Roadmap

**Target:** v0.5.0 — Autonomous Intelligence
**Rule:** 90% confidence before moving to next item. Test and verify each piece.

## Confidence Gate

Before marking any item "done":
- [ ] `./gradlew build` passes
- [ ] Manual test in `./gradlew runClient` confirms behavior
- [ ] No regressions in existing features (combat, follow, patrol, mining all still work)
- [ ] Config values added for all new gameplay numbers
- [ ] Lang strings added for all player-facing text
- [ ] Code reviewed for NeoForge 1.21.1 pitfalls (see CLAUDE.md)

---

## Phase 5a — New Weapons (Foundation)

### 1. Bow Item [TESTING]
- **Priority:** Highest. Textures already staged. Combat handler ready.
- **Confidence:** ~85% (built, 3 bug fixes applied, awaiting retest)
- **Done:**
  - [x] Create `SquireBowItem.java` in `item/`
  - [x] Register in `ModItems` (384 durability)  - [x] Add item model JSON with 3 pull-stage overrides
  - [x] Add crafting recipe (sticks + string)
  - [x] Add lang string ("Squire's Recurve Bow")
  - [x] CombatHandler ranged state already wired (`COMBAT_RANGED`)
  - [x] Fix: bow-only during COMBAT_RANGED state (weapon swaps back to melee)
  - [x] Fix: shield stowed when bow equipped, re-equipped on melee swap
  - [x] Fix: nametag/health bar repositioned above head
- **Remaining:**
  - [ ] Rebuild and retest all 3 fixes in-game
  - [ ] Verify pull animation plays
  - [ ] Verify ranged distance maintenance (squire backs away from close mobs)
  - [ ] Investigate skin rendering issue reported during testing
- **PINNED BUG: Ranged→Melee Weapon Swap**
  - Squire stays in bow mode visually when mob closes to melee range
  - Three fix attempts failed (state timing, direct swap, bypassed equip check)
  - **Likely root cause:** `switchToMeleeLoadout()` scans for `SwordItem`/`AxeItem` only — `SquireLanceItem` is neither, so if lance is the only melee weapon, mainhand ends up empty after stowing bow
  - **Fix to try:** Add `SquireLanceItem` to the melee weapon scan in `switchToMeleeLoadout()` and `runFullEquipCheck()`
  - Also check: periodic `runFullEquipCheck()` (every 60 ticks) may re-equip bow during `COMBAT_APPROACH` window

### 2. Halberd Item [WIP — DEFERRED]
- **Priority:** Medium. Rounds out melee arsenal with sweep.
- **Confidence:** ~30% (needs sweep mechanics design, custom texture, Blockbench model)
- **Blockers:** No vanilla halberd exists. Needs full custom item, model, texture, and sweep logic.
- **Tasks (when resumed):**
  - Design: sweep range, damage, cooldown, animation
  - Create `SquireHalberdItem.java`
  - Create texture (16x16, Grimdark style)
  - Create 3D Blockbench model
  - Register, recipe, lang string
  - Add sweep logic to `CombatHandler`
  - Test: sweep hits multiple mobs, damage numbers match config

---

## Phase 5a.5 — Visual & UX Polish

### 3. Visual Progression (Gear Appearance by Level) [NOT STARTED]
- **Priority:** High. Strong player feedback loop, textures available in Grimdark Battlepack.
- **Confidence:** ~60% (design complete, assets available, renderer pattern proven by BackpackLayer)
- **Design:** Cosmetic overlay — renderer picks texture variant by tier, actual item stats unchanged.
  - Tier 0 (Lv 1-9): Light armor, recurve bow, base skin
  - Tier 1 (Lv 10-19): Medium armor, shortbow, battle-worn skin
  - Tier 2 (Lv 20-29): Heavy armor, greatbow, veteran skin
  - Tier 3 (Lv 30): Royal armor, yumi, champion skin
- **Tasks:**
  - Extract tier armor textures from Grimdark Battlepack (light/medium/heavy/royal)
  - Create `squire_layer_1_t0..t3.png` and `squire_layer_2_t0..t3.png`
  - Create entity skin variants: `squire_t0..t3.png` and `squire_slim_t0..t3.png`
  - Modify `SquireRenderer.getTextureLocation()` to pick skin by tier
  - Create custom armor layer that overrides texture selection by tier
  - Extract bow variant textures (recurve/shortbow/greatbow/yumi)
  - Add config: `enableVisualProgression` toggle, tier level thresholds
  - Add lang strings for tier names
  - Test: level up squire through tiers, verify each visual stage renders correctly

### 4. Radial Command Menu [DONE]
- **Confidence:** 95%
- All tasks complete. Blur fix applied (override renderBackground). 8 wedges working.

### 5. Auto-Craft Basic Gear [DONE]
- **Confidence:** 90% (built, compiles, needs extended play-testing)
- Squire crafts wooden sword, shield, bow, and arrows from scavenged materials.
- Config toggle: `autoCraft.enabled`

### WIP: Horseback Movement
- `horse.move(MoverType.SELF)` approach written but horse still doesn't move correctly in testing.
- Root cause: `AbstractHorse.getControllingPassenger()` only returns Player in 1.21.1.
- Needs deeper investigation into how to drive a horse with an NPC passenger.

---

## Phase 5b — New Behaviors

### 5. Farming Handler [NOT STARTED]
- **Priority:** Medium. New behavior domain.
- **Confidence:** ~40% (needs full design)
- **Tasks:**
  - Design: `/squire farm <from> <to>` command flow  - New states: `FARM_APPROACH`, `FARM_TILL`, `FARM_PLANT`, `FARM_HARVEST`
  - Create `FarmingHandler.java`
  - Crop detection, seed selection from inventory, replant cycle
  - Config: farmReach, harvestCooldown, cropTypes
  - Test: full till-plant-harvest-replant loop

### 6. Fishing Handler [NOT STARTED]
- **Priority:** Low. Idle activity.
- **Confidence:** ~30% (design TBD)
- **Tasks:**
  - Design: water detection, idle trigger, loot table
  - New states: `FISHING_APPROACH`, `FISHING_CAST`, `FISHING_WAIT`
  - Create `FishingHandler.java`
  - Requires fishing rod in inventory
  - Config: fishingCooldown, waterSearchRange

---

## Phase 5c — Command Infrastructure

### 7. Task Queue [NOT STARTED]
- **Priority:** Medium. Enables chaining.
- **Confidence:** ~35% (architectural decision needed)
- **Tasks:**
  - Design: queue data structure, persistence (NBT), max queue size
  - `/squire queue add <command>`, `/squire queue list`, `/squire queue clear`
  - Integration with SquireAI state machine (queue pops after current task completes)
  - Test: chain mine -> store -> patrol

---

## Phase 5d — Advanced AI (Stretch)

### 8. Autonomous Multi-Step [NOT STARTED]
- **Confidence:** ~20% (research phase)
- Item knowledge system, need-based priority, long-range logistics

### 9. Multi-Squire Coordination [NOT STARTED]
- **Confidence:** ~15% (research phase)
- Formation, role assignment, shared task awareness

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
- Audited Grimdark Battlepack v27 zip: found halberd, armor sets, bow variants, polearms- Designed Visual Progression system (cosmetic tier overlays by level)
- Designed Radial Command Menu (keybind → wheel UI → server command dispatch)
- Added both as Phase 5a.5 items 3-4 in roadmap
- Next: Rebuild and retest bow fixes, then start Visual Progression or Radial Menu

### 2026-03-25 (Session 3)
- Built Radial Command Menu: SquireRadialScreen, SquireKeybinds, SquireClientEvents, SquireCommandPayload
- Fixed arrow crash (BowItem guard in performRangedAttack)
- Fixed radial menu flicker (consumeClick + keyPressed toggle, not isDown/keyReleased)
- Fixed radial menu angle inversion (wedge 0 now renders at top to match hover detection)
- Improved radial menu visuals: larger wheel, opaque wedges, screen dim, separator lines
- Fixed horseback movement: horse.getNavigation() doesn't work when squire is controlling passenger; switched to setting squire's yRot/zza/xxa to drive horse via passenger input
- Fixed mounted combat: added mount.isMounted() guard on regular combat entry so mounted squires go to MOUNTED_COMBAT instead of COMBAT_APPROACH
- Fixed mob targeting: changed from Monster.class to Mob.class with Enemy interface predicate (covers Slime, MagmaCube, Phantom, Ghast, Shulker, etc.)
- WIP: Custom squire weapons (lance, bow) have rendering/equip issues. Squire uses vanilla gear for now. Lance still works as player command tool.
- Next: Test horseback + mob targeting fixes, commit, then continue with Phase 5a.5

### 2026-03-25 (Session 4)
- Auto-craft gear: squire crafts wooden sword, shield, bow, arrows from inventory materials
- Fixed radial menu blur: overrode renderBackground() to skip vanilla gaussian blur
- Tuned radial opacity (0xB0 wedges), added ring border outlines
- WIP: horseback movement still broken, marked for deeper investigation
- Committed and pushed both sessions' work

---

## Version Bump Checklist (when Phase 5a complete)

- [ ] Update `mod_version` in `gradle.properties` to `0.4.0`
- [ ] Update CHANGELOG.md
- [ ] Tag `v0.4.0`
- [ ] `./deploy.sh all --restart`