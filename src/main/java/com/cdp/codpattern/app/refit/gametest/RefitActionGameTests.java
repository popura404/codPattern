package com.cdp.codpattern.app.refit.gametest;

import com.cdp.codpattern.app.refit.service.RefitActionService;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitAction;
import com.cdp.codpattern.network.RefitOperationResultPacket;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Exercises the production operation service with real loaded TaCZ guns and attachments. */
@GameTestHolder("codpattern_refit")
@PrefixGameTestTemplate(false)
public final class RefitActionGameTests {
    private static final int FIRST_SLOT = 9;

    private RefitActionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_actions", timeoutTicks = 100)
    public static void realAttachmentsInstallReplaceUnloadAndCancel(GameTestHelper helper) throws Exception {
        Fixture fixture = findFixture(helper);
        ServerPlayer player = player(helper);
        List<ItemStack> original = seedInventory(player);
        try (FilterState ignored = new FilterState()) {
            AttachmentEditSession session = start(player, fixture, fixture.catalog());
            install(helper, player, session, FIRST_SLOT, fixture.type(), fixture.first());
            install(helper, player, session, FIRST_SLOT + 2, fixture.type(), fixture.second());
            assertCatalog(helper, session, fixture.catalog());
            unload(helper, player, session, fixture.type());
            install(helper, player, session, FIRST_SLOT + 3, fixture.otherType(), fixture.other());
            helper.assertTrue(IGun.getIGunOrNull(working(session)).getAttachment(working(session), fixture.type()).isEmpty(),
                    "installing another type does not restore the removed attachment in the first slot");
            unload(helper, player, session, fixture.otherType());
            assertCatalog(helper, session, fixture.catalog());

            RefitOperationResultPacket cancelled = RefitActionService.execute(player, session.getSessionId(), RefitAction.cancel());
            helper.assertTrue(cancelled.success() && cancelled.finished()
                            && !AttachmentEditSessionManager.hasSession(player.getUUID()),
                    "CANCEL acknowledges completion and removes the active editing session");
            assertRestored(helper, player, original);
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_actions", timeoutTicks = 100)
    public static void invalidAndOldRequestsCannotMutateCurrentGun(GameTestHelper helper) throws Exception {
        Fixture fixture = findFixture(helper);
        ServerPlayer player = player(helper);
        List<ItemStack> original = seedInventory(player);
        try (FilterState ignored = new FilterState()) {
            AttachmentEditSession session = start(player, fixture, fixture.catalog());
            install(helper, player, session, FIRST_SLOT, fixture.type(), fixture.first());
            ItemStack before = working(session).copy();

            for (RefitAction rejected : List.of(
                    RefitAction.install(FIRST_SLOT, session.getEditHotbarSlot(), fixture.otherType()),
                    RefitAction.install(FIRST_SLOT + 1, session.getEditHotbarSlot(), fixture.type()),
                    RefitAction.install(-1, session.getEditHotbarSlot(), fixture.type()),
                    RefitAction.install(Integer.MAX_VALUE, session.getEditHotbarSlot(), fixture.type()),
                    RefitAction.install(FIRST_SLOT, (session.getEditHotbarSlot() + 1) % 9, fixture.type()),
                    new RefitAction(RefitAction.Kind.INSTALL, FIRST_SLOT, session.getEditHotbarSlot(),
                            "UNKNOWN_TYPE", Map.of(), false, 0))) {
                RefitOperationResultPacket result = RefitActionService.execute(player, session.getSessionId(), rejected);
                helper.assertTrue(!result.success() && !result.finished(),
                        "invalid type, empty/out-of-range candidate or wrong gun slot must be rejected without ending editing");
                helper.assertTrue(sameStack(before, working(session)) && sameStack(before, result.gun()),
                        "a rejected request leaves the authoritative gun unchanged and replies with that actual state");
            }

            UUID oldId = session.getSessionId();
            AttachmentEditSession replacement = start(player, fixture, fixture.catalog());
            ItemStack replacementBefore = working(replacement).copy();
            for (RefitAction stale : List.of(RefitAction.install(FIRST_SLOT, replacement.getEditHotbarSlot(), fixture.type()),
                    RefitAction.cancel(), RefitAction.save())) {
                RefitOperationResultPacket result = RefitActionService.execute(player, oldId, stale);
                helper.assertTrue(!result.success() && result.finished(), "old session messages are explicitly rejected");
                helper.assertTrue(AttachmentEditSessionManager.getSession(player.getUUID()) == replacement
                                && sameStack(replacementBefore, working(replacement)),
                        "a stale install, cancel or save cannot modify or close the replacement session");
            }
            RefitActionService.execute(player, replacement.getSessionId(), RefitAction.cancel());
            RefitOperationResultPacket missing = RefitActionService.execute(player, replacement.getSessionId(),
                    RefitAction.install(FIRST_SLOT, replacement.getEditHotbarSlot(), fixture.type()));
            helper.assertTrue(!missing.success() && missing.finished(), "operations after cancellation are rejected");
            assertRestored(helper, player, original);
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_actions", timeoutTicks = 100)
    public static void latestBlacklistRejectsInstallButAllowsRemoval(GameTestHelper helper) throws Exception {
        Fixture fixture = findFixture(helper);
        ServerPlayer player = player(helper);
        seedInventory(player);
        try (FilterState filter = new FilterState()) {
            AttachmentEditSession session = start(player, fixture, fixture.catalog());
            install(helper, player, session, FIRST_SLOT, fixture.type(), fixture.first());
            filter.config.setBlockedAttachmentIds(List.of(attachmentId(fixture.first()), attachmentId(fixture.second())));
            RefitOperationResultPacket blocked = RefitActionService.execute(player, session.getSessionId(),
                    RefitAction.install(FIRST_SLOT + 2, session.getEditHotbarSlot(), fixture.type()));
            helper.assertTrue(!blocked.success() && !blocked.finished(),
                    "a previously displayed candidate is rejected after the current filter disables its ID");
            assertInstalled(helper, session, fixture.type(), fixture.first());
            unload(helper, player, session, fixture.type());

            String namespace = attachmentId(fixture.other()).split(":", 2)[0];
            filter.config.setBlockedAttachmentNamespaces(List.of(namespace));
            blocked = RefitActionService.execute(player, session.getSessionId(),
                    RefitAction.install(FIRST_SLOT + 3, session.getEditHotbarSlot(), fixture.otherType()));
            helper.assertTrue(!blocked.success(), "namespace blacklist is enforced at the operation boundary");
            filter.config.setBlockedAttachmentIds(List.of());
            filter.config.setBlockedAttachmentNamespaces(List.of());
            install(helper, player, session, FIRST_SLOT + 2, fixture.type(), fixture.second());
            assertCatalog(helper, session, fixture.catalog());
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_actions", timeoutTicks = 100)
    public static void realOperationsReachLargeCatalogTailWithoutRenumbering(GameTestHelper helper) throws Exception {
        Fixture fixture = findFixture(helper);
        ServerPlayer player = player(helper);
        seedInventory(player);
        List<ItemStack> catalog = new ArrayList<>(Collections.nCopies(1027, ItemStack.EMPTY));
        catalog.set(0, fixture.first());
        catalog.set(1024, fixture.second());
        catalog.set(1026, fixture.other());
        try (FilterState ignored = new FilterState()) {
            AttachmentEditSession session = start(player, fixture, catalog);
            install(helper, player, session, FIRST_SLOT, fixture.type(), fixture.first());
            install(helper, player, session, FIRST_SLOT + 1024, fixture.type(), fixture.second());
            unload(helper, player, session, fixture.type());
            install(helper, player, session, FIRST_SLOT + 1026, fixture.otherType(), fixture.other());
            unload(helper, player, session, fixture.otherType());
            install(helper, player, session, FIRST_SLOT + 1024, fixture.type(), fixture.second());
            assertCatalog(helper, session, catalog);
            helper.assertTrue(session.getRefitInventory().getCandidateCount() == 1027,
                    "successful real operations do not truncate the 1027-entry catalog or consume its empty positions");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    private static Fixture findFixture(GameTestHelper helper) {
        List<ItemStack> attachments = TaczGatewayProvider.gateway().listAttachmentCandidates();
        var guns = TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList();
        for (var gunEntry : guns) {
            ItemStack gun = GunItemBuilder.create().setId(gunEntry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            IGun api = IGun.getIGunOrNull(gun);
            if (api == null) continue;
            Map<AttachmentType, List<ItemStack>> allowed = new EnumMap<>(AttachmentType.class);
            for (ItemStack attachment : attachments) {
                IAttachment attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                if (attachmentApi == null) continue;
                AttachmentType type = attachmentApi.getType(attachment);
                if (type == null || type == AttachmentType.NONE || type == AttachmentType.EXTENDED_MAG
                        || "MODULE".equals(type.name())) continue;
                if (api.allowAttachmentType(gun, type) && api.allowAttachment(gun, attachment)) {
                    allowed.computeIfAbsent(type, ignored -> new ArrayList<>()).add(attachment);
                }
            }
            var types = allowed.keySet().stream()
                    .sorted(Comparator.comparingInt(type -> type == AttachmentType.SCOPE ? -1 : type.ordinal())).toList();
            for (AttachmentType type : types) {
                List<ItemStack> sameType = allowed.get(type);
                if (sameType.size() < 2) continue;
                for (AttachmentType otherType : types) {
                    if (otherType == type) continue;
                    Fixture fixture = new Fixture(gun, type, sameType.get(0), sameType.get(1),
                            otherType, allowed.get(otherType).get(0));
                    LogUtils.getLogger().info("Refit operation test resources: gun={} type={} first={} second={} otherType={} other={}",
                            gunEntry.getKey(), type, attachmentId(fixture.first()), attachmentId(fixture.second()),
                            otherType, attachmentId(fixture.other()));
                    return fixture;
                }
            }
        }
        helper.fail("Loaded TaCZ resources must provide a real gun with two compatible same-type attachments and another attachment type");
        throw new AssertionError("required TaCZ operation resources missing");
    }

    private static AttachmentEditSession start(ServerPlayer player, Fixture fixture, List<ItemStack> catalog) {
        return AttachmentEditSessionManager.startSession(player, UUID.randomUUID(), 1, "primary", fixture.gun(), catalog);
    }

    private static void install(GameTestHelper helper, ServerPlayer player, AttachmentEditSession session,
            int slot, AttachmentType type, ItemStack expected) {
        RefitOperationResultPacket result = RefitActionService.execute(player, session.getSessionId(),
                RefitAction.install(slot, session.getEditHotbarSlot(), type));
        helper.assertTrue(result.success() && !result.finished(), "real attachment install must succeed: " + attachmentId(expected));
        assertInstalled(helper, session, type, expected);
        helper.assertTrue(result.sessionId().equals(session.getSessionId())
                        && result.gunSlot() == session.getEditHotbarSlot() && sameStack(result.gun(), working(session)),
                "successful operation replies with the current session and authoritative gun");
    }

    private static void unload(GameTestHelper helper, ServerPlayer player, AttachmentEditSession session, AttachmentType type) {
        RefitOperationResultPacket result = RefitActionService.execute(player, session.getSessionId(),
                RefitAction.unload(session.getEditHotbarSlot(), type));
        helper.assertTrue(result.success() && !result.finished(), "real attachment unload must succeed for " + type);
        helper.assertTrue(IGun.getIGunOrNull(working(session)).getAttachment(working(session), type).isEmpty(),
                "unload removes the attachment from the actual working gun");
        helper.assertTrue(sameStack(result.gun(), working(session)), "unload reply reflects the authoritative gun");
    }

    private static void assertInstalled(GameTestHelper helper, AttachmentEditSession session, AttachmentType type, ItemStack expected) {
        ItemStack actual = IGun.getIGunOrNull(working(session)).getAttachment(working(session), type);
        helper.assertTrue(attachmentId(expected).equals(attachmentId(actual)),
                "target type " + type + " contains exactly the selected attachment " + attachmentId(expected));
    }

    private static void assertCatalog(GameTestHelper helper, AttachmentEditSession session, List<ItemStack> expected) {
        List<ItemStack> actual = session.getRefitInventory().snapshotCandidates();
        helper.assertTrue(actual.size() == expected.size(), "operations keep the complete candidate catalog");
        for (int index = 0; index < expected.size(); index++) {
            helper.assertTrue(sameStack(expected.get(index), actual.get(index)),
                    "operations preserve original candidate identity and empty positions at index " + index);
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "refit-action"));
    }

    private static List<ItemStack> seedInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> original = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack marker = new ItemStack(Items.PAPER, 1 + slot % 16);
            marker.getOrCreateTag().putInt("OriginalSlot", slot);
            inventory.setItem(slot, marker.copy());
            original.add(marker);
        }
        inventory.selected = 4;
        return original;
    }

    private static void assertRestored(GameTestHelper helper, ServerPlayer player, List<ItemStack> original) {
        helper.assertTrue(player.getInventory().selected == 4, "session cancellation restores the selected hotbar slot");
        for (int slot = 0; slot < original.size(); slot++) {
            helper.assertTrue(sameStack(original.get(slot), player.getInventory().getItem(slot)),
                    "session cancellation restores the original item/count/NBT in slot " + slot);
        }
    }

    private static ItemStack working(AttachmentEditSession session) {
        return session.getRefitInventory().getSelected();
    }

    private static String attachmentId(ItemStack stack) {
        return TaczGatewayProvider.gateway().resolveAttachmentId(stack).orElse("");
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left.getItem() == right.getItem() && left.getCount() == right.getCount()
                && Objects.equals(left.getTag(), right.getTag());
    }

    private record Fixture(ItemStack gun, AttachmentType type, ItemStack first, ItemStack second,
            AttachmentType otherType, ItemStack other) {
        List<ItemStack> catalog() {
            return List.of(first, ItemStack.EMPTY, second, other);
        }
    }

    /** Avoids changing the GameTest world's configuration or another test's repository cache. */
    private static final class FilterState implements AutoCloseable {
        private final Map<Field, Object> previous = new LinkedHashMap<>();
        private final Path root;
        private final Path file;
        private final WeaponFilterConfig config;

        private FilterState() throws Exception {
            for (Field field : WeaponFilterConfigRepository.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || java.lang.reflect.Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                previous.put(field, field.get(null));
            }
            root = Files.createTempDirectory("cdp-refit-operation-filter-");
            file = root.resolve("filter.json");
            config = WeaponFilterConfigRepository.loadOrCreate(file);
            config.setBlockedAttachmentNamespaces(List.of());
            config.setBlockedAttachmentIds(List.of());
        }

        @Override
        public void close() throws Exception {
            for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }
}
