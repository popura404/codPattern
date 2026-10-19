package com.cdp.codpattern.app.refit.gametest;

import com.cdp.codpattern.app.refit.service.RefitActionService;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.BackpackRefitSessionContext;
import com.cdp.codpattern.core.refit.RefitAction;
import com.cdp.codpattern.event.AttachmentEditSessionServerEvents;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
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

/** Tests the actual CDP event handlers; it does not stop the test server or simulate a client GUI. */
@GameTestHolder("codpattern_refit")
@PrefixGameTestTemplate(false)
public final class AttachmentSessionLifecycleGameTests {
    private AttachmentSessionLifecycleGameTests() { }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_lifecycle", timeoutTicks = 100)
    public static void logoutRestoresInventoryAndRepeatedCleanupIsHarmless(GameTestHelper helper) {
        verifyHandler(helper, "logout", player -> AttachmentEditSessionServerEvents.onPlayerLoggedOut(
                new PlayerEvent.PlayerLoggedOutEvent(player)));
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_lifecycle", timeoutTicks = 100)
    public static void deathRestoresInventoryAndRepeatedCleanupIsHarmless(GameTestHelper helper) {
        verifyHandler(helper, "death", player -> AttachmentEditSessionServerEvents.onPlayerDeath(
                new LivingDeathEvent(player, helper.getLevel().damageSources().generic())));
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_lifecycle", timeoutTicks = 100)
    public static void dimensionChangeRestoresInventoryAndRepeatedCleanupIsHarmless(GameTestHelper helper) {
        verifyHandler(helper, "dimension_changed", player -> AttachmentEditSessionServerEvents.onPlayerChangedDimension(
                new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER)));
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_lifecycle", timeoutTicks = 100)
    public static void serverStopRestoresOnlineInventoryAndRepeatedCleanupIsHarmless(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        // FakePlayer is not normally registered. Exercise the actual online-player restore branch,
        // not abortAllSessions' offline branch that only forgets the session.
        try (OnlineLookup ignored = new OnlineLookup(player)) {
            helper.assertTrue(player.server.getPlayerList().getPlayer(player.getUUID()) == player,
                    "the stopping handler must resolve the test player through the real PlayerList");
            verifyHandler(helper, player, "server_stopping", current -> AttachmentEditSessionServerEvents.onServerStopping(
                    new ServerStoppingEvent(current.server)));
        }
        helper.assertTrue(player.server.getPlayerList().getPlayer(player.getUUID()) == null,
                "the temporary online-player registration is removed after the stopping test");
        helper.succeed();
    }

    private static void verifyHandler(GameTestHelper helper, String event, Consumer<ServerPlayer> handler) {
        verifyHandler(helper, player(helper), event, handler);
    }

    private static void verifyHandler(GameTestHelper helper, ServerPlayer player, String event, Consumer<ServerPlayer> handler) {
        List<ItemStack> original = seedInventory(player);
        Fixture fixture = findFixture(helper);
        try {
            AttachmentEditSession session = start(player, fixture);
            helper.assertTrue(BackpackRefitSessionContext.isBackpackRefitActive(player),
                    event + " starts with an active editing context");
            session.getRefitInventory().getSelected().getOrCreateTag().putBoolean("UnsavedLifecycleEdit", true);
            player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
            player.getInventory().selected = 2;

            handler.accept(player);
            assertClosed(helper, player, event);
            assertInventory(helper, player, original, 4, event);

            // A second event must not replay the old snapshot over legitimate later inventory changes.
            ItemStack later = new ItemStack(Items.GOLD_INGOT, 13);
            later.getOrCreateTag().putString("AfterSession", event);
            player.getInventory().setItem(0, later);
            player.getInventory().selected = 6;
            List<ItemStack> afterCleanup = snapshot(player);
            handler.accept(player);
            assertClosed(helper, player, event + " repeated");
            assertInventory(helper, player, afterCleanup, 6, event + " repeated");
            LogUtils.getLogger().info("Refit lifecycle handler verified: event={} full inventory/selection restored; repeat preserved later changes", event);
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
    }

    /** Opt-in only: enable codpattern_refit_duration separately from the normal matrix. */
    @GameTestHolder("codpattern_refit_duration")
    @PrefixGameTestTemplate(false)
    public static final class Duration {
        private Duration() { }

        @GameTest(template = "empty", templateNamespace = "codpattern_refit_duration", batch = "refit_duration", timeoutTicks = 2800)
        public static void sessionStillInstallsAndUnloadsAfter2420Ticks(GameTestHelper helper) {
            ServerPlayer player = player(helper);
            List<ItemStack> original = seedInventory(player);
            Fixture fixture = findFixture(helper);
            AttachmentEditSession session;
            try {
                session = start(player, fixture);
            } catch (RuntimeException | Error failure) {
                AttachmentEditSessionManager.endSession(player);
                throw failure;
            }
            long startTick = helper.getTick();
            long startedAt = System.nanoTime();
            helper.runAfterDelay(2420, () -> {
                // Hold the temporary repository state only for this synchronous operation callback.
                try (FilterState ignored = new FilterState()) {
                    long elapsedTicks = helper.getTick() - startTick;
                    helper.assertTrue(elapsedTicks > 2400, "the actual server has advanced more than 2400 ticks");
                    helper.assertTrue(AttachmentEditSessionManager.getSession(player.getUUID()) == session
                                    && BackpackRefitSessionContext.isBackpackRefitActive(player),
                            "the original session remains active throughout the wait");

                    var installed = RefitActionService.execute(player, session.getSessionId(),
                            RefitAction.install(9, session.getEditHotbarSlot(), AttachmentType.SCOPE));
                    helper.assertTrue(installed.success() && !installed.finished(),
                            "the original session accepts a real install after 2400 ticks: " + installed.message().getString());
                    ItemStack gun = session.getRefitInventory().getSelected();
                    ItemStack attachment = IGun.getIGunOrNull(gun).getAttachment(gun, AttachmentType.SCOPE);
                    helper.assertTrue(TaczGatewayProvider.gateway().resolveAttachmentId(fixture.attachment())
                                    .equals(TaczGatewayProvider.gateway().resolveAttachmentId(attachment)),
                            "the post-wait operation installs the selected real attachment");
                    var unloaded = RefitActionService.execute(player, session.getSessionId(),
                            RefitAction.unload(session.getEditHotbarSlot(), AttachmentType.SCOPE));
                    helper.assertTrue(unloaded.success() && !unloaded.finished()
                                    && IGun.getIGunOrNull(gun).getAttachment(gun, AttachmentType.SCOPE).isEmpty(),
                            "the original session also accepts a real unload after the long wait");
                    helper.assertTrue(session.getRefitInventory().getCandidateCount() == 1,
                            "post-wait operations preserve the candidate directory");
                    var cancelled = RefitActionService.execute(player, session.getSessionId(), RefitAction.cancel());
                    helper.assertTrue(cancelled.success() && cancelled.finished(), "cancel completes the long-lived session");
                    assertClosed(helper, player, "duration cancel");
                    assertInventory(helper, player, original, 4, "duration cancel");
                    LogUtils.getLogger().info("Refit duration verified: elapsedTicks={} elapsedMillis={} actual INSTALL/UNLOAD/CANCEL succeeded",
                            elapsedTicks, (System.nanoTime() - startedAt) / 1_000_000);
                } catch (Exception failure) {
                    throw new IllegalStateException("Long-lived refit session validation failed", failure);
                } finally {
                    AttachmentEditSessionManager.endSession(player);
                }
                helper.succeed();
            });
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "refit-lifecycle"));
    }

    private static AttachmentEditSession start(ServerPlayer player, Fixture fixture) {
        return AttachmentEditSessionManager.startSession(player, UUID.randomUUID(), 1, "primary",
                fixture.gun(), List.of(fixture.attachment()));
    }

    private static List<ItemStack> seedInventory(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack marker = new ItemStack(Items.PAPER, 1 + slot % 16);
            marker.getOrCreateTag().putInt("OriginalLifecycleSlot", slot);
            player.getInventory().setItem(slot, marker);
        }
        player.getInventory().selected = 4;
        return snapshot(player);
    }

    private static List<ItemStack> snapshot(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            items.add(player.getInventory().getItem(slot).copy());
        }
        return items;
    }

    private static void assertInventory(GameTestHelper helper, ServerPlayer player, List<ItemStack> expected,
            int selected, String reason) {
        helper.assertTrue(player.getInventory().selected == selected, reason + " preserves the expected selected slot");
        for (int slot = 0; slot < expected.size(); slot++) {
            ItemStack before = expected.get(slot);
            ItemStack after = player.getInventory().getItem(slot);
            helper.assertTrue(before.getItem() == after.getItem() && before.getCount() == after.getCount()
                            && Objects.equals(before.getTag(), after.getTag()),
                    reason + " preserves item/count/NBT at inventory slot " + slot);
        }
    }

    private static void assertClosed(GameTestHelper helper, ServerPlayer player, String reason) {
        helper.assertTrue(!AttachmentEditSessionManager.hasSession(player.getUUID())
                        && !BackpackRefitSessionContext.isBackpackRefitActive(player),
                reason + " clears both the editing session and its context");
    }

    private static Fixture findFixture(GameTestHelper helper) {
        List<ItemStack> attachments = TaczGatewayProvider.gateway().listAttachmentCandidates();
        var guns = TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList();
        for (var entry : guns) {
            ItemStack gun = GunItemBuilder.create().setId(entry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            IGun api = IGun.getIGunOrNull(gun);
            if (api == null) continue;
            for (ItemStack attachment : attachments) {
                IAttachment attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                if (attachmentApi != null && attachmentApi.getType(attachment) == AttachmentType.SCOPE
                        && api.allowAttachmentType(gun, AttachmentType.SCOPE) && api.allowAttachment(gun, attachment)) {
                    return new Fixture(gun, attachment);
                }
            }
        }
        helper.fail("Lifecycle tests require a loaded real TaCZ gun with a compatible scope");
        throw new AssertionError("required real gun/scope unavailable");
    }

    private record Fixture(ItemStack gun, ItemStack attachment) { }

    /** Select by generic type, so the lookup works in both development and production SRG names. */
    private static final class OnlineLookup implements AutoCloseable {
        private final Map<UUID, ServerPlayer> players;
        private final ServerPlayer player;

        @SuppressWarnings("unchecked")
        private OnlineLookup(ServerPlayer player) throws Exception {
            this.player = player;
            Map<UUID, ServerPlayer> found = null;
            for (Field field : PlayerList.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())
                        || !(field.getGenericType() instanceof ParameterizedType type)) continue;
                var arguments = type.getActualTypeArguments();
                if (arguments.length != 2 || arguments[0] != UUID.class || arguments[1] != ServerPlayer.class) continue;
                if (found != null) throw new IllegalStateException("Ambiguous PlayerList UUID lookup");
                field.setAccessible(true);
                found = (Map<UUID, ServerPlayer>) field.get(player.server.getPlayerList());
            }
            if (found == null) throw new IllegalStateException("No PlayerList UUID-to-ServerPlayer lookup found");
            players = found;
            if (players.containsKey(player.getUUID())) throw new IllegalStateException("Test player UUID already registered");
            players.put(player.getUUID(), player);
        }

        @Override
        public void close() {
            players.remove(player.getUUID(), player);
        }
    }

    private static final class FilterState implements AutoCloseable {
        private final Map<Field, Object> previous = new LinkedHashMap<>();
        private final Path root;
        private final Path file;

        private FilterState() throws Exception {
            for (Field field : WeaponFilterConfigRepository.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                previous.put(field, field.get(null));
            }
            root = Files.createTempDirectory("cdp-refit-duration-filter-");
            file = root.resolve("filter.json");
            try {
                var config = WeaponFilterConfigRepository.loadOrCreate(file);
                config.setBlockedAttachmentNamespaces(List.of());
                config.setBlockedAttachmentIds(List.of());
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }
}
