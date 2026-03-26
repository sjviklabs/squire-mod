package com.sjviklabs.squire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Squire's Guidebook. Right-click opens a written book UI
 * explaining commands, abilities, and progression.
 */
public class SquireGuidebookItem extends Item {

    public SquireGuidebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag flag) {
        tooltipComponents.add(Component.translatable("item.squire.squire_guidebook.tooltip1")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Build a temporary written book and open it
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = buildPages();
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Squire's Guidebook"),
                "A Loyal Companion",
                0,
                pages,
                true
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        player.openItemGui(book, hand);

        return InteractionResultHolder.consume(stack);
    }

    private List<Filterable<Component>> buildPages() {
        List<Filterable<Component>> pages = new ArrayList<>();

        // Page 1: Introduction
        pages.add(fp(Component.literal("")
                .append(bold("Your Squire\n\n"))
                .append(text("A loyal companion who fights, mines, and patrols by your side.\n\n"))
                .append(text("Your squire will never teleport. He walks everywhere, like a true knight."))
        ));

        // Page 2: Basic Commands
        pages.add(fp(Component.literal("")
                .append(bold("Commands\n\n"))
                .append(text("Right-click squire to cycle modes:\n"))
                .append(gray(" Follow")).append(text(" - Follows you\n"))
                .append(gray(" Guard")).append(text(" - Holds position\n"))
                .append(gray(" Stay")).append(text(" - Sits, rests\n\n"))
                .append(text("Shift+right-click opens inventory."))
        ));

        // Page 3: The Crest
        pages.add(fp(Component.literal("")
                .append(bold("The Crest\n\n"))
                .append(text("Your main squire tool:\n\n"))
                .append(gray("R-click ground")).append(text(" Summon\n"))
                .append(gray("R-click air")).append(text(" Recall\n"))
                .append(gray("Snk+R block")).append(text(" Set pos1\n"))
                .append(gray("Snk+L block")).append(text(" Set pos2\n"))
                .append(gray("Snk+R air")).append(text(" Preview area"))
        ));

        // Page 4: Combat
        pages.add(fp(Component.literal("")
                .append(bold("Combat\n\n"))
                .append(text("Fights hostile mobs on sight. Give him a sword and shield.\n\n"))
                .append(text("Level unlocks:\n"))
                .append(gray("Lv 5")).append(text("  Bow combat\n"))
                .append(gray("Lv 10")).append(text(" Lifesteal\n"))
                .append(gray("Lv 15")).append(text(" Shield block\n"))
                .append(gray("Lv 20")).append(text(" Fire resist"))
        ));

        // Page 5: Advanced
        pages.add(fp(Component.literal("")
                .append(bold("Advanced\n\n"))
                .append(gray("Lv 25")).append(text(" Thorns\n"))
                .append(gray("Lv 30")).append(text(" Undying\n\n"))
                .append(text("Auto-crafts a wooden sword and shield from planks + sticks.\n\n"))
                .append(text("Picks up items he walks over."))
        ));

        // Page 6: Mining & Patrol
        pages.add(fp(Component.literal("")
                .append(bold("Mining & Patrol\n\n"))
                .append(text("Use /squire mine to send him mining in a selected area.\n\n"))
                .append(text("Place Patrol Signposts for patrol routes.\n\n"))
                .append(text("He eats food from inventory to heal."))
        ));

        // Page 7: Safety
        pages.add(fp(Component.literal("")
                .append(bold("Safety\n\n"))
                .append(text("Built-in protections:\n\n"))
                .append(text("Won't drown\n"))
                .append(text("Slow falling on drops\n"))
                .append(text("Retreats at low HP\n"))
                .append(text("Unsticks if stuck\n"))
                .append(text("Follows teleports"))
        ));

        // Page 8: Tips
        pages.add(fp(Component.literal("")
                .append(bold("Tips\n\n"))
                .append(text("Keep food in his inventory.\n"))
                .append(text("Upgrade gear as you progress.\n"))
                .append(text("Drops a Crest on death.\n"))
                .append(text("Use radial menu (V key) for quick commands.\n\n"))
                .append(gray("Good luck, commander!"))
        ));

        return pages;
    }

    private static Filterable<Component> fp(Component c) {
        return Filterable.passThrough(c);
    }

    private static MutableComponent bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_PURPLE);
    }

    private static MutableComponent text(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BLACK);
    }

    private static MutableComponent gray(String s) {
        return Component.literal(s).withStyle(ChatFormatting.DARK_GRAY);
    }
}