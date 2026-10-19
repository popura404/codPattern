package com.cdp.codpattern.app.refit.gametest;

import com.cdp.codpattern.core.refit.AttachmentRefitInventory;
import com.cdp.codpattern.core.refit.AttachmentCandidateTransfer;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.BackpackRefitSessionContext;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.network.SyncAttachmentCandidatesPacket;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("codpattern_refit")
@PrefixGameTestTemplate(false)
public final class AttachmentCandidateGameTests {
    private static final int CANDIDATE_COUNT = 2048;
    private static final int FIRST_CANDIDATE_SLOT = 9;

    private AttachmentCandidateGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidateInventoryKeepsLargeList(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "refit-test"));
        List<ItemStack> candidates = candidates();
        ItemStack gunMarker = new ItemStack(Items.EMERALD);
        AttachmentRefitInventory inventory = new AttachmentRefitInventory(player, 4, gunMarker, candidates);

        helper.assertTrue(inventory.getContainerSize() == FIRST_CANDIDATE_SLOT + CANDIDATE_COUNT + 1,
                "virtual inventory retains all 2048 candidates and an empty unload slot");
        helper.assertTrue(inventory.getSelected() == gunMarker && inventory.getItem(4) == gunMarker,
                "candidate expansion preserves the selected gun slot");
        helper.assertTrue(inventory.items.size() >= inventory.getContainerSize(),
                "Mixin-backed inventory storage expands beyond the vanilla inventory");
        for (int index = 0; index < CANDIDATE_COUNT; index++) {
            int slot = FIRST_CANDIDATE_SLOT + index;
            assertCandidate(helper, inventory.getItem(slot), index, "virtual slot " + slot);
            assertCandidate(helper, inventory.items.get(slot), index, "backing slot " + slot);
        }

        int lastSlot = FIRST_CANDIDATE_SLOT + CANDIDATE_COUNT - 1;
        inventory.setItem(lastSlot, candidate(CANDIDATE_COUNT));
        assertCandidate(helper, inventory.getItem(lastSlot), CANDIDATE_COUNT - 1, "last candidate after old-item return");
        assertCandidate(helper, inventory.items.get(lastSlot), CANDIDATE_COUNT - 1, "last backing slot after return");

        int unloadSlot = inventory.getFreeSlot();
        helper.assertTrue(unloadSlot == lastSlot + 1 && inventory.getItem(unloadSlot).isEmpty(),
                "a full candidate list still exposes an empty unload slot");
        ItemStack returned = candidate(CANDIDATE_COUNT + 1);
        helper.assertTrue(inventory.add(returned) && returned.isEmpty(),
                "unloading acknowledges and consumes the returned preview item");
        helper.assertTrue(inventory.getItem(unloadSlot).isEmpty() && inventory.items.get(unloadSlot).isEmpty()
                        && inventory.getFreeSlot() == unloadSlot
                        && inventory.getContainerSize() == unloadSlot + 1,
                "unloading never appends a candidate or changes slot numbers");
        inventory.setItem(lastSlot, ItemStack.EMPTY);
        assertCandidate(helper, inventory.getItem(lastSlot), CANDIDATE_COUNT - 1,
                "consuming the last candidate preserves its fixed template");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidateSlotsRemainStableAndIsolated(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper);
        List<ItemStack> source = new ArrayList<>(Arrays.asList(candidate(0), ItemStack.EMPTY, candidate(2), null, candidate(4)));
        ItemStack gun = new ItemStack(Items.EMERALD);
        AttachmentRefitInventory inventory = new AttachmentRefitInventory(player, 2, gun, source);
        source.get(0).getOrCreateTag().putInt("CandidateIndex", 99);
        source.clear();

        // Consecutive same-item selections used to consume a list element and shift the next one.
        for (int index : new int[]{0, 2, 4, 2, 0, 4}) {
            int slot = FIRST_CANDIDATE_SLOT + index;
            ItemStack selected = inventory.getItem(slot);
            assertCandidate(helper, selected, index, "repeated selection");
            selected.shrink(1);
            inventory.setItem(slot, candidate(99));
            inventory.setItem(slot, ItemStack.EMPTY);
            assertCandidate(helper, inventory.getItem(slot), index, "selection after consume and replacement");
        }

        ItemStack fromBacking = inventory.items.get(FIRST_CANDIDATE_SLOT + 2);
        fromBacking.getOrCreateTag().putInt("CandidateIndex", 99);
        fromBacking.shrink(1);
        inventory.items.set(FIRST_CANDIDATE_SLOT + 2, candidate(99));
        assertCandidate(helper, inventory.getItem(FIRST_CANDIDATE_SLOT + 2), 2, "direct backing writes");
        assertCandidate(helper, inventory.removeItem(FIRST_CANDIDATE_SLOT + 2, 1), 2, "inventory consumption");
        assertCandidate(helper, inventory.removeItemNoUpdate(FIRST_CANDIDATE_SLOT + 2), 2, "inventory removal");
        assertCandidate(helper, inventory.items.get(FIRST_CANDIDATE_SLOT + 2), 2, "backing after removals");

        ItemStack returned = candidate(99);
        helper.assertTrue(inventory.add(inventory.getFreeSlot(), returned) && returned.isEmpty(),
                "indexed add consumes a returned preview item without changing the catalog");
        returned = candidate(99);
        inventory.placeItemBackInInventory(returned, true);
        helper.assertTrue(returned.isEmpty(), "vanilla return helper absorbs the preview without a real-inventory packet");
        List<ItemStack> snapshot = inventory.snapshotCandidates();
        helper.assertTrue(snapshot.size() == 5 && snapshot.get(1).isEmpty() && snapshot.get(3).isEmpty(),
                "snapshot retains empty and null positions as empty slots");
        assertCandidate(helper, snapshot.get(0), 0, "constructor snapshot isolation");
        assertCandidate(helper, snapshot.get(2), 2, "middle snapshot slot");
        assertCandidate(helper, snapshot.get(4), 4, "last snapshot slot");
        snapshot.get(0).getOrCreateTag().putInt("CandidateIndex", 99);
        snapshot.clear();
        assertCandidate(helper, inventory.snapshotCandidates().get(0), 0, "outbound snapshot isolation");

        ItemStack replacementGun = new ItemStack(Items.DIAMOND);
        inventory.items.set(2, replacementGun);
        helper.assertTrue(inventory.getSelected() == replacementGun && inventory.getItem(2) == replacementGun,
                "the public backing view still updates the actual working gun");
        replacementGun.getOrCreateTag().putBoolean("Changed", true);
        helper.assertTrue(inventory.items.get(2).getOrCreateTag().getBoolean("Changed"),
                "working gun mutations remain visible through both inventory interfaces");
        inventory.clearContent();
        helper.assertTrue(inventory.getSelected().isEmpty() && inventory.getCandidateCount() == 5,
                "clearing inventory contents does not delete the session catalog");
        assertCandidate(helper, inventory.getItem(FIRST_CANDIDATE_SLOT + 4), 4, "catalog after clear");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidatePacketPreservesLargeList(GameTestHelper helper) {
        List<ItemStack> decoded = transfer(helper, candidates());
        helper.assertTrue(decoded.size() == CANDIDATE_COUNT, "all 2048 candidates survive fragment assembly");
        for (int index = 0; index < CANDIDATE_COUNT; index++) {
            assertCandidate(helper, decoded.get(index), index, "assembled candidate " + index);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidatePacketPreservesEmptyPositions(GameTestHelper helper) {
        List<ItemStack> decoded = transfer(helper, List.of(candidate(0), ItemStack.EMPTY, candidate(2), ItemStack.EMPTY));
        helper.assertTrue(decoded.size() == 4 && decoded.get(1).isEmpty() && decoded.get(3).isEmpty(),
                "packet assembly preserves both middle and trailing empty slots");
        assertCandidate(helper, decoded.get(0), 0, "first assembled slot");
        assertCandidate(helper, decoded.get(2), 2, "assembled slot after a hole");
        helper.assertTrue(transfer(helper, List.of()).isEmpty(), "an empty catalog is also a complete snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidateFragmentsSplitLargeIndividualItems(GameTestHelper helper) {
        ItemStack large = candidate(37);
        byte[] tagBytes = new byte[AttachmentCandidateTransfer.MAX_CHUNK_BYTES * 3 + 111];
        for (int i = 0; i < tagBytes.length; i++) {
            tagBytes[i] = (byte) (i * 17);
        }
        large.getOrCreateTag().putByteArray("LargeCandidateData", tagBytes);
        List<ItemStack> original = new ArrayList<>(candidates());
        original.set(CANDIDATE_COUNT / 2, ItemStack.EMPTY);
        original.set(CANDIDATE_COUNT - 1, large);
        List<ItemStack> decoded = transfer(helper, original);
        helper.assertTrue(decoded.size() == CANDIDATE_COUNT && decoded.get(CANDIDATE_COUNT / 2).isEmpty(),
                "large item fragmentation retains every candidate and existing holes");
        assertCandidate(helper, decoded.get(CANDIDATE_COUNT - 1), 37, "large candidate at the final position");
        helper.assertTrue(Arrays.equals(tagBytes, decoded.get(CANDIDATE_COUNT - 1).getTag().getByteArray("LargeCandidateData")),
                "a single item larger than a packet is reassembled without truncation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidateFragmentsRejectIncompleteOrOutOfOrderData(GameTestHelper helper) {
        AttachmentCandidateTransfer assembler = new AttachmentCandidateTransfer();
        helper.assertTrue(assembler.accept(0, 4, false, new byte[]{1, 2}) == null,
                "an incomplete snapshot does not publish data");
        boolean rejected = false;
        try {
            assembler.accept(3, 4, true, new byte[]{4});
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "missing fragment bytes cannot silently form a truncated list");
        rejected = false;
        try {
            assembler.accept(2, 4, true, new byte[]{3, 4});
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "invalid transfers clear partial state before accepting another tail");
        helper.assertTrue(assembler.accept(0, 4, false, new byte[]{1, 2}) == null,
                "a fresh snapshot restarts cleanly");
        helper.assertTrue(Arrays.equals(assembler.accept(0, 2, true, new byte[]{9, 8}), new byte[]{9, 8}),
                "a new ordered snapshot start replaces unfinished data without a list version protocol");
        rejected = false;
        try {
            assembler.accept(0, 4, true, new byte[]{1, 2});
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "an early completion flag cannot truncate the declared snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidateBlacklistRestoresOriginalNumbers(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper);
        List<ItemStack> source = taczCandidates();
        source.set(3, ItemStack.EMPTY);
        WeaponFilterConfig filter = new WeaponFilterConfig();
        filter.setBlockedAttachmentIds(List.of(attachmentId(0).toString(), attachmentId(CANDIDATE_COUNT - 1).toString()));
        filter.setBlockedAttachmentNamespaces(List.of());
        try {
            AttachmentEditSessionManager.startSession(player, 7, "primary", new ItemStack(Items.EMERALD), source);
            List<ItemStack> blocked = AttachmentEditSessionManager.snapshotVisibleAttachmentCandidates(player, filter);
            helper.assertTrue(blocked.size() == CANDIDATE_COUNT && blocked.get(0).isEmpty()
                            && blocked.get(3).isEmpty() && blocked.get(CANDIDATE_COUNT - 1).isEmpty(),
                    "ID bans use holes at the first and final candidate without shrinking the directory");
            assertAttachmentId(helper, blocked.get(1), 1, "neighbor after a blocked first candidate");
            helper.assertTrue(!AttachmentEditSessionManager.snapshotAttachmentCandidates(player).get(0).isEmpty(),
                    "server catalog retains initially blocked identities for future unbans");
            filter.setBlockedAttachmentIds(List.of());
            filter.setBlockedAttachmentNamespaces(List.of(" CODPATTERN_REFIT "));
            List<ItemStack> namespaceBlocked = AttachmentEditSessionManager.snapshotVisibleAttachmentCandidates(player, filter);
            helper.assertTrue(namespaceBlocked.size() == CANDIDATE_COUNT
                            && namespaceBlocked.stream().allMatch(ItemStack::isEmpty),
                    "namespace bans keep all original positions, including existing holes");
            filter.setBlockedAttachmentNamespaces(List.of());
            List<ItemStack> restored = transfer(helper,
                    AttachmentEditSessionManager.snapshotVisibleAttachmentCandidates(player, filter));
            helper.assertTrue(restored.size() == CANDIDATE_COUNT && restored.get(3).isEmpty(),
                    "unbanning keeps the original empty slot");
            assertAttachmentId(helper, restored.get(0), 0, "restored first candidate");
            assertAttachmentId(helper, restored.get(CANDIDATE_COUNT - 1), CANDIDATE_COUNT - 1,
                    "restored final candidate beyond vanilla and single-packet limits");
            assertAttachmentId(helper, AttachmentEditSessionManager.getSession(player.getUUID()).getRefitInventory()
                    .getItem(FIRST_CANDIDATE_SLOT + CANDIDATE_COUNT - 1), CANDIDATE_COUNT - 1,
                    "server operation still uses the original final slot after every filter update");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidateSessionPreservesFullListAndRestoresInventory(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper);
        Inventory realInventory = player.getInventory();
        List<ItemStack> original = new ArrayList<>();
        for (int slot = 0; slot < realInventory.getContainerSize(); slot++) {
            ItemStack stack = candidate(CANDIDATE_COUNT + slot);
            realInventory.setItem(slot, stack.copy());
            original.add(stack);
        }
        realInventory.selected = 6;
        ItemStack gunMarker = new ItemStack(Items.EMERALD);
        try {
            var session = AttachmentEditSessionManager.startSession(player, 7, "primary", gunMarker, taczCandidates());
            helper.assertTrue(session.getRefitInventory().getCandidateCount() == CANDIDATE_COUNT,
                    "the actual session retains all 2048 TaCZ attachment candidates");
            helper.assertTrue(BackpackRefitSessionContext.isBackpackRefitActive(player),
                    "the actual session activates its inventory context");
            for (int slot = 0; slot < realInventory.getContainerSize(); slot++) {
                helper.assertTrue(slot == 6 ? realInventory.getItem(slot).is(Items.EMERALD)
                                : realInventory.getItem(slot).isEmpty(),
                        "real inventory slot " + slot + " contains no mirrored candidate");
            }
            List<ItemStack> snapshot = AttachmentEditSessionManager.snapshotAttachmentCandidates(player);
            helper.assertTrue(snapshot.size() == CANDIDATE_COUNT,
                    "the session snapshot includes every candidate beyond vanilla inventory capacity");
            for (int index = 0; index < CANDIDATE_COUNT; index++) {
                helper.assertTrue(TaczGatewayProvider.gateway().resolveAttachmentId(snapshot.get(index))
                                .filter(attachmentId(index).toString()::equals).isPresent(),
                        "session snapshot preserves attachment " + index);
            }
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.assertTrue(!AttachmentEditSessionManager.hasSession(player.getUUID())
                        && !BackpackRefitSessionContext.isBackpackRefitActive(player)
                        && realInventory.selected == 6,
                "ending the session clears its context and restores the selected slot");
        for (int slot = 0; slot < original.size(); slot++) {
            ItemStack actual = realInventory.getItem(slot);
            ItemStack expected = original.get(slot);
            helper.assertTrue(actual.getItem() == expected.getItem() && actual.getCount() == expected.getCount()
                            && Objects.equals(actual.getTag(), expected.getTag()),
                    "ending the session restores original inventory slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentSessionReplacementAndAbortRestoreOriginalInventory(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper);
        Inventory realInventory = player.getInventory();
        realInventory.selected = 4;
        ItemStack original = candidate(73);
        realInventory.setItem(0, original.copy());
        realInventory.setItem(4, new ItemStack(Items.DIAMOND));
        List<ItemStack> withHoles = List.of(
                AttachmentItemBuilder.create().setId(attachmentId(0)).build(), ItemStack.EMPTY,
                AttachmentItemBuilder.create().setId(attachmentId(2)).build());
        try {
            var first = AttachmentEditSessionManager.startSession(player, 7, "primary",
                    new ItemStack(Items.EMERALD), withHoles);
            List<ItemStack> snapshot = AttachmentEditSessionManager.snapshotAttachmentCandidates(player);
            helper.assertTrue(snapshot.size() == 3 && snapshot.get(1).isEmpty()
                            && TaczGatewayProvider.gateway().resolveAttachmentId(snapshot.get(2))
                            .filter(attachmentId(2).toString()::equals).isPresent(),
                    "session synchronization preserves the candidate position after an empty slot");
            var second = AttachmentEditSessionManager.startSession(player, 8, "secondary",
                    new ItemStack(Items.GOLD_INGOT), withHoles);
            helper.assertTrue(second != first && realInventory.getSelected().is(Items.GOLD_INGOT),
                    "opening another session replaces its working gun");
            AttachmentEditSessionManager.abortSession(player, "test_abort", false);
            helper.assertTrue(!AttachmentEditSessionManager.hasSession(player.getUUID())
                            && !BackpackRefitSessionContext.isBackpackRefitActive(player)
                            && realInventory.selected == 4 && realInventory.getSelected().is(Items.DIAMOND),
                    "aborting the replacement restores the original selected item and context");
            assertCandidate(helper, realInventory.getItem(0), 73, "original inventory after session replacement");
            realInventory.setItem(1, new ItemStack(Items.IRON_INGOT));
            AttachmentEditSessionManager.abortSession(player, "repeated_abort", false);
            AttachmentEditSessionManager.endSession(player);
            helper.assertTrue(realInventory.getItem(1).is(Items.IRON_INGOT),
                    "repeated cleanup without a session does not overwrite later inventory changes");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidateAddonUsesCdpInventoryWhenPresent(GameTestHelper helper) throws Exception {
        Class<?> addonClass;
        try {
            addonClass = Class.forName("com.mafuyu404.taczaddon.common.LiberateAttachment");
        } catch (ClassNotFoundException absent) {
            helper.assertTrue(!ModList.get().isLoaded("taczaddon"),
                    "an installed addon must expose its expected inventory entry point");
            LogUtils.getLogger().info("Attachment candidate addon test: not-installed; optional dependency is absent");
            helper.succeed();
            return;
        }
        var useVirtualInventory = addonClass.getMethod("useVirtualInventory", Inventory.class);
        var ruleClass = Class.forName("com.mafuyu404.taczaddon.init.RuleRegistry");
        var key = (GameRules.Key<GameRules.BooleanValue>) ruleClass.getField("LIBERATE_ATTACHMENT").get(null);
        var rule = helper.getLevel().getGameRules().getRule(key);
        boolean previousRule = rule.get();
        ServerPlayer player = fakePlayer(helper);
        Inventory sourceInventory = player.getInventory();
        try {
            for (boolean liberated : new boolean[]{false, true}) {
                rule.set(liberated, helper.getLevel().getServer());
                helper.assertTrue(useVirtualInventory.invoke(null, sourceInventory) == sourceInventory,
                        "outside a CDP session addon keeps the source inventory with liberateAttachment=" + liberated);
                try {
                    AttachmentEditSessionManager.startSession(player, 7, "primary", new ItemStack(Items.EMERALD), taczCandidates());
                    Inventory refitInventory = AttachmentEditSessionManager.getRefitInventory(player);
                    helper.assertTrue(refitInventory != null
                                    && useVirtualInventory.invoke(null, sourceInventory) == refitInventory
                                    && useVirtualInventory.invoke(null, refitInventory) == refitInventory,
                            "active session uses CDP inventory with liberateAttachment=" + liberated);
                } finally {
                    AttachmentEditSessionManager.endSession(player);
                }
            }
            LogUtils.getLogger().info("Attachment candidate addon test: source/session inventories verified with liberation off and on");
        } finally {
            AttachmentEditSessionManager.endSession(player);
            rule.set(previousRule, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidatesComeFromLoadedTaczCatalog(GameTestHelper helper) {
        var catalog = TimelessAPI.getAllCommonAttachmentIndex();
        helper.assertTrue(!catalog.isEmpty(), "TaCZ must load its real attachment resources for this catalog test");
        Set<String> expectedIds = new HashSet<>();
        for (var entry : catalog) {
            var index = entry.getValue();
            if (index.getType() != null && index.getType() != AttachmentType.NONE
                    && !index.getPojo().isHidden() && !DefaultAssets.isEmptyAttachmentId(entry.getKey())) {
                expectedIds.add(entry.getKey().toString());
            }
        }
        var gateway = TaczGatewayProvider.gateway();
        List<ItemStack> candidates = gateway.listAttachmentCandidates();
        Set<String> actualIds = new HashSet<>();
        for (ItemStack stack : candidates) {
            var id = gateway.resolveAttachmentId(stack);
            helper.assertTrue(id.isPresent(), "catalog candidates must resolve to TaCZ attachment IDs");
            helper.assertTrue(actualIds.add(id.orElseThrow()), "catalog candidates must not contain duplicate IDs");
        }
        helper.assertTrue(actualIds.equals(expectedIds),
                "CDP independently enumerates every visible attachment in the loaded TaCZ catalog");
        LogUtils.getLogger().info("Attachment candidate catalog test: resources={} candidates={} taczaddon={}",
                catalog.size(), candidates.size(), ModList.get().isLoaded("taczaddon"));
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "refit-test"));
    }

    private static List<ItemStack> taczCandidates() {
        List<ItemStack> result = new ArrayList<>(CANDIDATE_COUNT);
        for (int index = 0; index < CANDIDATE_COUNT; index++) {
            result.add(AttachmentItemBuilder.create().setId(attachmentId(index)).build());
        }
        return result;
    }

    private static ResourceLocation attachmentId(int index) {
        return new ResourceLocation("codpattern_refit", "candidate_" + index);
    }

    private static List<ItemStack> candidates() {
        List<ItemStack> result = new ArrayList<>(CANDIDATE_COUNT);
        for (int index = 0; index < CANDIDATE_COUNT; index++) {
            result.add(candidate(index));
        }
        return result;
    }

    private static ItemStack candidate(int index) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putInt("CandidateIndex", index);
        return stack;
    }

    private static void assertCandidate(GameTestHelper helper, ItemStack actual, int index, String context) {
        helper.assertTrue(actual.is(Items.PAPER) && actual.getCount() == 1 && actual.hasTag()
                        && actual.getTag().contains("CandidateIndex")
                        && actual.getTag().getInt("CandidateIndex") == index,
                context + " preserves candidate " + index);
    }

    private static List<ItemStack> transfer(GameTestHelper helper, List<ItemStack> candidates) {
        UUID sessionId = UUID.randomUUID();
        List<SyncAttachmentCandidatesPacket> fragments = SyncAttachmentCandidatesPacket.split(
                sessionId, 7, "primary", candidates);
        AttachmentCandidateTransfer assembler = new AttachmentCandidateTransfer();
        byte[] complete = null;
        int totalWireBytes = 0;
        int expectedOffset = 0;
        for (int i = 0; i < fragments.size(); i++) {
            FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
            FriendlyByteBuf reencoded = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SyncAttachmentCandidatesPacket.encode(fragments.get(i), encoded);
                helper.assertTrue(encoded.readableBytes() <= AttachmentCandidateTransfer.MAX_CHUNK_BYTES + 128,
                        "each candidate fragment remains within its bounded wire budget");
                totalWireBytes += encoded.readableBytes();
                byte[] originalBytes = bytes(encoded);
                SyncAttachmentCandidatesPacket decoded = SyncAttachmentCandidatesPacket.decode(encoded);
                helper.assertTrue(!encoded.isReadable() && decoded.sessionId().equals(sessionId)
                                && decoded.bagId() == 7 && decoded.slot().equals("primary")
                                && decoded.offset() == expectedOffset,
                        "fragment decoder retains its session, target, and contiguous byte offset");
                expectedOffset += decoded.payload().length;
                SyncAttachmentCandidatesPacket.encode(decoded, reencoded);
                helper.assertTrue(Arrays.equals(originalBytes, bytes(reencoded)),
                        "fragment re-encoding preserves its full payload");
                complete = assembler.accept(decoded.offset(), decoded.totalBytes(), decoded.complete(), decoded.payload());
                helper.assertTrue((complete != null) == (i == fragments.size() - 1),
                        "no partial list is published before its final fragment");
            } finally {
                encoded.release();
                reencoded.release();
            }
        }
        helper.assertTrue(complete != null, "every snapshot has a completed fragment sequence");
        LogUtils.getLogger().info("Attachment candidate transfer: candidates={} fragments={} snapshotBytes={} wireBytes={}",
                candidates.size(), fragments.size(), complete.length, totalWireBytes);
        return SyncAttachmentCandidatesPacket.readSnapshot(complete);
    }

    private static void assertAttachmentId(GameTestHelper helper, ItemStack stack, int index, String context) {
        helper.assertTrue(TaczGatewayProvider.gateway().resolveAttachmentId(stack)
                        .filter(attachmentId(index).toString()::equals).isPresent(),
                context + " retains attachment " + index);
    }

    private static byte[] bytes(FriendlyByteBuf buffer) {
        byte[] result = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), result);
        return result;
    }
}
