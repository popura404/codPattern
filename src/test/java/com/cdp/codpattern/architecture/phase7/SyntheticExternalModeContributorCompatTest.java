package com.cdp.codpattern.architecture.phase7;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.AreaLayerDefinition;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemaRegistry;
import com.cdp.codpattern.app.match.editor.ObjectFeatureDefinition;
import com.cdp.codpattern.app.match.editor.PointLayerDefinition;
import com.cdp.codpattern.app.match.extension.ModeDefinitionContributions;
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
import com.cdp.codpattern.app.match.runtime.player.ModePlayerLoginRouter;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class SyntheticExternalModeContributorCompatTest {
    private static final String GAME_TYPE = "external_fixture";
    private static final String ALIAS = "fixture_alias";

    private SyntheticExternalModeContributorCompatTest() {
    }

    public static void main(String[] args) {
        require(SyntheticExternalModeContributorCompatTest.class.getClassLoader().getResource(
                        "com/cdp/codpattern/app/zombies/bootstrap/ZombiesBootstrap.class") == null,
                "the synthetic external consumer must not rely on Zombies implementation classes");

        GameModeRuntimeProvider runtimeProvider = runtimeProvider();
        ModeMapPersistenceProvider persistenceProvider = persistenceProvider();
        ModeMapEditorSchema editorSchema = editorSchema();
        ClientModePresentation presentation = new ClientModePresentation(
                new ResourceLocation("external_fixture", "textures/gui/preview.png"),
                32,
                18,
                0xFF55CC88,
                "mode.external_fixture.description",
                "external_fixture");
        GameModeDefinition definition = new GameModeDefinition(
                GAME_TYPE,
                List.of(ALIAS),
                "mode.external_fixture",
                "screen.external_fixture.header",
                "/external-fixture create",
                List.of(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.MODE_DEFINED,
                ScoreboardKind.MODE_DEFINED,
                Set.of(ModeCapability.READY_STATE),
                Optional.of(runtimeProvider),
                Optional.of(persistenceProvider),
                Optional.of(editorSchema),
                Optional.of(presentation));

        ModeDefinitionContributor contributor = registrar -> registrar.register(definition);
        ModeDefinitionContributions.register(contributor);
        GameModeBootstrap.registerCommonProviders();

        require(GameModeRegistry.findDefinition(ALIAS).orElseThrow().gameType().equals(GAME_TYPE),
                "external definition and alias must reach the public mode registry");
        require(GameModeRuntimeRegistry.find(ALIAS).orElseThrow() == runtimeProvider,
                "external runtime provider must reach generic runtime routing");
        require(ModeMapPersistenceRegistry.find(ALIAS).orElseThrow() == persistenceProvider,
                "external persistence provider must reach generic persistence routing");
        require(ModeMapEditorSchemaRegistry.find(ALIAS).orElseThrow() == editorSchema,
                "external editor schema must reach generic editor routing");
        ClientModePresentation installedPresentation =
                ClientModePresentationRegistry.find(ALIAS).orElseThrow();
        require(installedPresentation.equals(presentation)
                        && "external_fixture".equals(installedPresentation.previewTexture().getNamespace()),
                "external namespaced client presentation must reach generic client routing");

        ModePlayerLoginContributor loginContributor = new ModePlayerLoginContributor() {
            @Override
            public String id() {
                return "external_fixture.login";
            }

            @Override
            public LoginDisposition onPlayerLogin(net.minecraft.server.level.ServerPlayer player) {
                return LoginDisposition.STOP_SHARED_LOGIN;
            }
        };
        ModePlayerLoginRouter loginRouter = new ModePlayerLoginRouter(List.of(loginContributor));
        require(loginRouter.route(null) == ModePlayerLoginContributor.LoginDisposition.STOP_SHARED_LOGIN,
                "external event contributor must reach the generic login route");

        ModeNetworkPacketSlotRegistry isolatedPackets = new ModeNetworkPacketSlotRegistry();
        AtomicInteger registered = new AtomicInteger();
        AtomicInteger discriminator = new AtomicInteger(700);
        isolatedPackets.install("external_fixture:packet", registered::incrementAndGet);
        require(isolatedPackets.registerOrReserve("external_fixture:packet", discriminator::getAndIncrement),
                "external packet contribution must execute in the isolated registry");
        require(registered.get() == 1 && discriminator.get() == 700,
                "installed external packet must not consume the reserve callback");
        require(!isolatedPackets.registerOrReserve("external_fixture:reserved", discriminator::getAndIncrement)
                        && discriminator.get() == 701,
                "missing isolated contribution must reserve exactly one local discriminator");

        System.out.println("PASS Phase 7 synthetic external/new-mode contributor");
    }

    private static GameModeRuntimeProvider runtimeProvider() {
        return new GameModeRuntimeProvider() {
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
        };
    }

    private static ModeMapPersistenceProvider persistenceProvider() {
        return new ModeMapPersistenceProvider() {
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
                return "external-payload";
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
        };
    }

    private static ModeMapEditorSchema editorSchema() {
        return new ModeMapEditorSchema() {
            @Override
            public List<PointLayerDefinition> pointLayers() {
                return List.of();
            }

            @Override
            public List<AreaLayerDefinition> areaLayers() {
                return List.of();
            }

            @Override
            public List<ObjectFeatureDefinition> objectFeatures() {
                return List.of();
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
