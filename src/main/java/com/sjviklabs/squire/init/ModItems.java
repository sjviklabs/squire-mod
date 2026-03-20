package com.sjviklabs.squire.init;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.item.SquireCrestItem;
import com.sjviklabs.squire.item.SquireLanceItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SquireMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SquireMod.MODID);

    // Registry ID stays "squire_badge" for world compatibility
    public static final DeferredItem<SquireCrestItem> SQUIRE_CREST = ITEMS.register("squire_badge",
            () -> new SquireCrestItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<SquireLanceItem> SQUIRE_LANCE = ITEMS.register("squire_lance",
            () -> new SquireLanceItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SQUIRE_TAB =
            CREATIVE_MODE_TABS.register("squire_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.squire"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> SQUIRE_CREST.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(SQUIRE_CREST.get());
                        output.accept(SQUIRE_LANCE.get());
                    }).build());
}
