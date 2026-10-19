package com.cdp.codpattern.mixin.refit;

import com.cdp.codpattern.core.refit.RefitPreviewContext;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkHandler.class, remap = false)
public abstract class RefitRefreshPacketMixin {
    @Inject(method = "sendToClientPlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private static <T> void codpattern$deferPreviewRefresh(T message, Player player, CallbackInfo ci) {
        if (message instanceof ServerMessageRefreshRefitScreen && RefitPreviewContext.isActiveFor(player)) {
            ci.cancel();
        }
    }
}
