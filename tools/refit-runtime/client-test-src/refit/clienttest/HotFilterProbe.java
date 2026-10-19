package refit.clienttest;

import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Exercises live filter reloads; only the stale request is an explicit native-packet replay. */
final class HotFilterProbe {
    private static final int FIRST_CANDIDATE_SLOT = 9;
    private static final String BLOCKED_MESSAGE = "message.codpattern.refit.save_failed_attachment_blocked";
    private static int step;
    private static int reportedStep = -1;
    private static UUID playerId;
    private static UUID sessionId;
    private static int candidateSlot;
    private static int candidateIndex;
    private static String candidateId;
    private static ItemStack originalCandidate;
    private static List<ItemStack> originalCatalog;
    private static List<String> originalBlockedIds;
    private static Map<AttachmentType, String> beforeInstall;
    private static int previousBlockedMessages;
    private static CompletableFuture<Void> serverTask;

    private HotFilterProbe() { }

    static void tick(Screen screen) throws Exception {
        if (serverTask != null) {
            if (!serverTask.isDone()) return;
            serverTask.join();
            serverTask = null;
        }
        if (reportedStep != step) {
            LogUtils.getLogger().info("CDP_CLIENT_PROBE hot_filter_step={}", step);
            reportedStep = step;
        }
        Minecraft mc = Minecraft.getInstance();
        if (step < 7 && (!(screen instanceof CodGunRefitScreen)
                || !AttachmentRefitClientState.acceptsTaczOperations())) {
            throw new AssertionError("Hot filter test lost its active refit screen");
        }
        switch (step) {
            case 0 -> {
                if (!RefitScreenProbe.acknowledged()
                        || !RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                originalCatalog = AttachmentRefitClientState.getAttachmentCandidates();
                beforeInstall = Map.copyOf(RefitScreenProbe.expected);
                var candidates = RefitScreenProbe.visibleCandidates(screen);
                InventoryAttachmentSlot selected = null;
                for (var button : candidates) {
                    ItemStack item = RefitScreenProbe.candidate(button);
                    String id = RefitScreenProbe.attachmentId(item);
                    if (!id.isEmpty() && !id.equals(beforeInstall.get(AttachmentType.SCOPE))) {
                        selected = button;
                        originalCandidate = item.copy();
                        candidateId = id;
                        break;
                    }
                }
                if (selected == null) throw new AssertionError("No uninstalled scope candidate for the blacklist probe");
                candidateSlot = selected.getSlotIndex();
                candidateIndex = candidateSlot - FIRST_CANDIDATE_SLOT;
                if (candidateIndex < 0 || candidateIndex >= originalCatalog.size()
                        || !sameStack(originalCatalog.get(candidateIndex), originalCandidate)) {
                    throw new AssertionError("Visible scope does not match its stable catalog slot");
                }
                playerId = mc.player.getUUID();
                sessionId = AttachmentRefitClientState.getSessionId();
                MinecraftServer server = mc.getSingleplayerServer();
                serverTask = CompletableFuture.runAsync(() -> {
                    assertServerState(beforeInstall);
                    var config = WeaponFilterConfigRepository.loadOrCreate(ConfigPath.SERVER_FILTER.getPath(server));
                    originalBlockedIds = new ArrayList<>(config.getBlockedAttachmentIds());
                    if (originalBlockedIds.contains(candidateId)) {
                        throw new AssertionError("Visible fixture candidate was already blocked");
                    }
                    var blocked = new ArrayList<>(originalBlockedIds);
                    blocked.add(candidateId);
                    config.setBlockedAttachmentIds(blocked);
                    WeaponFilterConfigRepository.save(config);
                    reloadThroughCommand(server);
                }, server);
                step++;
            }
            case 1 -> {
                if (!maskedCatalogArrived(screen)) return;
                assertGun(AttachmentRefitClientState.resolveRefitScreenInventory(mc.player).getSelected(), beforeInstall);
                ClientProbeMod.screenshot("hot-filter-hidden");
                previousBlockedMessages = blockedMessageCount();
                // The old button has been removed by the real UI refresh. Replay its native
                // wire request explicitly instead of resurrecting or directly invoking it.
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageRefitGun(
                        candidateSlot, mc.player.getInventory().selected, AttachmentType.SCOPE));
                if (RefitScreenProbe.acknowledged()) {
                    throw new AssertionError("Native stale request bypassed the CDP operation queue");
                }
                LogUtils.getLogger().info(
                        "CDP_CLIENT_PROBE native_stale_packet_replay slot={} id={} (not a click on a removed widget)",
                        candidateSlot, candidateId);
                step++;
            }
            case 2 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                if (!maskedCatalogArrived(screen)) throw new AssertionError("Stale request changed the masked catalog");
                if (blockedMessageCount() <= previousBlockedMessages) {
                    throw new AssertionError("Stale request did not return the explicit attachment-blocked feedback");
                }
                assertGun(AttachmentRefitClientState.resolveRefitScreenInventory(mc.player).getSelected(), beforeInstall);
                serverTask = CompletableFuture.runAsync(() -> assertServerState(beforeInstall), mc.getSingleplayerServer());
                step++;
            }
            case 3 -> {
                MinecraftServer server = mc.getSingleplayerServer();
                serverTask = CompletableFuture.runAsync(() -> {
                    var config = WeaponFilterConfigRepository.loadOrCreate(ConfigPath.SERVER_FILTER.getPath(server));
                    config.setBlockedAttachmentIds(originalBlockedIds);
                    WeaponFilterConfigRepository.save(config);
                    reloadThroughCommand(server);
                }, server);
                step++;
            }
            case 4 -> {
                var catalog = AttachmentRefitClientState.getAttachmentCandidates();
                if (catalog.size() != originalCatalog.size()) throw new AssertionError("Unban changed catalog length");
                if (catalog.get(candidateIndex).isEmpty()) return;
                assertCatalog(catalog, false);
                InventoryAttachmentSlot restored = null;
                for (var button : RefitScreenProbe.visibleCandidates(screen)) {
                    if (button.getSlotIndex() == candidateSlot) {
                        if (!sameStack(RefitScreenProbe.candidate(button), originalCandidate)) {
                            throw new AssertionError("Restored button points at a different candidate");
                        }
                        restored = button;
                    }
                }
                if (restored == null) return;
                RefitScreenProbe.expected.put(AttachmentType.SCOPE, candidateId);
                ClientProbeMod.screenshot("hot-filter-restored");
                ClientProbeMod.click(screen, restored);
                if (RefitScreenProbe.acknowledged()) throw new AssertionError("Restored UI click did not enqueue installation");
                step++;
            }
            case 5 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                RefitScreenProbe.verifyWorking();
                step++;
            }
            case 6 -> {
                ClientProbeMod.screenshot("hot-filter-installed");
                if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("Hot filter save ESC was not consumed");
                step++;
            }
            case 7 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                RefitScreenProbe.verifySaved();
                step++;
            }
            case 8 -> ClientProbeMod.finish(true, "hot_filter_refreshed", null);
            default -> throw new AssertionError("Unknown hot filter phase " + step);
        }
    }

    private static void reloadThroughCommand(MinecraftServer server) {
        int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "cdp update");
        if (result != 1) throw new AssertionError("The production cdp update command failed: " + result);
    }

    private static boolean maskedCatalogArrived(Screen screen) throws Exception {
        var catalog = AttachmentRefitClientState.getAttachmentCandidates();
        if (catalog.size() != originalCatalog.size()) throw new AssertionError("Blacklist refresh truncated the catalog");
        if (!catalog.get(candidateIndex).isEmpty()) return false;
        assertCatalog(catalog, true);
        for (var button : RefitScreenProbe.visibleCandidates(screen)) {
            if (button.getSlotIndex() == candidateSlot
                    || candidateId.equals(RefitScreenProbe.attachmentId(RefitScreenProbe.candidate(button)))) {
                return false;
            }
        }
        return true;
    }

    private static void assertCatalog(List<ItemStack> catalog, boolean masked) {
        for (int index = 0; index < originalCatalog.size(); index++) {
            ItemStack expected = masked && index == candidateIndex ? ItemStack.EMPTY : originalCatalog.get(index);
            if (!sameStack(catalog.get(index), expected)) {
                throw new AssertionError("Filter refresh changed candidate identity/slot at index " + index);
            }
        }
    }

    private static void assertServerState(Map<AttachmentType, String> expected) {
        AttachmentEditSession session = AttachmentEditSessionManager.getSession(playerId);
        if (session == null || !sessionId.equals(session.getSessionId())) {
            throw new AssertionError("Filter refresh or stale request lost the server session");
        }
        if (!sameStack(session.getRefitInventory().getItem(candidateSlot), originalCandidate)) {
            throw new AssertionError("Blacklist mutated the server's original candidate identity");
        }
        assertGun(session.getRefitInventory().getSelected(), expected);
    }

    private static void assertGun(ItemStack gun, Map<AttachmentType, String> expected) {
        var api = IGun.getIGunOrNull(gun);
        if (api == null) throw new AssertionError("Missing hot-filter working gun");
        expected.forEach((type, id) -> {
            String actual = RefitScreenProbe.attachmentId(api.getAttachment(gun, type));
            if (!id.equals(actual)) throw new AssertionError("Hot filter changed " + type + ": " + id + " -> " + actual);
        });
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameTags(first, second);
    }

    private static int blockedMessageCount() throws ReflectiveOperationException {
        ChatComponent chat = Minecraft.getInstance().gui.getChat();
        for (Field field : ChatComponent.class.getDeclaredFields()) {
            // Reflection by the retained generic type avoids unmapped Minecraft field-name strings.
            if (!(field.getGenericType() instanceof ParameterizedType generic)
                    || generic.getActualTypeArguments().length != 1
                    || generic.getActualTypeArguments()[0] != GuiMessage.class
                    || !List.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            int count = 0;
            for (Object value : (List<?>) field.get(chat)) {
                GuiMessage message = (GuiMessage) value;
                if (message.content().getContents() instanceof TranslatableContents text
                        && BLOCKED_MESSAGE.equals(text.getKey())) count++;
            }
            return count;
        }
        throw new NoSuchFieldException("ChatComponent has no List<GuiMessage> feedback history");
    }
}
