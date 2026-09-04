package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.ModeCatalog;
import com.cdp.codpattern.app.match.ModeModuleCollector;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.extension.ModeModule;
import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamPolicy;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Verifies concurrent collection, deterministic freeze, and all catalog conflict fences. */
public final class ModeRegistryConflictBaselineCompatTest {
    private ModeRegistryConflictBaselineCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        lifecycleAndIdentityRules();
        parallelContributionsFreezeDeterministically();
        definitionAndAliasConflictsFail();
        providerAndExtensionConflictsFail();
        System.out.println("PASS immutable mode catalog concurrency and conflict compat");
    }

    private static void lifecycleAndIdentityRules() {
        ModeModuleCollector collector = new ModeModuleCollector();
        ModeModule module = module("fixture", 0, definition("fixture", List.of("fixture_alias")));
        expectFailure(collector::catalog, "not frozen");
        collector.contribute(module);
        collector.contribute(module);
        require(collector.contributionCount() == 1, "same module instance contribution must be idempotent");
        expectFailure(() -> collector.contribute(module("fixture", 0, definition("other", List.of()))),
                "Different mode module instance");

        ModeCatalog first = collector.freeze();
        require(first == collector.freeze(), "repeated freeze must return the same immutable snapshot");
        require(first.findDefinition("FIXTURE_ALIAS").orElseThrow().gameType().equals("fixture"),
                "frozen aliases must be normalized and immutable");
        expectFailure(() -> collector.contribute(module("late", 0, definition("late", List.of()))),
                "already frozen");
    }

    private static void parallelContributionsFreezeDeterministically() throws Exception {
        int moduleCount = 240;
        ModeModuleCollector collector = new ModeModuleCollector();
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<ModeModule> modules = new ArrayList<>();
        for (int index = 0; index < moduleCount; index++) {
            String id = "parallel_" + index;
            ModeModule module = module(id, index % 7, definition(id, List.of("alias_" + index)));
            modules.add(module);
            executor.submit(() -> {
                await(start);
                collector.contribute(module);
                collector.contribute(module);
            });
        }
        start.countDown();
        executor.shutdown();
        require(executor.awaitTermination(30, TimeUnit.SECONDS), "parallel contributions timed out");
        require(collector.contributionCount() == moduleCount, "parallel collection lost module contributions");

        List<String> expected = modules.stream()
                .sorted(java.util.Comparator.comparingInt(ModeModule::order)
                        .thenComparing(module -> module.id().toString()))
                .flatMap(module -> module.definitions().stream())
                .map(GameModeDefinition::gameType)
                .toList();
        List<String> actual = collector.freeze().definitions().stream()
                .map(GameModeDefinition::gameType)
                .toList();
        require(actual.equals(expected), "parallel freeze order must be stable by order, id, and declaration");
    }

    private static void definitionAndAliasConflictsFail() {
        expectFreezeFailure(List.of(
                module("one", 0, definition("shared", List.of())),
                module("two", 0, definition("SHARED", List.of()))), "Duplicate game mode");
        expectFreezeFailure(List.of(
                module("one", 0, definition("one", List.of("shared_alias"))),
                module("two", 0, definition("two", List.of("SHARED_ALIAS")))), "Duplicate game mode alias");
        expectFreezeFailure(List.of(
                module("one", 0, definition("one", List.of("two"))),
                module("two", 0, definition("two", List.of()))), "occupies a mode name");
    }

    private static void providerAndExtensionConflictsFail() {
        GameModeRuntimeProvider mismatched = runtimeProvider("wrong");
        GameModeDefinition invalidProvider = withRuntime(definition("right", List.of()), mismatched);
        expectFreezeFailure(List.of(module("provider", 0, invalidProvider)), "runtime provider");

        ModePlayerLoginContributor first = loginContributor("duplicate.extension");
        ModePlayerLoginContributor second = loginContributor("duplicate.extension");
        ModeModule left = moduleWithLogin("left", definition("left", List.of()), first);
        ModeModule right = moduleWithLogin("right", definition("right", List.of()), second);
        expectFreezeFailure(List.of(left, right), "Duplicate player-login contributor id");
    }

    private static void expectFreezeFailure(List<ModeModule> modules, String messagePart) {
        ModeModuleCollector collector = new ModeModuleCollector();
        modules.forEach(collector::contribute);
        expectFailure(collector::freeze, messagePart);
        expectFailure(collector::catalog, "freeze failed");
        expectFailure(() -> collector.contribute(module("after_failure", 0,
                definition("after_failure", List.of()))), "already frozen");
    }

    private static ModeModule module(String id, int order, GameModeDefinition... definitions) {
        return new ModeModule() {
            private final ResourceLocation moduleId = new ResourceLocation("test", id);

            @Override
            public ResourceLocation id() {
                return moduleId;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public List<GameModeDefinition> definitions() {
                return List.of(definitions);
            }
        };
    }

    private static ModeModule moduleWithLogin(
            String id,
            GameModeDefinition definition,
            ModePlayerLoginContributor contributor
    ) {
        ModeModule base = module(id, 0, definition);
        return new ModeModule() {
            @Override
            public ResourceLocation id() {
                return base.id();
            }

            @Override
            public List<GameModeDefinition> definitions() {
                return base.definitions();
            }

            @Override
            public List<ModePlayerLoginContributor> playerLoginContributors() {
                return List.of(contributor);
            }
        };
    }

    private static GameModeDefinition definition(String gameType, List<String> aliases) {
        return new GameModeDefinition(
                gameType,
                aliases,
                "mode." + gameType,
                "room." + gameType,
                "command." + gameType,
                List.of(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.MODE_DEFINED,
                ScoreboardKind.MODE_DEFINED,
                Set.of());
    }

    private static GameModeDefinition withRuntime(
            GameModeDefinition definition,
            GameModeRuntimeProvider provider
    ) {
        return new GameModeDefinition(
                definition.gameType(), definition.aliases(), definition.displayNameKey(),
                definition.roomHeaderKey(), definition.createCommand(), definition.teams(), definition.family(),
                definition.teamPolicy(), definition.joinPolicy(), definition.lifecycleKind(),
                definition.scoreboardKind(), definition.capabilities(), Optional.of(provider), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    private static GameModeRuntimeProvider runtimeProvider(String gameType) {
        return new GameModeRuntimeProvider() {
            @Override
            public String gameType() {
                return gameType;
            }

            @Override
            public BaseMap createMap(ServerLevel level, String mapName, AreaData areaData) {
                return null;
            }

            @Override
            public Optional<ModeRoomHandle> roomHandle(BaseMap map) {
                return Optional.empty();
            }

            @Override
            public java.util.stream.Stream<ModeRoomHandle> listRoomHandles() {
                return java.util.stream.Stream.empty();
            }
        };
    }

    private static ModePlayerLoginContributor loginContributor(String id) {
        return new ModePlayerLoginContributor() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public LoginDisposition onPlayerLogin(net.minecraft.server.level.ServerPlayer player) {
                return LoginDisposition.CONTINUE;
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void expectFailure(Runnable operation, String messagePart) {
        try {
            operation.run();
            throw new AssertionError("Expected failure containing: " + messagePart);
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(messagePart),
                    "unexpected failure message: " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
