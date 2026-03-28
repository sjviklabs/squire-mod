package com.sjviklabs.squire.test;

import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.entity.SquireTier;
import com.sjviklabs.squire.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world GameTests for the Squire mod. Runs via {@code ./gradlew gameTestServer}.
 *
 * <p>These tests verify entity spawn defaults and tier-based feature gates
 * using NeoForge's GameTest framework (Mojang test harness + NeoForge extensions).
 *
 * <p>Must live in the main source set — NeoForge discovers GameTest classes at runtime.
 */
@GameTestHolder("squire")
@PrefixGameTestTemplate(false)
public class SquireGameTests {

    @GameTest(template = "squire:empty")
    public void squireSpawnsWithCorrectDefaults(GameTestHelper helper) {
        SquireEntity squire = helper.spawn(ModEntities.SQUIRE.get(), new BlockPos(1, 2, 1));
        helper.assertTrue(squire.isAlive(), "Squire should be alive after spawn");
        helper.assertTrue(squire.getSquireLevel() == 0, "Squire should start at level 0");
        helper.assertTrue(squire.getTier() == SquireTier.SERVANT, "Squire should start as Servant tier");
        helper.assertTrue(squire.getHealth() > 0, "Squire should have positive health");
        helper.succeed();
    }

    @GameTest(template = "squire:empty")
    public void servantTierHasCorrectGates(GameTestHelper helper) {
        SquireEntity squire = helper.spawn(ModEntities.SQUIRE.get(), new BlockPos(1, 2, 1));
        SquireTier tier = squire.getTier();
        helper.assertTrue(!tier.canFight(), "Servant should not be able to fight");
        helper.assertTrue(tier.canFarm(), "Servant should be able to farm");
        helper.assertTrue(tier.canFish(), "Servant should be able to fish");
        helper.assertTrue(!tier.canMine(), "Servant should not be able to mine");
        helper.assertTrue(!tier.canMount(), "Servant should not be able to mount");
        helper.assertTrue(!tier.canRanged(), "Servant should not have ranged combat");
        helper.assertTrue(!tier.canSweep(), "Servant should not be able to sweep");
        helper.succeed();
    }
}
