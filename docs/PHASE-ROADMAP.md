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

### Squire's Shield
- [x] Custom placeholder texture
- [x] Functionally identical to vanilla shield
- [x] Crafting recipe: iron + planks + Squire's Crest
- [x] Usable by both player and squire

### Squire's Armor (4-piece set)
- [x] Helmet, chestplate, leggings, boots
- [x] Placeholder item textures + armor layer textures (64x32)
- [x] Stats: between iron (15 def, 0 tough) and diamond (20 def, 2 tough) — 18 defense, 1.5 toughness
- [x] **Set bonus (squire only):** Regen I every 10s when all 4 pieces equipped
- [x] Players can wear for fashion + decent protection, no set bonus for players
- [x] Crafting recipes: iron + leather + gold accents

### Player-Sized Entity
- [x] Full Steve dimensions: 1.8 blocks tall, 0.6 wide (confirmed from Phase 1)
- [x] Armor renders via HumanoidArmorLayer (wired in SquireRenderer)

### Mounted Combat
- [x] `MountHandler` — find saddled horse within range, mount/dismount
- [x] `/squire mount` — assign nearest saddled horse (persist horse UUID in NBT)
- [x] `/squire dismount` — dismount and release horse
- [x] Squire auto-mounts assigned horse when entering patrol or follow mode
- [x] Horse persists across restarts via UUID lookup
- [x] Mounted movement: squire controls the horse via direct navigation
- [x] Halberd reach extended while mounted
- [x] MOUNTING, MOUNTED_IDLE, MOUNTED_FOLLOW, MOUNTED_COMBAT states
- [x] Config: horseSearchRange, autoMountEnabled

### Signpost Patrol System
- [x] **Squire's Signpost** — new craftable block
  - Right-click shows config, shift+right-click cycles mode
  - Modes: WAYPOINT, GUARD_POST, PERIMETER
  - Placeholder block texture
- [x] **Perimeter patrol:**
  - PatrolHandler with multi-waypoint route, looping walk
  - Wait timer at each waypoint, random head turns
- [x] **Guard post:**
  - Single-point guard mode via PatrolHandler
- [x] `/squire patrol start [pos]` / `/squire patrol stop`
- [x] Signpost data stored in block entity (mode, links, assigned owner UUID, wait ticks)
- [x] Config: patrolDefaultWait, patrolMaxRouteLength

### Chest Interaction
- [x] `/squire store [pos]` — deposit inventory into nearest/targeted chest
- [x] `/squire fetch [item]` — withdraw specific item type from chest
- [x] Squire walks to chest, opens it (sound), transfers items
- [x] Deposit skips equipment slots, fetch uses optional item filter
- [x] Works with any `BaseContainerBlockEntity`
- [x] Ability-gated via CHEST_DEPOSIT (Lv20)
- [x] Config: chestSearchRange

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
