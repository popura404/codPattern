package com.cdp.codpattern.compat.fpsmatch.map;

final class CodTdmTickRuntime {
    private final Runnable tickPhaseStateMachine;
    private final Runnable tickVoteSession;
    private final Runnable tickDeathCam;
    private final Runnable tickRespawn;
    private final Runnable tickInvincibility;
    private final Runnable tickCombatRegen;
    private final Runnable tickDisconnectedPlayers;

    CodTdmTickRuntime(
            Runnable tickPhaseStateMachine,
            Runnable tickVoteSession,
            Runnable tickDeathCam,
            Runnable tickRespawn,
            Runnable tickInvincibility,
            Runnable tickCombatRegen,
            Runnable tickDisconnectedPlayers
    ) {
        this.tickPhaseStateMachine = tickPhaseStateMachine;
        this.tickVoteSession = tickVoteSession;
        this.tickDeathCam = tickDeathCam;
        this.tickRespawn = tickRespawn;
        this.tickInvincibility = tickInvincibility;
        this.tickCombatRegen = tickCombatRegen;
        this.tickDisconnectedPlayers = tickDisconnectedPlayers;
    }

    void tick() {
        tickPhaseStateMachine.run();
        tickVoteSession.run();
        tickDeathCam.run();
        tickInvincibility.run();
        tickRespawn.run();
        tickCombatRegen.run();
        tickDisconnectedPlayers.run();
    }
}
