package com.sjviklabs.squire;

import com.sjviklabs.squire.client.SquireRenderer;
import com.sjviklabs.squire.init.ModEntities;
import com.sjviklabs.squire.init.ModMenuTypes;
import com.sjviklabs.squire.inventory.SquireScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = SquireMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SQUIRE_MENU.get(), SquireScreen::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SQUIRE.get(), SquireRenderer::new);
    }
}
