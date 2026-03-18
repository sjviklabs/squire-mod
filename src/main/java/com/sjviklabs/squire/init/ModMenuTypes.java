package com.sjviklabs.squire.init;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.inventory.SquireMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SquireMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SquireMenu>> SQUIRE_MENU =
            MENUS.register("squire_menu", () -> IMenuTypeExtension.create(SquireMenu::new));
}
