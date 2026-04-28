package com.cdp.codpattern.app.match.port;

import java.util.UUID;

public interface VoteControlPort {
    boolean initiateStartVote(UUID initiator);

    boolean initiateEndVote(UUID initiator);

    boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted);
}
