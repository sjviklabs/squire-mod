package com.sjviklabs.squire.util;

import com.sjviklabs.squire.ai.statemachine.SquireAI;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Force-loads chunks around a squire while it is area clearing AND its owner is online.
 * Uses vanilla chunk forcing (same mechanism as /forceload).
 *
 * Tether model: chunks are only forced while the owner is logged in.
 * When the owner logs off, all chunks are released and the squire pauses naturally
 * (entity stops ticking in unloaded chunks). When the owner returns, chunks are
 * re-forced on the next aiStep cycle.
 */
public final class SquireChunkLoader {

    private static final Map<UUID, Set<ChunkPos>> loadedChunks = new HashMap<>();

    // Cached positions to avoid per-tick allocation
    private static final Map<UUID, ChunkPos> lastSquireChunk = new HashMap<>();
    private static final Map<UUID, ChunkPos> lastTargetChunk = new HashMap<>();

    private SquireChunkLoader() {}

    /**
     * Called every server tick from SquireEntity.aiStep().
     * Forces the squire's chunk + target chunk if area clearing and owner is online.
     * Releases all chunks otherwise.
     */
    public static void tick(SquireEntity squire) {
        if (!(squire.level() instanceof ServerLevel level)) return;

        SquireAI ai = squire.getSquireAI();
        boolean shouldLoad = ai != null
                && ai.getMining().isAreaClearing()
                && squire.getOwner() != null; // owner online = getOwner() non-null

        if (!shouldLoad) {
            release(squire);
            return;
        }

        UUID id = squire.getUUID();
        ChunkPos squireChunk = new ChunkPos(squire.blockPosition());
        ChunkPos targetChunk = ai.getMining().getTargetPos() != null
                ? new ChunkPos(ai.getMining().getTargetPos()) : null;

        // Skip update if positions haven't changed
        ChunkPos prevSquire = lastSquireChunk.get(id);
        ChunkPos prevTarget = lastTargetChunk.get(id);
        if (squireChunk.equals(prevSquire)
                && (targetChunk == null ? prevTarget == null : targetChunk.equals(prevTarget))) {
            return;
        }

        // Positions changed — rebuild
        lastSquireChunk.put(id, squireChunk);
        lastTargetChunk.put(id, targetChunk);

        Set<ChunkPos> needed = new HashSet<>();
        needed.add(squireChunk);
        if (targetChunk != null) {
            needed.add(targetChunk);
        }

        Set<ChunkPos> current = loadedChunks.computeIfAbsent(id, k -> new HashSet<>());

        // Unload chunks no longer needed
        for (ChunkPos old : current) {
            if (!needed.contains(old)) {
                level.setChunkForced(old.x, old.z, false);
            }
        }

        // Load new chunks
        for (ChunkPos chunk : needed) {
            if (!current.contains(chunk)) {
                level.setChunkForced(chunk.x, chunk.z, true);
            }
        }

        current.clear();
        current.addAll(needed);
    }

    /** Release all force-loaded chunks for this squire. Called on death, cancel, or clear complete. */
    public static void release(SquireEntity squire) {
        if (!(squire.level() instanceof ServerLevel level)) return;

        UUID id = squire.getUUID();
        Set<ChunkPos> current = loadedChunks.remove(id);
        if (current != null) {
            for (ChunkPos chunk : current) {
                level.setChunkForced(chunk.x, chunk.z, false);
            }
        }
        lastSquireChunk.remove(id);
        lastTargetChunk.remove(id);
    }
}
