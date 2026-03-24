package com.sjviklabs.squire;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.init.ModBlockEntities;
import com.sjviklabs.squire.init.ModBlocks;
import com.sjviklabs.squire.init.ModEntities;
import com.sjviklabs.squire.init.ModItems;
import com.sjviklabs.squire.init.ModMenuTypes;
import com.sjviklabs.squire.network.SquireModePayload;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(SquireMod.MODID)
public class SquireMod {
    public static final String MODID = "squire";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SquireMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Squire mod initializing");

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, SquireConfig.SPEC);

        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ARMOR_MATERIALS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        // Register network payloads
        modEventBus.addListener(SquireModePayload::register);
    }
}
