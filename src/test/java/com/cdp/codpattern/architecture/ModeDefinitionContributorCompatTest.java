package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.extension.ModeDefinitionContributor;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ModeDefinitionContributorCompatTest {
    private ModeDefinitionContributorCompatTest() {
    }

    public static void main(String[] args) {
        GameModeDefinition definition = new GameModeDefinition(
                "external_fixture",
                List.of("fixture_alias"),
                "mode.fixture",
                "screen.fixture.header",
                "/fixture create",
                List.of(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.MODE_DEFINED,
                ScoreboardKind.MODE_DEFINED,
                Set.of(ModeCapability.READY_STATE));
        ModeDefinitionContributor contributor = registrar -> registrar.register(definition);
        List<GameModeDefinition> collected = new ArrayList<>();

        contributor.contribute(collected::add);

        require(collected.equals(List.of(definition)),
                "the extension skeleton should expose definitions without a concrete registry dependency");
        System.out.println("PASS mode definition contributor compat");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
