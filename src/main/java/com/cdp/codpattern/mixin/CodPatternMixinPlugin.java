package com.cdp.codpattern.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class CodPatternMixinPlugin implements IMixinConfigPlugin {
    private static final String LIBERATE_ATTACHMENT_MIXIN =
            "com.cdp.codpattern.mixin.taczaddon.LiberateAttachmentMixin";
    private static final String LIBERATE_ATTACHMENT_TARGET =
            "com.mafuyu404.taczaddon.common.LiberateAttachment";

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

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, CodPatternMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
