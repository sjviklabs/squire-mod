package com.sjviklabs.squire.ability;

import com.sjviklabs.squire.entity.SquireEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Static helpers for querying squire abilities. Thin layer over the
 * {@link SquireAbility} enum — keeps call sites clean and provides
 * aggregate queries (list unlocked, next unlock, etc.) for UI.
 */
public final class AbilityHelper {

    private AbilityHelper() {}

    /** Check if the squire has a specific ability. */
    public static boolean has(SquireEntity squire, SquireAbility ability) {
        return ability.isUnlockedAt(squire.getSquireLevel());
    }

    /** All abilities the squire has currently unlocked. */
    public static List<SquireAbility> getUnlocked(SquireEntity squire) {
        int level = squire.getSquireLevel();
        List<SquireAbility> result = new ArrayList<>();
        for (SquireAbility ability : SquireAbility.values()) {
            if (ability.isUnlockedAt(level)) {
                result.add(ability);
            }
        }
        return result;
    }

    /** All abilities in a given category that the squire has unlocked. */
    public static List<SquireAbility> getUnlocked(SquireEntity squire, SquireAbility.Category category) {
        int level = squire.getSquireLevel();
        List<SquireAbility> result = new ArrayList<>();
        for (SquireAbility ability : SquireAbility.values()) {
            if (ability.getCategory() == category && ability.isUnlockedAt(level)) {
                result.add(ability);
            }
        }
        return result;
    }

    /**
     * The next ability the squire will unlock, sorted by unlock level.
     * Returns null if all abilities are unlocked.
     */
    @Nullable
    public static SquireAbility getNextUnlock(SquireEntity squire) {
        int level = squire.getSquireLevel();
        return Arrays.stream(SquireAbility.values())
                .filter(a -> !a.isUnlockedAt(level))
                .min(Comparator.comparingInt(SquireAbility::getUnlockLevel))
                .orElse(null);
    }

    /** Number of abilities unlocked at the given level. */
    public static int countUnlocked(int level) {
        int count = 0;
        for (SquireAbility ability : SquireAbility.values()) {
            if (ability.isUnlockedAt(level)) count++;
        }
        return count;
    }

    /** All abilities sorted by unlock level. */
    public static List<SquireAbility> getAllSorted() {
        List<SquireAbility> sorted = new ArrayList<>(Arrays.asList(SquireAbility.values()));
        sorted.sort(Comparator.comparingInt(SquireAbility::getUnlockLevel));
        return sorted;
    }
}
