package com.sjviklabs.squire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * with medieval-flavored lore, ability guides, and future hints.
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

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = buildPages();
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("The Squire's Compendium"),
                "Ser Aldric the Faithful",
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

        // === PAGE 1: Title / Intro ===
        pages.add(fp(Component.literal("")
                .append(title("The Squire's\nCompendium\n\n"))
                .append(text("Herein lies the collected\nwisdom of those who\nhave walked beside a\nsworn companion.\n\n"))
                .append(lore("Penned by Ser Aldric\nthe Faithful, Knight of\nthe Old Watch."))
        ));

        // === PAGE 2: Lore - Origins ===
        pages.add(fp(Component.literal("")
                .append(title("Of Their Origins\n\n"))
                .append(text("In ages past, when the\nfirst builders raised\ntheir keeps, there came\nforth souls sworn to\nservice. Neither summoned\nby dark art nor born of\nenchantment, but forged\nby oath and loyalty.\n\n"))
                .append(lore("They do not vanish into\nthin air. They walk."))
        ));

        // === PAGE 3: The Crest ===
        pages.add(fp(Component.literal("")
                .append(title("The Crest\n\n"))
                .append(text("The Crest is thy bond\nto thy squire. Guard it\nwell, for it is the sole\nmeans of command.\n\n"))
                .append(label("Strike ground"))
                .append(text(" Summon\n"))
                .append(label("Strike air"))
                .append(text(" Recall\n"))
                .append(label("Sneak+R block"))
                .append(text(" Mark\n"))
                .append(label("Sneak+L block"))
                .append(text(" Mark\n"))
                .append(label("Sneak+R air"))
                .append(text(" Preview"))
        ));

        // === PAGE 4: Modes of Service ===
        pages.add(fp(Component.literal("")
                .append(title("Modes of Service\n\n"))
                .append(text("Address thy squire with\na rightward hand to bid\nhim change his duty:\n\n"))
                .append(label("Follow"))
                .append(text(" - Walks at thy\n  heel through all peril\n"))
                .append(label("Guard"))
                .append(text(" - Stands watch,\n  blade drawn and ready\n"))
                .append(label("Stay"))
                .append(text(" - Rests and awaits\n  thy return\n\n"))
                .append(lore("Sneak+click for packs."))
        ));

        // === PAGE 5: The Radial Seal ===
        pages.add(fp(Component.literal("")
                .append(title("The Radial Seal\n\n"))
                .append(text("Press the V key whilst\nnear thy squire and a\nwheel of eight commands\nshall appear before thee.\n\n"))
                .append(text("From this seal thou\nmayst change mode, begin\npatrol, store or fetch\ngoods, mount his steed,\nor open his packs.\n\n"))
                .append(lore("Swifter than speech."))
        ));

        // === PAGE 6: Combat - Melee ===
        pages.add(fp(Component.literal("")
                .append(title("The Art of Battle\n\n"))
                .append(text("Thy squire shall draw\nsteel against any fell\ncreature that dares\napproach. He fighteth\nwith sword or axe, and\nraiseth shield 'gainst\nblows most grievous.\n\n"))
                .append(text("He knoweth each foe.\nCreepers he avoideth,\nskeletons he closeth\nswiftly."))
        ));

        // === PAGE 7: Combat - Ranks ===
        pages.add(fp(Component.literal("")
                .append(title("Ranks of Prowess\n\n"))
                .append(text("Through battle doth thy\nsquire grow in strength.\nEach rank unlocketh new\nabilities:\n\n"))
                .append(label("Lv 5"))
                .append(text("  Apprentice rank\n"))
                .append(label("Lv 10"))
                .append(text(" Squire rank\n"))
                .append(label("Lv 20"))
                .append(text(" Knight rank\n"))
                .append(label("Lv 30"))
                .append(text(" Champion rank\n\n"))
                .append(lore("His garb doth change\nwith each rank attained."))
        ));

        // === PAGE 8: Combat - Abilities ===
        pages.add(fp(Component.literal("")
                .append(title("Abilities Unlocked\n\n"))
                .append(label("Apprentice"))
                .append(text(" Lifesteal,\n  ranged awareness\n"))
                .append(label("Squire"))
                .append(text(" Shield mastery,\n  fire resistance\n"))
                .append(label("Knight"))
                .append(text(" Thorns aura,\n  slow falling\n"))
                .append(label("Champion"))
                .append(text(" Totem of\n  Undying, full might\n\n"))
                .append(lore("The Champion fear'th\nneither death nor dark."))
        ));

        // === PAGE 9: Mining ===
        pages.add(fp(Component.literal("")
                .append(title("The Miner's Charge\n\n"))
                .append(text("Bid thy squire break\nstone with /squire mine.\nMark two points and he\nshall clear all blocks\nwithin.\n\n"))
                .append(text("Small quarries begin at\nonce. Great excavations\nrequire thy confirmation.\n\n"))
                .append(lore("He favoureth low ground\nere he reacheth high."))
        ));

        // === PAGE 10: Patrol ===
        pages.add(fp(Component.literal("")
                .append(title("The Patrol Route\n\n"))
                .append(text("Place Signposts upon the\nland and thy squire\nshall walk between them\nin endless vigil.\n\n"))
                .append(text("Each signpost marketh a\nwaypoint. He traverses\nthem in order, then\nreturns to the first.\n\n"))
                .append(lore("On horseback, his patrol\nis swifter still."))
        ));

        // === PAGE 11: Farming ===
        pages.add(fp(Component.literal("")
                .append(title("The Tiller's Art\n\n"))
                .append(text("With /squire farm, mark\na plot of earth. Thy\nsquire shall till, plant\nseeds from his stores,\nand harvest ripe crops.\n\n"))
                .append(text("He tendeth wheat,\npotatoes, carrots, and\nbeetroot with equal\ncare.\n\n"))
                .append(lore("Keep seeds in his pack."))
        ));

        // === PAGE 12: Fishing ===
        pages.add(fp(Component.literal("")
                .append(title("The Angler's Rest\n\n"))
                .append(text("Issue /squire fish and\nthy squire shall seek\nthe nearest water. Rod\nin hand, he casts his\nline and waits.\n\n"))
                .append(text("Cod and salmon fill his\npacks most often, though\nrarer catches may come.\n\n"))
                .append(lore("A patient squire is a\nfed squire."))
        ));

        // === PAGE 13: Task Queue ===
        pages.add(fp(Component.literal("")
                .append(title("The Task Ledger\n\n"))
                .append(text("Thy squire keepeth a\nledger of orders. With\n/squire queue add, thou\nmayst chain commands\ntogether.\n\n"))
                .append(text("When one task endeth,\nhe turneth the page and\nbegins the next. Should\ncombat arise, the ledger\nis set aside until the\nthreat hath passed."))
        ));

        // === PAGE 14: Equipment ===
        pages.add(fp(Component.literal("")
                .append(title("Arms and Armour\n\n"))
                .append(text("Outfit thy squire well.\nSneak+click to open his\npacks and place gear:\n\n"))
                .append(label("Weapon"))
                .append(text(" Sword or axe\n"))
                .append(label("Shield"))
                .append(text(" Squire's shield\n"))
                .append(label("Armour"))
                .append(text(" Helm to boots\n"))
                .append(label("Food"))
                .append(text(" He eateth to heal\n\n"))
                .append(lore("He crafteth a wooden\nsword if given planks."))
        ));

        // === PAGE 15: Safety ===
        pages.add(fp(Component.literal("")
                .append(title("Protections\n\n"))
                .append(text("Thy squire is no fool.\nHe doth not drown and\nfalleth softly from\ngreat heights.\n\n"))
                .append(text("When wounded grievously,\nhe retreateth to heal.\nShould he fall, his\nCrest marketh the spot\nof his demise.\n"))
                .append(lore("Diminished but never\ndefeated."))
        ));

        // === PAGE 16: Tips ===
        pages.add(fp(Component.literal("")
                .append(title("Words of Counsel\n\n"))
                .append(text("Keep victuals in his\npacks, that he may mend\nhimself between battles.\n\n"))
                .append(text("Upgrade his steel as\nthy wealth permits.\nUpon death, he loseth\nsome experience.\n\n"))
                .append(lore("A well-kept squire is\nworth ten hired swords."))
        ));

        // === PAGE 17: Future - Whispers ===
        pages.add(fp(Component.literal("")
                .append(title("Whispers of What\nMay Come\n\n"))
                .append(lore("The scribes speak of\ndays when a squire\nmight think for himself.\nMine ore, smelt it at\nthe furnace, and store\nthe ingots, all without\na single command.\n\n"))
                .append(lore("Such autonomy requireth\ngreat wisdom..."))
        ));

        // === PAGE 18: Future - Brotherhood ===
        pages.add(fp(Component.literal("")
                .append(title("The Brotherhood\n\n"))
                .append(lore("There are rumours of\ncommanders who keep not\none squire, but many.\n\n"))
                .append(lore("They march in formation,\neach knowing his role.\nOne mineth whilst another\nguards. One farmeth\nwhilst another patrols.\n\n"))
                .append(lore("Such coordination hath\nnot yet been achieved."))
        ));

        // === PAGE 19: Closing ===
        pages.add(fp(Component.literal("")
                .append(title("Finis\n\n"))
                .append(text("This volume shall grow\nas thy squire's deeds\ngrow. New chapters await\nthose who press onward.\n\n"))
                .append(text("May thy blade stay\nsharp, thy stores stay\nfull, and thy squire\nstay ever at thy side.\n\n"))
                .append(lore("Walk well, commander."))
        ));

        return pages;
    }

    /** Dark purple bold for section titles. */
    private static Filterable<Component> fp(Component c) {
        return Filterable.passThrough(c);
    }

    /** Dark purple bold for section titles. */
    private static MutableComponent title(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_PURPLE);
    }

    /** Black body text. */
    private static MutableComponent text(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BLACK);
    }

    /** Dark gray for labels and mechanical references. */
    private static MutableComponent label(String s) {
        return Component.literal(s).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Dark aqua italic for lore flavor text and future hints. */
    private static MutableComponent lore(String s) {
        return Component.literal(s).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC);
    }
}
