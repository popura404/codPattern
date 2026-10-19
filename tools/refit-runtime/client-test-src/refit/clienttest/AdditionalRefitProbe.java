package refit.clienttest;

import com.cdp.codpattern.client.gui.screen.WeaponMenuScreen;
import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** Independent scenarios sharing only the already verified normal menu entry. */
final class AdditionalRefitProbe {
    private static int step;
    private static long editingStarted;
    private static int editingTicks;

    static void tick(Screen screen) throws Exception {
        String stage = RefitScreenProbe.stage();
        boolean module = stage.equals("module");
        AttachmentType type = module ? AttachmentType.valueOf("MODULE") : AttachmentType.SCOPE;
        switch (step) {
            case 0 -> {
                if (!RefitScreenProbe.selectType(screen, type)) return;
                if (stage.equals("duration")) {
                    editingStarted = System.nanoTime();
                    LogUtils.getLogger().info("CDP_CLIENT_PROBE editing_duration_started session={}",
                            AttachmentRefitClientState.getSessionId());
                    step = 1;
                } else step = 2;
            }
            case 1 -> {
                if (AttachmentRefitClientState.getSessionId() == null) throw new AssertionError("Editing session expired");
                long elapsedMillis = (System.nanoTime() - editingStarted) / 1_000_000L;
                editingTicks++;
                if (elapsedMillis >= 120_000L && editingTicks > 2400) {
                    ClientProbeMod.evidence("editingElapsedMillis", elapsedMillis);
                    ClientProbeMod.evidence("editingClientTicks", editingTicks);
                    LogUtils.getLogger().info("CDP_CLIENT_PROBE editing_duration_passed elapsedMillis={} elapsedTicks={}",
                            elapsedMillis, editingTicks);
                    step = 2;
                }
            }
            case 2 -> {
                RefitScreenProbe.installFirst(screen, type);
                if (stage.equals("immediate-close")) {
                    if (RefitScreenProbe.queue().inFlight() == null) throw new AssertionError("Install was not in flight");
                    if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("Immediate ESC was rejected");
                    if (RefitScreenProbe.queue().snapshot().size() != 2) throw new AssertionError("Save was not queued after install");
                    step = 7;
                } else step = 3;
            }
            case 3 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                RefitScreenProbe.verifyWorking();
                step = module ? 4 : 6;
            }
            case 4 -> {
                var unload = screen.children().stream().filter(RefitUnloadButton.class::isInstance)
                        .map(RefitUnloadButton.class::cast).findFirst().orElseThrow();
                RefitScreenProbe.expected.put(type, "");
                ClientProbeMod.click(screen, unload);
                step = 5;
            }
            case 5 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                RefitScreenProbe.verifyWorking();
                step = 20;
            }
            case 20 -> { RefitScreenProbe.installFirst(screen, type); step = 21; }
            case 21 -> {
                if (!RefitScreenProbe.acknowledged()) return;
                RefitScreenProbe.verifyWorking(); step = 6;
            }
            case 6 -> {
                ClientProbeMod.screenshot(stage + "-before-save");
                if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("ESC was rejected");
                step = 7;
            }
            case 7 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                RefitScreenProbe.verifySaved(); step = 8;
            }
            case 8 -> { if (focusPrimary(screen)) step = 9; }
            case 9 -> {
                if (ClientProbeMod.clickButton(screen, "screen.codpattern.attachment_config.button")) step = 10;
            }
            case 10 -> {
                if (!(screen instanceof CodGunRefitScreen) || !AttachmentRefitClientState.acceptsTaczOperations()) return;
                RefitScreenProbe.verifyWorking(); step = 11;
            }
            case 11 -> {
                if (!RefitScreenProbe.selectType(screen, type)) return;
                ClientProbeMod.screenshot(stage + "-reopened");
                if (!screen.keyPressed(256, 0, 0)) throw new AssertionError("Final ESC was rejected");
                step = 12;
            }
            case 12 -> {
                if (AttachmentRefitClientState.getSessionId() != null) return;
                RefitScreenProbe.verifySaved(); step = 13;
            }
            case 13 -> ClientProbeMod.finish(true, switch (stage) {
                case "immediate-close" -> "immediate_close_saved";
                case "duration" -> "duration_saved";
                case "module" -> "module_saved_reopened";
                default -> throw new IllegalStateException("Unknown additional scenario " + stage);
            }, null);
            default -> throw new AssertionError("Unknown scenario step " + step);
        }
    }

    static boolean focusPrimary(Screen screen) throws Exception {
        if (!(screen instanceof WeaponMenuScreen)) return false;
        var primary = WeaponMenuScreen.class.getDeclaredField(RefitScreenProbe.slot() + "Button");
        primary.setAccessible(true);
        screen.setFocused((GuiEventListener) primary.get(screen));
        return true;
    }
}
