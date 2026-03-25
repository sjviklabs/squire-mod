package com.sjviklabs.squire.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Keybind definitions for squire client interactions.
 * Registered via {@code RegisterKeyMappingsEvent} in {@link com.sjviklabs.squire.ClientSetup}.
 */
public final class SquireKeybinds {

    private SquireKeybinds() {}

    /** Category shown in Controls settings */
    private static final String CATEGORY = "key.categories.squire";

    /**
     * Hold this key while looking at a squire to open the radial command menu.
     * Default: R
     */
    public static final KeyMapping RADIAL_MENU = new KeyMapping(
            "key.squire.radial_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY
    );
}