package com.cdp.codpattern.mixin.network;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Common-side bridge: a dedicated server never resolves the client refit state or TaCZ UI. */
@Mixin(value = SimpleChannel.class, remap = false)
public abstract class RefitMessageRoutingMixin {
    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true)
    private void codpattern$routeRefitOperation(Object message, CallbackInfo ci) {
        if (ClientPacketBridge.routeRefitMessage(this, message)) {
            ci.cancel();
        }
    }
}
