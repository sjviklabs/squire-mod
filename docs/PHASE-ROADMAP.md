# Squire Mod — Phase Roadmap

## Vision

A true player-equivalent companion entity for Minecraft. The squire walks, fights, equips gear, eats, follows instructions, patrols your base on horseback, and eventually performs autonomous multi-step tasks. Not a pet. A knight.

---

## Phase 1: Walk, Fight, Equip, Follow — COMPLETE (v0.1.0)

**Goal:** Functional companion that moves like a player and fights like a player. No teleporting.

**Shipped:**
- TamableAnimal base with player model (HumanoidMobRenderer + PlayerModel)
- Follow owner (walk/sprint, speed matching, no teleport)
- Combat (owner-based targeting, weapon damage + enchantments, arm swing, owner leash)
- Auto-equip armor, weapons, shields from inventory
- Eat food when health is low
- FOLLOW / STAY modes (shift+right-click toggle)
- Recall mechanic (crest right-click)
- Death drops crest + inventory, chat notification with coordinates
- 1 squire per player (configurable)
- Admin commands (/squire list, kill, limit)
- Config file (all magic numbers externalized)
- Custom 64x64 skin texture
- Health bar nameplate
- CI/CD pipeline (GitHub Actions)

---

## Phase 2: Mine, Place, Progress — COMPLETE (v0.2.0)

**Goal:** Squire interacts with the world like a player. Progression system with level-gated abilities.

**Shipped:**
- Tick-rate state machine (replaced vanilla Goals)
- Handler composition (CombatHandler, MiningHandler, FollowHandler, ProgressionHandler, etc.)
- Block breaking with crack animation and sound
- Block placing from inventory
- Tool selection (best tool for block type)
- Area clearing with particle preview, confirm/cancel, top-down queue
- Chunk loading during area clear
- Progression system (quadratic XP curve, max level 30)
- 6 level-gated abilities (fire res, ranged, shield, thorns, lifesteal, undying)
- Ranged combat (bow + arrow, optimal distance switching)
- Guard mode (3-mode cycle: Follow/Guard/Stay)
- Proactive aggro (auto-target hostiles near owner)
- Activity logging (/squire log)
- Custom nameplate with health bar + level display

---

## Phase 3: Personality & Polish — v0.3.0

**Goal:** The squire feels alive. Bug fixes, QOL, and immersion features that make people want to keep the squire around.

### Bug Fixes
- [ ] **Tool visual during mining** — equip check overwrites tool mid-mine. Skip `runFullEquipCheck` when in MINING states.
- [ ] **Mining speed parity** — add efficiency enchantment bonus and canHarvestBlock check (30 vs 100 divisor) to match vanilla player break speed.

### Custom Naming
- [ ] `/squire name <text>` command
- [ ] Persist name in NBT, display on nameplate
- [ ] Color/formatting support (Minecraft formatting codes)

### Male/Female Appearance
- [ ] Two skin textures: wide arms (male) and slim arms (female/Alex model)
- [ ] `/squire appearance <male|female>` command
- [ ] Persist in NBT, sync to client for correct model rendering
- [ ] PlayerModel `slim` boolean toggle on the renderer

### Chat Lines (Contextual Flavor)
- [ ] Triggered on state transitions (already have the hooks):
  - Combat start: "Hostile ahead!", "I'll handle this."
  - Low health: "I'm hurting...", "Need food!"
  - Kill: "Got 'em.", "That's another one."
  - Mining: "Mining away.", "This rock is tough."
  - Idle (30s+): "...", "Nice day.", "Standing by."
  - Level up: "I feel stronger!"
- [ ] Configurable: enable/disable in config, message frequency cap
- [ ] Owner-only visibility (not global chat spam)

### Auto-Torching
- [ ] Check light level around owner's position while in FOLLOW mode
- [ ] Place torch from inventory when light < configurable threshold (default 7)
- [ ] Cooldown between placements (don't carpet-bomb torches)
- [ ] Configurable: enable/disable, light threshold, placement interval

### Idle Behaviors
- [ ] Head turning (look at random nearby points)
- [ ] Occasional sitting after extended idle
- [ ] Look at owner when owner looks at squire
- [ ] Resume standing when mode changes or owner moves

### Inventory Screen Redesign
- [ ] Entity preview panel (rendered squire with current gear, rotatable)
- [ ] Equipment slots on left (armor) and right (weapons) flanking backpack grid
- [ ] Stats display: HP bar, XP bar, level, current mode, active ability
- [ ] Locked rows shown as greyed/X'd — visible but inaccessible
- [ ] Custom background texture (not vanilla chest skin)

### Leveled Backpack
- [ ] Lv1-9: Satchel — 9 slots (1 row). Small belt pouch on 3D model.
- [ ] Lv10-19: Pack — 18 slots (2 rows). Medium bag on back.
- [ ] Lv20-29: Knapsack — 27 slots (3 rows). Full backpack, visible on model.
- [ ] Lv30: War Chest — 36 slots (4 rows). Large chest-pack with bedroll.
- [ ] 3D model via `RenderLayer` — swap based on `backpackTier` synced data
- [ ] Dynamic `MenuType` with slot count based on current tier
- [ ] Persist tier + contents in NBT (handle tier upgrades gracefully — don't lose items)

---

## Phase 4: The Knight — v0.4.0

**Goal:** Custom equipment, mounted combat, and patrol system. The squire becomes a proper knight defending your colony.

### Squire's Lance (Weapon)
- [ ] Dual-purpose item:
  - **Sneaking + use:** area selection tool (current behavior)
  - **Normal use:** melee weapon with extended reach
- [ ] Stats: 4.5 block reach (on foot), 6.0 (mounted). Slow attack speed (0.8). 5 base damage, 9 mounted charge.
- [ ] Mounted charge: speed-based bonus damage when riding a horse above sprint threshold
- [ ] Post-charge cooldown (lance "lowers", 1-2 second swing lockout)
- [ ] Durability: 800 (breaks faster than diamond sword)
- [ ] Crafting recipe: iron + stick + banner (TBD)
- [ ] Usable by both player and squire

### Squire's Shield
- [ ] Custom texture with heraldry/crest design
- [ ] Functionally identical to vanilla shield
- [ ] Custom model with crest emblem
- [ ] Crafting recipe: iron + planks + Squire's Crest
- [ ] Usable by both player and squire

### Squire's Armor (4-piece set)
- [ ] Helmet, chestplate, leggings, boots
- [ ] Custom model + textures (knight aesthetic, not just recolored iron)
- [ ] Stats: between iron and diamond tier
- [ ] **Set bonus (squire only):** when all 4 pieces equipped, grant a bonus (faster regen, +1 reach, or reduced ability cooldowns — TBD)
- [ ] Players can wear for fashion + decent protection, no set bonus for players
- [ ] Crafting recipes: iron + leather + custom ingredient (TBD)

### Player-Sized Entity
- [ ] Full Steve dimensions: 1.8 blocks tall, 0.6 wide bounding box
- [ ] Verify armor renders correctly at full scale
- [ ] Verify horse mounting position is correct
- [ ] Update collision/pathfinding if needed

### Mounted Combat
- [ ] `MountHandler` — find saddled horse within range, mount/dismount
- [ ] `/squire mount` — assign nearest saddled horse (persist horse UUID in NBT)
- [ ] `/squire dismount` — dismount and release horse
- [ ] Squire auto-mounts assigned horse when entering patrol or follow mode
- [ ] Horse persists across restarts via UUID lookup
- [ ] Mounted movement: squire controls the horse (not passenger physics)
- [ ] Lance + shield while mounted — mainhand lance, offhand shield
- [ ] Charge attack: detect speed threshold, apply lance damage multiplier
- [ ] If horse dies: squire dismounts, continues on foot, switches to normal weapon
- [ ] Horse follows squire when dismounted (leash-like behavior)

### Signpost Patrol System
- [ ] **Squire's Signpost** — new craftable block
  - Right-click to open config GUI
  - Modes: Guard (defend this point), Patrol (walk a route), Rally (gather point)
  - Visual: wooden post with hanging sign, changes icon based on mode
- [ ] **Perimeter patrol:**
  - Place 2+ signposts with Patrol mode
  - Signposts auto-link in placement order (or manual linking via shift+right-click)
  - Squire walks between posts in sequence, loops back to first
  - Walk speed while patrolling, sprint to engage hostiles
  - Return to patrol route after combat
  - Terrain-following (Y adjusts to ground level, not signpost Y)
- [ ] **Rectangle patrol (shortcut):**
  - Place 2 signposts at diagonal corners
  - Squire calculates 4 corner path and walks the perimeter
  - Particle outline shows the patrol rectangle when placed
- [ ] **Guard post:**
  - Single signpost with Guard mode
  - Squire stands near post, engages hostiles within aggro range
  - Basically current Guard mode but anchored to a block, not a position
- [ ] `/squire patrol` — assign squire to nearest linked signpost network
- [ ] `/squire patrol stop` — return to follow mode
- [ ] Signpost data stored in block entity (mode, links, assigned squire UUID)
- [ ] Squire patrol assignment persisted in NBT

### Chest Interaction
- [ ] `/squire store` — deposit inventory into targeted chest (look-at or nearest)
- [ ] `/squire fetch <item>` — withdraw specific item type from chest
- [ ] Squire walks to chest, opens it (lid animation), transfers items, closes
- [ ] Configurable: deposit all, deposit non-equipment only, deposit specific types
- [ ] Works with any `Container` block (chest, barrel, shulker box)

---

## Phase 5: Autonomous Intelligence — v0.5.0+

**Goal:** Squire operates autonomously with multi-step task planning.

### Farming
- [ ] `/squire farm <from> <to>` — define farmland area (same preview pattern as clear)
- [ ] Till dirt → plant seeds from inventory → wait for growth → harvest → replant
- [ ] Supports: wheat, carrots, potatoes, beetroot (extensible)
- [ ] Deposit harvested crops into assigned chest (if chest interaction available)

### Fishing
- [ ] Idle activity: squire fishes when near water with no threats and no active task
- [ ] Requires fishing rod in inventory
- [ ] Passive food + XP generation
- [ ] Auto-stores catches in inventory

### Task Queue
- [ ] Multiple commands in sequence: "mine this area, then store items in that chest"
- [ ] `/squire queue add <command>` — append to task list
- [ ] `/squire queue clear` — wipe pending tasks
- [ ] `/squire queue list` — show pending tasks
- [ ] Squire completes current task, moves to next automatically

### Autonomous Multi-Step Tasks
- [ ] "Go mine iron, smelt it, bring me ingots"
- [ ] Item knowledge system (what tool for what block, what furnace recipe gives what)
- [ ] Need-based priority (hunger, safety, task completion)
- [ ] Long-range logistics (navigate to distant locations, return)

### Multi-Squire Coordination
- [ ] If maxSquiresPerPlayer > 1, squires spread in formation (not stacking)
- [ ] Role assignment: one patrols, one mines, one farms
- [ ] Shared task awareness (don't mine the same block)

---

## Version Support

- 1.21.1 for all current phases (modpack standard)
- Evaluate 1.21.4/1.22 for Phase 5+ based on community adoption
- NeoForge only (no Fabric port planned)

## Publishing

- **Platforms:** CurseForge + Modrinth + GitHub Releases
- **License:** MIT
- **Versioning:** Semver, filename `squire-neoforge-1.21.1-{version}.jar`
- **CI/CD:** GitHub Actions with ModPublisher Gradle plugin
