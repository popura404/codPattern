package com.cdp.codpattern.verification;

import com.cdp.codpattern.client.refit.AttachmentRefitCandidateStaticContractCompatTest;
import com.cdp.codpattern.architecture.ModeDefinitionContributorCompatTest;
import com.cdp.codpattern.architecture.ModeExtensionRuntimeRouterCompatTest;
import com.cdp.codpattern.architecture.ModeOperationResultCompatTest;
import com.cdp.codpattern.architecture.ModeRegistryConflictBaselineCompatTest;
import com.cdp.codpattern.architecture.ModeRoomHandleBuilderCompatTest;
import com.cdp.codpattern.app.match.runtime.ready.DefaultReadyStateServiceCompatTest;
import com.cdp.codpattern.app.match.runtime.roster.RoomRosterSyncCoordinatorCompatTest;
import com.cdp.codpattern.app.match.runtime.vote.RoomVoteEngineCompatTest;
import com.cdp.codpattern.app.match.runtime.Phase3RuntimePrimitivesCompatTest;
import com.cdp.codpattern.app.match.runtime.Phase5ContributionPrimitivesCompatTest;
import com.cdp.codpattern.app.match.runtime.object.ModeObjectRuntimeCompatTest;

public final class CommonCompatTestSuite {
    private CommonCompatTestSuite() {
    }

    public static void main(String[] args) throws Exception {
        AttachmentRefitCandidateStaticContractCompatTest.main(args);
        ModeRegistryConflictBaselineCompatTest.main(args);
        ModeRoomHandleBuilderCompatTest.main(args);
        ModeOperationResultCompatTest.main(args);
        ModeDefinitionContributorCompatTest.main(args);
        ModeExtensionRuntimeRouterCompatTest.main(args);
        DefaultReadyStateServiceCompatTest.main(args);
        RoomVoteEngineCompatTest.main(args);
        RoomRosterSyncCoordinatorCompatTest.main(args);
        Phase3RuntimePrimitivesCompatTest.main(args);
        ModeObjectRuntimeCompatTest.main(args);
        Phase5ContributionPrimitivesCompatTest.main(args);
        System.out.println("PASS common compatibility suite (12/12)");
    }
}
