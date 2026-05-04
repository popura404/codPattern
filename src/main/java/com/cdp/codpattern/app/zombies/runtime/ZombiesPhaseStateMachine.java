package com.cdp.codpattern.app.zombies.runtime;

import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;

import java.util.Objects;
import java.util.Optional;

public final class ZombiesPhaseStateMachine {
    public static final int TICKS_PER_SECOND = 20;
    public static final int DEFAULT_OPENING_COUNTDOWN_SECONDS = 20;
    public static final int DEFAULT_INTERMISSION_SECONDS = 5;
    public static final int DEFAULT_WAVE_TIMEOUT_SECONDS = 1200;
    public static final int DEFAULT_WAVE_TIMEOUT_TICKS = secondsToTicks(DEFAULT_WAVE_TIMEOUT_SECONDS);

    public record Config(
            int openingCountdownTicks,
            int intermissionTicks,
            int endDelayTicks
    ) {
        public Config {
            openingCountdownTicks = Math.max(0, openingCountdownTicks);
            intermissionTicks = Math.max(0, intermissionTicks);
            endDelayTicks = Math.max(0, endDelayTicks);
        }

        public static Config fromSeconds(int openingCountdownSeconds, int intermissionSeconds, int endDelaySeconds) {
            return new Config(
                    secondsToTicks(openingCountdownSeconds),
                    secondsToTicks(intermissionSeconds),
                    secondsToTicks(endDelaySeconds)
            );
        }
    }

    public interface FailurePriority {
        FailureCheckResult checkFailure(ZombiesRoomRuntimeState state);
    }

    public interface WaveCompletion {
        boolean isWaveComplete(ZombiesRoomRuntimeState state);
    }

    public record FailureCheckResult(boolean failed, ZombiesErrorCode code) {
        public static FailureCheckResult none() {
            return new FailureCheckResult(false, ZombiesErrorCode.OK);
        }

        public static FailureCheckResult failed(ZombiesErrorCode code) {
            return new FailureCheckResult(true, code);
        }
    }

    public record TickResult(Optional<ZombiesGamePhase> nextPhase, boolean resetTriggered) {
        public static TickResult stay() {
            return new TickResult(Optional.empty(), false);
        }

        public static TickResult next(ZombiesGamePhase phase) {
            return new TickResult(Optional.of(phase), false);
        }

        public static TickResult reset() {
            return new TickResult(Optional.of(ZombiesGamePhase.WAITING), true);
        }
    }

    private ZombiesPhaseStateMachine() {
    }

    public static TickResult tick(
            ZombiesRoomRuntimeState state,
            Config config,
            FailurePriority failurePriority,
            WaveCompletion waveCompletion
    ) {
        Objects.requireNonNull(state, "state");
        Config safeConfig = config == null ? Config.fromSeconds(DEFAULT_OPENING_COUNTDOWN_SECONDS, DEFAULT_INTERMISSION_SECONDS, 0) : config;
        FailureCheckResult failure = checkFailure(state, failurePriority);
        if (state.phase().isRoundRunning() && failure.failed()) {
            state.markFailure(failure.code());
            return TickResult.next(ZombiesGamePhase.FAILED);
        }

        state.incrementRoomTick();
        return switch (state.phase()) {
            case WAITING, START_VOTE -> TickResult.stay();
            case OPENING_COUNTDOWN -> tickTimedPhase(state, safeConfig.openingCountdownTicks(), ZombiesGamePhase.INTERMISSION);
            case INTERMISSION -> tickTimedPhase(state, safeConfig.intermissionTicks(), ZombiesGamePhase.WAVE_ACTIVE);
            case WAVE_ACTIVE -> tickWaveActive(state, waveCompletion);
            case VICTORY, FAILED -> tickTimedPhase(state, safeConfig.endDelayTicks(), ZombiesGamePhase.ENDING);
            case ENDING -> TickResult.reset();
        };
    }

    public static int remainingPhaseTicks(ZombiesRoomRuntimeState state, Config config) {
        Objects.requireNonNull(state, "state");
        Config safeConfig = config == null ? Config.fromSeconds(DEFAULT_OPENING_COUNTDOWN_SECONDS, DEFAULT_INTERMISSION_SECONDS, 0) : config;
        int duration = switch (state.phase()) {
            case OPENING_COUNTDOWN -> safeConfig.openingCountdownTicks();
            case INTERMISSION -> safeConfig.intermissionTicks();
            case WAVE_ACTIVE -> DEFAULT_WAVE_TIMEOUT_TICKS;
            case VICTORY, FAILED -> safeConfig.endDelayTicks();
            default -> 0;
        };
        int elapsed = state.phase() == ZombiesGamePhase.WAVE_ACTIVE
                ? state.waveState().waveTimeTicks()
                : state.phaseTimerTicks();
        return Math.max(0, duration - elapsed);
    }

    public static int secondsToTicks(int seconds) {
        return Math.max(0, seconds) * TICKS_PER_SECOND;
    }

    private static TickResult tickTimedPhase(
            ZombiesRoomRuntimeState state,
            int durationTicks,
            ZombiesGamePhase nextPhase
    ) {
        state.incrementPhaseTimer();
        return state.phaseTimerTicks() >= durationTicks ? TickResult.next(nextPhase) : TickResult.stay();
    }

    private static TickResult tickWaveActive(ZombiesRoomRuntimeState state, WaveCompletion waveCompletion) {
        if (state.waveState().waveTimeTicks() >= DEFAULT_WAVE_TIMEOUT_TICKS) {
            state.markFailure(ZombiesErrorCode.WAVE_TIMEOUT);
            return TickResult.next(ZombiesGamePhase.FAILED);
        }
        boolean complete = waveCompletion != null
                ? waveCompletion.isWaveComplete(state)
                : state.waveState().isWaveComplete();
        if (!complete) {
            return TickResult.stay();
        }
        int nextWave = state.waveState().currentWave() + 1;
        if (state.waveState().maxWave() > 0 && nextWave > state.waveState().maxWave()) {
            return TickResult.next(ZombiesGamePhase.VICTORY);
        }
        state.waveState().prepareTargetWave(nextWave);
        return TickResult.next(ZombiesGamePhase.INTERMISSION);
    }

    private static FailureCheckResult checkFailure(
            ZombiesRoomRuntimeState state,
            FailurePriority failurePriority
    ) {
        if (failurePriority == null) {
            return FailureCheckResult.none();
        }
        FailureCheckResult result = failurePriority.checkFailure(state);
        return result == null ? FailureCheckResult.none() : result;
    }
}
