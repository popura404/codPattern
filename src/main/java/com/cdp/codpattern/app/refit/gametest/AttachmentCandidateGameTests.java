package com.cdp.codpattern.app.refit.gametest;

import com.cdp.codpattern.core.refit.AttachmentRefitInventory;
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
    private static final int CANDIDATE_COUNT = 1024;
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
                "virtual inventory retains all 1024 candidates and an empty unload slot");
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
        assertCandidate(helper, inventory.getItem(lastSlot), CANDIDATE_COUNT, "last candidate replacement");
        assertCandidate(helper, inventory.items.get(lastSlot), CANDIDATE_COUNT, "last backing replacement");

        int unloadSlot = inventory.getFreeSlot();
        helper.assertTrue(unloadSlot == lastSlot + 1 && inventory.getItem(unloadSlot).isEmpty(),
                "a full candidate list still exposes an empty unload slot");
        helper.assertTrue(inventory.add(candidate(CANDIDATE_COUNT + 1)),
                "unloading another attachment succeeds beyond all 1024 candidates");
        assertCandidate(helper, inventory.getItem(unloadSlot), CANDIDATE_COUNT + 1, "returned attachment");
        assertCandidate(helper, inventory.items.get(unloadSlot), CANDIDATE_COUNT + 1, "returned backing attachment");
        helper.assertTrue(inventory.getFreeSlot() == unloadSlot + 1
                        && inventory.getContainerSize() == unloadSlot + 2
                        && inventory.getItem(inventory.getFreeSlot()).isEmpty(),
                "unloading grows the inventory and retains another empty slot");

        inventory.setItem(unloadSlot, ItemStack.EMPTY);
        helper.assertTrue(inventory.getContainerSize() == FIRST_CANDIDATE_SLOT + CANDIDATE_COUNT + 1
                        && inventory.getFreeSlot() == unloadSlot
                        && inventory.getItem(unloadSlot).isEmpty(),
                "removing the appended candidate restores the empty tail");
        assertCandidate(helper, inventory.getItem(lastSlot), CANDIDATE_COUNT,
                "removing the tail preserves the previous last candidate");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "attachment_candidates", timeoutTicks = 100)
    public static void attachmentCandidatePacketPreservesLargeList(GameTestHelper helper) {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf reencoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SyncAttachmentCandidatesPacket.encode(new SyncAttachmentCandidatesPacket(7, "primary", candidates()), encoded);
            byte[] originalBytes = bytes(encoded);

            helper.assertTrue(encoded.readInt() == 7 && "primary".equals(encoded.readUtf()),
                    "candidate packet retains the target backpack and slot");
            helper.assertTrue(encoded.readVarInt() == CANDIDATE_COUNT,
                    "candidate packet carries all 1024 entries");
            for (int index = 0; index < CANDIDATE_COUNT; index++) {
                assertCandidate(helper, encoded.readItem(), index, "packet candidate " + index);
            }
            helper.assertTrue(!encoded.isReadable(), "the packet contains exactly the declared candidates");

            encoded.readerIndex(0);
            SyncAttachmentCandidatesPacket decoded = SyncAttachmentCandidatesPacket.decode(encoded);
            helper.assertTrue(!encoded.isReadable(), "production decoder consumes the entire candidate packet");
            SyncAttachmentCandidatesPacket.encode(decoded, reencoded);
            helper.assertTrue(Arrays.equals(originalBytes, bytes(reencoded)),
                    "decode and re-encode preserve every candidate, its tag, and its order");
        } finally {
            encoded.release();
            reencoded.release();
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
                    "the actual session retains all 1024 TaCZ attachment candidates");
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

    private static byte[] bytes(FriendlyByteBuf buffer) {
        byte[] result = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), result);
        return result;
    }
}
