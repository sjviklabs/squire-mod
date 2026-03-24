package com.sjviklabs.squire.init;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.item.SquireArmorItem;
import com.sjviklabs.squire.item.SquireCrestItem;
import com.sjviklabs.squire.item.SquireLanceItem;
import com.sjviklabs.squire.item.SquireShieldItem;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SquireMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SquireMod.MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, SquireMod.MODID);

    // ---- Armor Material ----
    // Stats between iron (15 defense, 0 toughness) and diamond (20 defense, 2 toughness)
    // Squire: 18 defense total (3+7+5+3), 1.5 toughness
    public static final Holder<ArmorMaterial> SQUIRE_ARMOR_MATERIAL =
            ARMOR_MATERIALS.register("squire", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.BOOTS, 3);
                        map.put(ArmorItem.Type.LEGGINGS, 5);
                        map.put(ArmorItem.Type.CHESTPLATE, 7);
                        map.put(ArmorItem.Type.HELMET, 3);
                    }),
                    15, // enchantability
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(Items.IRON_INGOT),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "squire")
                    )),
                    1.5F, // toughness
                    0.0F  // knockback resistance
            ));

    // ---- Items ----

    // Registry ID stays "squire_badge" for world compatibility
    public static final DeferredItem<SquireCrestItem> SQUIRE_CREST = ITEMS.register("squire_badge",
            () -> new SquireCrestItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<SquireLanceItem> SQUIRE_LANCE = ITEMS.register("squire_lance",
            () -> new SquireLanceItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(800)
                    .attributes(SquireLanceItem.createAttributes())));

    public static final DeferredItem<SquireShieldItem> SQUIRE_SHIELD = ITEMS.register("squire_shield",
            () -> new SquireShieldItem(new Item.Properties()
                    .durability(336)
                    .stacksTo(1)));

    // Durability uses base multiplier 20 × type constant (helmet=11, chest=16, legs=15, boots=13)
    public static final DeferredItem<SquireArmorItem> SQUIRE_HELMET = ITEMS.register("squire_helmet",
            () -> new SquireArmorItem(SQUIRE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(20))));

    public static final DeferredItem<SquireArmorItem> SQUIRE_CHESTPLATE = ITEMS.register("squire_chestplate",
            () -> new SquireArmorItem(SQUIRE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(20))));

    public static final DeferredItem<SquireArmorItem> SQUIRE_LEGGINGS = ITEMS.register("squire_leggings",
            () -> new SquireArmorItem(SQUIRE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(20))));

    public static final DeferredItem<SquireArmorItem> SQUIRE_BOOTS = ITEMS.register("squire_boots",
            () -> new SquireArmorItem(SQUIRE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(20))));

    // ---- Block Items ----

    public static final DeferredItem<BlockItem> SIGNPOST_ITEM = ITEMS.register("signpost",
            () -> new BlockItem(ModBlocks.SIGNPOST.get(), new Item.Properties()));

    // ---- Creative Tab ----

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SQUIRE_TAB =
            CREATIVE_MODE_TABS.register("squire_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.squire"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> SQUIRE_CREST.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(SQUIRE_CREST.get());
                        output.accept(SQUIRE_LANCE.get());
                        output.accept(SQUIRE_SHIELD.get());
                        output.accept(SQUIRE_HELMET.get());
                        output.accept(SQUIRE_CHESTPLATE.get());
                        output.accept(SQUIRE_LEGGINGS.get());
                        output.accept(SQUIRE_BOOTS.get());
                        output.accept(SIGNPOST_ITEM.get());
                    }).build());
}
