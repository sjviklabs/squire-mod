package com.sjviklabs.squire.ai.statemachine;

/**
 * All possible states for the squire's tick-rate state machine.
 * Organized by priority layer: survival > combat > follow > work > utility.
 */
public enum SquireAIState {
    // Survival layer (highest priority)
    IDLE,
    EATING,
    FLEEING,            // Phase 2: flee when low health

    // Combat layer
    COMBAT_APPROACH,
    COMBAT_ATTACK,    // Phase 2: separate state when in melee range

    // Follow layer
    FOLLOWING_OWNER,
    SITTING,

    // Work layer (Phase 2: mining + placing)
    MINING_APPROACH,
    MINING_BREAK,
    PLACING_APPROACH,
    PLACING_BLOCK,
    PICKING_UP_ITEM,

    // Utility (lowest priority — Phase 2)
    LOOKING_AROUND,
    WANDERING
}
