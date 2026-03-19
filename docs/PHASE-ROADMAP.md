# Squire Mod — Phase Roadmap

## Vision

A true player-equivalent companion entity for Minecraft. The squire walks, fights, equips gear, eats, follows instructions, and eventually performs autonomous multi-step tasks with MineColonies-grade intelligence.

## Phase 1: Walk, Fight, Equip, Follow (Current)

**Goal:** Functional companion that moves like a player and fights like a player. No teleporting.

**Features:**
- TamableAnimal base with player model (HumanoidMobRenderer + PlayerModel)
- Follow owner (walk/sprint, speed matching, no teleport)
- Combat (owner-based targeting, weapon damage + enchantments, arm swing, owner leash)
- Auto-equip armor, weapons, shields from inventory
- Eat food when health is low
- FOLLOW / STAY modes (shift+right-click toggle)
- Recall mechanic (badge right-click)
- Death drops badge + inventory, chat notification with coordinates
- 1 squire per player (configurable)
- Admin commands (/squire list, kill, limit)
- Config file (all magic numbers externalized)
- Custom 64x64 skin texture
- Health bar nameplate
- CI/CD pipeline (GitHub Actions → CurseForge + Modrinth)

**AI System:** Vanilla Goals (adequate for this scope)

## Phase 2: Mine, Place, Progress

**Goal:** Squire can interact with the world like a player — break blocks, place blocks, use tools.

**Features:**
- Block breaking with proper break speed, tool effectiveness, break animation
- Block placing from inventory
- Tool selection (best tool for the job, auto-switch)
- Progression system (XP from combat/mining → levels → better stats)
- Patreon skin system (cosmetic tiers)
- Tick-rate state machine refactor (replace vanilla Goals)
- Handler composition pattern (InventoryHandler, CombatHandler, ToolHandler)

**AI System:** Custom tick-rate state machine (MineColonies-inspired)

## Phase 3: Commands & Containers

**Goal:** Player can give the squire instructions. Squire interacts with storage.

**Features:**
- Command system (GUI radial menu or keybind)
  - "Mine here" — mine blocks in an area
  - "Guard this spot" — patrol and defend a location
  - "Store items" — deposit inventory into a target chest
  - "Follow me" / "Stay here" (expanded modes)
- Container interaction (open chests, deposit/withdraw items)
- Crafting (craft items from inventory, teach recipes)
- Task queue (multiple commands in sequence)

**AI System:** State machine with command interpreter

## Phase 4: Autonomous Intelligence

**Goal:** Squire operates autonomously with MineColonies-grade intelligence.

**Features:**
- Autonomous multi-step tasks ("go mine iron, smelt it, bring me ingots")
- Custom async pathfinding (thread pool, chunk cache, specialized path jobs)
- Item knowledge system (typed requests, tool efficiency queries)
- Need-based priority (hunger, safety, task completion)
- Long-range logistics (navigate to distant locations, return)
- Multi-squire coordination (if multiple squires allowed)

**AI System:** Full state machine + async pathfinding + request system

## Version Support

- 1.21.1 only for Phase 1-2 (modpack standard)
- Evaluate 1.21.4/1.21.5 for Phase 3+ based on community adoption
- NeoForge only (no Fabric port planned)

## Publishing

- **Platforms:** CurseForge + Modrinth + GitHub Releases
- **License:** MIT
- **Versioning:** Semver, filename `squire-neoforge-1.21.1-{version}.jar`
- **CI/CD:** GitHub Actions with ModPublisher Gradle plugin
