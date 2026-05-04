package com.cdp.codpattern.app.zombies.runtime;

import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;
import com.cdp.codpattern.app.zombies.service.ZombiesServiceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ZombiesLifecycleRuntime {
    private final ZombiesRoomRuntimeState state;
    private final ZombiesPhaseStateMachine.Config config;
    private final ZombiesPhaseStateMachine.FailurePriority failurePriority;
    private final ZombiesPhaseStateMachine.WaveCompletion waveCompletion;
    private final List<ZombiesLifecycleHooks> hooks;

    public ZombiesLifecycleRuntime(
            ZombiesRoomRuntimeState state,
            ZombiesPhaseStateMachine.Config config,
            ZombiesPhaseStateMachine.FailurePriority failurePriority,
            ZombiesPhaseStateMachine.WaveCompletion waveCompletion,
            List<ZombiesLifecycleHooks> hooks
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.config = Objects.requireNonNull(config, "config");
        this.failurePriority = failurePriority;
        this.waveCompletion = waveCompletion;
        this.hooks = new ArrayList<>(hooks == null ? List.of() : hooks);
        this.hooks.sort(Comparator.comparingInt(ZombiesLifecycleHooks::order));
    }

    public ZombiesRoomRuntimeState state() {
        return state;
    }

    public ZombiesGamePhase phase() {
        return state.phase();
    }

    public void beginStartVote() {
        transitionTo(ZombiesGamePhase.START_VOTE);
    }

    public void cancelStartVote() {
        ZombiesGamePhase currentPhase = state.phase();
        if (currentPhase == ZombiesGamePhase.WAITING) {
            return;
        }
        if (currentPhase == ZombiesGamePhase.START_VOTE) {
            state.transitionTo(ZombiesGamePhase.WAITING);
            return;
        }
        transitionTo(ZombiesGamePhase.WAITING);
    }

    public void beginOpeningCountdown(int maxWave) {
        state.configureMaxWave(maxWave);
        state.waveState().prepareTargetWave(1);
        transitionTo(ZombiesGamePhase.OPENING_COUNTDOWN);
    }

    public void fail(ZombiesErrorCode code) {
        state.markFailure(code);
        transitionTo(ZombiesGamePhase.FAILED);
    }

    public ZombiesServiceResult<Void> tick() {
        ZombiesServiceResult<Void> tickResult = runTickHooks();
        if (!tickResult.success()) {
            fail(tickResult.code());
            return tickResult;
        }

        ZombiesPhaseStateMachine.TickResult result = ZombiesPhaseStateMachine.tick(
                state,
                config,
                failurePriority,
                waveCompletion
        );
        if (result.nextPhase().isPresent()) {
            transitionTo(result.nextPhase().get());
        }
        return ZombiesServiceResult.ok();
    }

    public int remainingPhaseTicks() {
        return ZombiesPhaseStateMachine.remainingPhaseTicks(state, config);
    }

    public void resetToWaiting() {
        state.resetForWaiting();
    }

    private void transitionTo(ZombiesGamePhase nextPhase) {
        ZombiesGamePhase previousPhase = state.phase();
        if (previousPhase == nextPhase && nextPhase != ZombiesGamePhase.WAITING) {
            return;
        }
        ZombiesPhaseTransitionContext exitContext = context(previousPhase, nextPhase, previousPhase);
        if (!runExitHooks(exitContext)) {
            return;
        }
        state.transitionTo(nextPhase);
        if (nextPhase == ZombiesGamePhase.WAITING) {
            runCleanupHooks(context(previousPhase, ZombiesGamePhase.WAITING, ZombiesGamePhase.WAITING));
            return;
        }
        runEnterHooks(context(previousPhase, nextPhase, nextPhase));
    }

    private ZombiesServiceResult<Void> runTickHooks() {
        ZombiesPhaseTransitionContext context = context(state.phase(), state.phase(), state.phase());
        for (ZombiesLifecycleHooks hook : hooks) {
            ZombiesServiceResult<Void> result = hook.onTick(context);
            if (!result.success()) {
                return result;
            }
        }
        return ZombiesServiceResult.ok();
    }

    private void runEnterHooks(ZombiesPhaseTransitionContext context) {
        for (ZombiesLifecycleHooks hook : hooks) {
            ZombiesServiceResult<Void> result = hook.onEnter(context);
            if (!result.success()) {
                state.markFailure(result.code());
                state.transitionTo(ZombiesGamePhase.FAILED);
                return;
            }
        }
    }

    private boolean runExitHooks(ZombiesPhaseTransitionContext context) {
        for (ZombiesLifecycleHooks hook : hooks) {
            ZombiesServiceResult<Void> result = hook.onExit(context);
            if (!result.success()) {
                state.markFailure(result.code());
                state.transitionTo(ZombiesGamePhase.FAILED);
                return false;
            }
        }
        return true;
    }

    private void runCleanupHooks(ZombiesPhaseTransitionContext context) {
        for (ZombiesLifecycleHooks hook : hooks) {
            ZombiesServiceResult<Void> result = hook.onCleanup(context);
            if (!result.success()) {
                state.markFailure(result.code());
            }
        }
    }

    private ZombiesPhaseTransitionContext context(
            ZombiesGamePhase previousPhase,
            ZombiesGamePhase currentPhase,
            ZombiesGamePhase nextPhase
    ) {
        return new ZombiesPhaseTransitionContext(
                state.roomId(),
                previousPhase == null ? "" : previousPhase.key(),
                currentPhase == null ? "" : currentPhase.key(),
                nextPhase == null ? "" : nextPhase.key(),
                state.roomTick()
        );
    }
}
