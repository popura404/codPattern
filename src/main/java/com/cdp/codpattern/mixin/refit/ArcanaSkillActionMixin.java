package com.cdp.codpattern.mixin.refit;

import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"group.taczexpands.dist.FkeGEJ2j", "group.taczexpands.server.skill.Skill"}, remap = false)
public abstract class ArcanaSkillActionMixin {
    @Inject(method = {"jaOjf2Sa", "triggerAction"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void codpattern$skipDirectSkill(@Coerce Object context, CallbackInfoReturnable<Boolean> cir) {
        if (ArcanaRefitCompat.suppressSkill(context)) cir.setReturnValue(false);
    }

    @Inject(method = {"wtAAjD2V", "triggerReverseAction"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void codpattern$skipReverseSkill(@Coerce Object context, CallbackInfo ci) {
        if (ArcanaRefitCompat.suppressSkill(context)) ci.cancel();
    }

    // Redirectors must have the same static modifier as their target method in Mixin 0.8.5.
    @Group(name = "codpattern$instanceSkillAction", min = 1)
    @Redirect(method = {"jaOjf2Sa", "wtAAjD2V"}, at = @At(value = "INVOKE", target = "Lgroup/taczexpands/dist/QEMFAKxS;qUyEZ9qy(Lgroup/taczexpands/dist/FkeGEJ2j;Lgroup/taczexpands/dist/kMbDq4yQ;)V"), require = 0)
    private void codpattern$shippedInstanceAction(@Coerce Object action, @Coerce Object skill, @Coerce Object context) {
        ArcanaRefitCompat.performScheduledSkillAction(action, skill, context);
    }

    @Group(name = "codpattern$instanceSkillAction", min = 1)
    @Redirect(method = {"triggerAction", "triggerReverseAction"}, at = @At(value = "INVOKE", target = "Lgroup/taczexpands/server/config/action/base/Action;perform(Lgroup/taczexpands/server/skill/Skill;Lgroup/taczexpands/server/context/Context;)V"), require = 0)
    private void codpattern$sourceInstanceAction(@Coerce Object action, @Coerce Object skill, @Coerce Object context) {
        ArcanaRefitCompat.performScheduledSkillAction(action, skill, context);
    }

    // Redirect only the action inside the static lambda, preserving its repeat count and rescheduling.
    @Group(name = "codpattern$scheduledSkillAction", min = 1)
    @Redirect(method = "UpBotgoC", at = @At(value = "INVOKE", target = "Lgroup/taczexpands/dist/QEMFAKxS;qUyEZ9qy(Lgroup/taczexpands/dist/FkeGEJ2j;Lgroup/taczexpands/dist/kMbDq4yQ;)V"), require = 0)
    private static void codpattern$shippedScheduledAction(@Coerce Object action, @Coerce Object skill, @Coerce Object context) {
        ArcanaRefitCompat.performScheduledSkillAction(action, skill, context);
    }

    @Group(name = "codpattern$scheduledSkillAction", min = 1)
    @Redirect(method = "*(Lgroup/taczexpands/server/skill/Skill;Lgroup/taczexpands/server/context/Context;Lkotlin/jvm/internal/Ref$IntRef;Lgroup/taczexpands/server/util/ScheduledTask;)Lkotlin/Unit;",
            at = @At(value = "INVOKE", target = "Lgroup/taczexpands/server/config/action/base/Action;perform(Lgroup/taczexpands/server/skill/Skill;Lgroup/taczexpands/server/context/Context;)V"), require = 0)
    private static void codpattern$sourceScheduledAction(@Coerce Object action, @Coerce Object skill, @Coerce Object context) {
        ArcanaRefitCompat.performScheduledSkillAction(action, skill, context);
    }
}
