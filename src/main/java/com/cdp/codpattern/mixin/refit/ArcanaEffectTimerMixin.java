package com.cdp.codpattern.mixin.refit;

import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The shipped Effect.CLEAR timing callback has a gameplay action before its time--. */
@Pseudo
@Mixin(targets = "group.taczexpands.dist.jZItwoCj", remap = false)
public abstract class ArcanaEffectTimerMixin {
    @Redirect(method = "ZJsYraWz", at = @At(value = "INVOKE",
            target = "Lkotlin/jvm/functions/Function0;invoke()Ljava/lang/Object;"), require = 1)
    private static Object codpattern$skipTimedEffect(@Coerce Object callback,
            @Coerce Object capturedCallback, @Coerce Object task) {
        return ArcanaRefitCompat.runScheduledEffect(callback, task);
    }
}
