package com.sjviklabs.squire.item;

import net.minecraft.world.item.ShieldItem;

/**
 * Custom shield with heraldry texture. Functionally identical to vanilla shield.
 * Subclassed for registration identity and future set bonus detection.
 */
public class SquireShieldItem extends ShieldItem {

    public SquireShieldItem(Properties properties) {
        super(properties);
    }
}
