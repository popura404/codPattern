package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.extension.ModeEntityReconciliationContributor;
import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.match.runtime.entity.ModeEntityReconciliationRouter;
import com.cdp.codpattern.app.match.runtime.player.ModePlayerLoginRouter;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ModeExtensionRuntimeRouterCompatTest {
    private ModeExtensionRuntimeRouterCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        loginContributorsRunDeterministicallyAndCanStopSharedLogin();
        missingEntityCallbacksStayModeOwned();
        futureMainRoutesContainNoZombiesImplementationImports();
    }

    private static void loginContributorsRunDeterministicallyAndCanStopSharedLogin() {
        List<String> calls = new ArrayList<>();
        ModePlayerLoginRouter router = new ModePlayerLoginRouter(List.of(
                loginContributor("late", 30, ModePlayerLoginContributor.LoginDisposition.CONTINUE, calls),
                loginContributor("stop", 20, ModePlayerLoginContributor.LoginDisposition.STOP_SHARED_LOGIN, calls),
                loginContributor("early", 10, ModePlayerLoginContributor.LoginDisposition.CONTINUE, calls)));

        ModePlayerLoginContributor.LoginDisposition result = router.route(null);
        require(result == ModePlayerLoginContributor.LoginDisposition.STOP_SHARED_LOGIN,
                "a contributor should preserve the existing deferred-leave short circuit");
        require(calls.equals(List.of("early", "stop")),
                "login contributors should run by order and stop before later shared recovery");
    }

    private static void missingEntityCallbacksStayModeOwned() {
        List<String> calls = new ArrayList<>();
        ModeEntityReconciliationRouter router = new ModeEntityReconciliationRouter(List.of(
                entityContributor("other", 20, "frontline", calls),
                entityContributor("zombies", 10, "zombies", calls)));
        ModeEntityOwnershipRegistry.Entry entry = new ModeEntityOwnershipRegistry.Entry(
                RoomId.of("zombies", "map-a"),
                Level.OVERWORLD,
                UUID.fromString("53000000-0000-0000-0000-000000000001"));

        require(router.onMissingEntity(entry), "matching mode callback should handle a missing entity projection");
        require(calls.equals(List.of("zombies")), "unrelated mode callbacks must not run");
    }

    private static void futureMainRoutesContainNoZombiesImplementationImports() throws Exception {
        String login = Files.readString(Path.of(
                "src/main/java/com/cdp/codpattern/event/PlayerLoggedInEventHandler.java"));
        String tick = Files.readString(Path.of(
                "src/main/java/com/cdp/codpattern/compat/fpsmatch/event/ModeRoomTickEventHandler.java"));

        require(!login.contains("app.zombies") && !login.contains("ZombiesMap"),
                "future-main login route must not import Zombies recovery implementation");
        require(login.contains("ModePlayerLoginContributors.route(serverPlayer)"),
                "future-main login route should dispatch through public contributors");
        require(!tick.contains("app.zombies") && !tick.contains("ZombiesActiveMobCounter"),
                "future-main entity reconciliation route must not import Zombies counters");
        require(tick.contains("ModeEntityReconciliationContributors.onMissingEntity(entry)"),
                "missing tracked entities should dispatch through mode-owned callbacks");
    }

    private static ModePlayerLoginContributor loginContributor(
            String id,
            int order,
            ModePlayerLoginContributor.LoginDisposition disposition,
            List<String> calls
    ) {
        return new ModePlayerLoginContributor() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public LoginDisposition onPlayerLogin(net.minecraft.server.level.ServerPlayer player) {
                calls.add(id);
                return disposition;
            }
        };
    }

    private static ModeEntityReconciliationContributor entityContributor(
            String id,
            int order,
            String gameType,
            List<String> calls
    ) {
        return new ModeEntityReconciliationContributor() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public boolean supports(RoomId roomId) {
                return roomId != null && gameType.equals(roomId.gameType());
            }

            @Override
            public void onMissingEntity(ModeEntityOwnershipRegistry.Entry entry) {
                calls.add(id);
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
