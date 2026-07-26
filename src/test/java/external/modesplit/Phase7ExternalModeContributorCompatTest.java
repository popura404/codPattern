package external.modesplit;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemaRegistry;
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
import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.app.match.runtime.network.ModeNetworkPacketSlotRegistry;
import com.cdp.codpattern.app.match.runtime.player.ModePlayerLoginContributors;
import com.cdp.codpattern.client.runtime.ModeClientActionHandlers;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Phase 7 fixture deliberately lives outside the product namespace. It proves that a new mode can
 * reach the documented definition, provider, event, network, and client routes without a branch in
 * future-main routing or a dependency on the combined-distribution shim.
 */
public final class Phase7ExternalModeContributorCompatTest {
    private static final String GAME_TYPE = "externalarena";
    private static final String ALIAS = "external_arena";
    private static final String CLIENT_ACTION_ID = "externalarena:open_panel";
    private static final String NETWORK_SLOT_ID = "externalarena:state_sync";

    private Phase7ExternalModeContributorCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        ExternalModeFixture fixture = new ExternalModeFixture();
        fixture.install();

        definitionAndAliasReachThePublicRegistry(fixture);
        runtimePersistenceAndEditorProvidersReachTheirPublicRoutes(fixture);
        loginRecoveryReachesTheProductionEventContributionRoute(fixture);
        packetRegistrationReachesAnIsolatedPublicNetworkRoute(fixture);
        clientPresentationAndOpaqueActionReachPublicClientRoutes(fixture);
        futureMainContainsNoFixtureSpecificRouting();

        System.out.println("PASS phase7 synthetic external mode contributor");
    }

    private static void definitionAndAliasReachThePublicRegistry(ExternalModeFixture fixture) {
        GameModeDefinition definition = GameModeRegistry.findDefinition(ALIAS)
                .orElseThrow(() -> new AssertionError("external definition alias was not registered"));
        require(GAME_TYPE.equals(definition.gameType()), "alias should resolve to the external canonical ID");
        require(definition.hasCapability(ModeCapability.READY_STATE),
                "external definition capabilities should survive contribution");
        require(GameModeRegistry.orderedDefinitions().contains(fixture.definition),
                "the external definition should be visible through normal mode listing");
    }

    private static void runtimePersistenceAndEditorProvidersReachTheirPublicRoutes(ExternalModeFixture fixture) {
        require(GameModeRuntimeRegistry.find(ALIAS).orElseThrow() == fixture.runtimeProvider,
                "external runtime provider should be reachable through the canonical runtime registry");
        require(ModeMapPersistenceRegistry.find(ALIAS).orElseThrow() == fixture.persistenceProvider,
                "external persistence provider should be reachable through the canonical persistence registry");
        require(ModeMapEditorSchemaRegistry.find(ALIAS).orElseThrow() == fixture.editorSchema,
                "external editor schema should be reachable through the canonical editor registry");
    }

    private static void loginRecoveryReachesTheProductionEventContributionRoute(ExternalModeFixture fixture) {
        ModePlayerLoginContributor.LoginDisposition disposition = ModePlayerLoginContributors.route(null);
        require(disposition == ModePlayerLoginContributor.LoginDisposition.CONTINUE,
                "the external event contributor should preserve shared login continuation");
        require(fixture.loginCalls.get() == 1,
                "the external login contributor should be invoked exactly once through the production route");
    }

    private static void packetRegistrationReachesAnIsolatedPublicNetworkRoute(ExternalModeFixture fixture) {
        AtomicInteger reservedDiscriminator = new AtomicInteger(700);
        require(fixture.networkRegistry.registerOrReserve(
                        NETWORK_SLOT_ID,
                        reservedDiscriminator::getAndIncrement),
                "the installed external packet contribution should own its isolated slot");
        require(fixture.networkRegistrations.get() == 1,
                "the isolated network route should execute the external registration exactly once");
        require(reservedDiscriminator.get() == 700,
                "an installed external contribution must not consume the reservation fallback");
    }

    private static void clientPresentationAndOpaqueActionReachPublicClientRoutes(ExternalModeFixture fixture) {
        ClientModePresentation presentation = ClientModePresentationRegistry.find(ALIAS)
                .orElseThrow(() -> new AssertionError("external client presentation was not registered"));
        require(new ResourceLocation("externalarena", "textures/gui/mode_preview.png")
                        .equals(presentation.previewTexture()),
                "the external presentation should retain its addon-owned resource namespace");

        require(ModeClientActionHandlers.dispatch(CLIENT_ACTION_ID, "open"),
                "the installed external client action should be reachable through the opaque route");
        require(fixture.clientActionCalls.get() == 1,
                "the external client action should receive its payload exactly once");
    }

    private static void futureMainContainsNoFixtureSpecificRouting() throws Exception {
        Path mainJava = Path.of("src/main/java");
        try (Stream<Path> sources = Files.walk(mainJava)) {
            Optional<Path> fixtureBranch = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsFixtureIdentity(path))
                    .findFirst();
            require(fixtureBranch.isEmpty(),
                    "synthetic mode identity leaked into generic production routing: "
                            + fixtureBranch.map(Path::toString).orElse(""));
        }

        String ownSource = Files.readString(Path.of(
                "src/test/java/external/modesplit/Phase7ExternalModeContributorCompatTest.java"));
        require(!ownSource.contains("app.zombies")
                        && !ownSource.contains("ZombiesBootstrap")
                        && !ownSource.contains("com.cdp.codpattern.CodPattern"),
                "the external fixture must not depend on addon implementations or the composition shim");
    }

    private static boolean containsFixtureIdentity(Path source) {
        try {
            String text = Files.readString(source);
            return text.contains(GAME_TYPE)
                    || text.contains(ALIAS)
                    || text.contains(CLIENT_ACTION_ID)
                    || text.contains(NETWORK_SLOT_ID);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to inspect production source " + source, exception);
        }
    }

    private static final class ExternalModeFixture {
        private final AtomicInteger loginCalls = new AtomicInteger();
        private final AtomicInteger clientActionCalls = new AtomicInteger();
        private final AtomicInteger networkRegistrations = new AtomicInteger();
        private final ModeNetworkPacketSlotRegistry networkRegistry = new ModeNetworkPacketSlotRegistry();
        private final GameModeRuntimeProvider runtimeProvider = new ExternalRuntimeProvider();
        private final ModeMapPersistenceProvider persistenceProvider = new ExternalPersistenceProvider();
        private final ModeMapEditorSchema editorSchema = new EmptyEditorSchema();
        private final GameModeDefinition definition = new GameModeDefinition(
                GAME_TYPE,
                List.of(ALIAS),
                "mode.externalarena",
                "screen.externalarena.room.header",
                "/externalarena create",
                List.of(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.OBJECTIVE_LOOP,
                ScoreboardKind.PLAYER_SCORE,
                Set.of(ModeCapability.READY_STATE),
                Optional.of(runtimeProvider),
                Optional.of(persistenceProvider),
                Optional.of(editorSchema),
                Optional.of(new ClientModePresentation(
                        new ResourceLocation("externalarena", "textures/gui/mode_preview.png"),
                        320,
                        180,
                        0xFF55CCAA,
                        "mode.externalarena.description",
                        "externalarena")));

        private void install() {
            ModeDefinitionContributor definitionContributor =
                    registrar -> registrar.register(definition);
            definitionContributor.contribute(GameModeRegistry::registerDefinition);
            GameModeBootstrap.registerCommonProviders();

            ModePlayerLoginContributors.register(new ModePlayerLoginContributor() {
                @Override
                public String id() {
                    return "externalarena.login";
                }

                @Override
                public LoginDisposition onPlayerLogin(net.minecraft.server.level.ServerPlayer player) {
                    loginCalls.incrementAndGet();
                    return LoginDisposition.CONTINUE;
                }
            });

            networkRegistry.install(NETWORK_SLOT_ID, networkRegistrations::incrementAndGet);
            ModeClientActionHandlers.register(CLIENT_ACTION_ID, payload -> {
                if ("open".equals(payload)) {
                    clientActionCalls.incrementAndGet();
                }
            });
        }
    }

    private static final class ExternalRuntimeProvider implements GameModeRuntimeProvider {
        @Override
        public String gameType() {
            return GAME_TYPE;
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

    private static final class ExternalPersistenceProvider implements ModeMapPersistenceProvider {
        @Override
        public String gameType() {
            return GAME_TYPE;
        }

        @Override
        public BaseMap createMap(ServerLevel level, CommonModeMapData commonData, Object payload) {
            return null;
        }

        @Override
        public Object capturePayload(BaseMap map) {
            return null;
        }

        @Override
        public void applyPayload(BaseMap map, Object payload) {
        }

        @Override
        public void save(BaseMap map, FPSMDataManager manager) {
        }

        @Override
        public FPSMDataManager.DeleteStatus delete(String mapName, FPSMDataManager manager) {
            return null;
        }
    }

    private static final class EmptyEditorSchema implements ModeMapEditorSchema {
        @Override
        public List<com.cdp.codpattern.app.match.editor.PointLayerDefinition> pointLayers() {
            return List.of();
        }

        @Override
        public List<com.cdp.codpattern.app.match.editor.AreaLayerDefinition> areaLayers() {
            return List.of();
        }

        @Override
        public List<com.cdp.codpattern.app.match.editor.ObjectFeatureDefinition> objectFeatures() {
            return List.of();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
