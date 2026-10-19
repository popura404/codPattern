package com.cdp.codpattern.mixin.refit;

import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"group.taczexpands.dist.Gcj8PhOz", "group.taczexpands.server.skill.SkillManager"}, remap = false)
public abstract class ArcanaSkillManagerMixin {
    @Inject(method = {"rTlp7UQU", "CJ6pttSh", "OIyJu4qq", "trigger", "triggerSpecificType", "triggerReverse"},
            at = @At("HEAD"), cancellable = true, require = 1)
    private void codpattern$skipConfigurationSkills(@Coerce Object type, @Coerce Object context,
                                                   int magicNum, CallbackInfo ci) {
        if (ArcanaRefitCompat.suppressSkill(context)) {
            ci.cancel();
        }
    }

    @Inject(method = {"ZnjAHN9m", "triggerActionDirectly"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void codpattern$skipQueuedConfigurationSkills(@Coerce Object context, @Coerce Object skill,
                                                         int magicNum, CallbackInfo ci) {
        if (ArcanaRefitCompat.suppressSkill(context)) {
            ci.cancel();
        }
    }
}
