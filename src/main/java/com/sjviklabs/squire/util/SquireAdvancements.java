package com.sjviklabs.squire.util;

import com.sjviklabs.squire.SquireMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants custom advancements to the squire's owner.
 * Advancements use "minecraft:impossible" trigger and are
 * granted programmatically when conditions are met.
 */
public final class SquireAdvancements {

    private SquireAdvancements() {}

    public static void grantSummon(ServerPlayer player) {
        grant(player, "summon_squire");
    }

    public static void grantFirstKill(ServerPlayer player) {
        grant(player, "first_blood");
    }

    public static void grantLevel(ServerPlayer player, int level) {
        if (level >= 5) grant(player, "level_5");
        if (level >= 10) grant(player, "level_10");
        if (level >= 20) grant(player, "level_20");
        if (level >= 30) grant(player, "level_30");
    }

    private static void grant(ServerPlayer player, String name) {
        var advancements = player.server.getAdvancements();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, name);
        AdvancementHolder holder = advancements.get(id);
        if (holder == null) return;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) return;

        // Grant all remaining criteria
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}