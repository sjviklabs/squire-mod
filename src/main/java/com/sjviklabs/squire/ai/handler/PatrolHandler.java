package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.block.SignpostBlockEntity;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles patrol behavior — walking between signpost waypoints in a loop.
 * Also supports guard mode (stay at single point, engage hostiles).
 *
 * Route is built by following linked signpost block entities.
 * Can also be given a manual list of positions via command.
 */
public class PatrolHandler {

    private final SquireEntity squire;
    private List<BlockPos> route;
    private int currentIndex;
    private int waitTimer;
    private boolean patrolling;

    public PatrolHandler(SquireEntity squire) {
        this.squire = squire;
    }

    public boolean isPatrolling() { return patrolling; }

    /**
     * Start patrolling with a list of waypoints.
     */
    public void startPatrol(List<BlockPos> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) return;
        this.route = new ArrayList<>(waypoints);
        this.currentIndex = 0;
        this.waitTimer = 0;
        this.patrolling = true;
    }

    /** Start guard mode at a single position. */
    public void startGuard(BlockPos pos) {
        this.route = List.of(pos);
        this.currentIndex = 0;
        this.waitTimer = 0;
        this.patrolling = true;
    }

    public void stopPatrol() {
        this.patrolling = false;
        this.route = null;
        this.currentIndex = 0;
        this.waitTimer = 0;
    }

    /**
     * Build a patrol route by following the linked signpost chain starting at the given position.
     * Returns the list of waypoints, or empty if the signpost has no links.
     */
    public static List<BlockPos> buildRouteFromSignpost(Level level, BlockPos start) {
        List<BlockPos> waypoints = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos current = start;
        int maxRoute = SquireConfig.patrolMaxRouteLength.get();

        while (current != null && waypoints.size() < maxRoute) {
            if (visited.contains(current)) {
                // Loop detected — that's fine, route is complete
                break;
            }
            visited.add(current);

            BlockEntity be = level.getBlockEntity(current);
            if (be instanceof SignpostBlockEntity signpost) {
                waypoints.add(current);
                current = signpost.getLinkedSignpost();
            } else {
                break;
            }
        }

        return waypoints;
    }

    public SquireAIState tickWalk(SquireEntity s) {
        if (!patrolling || route == null || route.isEmpty()) {
            patrolling = false;
            return SquireAIState.IDLE;
        }

        BlockPos target = route.get(currentIndex);
        double distSq = s.distanceToSqr(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (distSq <= 4.0) { // Within 2 blocks of waypoint
            waitTimer = SquireConfig.patrolDefaultWait.get();
            return SquireAIState.PATROL_WAIT;
        }

        // Walk toward current waypoint
        s.getNavigation().moveTo(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
        return SquireAIState.PATROL_WALK;
    }

    public SquireAIState tickWait(SquireEntity s) {
        if (!patrolling || route == null || route.isEmpty()) {
            patrolling = false;
            return SquireAIState.IDLE;
        }

        waitTimer--;
        if (waitTimer <= 0) {
            // Guard mode (single waypoint): stay forever
            if (route.size() == 1) {
                waitTimer = SquireConfig.patrolDefaultWait.get();
                return SquireAIState.PATROL_WAIT;
            }

            // Advance to next waypoint (loop)
            int nextIndex = (currentIndex + 1) % route.size();
            if (nextIndex == 0) {
                // Completed a full loop — award XP
                squire.getProgression().addPatrolLoopXP();
            }
            currentIndex = nextIndex;
            return SquireAIState.PATROL_WALK;
        }

        // Look around while waiting
        if (s.getRandom().nextFloat() < 0.1F) {
            double rx = s.getX() + (s.getRandom().nextDouble() - 0.5) * 16.0;
            double ry = s.getEyeY() + (s.getRandom().nextDouble() - 0.5) * 4.0;
            double rz = s.getZ() + (s.getRandom().nextDouble() - 0.5) * 16.0;
            s.getLookControl().setLookAt(rx, ry, rz);
        }

        return SquireAIState.PATROL_WAIT;
    }
}
