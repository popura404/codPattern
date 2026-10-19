package com.cdp.codpattern.mixin.refit;

import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"group.taczexpands.dist.ZKUfbeiQ", "group.taczexpands.server.network.NetworkManager"}, remap = false)
public abstract class ArcanaRefitDrawPacketMixin {
    @Inject(method = {"KGYtegcA", "sendToPlayer"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void codpattern$deferPreviewDraw(Object message, ServerPlayer player, CallbackInfo ci) {
        if (ArcanaRefitCompat.suppressDrawPacket(message, player)) {
            ci.cancel();
        }
    }
}
