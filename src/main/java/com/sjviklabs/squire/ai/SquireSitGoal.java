package com.sjviklabs.squire.ai;

import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * STAY mode goal. When the squire is ordered to sit, this goal blocks MOVE and JUMP
 * flags, keeping the squire stationary. Works alongside TamableAnimal's built-in
 * isOrderedToSit()/setInSittingPose() state.
 *
 * This is a custom implementation rather than extending vanilla SitGoal because
 * SitGoal only blocks JUMP, and we need to also block MOVE to prevent other goals
 * from moving the squire while in STAY mode.
 */
public class SquireSitGoal extends Goal {

    private final SquireEntity squire;

    public SquireSitGoal(SquireEntity squire) {
        this.squire = squire;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // Active whenever the squire is ordered to sit (STAY mode)
        if (!this.squire.isTame()) return false;
        if (this.squire.isInWaterOrBubble()) return false;
        return this.squire.isOrderedToSit();
    }

    @Override
    public boolean canContinueToUse() {
        return this.squire.isOrderedToSit();
    }

    @Override
    public void start() {
        this.squire.getNavigation().stop();
        this.squire.setInSittingPose(true);
    }

    @Override
    public void stop() {
        this.squire.setInSittingPose(false);
    }
}
