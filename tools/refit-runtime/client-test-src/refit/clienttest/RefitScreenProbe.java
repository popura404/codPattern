package refit.clienttest;

import com.cdp.codpattern.client.gui.refit.BackPackSelectButton;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.client.gui.screen.WeaponMenuScreen;
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
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Drives screen event methods, never the CDP request service or its queue directly. */
final class RefitScreenProbe {
    private static final String MARKER = "screen-probe-" + System.nanoTime();
    private static volatile boolean fixtureReady;
    private static volatile Throwable fixtureFailure;
    private static int step;
    private static int lastStep = -1;
    static final Map<AttachmentType, String> expected = new LinkedHashMap<>();
    private static CompletableFuture<Void> serverCheck;
    private static List<String> initialCatalog;
    private static int firstScopeSlot;
    private static int secondScopeSlot;

    static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || stage().equals("world")) return;
        try {
            ItemStack gun = findFixture();
            var playerData = BackpackConfigRepository.loadOrCreatePlayer(player.getStringUUID(),
                    ConfigPath.SERVERBACKPACK.getPath(player.server));
            var bag = new BackpackConfig.Backpack(MARKER);
            bag.setItem_MAP(slot(), new BackpackConfig.Backpack.ItemData(
                    BuiltInRegistries.ITEM.getKey(gun.getItem()).toString(), 1, gun.getTag().toString()));
            playerData.getBackpacks_MAP().clear();
            playerData.getBackpacks_MAP().put(1, bag);
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
            if (stage().equals("module")) expected.put(AttachmentType.valueOf("MODULE"), "");
            if (stage().equals("hsv")) expected.put(AttachmentType.LASER, "tacz:laser_lopro");
            if (stage().equals("form")) FormProbe.registerExpected();
            fixtureReady = true;
            LogUtils.getLogger().info("CDP_CLIENT_PROBE fixture={} marker={}",
                    TaczGatewayProvider.gateway().resolveRefitGunId(gun), MARKER);
        } catch (Throwable failure) {
            fixtureFailure = failure;
        }
    }

    private static ItemStack findFixture() {
        if (stage().equals("form")) return FormProbe.createFixture();
        var attachments = TaczGatewayProvider.gateway().listAttachmentCandidates();
        if (stage().equals("hsv")) {
            ItemStack gun = GunItemBuilder.create().setId(new ResourceLocation("swpu:m4a1"))
                    .setAmmoCount(0).setAmmoInBarrel(false).build();
            ItemStack laser = attachments.stream().filter(item -> attachmentId(item).equals("tacz:laser_lopro"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Real visible laser_lopro fixture is missing"));
            if (!TaczGatewayProvider.gateway().isValidRefitGun(gun) || !TaczGatewayProvider.gateway().canAttach(gun, laser)) {
                throw new IllegalStateException("Real swpu:m4a1 fixture does not allow laser_lopro");
            }
            IGun.getIGunOrNull(gun).installAttachment(gun, laser.copy());
            return gun;
        }
        for (var entry : TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList()) {
            var gun = GunItemBuilder.create().setId(entry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            var api = IGun.getIGunOrNull(gun);
            if (api == null) continue;
            if (stage().equals("module")) {
                AttachmentType module = AttachmentType.valueOf("MODULE");
                boolean acceptsModule = attachments.stream().anyMatch(attachment -> {
                    var item = IAttachment.getIAttachmentOrNull(attachment);
                    return item != null && item.getType(attachment) == module
                            && TaczGatewayProvider.gateway().canAttach(gun, attachment);
                });
                if (acceptsModule) return gun;
                continue;
            }
            boolean eligible = true;
            for (var type : List.of(AttachmentType.SCOPE, AttachmentType.MUZZLE, AttachmentType.STOCK)) {
                long count = attachments.stream().filter(attachment -> {
                    var attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                    return attachmentApi != null && attachmentApi.getType(attachment) == type
                            && TaczGatewayProvider.gateway().canAttach(gun, attachment);
                }).count();
                if (count < (type == AttachmentType.SCOPE ? 2 : 1)) eligible = false;
            }
            if (eligible) return gun;
        }
        throw new IllegalStateException("No real gun with two scopes, a muzzle and a stock was loaded");
    }

    static void tick() throws Exception {
        if (fixtureFailure != null) throw new IllegalStateException("Server fixture setup failed", fixtureFailure);
        if (!fixtureReady) return;
        Minecraft mc = Minecraft.getInstance();
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
            else if (stage().equals("form")) FormProbe.tick(screen);
            else AdditionalRefitProbe.tick(screen);
            return;
        }
        switch (step) {
            case 0 -> { BackpackClientCache.clear(); mc.setScreen(new PauseScreen(true)); step++; }
            case 1 -> { if (ClientProbeMod.clickButton(screen, "screen.codpattern.backpack.manage_button")) step++; }
            case 2 -> {
                if (!(screen instanceof BackpackMenuScreen)) return;
                var data = BackpackClientCache.get();
                if (data == null || data.getBackpacks_MAP().get(1) == null
                        || !MARKER.equals(data.getBackpacks_MAP().get(1).getName())) return;
                for (var child : screen.children()) {
                    if (child instanceof BackPackSelectButton button && button.getBAGSERIAL() == 1) {
                        screen.setFocused(button); step++; return;
                    }
                }
            }
            case 3 -> { if (ClientProbeMod.clickButton(screen, "screen.codpattern.backpack.action.configure")) step++; }
            case 4, 17 -> {
                if (!(screen instanceof WeaponMenuScreen)) return;
                Field primary = WeaponMenuScreen.class.getDeclaredField(slot() + "Button");
                primary.setAccessible(true);
                screen.setFocused((GuiEventListener) primary.get(screen));
                step++;
            }
            case 5, 18 -> { if (ClientProbeMod.clickButton(screen, "screen.codpattern.attachment_config.button")) step++; }
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
                ClientProbeMod.evidence("rapidClickSlots", List.of(firstScopeSlot, secondScopeSlot));
                if (firstScopeSlot == secondScopeSlot) throw new AssertionError("Candidate slot collision");
                expected.put(AttachmentType.SCOPE, attachmentId(candidate(second)));
                ClientProbeMod.screenshot("scope-before-clicks");
                ClientProbeMod.click(screen, first);
                ClientProbeMod.click(screen, second);
                if (queue().snapshot().size() != 2 || queue().inFlight() == null) {
                    throw new AssertionError("Two immediate clicks did not enter the serialized queue");
                }
                step++;
            }
            case 8 -> { if (acknowledged()) { verifyWorking(); step++; } }
            case 9 -> { if (selectType(screen, AttachmentType.MUZZLE)) { installFirst(screen, AttachmentType.MUZZLE); step++; } }
            case 10 -> { if (acknowledged()) { verifyWorking(); step++; } }
            case 11 -> { if (selectType(screen, AttachmentType.STOCK)) { installFirst(screen, AttachmentType.STOCK); step++; } }
            case 12 -> { if (acknowledged()) { verifyWorking(); step++; } }
            case 13 -> {
                var unload = screen.children().stream().filter(RefitUnloadButton.class::isInstance)
                        .map(RefitUnloadButton.class::cast).findFirst().orElseThrow();
                expected.put(AttachmentType.STOCK, "");
                ClientProbeMod.click(screen, unload); step++;
            }
            case 14 -> { if (acknowledged()) { verifyWorking(); step++; } }
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
                verifyWorking(); step++;
            }
            case 20 -> {
                if (!selectType(screen, AttachmentType.SCOPE)) return;
                var candidates = visibleCandidates(screen);
                if (candidates.size() < 2 || candidates.get(0).getSlotIndex() != firstScopeSlot
                        || candidates.get(1).getSlotIndex() != secondScopeSlot) {
                    throw new AssertionError("Reopening changed scope candidate numbering");
                }
                ClientProbeMod.screenshot("reopened");
                if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("Final ESC was not consumed");
                step++;
            }
            case 21 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySaved(); step++;
            }
            case 22 -> ClientProbeMod.finish(true, "refit_saved_reopened", null);
            default -> throw new AssertionError("Unexpected test phase " + step);
        }
    }

    static String stage() { return System.getProperty("codpattern.clientTestStage", "world"); }
    static String slot() { return System.getProperty("codpattern.clientTestSlot", "primary"); }

    static boolean selectType(Screen screen, AttachmentType type) {
        if (RefitTransform.getOpeningProgress() != 1F || RefitTransform.getTransformProgress() != 1F) return false;
        if (RefitTransform.getCurrentTransformType() == type) return true;
        for (var child : screen.children()) {
            if (child instanceof GunAttachmentSlot slot && slot.getType() == type) {
                if (!slot.isAllow()) throw new AssertionError("Fixture rejects type " + type);
                ClientProbeMod.click(screen, slot);
                return false; // Allow the real animation and render to finish.
            }
        }
        throw new AssertionError("No UI type slot " + type);
    }

    static List<InventoryAttachmentSlot> visibleCandidates(Screen screen) {
        return screen.children().stream().filter(InventoryAttachmentSlot.class::isInstance)
                .map(InventoryAttachmentSlot.class::cast).toList();
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
