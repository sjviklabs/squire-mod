package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * Contextual flavor text — the squire says things based on what's happening.
 * Messages are owner-only (actionbar/system chat), never global.
 * Frequency-capped to prevent spam.
 */
public class ChatHandler {

    private static final Random RNG = new Random();

    // Cooldown in ticks between chat messages (prevents spam)
    private static final int GLOBAL_COOLDOWN = 200; // 10 seconds

    private final SquireEntity squire;
    private int cooldown;

    public ChatHandler(SquireEntity squire) {
        this.squire = squire;
    }

    /** Tick the cooldown. Call every server tick. */
    public void tick() {
        if (cooldown > 0) cooldown--;
    }

    // ---- Context triggers (call from handlers/transitions) ----

    public void onCombatStart() {
        say(COMBAT_START);
    }

    public void onKill() {
        say(KILL);
    }

    public void onLowHealth() {
        say(LOW_HEALTH);
    }

    public void onMiningStart() {
        say(MINING_START);
    }

    public void onLevelUp(int newLevel) {
        sayForced("I feel stronger! Level " + newLevel + "!");
    }

    public void onEating() {
        say(EATING);
    }

    public void onIdleLong() {
        say(IDLE);
    }

    public void onTorchPlaced() {
        say(TORCH);
    }

    // ---- Internal ----

    private void say(String[] pool) {
        if (!SquireConfig.chatLinesEnabled.get()) return;
        if (cooldown > 0) return;

        String msg = pool[RNG.nextInt(pool.length)];
        sendToOwner(msg);
        cooldown = GLOBAL_COOLDOWN;
    }

    /** Bypasses cooldown check — for important events like level up. */
    private void sayForced(String msg) {
        if (!SquireConfig.chatLinesEnabled.get()) return;
        sendToOwner(msg);
        cooldown = GLOBAL_COOLDOWN;
    }

    private void sendToOwner(String msg) {
        if (squire.getOwner() instanceof ServerPlayer owner) {
            String name = squire.hasCustomName()
                    ? squire.getCustomName().getString()
                    : "Squire";
            owner.sendSystemMessage(Component.literal("<" + name + "> " + msg));
        }
    }

    // ---- Message pools ----

    private static final String[] COMBAT_START = {
            "Hostile ahead!",
            "I'll handle this.",
            "Stay behind me!",
            "Weapon ready!",
            "For the cause!",
    };

    private static final String[] KILL = {
            "Got 'em.",
            "That's another one.",
            "Target down.",
            "No threat now.",
            "Too easy.",
    };

    private static final String[] LOW_HEALTH = {
            "I'm hurting...",
            "Need food!",
            "I can't take much more.",
            "Ugh... that one stung.",
            "A little help?",
    };

    private static final String[] MINING_START = {
            "Mining away.",
            "On it.",
            "This rock won't mine itself.",
            "Breaking ground.",
    };

    private static final String[] EATING = {
            "Mmm, that hit the spot.",
            "Thanks for the food.",
            "Needed that.",
    };

    private static final String[] IDLE = {
            "...",
            "Nice day.",
            "Standing by.",
            "Anything you need?",
            "I could use some action.",
            "Quiet out here.",
    };

    private static final String[] TORCH = {
            "Getting dark.",
            "Let me light the way.",
            "Torch down.",
    };
}
