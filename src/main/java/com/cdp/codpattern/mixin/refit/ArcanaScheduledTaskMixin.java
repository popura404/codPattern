package com.cdp.codpattern.mixin.refit;

import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {"group.taczexpands.dist.cvNhRCT1", "group.taczexpands.server.util.ScheduledTask"}, remap = false)
public abstract class ArcanaScheduledTaskMixin {
    // Keep ordinal 0's timing callback; Effect.CLEAR's side effect inside it has its own precise hook.
    @Redirect(method = {"ddDNMnwx", "tick"}, at = @At(value = "INVOKE",
            target = "Lkotlin/jvm/functions/Function1;invoke(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1), require = 1)
    private Object codpattern$skipDueConfigurationAction(@Coerce Object callback, Object task) {
        return ArcanaRefitCompat.runScheduledTask(callback, task);
    }
}
