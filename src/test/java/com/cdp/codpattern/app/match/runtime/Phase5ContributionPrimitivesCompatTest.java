package com.cdp.codpattern.app.match.runtime;

import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.runtime.network.ModeNetworkPacketSlotRegistry;
import com.cdp.codpattern.client.runtime.ModeClientActionHandlers;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Phase5ContributionPrimitivesCompatTest {
    private Phase5ContributionPrimitivesCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        absentNetworkContributionsReserveTheirLegacySlot();
        opaqueClientActionsDoNotTypeAddonPayloadsInTheCommonBridge();
        clientPresentationsCarryTheirCompleteNamespace();
        futureMainBlockersContainNoZombiesImplementationReferences();
        combinedEntrypointIsOnlyOrderedBootstrapWiring();
        System.out.println("PASS phase5 contribution primitives compat");
    }

    private static void absentNetworkContributionsReserveTheirLegacySlot() {
        ModeNetworkPacketSlotRegistry registry = new ModeNetworkPacketSlotRegistry();
        AtomicInteger discriminator = new AtomicInteger(51);
        require(!registry.registerOrReserve("missing", discriminator::getAndIncrement),
                "an absent addon packet should reserve instead of registering its slot");
        require(discriminator.get() == 52, "the absent addon slot should consume exactly one discriminator");

        AtomicInteger registrations = new AtomicInteger();
        registry.install("installed", registrations::incrementAndGet);
        require(registry.registerOrReserve("installed", discriminator::getAndIncrement),
                "an installed addon packet should execute its registration callback");
        require(registrations.get() == 1 && discriminator.get() == 52,
                "installed packet registration should own allocation and must not also reserve");
    }

    private static void opaqueClientActionsDoNotTypeAddonPayloadsInTheCommonBridge() {
        AtomicInteger handled = new AtomicInteger();
        ModeClientActionHandlers.register("compat:phase5", payload -> {
            if ("payload".equals(payload)) {
                handled.incrementAndGet();
            }
        });
        require(ModeClientActionHandlers.dispatch("compat:phase5", "payload"),
                "installed opaque client actions should dispatch");
        require(handled.get() == 1, "opaque client action payload should reach the installed handler once");
        require(!ModeClientActionHandlers.dispatch("compat:missing", "payload"),
                "missing optional client actions should remain explicit no-ops");
    }

    private static void clientPresentationsCarryTheirCompleteNamespace() {
        ResourceLocation texture = new ResourceLocation("external_mode", "textures/gui/preview.png");
        ClientModePresentation presentation = new ClientModePresentation(
                texture, 16, 9, 0xFFFFFFFF, "mode.external.description", "external");
        require(texture.equals(presentation.previewTexture()),
                "client presentation must preserve an addon-owned texture namespace");
    }

    private static void futureMainBlockersContainNoZombiesImplementationReferences() throws Exception {
        List<Path> paths = List.of(
                Path.of("src/main/java/com/cdp/codpattern/adapter/forge/network/FpsmPacketRegistrar.java"),
                Path.of("src/main/java/com/cdp/codpattern/client/network/ClientPacketBridgeInstaller.java"),
                Path.of("src/main/java/com/cdp/codpattern/command/ModeDebugCommand.java"),
                Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/event/CodTdmEventHandler.java"),
                Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/event/ModeObjectInteractionEventHandler.java"),
                Path.of("src/main/java/com/cdp/codpattern/event/client/ClientModEvents.java"),
                Path.of("src/main/java/com/cdp/codpattern/event/client/TdmVanillaHudSuppressor.java"),
                Path.of("src/main/java/com/cdp/codpattern/config/path/ConfigPath.java"),
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/FPSMEvents.java"),
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/FpsmClientPacketHandler.java"),
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/FPSMItemRegister.java"),
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/FpsmClientPacketBridge.java"));
        for (Path path : paths) {
            String source = Files.readString(path);
            require(!source.contains("app.zombies")
                            && !source.contains("client.zombies")
                            && !source.contains("OpenZombies")
                            && !source.contains("ZombiesDeployTool")
                            && !source.contains("ZombiesBoxInteractionBlock")
                            && !source.contains("ZombiesHudOverlay"),
                    "future-main blocker still types a Zombies implementation: " + path);
        }
    }

    private static void combinedEntrypointIsOnlyOrderedBootstrapWiring() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/cdp/codpattern/CodPattern.java"));
        int core = source.indexOf("CoreBootstrap.install(modEventBus);");
        int zombies = source.indexOf("ZombiesBootstrap.install(modEventBus);");
        require(core >= 0 && zombies > core, "combined entrypoint should install core before Zombies");
        require(!source.contains("@SubscribeEvent") && !source.contains("ModNetworkChannel.register()"),
                "combined entrypoint should contain no lifecycle implementation beyond ordered wiring");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
