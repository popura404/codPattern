package refit.clienttest;

import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.cdp.codpattern.config.backpack.BackpackClientCache;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Real Vanguard conversion and reversal through UI, preserving the temporarily unusable attachments. */
final class FormProbe {
    private static final String BASE = "ts:ak308", FORM = "ts:ak308_gpr20", MODULE = "ts:gpr20obr28";
    private static final Map<AttachmentType, ItemStack> dormant = new LinkedHashMap<>();
    private static CompoundTag originalNbt;
    private static ItemStack module;
    private static CompletableFuture<Void> serverCheck;
    private static int step;
    private static int reportedStep = -1;
    private static int pagesVisited;
    private static List<String> candidateIds;
    private static long fixtureStamp;
    private static long distributionStarted;
    private static int distributionsReceived;

    private FormProbe() { }

    static ItemStack createFixture() {
        ItemStack gun = gun(BASE);
        ItemStack derived = gun(FORM);
        var gateway = TaczGatewayProvider.gateway();
        if (!gateway.isValidRefitGun(gun) || !gateway.isValidRefitGun(derived)) {
            throw new IllegalStateException("Missing the real Vanguard AK308 and GPR20 form fixtures");
        }
        List<ItemStack> candidates = gateway.listAttachmentCandidates().stream()
                .sorted(Comparator.comparing(RefitScreenProbe::attachmentId)).toList();
        module = candidates.stream().filter(item -> MODULE.equals(RefitScreenProbe.attachmentId(item)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing the real GPR20 conversion module"));
        if (!gateway.canAttach(gun, module) || gateway.canAttach(derived, module)) {
            throw new AssertionError("Fixture must accept the module only in its base form");
        }
        IGun api = IGun.getIGunOrNull(gun);
        IGun derivedApi = IGun.getIGunOrNull(derived);
        for (AttachmentType type : List.of(AttachmentType.SCOPE, AttachmentType.STOCK, AttachmentType.LASER)) {
            ItemStack attachment = candidates.stream().filter(item -> {
                var itemApi = IAttachment.getIAttachmentOrNull(item);
                return itemApi != null && itemApi.getType(item) == type && gateway.canAttach(gun, item);
            }).findFirst().orElseThrow(() -> new IllegalStateException("Missing real base-compatible " + type)).copy();
            if (derivedApi.allowAttachmentType(derived, type)) {
                throw new AssertionError("Derived form must temporarily disable " + type);
            }
            attachment.getOrCreateTag().putString("FormProbePreserved", type.name());
            api.installAttachment(gun, attachment.copy());
            dormant.put(type, attachment.copy());
        }
        gun.getOrCreateTag().putInt("FormProbePreservedGun", 719);
        fixtureStamp = System.nanoTime();
        gun.getOrCreateTag().putLong("FormProbeDistributionRun", fixtureStamp);
        originalNbt = gun.getTag().copy();
        return gun;
    }

    static void registerExpected() {
        dormant.forEach((type, item) -> RefitScreenProbe.expected.put(type, RefitScreenProbe.attachmentId(item)));
        RefitScreenProbe.expected.put(moduleType(), "");
    }

    static void tick(Screen screen) throws Exception {
        if (serverCheck != null) {
            if (!serverCheck.isDone()) return;
            serverCheck.join();
            serverCheck = null;
        }
        if (reportedStep != step) {
            LogUtils.getLogger().info("CDP_CLIENT_PROBE form_step={}", step);
            reportedStep = step;
        }
        switch (step) {
            case 0 -> {
                if (!active(screen)) return;
                candidateIds = RefitScreenProbe.catalogIds();
                verifyWorking(BASE, false);
                ClientProbeMod.evidence("formBase", BASE);
                ClientProbeMod.evidence("formDerived", FORM);
                ClientProbeMod.evidence("formModule", MODULE);
                ClientProbeMod.evidence("formDormantAttachments", dormant.entrySet().stream()
                        .map(entry -> entry.getKey().name() + "=" + RefitScreenProbe.attachmentId(entry.getValue())).toList());
                step++;
            }
            case 1 -> { if (RefitScreenProbe.selectType(screen, moduleType())) step++; }
            case 2 -> {
                InventoryAttachmentSlot candidate = visibleModule(screen);
                if (candidate == null) {
                    if (nextPage(screen)) return;
                    throw new AssertionError("Actual MODULE pages do not offer the real conversion module");
                }
                ClientProbeMod.evidence("formModuleCandidateSlot", candidate.getSlotIndex());
                RefitScreenProbe.expected.put(moduleType(), MODULE);
                ClientProbeMod.click(screen, candidate);
                step++;
            }
            case 3 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                verifyWorking(FORM, true);
                if (TaczGatewayProvider.gateway().canAttach(clientGun(), module)) {
                    throw new AssertionError("The converted gun incorrectly permits module reinstallation");
                }
                step++;
            }
            case 4 -> {
                if (!RefitScreenProbe.selectType(screen, moduleType())) return;
                if (visibleModule(screen) != null) throw new AssertionError("Converted MODULE page offers forbidden reinstallation");
                if (nextPage(screen)) return;
                unloadButton(screen); // Installed MODULE remains reachable even though it is no longer a candidate.
                step++;
            }
            case 5 -> { ClientProbeMod.screenshot("form-derived-before-save"); close(screen); step++; }
            case 6 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySaved(FORM, true);
                step++;
            }
            case 7 -> { verifyClientFactory(FORM, true); distributeSaved(FORM, true); step = 30; }
            case 30 -> { if (receivedDistribution(FORM, true)) step = 8; }
            case 8 -> { if (AdditionalRefitProbe.focusPrimary(screen)) step++; }
            case 9 -> { if (ClientProbeMod.clickButton(screen, "screen.codpattern.attachment_config.button")) step++; }
            case 10 -> { if (active(screen)) { verifyWorking(FORM, true); step++; } }
            case 11 -> { if (RefitScreenProbe.selectType(screen, moduleType())) step++; }
            case 12 -> {
                ClientProbeMod.screenshot("form-derived-reopened");
                RefitScreenProbe.expected.put(moduleType(), "");
                ClientProbeMod.click(screen, unloadButton(screen));
                step++;
            }
            case 13 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                verifyWorking(BASE, false);
                step++;
            }
            case 14 -> { ClientProbeMod.screenshot("form-base-after-unload"); close(screen); step++; }
            case 15 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySaved(BASE, false);
                step++;
            }
            case 16 -> { verifyClientFactory(BASE, false); distributeSaved(BASE, false); step = 31; }
            case 31 -> { if (receivedDistribution(BASE, false)) step = 17; }
            case 17 -> { if (AdditionalRefitProbe.focusPrimary(screen)) step++; }
            case 18 -> { if (ClientProbeMod.clickButton(screen, "screen.codpattern.attachment_config.button")) step++; }
            case 19 -> { if (active(screen)) { verifyWorking(BASE, false); step++; } }
            case 20 -> {
                if (!RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                ClientProbeMod.screenshot("form-base-reopened");
                close(screen);
                step++;
            }
            case 21 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySaved(BASE, false);
                step++;
            }
            case 22 -> {
                if (distributionsReceived != 2) throw new AssertionError("Both saved forms must be issued to the real client");
                ClientProbeMod.evidence("formDistributionsReceived", distributionsReceived);
                ClientProbeMod.evidence("formDistributionInventorySlot", distributionSlot());
                ClientProbeMod.evidence("formDistributionEntry", "server administrator command: cdp distribute <player name>");
                ClientProbeMod.finish(true, "form_saved_reopened_reversed", null);
            }
            default -> throw new AssertionError("Unknown form phase " + step);
        }
    }

    private static void verifyWorking(String expectedId, boolean installed) {
        if (!candidateIds.equals(RefitScreenProbe.catalogIds())) {
            throw new AssertionError("Form conversion changed the fixed candidate directory");
        }
        assertGun(clientGun(), expectedId, installed);
        var mc = Minecraft.getInstance();
        var playerId = mc.player.getUUID();
        var sessionId = AttachmentRefitClientState.getSessionId();
        serverCheck = CompletableFuture.runAsync(() -> {
            var session = AttachmentEditSessionManager.getSession(playerId);
            if (session == null || !sessionId.equals(session.getSessionId())) throw new AssertionError("Missing form session");
            assertGun(session.getRefitInventory().getSelected(), expectedId, installed);
        }, mc.getSingleplayerServer());
    }

    private static void verifySaved(String expectedId, boolean installed) {
        var mc = Minecraft.getInstance();
        var playerId = mc.player.getStringUUID();
        serverCheck = CompletableFuture.runAsync(() -> {
            try {
                var config = new Gson().fromJson(Files.readString(ConfigPath.SERVERBACKPACK.getPath(mc.getSingleplayerServer())),
                        BackpackConfig.class);
                var item = config.getPlayerData().get(playerId).getBackpacks_MAP().get(1).getItem_MAP().get(RefitScreenProbe.slot());
                CompoundTag expectedNbt = originalNbt.copy();
                expectedNbt.putString("GunId", expectedId);
                if (!expectedNbt.equals(TagParser.parseTag(item.getNbt()))) {
                    throw new AssertionError("Form save changed base NBT beyond its validated root GunId");
                }
                if (item.getAttachmentPreset() == null || item.getAttachmentPreset().isBlank()) {
                    throw new AssertionError("Form save persisted no attachment preset");
                }
                assertGun(BackpackItemStackFactory.create(item), expectedId, installed);
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot verify actual form configuration file", failure);
            }
        }, mc.getSingleplayerServer());
    }

    private static void verifyClientFactory(String expectedId, boolean installed) throws Exception {
        // Reflection keeps this external bootstrap compilable against the previous production artifact.
        Class<?> rules = Class.forName("com.cdp.codpattern.compat.arcana.ArcanaGunFormRules");
        Object catalog = rules.getMethod("catalog").invoke(null);
        String status = catalog.getClass().getMethod("status").invoke(catalog).toString();
        if (!status.equals("UNAVAILABLE")) throw new AssertionError("Expected no server form rules on render thread: " + status);
        var cached = BackpackClientCache.get();
        if (cached == null) throw new AssertionError("SAVE did not supply the client backpack cache");
        var item = cached.getBackpacks_MAP().get(1).getItem_MAP().get(RefitScreenProbe.slot());
        if (!expectedId.equals(TagParser.parseTag(item.getNbt()).getString("GunId"))) {
            throw new AssertionError("Client cache did not receive the persisted form identity");
        }
        assertGun(BackpackItemStackFactory.create(item), expectedId, installed);
        ClientProbeMod.evidence("formClientRuleStatus", status);
    }

    private static void distributeSaved(String expectedId, boolean installed) {
        if (AttachmentRefitClientState.getSessionId() != null) throw new AssertionError("Cannot issue a gun during editing");
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var playerId = mc.player.getUUID();
        distributionStarted = System.nanoTime();
        serverCheck = CompletableFuture.runAsync(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null) throw new AssertionError("Distribution target disconnected");
            // Enter through the registered administrator command, never call the distributor or factory directly.
            int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "cdp distribute " + player.getGameProfile().getName());
            if (result != 1) throw new AssertionError("The actual distribute command failed: " + result);
            ItemStack issued = player.getInventory().getItem(distributionSlot());
            assertGun(issued, expectedId, installed);
            if (issued.getTag().getLong("FormProbeDistributionRun") != fixtureStamp) {
                throw new AssertionError("Distributor did not load this run's saved backpack item");
            }
        }, server);
    }

    private static boolean receivedDistribution(String expectedId, boolean installed) {
        ItemStack received = Minecraft.getInstance().player.getInventory().getItem(distributionSlot());
        CompoundTag tag = received.getTag();
        if (tag == null || !expectedId.equals(tag.getString("GunId"))
                || tag.getLong("FormProbeDistributionRun") != fixtureStamp) {
            if ((System.nanoTime() - distributionStarted) / 1_000_000L > 10_000L) {
                throw new AssertionError("The issued " + expectedId + " never reached the client's real inventory");
            }
            return false;
        }
        // Normal inventory synchronization supplies this stack. No local inventory write is performed.
        assertGun(received, expectedId, installed);
        distributionsReceived++;
        LogUtils.getLogger().info("CDP_CLIENT_PROBE form_distribution_received gun={} inventorySlot={} count={}",
                expectedId, distributionSlot(), distributionsReceived);
        return true;
    }

    private static int distributionSlot() { return RefitScreenProbe.slot().equals("secondary") ? 1 : 0; }

    private static void assertGun(ItemStack gun, String expectedId, boolean installed) {
        IGun api = IGun.getIGunOrNull(gun);
        if (api == null || gun.getTag() == null || !expectedId.equals(gun.getTag().getString("GunId"))
                || !expectedId.equals(TaczGatewayProvider.gateway().resolveRefitGunId(gun).orElse(""))) {
            throw new AssertionError("Expected actual root form " + expectedId + " but got " + gun);
        }
        String actualModule = RefitScreenProbe.attachmentId(storedAttachment(gun, moduleType()));
        if (!(installed ? MODULE : "").equals(actualModule)) throw new AssertionError("Wrong persisted conversion module: " + actualModule);
        dormant.forEach((type, original) -> {
            // Real TaCZ getters intentionally hide types disabled in the current form.
            // Inspect their stored stacks so an empty getter is not confused with deleted NBT.
            if (!ItemStack.matches(original, storedAttachment(gun, type))) {
                throw new AssertionError("Conversion lost or changed dormant " + type);
            }
            boolean allowed = api.allowAttachmentType(gun, type);
            if (allowed == installed) throw new AssertionError("Dormant type visibility does not match form: " + type);
        });
    }

    private static ItemStack storedAttachment(ItemStack gun, AttachmentType type) {
        return gun.getTag() == null ? ItemStack.EMPTY
                : ItemStack.of(gun.getTag().getCompound("Attachment" + type.name()));
    }

    private static InventoryAttachmentSlot visibleModule(Screen screen) throws Exception {
        for (var slot : RefitScreenProbe.visibleCandidates(screen)) {
            if (MODULE.equals(RefitScreenProbe.attachmentId(RefitScreenProbe.candidate(slot)))) return slot;
        }
        return null;
    }

    private static boolean nextPage(Screen screen) throws Exception {
        var direction = RefitTurnPageButton.class.getDeclaredField("isUpPage");
        direction.setAccessible(true);
        for (var child : screen.children()) {
            if (child instanceof RefitTurnPageButton button && button.active && button.visible && !direction.getBoolean(button)) {
                if (++pagesVisited > AttachmentRefitClientState.getAttachmentCandidates().size() * 2) {
                    throw new AssertionError("Form candidate paging never reached its end");
                }
                ClientProbeMod.click(screen, button);
                return true;
            }
        }
        return false;
    }

    private static RefitUnloadButton unloadButton(Screen screen) {
        return screen.children().stream().filter(RefitUnloadButton.class::isInstance).map(RefitUnloadButton.class::cast)
                .filter(button -> button.active && button.visible).findFirst()
                .orElseThrow(() -> new AssertionError("Installed MODULE has no usable real unload control"));
    }

    private static boolean active(Screen screen) { return screen instanceof CodGunRefitScreen && AttachmentRefitClientState.acceptsTaczOperations(); }
    private static AttachmentType moduleType() { return AttachmentType.valueOf("MODULE"); }
    private static ItemStack clientGun() { return AttachmentRefitClientState.resolveRefitScreenInventory(Minecraft.getInstance().player).getSelected(); }
    private static ItemStack gun(String id) { return GunItemBuilder.create().setId(new ResourceLocation(id)).setAmmoCount(7).setAmmoInBarrel(true).build(); }
    private static void close(Screen screen) { if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("Form save ESC was rejected"); }
}
