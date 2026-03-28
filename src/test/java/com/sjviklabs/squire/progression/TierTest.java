package com.sjviklabs.squire.progression;

import com.sjviklabs.squire.entity.SquireTier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TierTest {

    @Test
    void level0IsTier0Servant() {
        assertEquals(SquireTier.SERVANT, SquireTier.forLevel(0));
        assertEquals(SquireTier.SERVANT, SquireTier.forLevel(1));
        assertEquals(SquireTier.SERVANT, SquireTier.forLevel(4));
    }

    @Test
    void level5IsTier1Apprentice() {
        assertEquals(SquireTier.APPRENTICE, SquireTier.forLevel(5));
        assertEquals(SquireTier.APPRENTICE, SquireTier.forLevel(9));
    }

    @Test
    void level10IsTier2Squire() {
        assertEquals(SquireTier.SQUIRE, SquireTier.forLevel(10));
        assertEquals(SquireTier.SQUIRE, SquireTier.forLevel(19));
    }

    @Test
    void level20IsTier3Knight() {
        assertEquals(SquireTier.KNIGHT, SquireTier.forLevel(20));
        assertEquals(SquireTier.KNIGHT, SquireTier.forLevel(29));
    }

    @Test
    void level30IsTier4Champion() {
        assertEquals(SquireTier.CHAMPION, SquireTier.forLevel(30));
    }

    @Test
    void canFightRequiresTier1() {
        assertFalse(SquireTier.SERVANT.canFight());
        assertTrue(SquireTier.APPRENTICE.canFight());
        assertTrue(SquireTier.KNIGHT.canFight());
    }

    @Test
    void canMineRequiresTier1() {
        assertFalse(SquireTier.SERVANT.canMine());
        assertTrue(SquireTier.APPRENTICE.canMine());
    }

    @Test
    void canMountRequiresTier3() {
        assertFalse(SquireTier.SQUIRE.canMount());
        assertTrue(SquireTier.KNIGHT.canMount());
        assertTrue(SquireTier.CHAMPION.canMount());
    }

    @Test
    void canMountedCombatRequiresTier4() {
        assertFalse(SquireTier.KNIGHT.canMountedCombat());
        assertTrue(SquireTier.CHAMPION.canMountedCombat());
    }

    @Test
    void canRangedRequiresTier2() {
        assertFalse(SquireTier.APPRENTICE.canRanged());
        assertTrue(SquireTier.SQUIRE.canRanged());
    }

    @Test
    void canFarmAlwaysTrue() {
        assertTrue(SquireTier.SERVANT.canFarm());
        assertTrue(SquireTier.CHAMPION.canFarm());
    }

    @Test
    void canFishAlwaysTrue() {
        assertTrue(SquireTier.SERVANT.canFish());
        assertTrue(SquireTier.CHAMPION.canFish());
    }

    @Test
    void canPatrolRequiresTier1Level8() {
        assertFalse(SquireTier.canPatrolAtLevel(7));
        assertTrue(SquireTier.canPatrolAtLevel(8));
    }

    @Test
    void backpackSlotsScaleByTier() {
        assertEquals(9, SquireTier.SERVANT.getBackpackSlots());
        assertEquals(18, SquireTier.APPRENTICE.getBackpackSlots());
        assertEquals(27, SquireTier.SQUIRE.getBackpackSlots());
        assertEquals(36, SquireTier.KNIGHT.getBackpackSlots());
        assertEquals(36, SquireTier.CHAMPION.getBackpackSlots());
    }
}
