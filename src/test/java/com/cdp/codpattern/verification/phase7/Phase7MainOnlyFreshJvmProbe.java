package com.cdp.codpattern.verification.phase7;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.extension.ModeDefinitionContributor;
import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamPolicy;
import com.cdp.codpattern.app.match.runtime.network.ModeNetworkPacketSlotRegistry;
import com.cdp.codpattern.app.match.runtime.player.ModePlayerLoginRouter;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Fresh-process proof that the future-main runtime is usable with addon implementations absent. */
public final class Phase7MainOnlyFreshJvmProbe {
    private static final String EXTERNAL_MODE = "external_fixture";

    private Phase7MainOnlyFreshJvmProbe() {
    }

    public static void main(String[] args) throws Exception {
        addonAndShimClassesAreAbsent();
        mainOnlyBootstrapHasNoRetainedZombiesState();
        syntheticExternalContributorUsesOnlyPublicRoutes();
        System.out.println("PASS Phase 7 main-only fresh-JVM and external contributor rehearsal");
    }

    private static void addonAndShimClassesAreAbsent() throws Exception {
        requireClassAbsent("com.cdp.codpattern.app.zombies.bootstrap.ZombiesBootstrap");
        requireClassAbsent("com.cdp.codpattern.app.zombies.model.ZombiesGameModeDefinitions");
        requireClassAbsent("com.cdp.codpattern.compat.fpsmatch.map.ZombiesRuntimeProvider");
        requireClassAbsent("com.cdp.codpattern.CodPattern");
    }

    private static void mainOnlyBootstrapHasNoRetainedZombiesState() {
        List<String> installed = MainOnlyVerificationBootstrap.install();
        require(installed.equals(List.of("frontline", "teamdeathmatch")),
                "main-only bootstrap should install only Frontline and Team Deathmatch: " + installed);
        require(GameModeRuntimeRegistry.find("frontline").isPresent(),
                "Frontline runtime provider should be reachable after main-only bootstrap");
        require(GameModeRuntimeRegistry.find("teamdeathmatch").isPresent(),
                "Team Deathmatch runtime provider should be reachable after main-only bootstrap");
        require(ClientModePresentationRegistry.find("frontline").isPresent(),
                "Frontline client presentation should be reachable after main-only bootstrap");
        require(ClientModePresentationRegistry.find("teamdeathmatch").isPresent(),
                "Team Deathmatch client presentation should be reachable after main-only bootstrap");
        require(GameModeRegistry.findDefinition("zombies").isEmpty(),
                "fresh main-only definition registry must not retain Zombies");
        require(GameModeRuntimeRegistry.find("zombies").isEmpty(),
                "fresh main-only runtime registry must not retain Zombies");
        require(ClientModePresentationRegistry.find("zombies").isEmpty(),
                "fresh main-only client presentation registry must not retain Zombies");
    }

    private static void syntheticExternalContributorUsesOnlyPublicRoutes() {
        FixtureRuntimeProvider runtimeProvider = new FixtureRuntimeProvider();
        ClientModePresentation presentation = new ClientModePresentation(
                new ResourceLocation("external_fixture", "textures/gui/mode_preview.png"),
                16,
                9,
                0xFF44AAEE,
                "mode.external_fixture.description",
                "external_fixture");
        GameModeDefinition definition = new GameModeDefinition(
                EXTERNAL_MODE,
                List.of("external_fixture_alias"),
                "mode.external_fixture",
                "screen.external_fixture.header",
                "/external_fixture create",
                List.of(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.MODE_DEFINED,
                ScoreboardKind.MODE_DEFINED,
                Set.of(ModeCapability.READY_STATE),
                Optional.of(runtimeProvider),
                Optional.empty(),
                Optional.empty(),
                Optional.of(presentation));

        ModeDefinitionContributor contributor = registrar -> registrar.register(definition);
        contributor.contribute(GameModeRegistry::registerDefinition);
        GameModeBootstrap.registerCommonProviders();

        require(GameModeRegistry.findDefinition("external_fixture_alias").orElseThrow().gameType()
                        .equals(EXTERNAL_MODE),
                "external definition and alias should reach the public registry");
        require(GameModeRuntimeRegistry.find(EXTERNAL_MODE).orElseThrow() == runtimeProvider,
                "external runtime should reach the public runtime registry");
        require(ClientModePresentationRegistry.find(EXTERNAL_MODE).orElseThrow().equals(presentation),
                "external presentation should reach the public client registry");

        AtomicInteger eventCalls = new AtomicInteger();
        ModePlayerLoginRouter eventRouter = new ModePlayerLoginRouter(List.of(new ModePlayerLoginContributor() {
            @Override
            public String id() {
                return "external_fixture.login";
            }

            @Override
            public LoginDisposition onPlayerLogin(net.minecraft.server.level.ServerPlayer player) {
                eventCalls.incrementAndGet();
                return LoginDisposition.CONTINUE;
            }
        }));
        require(eventRouter.route(null) == ModePlayerLoginContributor.LoginDisposition.CONTINUE,
                "external event contributor should reach the public event router");
        require(eventCalls.get() == 1, "external event contributor should run exactly once");

        ModeNetworkPacketSlotRegistry isolatedPackets = new ModeNetworkPacketSlotRegistry();
        AtomicInteger packetRegistrations = new AtomicInteger();
        AtomicInteger reservedDiscriminators = new AtomicInteger(900);
        isolatedPackets.install("external_fixture.packet", packetRegistrations::incrementAndGet);
        require(isolatedPackets.registerOrReserve(
                        "external_fixture.packet", reservedDiscriminators::getAndIncrement),
                "external packet contributor should run through an isolated registry");
        require(packetRegistrations.get() == 1 && reservedDiscriminators.get() == 900,
                "installed isolated packet must not consume a production-style reservation");
        require(!isolatedPackets.registerOrReserve(
                        "external_fixture.absent", reservedDiscriminators::getAndIncrement),
                "missing isolated packet should reserve its local discriminator");
        require(reservedDiscriminators.get() == 901,
                "isolated missing packet should consume exactly one local discriminator");
    }

    private static void requireClassAbsent(String className) throws Exception {
        String resourceName = className.replace('.', '/') + ".class";
        require(Phase7MainOnlyFreshJvmProbe.class.getClassLoader().getResource(resourceName) == null,
                "forbidden class resource is present on the main-only runtime classpath: " + className);
        try {
            Class.forName(className, false, Phase7MainOnlyFreshJvmProbe.class.getClassLoader());
            throw new AssertionError("forbidden class loaded from the main-only runtime classpath: " + className);
        } catch (ClassNotFoundException expected) {
            // Expected: this process deliberately has no addon or composition-shim output.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FixtureRuntimeProvider implements GameModeRuntimeProvider {
        @Override
        public String gameType() {
            return EXTERNAL_MODE;
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
        public Stream<ModeRoomHandle> listRoomHandles() {
            return Stream.empty();
        }
    }
}
