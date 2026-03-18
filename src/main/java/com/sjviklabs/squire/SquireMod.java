package com.sjviklabs.squire;

import com.sjviklabs.squire.init.ModEntities;
import com.sjviklabs.squire.init.ModItems;
import com.sjviklabs.squire.init.ModMenuTypes;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(SquireMod.MODID)
public class SquireMod {
    public static final String MODID = "squire";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SquireMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Squire mod initializing");

        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
    }
}
