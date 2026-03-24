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
- [x] **Tool visual during mining** — equip check overwrites tool mid-mine. Skip `runFullEquipCheck` when in MINING states.
- [x] **Mining speed parity** — add efficiency enchantment bonus and canHarvestBlock check (30 vs 100 divisor) to match vanilla player break speed.

### Custom Naming
- [x] `/squire name <text>` command (max 32 chars)
- [x] Persist name in NBT (vanilla CustomName), display on nameplate
- [x] Color/formatting support (`&` codes parsed to styled Components)

### Male/Female Appearance
- [x] Two skin textures: wide arms (male) and slim arms (female/Alex model)
- [x] `/squire appearance <male|female>` command
- [x] Persist in NBT, sync to client via SynchedEntityData
- [x] PlayerModel `slim` boolean toggle on the renderer (dual-model swap)

### Chat Lines (Contextual Flavor)
- [x] Triggered on state transitions via ChatHandler:
  - Combat start: "Hostile ahead!", "I'll handle this.", etc.
  - Low health/eating: "I'm hurting...", "Need food!"
  - Kill: "Got 'em.", "That's another one.", "Target down."
  - Idle (30s+): "...", "Nice day.", "Standing by."
  - Level up: "I feel stronger! Level X!"
  - Torch placed: "Getting dark.", "Let me light the way."
- [x] Configurable: chatLinesEnabled in config, 10s global cooldown
- [x] Owner-only visibility (system chat, not global)

### Auto-Torching
- [x] Check light level at squire's position while in IDLE/FOLLOW mode
- [x] Place torch from inventory when block light <= configurable threshold (default 7)
- [x] Cooldown between placements (60 ticks / 3s, anti-cluster near existing torches)
- [x] Configurable: torchLightThreshold, torchCooldownTicks
- [x] Level-gated via AUTO_TORCH ability (Lv5)

### Idle Behaviors
- [x] Head turning (30% chance random look when no player nearby)
- [x] Occasional sitting after 60s idle (sits down, stands up when interrupted)
- [x] Look at nearest player within 8 blocks
- [x] Resume standing when state changes or owner moves

### Inventory Screen Redesign
- [x] Entity preview panel (rendered squire with current gear, rotatable)
- [x] Equipment slots on left (armor) and right (weapons) flanking backpack grid
- [x] Stats display: HP bar, XP bar, level, current mode
- [x] Locked rows shown as greyed with "Lv.X" labels — visible but inaccessible
- [x] Custom background (fully programmatic — dark UI with colored bars, no vanilla texture)

### Leveled Backpack
- [x] Lv1-9: Satchel — 9 slots (1 row)
- [x] Lv10-19: Pack — 18 slots (2 rows)
- [x] Lv20-29: Knapsack — 27 slots (3 rows)
- [x] Lv30: War Chest — 36 slots (4 rows)
- [x] 3D model via `BackpackLayer` — tier-scaled colored box on body, swaps size/color per tier
- [x] Dynamic `MenuType` with slot count based on current tier
- [x] Persist tier + contents in NBT (handle tier upgrades gracefully — don't lose items)

### Sound Effects
- [x] Eating: crunch every 4 ticks + burp on finish
- [x] Combat: `PLAYER_ATTACK_STRONG` on hit, `PLAYER_ATTACK_CRIT` on kill
- [x] Equipment: `ARMOR_EQUIP_IRON` for armor, `ARMOR_EQUIP_GENERIC` for weapons
- [x] Ambient: villager "hmm" (25% chance, 0.6 volume)
- [x] Hurt/death: `PLAYER_HURT`, `PLAYER_DEATH` (from Phase 1)

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
