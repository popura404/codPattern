package refit.clienttest;

import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.client.gui.screen.WeaponScreen;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentRefitInventory;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Synthetic 1,057-scope directory and a single muzzle, using the production catalog refresh and UI. */
final class LargeCatalogProbe {
    private static final int SCOPE_COUNT = 1057;
    private static int step;
    private static CompletableFuture<Void> update;
    private static String finalId;
    private static List<String> catalog;
    private static double partialScroll;
    private static double finalScroll;
    private static int columnX = -1;
    private static int columnWidth;
    private static boolean installedInitialLaser;
    private static String builtinScope;

    @SuppressWarnings("unchecked")
    static void tick(Screen screen) throws Exception {
        if (update != null) {
            if (!update.isDone()) return;
            update.join(); update = null;
        }
        switch (step) {
            case 0 -> {
                if (columnX == -1) {
                    verifyPickerBounds(screen, true);
                    var slots = screen.children().stream().filter(GunAttachmentSlot.class::isInstance)
                            .map(GunAttachmentSlot.class::cast).filter(slot -> slot.visible).toList();
                    if (slots.isEmpty() || !RefitScreenProbe.visibleCandidates(screen).isEmpty()) {
                        throw new AssertionError("Expected just the loadout slots when refit opens");
                    }
                    columnX = slots.get(0).getX();
                    columnWidth = slots.get(0).getWidth();
                    if (columnX >= screen.width / 2) throw new AssertionError("Slot list is not on the left");
                    verifyColumn(slots);
                }
                if (!installedInitialLaser) {
                    if (!RefitScreenProbe.selectType(screen, AttachmentType.LASER)) return;
                    builtinScope = builtinScope();
                    ClientProbeMod.evidence("builtinScope", builtinScope);
                    RefitScreenProbe.installFirst(screen, AttachmentType.LASER);
                    installedInitialLaser = true;
                    return;
                }
                if (!RefitScreenProbe.acknowledged()) return;
                if (!RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                var candidates = RefitScreenProbe.visibleCandidates(screen);
                if (candidates.size() < 2) throw new AssertionError("Need two real scope IDs");
                ItemStack ordinary = RefitScreenProbe.candidate(candidates.get(0));
                ItemStack last = RefitScreenProbe.candidate(candidates.get(1));
                var muzzle = AttachmentRefitClientState.getAttachmentCandidates().stream()
                        .filter(stack -> {
                            var part = IAttachment.getIAttachmentOrNull(stack);
                            return part != null && part.getType(stack) == AttachmentType.MUZZLE;
                        })
                        .findFirst().orElseThrow(() -> new AssertionError("Need a real muzzle candidate"));
                finalId = RefitScreenProbe.attachmentId(last);
                List<ItemStack> synthetic = new ArrayList<>(SCOPE_COUNT + 1);
                for (int index = 0; index < SCOPE_COUNT; index++) {
                    var item = (index == SCOPE_COUNT - 1 ? last : ordinary).copy();
                    item.getOrCreateTag().putInt("ClientProbeDirectoryIndex", index);
                    synthetic.add(item);
                }
                ItemStack shortPageCandidate = muzzle.copy();
                shortPageCandidate.getOrCreateTag().putInt("ClientProbeDirectoryIndex", SCOPE_COUNT);
                synthetic.add(shortPageCandidate);
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
                        AttachmentEditSessionManager.syncVisibleAttachmentCandidates(player);
                    } catch (Exception failure) {
                        throw new IllegalStateException("Synthetic directory fixture failed", failure);
                    }
                }, mc.getSingleplayerServer());
                step = 1;
            }
            case 1 -> {
                if (AttachmentRefitClientState.getAttachmentCandidates().size() != catalog.size()) return;
                if (!catalog.equals(RefitScreenProbe.catalogIds())) throw new AssertionError("Full synthetic directory was not received");
                var full = AttachmentRefitClientState.getAttachmentCandidates();
                for (int index = 0; index < full.size(); index++) {
                    if (full.get(index).getOrCreateTag().getInt("ClientProbeDirectoryIndex") != index) {
                        throw new AssertionError("Directory fragment assembly changed item payload at " + index);
                    }
                }
                RefitScreenProbe.replaceExpectedCatalog(catalog);
                if (!RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                step = 2;
            }
            case 2 -> {
                verifyPickerBounds(screen, false);
                var visible = RefitScreenProbe.visibleCandidates(screen);
                if (visible.size() < 2 || visible.get(0).getSlotIndex() != 9
                        || screen.children().stream().anyMatch(child -> child instanceof RefitTurnPageButton
                                || child instanceof GunAttachmentSlot slot && slot.visible)) {
                    throw new AssertionError("Expected just a window into the long candidate list");
                }
                verifyColumn(visible);
                verifyVisibleWindow(screen, visible);
                var none = RefitScreenProbe.verifyNoAttachment(screen, true);
                if (none.getY() != value(screen, "top") || none.getY() + none.getHeight() > visible.get(0).getY()) {
                    throw new AssertionError("No attachment is not the first candidate row");
                }
                AbstractWidget firstReal = visible.get(0);
                var emptyHover = hoveredCandidate(screen, none.getX() + 2, none.getY() + 2);
                var realHover = hoveredCandidate(screen, firstReal.getX() + 2, firstReal.getY() + 2);
                if (emptyHover == null || !emptyHover.isEmpty() || realHover == null
                        || !ItemStack.matches(realHover, RefitScreenProbe.candidate(visible.get(0)))
                        || hoveredCandidate(screen, none.getX() - 1, none.getY() + 2) != null) {
                    throw new AssertionError("Hover did not distinguish removal, the actual candidate NBT, and leaving the list");
                }
                ClientProbeMod.evidence("slotAndCandidateColumnX", columnX);
                ClientProbeMod.evidence("initialVisibleWidgets", visible.size());
                if (!RefitScreenProbe.scrollCandidates(screen, -1.1)) throw new AssertionError("Wheel ignored");
                partialScroll = value(screen, "target");
                if (partialScroll <= 0 || value(screen, "scroll") != 0) throw new AssertionError("Wheel jumped instantly");
                step = 3;
            }
            case 3 -> {
                if (value(screen, "scroll") != partialScroll) return;
                screen.resize(Minecraft.getInstance(), screen.width, screen.height);
                if (value(screen, "scroll") != partialScroll) throw new AssertionError("Refreshing the list reset fractional scroll");
                if (RefitScreenProbe.noAttachment(screen) != null) throw new AssertionError("No attachment was pinned outside the viewport");
                var first = RefitScreenProbe.visibleCandidates(screen).get(0);
                AbstractWidget row = first;
                double x = row.getX() + 2, top = value(screen, "top");
                if (row.getY() >= top || first.isMouseOver(x, top - 1) || first.mouseClicked(x, top - 1, 0)
                        || hoveredCandidate(screen, x, top - 1) != null
                        || !ItemStack.matches(RefitScreenProbe.candidate(first), hoveredCandidate(screen, x, top + 1))
                        || !first.isMouseOver(x, top + 1) || !screen.mouseClicked(x, top + 1, 0)) {
                    throw new AssertionError("The clipped row did not honor its visible input bounds");
                }
                screen.mouseReleased(x, top + 1, 0);
                RefitScreenProbe.assertSlotPage(screen);
                ClientProbeMod.evidence("clippedHoverAndClick", true);
                RefitScreenProbe.expected.put(AttachmentType.SCOPE, RefitScreenProbe.attachmentId(RefitScreenProbe.candidate(first)));
                step = 4;
            }
            case 4 -> {
                if (!RefitScreenProbe.acknowledged() || !RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                RefitScreenProbe.verifyWorking();
                for (var row : RefitScreenProbe.visibleCandidates(screen)) {
                    if (RefitScreenProbe.rowSelected(row) != (row.getSlotIndex() == 9)) {
                        throw new AssertionError("Rows sharing a scope ID did not highlight the installed NBT variant");
                    }
                }
                if (value(screen, "scroll") != 0) throw new AssertionError("Reopened scope list did not start at the top");
                // Use the real scrollbar thumb to traverse the 1,057-entry directory.
                var scrollbar = screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast)
                        .filter(widget -> "screen.codpattern.refit.scrollbar".equals(ClientProbeMod.key(widget.getMessage())))
                        .findFirst().orElseThrow(() -> new AssertionError("Missing scrollbar"));
                double x = scrollbar.getX() + scrollbar.getWidth() / 2.0;
                double top = value(screen, "top"), bottom = value(screen, "bottom");
                if (!screen.mouseClicked(x, top + 3, 0)
                        || !screen.mouseDragged(x, bottom - 1, 0, 0, bottom - top - 4)) {
                    throw new AssertionError("The large directory did not accept a scrollbar drag");
                }
                screen.mouseReleased(x, bottom - 1, 0);
                if (!RefitScreenProbe.scrollCandidates(screen, -SCOPE_COUNT)) throw new AssertionError("End wheel ignored");
                step = 5;
            }
            case 5 -> {
                if (value(screen, "scroll") != value(screen, "target")) return;
                var visible = RefitScreenProbe.visibleCandidates(screen);
                var last = visible.get(visible.size() - 1);
                finalScroll = value(screen, "scroll");
                verifyVisibleWindow(screen, visible);
                if (finalScroll <= partialScroll || last.getSlotIndex() != 9 + SCOPE_COUNT - 1
                        || last.getY() + last.getHeight() != value(screen, "bottom")
                        || !finalId.equals(RefitScreenProbe.attachmentId(RefitScreenProbe.candidate(last)))) {
                    throw new AssertionError("The end of the scroll list did not expose the final stable candidate");
                }
                RefitScreenProbe.expected.put(AttachmentType.SCOPE, finalId);
                ClientProbeMod.screenshot("large-catalog-end");
                ClientProbeMod.click(screen, last);
                RefitScreenProbe.assertSlotPage(screen);
                ClientProbeMod.evidence("syntheticDirectoryCount", SCOPE_COUNT);
                ClientProbeMod.evidence("fullCandidateCount", catalog.size());
                ClientProbeMod.evidence("finalCandidateSlot", last.getSlotIndex());
                ClientProbeMod.evidence("finalScrollPixels", finalScroll);
                step = 6;
            }
            case 6 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                RefitScreenProbe.verifyWorking();
                RefitScreenProbe.assertSlotPage(screen);
                step = 7;
            }
            case 7 -> {
                if (!RefitScreenProbe.selectType(screen, AttachmentType.MUZZLE)) return;
                var visible = RefitScreenProbe.visibleCandidates(screen);
                if (value(screen, "scroll") != 0 || visible.size() != 1 || visible.get(0).getSlotIndex() != 9 + SCOPE_COUNT
                        || visible.get(0).getX() != columnX || visible.get(0).getWidth() != columnWidth) {
                    throw new AssertionError("Switching from the end of the list did not reveal the single muzzle");
                }
                RefitScreenProbe.verifyNoAttachment(screen, true);
                RefitScreenProbe.installFirst(screen, AttachmentType.MUZZLE);
                step = 8;
            }
            case 8 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                if (!RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                var visible = RefitScreenProbe.visibleCandidates(screen);
                if (value(screen, "scroll") != 0 || visible.isEmpty() || visible.get(0).getSlotIndex() != 9) {
                    throw new AssertionError("Returning to scopes did not show the top of the list");
                }
                screen.setFocused(visible.get(0));
                if (!screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0)
                        || !(screen.getFocused() instanceof InventoryAttachmentSlot end) || end.getSlotIndex() != 9 + SCOPE_COUNT - 1
                        || !screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0)
                        || screen.getFocused() != RefitScreenProbe.verifyNoAttachment(screen, false)) {
                    throw new AssertionError("Keyboard navigation did not focus the last and first candidate");
                }
                ClientProbeMod.evidence("categoryScrollPixels", List.of(finalScroll, 0, 0));
                ClientProbeMod.evidence("keyboardHomeEnd", true);
                RefitScreenProbe.pressNoAttachment(screen, AttachmentType.SCOPE, false);
                RefitScreenProbe.expected.put(AttachmentType.SCOPE, "");
                step = 9;
            }
            case 9 -> {
                if (!RefitScreenProbe.acknowledged() || !RefitScreenProbe.selectType(screen, AttachmentType.SCOPE)) return;
                RefitScreenProbe.verifyWorking();
                RefitScreenProbe.pressNoAttachment(screen, AttachmentType.SCOPE, true);
                if (!builtinScope.equals(builtinScope())) throw new AssertionError("Unloading changed the built-in scope");
                step = 10;
            }
            case 10 -> {
                if (!RefitScreenProbe.selectType(screen, AttachmentType.LASER)) return;
                if (!RefitScreenProbe.visibleCandidates(screen).isEmpty()) throw new AssertionError("Expected an empty laser catalog");
                RefitScreenProbe.pressNoAttachment(screen, AttachmentType.LASER, false);
                RefitScreenProbe.expected.put(AttachmentType.LASER, "");
                step = 11;
            }
            case 11 -> {
                if (!RefitScreenProbe.acknowledged() || !RefitScreenProbe.selectType(screen, AttachmentType.LASER)) return;
                RefitScreenProbe.verifyWorking();
                RefitScreenProbe.pressNoAttachment(screen, AttachmentType.LASER, true);
                ClientProbeMod.evidence("unloadedWithEmptyCatalog", true);
                if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("ESC was rejected");
                step = 12;
            }
            case 12 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                RefitScreenProbe.verifySaved(); step = 13;
            }
            case 13 -> ClientProbeMod.finish(true, "large_catalog_end", null);
            default -> throw new AssertionError("Unknown large-directory phase " + step);
        }
    }

    private static String builtinScope() {
        var gun = Minecraft.getInstance().player.getMainHandItem();
        return RefitScreenProbe.attachmentId(IGun.getIGunOrNull(gun).getBuiltinAttachment(gun, AttachmentType.SCOPE));
    }

    private static double value(Screen screen, String name) throws ReflectiveOperationException {
        var list = candidateList(screen);
        var value = list.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value.getDouble(list);
    }

    private static ItemStack hoveredCandidate(Screen screen, double x, double y) throws ReflectiveOperationException {
        var list = candidateList(screen);
        var hover = list.getClass().getDeclaredMethod("hoveredCandidate", double.class, double.class);
        hover.setAccessible(true);
        return (ItemStack) hover.invoke(list, x, y);
    }

    private static Object candidateList(Screen screen) throws ReflectiveOperationException {
        var field = screen.getClass().getDeclaredField("candidateList");
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void verifyColumn(List<? extends AbstractWidget> rows) {
        int bottom = Integer.MIN_VALUE;
        for (var row : rows) {
            if (row.getX() != columnX || row.getY() < bottom) {
                throw new AssertionError("Expected non-overlapping rows in the same left column");
            }
            bottom = row.getY() + row.getHeight();
        }
    }

    private static void verifyPickerBounds(Screen refit, boolean slots) {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        int original = mc.options.guiScale().get();
        int maximum = window.calculateScale(0, mc.isEnforceUnicode());
        var sizes = new ArrayList<List<Number>>();
        try {
            for (int scale = 1; scale <= maximum; scale++) {
                mc.options.guiScale().set(scale);
                mc.resizeDisplay();
                var picker = new WeaponScreen(refit, new BackpackConfig.Backpack("test"), 1, BackpackSlot.PRIMARY);
                picker.init(mc, refit.width, refit.height);
                var card = picker.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).filter(w -> w.getClass().getSimpleName().equals("CandidateButton"))
                        .findFirst().orElseThrow(() -> new AssertionError("No real picker card to compare"));
                double pixels = window.getWidth() / (double) window.getGuiScaledWidth();
                double left = picker.screenX(card.getX()) * pixels;
                double right = picker.screenX(card.getX() + card.getWidth()) * pixels;
                int x = (int) Math.floor(left / window.getGuiScale() + .00001);
                int end = (int) Math.ceil(right / window.getGuiScale() - .00001);
                var rows = refit.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(w -> w.visible && (slots ? w instanceof GunAttachmentSlot : w instanceof InventoryAttachmentSlot)).toList();
                if (rows.isEmpty() || rows.stream().anyMatch(w -> w.getX() != x || w.getX() + w.getWidth() != end)) {
                    throw new AssertionError("Refit cards differ from the weapon picker at GUI scale " + scale);
                }
                sizes.add(List.of(scale, Math.round(left), Math.round(right), rows.get(0).getHeight()));
            }
        } finally {
            mc.options.guiScale().set(original);
            mc.resizeDisplay();
        }
        ClientProbeMod.evidence(slots ? "slotCardBoundsByScale" : "candidateCardBoundsByScale", sizes);
    }

    private static void verifyVisibleWindow(Screen screen, List<? extends AbstractWidget> rows) throws ReflectiveOperationException {
        double top = value(screen, "top"), bottom = value(screen, "bottom");
        for (var row : rows) {
            if (row.getY() >= bottom || row.getY() + row.getHeight() <= top) {
                throw new AssertionError("An offscreen candidate was retained as a widget");
            }
        }
        if (rows.size() >= SCOPE_COUNT) throw new AssertionError("Entire catalog was materialized as widgets");
    }
}
