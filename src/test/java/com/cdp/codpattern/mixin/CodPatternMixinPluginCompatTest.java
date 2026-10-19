package com.cdp.codpattern.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;

import java.io.IOException;

public final class CodPatternMixinPluginCompatTest {
    private static final String TARGET = "com.mafuyu404.taczaddon.common.LiberateAttachment";
    private static final String MIXIN = "com.cdp.codpattern.mixin.taczaddon.LiberateAttachmentMixin";

    private CodPatternMixinPluginCompatTest() {
    }

    public static void main(String[] args) {
        verifyAvailableAddonIsReadAsBytecode();
        verifyAbsentAddonIsOptional();
        verifyUnreadableAddonIsOptional();
        verifyUnrelatedMixinsDoNotProbeAddon();
        System.out.println("PASS optional TaCZAddon mixin discovery");
    }

    private static void verifyAvailableAddonIsReadAsBytecode() {
        int[] reads = {0};
        CodPatternMixinPlugin plugin = new CodPatternMixinPlugin(() -> new TestBytecodeProvider() {
            @Override
            public ClassNode getClassNode(String name) {
                require(TARGET.equals(name), "wrong optional target");
                reads[0]++;
                ClassNode node = new ClassNode();
                node.name = name.replace('.', '/');
                return node;
            }
        });
        require(plugin.shouldApplyMixin(TARGET, MIXIN), "available Addon must receive the compatibility mixin");
        require(reads[0] == 1, "the optional class must be checked through the bytecode provider");
    }

    private static void verifyAbsentAddonIsOptional() {
        CodPatternMixinPlugin plugin = new CodPatternMixinPlugin(() -> new TestBytecodeProvider() {
            @Override
            public ClassNode getClassNode(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        });
        require(!plugin.shouldApplyMixin(TARGET, MIXIN), "missing Addon must not be required");
    }

    private static void verifyUnreadableAddonIsOptional() {
        CodPatternMixinPlugin plugin = new CodPatternMixinPlugin(() -> new TestBytecodeProvider() {
            @Override
            public ClassNode getClassNode(String name) throws IOException {
                throw new IOException("unavailable bytecode");
            }
        });
        require(!plugin.shouldApplyMixin(TARGET, MIXIN), "unavailable optional bytecode must not be loaded as a class");
    }

    private static void verifyUnrelatedMixinsDoNotProbeAddon() {
        CodPatternMixinPlugin plugin = new CodPatternMixinPlugin(() -> {
            throw new AssertionError("unrelated mixins must not initialize Addon discovery");
        });
        require(plugin.shouldApplyMixin("some.Target", "some.OtherMixin"), "unrelated mixin was disabled");
    }

    private abstract static class TestBytecodeProvider implements IClassBytecodeProvider {
        @Override
        public ClassNode getClassNode(String name, boolean runTransformers) {
            throw new AssertionError("use the provider's standard lookup; ModLauncher does not support raw bytecode");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
