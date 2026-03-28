package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AIStateTest {

    @Test
    void allStatesExist() {
        SquireAIState[] states = SquireAIState.values();
        assertTrue(states.length >= 25, "Expected at least 25 states, got " + states.length);
    }

    @Test
    void idleStateExists() {
        assertNotNull(SquireAIState.valueOf("IDLE"));
    }

    @Test
    void combatStatesExist() {
        assertNotNull(SquireAIState.valueOf("COMBAT_APPROACH"));
        assertNotNull(SquireAIState.valueOf("COMBAT_ATTACK"));
        assertNotNull(SquireAIState.valueOf("COMBAT_RANGED"));
    }

    @Test
    void farmingStatesExist() {
        assertNotNull(SquireAIState.valueOf("FARM_APPROACH"));
        assertNotNull(SquireAIState.valueOf("FARM_WORK"));
        assertNotNull(SquireAIState.valueOf("FARM_SCAN"));
    }

    @Test
    void fishingStatesExist() {
        assertNotNull(SquireAIState.valueOf("FISHING_APPROACH"));
        assertNotNull(SquireAIState.valueOf("FISHING_IDLE"));
    }

    @Test
    void mountStatesExist() {
        assertNotNull(SquireAIState.valueOf("MOUNTING"));
        assertNotNull(SquireAIState.valueOf("MOUNTED_IDLE"));
        assertNotNull(SquireAIState.valueOf("MOUNTED_FOLLOW"));
        assertNotNull(SquireAIState.valueOf("MOUNTED_COMBAT"));
    }

    @Test
    void patrolStatesExist() {
        assertNotNull(SquireAIState.valueOf("PATROL_WALK"));
        assertNotNull(SquireAIState.valueOf("PATROL_WAIT"));
    }

    @Test
    void chestStatesExist() {
        assertNotNull(SquireAIState.valueOf("CHEST_APPROACH"));
        assertNotNull(SquireAIState.valueOf("CHEST_INTERACT"));
    }

    @Test
    void survivalStatesExist() {
        assertNotNull(SquireAIState.valueOf("EATING"));
        assertNotNull(SquireAIState.valueOf("FLEEING"));
    }
}
