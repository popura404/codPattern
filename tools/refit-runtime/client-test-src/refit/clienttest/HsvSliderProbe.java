package refit.clienttest;

import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitAction;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup.LaserColorSlider;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import java.awt.Color;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Drives actual HSV widgets through Screen events, including a drag held across server ACKs. */
final class HsvSliderProbe {
    private static final String LASER_ID = "tacz:laser_lopro";
    private static int step;
    private static int reportedStep = -1;
    private static int dragEvents;
    private static int heldAcrossAcks;
    private static int candidatePages;
    private static UUID playerId;
    private static Integer gunColor;
    private static Integer laserColor;
    private static Panel panel;
    private static LaserColorSlider pressed;
    private static double previousX;
    private static double pressedY;
    private static CompletableFuture<Void> serverTask;

    private HsvSliderProbe() { }

    static void tick(Screen screen) throws Exception {
        if (serverTask != null) {
            if (!serverTask.isDone()) return;
            serverTask.join();
            serverTask = null;
        }
        if (reportedStep != step) {
            LogUtils.getLogger().info("CDP_CLIENT_PROBE hsv_step={}", step);
            reportedStep = step;
        }
        switch (step) {
            case 0 -> {
                if (!active(screen) || !RefitScreenProbe.acknowledged() || !selectNone(screen)) return;
                assertFixture();
                playerId = Minecraft.getInstance().player.getUUID();
                panel = panel(screen);
                dragOnce(screen, panel.saturation(), 0.77);
                gunColor = panel.color();
                assertClientColors();
                assertQueuedColor(AttachmentType.NONE, gunColor);
                step++;
            }
            case 1 -> {
                assertClientColors();
                if (!RefitScreenProbe.acknowledged()) return;
                verifyWorkingColors();
                step++;
            }
            case 2 -> {
                panel = panel(screen);
                press(screen, panel.hue());
                dragTo(screen, 0.15);
                dragTo(screen, 0.47);
                dragTo(screen, 0.72);
                gunColor = panel.color();
                assertCoalesced(AttachmentType.NONE, gunColor);
                assertClientColors();
                // Keep the button down until the actual color requests have been acknowledged.
                step++;
            }
            case 3 -> {
                assertHeld(screen);
                assertClientColors();
                if (!RefitScreenProbe.acknowledged()) return;
                heldAcrossAcks++;
                dragTo(screen, 0.92);
                gunColor = panel.color();
                release(screen);
                assertClientColors();
                assertQueuedColor(AttachmentType.NONE, gunColor);
                step++;
            }
            case 4 -> {
                assertClientColors();
                if (!RefitScreenProbe.acknowledged()) return;
                verifyWorkingColors();
                ClientProbeMod.evidence("hsvGunColor", hex(gunColor));
                step++;
            }
            case 5 -> {
                if (!RefitScreenProbe.selectType(screen, AttachmentType.LASER)) return;
                panel = panel(screen);
                dragOnce(screen, panel.saturation(), 0.66);
                laserColor = panel.color();
                assertClientColors();
                assertQueuedColor(AttachmentType.LASER, laserColor);
                step++;
            }
            case 6 -> {
                assertClientColors();
                if (!RefitScreenProbe.acknowledged()) return;
                verifyWorkingColors();
                step++;
            }
            case 7 -> {
                panel = panel(screen);
                press(screen, panel.hue());
                dragTo(screen, 0.21);
                dragTo(screen, 0.44);
                dragTo(screen, 0.67);
                laserColor = panel.color();
                assertCoalesced(AttachmentType.LASER, laserColor);
                assertClientColors();
                step++;
            }
            case 8 -> {
                assertHeld(screen);
                assertClientColors();
                if (!RefitScreenProbe.acknowledged()) return;
                heldAcrossAcks++;
                dragTo(screen, 0.83);
                laserColor = panel.color();
                release(screen);
                assertClientColors();
                assertQueuedColor(AttachmentType.LASER, laserColor);
                step++;
            }
            case 9 -> {
                assertClientColors();
                if (!RefitScreenProbe.acknowledged()) return;
                verifyWorkingColors();
                ClientProbeMod.screenshot("hsv-before-reinstall");
                step++;
            }
            case 10 -> {
                InventoryAttachmentSlot candidate = findLoproOrTurnPage(screen);
                if (candidate == null) return;
                panel = panel(screen);
                int previousLaserColor = laserColor;
                ClientProbeMod.click(screen, candidate);
                var installation = actions();
                if (installation.size() != 1 || installation.get(0).kind() != RefitAction.Kind.INSTALL
                        || installation.get(0).candidateSlot() != candidate.getSlotIndex()
                        || !AttachmentType.LASER.name().equals(installation.get(0).attachmentType())) {
                    throw new AssertionError("Real Lopro candidate click did not start its installation");
                }
                // The native candidate click and the following real drag occur in this one tick.
                // Read the still-present old panel; never resurrect or invoke a removed widget.
                assertPanelPresent(screen);
                press(screen, panel.hue());
                dragTo(screen, 0.19);
                dragTo(screen, 0.38);
                laserColor = panel.color();
                release(screen);
                var queued = actions();
                if (queued.size() != 2 || queued.get(0).kind() != RefitAction.Kind.INSTALL
                        || queued.get(1).kind() != RefitAction.Kind.LASER) {
                    throw new AssertionError("Reinstall and old-panel drag were not ordered INSTALL then LASER: " + queued);
                }
                assertActionColor(queued.get(1), AttachmentType.LASER, laserColor);
                // The pending color belongs after INSTALL. The old attachment must retain its color until its ACK.
                assertColors(clientGun(), gunColor, previousLaserColor);
                ClientProbeMod.evidence("hsvReinstallQueue", queued.stream().map(action -> action.kind().name()).toList());
                ClientProbeMod.evidence("hsvReinstallCandidateSlot", candidate.getSlotIndex());
                step++;
            }
            case 11 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                verifyWorkingColors();
                ClientProbeMod.evidence("hsvLaserColor", hex(laserColor));
                ClientProbeMod.evidence("hsvDragEvents", dragEvents);
                ClientProbeMod.evidence("hsvDragsHeldAcrossAcks", heldAcrossAcks);
                if (heldAcrossAcks != 2) throw new AssertionError("Both real drags must survive their color ACKs");
                step++;
            }
            case 12 -> {
                ClientProbeMod.screenshot("hsv-before-save");
                close(screen);
                step++;
            }
            case 13 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySavedColors();
                step++;
            }
            case 14 -> { if (AdditionalRefitProbe.focusPrimary(screen)) step++; }
            case 15 -> {
                if (ClientProbeMod.clickButton(screen, "screen.codpattern.attachment_config.button")) step++;
            }
            case 16 -> {
                if (!active(screen)) return;
                verifyWorkingColors();
                step++;
            }
            case 17 -> {
                if (!selectNone(screen)) return;
                assertPanelReadback(panel(screen), gunColor);
                ClientProbeMod.screenshot("hsv-gun-reopened");
                step++;
            }
            case 18 -> {
                if (!RefitScreenProbe.selectType(screen, AttachmentType.LASER)) return;
                assertPanelReadback(panel(screen), laserColor);
                assertClientColors();
                ClientProbeMod.screenshot("hsv-laser-reopened");
                close(screen);
                step++;
            }
            case 19 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                verifySavedColors();
                step++;
            }
            case 20 -> ClientProbeMod.finish(true, "hsv_saved_reopened", null);
            default -> throw new AssertionError("Unknown HSV phase " + step);
        }
    }

    private static boolean active(Screen screen) {
        return screen instanceof CodGunRefitScreen && AttachmentRefitClientState.acceptsTaczOperations();
    }

    private static boolean selectNone(Screen screen) {
        if (RefitTransform.getOpeningProgress() != 1F || RefitTransform.getTransformProgress() != 1F) return false;
        AttachmentType current = RefitTransform.getCurrentTransformType();
        if (current == AttachmentType.NONE) return true;
        for (var child : screen.children()) {
            if (child instanceof GunAttachmentSlot slot && slot.getType() == current) {
                ClientProbeMod.click(screen, slot); // TaCZ toggles the selected type back to NONE.
                return false;
            }
        }
        throw new AssertionError("Cannot return to the gun's native HSV panel");
    }

    private static void assertFixture() {
        ItemStack gun = clientGun();
        IGun api = IGun.getIGunOrNull(gun);
        if (api == null || !"swpu:m4a1".equals(api.getGunId(gun).toString())
                || !LASER_ID.equals(RefitScreenProbe.attachmentId(api.getAttachment(gun, AttachmentType.LASER)))) {
            throw new AssertionError("HSV fixture must be swpu:m4a1 with actual tacz:laser_lopro installed");
        }
        var gunDisplay = TimelessAPI.getGunDisplay(gun).orElseThrow();
        var laserIndex = TimelessAPI.getClientAttachmentIndex(new ResourceLocation(LASER_ID)).orElseThrow();
        if (gunDisplay.getLaserConfig() == null || !gunDisplay.getLaserConfig().canEdit()
                || laserIndex.getLaserConfig() == null || !laserIndex.getLaserConfig().canEdit()) {
            throw new AssertionError("The loaded gun and LASER displays must both permit actual HSV editing");
        }
        RefitScreenProbe.verifyGun(gun, RefitScreenProbe.expected);
    }

    private static Panel panel(Screen screen) {
        List<LaserColorSlider> sliders = screen.children().stream().filter(LaserColorSlider.class::isInstance)
                .map(LaserColorSlider.class::cast).sorted(Comparator.comparingInt(LaserColorSlider::getY)).toList();
        if (sliders.size() != 2) throw new AssertionError("Expected the two actual HSV sliders, found " + sliders.size());
        if (!sliders.get(0).active || !sliders.get(0).visible || !sliders.get(1).active || !sliders.get(1).visible) {
            throw new AssertionError("HSV sliders are not interactable");
        }
        return new Panel(sliders.get(0), sliders.get(1));
    }

    private static void dragOnce(Screen screen, LaserColorSlider slider, double value) {
        press(screen, slider);
        dragTo(screen, value);
        release(screen);
    }

    private static void press(Screen screen, LaserColorSlider slider) {
        if (pressed != null) throw new AssertionError("Previous real mouse press was not released");
        pressed = slider;
        previousX = mouseX(slider, slider.getValue());
        pressedY = slider.getY() + slider.getHeight() / 2.0;
        if (!screen.mouseClicked(previousX, pressedY, 0)) throw new AssertionError("Screen rejected HSV mouse press");
        assertHeld(screen);
    }

    private static void dragTo(Screen screen, double value) {
        assertHeld(screen);
        double x = mouseX(pressed, value);
        if (!screen.mouseDragged(x, pressedY, 0, x - previousX, 0)) {
            throw new AssertionError("Screen rejected actual HSV mouse drag");
        }
        previousX = x;
        dragEvents++;
        double snapped = Math.round(value * 100.0) / 100.0;
        if (Math.abs(pressed.getValue() - snapped) > 0.000001) {
            throw new AssertionError("Real HSV drag did not reach the requested quantized value");
        }
    }

    private static void release(Screen screen) {
        assertHeld(screen);
        screen.mouseReleased(previousX, pressedY, 0);
        if (screen.isDragging()) throw new AssertionError("Screen did not release the HSV drag");
        pressed = null;
    }

    private static double mouseX(LaserColorSlider slider, double value) {
        return slider.getX() + 4.0 + value * (slider.getWidth() - 8.0);
    }

    private static void assertPanelPresent(Screen screen) {
        if (!screen.children().contains(panel.hue()) || !screen.children().contains(panel.saturation())) {
            throw new AssertionError("The actual HSV widgets were rebuilt during the test's active interaction");
        }
    }

    private static void assertHeld(Screen screen) {
        assertPanelPresent(screen);
        if (pressed == null || screen.getFocused() != pressed || !screen.isDragging()) {
            throw new AssertionError("HSV drag lost its real Screen focus/pressed state");
        }
    }

    private static List<RefitAction> actions() throws Exception {
        return RefitScreenProbe.queue().snapshot().stream().map(RefitAction.class::cast).toList();
    }

    private static void assertCoalesced(AttachmentType type, int expected) throws Exception {
        List<RefitAction> queued = actions();
        if (queued.size() != 2 || queued.stream().anyMatch(action -> action.kind() != RefitAction.Kind.LASER)) {
            throw new AssertionError("Three same-tick real drags must retain the in-flight color and latest pending color: " + queued);
        }
        assertActionColor(queued.get(queued.size() - 1), type, expected);
    }

    private static void assertQueuedColor(AttachmentType type, int expected) throws Exception {
        List<RefitAction> queued = actions();
        if (queued.isEmpty()) throw new AssertionError("Real HSV drag did not enqueue a color request");
        assertActionColor(queued.get(queued.size() - 1), type, expected);
    }

    private static void assertActionColor(RefitAction action, AttachmentType type, int expected) {
        boolean matches = type == AttachmentType.NONE
                ? action.applyGunColor() && action.gunColor() == expected && action.laserColors().isEmpty()
                : !action.applyGunColor() && action.laserColors().size() == 1
                        && Integer.valueOf(expected).equals(action.laserColors().get(type.name()));
        if (action.kind() != RefitAction.Kind.LASER || !matches) {
            throw new AssertionError("Queued color does not match the actual HSV widget result: " + action);
        }
    }

    private static InventoryAttachmentSlot findLoproOrTurnPage(Screen screen) throws Exception {
        for (var button : RefitScreenProbe.visibleCandidates(screen)) {
            if (LASER_ID.equals(RefitScreenProbe.attachmentId(RefitScreenProbe.candidate(button)))) return button;
        }
        var direction = RefitTurnPageButton.class.getDeclaredField("isUpPage");
        direction.setAccessible(true);
        for (var child : screen.children()) {
            if (child instanceof RefitTurnPageButton button && button.active && button.visible && !direction.getBoolean(button)) {
                if (++candidatePages > AttachmentRefitClientState.getAttachmentCandidates().size()) {
                    throw new AssertionError("LASER paging did not reach the real Lopro candidate");
                }
                ClientProbeMod.click(screen, button);
                return null;
            }
        }
        throw new AssertionError("The real LASER candidate controls do not contain " + LASER_ID);
    }

    private static ItemStack clientGun() {
        return AttachmentRefitClientState.resolveRefitScreenInventory(Minecraft.getInstance().player).getSelected();
    }

    private static void assertClientColors() { assertColors(clientGun(), gunColor, laserColor); }

    private static void assertColors(ItemStack gun, Integer expectedGun, Integer expectedLaser) {
        IGun api = IGun.getIGunOrNull(gun);
        if (api == null) throw new AssertionError("No working gun for HSV readback");
        if (expectedGun != null && (!api.hasCustomLaserColor(gun) || api.getLaserColor(gun) != expectedGun)) {
            throw new AssertionError("Gun color expected=" + hex(expectedGun) + " actual=" + hex(api.getLaserColor(gun)));
        }
        ItemStack attachment = api.getAttachment(gun, AttachmentType.LASER);
        IAttachment attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
        if (attachmentApi == null || !LASER_ID.equals(RefitScreenProbe.attachmentId(attachment))) {
            throw new AssertionError("Color operation changed the installed real LASER identity");
        }
        if (expectedLaser != null && (!attachmentApi.hasCustomLaserColor(attachment)
                || attachmentApi.getLaserColor(attachment) != expectedLaser)) {
            throw new AssertionError("Attachment color expected=" + hex(expectedLaser)
                    + " actual=" + hex(attachmentApi.getLaserColor(attachment)));
        }
    }

    private static void verifyWorkingColors() {
        assertClientColors();
        RefitScreenProbe.verifyWorking();
        UUID sessionId = AttachmentRefitClientState.getSessionId();
        Integer expectedGun = gunColor;
        Integer expectedLaser = laserColor;
        serverTask = CompletableFuture.runAsync(() -> {
            var session = AttachmentEditSessionManager.getSession(playerId);
            if (session == null || !sessionId.equals(session.getSessionId())) throw new AssertionError("Lost HSV server session");
            assertColors(session.getRefitInventory().getSelected(), expectedGun, expectedLaser);
        }, Minecraft.getInstance().getSingleplayerServer());
    }

    private static void verifySavedColors() {
        RefitScreenProbe.verifySaved();
        var server = Minecraft.getInstance().getSingleplayerServer();
        Integer expectedGun = gunColor;
        Integer expectedLaser = laserColor;
        String slot = RefitScreenProbe.slot();
        serverTask = CompletableFuture.runAsync(() -> {
            try {
                var config = new Gson().fromJson(Files.readString(ConfigPath.SERVERBACKPACK.getPath(server)), BackpackConfig.class);
                var item = config.getPlayerData().get(playerId.toString()).getBackpacks_MAP().get(1).getItem_MAP().get(slot);
                if (item.getAttachmentPreset() == null || item.getAttachmentPreset().isBlank()) {
                    throw new AssertionError("HSV test saved no attachment preset to disk");
                }
                assertColors(BackpackItemStackFactory.create(item), expectedGun, expectedLaser);
            } catch (Exception failure) {
                throw new IllegalStateException("Saved HSV color readback failed", failure);
            }
        }, server);
    }

    private static void assertPanelReadback(Panel actual, int color) {
        float[] hsv = Color.RGBtoHSB((color >>> 16) & 255, (color >>> 8) & 255, color & 255, null);
        // ForgeSlider snaps reconstructed HSV to hundredths; persisted RGB remains exact.
        if (Math.abs(actual.hue().getValue() - hsv[0]) > 0.011
                || Math.abs(actual.saturation().getValue() - hsv[1]) > 0.011) {
            throw new AssertionError("Reopened real HSV controls do not display the saved color " + hex(color));
        }
    }

    private static void close(Screen screen) {
        if (pressed != null) throw new AssertionError("Cannot close with a test-owned mouse press outstanding");
        if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("HSV save ESC was rejected");
    }

    private static String hex(int color) { return String.format("0x%08X", color); }

    private record Panel(LaserColorSlider hue, LaserColorSlider saturation) {
        int color() { return Color.HSBtoRGB((float) hue.getValue(), (float) saturation.getValue(), 1F); }
    }
}
