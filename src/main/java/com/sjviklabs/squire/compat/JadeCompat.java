package com.sjviklabs.squire.compat;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft compatibility for Jade/WTHIT/HWYLA tooltip mods.
 * Provides formatted info lines about a squire entity.
 *
 * Since we can't compile against Jade without adding a dependency,
 * this class produces tooltip data that the entity's hover text
 * system can use. Jade automatically picks up entity display names
 * and custom name tags, but we enhance the squire's built-in
 * tooltip by overriding getAddEntityPacket info.
 *
 * The primary integration point is SquireEntity returning rich
 * info via standard Minecraft mechanisms that Jade reads:
 * - Custom name (entity name + mode)
 * - Health bar (Jade auto-renders this for living entities)
 *
 * For deeper Jade integration (custom panels), a Jade plugin
 * would need to be registered. This class provides the data
 * formatting used by both our renderer and any future plugin.
 */
public final class JadeCompat {

    private JadeCompat() {}

    /**
     * Build tooltip lines for a squire entity. Used by the renderer's
     * name tag and available for future Jade/WTHIT plugin registration.
     *
     * @return list of formatted components describing the squire's state
     */
    public static List<Component> buildTooltipLines(SquireEntity squire) {
        List<Component> lines = new ArrayList<>();

        // Level
        int level = squire.getSquireLevel();
        lines.add(Component.literal("Level " + level)
                .withStyle(level >= 30 ? ChatFormatting.GOLD :
                        level >= 20 ? ChatFormatting.LIGHT_PURPLE :
                                level >= 10 ? ChatFormatting.AQUA : ChatFormatting.GREEN));

        // Mode
        String mode = SquireEntity.modeName(squire.getSquireMode());
        ChatFormatting modeColor = switch (squire.getSquireMode()) {
            case SquireEntity.MODE_FOLLOW -> ChatFormatting.GREEN;
            case SquireEntity.MODE_GUARD -> ChatFormatting.YELLOW;
            case SquireEntity.MODE_STAY -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
        lines.add(Component.literal("Mode: " + mode).withStyle(modeColor));

        // Health
        float hp = squire.getHealth();
        float maxHp = squire.getMaxHealth();
        float pct = hp / maxHp;
        ChatFormatting hpColor = pct > 0.5f ? ChatFormatting.GREEN :
                pct > 0.2f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        lines.add(Component.literal(String.format("HP: %.0f/%.0f", hp, maxHp)).withStyle(hpColor));

        // Owner
        if (squire.getOwner() != null) {
            lines.add(Component.literal("Owner: " + squire.getOwner().getName().getString())
                    .withStyle(ChatFormatting.GRAY));
        }

        return lines;
    }

    /**
     * Build a single-line summary for compact tooltip display.
     */
    public static Component buildCompactLine(SquireEntity squire) {
        int level = squire.getSquireLevel();
        String mode = SquireEntity.modeName(squire.getSquireMode());
        float hp = squire.getHealth();
        float maxHp = squire.getMaxHealth();

        MutableComponent line = Component.literal("Lv." + level + " ")
                .withStyle(ChatFormatting.AQUA);
        line.append(Component.literal(mode + " ")
                .withStyle(ChatFormatting.YELLOW));
        line.append(Component.literal(String.format("%.0f/%.0f HP", hp, maxHp))
                .withStyle(ChatFormatting.GREEN));

        return line;
    }
}