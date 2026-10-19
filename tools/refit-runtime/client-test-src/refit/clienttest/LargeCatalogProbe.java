package refit.clienttest;

import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentRefitInventory;
import com.cdp.codpattern.core.refit.AttachmentCandidateTransfer;
import com.cdp.codpattern.network.SyncAttachmentCandidatesPacket;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/** Synthetic 1,057-entry directory; real loaded attachment IDs and actual packet/UI paths. */
final class LargeCatalogProbe {
    private static final int TOTAL = 1057;
    private static int step;
    private static CompletableFuture<Void> update;
    private static int pagesVisited;
    private static String finalId;
    private static List<String> catalog;
    private static int lastPageTicks;

    @SuppressWarnings("unchecked")
    static void tick(Screen screen) throws Exception {
        if (update != null) {
            if (!update.isDone()) return;
            update.join(); update = null;
        }
        switch (step) {
            case 0 -> {
                if (!RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                var candidates = RefitScreenProbe.visibleCandidates(screen);
                if (candidates.size() < 2) throw new AssertionError("Need two real scope IDs");
                ItemStack ordinary = RefitScreenProbe.candidate(candidates.get(0));
                ItemStack last = RefitScreenProbe.candidate(candidates.get(1));
                finalId = RefitScreenProbe.attachmentId(last);
                List<ItemStack> synthetic = new ArrayList<>(TOTAL);
                for (int index = 0; index < TOTAL; index++) {
                    var item = (index == TOTAL - 1 ? last : ordinary).copy();
                    item.getOrCreateTag().putInt("ClientProbeDirectoryIndex", index);
                    synthetic.add(item);
                }
                catalog = synthetic.stream().map(RefitScreenProbe::attachmentId).toList();
                Minecraft mc = Minecraft.getInstance();
                UUID playerId = mc.player.getUUID();
                update = CompletableFuture.runAsync(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                        var original = AttachmentEditSessionManager.getSession(playerId);
                        var inventory = new AttachmentRefitInventory(player, original.getEditHotbarSlot(),
                                original.getRefitInventory().getSelected(), synthetic);
                        var replacement = new AttachmentEditSession(original.getSessionId(), original.getBagId(), original.getSlot(),
                                original.getEditHotbarSlot(), original.getOriginalSelectedSlot(), original.getInventorySnapshot(), inventory);
                        // Test-only fixture injection preserves the original real-inventory restore snapshot.
                        // The normal initial request already established and synchronized this session UUID.
                        var field = AttachmentEditSessionManager.class.getDeclaredField("SESSIONS");
                        field.setAccessible(true);
                        ((Map<UUID, AttachmentEditSession>) field.get(null)).put(playerId, replacement);
                        var fragments = SyncAttachmentCandidatesPacket.split(replacement.getSessionId(),
                                replacement.getBagId(), replacement.getSlot(), synthetic);
                        if (fragments.size() < 2 || fragments.stream().anyMatch(fragment ->
                                fragment.payload().length > AttachmentCandidateTransfer.MAX_CHUNK_BYTES)) {
                            throw new AssertionError("Large fixture did not exercise multiple bounded fragments");
                        }
                        LogUtils.getLogger().info("CDP_CLIENT_PROBE directory_fragments={} totalBytes={}",
                                fragments.size(), fragments.get(0).totalBytes());
                        ClientProbeMod.evidence("directoryFragments", fragments.size());
                        ClientProbeMod.evidence("directoryBytes", fragments.get(0).totalBytes());
                        AttachmentEditSessionManager.syncVisibleAttachmentCandidates(player);
                    } catch (Exception failure) {
                        throw new IllegalStateException("Synthetic directory fixture failed", failure);
                    }
                }, mc.getSingleplayerServer());
                step = 1;
            }
            case 1 -> {
                if (AttachmentRefitClientState.getAttachmentCandidates().size() != TOTAL) return;
                if (!catalog.equals(RefitScreenProbe.catalogIds())) throw new AssertionError("Full synthetic directory was not received");
                var full = AttachmentRefitClientState.getAttachmentCandidates();
                for (int index = 0; index < TOTAL; index++) {
                    if (full.get(index).getOrCreateTag().getInt("ClientProbeDirectoryIndex") != index) {
                        throw new AssertionError("Directory fragment assembly changed item payload at " + index);
                    }
                }
                RefitScreenProbe.replaceExpectedCatalog(catalog);
                if (!RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                step = 2;
            }
            case 2 -> {
                var currentPage = GunRefitScreen.class.getDeclaredField("currentPage");
                currentPage.setAccessible(true);
                int before = currentPage.getInt(screen);
                var visible = RefitScreenProbe.visibleCandidates(screen);
                if (visible.isEmpty()) throw new AssertionError("Synthetic directory unexpectedly empty");
                var widget = visible.get(0);
                if (screen.mouseScrolled(widget.getX() + 2, widget.getY() + 2, -1)
                        || before != currentPage.getInt(screen)) throw new AssertionError("Unexpected wheel paging semantics");
                var direction = RefitTurnPageButton.class.getDeclaredField("isUpPage");
                direction.setAccessible(true);
                for (var child : List.copyOf(screen.children())) {
                    if (child instanceof RefitTurnPageButton button && !direction.getBoolean(button)) {
                        ClientProbeMod.click(screen, button);
                        if (currentPage.getInt(screen) != before + 1) throw new AssertionError("Page button did not advance");
                        pagesVisited++;
                        return;
                    }
                }
                var last = visible.get(visible.size() - 1);
                if (pagesVisited + 1 != (TOTAL + 7) / 8 || before != (TOTAL - 1) / 8) {
                    throw new AssertionError("Did not visit every real UI page: " + pagesVisited + "/" + before);
                }
                if (last.getSlotIndex() != 9 + TOTAL - 1 || !finalId.equals(RefitScreenProbe.attachmentId(RefitScreenProbe.candidate(last)))) {
                    throw new AssertionError("Final stable candidate slot was truncated or substituted");
                }
                step = 7;
            }
            case 7 -> {
                if (++lastPageTicks < 20) return;
                var visible = RefitScreenProbe.visibleCandidates(screen);
                var last = visible.get(visible.size() - 1);
                if (last.getSlotIndex() != 9 + TOTAL - 1) throw new AssertionError("Final page changed while settling");
                RefitScreenProbe.expected.put(AttachmentType.SCOPE, finalId);
                ClientProbeMod.screenshot("large-catalog-last-page");
                ClientProbeMod.click(screen, last);
                LogUtils.getLogger().info("CDP_CLIENT_PROBE synthetic_directory total={} pagesVisited={} lastSlot={}",
                        TOTAL, pagesVisited + 1, last.getSlotIndex());
                ClientProbeMod.evidence("syntheticDirectoryCount", TOTAL);
                ClientProbeMod.evidence("pagesVisited", pagesVisited + 1);
                ClientProbeMod.evidence("finalCandidateSlot", last.getSlotIndex());
                step = 3;
            }
            case 3 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                RefitScreenProbe.verifyWorking(); step = 4;
            }
            case 4 -> {
                if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("ESC was rejected");
                step = 5;
            }
            case 5 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                RefitScreenProbe.verifySaved(); step = 6;
            }
            case 6 -> ClientProbeMod.finish(true, "large_catalog_last_page", null);
            default -> throw new AssertionError("Unknown large-directory phase " + step);
        }
    }
}
