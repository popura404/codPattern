package com.cdp.codpattern.app.zombies.runtime;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;

import java.util.Objects;
import java.util.Optional;

public final class ZombiesRoomRuntimeState {
    private final RoomId roomId;
    private final ZombiesWaveRuntimeState waveState = new ZombiesWaveRuntimeState();
    private ZombiesGamePhase phase = ZombiesGamePhase.WAITING;
    private int phaseTimerTicks;
    private long roomTick;
    private long revision;
    private ZombiesErrorCode lastFailureCode;

    public ZombiesRoomRuntimeState(RoomId roomId) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
    }

    public synchronized RoomId roomId() {
        return roomId;
    }

    public synchronized ZombiesGamePhase phase() {
        return phase;
    }

    public synchronized String phaseKey() {
        return phase.key();
    }

    public synchronized int phaseTimerTicks() {
        return phaseTimerTicks;
    }

    public synchronized long roomTick() {
        return roomTick;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized ZombiesWaveRuntimeState waveState() {
        return waveState;
    }

    public synchronized Optional<ZombiesErrorCode> lastFailureCode() {
        return Optional.ofNullable(lastFailureCode);
    }

    public synchronized void configureMaxWave(int maxWave) {
        waveState.configureMaxWave(maxWave);
        bumpRevision();
    }

    public synchronized void transitionTo(ZombiesGamePhase nextPhase) {
        ZombiesGamePhase cleaned = Objects.requireNonNull(nextPhase, "nextPhase");
        if (phase == cleaned) {
            phaseTimerTicks = 0;
            return;
        }
        phase = cleaned;
        phaseTimerTicks = 0;
        if (phase == ZombiesGamePhase.INTERMISSION && waveState.targetWave() < 1) {
            waveState.prepareTargetWave(1);
        } else if (phase == ZombiesGamePhase.WAVE_ACTIVE) {
            waveState.beginTargetWave();
        } else if (phase == ZombiesGamePhase.WAITING) {
            resetForWaiting();
            return;
        }
        bumpRevision();
    }

    public synchronized void prepareNextWave() {
        waveState.prepareTargetWave(Math.max(1, waveState.currentWave() + 1));
        bumpRevision();
    }

    public synchronized void markFailure(ZombiesErrorCode code) {
        lastFailureCode = code == null ? ZombiesErrorCode.STARTUP_PREFLIGHT_FAILED : code;
        bumpRevision();
    }

    synchronized void incrementPhaseTimer() {
        phaseTimerTicks++;
    }

    synchronized void incrementRoomTick() {
        roomTick++;
        if (phase == ZombiesGamePhase.WAVE_ACTIVE) {
            waveState.tickWaveTime();
        }
    }

    synchronized void resetForWaiting() {
        phase = ZombiesGamePhase.WAITING;
        phaseTimerTicks = 0;
        roomTick = 0L;
        lastFailureCode = null;
        waveState.reset();
        bumpRevision();
    }

    private void bumpRevision() {
        revision++;
    }
}
