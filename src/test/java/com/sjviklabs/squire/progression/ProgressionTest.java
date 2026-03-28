package com.sjviklabs.squire.progression;

import com.sjviklabs.squire.util.SquireAbilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {

    @Test
    void level0At0XP() {
        assertEquals(0, SquireAbilities.calculateLevel(0, 100, 30));
    }

    @Test
    void level1At100XP() {
        assertEquals(1, SquireAbilities.calculateLevel(100, 100, 30));
    }

    @Test
    void level5At2500XP() {
        assertEquals(5, SquireAbilities.calculateLevel(2500, 100, 30));
    }

    @Test
    void level10At10000XP() {
        assertEquals(10, SquireAbilities.calculateLevel(10000, 100, 30));
    }

    @Test
    void level30At90000XP() {
        assertEquals(30, SquireAbilities.calculateLevel(90000, 100, 30));
    }

    @Test
    void levelCapsAtMax() {
        assertEquals(30, SquireAbilities.calculateLevel(999999, 100, 30));
    }

    @Test
    void negativeXPReturns0() {
        assertEquals(0, SquireAbilities.calculateLevel(-100, 100, 30));
    }

    @Test
    void zeroBaseXPReturns0() {
        assertEquals(0, SquireAbilities.calculateLevel(1000, 0, 30));
    }

    @Test
    void xpForLevelRoundTrips() {
        for (int level = 0; level <= 30; level++) {
            int xp = SquireAbilities.xpForLevel(level, 100);
            int computed = SquireAbilities.calculateLevel(xp, 100, 30);
            assertEquals(level, computed, "Level " + level + " at " + xp + " XP");
        }
    }

    @Test
    void xpForLevelIsQuadratic() {
        assertEquals(0, SquireAbilities.xpForLevel(0, 100));
        assertEquals(100, SquireAbilities.xpForLevel(1, 100));
        assertEquals(400, SquireAbilities.xpForLevel(2, 100));
        assertEquals(900, SquireAbilities.xpForLevel(3, 100));
        assertEquals(90000, SquireAbilities.xpForLevel(30, 100));
    }

    @Test
    void midLevelXPResolvesCorrectly() {
        // 150 XP is between Lv1 (100) and Lv2 (400) — should be Lv1
        assertEquals(1, SquireAbilities.calculateLevel(150, 100, 30));
        // 399 XP is just under Lv2 (400) — should be Lv1
        assertEquals(1, SquireAbilities.calculateLevel(399, 100, 30));
        // 400 XP is exactly Lv2
        assertEquals(2, SquireAbilities.calculateLevel(400, 100, 30));
    }
}
