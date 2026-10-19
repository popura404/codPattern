package com.cdp.codpattern.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class CodPatternMixinPlugin implements IMixinConfigPlugin {
    private static final String LIBERATE_ATTACHMENT_MIXIN =
            "com.cdp.codpattern.mixin.taczaddon.LiberateAttachmentMixin";
    private static final String LIBERATE_ATTACHMENT_TARGET =
            "com.mafuyu404.taczaddon.common.LiberateAttachment";
    private static final Set<String> ARCANA_MIXINS = Set.of(
            "com.cdp.codpattern.mixin.refit.ArcanaSkillManagerMixin",
            "com.cdp.codpattern.mixin.refit.ArcanaSkillActionMixin",
            "com.cdp.codpattern.mixin.refit.ArcanaScheduledTaskMixin",
            "com.cdp.codpattern.mixin.refit.ArcanaEffectTimerMixin",
            "com.cdp.codpattern.mixin.refit.ArcanaRefitDrawPacketMixin");
    private final Supplier<IClassBytecodeProvider> bytecodeProvider;

    public CodPatternMixinPlugin() {
        this(() -> MixinService.getService().getBytecodeProvider());
    }

    CodPatternMixinPlugin(Supplier<IClassBytecodeProvider> bytecodeProvider) {
        this.bytecodeProvider = bytecodeProvider;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (LIBERATE_ATTACHMENT_MIXIN.equals(mixinClassName)) {
            return classExists(LIBERATE_ATTACHMENT_TARGET);
        }
        if (ARCANA_MIXINS.contains(mixinClassName)) {
            return classExists(targetClassName);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private boolean classExists(String className) {
        try {
            // Loading a target class here can define it before its mixins are applied.
            return bytecodeProvider.get().getClassNode(className) != null;
        } catch (ClassNotFoundException | IOException ignored) {
            return false;
        }
    }
}
