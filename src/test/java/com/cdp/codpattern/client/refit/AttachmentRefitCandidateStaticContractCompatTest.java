package com.cdp.codpattern.client.refit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AttachmentRefitCandidateStaticContractCompatTest {
    private static final Path GUN_REFIT_SCREEN_MIXIN =
            Path.of("src/main/java/com/cdp/codpattern/mixin/tacz/GunRefitScreenMixin.java");
    private static final Path REFIT_GUN_MESSAGE_MIXIN =
            Path.of("src/main/java/com/cdp/codpattern/mixin/tacz/ClientMessageRefitGunMixin.java");
    private static final Path UNLOAD_MESSAGE_MIXIN =
            Path.of("src/main/java/com/cdp/codpattern/mixin/tacz/ClientMessageUnloadAttachmentMixin.java");
    private static final Path REFIT_INVENTORY =
            Path.of("src/main/java/com/cdp/codpattern/core/refit/AttachmentRefitInventory.java");
    private static final Path SESSION_MANAGER =
            Path.of("src/main/java/com/cdp/codpattern/core/refit/AttachmentEditSessionManager.java");
    private static final Path CANDIDATE_PACKET =
            Path.of("src/main/java/com/cdp/codpattern/network/SyncAttachmentCandidatesPacket.java");
    private static final Path CLIENT_STATE =
            Path.of("src/main/java/com/cdp/codpattern/client/refit/AttachmentRefitClientState.java");

    private AttachmentRefitCandidateStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String screenMixin = Files.readString(GUN_REFIT_SCREEN_MIXIN);
        String refitGunMixin = Files.readString(REFIT_GUN_MESSAGE_MIXIN);
        String unloadMixin = Files.readString(UNLOAD_MESSAGE_MIXIN);
        String refitInventory = Files.readString(REFIT_INVENTORY);
        String sessionManager = Files.readString(SESSION_MANAGER);
        String candidatePacket = Files.readString(CANDIDATE_PACKET);
        String clientState = Files.readString(CLIENT_STATE);

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

        requireServerMixinContract(refitGunMixin, "refit gun message mixin");
        requireServerMixinContract(unloadMixin, "unload attachment message mixin");

        requireContains(refitInventory, "return HOTBAR_SIZE + attachmentCandidates.size() + 1;",
                "refit inventory size must grow with every attachment candidate");
        requireContains(refitInventory, "NonNullList.withSize(Math.max(VANILLA_MAIN_INVENTORY_SIZE, getContainerSize())",
                "refit inventory backing list must be large enough for candidates beyond vanilla slots");
        requireContains(sessionManager, "for (int slot = 0; slot < inventory.getContainerSize(); slot++)",
                "candidate sync snapshot must scan the virtual inventory size");
        requireContains(candidatePacket, "buffer.writeVarInt(packet.attachmentCandidates.size());",
                "candidate packet must encode the full candidate count");
        requireContains(clientState, "if (!attachmentCandidatesReady) {\n            return;\n        }",
                "backpack refit screen must wait for the candidate packet before opening");

        System.out.println("PASS attachment refit candidate static contract compat");
    }

    private static void requireServerMixinContract(String source, String name) {
        requireContains(source, "priority = 900",
                name + " must run after TaCZAddon and preserve the final backpack refit inventory");
        requireContains(source, "@ModifyVariable(method = \"lambda$handle$0\", at = @At(\"STORE\"), ordinal = 0)",
                name + " must rewrite the TaCZ local inventory after store");
        requireContains(source, "AttachmentEditSessionManager.getRefitInventory(player)",
                name + " must use the server-side full candidate inventory");
        requireContains(source, "return refitInventory == null ? inventory : refitInventory;",
                name + " must preserve non-backpack TaCZAddon inventory behavior");
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
