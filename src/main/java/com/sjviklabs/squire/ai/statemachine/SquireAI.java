package com.sjviklabs.squire.ai.statemachine;

import com.sjviklabs.squire.ai.handler.CombatHandler;
import com.sjviklabs.squire.ai.handler.FollowHandler;
import com.sjviklabs.squire.ai.handler.ItemHandler;
import com.sjviklabs.squire.ai.handler.MiningHandler;
import com.sjviklabs.squire.ai.handler.PlacingHandler;
import com.sjviklabs.squire.ai.handler.SurvivalHandler;
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

    public SquireAI(SquireEntity squire) {
        this.squire = squire;
        this.machine = new TickRateStateMachine();
        this.combat = new CombatHandler(squire);
        this.survival = new SurvivalHandler(squire);
        this.follow = new FollowHandler(squire);
        this.items = new ItemHandler(squire);
        this.mining = new MiningHandler(squire);
        this.placing = new PlacingHandler(squire);
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

    public void tick() {
        machine.tick(squire);
    }

    // ================================================================
    // Transition registration — thin wiring layer
    // ================================================================

    private void registerTransitions() {
        registerSittingTransitions();
        registerCombatTransitions();
        registerEatingTransitions();
        registerFollowTransitions();
        registerMiningTransitions();
        registerPlacingTransitions();
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
        machine.addTransition(new AITransition(
                null,
                () -> {
                    SquireAIState state = machine.getCurrentState();
                    if (state == SquireAIState.COMBAT_APPROACH || state == SquireAIState.COMBAT_ATTACK)
                        return false;
                    if (squire.isOrderedToSit()) return false;
                    return combat.hasTarget();
                },
                s -> {
                    combat.start();
                    return SquireAIState.COMBAT_APPROACH;
                },
                1, 10
        ));

        machine.addTransition(new AITransition(
                SquireAIState.COMBAT_APPROACH,
                () -> true,
                combat::tick,
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
                follow::tick,
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

    // ---- Idle cosmetics (priority 50) ----

    private void registerIdleTransitions() {
        machine.addTransition(new AITransition(
                SquireAIState.IDLE,
                () -> !squire.isOrderedToSit(),
                s -> {
                    Player player = s.level().getNearestPlayer(s, 8.0);
                    if (player != null) {
                        s.getLookControl().setLookAt(player, 10.0F,
                                (float) s.getMaxHeadXRot());
                    }
                    return SquireAIState.IDLE;
                },
                20, 50
        ));
    }
}
