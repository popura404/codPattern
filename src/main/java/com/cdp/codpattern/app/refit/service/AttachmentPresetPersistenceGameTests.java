package com.cdp.codpattern.app.refit.service;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.cdp.codpattern.core.refit.RefitAction;
import com.cdp.codpattern.core.refit.RefitPreviewContext;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Same package as the service to exercise its final persistence boundary using temporary files. */
@GameTestHolder("codpattern_refit")
@PrefixGameTestTemplate(false)
public final class AttachmentPresetPersistenceGameTests {
    private AttachmentPresetPersistenceGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_persistence", timeoutTicks = 100)
    public static void primaryAndSecondaryPresetSavesRoundTrip(GameTestHelper helper) throws Exception {
        for (String slot : List.of("primary", "secondary")) {
            ServerPlayer player = player(helper);
            List<ItemStack> original = seedInventory(player);
            try (RepositoryFiles files = new RepositoryFiles()) {
                Editing editing = start(helper, player, files, slot);
                String expectedPreset = AttachmentPresetUtil.buildPresetFromGun(editing.session().getRefitInventory().getSelected()).toString();
                AttachmentPresetSaveService.Result result = AttachmentPresetSaveService.save(player, 1, slot,
                        files.backpackPath, files.filterPath, BackpackConfigRepository::save);
                helper.assertTrue(result.success() && result.payloadLength() == expectedPreset.length(),
                        "normal " + slot + " save confirms the server-built preset was persisted");
                BackpackConfig reloaded = BackpackConfigRepository.loadOrCreate(files.backpackPath);
                BackpackConfig.Backpack.ItemData saved = reloaded.getPlayerData().get(player.getStringUUID())
                        .getBackpacks_MAP().get(1).getItem_MAP().get(slot);
                helper.assertTrue(expectedPreset.equals(saved.getAttachmentPreset()),
                        "reloading the actual saved file preserves the current working gun's attachment preset");
                helper.assertTrue(editing.previous().getItem().equals(saved.getItem())
                                && editing.previous().getCount() == saved.getCount()
                                && editing.previous().getNbt().equals(saved.getNbt()),
                        "saving attachments preserves the configured base item, count and unrelated gun NBT");
                assertRestored(helper, player, original);

                ItemStack distributedGun = BackpackItemStackFactory.create(saved);
                assertConsumedPreset(helper, distributedGun, expectedPreset, slot + " distribution factory");

                try (RequestPaths ignored = new RequestPaths(player, files)) {
                    // Force prepare to consume the saved disk configuration, not this test's cached ItemData.
                    Field cache = BackpackConfigRepository.class.getDeclaredField("serverConfig");
                    cache.setAccessible(true);
                    cache.set(null, null);
                    UUID reopenedId = UUID.randomUUID();
                    java.util.Optional<AttachmentPresetRequestService.Result> reopened;
                    try (ReopenPropertyProbe properties = new ReopenPropertyProbe(player)) {
                        reopened = AttachmentPresetRequestService.prepare(player, reopenedId, 1, slot);
                        helper.assertTrue(properties.events > 0 && properties.allScoped && properties.beforeSession,
                                "actual reopen retains property events inside the exact player/working-gun preview scope before starting the session");
                    }
                    helper.assertTrue(!RefitPreviewContext.isActiveFor(player),
                            "actual reopen closes its preparation scope before returning the new session");
                    helper.assertTrue(reopened.isPresent(), "actual RequestService.prepare reopens saved " + slot + " configuration");
                    var packet = reopened.orElseThrow().packet();
                    AttachmentEditSession session = AttachmentEditSessionManager.getSession(player.getUUID());
                    helper.assertTrue(session != null && session.getSessionId().equals(reopenedId)
                                    && session.getBagId() == 1 && session.getSlot().equals(slot)
                                    && packet.sessionId().equals(reopenedId) && packet.slot().equals(slot),
                            "reopening creates the correct authoritative session and client response for " + slot);
                    assertConsumedPreset(helper, session.getRefitInventory().getSelected(), expectedPreset, slot + " reopened session");
                    assertConsumedPreset(helper, packet.workingGun(), expectedPreset, slot + " reopen packet");
                    helper.assertTrue(AttachmentPresetUtil.parsePresetString(expectedPreset).equals(
                                    AttachmentPresetUtil.parsePresetString(packet.presetPayload())),
                            "reopen response serializes the restored attachment state for " + slot);
                    RefitActionService.execute(player, reopenedId, RefitAction.cancel());
                    assertRestored(helper, player, original);
                } finally {
                    AttachmentEditSessionManager.endSession(player);
                }
                LogUtils.getLogger().info("Refit saved preset round trip verified through actual request and distribution entries: slot={}", slot);
            } finally {
                AttachmentEditSessionManager.endSession(player);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_persistence", timeoutTicks = 100)
    public static void finalWriteFailureRollsBackPresetAndRestoresInventory(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        List<ItemStack> original = seedInventory(player);
        try (RepositoryFiles files = new RepositoryFiles()) {
            Editing editing = start(helper, player, files, "primary");
            boolean[] reachedFinalWrite = {false};
            AttachmentPresetSaveService.Result result = AttachmentPresetSaveService.save(player, 1, "primary",
                    files.backpackPath, files.filterPath, () -> {
                        helper.assertTrue(editing.backpack().getItem_MAP().get("primary") != editing.previous(),
                                "failure injection occurs after the new preset has mutated the actual configuration");
                        reachedFinalWrite[0] = true;
                        try {
                            // Pre-save reads/writes have already succeeded; only the final actual write now fails.
                            Files.delete(files.backpackPath);
                            Files.createDirectory(files.backpackPath);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                        BackpackConfigRepository.save();
                    });
            helper.assertTrue(reachedFinalWrite[0] && Files.isDirectory(files.backpackPath),
                    "the test reaches the final persistence boundary and invokes repository IO against an unwritable destination");
            helper.assertTrue(!result.success() && result.payloadLength() == 0,
                    "actual final write failure must never return a successful saved result");
            assertMessage(helper, result, "message.codpattern.config.write_failed");
            helper.assertTrue(editing.backpack().getItem_MAP().get("primary") == editing.previous(),
                    "the real save-service catch restores the previous ItemData after final write failure");
            helper.assertTrue(BackpackConfigRepository.getConfig().getPlayerData().get(player.getStringUUID())
                            .getBackpacks_MAP().get(1).getItem_MAP().get("primary") == editing.previous(),
                    "the repository cache also contains the restored preset, not the unsaved replacement");
            assertRestored(helper, player, original);
            LogUtils.getLogger().info("Refit persistence test: actual final WRITE failure reached caller rollback and restored player inventory");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_persistence", timeoutTicks = 100)
    public static void configReadAndParseFailuresAreDistinguished(GameTestHelper helper) throws Exception {
        for (boolean invalidJson : List.of(false, true)) {
            ServerPlayer player = player(helper);
            List<ItemStack> original = seedInventory(player);
            try (RepositoryFiles files = new RepositoryFiles()) {
                Editing editing = start(helper, player, files, "primary");
                if (invalidJson) {
                    Files.writeString(files.filterPath, "{\"blockedAttachmentIds\":");
                } else {
                    Files.delete(files.filterPath);
                    Files.createDirectory(files.filterPath);
                }
                boolean[] finalWriteAttempted = {false};
                AttachmentPresetSaveService.Result result = AttachmentPresetSaveService.save(player, 1, "primary",
                        files.backpackPath, files.filterPath, () -> {
                            finalWriteAttempted[0] = true;
                            BackpackConfigRepository.save();
                        });
                helper.assertTrue(!result.success() && !finalWriteAttempted[0],
                        "unreadable/invalid configuration stops the save before its mutation and final write");
                assertMessage(helper, result, invalidJson
                        ? "message.codpattern.config.parse_failed" : "message.codpattern.config.read_failed");
                helper.assertTrue(editing.backpack().getItem_MAP().get("primary") == editing.previous(),
                        "configuration load failure preserves the previous configured preset");
                helper.assertTrue(invalidJson
                                ? "{\"blockedAttachmentIds\":".equals(Files.readString(files.filterPath))
                                : Files.isDirectory(files.filterPath),
                        "failed configuration load does not overwrite the bad source with defaults");
                assertRestored(helper, player, original);
            } finally {
                AttachmentEditSessionManager.endSession(player);
            }
        }
        helper.succeed();
    }

    private static Editing start(GameTestHelper helper, ServerPlayer player, RepositoryFiles files, String slot) {
        GunAndAttachment fixture = findFixture(helper);
        BackpackConfig.PlayerBackpackData playerData = files.backpack.getOrCreatePlayerData(player.getStringUUID());
        BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(1);
        ItemStack baseGun = fixture.gun().copy();
        baseGun.getOrCreateTag().putInt("UnrelatedBaseMarker", 73);
        BackpackConfig.Backpack.ItemData previous = new BackpackConfig.Backpack.ItemData(
                baseGun.getItem().builtInRegistryHolder().key().location().toString(), 1,
                baseGun.getOrCreateTag().toString(), "{PreviousPreset:1b}");
        backpack.getItem_MAP().put(slot, previous);
        BackpackConfigRepository.save();
        AttachmentEditSession session = AttachmentEditSessionManager.startSession(player, UUID.randomUUID(),
                1, slot, fixture.gun(), List.of(fixture.attachment()));
        var installed = RefitActionService.execute(player, session.getSessionId(),
                RefitAction.install(9, session.getEditHotbarSlot(), fixture.type()));
        helper.assertTrue(installed.success(), "persistence tests install a real attachment through the production operation service");
        return new Editing(session, backpack, previous);
    }

    private static GunAndAttachment findFixture(GameTestHelper helper) {
        var attachments = TaczGatewayProvider.gateway().listAttachmentCandidates();
        var guns = TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList();
        for (var entry : guns) {
            ItemStack gun = GunItemBuilder.create().setId(entry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            IGun api = IGun.getIGunOrNull(gun);
            if (api == null) continue;
            for (ItemStack attachment : attachments) {
                IAttachment attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                if (attachmentApi == null || attachmentApi.getType(attachment) != AttachmentType.SCOPE) continue;
                if (api.allowAttachmentType(gun, AttachmentType.SCOPE) && api.allowAttachment(gun, attachment)) {
                    return new GunAndAttachment(gun, attachment, AttachmentType.SCOPE);
                }
            }
        }
        helper.fail("Persistence tests require a loaded real TaCZ gun with a compatible scope");
        throw new AssertionError("required real gun/scope unavailable");
    }

    private static void assertMessage(GameTestHelper helper, AttachmentPresetSaveService.Result result, String key) {
        helper.assertTrue(result.userMessage().getContents() instanceof TranslatableContents contents
                        && key.equals(contents.getKey()),
                "failure must report the precise localizable result " + key);
    }

    private static void assertConsumedPreset(GameTestHelper helper, ItemStack gun, String expectedPreset, String entry) {
        helper.assertTrue(!gun.isEmpty() && TaczGatewayProvider.gateway().isGun(gun), entry + " reconstructs a real gun");
        helper.assertTrue(AttachmentPresetUtil.parsePresetString(expectedPreset).equals(AttachmentPresetUtil.buildPresetFromGun(gun)),
                entry + " consumes the saved preset and restores the complete installed attachment state");
        helper.assertTrue(gun.getOrCreateTag().getInt("UnrelatedBaseMarker") == 73,
                entry + " preserves unrelated base gun NBT while applying the preset");
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "refit-save"));
    }

    private static List<ItemStack> seedInventory(ServerPlayer player) {
        List<ItemStack> original = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack marker = new ItemStack(Items.PAPER, 1 + slot % 16);
            marker.getOrCreateTag().putInt("OriginalSaveSlot", slot);
            player.getInventory().setItem(slot, marker.copy());
            original.add(marker);
        }
        player.getInventory().selected = 3;
        return original;
    }

    private static void assertRestored(GameTestHelper helper, ServerPlayer player, List<ItemStack> original) {
        helper.assertTrue(!AttachmentEditSessionManager.hasSession(player.getUUID()) && player.getInventory().selected == 3,
                "save success or failure always closes its session and restores the selected slot");
        for (int slot = 0; slot < original.size(); slot++) {
            ItemStack before = original.get(slot);
            ItemStack after = player.getInventory().getItem(slot);
            helper.assertTrue(before.getItem() == after.getItem() && before.getCount() == after.getCount()
                            && Objects.equals(before.getTag(), after.getTag()),
                    "save completion restores original item/count/NBT at slot " + slot);
        }
    }

    private record Editing(AttachmentEditSession session, BackpackConfig.Backpack backpack,
            BackpackConfig.Backpack.ItemData previous) { }

    private record GunAndAttachment(ItemStack gun, ItemStack attachment, AttachmentType type) { }

    private static final class ReopenPropertyProbe implements AutoCloseable {
        private int events;
        private boolean allScoped = true;
        private boolean beforeSession = true;
        private final Consumer<AttachmentPropertyEvent> listener;

        private ReopenPropertyProbe(ServerPlayer player) {
            listener = event -> {
                ItemStack gun = event.getGunItem();
                if (!gun.hasTag() || gun.getTag().getInt("UnrelatedBaseMarker") != 73) return;
                events++;
                allScoped &= RefitPreviewContext.isActiveFor(player) && RefitPreviewContext.isActiveFor(gun);
                beforeSession &= !AttachmentEditSessionManager.hasSession(player.getUUID());
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, AttachmentPropertyEvent.class, listener);
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    /** Scope the real request entry to test files without adding a production-only injection API. */
    static final class RequestPaths implements AutoCloseable {
        private final Field pathField;
        private final Map<ConfigPath, String> previous = new LinkedHashMap<>();

        RequestPaths(ServerPlayer player, RepositoryFiles files) throws Exception {
            pathField = ConfigPath.class.getDeclaredField("path");
            pathField.setAccessible(true);
            for (ConfigPath path : List.of(ConfigPath.SERVERBACKPACK, ConfigPath.SERVER_FILTER)) {
                previous.put(path, (String) pathField.get(path));
            }
            try {
                pathField.set(ConfigPath.SERVERBACKPACK, files.backpackPath.toAbsolutePath().toString());
                pathField.set(ConfigPath.SERVER_FILTER, files.filterPath.toAbsolutePath().toString());
                if (!ConfigPath.SERVERBACKPACK.getPath(player.server).equals(files.backpackPath.toAbsolutePath())
                        || !ConfigPath.SERVER_FILTER.getPath(player.server).equals(files.filterPath.toAbsolutePath())) {
                    throw new IllegalStateException("Refit request test could not isolate its temporary configuration paths");
                }
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            for (var entry : previous.entrySet()) pathField.set(entry.getKey(), entry.getValue());
        }
    }

    static final class RepositoryFiles implements AutoCloseable {
        private final Map<Field, Object> previous = new LinkedHashMap<>();
        private final Path root;
        final Path backpackPath;
        final Path filterPath;
        final BackpackConfig backpack;

        RepositoryFiles() throws Exception {
            for (Class<?> type : List.of(BackpackConfigRepository.class, WeaponFilterConfigRepository.class)) {
                for (String name : List.of("serverConfigPath", "serverConfig", "loadFailure")) {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    previous.put(field, field.get(null));
                }
            }
            root = Files.createTempDirectory("cdp-refit-persistence-");
            backpackPath = root.resolve("backpack.json");
            filterPath = root.resolve("filter.json");
            try {
                backpack = BackpackConfigRepository.loadOrCreate(backpackPath);
                var filter = WeaponFilterConfigRepository.loadOrCreate(filterPath);
                filter.setBlockedAttachmentIds(List.of());
                filter.setBlockedAttachmentNamespaces(List.of());
                WeaponFilterConfigRepository.save(filter);
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
