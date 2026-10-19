package com.cdp.codpattern.mixin.tacz;

import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Configuration operations use the session-bound CDP protocol, including with Arcana installed. */
@Mixin(value = ClientMessageUnloadAttachment.class, remap = false, priority = 2000)
public abstract class ClientMessageUnloadAttachmentMixin {
    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true)
    private static void codpattern$rejectLegacySessionOperation(NetworkEvent.Context context,
            ClientMessageUnloadAttachment message, CallbackInfo ci) {
        var player = context.getSender();
        if (player != null && AttachmentEditSessionManager.hasSession(player.getUUID())) {
            ci.cancel();
        }
    }
}
