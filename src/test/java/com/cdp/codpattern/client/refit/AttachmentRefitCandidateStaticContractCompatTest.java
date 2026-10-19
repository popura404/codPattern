package com.cdp.codpattern.client.refit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AttachmentRefitCandidateStaticContractCompatTest {
    private static final Path GUN_REFIT_SCREEN_MIXIN =
            Path.of("src/main/java/com/cdp/codpattern/mixin/tacz/GunRefitScreenMixin.java");
    private AttachmentRefitCandidateStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String screenMixin = Files.readString(GUN_REFIT_SCREEN_MIXIN);
        requireContains(screenMixin, "@Mixin(value = GunRefitScreen.class, remap = false, priority = 900)",
                "screen mixin must run after TaCZAddon and keep the backpack refit inventory as the final local value");
        requireContains(screenMixin, "@ModifyVariable(method = \"addInventoryAttachmentButtons\", at = @At(\"STORE\"), ordinal = 0)",
                "candidate button inventory local must be rewritten after store");
        requireContains(screenMixin, "@ModifyVariable(method = \"addAttachmentTypeButtons\", at = @At(\"STORE\"), ordinal = 0)",
                "attachment type button inventory local must be rewritten after store");
        requireContains(screenMixin, "BackpackRefitSessionContext.isBackpackRefitActive(player)",
                "screen mixin should only override the local inventory during backpack refit");
        requireContains(screenMixin, "AttachmentRefitClientState.resolveRefitScreenInventory(player)",
                "screen mixin must use the full synced candidate inventory");

        // Full candidate counts, backing-slot stability, packet round trips, and action
        // conversion run as GameTests. The operation queue has executable pure-Java tests.
        System.out.println("PASS attachment refit candidate static contract compat");
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
