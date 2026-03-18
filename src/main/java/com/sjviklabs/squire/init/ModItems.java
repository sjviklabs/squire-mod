package com.sjviklabs.squire.init;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.item.SquireBadgeItem;
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

    public static final DeferredItem<SquireBadgeItem> SQUIRE_BADGE = ITEMS.register("squire_badge",
            () -> new SquireBadgeItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SQUIRE_TAB =
            CREATIVE_MODE_TABS.register("squire_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.squire"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> SQUIRE_BADGE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(SQUIRE_BADGE.get());
                    }).build());
}
