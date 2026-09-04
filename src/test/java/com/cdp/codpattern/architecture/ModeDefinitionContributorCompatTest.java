package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.extension.ModeModule;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamPolicy;

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
        ModeModule module = new ModeModule() {
            @Override
            public net.minecraft.resources.ResourceLocation id() {
                return new net.minecraft.resources.ResourceLocation("external_fixture", "mode");
            }

            @Override
            public List<GameModeDefinition> definitions() {
                return List.of(definition);
            }
        };

        require(module.definitions().equals(List.of(definition)),
                "the public module API should expose definitions without a concrete registry dependency");
        System.out.println("PASS mode module public API compat");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
