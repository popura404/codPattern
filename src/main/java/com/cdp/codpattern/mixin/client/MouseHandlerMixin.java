package com.cdp.codpattern.mixin.client;

import com.cdp.codpattern.client.ClientTdmState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void codpattern$lockDeathCamTurn(CallbackInfo ci) {
        if (!ClientTdmState.isDeathCamViewLocked()) {
            return;
        }

        accumulatedDX = 0.0D;
        accumulatedDY = 0.0D;
        ClientTdmState.enforceDeathCamViewLock();
        ci.cancel();
    }
}
