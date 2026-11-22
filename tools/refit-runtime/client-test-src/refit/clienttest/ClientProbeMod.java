package refit.clienttest;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.gui.LoadingErrorScreen;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.eventbus.api.EventPriority;

/** Isolated, opt-in production client probe. Never packaged in the CDP mod. */
@Mod("refitclienttests")
public final class ClientProbeMod {
    private static final long STARTED = System.nanoTime();
    private static boolean opening;
    private static boolean complete;
    private static int readyTicks;
    private static int titleTicks;
    private static String lastScreen = "";
    private static final Map<String, Object> evidence = new LinkedHashMap<>();

    public ClientProbeMod() {
        if (!Boolean.getBoolean("codpattern.clientTestBootstrap")) {
            throw new IllegalStateException("Client test mod requires the explicit isolated test property");
        }
        MinecraftForge.EVENT_BUS.addListener(ClientProbeMod::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, RefitScreenProbe::loggedIn);
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || complete) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            String screenName = mc.screen == null ? "none" : mc.screen.getClass().getName();
            if (!screenName.equals(lastScreen)) {
                LogUtils.getLogger().info("CDP_CLIENT_PROBE screen={}", screenName);
                lastScreen = screenName;
            }
            long deadline = RefitScreenProbe.stage().equals("duration") ? 420_000L : 300_000L;
            if ((System.nanoTime() - STARTED) / 1_000_000L > deadline) {
                throw new IllegalStateException("Client probe exceeded " + deadline + " ms");
            }
            if (mc.getOverlay() != null) return;
            if (mc.screen instanceof LoadingErrorScreen screen) {
                var errorsField = LoadingErrorScreen.class.getDeclaredField("modLoadErrors");
                errorsField.setAccessible(true);
                var warningsField = LoadingErrorScreen.class.getDeclaredField("modLoadWarnings");
                warningsField.setAccessible(true);
                LogUtils.getLogger().error("CDP_CLIENT_PROBE loading errors={} warnings={}",
                        errorsField.get(screen), warningsField.get(screen));
                screenshot("loading-failure");
                throw new IllegalStateException("Forge displayed loading errors or warnings; inspect probe log");
            }
            if (mc.screen instanceof AccessibilityOnboardingScreen screen) {
                screen.onClose();
                return;
            }
            if (!opening && mc.screen instanceof TitleScreen) {
                if (++titleTicks < 40) return; // Let the title menu fade in before recording it.
                opening = true;
                LogUtils.getLogger().info("CDP_CLIENT_PROBE title_ready");
                screenshot("title");
                mc.createWorldOpenFlows().loadLevel(mc.screen, "refit-client-fixture");
                return;
            }
            if (opening && mc.screen instanceof ConfirmScreen screen
                    && key(screen.getTitle()).equals("selectWorld.backupQuestion.experimental")) {
                clickButton(screen, "gui.proceed");
                return;
            }
            if (opening && mc.screen instanceof BackupConfirmScreen screen) {
                clickButton(screen, "selectWorld.backupJoinSkipButton");
                return;
            }
            if (mc.player != null && mc.level != null && mc.getConnection() != null
                    && mc.getSingleplayerServer() != null && !(mc.screen instanceof ReceivingLevelScreen)) {
                if (++readyTicks == 60) {
                    screenshot("world");
                    if (System.getProperty("codpattern.clientTestStage", "world").equals("world")) {
                        finish(true, "world_ready", null);
                    }
                }
                if (readyTicks >= 60 && !complete) RefitScreenProbe.tick();
            }
        } catch (Throwable failure) {
            LogUtils.getLogger().error("CDP_CLIENT_PROBE failed", failure);
            finish(false, "failed", failure.toString());
        }
    }

    static String key(Component component) {
        return component.getContents() instanceof TranslatableContents text ? text.getKey() : "";
    }

    static void screenshot(String phase) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(Path.of(System.getProperty("codpattern.clientTestResult")).getParent().toFile(),
                "client-probe-" + phase + ".png", mc.getMainRenderTarget(),
                message -> LogUtils.getLogger().info("CDP_CLIENT_PROBE screenshot={}", message.getString()));
    }

    static void evidence(String key, Object value) { evidence.put(key, value); }

    static boolean clickButton(Screen screen, String translationKey) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.active && button.visible
                    && key(button.getMessage()).equals(translationKey)) {
                click(screen, button);
                return true;
            }
        }
        return false;
    }

    static void click(Screen screen, AbstractWidget button) {
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        if (!screen.mouseClicked(x, y, 0)) {
            throw new IllegalStateException("Screen rejected button click: " + button.getMessage().getString());
        }
        screen.mouseReleased(x, y, 0);
    }

    static void finish(boolean success, String phase, String error) {
        if (complete) return;
        complete = true;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("phase", phase);
        result.put("elapsedMillis", (System.nanoTime() - STARTED) / 1_000_000L);
        result.put("screen", lastScreen);
        result.put("stage", RefitScreenProbe.stage());
        result.put("slot", RefitScreenProbe.slot());
        result.put("evidence", evidence);
        result.put("error", error);
        try {
            Files.writeString(Path.of(System.getProperty("codpattern.clientTestResult")),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        } catch (Exception failure) {
            LogUtils.getLogger().error("Could not write client test result", failure);
        }
        LogUtils.getLogger().info("CDP_CLIENT_PROBE result={}", result);
        Minecraft.getInstance().stop();
    }
}
