package com.sjviklabs.squire.init;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = SquireMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SquireMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SquireEntity>> SQUIRE =
            ENTITY_TYPES.register("squire", () -> EntityType.Builder
                    .of(SquireEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .ridingOffset(-0.7F)
                    .clientTrackingRange(10)
                    .build(SquireMod.MODID + ":squire"));

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SQUIRE.get(), SquireEntity.createAttributes().build());
    }
}
