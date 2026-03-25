package com.sjviklabs.squire.ai.statemachine;

import com.sjviklabs.squire.ai.handler.ChatHandler;
import com.sjviklabs.squire.ai.handler.ChestHandler;
import com.sjviklabs.squire.ai.handler.CombatHandler;
import com.sjviklabs.squire.ai.handler.FollowHandler;
import com.sjviklabs.squire.ai.handler.ItemHandler;
import com.sjviklabs.squire.ai.handler.MiningHandler;
import com.sjviklabs.squire.ai.handler.MountHandler;
import com.sjviklabs.squire.ai.handler.PatrolHandler;
import com.sjviklabs.squire.ai.handler.PlacingHandler;
import com.sjviklabs.squire.ai.handler.SurvivalHandler;
import com.sjviklabs.squire.ai.handler.TorchHandler;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Registers all AI transitions for the squire, bridging entity to tick-rate
 * state machine. Delegates behavior logic to handler classes.
 *
 * Target selection still uses vanilla targetSelector goals (OwnerHurtByTargetGoal,
 * OwnerHurtTargetGoal, HurtByTargetGoal) — they set getTarget() which this
 * class reads. FloatGoal is also kept in the vanilla system.
 *
 * Priority layers: survival (1-9) > combat (10-19) > eating (20-29) >
 * follow (30-39) > pickup (40-49) > cosmetic (50+)
 */
public class SquireAI {

    private final SquireEntity squire;
    private final TickRateStateMachine machine;

    // Handlers own per-behavior state and logic
    private final CombatHandler combat;
    private final SurvivalHandler survival;
    private final FollowHandler follow;
    private final ItemHandler items;
    private final MiningHandler mining;
    private final PlacingHandler placing;
    private final TorchHandler torch;
    private final ChatHandler chat;
    private final MountHandler mount;
    private final ChestHandler chest;
    private final PatrolHandler patrol;
    private int idleTicks;

    public SquireAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine();
        this.combat = new CombatHandler(squire);
        this.survival = new SurvivalHandler(squire);
        this.follow = new FollowHandler(squire);
        this.items = new ItemHandler(squire);
        this.mining = new MiningHandler(squire);
        this.placing = new PlacingHandler(squire);
        this.torch = new TorchHandler(squire);
        this.chat = new ChatHandler(squire);
        this.mount = new MountHandler(squire);
        this.chest = new ChestHandler(squire);
        this.patrol = new PatrolHandler(squire);
        registerTransitions();
    }

    public TickRateStateMachine getMachine() {
        return machine;
    }

    public CombatHandler getCombat() { return combat; }
    public SurvivalHandler getSurvival() { return survival; }
    public FollowHandler getFollow() { return follow; }
    public ItemHandler getItems() { return items; }
    public MiningHandler getMining() { return mining; }
    public PlacingHandler getPlacing() { return placing; }
    public ChatHandler getChat() { return chat; }
    public MountHandler getMount() { return mount; }
    public ChestHandler getChest() { return chest; }
    public PatrolHandler getPatrol() { return patrol; }

    /** Check if the state machine is currently in a specific state. */
    public boolean isInState(SquireAIState state) {
        return machine.getCurrentState() == state;
    }

    /** Convenience: delegates to SquireEntity's ProgressionHandler. */
    public void awardKillXP() { squire.getProgression().addKillXP(); }
    public void awardMineXP() { squire.getProgression().addMineXP(); }

    public void tick() {
        chat.tick();
        if (machine.getCurrentState() != SquireAIState.IDLE) {
            // Stand up from idle sit when entering any active state
            if (idleTicks > 0 && squire.isInSittingPose() && !squire.isOrderedToSit()) {
                squire.setInSittingPose(false);
            }
            idleTicks = 0;
        }
        machine.tick(squire);
    }

    // ================================================================
    // Transition registration — thin wiring layer
    // ================================================================

    private void registerTransitions() {
        registerSittingTransitions();
        registerCombatTransitions();
        registerEatingTransitions();
        registerMountTransitions();
        registerFollowTransitions();
        registerMiningTransitions();
        registerPlacingTransitions();
        registerChestTransitions();
        registerPatrolTransitions();
        registerPickupTransitions();
        registerIdleTransitions();
    }

    // ---- Sitting (global, priority 1) ----

    private void registerSittingTransitions() {
        machine.addTransition(new AITransition(
                null,
                () -> machine.getCurrentState() != SquireAIState.SITTING
                        && squire.isTame()
                        && squire.isOrderedToSit()
                        && !squire.isInWaterOrBubble(),
                s -> {
                    s.getNavigation().stop();
                    s.setInSittingPose(true);
                    return SquireAIState.SITTING;
                },
                1, 1
        ));

        machine.addTransition(new AITransition(
                SquireAIState.SITTING,
                () -> !squire.isOrderedToSit(),
                s -> {
                    s.setInSittingPose(false);
                    return SquireAIState.IDLE;
                },
                1, 1
        ));
    }

    // ---- Combat (global, priority 10) ----

    private void registerCombatTransitions() {
        // Enter combat — choose melee or ranged based on equipment + level
        // Skip if already in combat OR if mounted (mounted combat is handled separately)
        machine.addTransition(new AITransition(
                null,
                () -> {
                    SquireAIState state = machine.getCurrentState();
                    if (state == SquireAIState.COMBAT_APPROACH || state == SquireAIState.COMBAT_ATTACK
                            || state == SquireAIState.COMBAT_RANGED)
                        return false;
                    if (mount.isMounted()) return false;
                    if (squire.isOrderedToSit()) return false;
                    return combat.hasTarget();
                },
                s -> {
                    combat.start();
                    chat.onCombatStart();
                    if (combat.shouldUseRanged()) {
                        return SquireAIState.COMBAT_RANGED;
                    }
                    return SquireAIState.COMBAT_APPROACH;
                },
                1, 10
        ));

        // Melee tick
        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_APPROACH,
                () -> true,
                combat::tick,
                1, 10
        ));

        // Ranged tick
        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_RANGED,
                () -> true,
                combat::tickRanged,
                1, 10
        ));
    }

    // ---- Eating (global, priority 20) ----

    private void registerEatingTransitions() {
        machine.addTransition(new AITransition(
                null,
                () -> {
                    if (machine.getCurrentState() == SquireAIState.EATING) return false;
                    return survival.shouldEat();
                },
                s -> {
                    survival.startEating();
                    chat.onLowHealth();
                    return SquireAIState.EATING;
                },
                20, 20
        ));

        machine.addTransition(new AITransition(
                SquireAIState.EATING,
                () -> !survival.isEating(),
                s -> {
                    survival.reset();
                    return SquireAIState.IDLE;
                },
                1, 19
        ));

        machine.addTransition(new AITransition(
                SquireAIState.EATING,
                survival::isEating,
                survival::tick,
                1, 20
        ));
    }

    // ---- Follow (priority 30) ----

    private void registerFollowTransitions() {
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                follow::shouldFollow,
                s -> {
                    follow.start();
                    return SquireAIState.FOLLOWING_OWNER;
                },
                10, 30
        ));

        machine.addTransition(new AITransition(
                SquireAIState.FOLLOWING_OWNER,
                follow::shouldStop,
                s -> {
                    follow.stop();
                    return SquireAIState.IDLE;
                },
                1, 29
        ));

        machine.addTransition(new AITransition(
                SquireAIState.FOLLOWING_OWNER,
                () -> !follow.shouldStop(),
                s -> {
                    // Auto-torch while following in dark areas
                    torch.tryPlaceTorch();
                    return follow.tick(s);
                },
                1, 30
        ));
    }

    // ---- Mining (priority 35) ----
    // Mining is command-driven: setTarget() on MiningHandler, then state machine takes over.

    private void registerMiningTransitions() {
        // MINING_APPROACH: walk toward target block
        machine.addTransition(new AITransition(
                SquireAIState.MINING_APPROACH,
                () -> !mining.hasTarget(),
                s -> {
                    mining.clearTarget();
                    return SquireAIState.IDLE;
                },
                1, 34
        ));

        machine.addTransition(new AITransition(
                SquireAIState.MINING_APPROACH,
                mining::hasTarget,
                mining::tickApproach,
                1, 35
        ));

        // MINING_BREAK: break the block
        machine.addTransition(new AITransition(
                SquireAIState.MINING_BREAK,
                () -> !mining.hasTarget(),
                s -> {
                    mining.clearTarget();
                    return SquireAIState.IDLE;
                },
                1, 34
        ));

        machine.addTransition(new AITransition(
                SquireAIState.MINING_BREAK,
                mining::hasTarget,
                mining::tickBreak,
                1, 35
        ));
    }

    // ---- Placing (priority 36) ----
    // Command-driven: setTarget() on PlacingHandler, then state machine takes over.

    private void registerPlacingTransitions() {
        // PLACING_APPROACH: walk toward target position
        machine.addTransition(new AITransition(
                SquireAIState.PLACING_APPROACH,
                () -> !placing.hasTarget(),
                s -> {
                    placing.clearTarget();
                    return SquireAIState.IDLE;
                },
                1, 35
        ));

        machine.addTransition(new AITransition(
                SquireAIState.PLACING_APPROACH,
                placing::hasTarget,
                placing::tickApproach,
                1, 36
        ));

        // PLACING_BLOCK: place the block
        machine.addTransition(new AITransition(
                SquireAIState.PLACING_BLOCK,
                () -> !placing.hasTarget(),
                s -> {
                    placing.clearTarget();
                    return SquireAIState.IDLE;
                },
                1, 35
        ));

        machine.addTransition(new AITransition(
                SquireAIState.PLACING_BLOCK,
                placing::hasTarget,
                placing::tickPlace,
                1, 36
        ));
    }

    // ---- Pickup (priority 40) ----

    private void registerPickupTransitions() {
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                () -> {
                    if (squire.isOrderedToSit()) return false;
                    if (squire.getTarget() != null) return false;
                    return items.findClosestItem();
                },
                s -> {
                    items.start();
                    return SquireAIState.PICKING_UP_ITEM;
                },
                40, 40
        ));

        machine.addTransition(new AITransition(
                SquireAIState.PICKING_UP_ITEM,
                () -> !items.hasTarget(),
                s -> {
                    items.stop();
                    return SquireAIState.IDLE;
                },
                1, 39
        ));

        machine.addTransition(new AITransition(
                SquireAIState.PICKING_UP_ITEM,
                items::hasTarget,
                items::tick,
                1, 40
        ));
    }

    // ---- Mount (priority 25) ----

    private void registerMountTransitions() {
        // Auto-mount: if squire has an assigned horse nearby and is idle
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                mount::shouldAutoMount,
                s -> {
                    mount.startApproach();
                    return SquireAIState.MOUNTING;
                },
                40, 25
        ));

        // Mounting approach tick
        machine.addTransition(new AITransition(
                SquireAIState.MOUNTING,
                () -> true,
                mount::tickApproach,
                1, 25
        ));

        // Mounted idle → mounted follow when owner moves away
        machine.addTransition(new AITransition(
                SquireAIState.MOUNTED_IDLE,
                () -> mount.isMounted() && follow.shouldFollow(),
                s -> SquireAIState.MOUNTED_FOLLOW,
                10, 30
        ));

        // Mounted follow tick
        machine.addTransition(new AITransition(
                SquireAIState.MOUNTED_FOLLOW,
                () -> mount.isMounted(),
                mount::tickMountedFollow,
                1, 30
        ));

        // Mounted follow → mounted idle when close to owner
        machine.addTransition(new AITransition(
                SquireAIState.MOUNTED_FOLLOW,
                () -> !mount.isMounted(),
                s -> SquireAIState.IDLE,
                1, 29
        ));

        // Mounted combat entry — global, interrupts any mounted state
        machine.addTransition(new AITransition(
                null,
                () -> {
                    SquireAIState state = machine.getCurrentState();
                    if (state == SquireAIState.MOUNTED_COMBAT) return false;
                    if (!mount.isMounted()) return false;
                    return combat.hasTarget();
                },
                s -> {
                    combat.start();
                    chat.onCombatStart();
                    return SquireAIState.MOUNTED_COMBAT;
                },
                1, 10
        ));

        // Mounted combat tick
        machine.addTransition(new AITransition(
                SquireAIState.MOUNTED_COMBAT,
                () -> mount.isMounted() && combat.hasTarget(),
                mount::tickMountedCombat,
                1, 10
        ));

        // Mounted combat → mounted idle when target dead
        machine.addTransition(new AITransition(
                SquireAIState.MOUNTED_COMBAT,
                () -> !combat.hasTarget(),
                s -> SquireAIState.MOUNTED_IDLE,
                1, 9
        ));

        // Dismounted during any mounted state → IDLE
        machine.addTransition(new AITransition(
                SquireAIState.MOUNTED_IDLE,
                () -> !mount.isMounted(),
                s -> SquireAIState.IDLE,
                1, 24
        ));
    }

    // ---- Chest interaction (priority 37) ----

    private void registerChestTransitions() {
        machine.addTransition(new AITransition(
                SquireAIState.CHEST_APPROACH,
                () -> !chest.hasTarget(),
                s -> {
                    chest.clearTarget();
                    return SquireAIState.IDLE;
                },
                1, 36
        ));

        machine.addTransition(new AITransition(
                SquireAIState.CHEST_APPROACH,
                chest::hasTarget,
                chest::tickApproach,
                1, 37
        ));

        machine.addTransition(new AITransition(
                SquireAIState.CHEST_INTERACT,
                () -> !chest.hasTarget(),
                s -> {
                    chest.clearTarget();
                    return SquireAIState.IDLE;
                },
                1, 36
        ));

        machine.addTransition(new AITransition(
                SquireAIState.CHEST_INTERACT,
                chest::hasTarget,
                chest::tickInteract,
                1, 37
        ));
    }

    // ---- Patrol (priority 32) ----

    private void registerPatrolTransitions() {
        machine.addTransition(new AITransition(
                SquireAIState.PATROL_WALK,
                () -> !patrol.isPatrolling(),
                s -> {
                    patrol.stopPatrol();
                    return SquireAIState.IDLE;
                },
                1, 31
        ));

        machine.addTransition(new AITransition(
                SquireAIState.PATROL_WALK,
                patrol::isPatrolling,
                patrol::tickWalk,
                1, 32
        ));

        machine.addTransition(new AITransition(
                SquireAIState.PATROL_WAIT,
                () -> !patrol.isPatrolling(),
                s -> {
                    patrol.stopPatrol();
                    return SquireAIState.IDLE;
                },
                1, 31
        ));

        machine.addTransition(new AITransition(
                SquireAIState.PATROL_WAIT,
                patrol::isPatrolling,
                patrol::tickWait,
                1, 32
        ));

        // Resume patrol after interruption (combat, eating, etc.)
        // If squire lands in IDLE but patrol flag is still active, re-enter patrol.
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                patrol::isPatrolling,
                s -> SquireAIState.PATROL_WALK,
                1, 32
        ));
    }

    // ---- Idle cosmetics + utility (priority 50) ----

    private void registerIdleTransitions() {
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                () -> !squire.isOrderedToSit(),
                s -> {
                    // Auto-torch: place torch if dark (ability-gated, has its own cooldown)
                    torch.tryPlaceTorch();

                    // Idle chat after 30+ seconds of standing around
                    idleTicks += 20; // this transition runs every 20 ticks
                    if (idleTicks >= 600) { // 30 seconds
                        chat.onIdleLong();
                        idleTicks = 0;
                    }

                    // Idle sit after 60+ seconds — squire sits down until interrupted
                    if (idleTicks >= 1200 && !s.isInSittingPose()) {
                        s.setInSittingPose(true);
                    }

                    // Look at nearby player, or random head turn
                    Player player = s.level().getNearestPlayer(s, 8.0);
                    if (player != null) {
                        s.getLookControl().setLookAt(player, 10.0F,
                                (float) s.getMaxHeadXRot());
                    } else if (s.getRandom().nextFloat() < 0.3F) {
                        // Random head turn — look at a random point within 8 blocks
                        double rx = s.getX() + (s.getRandom().nextDouble() - 0.5) * 16.0;
                        double ry = s.getEyeY() + (s.getRandom().nextDouble() - 0.5) * 4.0;
                        double rz = s.getZ() + (s.getRandom().nextDouble() - 0.5) * 16.0;
                        s.getLookControl().setLookAt(rx, ry, rz);
                    }
                    return SquireAIState.IDLE;
                },
                20, 50
        ));
    }
}
