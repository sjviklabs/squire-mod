package com.sjviklabs.squire.init;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.block.SignpostBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SquireMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignpostBlockEntity>> SIGNPOST =
            BLOCK_ENTITIES.register("signpost", () ->
                    BlockEntityType.Builder.of(SignpostBlockEntity::new, ModBlocks.SIGNPOST.get()).build(null));
}
