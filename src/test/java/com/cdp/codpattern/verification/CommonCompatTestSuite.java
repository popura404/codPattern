package com.cdp.codpattern.verification;

import com.cdp.codpattern.client.refit.AttachmentRefitCandidateStaticContractCompatTest;
import com.cdp.codpattern.architecture.ModeDefinitionContributorCompatTest;
import com.cdp.codpattern.architecture.ModeExtensionRuntimeRouterCompatTest;
import com.cdp.codpattern.architecture.ModeRegistryConflictBaselineCompatTest;
import com.cdp.codpattern.architecture.ModeRoomHandleBuilderCompatTest;
import com.cdp.codpattern.architecture.GameModeFpsRegistrationCompatTest;
import com.cdp.codpattern.app.match.runtime.ready.DefaultReadyStateServiceCompatTest;
import com.cdp.codpattern.app.match.runtime.roster.RoomRosterSyncCoordinatorCompatTest;
import com.cdp.codpattern.app.match.runtime.vote.RoomVoteEngineCompatTest;
import com.cdp.codpattern.app.match.runtime.Phase3RuntimePrimitivesCompatTest;
import com.cdp.codpattern.app.match.runtime.Phase5ContributionPrimitivesCompatTest;
import com.cdp.codpattern.app.match.runtime.object.ModeObjectRuntimeCompatTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Field;

public final class CommonCompatTestSuite {
    private CommonCompatTestSuite() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null, true);
        if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) {
            throw new AssertionError("built-in registries failed to initialize for compatibility tests");
        }
        AttachmentRefitCandidateStaticContractCompatTest.main(args);
        ModeRegistryConflictBaselineCompatTest.main(args);
        ModeRoomHandleBuilderCompatTest.main(args);
        ModeDefinitionContributorCompatTest.main(args);
        ModeExtensionRuntimeRouterCompatTest.main(args);
        DefaultReadyStateServiceCompatTest.main(args);
        RoomVoteEngineCompatTest.main(args);
        RoomRosterSyncCoordinatorCompatTest.main(args);
        Phase3RuntimePrimitivesCompatTest.main(args);
        ModeObjectRuntimeCompatTest.main(args);
        Phase5ContributionPrimitivesCompatTest.main(args);
        GameModeFpsRegistrationCompatTest.main(args);
        System.out.println("PASS future-main common compatibility suite (12/12)");
    }
}
