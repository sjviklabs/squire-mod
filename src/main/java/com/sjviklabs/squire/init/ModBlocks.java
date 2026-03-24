package com.sjviklabs.squire.init;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.block.SignpostBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SquireMod.MODID);

    public static final DeferredBlock<SignpostBlock> SIGNPOST = BLOCKS.register("signpost",
            () -> new SignpostBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));
}
