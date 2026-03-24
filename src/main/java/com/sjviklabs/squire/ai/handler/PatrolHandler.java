package com.sjviklabs.squire.ai.handler;

import com.sjviklabs.squire.ai.statemachine.SquireAIState;
import com.sjviklabs.squire.config.SquireConfig;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

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
            currentIndex = (currentIndex + 1) % route.size();
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
