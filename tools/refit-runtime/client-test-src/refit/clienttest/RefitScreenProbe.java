package refit.clienttest;

import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.client.refit.RefitOperationQueue;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.cdp.codpattern.config.backpack.BackpackClientCache;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitAction;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.lwjgl.glfw.GLFW;

/** Drives screen event methods, never the CDP request service or its queue directly. */
final class RefitScreenProbe {
    private static final String MARKER = "screen-probe-" + System.nanoTime();
    private static volatile boolean fixtureReady;
    private static volatile Throwable fixtureFailure;
    private static boolean hsvFixtureScheduled;
    private static int step;
    private static int lastStep = -1;
    static final Map<AttachmentType, String> expected = new LinkedHashMap<>();
    private static CompletableFuture<Void> serverCheck;
    private static List<String> initialCatalog;
    private static int firstScopeSlot;
    private static int secondScopeSlot;
    private static final CountDownLatch releaseFirstInstall = new CountDownLatch(1);

    static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || stage().equals("world") || stage().equals("hsv")) return;
        try {
            prepareFixture(player, findFixture());
        } catch (Throwable failure) {
            fixtureFailure = failure;
        }
    }

    private static void prepareFixture(ServerPlayer player, ItemStack gun) {
        try {
            var playerData = BackpackConfigRepository.loadOrCreatePlayer(player.getStringUUID(),
                    ConfigPath.SERVERBACKPACK.getPath(player.server));
            var bag = new BackpackConfig.Backpack(MARKER);
            bag.setItem_MAP(slot(), new BackpackConfig.Backpack.ItemData(
                    BuiltInRegistries.ITEM.getKey(gun.getItem()).toString(), 1, gun.getTag().toString()));
            playerData.getBackpacks_MAP().clear();
            playerData.getBackpacks_MAP().put(1, bag);
            playerData.getBackpacks_MAP().put(2, new BackpackConfig.Backpack(MARKER + "-other"));
            playerData.setSelectedBackpack(1);
            BackpackConfigRepository.save();
            var filter = WeaponFilterConfigRepository.loadOrCreate(ConfigPath.SERVER_FILTER.getPath(player.server));
            filter.setBlockedItemNamespaces(List.of());
            filter.setBlockedWeaponIds(List.of());
            filter.setBlockedAttachmentNamespaces(List.of());
            filter.setBlockedAttachmentIds(List.of());
            WeaponFilterConfigRepository.save(filter);
            player.setGameMode(GameType.CREATIVE);
            expected.put(AttachmentType.SCOPE, "");
            expected.put(AttachmentType.MUZZLE, "");
            expected.put(AttachmentType.STOCK, "");
            if (stage().equals("hsv")) {
                var installedLaser = IGun.getIGunOrNull(gun).getAttachment(gun, AttachmentType.LASER);
                expected.put(AttachmentType.LASER, attachmentId(installedLaser));
            }
            fixtureReady = true;
            LogUtils.getLogger().info("CDP_CLIENT_PROBE fixture={} marker={}",
                    TaczGatewayProvider.gateway().resolveGunId(gun), MARKER);
        } catch (Throwable failure) {
            fixtureFailure = failure;
        }
    }

    private static ItemStack findFixture() {
        var attachments = TaczGatewayProvider.gateway().listAttachmentCandidates();
        boolean hsv = stage().equals("hsv");
        boolean large = stage().equals("large-catalog");
        var lasers = hsv ? attachments.stream().filter(HsvSliderProbe::hasEditableLaser).toList() : List.<ItemStack>of();
        int editableGuns = 0;
        var guns = TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList();
        if (large) {
            guns = guns.stream().sorted(Comparator.comparing(entry ->
                    !entry.getValue().getGunData().getBuiltInAttachments().containsKey(AttachmentType.SCOPE))).toList();
        }
        for (var entry : guns) {
            var gun = GunItemBuilder.create().setId(entry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            var api = IGun.getIGunOrNull(gun);
            if (api == null) continue;
            if (hsv) {
                var display = TimelessAPI.getGunDisplay(gun).orElse(null);
                if (display == null || display.getLaserConfig() == null || !display.getLaserConfig().canEdit()) continue;
                editableGuns++;
                for (ItemStack laser : lasers) {
                    if (TaczGatewayProvider.gateway().canAttach(gun, laser)) {
                        api.installAttachment(gun, laser.copy());
                        return gun;
                    }
                }
                continue;
            }
            boolean eligible = true;
            for (var type : List.of(AttachmentType.SCOPE, AttachmentType.MUZZLE, large ? AttachmentType.LASER : AttachmentType.STOCK)) {
                long count = attachments.stream().filter(attachment -> {
                    var attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                    return attachmentApi != null && attachmentApi.getType(attachment) == type
                            && TaczGatewayProvider.gateway().canAttach(gun, attachment);
                }).count();
                if (count < (type == AttachmentType.SCOPE ? 2 : 1)) eligible = false;
            }
            if (eligible) return gun;
        }
        if (hsv) throw new IllegalStateException("No compatible editable HSV pair among " + editableGuns
                + " editable guns and " + lasers.size() + " editable laser attachments");
        throw new IllegalStateException("No real gun loaded with two scopes, a muzzle and a " + (large ? "laser" : "stock"));
    }

    static void tick() throws Exception {
        if (fixtureFailure != null) throw new IllegalStateException("Server fixture setup failed", fixtureFailure);
        Minecraft mc = Minecraft.getInstance();
        if (!fixtureReady) {
            // Display indices are populated on the client after the integrated server's login event.
            if (stage().equals("hsv") && !hsvFixtureScheduled
                    && !TimelessAPI.getAllClientGunIndex().isEmpty() && !TimelessAPI.getAllClientAttachmentIndex().isEmpty()) {
                var gun = findFixture();
                var playerId = mc.player.getUUID();
                var server = mc.getSingleplayerServer();
                hsvFixtureScheduled = true;
                server.execute(() -> prepareFixture(server.getPlayerList().getPlayer(playerId), gun));
            }
            return;
        }
        if (serverCheck != null) {
            if (!serverCheck.isDone()) return;
            serverCheck.join();
            serverCheck = null;
        }
        if (lastStep != step) {
            LogUtils.getLogger().info("CDP_CLIENT_PROBE refit_step={}", step);
            lastStep = step;
        }
        Screen screen = mc.screen;
        if (step >= 6 && !stage().equals("refit")) {
            if (initialCatalog == null) {
                if (!(screen instanceof CodGunRefitScreen) || !AttachmentRefitClientState.acceptsTaczOperations()) return;
                initialCatalog = catalogIds();
                ClientProbeMod.evidence("realCatalogCount", initialCatalog.size());
                LogUtils.getLogger().info("CDP_CLIENT_PROBE real_catalog_count={}", initialCatalog.size());
            }
            if (stage().equals("hot-filter")) HotFilterProbe.tick(screen);
            else if (stage().equals("large-catalog")) LargeCatalogProbe.tick(screen);
            else if (stage().equals("hsv")) HsvSliderProbe.tick(screen);

            else AdditionalRefitProbe.tick(screen);
            return;
        }
        switch (step) {
            case 0 -> {
                BackpackClientCache.clear();
                var pause = new PauseScreen(true);
                mc.setScreen(pause);
                mc.setScreen(new GunRefitScreen());
                if (mc.screen != pause || AttachmentRefitClientState.getSessionId() != null) {
                    throw new AssertionError("Opening TaCZ refit replaced the current screen or started a session");
                }
                ClientProbeMod.evidence("nativeRefitBlocked", true);
                step++;
            }
            case 1 -> { if (ClientProbeMod.clickButton(screen, "screen.codpattern.backpack.manage_button")) step++; }
            case 2 -> {
                if (!(screen instanceof BackpackMenuScreen backpack)) return;
                var data = BackpackClientCache.get();
                if (data == null || data.getBackpacks_MAP().get(1) == null
                        || !MARKER.equals(data.getBackpacks_MAP().get(1).getName())) return;
                for (var child : screen.children()) {
                    if (child instanceof Button button && MARKER.equals(button.getMessage().getString())) {
                        double x = button.getX() + 2;
                        double y = button.getY() + button.getHeight() / 2.0;
                        backpack.mouseMoved(backpack.screenX(x), backpack.screenY(y));
                        step++;
                        return;
                    }
                }
            }
            case 3 -> { if (ClientProbeMod.clickButton(screen, "screen.codpattern.loadout.edit")) step++; }
            case 4, 17 -> {
                if (hoverConfiguredSlot(screen)) {
                    verifyBagList((BackpackMenuScreen) screen, true);
                    if (step == 4 && stage().equals("refit")) ClientProbeMod.screenshot("backpack-detail-locked-list");
                    step++;
                }
            }
            case 5, 18 -> { if (openConfiguredRefit(screen)) step++; }
            case 6 -> {
                if (!(screen instanceof CodGunRefitScreen) || !AttachmentRefitClientState.acceptsTaczOperations()) return;
                initialCatalog = catalogIds();
                ClientProbeMod.evidence("realCatalogCount", initialCatalog.size());
                if (!selectType(screen, AttachmentType.SCOPE)) return;
                step++;
            }
            case 7 -> {
                var visible = visibleCandidates(screen);
                if (visible.size() < 2) throw new IllegalStateException("Scope fixture must show two candidates");
                var first = visible.get(0);
                var second = visible.get(1);
                firstScopeSlot = first.getSlotIndex();
                secondScopeSlot = second.getSlotIndex();
                if (firstScopeSlot == secondScopeSlot) throw new AssertionError("Candidate slot collision");
                expected.put(AttachmentType.SCOPE, attachmentId(candidate(first)));
                ClientProbeMod.screenshot("scope-before-clicks");
                // Hold the isolated server's first ACK while the real camera animates and the player reopens scopes.
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        releaseFirstInstall.await(8, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                ClientProbeMod.click(screen, first);
                assertSlotPage(screen);
                if (queue().snapshot().size() != 1 || queue().inFlight() == null) {
                    throw new AssertionError("Choosing the first scope did not queue exactly one installation");
                }
                step++;
            }
            case 8 -> {
                if (!selectType(screen, AttachmentType.SCOPE)) return;
                var scopes = visibleCandidates(screen);
                if (scopes.size() < 2 || scopes.get(1).getSlotIndex() != secondScopeSlot) {
                    throw new AssertionError("Reopened scope choices lost their slot identity");
                }
                var second = scopes.get(1);
                expected.put(AttachmentType.SCOPE, attachmentId(candidate(second)));
                screen.setFocused(second);
                if (!screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)) throw new AssertionError("Keyboard selection was rejected");
                assertSlotPage(screen);
                var queued = queue().snapshot().stream().map(RefitAction.class::cast).toList();
                releaseFirstInstall.countDown();
                if (queued.size() != 2 || queued.stream().anyMatch(action -> action.kind() != RefitAction.Kind.INSTALL)
                        || queued.get(0).candidateSlot() != firstScopeSlot || queued.get(1).candidateSlot() != secondScopeSlot
                        || queue().inFlight() != queued.get(0)) {
                    throw new AssertionError("The two selections did not queue in order: " + queued);
                }
                ClientProbeMod.evidence("rapidClickSlots", List.of(firstScopeSlot, secondScopeSlot));
                step++;
            }
            case 9 -> {
                if (!acknowledged() || !selectType(screen, AttachmentType.MUZZLE)) return;
                verifyWorking();
                installFirst(screen, AttachmentType.MUZZLE);
                step++;
            }
            case 10 -> { if (acknowledged()) { verifyWorking(); step++; } }
            case 11 -> { if (selectType(screen, AttachmentType.STOCK)) { installFirst(screen, AttachmentType.STOCK); step++; } }
            case 12 -> { if (acknowledged()) { verifyWorking(); step++; } }
            case 13 -> {
                if (!selectType(screen, AttachmentType.STOCK)) return;
                pressNoAttachment(screen, AttachmentType.STOCK, false);
                expected.put(AttachmentType.STOCK, "");
                step++;
            }
            case 14 -> {
                if (!acknowledged() || !selectType(screen, AttachmentType.STOCK)) return;
                verifyWorking();
                pressNoAttachment(screen, AttachmentType.STOCK, true);
                step++;
            }
            case 15 -> {
                ClientProbeMod.screenshot("before-save");
                if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("ESC was not consumed");
                step++;
            }
            case 16 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySaved(); step++;
            }
            case 19 -> {
                if (!(screen instanceof CodGunRefitScreen) || !AttachmentRefitClientState.acceptsTaczOperations()) return;
                selectType(screen, AttachmentType.SCOPE);
                if (RefitTransform.getCurrentTransformType() != AttachmentType.SCOPE) return;
                // Cancel in the same client tick as the category click, before the camera has settled.
                escapeToSlots(screen);
                verifyWorking(); step++;
            }
            case 20 -> {
                if (!selectType(screen, AttachmentType.SCOPE)) return;
                var candidates = visibleCandidates(screen);
                if (candidates.size() < 2 || candidates.get(0).getSlotIndex() != firstScopeSlot
                        || candidates.get(1).getSlotIndex() != secondScopeSlot || !rowSelected(candidates.get(1))) {
                    throw new AssertionError("Reopening changed scope candidate numbering");
                }
                ClientProbeMod.screenshot("reopened");
                escapeToSlots(screen);
                if (!screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)) throw new AssertionError("Final ESC was not consumed");
                step++;
            }
            case 21 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySaved(); step++;
            }
            case 22 -> {
                if (!(screen instanceof BackpackMenuScreen backpack) || !hoverConfiguredSlot(screen)) return;
                verifyBagList(backpack, true);
                backpack.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                step++;
            }
            case 23 -> {
                verifyBagList((BackpackMenuScreen) screen, false);
                ClientProbeMod.finish(true, "refit_saved_reopened", null);
            }
            default -> throw new AssertionError("Unexpected test phase " + step);
        }
    }

    static String stage() { return System.getProperty("codpattern.clientTestStage", "world"); }
    static String slot() { return System.getProperty("codpattern.clientTestSlot", "primary"); }

    static boolean openConfiguredRefit(Screen screen) {
        // Mouse position or a config sync can change between ticks; exercise the owning card and its action together.
        return hoverConfiguredSlot(screen) && ClientProbeMod.clickButton(screen, "screen.codpattern.loadout.refit");
    }

    static boolean hoverConfiguredSlot(Screen screen) {
        if (!(screen instanceof BackpackMenuScreen backpack)) return false;
        String key = "screen.codpattern.loadout.slot." + slot();
        for (var child : screen.children()) {
            if (child instanceof Button button && button.visible && button.active
                    && key.equals(ClientProbeMod.key(button.getMessage()))) {
                double x = button.getX() + button.getWidth() / 2.0;
                double y = button.getY() + button.getHeight() / 2.0;
                backpack.mouseMoved(backpack.screenX(x - 1), backpack.screenY(y));
                backpack.mouseMoved(backpack.screenX(x), backpack.screenY(y));
                // The editor ignores mouse input until its transition ends.
                return screen.children().stream().anyMatch(childWidget -> childWidget instanceof Button refit
                        && refit.visible && refit.active
                        && "screen.codpattern.loadout.refit".equals(ClientProbeMod.key(refit.getMessage())));
            }
        }
        return false;
    }

    static boolean selectType(Screen screen, AttachmentType type) {
        if (RefitTransform.getOpeningProgress() != 1F || RefitTransform.getTransformProgress() != 1F) return false;
        var current = RefitTransform.getCurrentTransformType();
        if (current == type) return true;
        if (current != AttachmentType.NONE) {
            if (!ClientProbeMod.clickButton(screen, "screen.codpattern.refit.back_to_slots")) {
                throw new AssertionError("Cannot return from " + current + " to the attachment slots");
            }
            return false;
        }
        for (var child : screen.children()) {
            if (child instanceof GunAttachmentSlot slot && slot.visible && slot.active && slot.getType() == type) {
                if (!slot.isAllow()) throw new AssertionError("Fixture rejects type " + type);
                ClientProbeMod.click(screen, slot);
                return false; // Allow the real animation and render to finish.
            }
        }
        throw new AssertionError("No UI type slot " + type);
    }

    static void assertSlotPage(Screen screen) {
        if (Minecraft.getInstance().screen != screen || !(screen instanceof CodGunRefitScreen)
                || !AttachmentRefitClientState.acceptsTaczOperations() || RefitTransform.getCurrentTransformType() != AttachmentType.NONE
                || !visibleCandidates(screen).isEmpty() || noAttachment(screen) != null
                || screen.children().stream().noneMatch(child -> child instanceof GunAttachmentSlot slot && slot.visible && slot.active)) {
            throw new AssertionError("Expected the attachment types in the same open refit session");
        }
    }

    static void escapeToSlots(Screen screen) throws Exception {
        var pending = queue().snapshot();
        if (!screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)) throw new AssertionError("Candidate ESC was rejected");
        assertSlotPage(screen);
        if (!queue().snapshot().equals(pending)) throw new AssertionError("Candidate ESC changed the pending operations");
    }

    private static void verifyBagList(BackpackMenuScreen screen, boolean editing) {
        var other = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(row -> (MARKER + "-other").equals(row.getMessage().getString()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing the second bag row"));
        var create = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(row -> "screen.codpattern.loadout.create".equals(ClientProbeMod.key(row.getMessage())))
                .findFirst().orElseThrow(() -> new AssertionError("Missing the new bag row"));
        if (!other.visible || other.active == editing || !create.visible || create.active == editing) {
            throw new AssertionError("Unexpected bag list availability");
        }
        if (editing) {
            double x = screen.screenX(other.getX() + 2), y = screen.screenY(other.getY() + other.getHeight() / 2.0);
            if (screen.mouseClicked(x, y, 0) || screen.mouseClicked(x, y, 1)) {
                throw new AssertionError("The locked bag accepted a mouse action");
            }
            for (int i = 0; i <= screen.children().size(); i++) {
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                if (screen.getFocused() == other || screen.getFocused() == create) {
                    throw new AssertionError("Tab focused a locked bag list entry");
                }
            }
            if (!hoverConfiguredSlot(screen)) {
                throw new AssertionError("The locked list accepted an action or lost the bag being edited");
            }
        }
    }

    static List<InventoryAttachmentSlot> visibleCandidates(Screen screen) {
        return screen.children().stream().filter(InventoryAttachmentSlot.class::isInstance)
                .map(InventoryAttachmentSlot.class::cast).filter(slot -> slot.visible).toList();
    }

    static Button noAttachment(Screen screen) {
        if (screen.children().stream().anyMatch(RefitUnloadButton.class::isInstance)) {
            throw new AssertionError("A standalone unload button is still registered");
        }
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && "screen.codpattern.refit.no_attachment".equals(ClientProbeMod.key(button.getMessage())))
                .findFirst().orElse(null);
    }

    static Button verifyNoAttachment(Screen screen, boolean selected) throws ReflectiveOperationException {
        var button = noAttachment(screen);
        if (button == null) throw new AssertionError("The no-attachment option is not visible");
        if (rowSelected(button) != selected) throw new AssertionError("Incorrect no-attachment highlight");
        return button;
    }

    static boolean rowSelected(Button button) throws ReflectiveOperationException {
        var selection = button.getClass().getDeclaredMethod("selected");
        selection.setAccessible(true);
        return (boolean) selection.invoke(button);
    }

    static void pressNoAttachment(Screen screen, AttachmentType type, boolean wasEmpty) throws Exception {
        ClientProbeMod.click(screen, verifyNoAttachment(screen, wasEmpty));
        var pending = queue().snapshot();
        if (wasEmpty ? !pending.isEmpty() : pending.size() != 1 || !(pending.get(0) instanceof RefitAction action)
                || action.kind() != RefitAction.Kind.UNLOAD || !type.name().equals(action.attachmentType())) {
            throw new AssertionError("Unexpected actions after pressing no attachment for " + type + ": " + pending);
        }
        assertSlotPage(screen);
    }

    static boolean scrollCandidates(Screen screen, double delta) {
        var rows = visibleCandidates(screen);
        if (rows.isEmpty()) return false;
        AbstractWidget middle = rows.get(rows.size() / 2);
        return screen.mouseScrolled(middle.getX() + 2, middle.getY() + middle.getHeight() / 2.0, delta);
    }

    static ItemStack candidate(InventoryAttachmentSlot slot) throws Exception {
        var field = InventoryAttachmentSlot.class.getDeclaredField("inventory");
        field.setAccessible(true);
        return ((Inventory) field.get(slot)).getItem(slot.getSlotIndex()).copy();
    }

    static void installFirst(Screen screen, AttachmentType type) throws Exception {
        var visible = visibleCandidates(screen);
        if (visible.isEmpty()) throw new AssertionError("No visible " + type + " candidate");
        expected.put(type, attachmentId(candidate(visible.get(0))));
        ClientProbeMod.click(screen, visible.get(0));
        assertSlotPage(screen);
    }

    static RefitOperationQueue<?> queue() throws Exception {
        var field = AttachmentRefitClientState.class.getDeclaredField("operations");
        field.setAccessible(true);
        return (RefitOperationQueue<?>) field.get(null);
    }

    static boolean acknowledged() throws Exception {
        if (AttachmentRefitClientState.getSessionId() == null) throw new AssertionError("Unexpected session termination");
        return queue().snapshot().isEmpty();
    }

    static String attachmentId(ItemStack stack) {
        return TaczGatewayProvider.gateway().resolveAttachmentId(stack).orElse("");
    }

    static List<String> catalogIds() {
        return AttachmentRefitClientState.getAttachmentCandidates().stream().map(RefitScreenProbe::attachmentId).toList();
    }

    static void verifyGun(ItemStack gun, Map<AttachmentType, String> snapshot) {
        var api = IGun.getIGunOrNull(gun);
        if (api == null) throw new AssertionError("Missing working gun");
        snapshot.forEach((type, id) -> {
            String actual = attachmentId(api.getAttachment(gun, type));
            if (!id.equals(actual)) throw new AssertionError(type + " expected=" + id + " actual=" + actual);
        });
    }

    static void replaceExpectedCatalog(List<String> replacement) { initialCatalog = List.copyOf(replacement); }

    static void verifyWorking() {
        Minecraft mc = Minecraft.getInstance();
        if (!initialCatalog.equals(catalogIds())) throw new AssertionError("Candidate catalog changed after clicking");
        var snapshot = Map.copyOf(expected);
        verifyGun(AttachmentRefitClientState.resolveRefitScreenInventory(mc.player).getSelected(), snapshot);
        var playerId = mc.player.getUUID();
        var sessionId = AttachmentRefitClientState.getSessionId();
        serverCheck = CompletableFuture.runAsync(() -> {
            var session = AttachmentEditSessionManager.getSession(playerId);
            if (session == null || !sessionId.equals(session.getSessionId())) throw new AssertionError("Missing server session");
            verifyGun(session.getRefitInventory().getSelected(), snapshot);
        }, mc.getSingleplayerServer());
    }

    static void verifySaved() {
        Minecraft mc = Minecraft.getInstance();
        var uuid = mc.player.getStringUUID();
        var snapshot = Map.copyOf(expected);
        serverCheck = CompletableFuture.runAsync(() -> {
            try {
                var config = new Gson().fromJson(Files.readString(ConfigPath.SERVERBACKPACK.getPath(mc.getSingleplayerServer())),
                        BackpackConfig.class);
                var item = config.getPlayerData().get(uuid).getBackpacks_MAP().get(1).getItem_MAP().get(slot());
                if (item.getAttachmentPreset() == null || item.getAttachmentPreset().isBlank()) {
                    throw new AssertionError("No preset was persisted to the actual configuration file");
                }
                verifyGun(BackpackItemStackFactory.create(item), snapshot);
            } catch (Exception failure) {
                throw new IllegalStateException("Saved configuration assertion failed", failure);
            }
        }, mc.getSingleplayerServer());
    }
}
