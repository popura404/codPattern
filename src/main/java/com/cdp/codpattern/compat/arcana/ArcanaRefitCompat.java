package com.cdp.codpattern.compat.arcana;

import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitPreviewContext;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/** Optional entry points verified against the shipped Arcana 1.1.4 implementation. */
public final class ArcanaRefitCompat {
    public static final String MOD_ID = "taczexpands";
    private static volatile Binding binding;

    // ClassValue also handles subclasses such as AttachmentChangeContext without loading optional classes eagerly.
    private static final ClassValue<Method> CONTEXT_PLAYER = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            return findMethod(type, new String[]{"pcB2hI0D", "getSelf"});
        }
    };
    private static final ClassValue<Method> TASK_PLAYER = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            return findMethod(type, new String[]{"rJ3Qn19V", "getPlayer"});
        }
    };

    private ArcanaRefitCompat() {
    }

    public static final class CompatibilityException extends IllegalStateException {
        private CompatibilityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static boolean isLoaded() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(MOD_ID);
    }

    public static boolean install(ServerPlayer player, ItemStack gun, ItemStack candidate, int candidateSlot,
                                  AttachmentType type, Inventory inventory) {
        Binding api = binding();
        Objects.requireNonNull(inventory, "CDP refit inventory");
        try (RefitPreviewContext.Scope ignored = RefitPreviewContext.open(player, gun)) {
            return (boolean) invoke(api.install, api.manager, type, candidate.copy(),
                    Integer.valueOf(candidateSlot), gun, player, false, inventory);
        }
    }

    public static boolean unload(ServerPlayer player, ItemStack gun, AttachmentType type, Inventory inventory) {
        Binding api = binding();
        Objects.requireNonNull(inventory, "CDP refit inventory");
        try (RefitPreviewContext.Scope ignored = RefitPreviewContext.open(player, gun)) {
            return (boolean) invoke(api.unload, api.manager, type, gun, player, inventory);
        }
    }

    /** Blocks gameplay skills for this player's configuration session, while property events remain enabled. */
    public static boolean suppressSkill(Object context) {
        if (context == null) {
            return false;
        }
        Object actor = invoke(CONTEXT_PLAYER.get(context.getClass()), context);
        return actor instanceof ServerPlayer player && suppressPlayer(player);
    }

    private static boolean suppressPlayer(ServerPlayer player) {
        return RefitPreviewContext.isActiveFor(player) || AttachmentEditSessionManager.hasSession(player.getUUID());
    }

    /** Leaves the repeating task's counter/rescheduling code intact, omitting only its gameplay action. */
    public static void performScheduledSkillAction(Object action, Object skill, Object context) {
        if (!suppressSkill(context)) invoke(SkillActionApi.PERFORM, action, skill, context);
    }

    /** Invoked at ScheduledTask's due-action callback; its countdown and removal logic still run. */
    public static Object runScheduledTask(Object callback, Object task) {
        Object actor = invoke(TASK_PLAYER.get(task.getClass()), task);
        if (actor instanceof ServerPlayer player && suppressPlayer(player)) return null;
        return invoke(SkillActionApi.FUNCTION_INVOKE, callback, task);
    }

    public static Object runScheduledEffect(Object callback, Object task) {
        Object actor = invoke(TASK_PLAYER.get(task.getClass()), task);
        if (actor instanceof ServerPlayer player && suppressPlayer(player)) return null;
        return invoke(SkillActionApi.FUNCTION0_INVOKE, callback);
    }

    /** Creates only Arcana's normal underbarrel state when the loaded real gun actually supports one. */
    public static CompoundTag underbarrelRootForPreset(ItemStack gun) {
        Object underbarrel = invoke(NbtApi.UNDERBARREL, NbtApi.INSTANCE, gun);
        if (underbarrel == null) return null;
        return (CompoundTag) invoke(NbtApi.CREATE_ROOT, NbtApi.INSTANCE, gun);
    }

    public static Object captureUnderbarrel(ItemStack gun) {
        return invoke(NbtApi.UNDERBARREL, NbtApi.INSTANCE, gun);
    }

    /** Matches GunManager's GRIP transition reset without returning ammo or replacing unrelated Extras. */
    public static void resetUnderbarrelAfterPreset(ItemStack gun, Object before) {
        if (before == null) return;
        Object after = captureUnderbarrel(gun);
        if (!(boolean) invoke(NbtApi.SAME_UNDERBARREL, NbtApi.GUN_MANAGER, before, after)) {
            invoke(NbtApi.SET_USING, NbtApi.INSTANCE, gun, false);
            invoke(NbtApi.DELETE_ROOT, NbtApi.INSTANCE, gun);
        }
    }

    private static Class<?> optionalClass(String shipped, String source) {
        try {
            try {
                return Class.forName(shipped, false, ArcanaRefitCompat.class.getClassLoader());
            } catch (ClassNotFoundException absent) {
                return Class.forName(source, false, ArcanaRefitCompat.class.getClassLoader());
            }
        } catch (ClassNotFoundException failure) {
            throw new CompatibilityException("Unsupported Arcana 1.1.4 API", failure);
        }
    }

    private static Object singleton(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getField(name).get(null);
            } catch (ReflectiveOperationException ignored) { }
        }
        throw new CompatibilityException("Unsupported Arcana singleton: " + type.getName(), null);
    }

    private static final class NbtApi {
        private static final Class<?> TYPE = optionalClass("group.taczexpands.dist.cSFSPogO", "group.taczexpands.common.nbt.GunExtras");
        private static final Object INSTANCE = singleton(TYPE, "lYcekq0C", "INSTANCE");
        private static final Method UNDERBARREL = findMethod(TYPE, new String[]{"SMuDyDJd", "getUnderBarrel"}, ItemStack.class);
        private static final Method CREATE_ROOT = findMethod(TYPE, new String[]{"BVInoqtN", "getOrCreateUnderBarrelRootTag"}, ItemStack.class);
        private static final Method SET_USING = findMethod(TYPE, new String[]{"sTzrnRrP", "setUsingUnderBarrel"}, ItemStack.class, boolean.class);
        private static final Method DELETE_ROOT = findMethod(TYPE, new String[]{"AxW94rhQ", "deleteUnderBarrelRootTag"}, ItemStack.class);
        private static final Class<?> MANAGER_TYPE = optionalClass("group.taczexpands.dist.jlkX1CVq", "group.taczexpands.server.util.GunManager");
        private static final Object GUN_MANAGER = singleton(MANAGER_TYPE, "RtLWCp8E", "INSTANCE");
        private static final Method SAME_UNDERBARREL = sameUnderbarrelMethod();

        private static Method sameUnderbarrelMethod() {
            Class<?> underbarrel = UNDERBARREL.getReturnType();
            for (String name : new String[]{"PQ3jy0GO", "isSameUnderBarrel"}) {
                try {
                    Method method = MANAGER_TYPE.getDeclaredMethod(name, underbarrel, underbarrel);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) { }
            }
            throw new CompatibilityException("Unsupported Arcana underbarrel comparison API", null);
        }
    }

    private static final class SkillActionApi {
        private static final Class<?> SKILL = optionalClass("group.taczexpands.dist.FkeGEJ2j", "group.taczexpands.server.skill.Skill");
        private static final Class<?> CONTEXT = optionalClass("group.taczexpands.dist.kMbDq4yQ", "group.taczexpands.server.context.Context");
        private static final Method PERFORM = findMethod(optionalClass("group.taczexpands.dist.QEMFAKxS",
                "group.taczexpands.server.config.action.base.Action"), new String[]{"qUyEZ9qy", "perform"}, SKILL, CONTEXT);
        private static final Method FUNCTION_INVOKE = findMethod(optionalClass("kotlin.jvm.functions.Function1",
                "kotlin.jvm.functions.Function1"), new String[]{"invoke"}, Object.class);
        private static final Method FUNCTION0_INVOKE = findMethod(optionalClass("kotlin.jvm.functions.Function0",
                "kotlin.jvm.functions.Function0"), new String[]{"invoke"});
    }

    /** Only the draw packets emitted by GunManager are superseded by the CDP operation result. */
    public static boolean suppressDrawPacket(Object packet, ServerPlayer player) {
        if (!RefitPreviewContext.isActiveFor(player) || packet == null) {
            return false;
        }
        Binding api = binding();
        return api.actionPacket.isInstance(packet)
                && invoke(api.packetAction, packet) == api.drawAction;
    }

    private static Binding binding() {
        if (!isLoaded()) {
            throw new IllegalStateException("Arcana is not loaded");
        }
        Binding result = binding;
        if (result != null) {
            return result;
        }
        synchronized (ArcanaRefitCompat.class) {
            if (binding == null) {
                binding = resolveBinding();
            }
            return binding;
        }
    }

    private static Binding resolveBinding() {
        ClassLoader loader = ArcanaRefitCompat.class.getClassLoader();
        try {
            Class<?> managerClass;
            boolean shippedNames;
            try {
                managerClass = Class.forName("group.taczexpands.dist.jlkX1CVq", false, loader);
                shippedNames = true;
            } catch (ClassNotFoundException absentShippedClass) {
                managerClass = Class.forName("group.taczexpands.server.util.GunManager", false, loader);
                shippedNames = false;
            }
            String installName = shippedNames ? "nwLBQEs8" : "refitAttachment";
            String unloadName = shippedNames ? "oVcwyFJh" : "unloadAttachment";
            Method install = managerClass.getMethod(installName, AttachmentType.class, ItemStack.class,
                    Integer.class, ItemStack.class, ServerPlayer.class, boolean.class, Inventory.class);
            Method unload = managerClass.getMethod(unloadName, AttachmentType.class, ItemStack.class,
                    ServerPlayer.class, Inventory.class);
            if (install.getReturnType() != boolean.class || unload.getReturnType() != boolean.class) {
                throw new NoSuchMethodException("Arcana refit methods must return boolean");
            }
            Class<?> contextClass = Class.forName(shippedNames ? "group.taczexpands.dist.kMbDq4yQ"
                    : "group.taczexpands.server.context.Context", false, loader);
            Method contextPlayer = CONTEXT_PLAYER.get(contextClass);
            if (contextPlayer.getReturnType() != ServerPlayer.class) {
                throw new NoSuchMethodException("Arcana context must expose its server player");
            }
            Class<?> skillManager = Class.forName(shippedNames ? "group.taczexpands.dist.Gcj8PhOz"
                    : "group.taczexpands.server.skill.SkillManager", false, loader);
            Class<?> triggerType = Class.forName(shippedNames ? "group.taczexpands.dist.ob4IlMnJ"
                    : "group.taczexpands.server.skill.TriggerType", false, loader);
            for (String name : shippedNames ? new String[]{"rTlp7UQU", "CJ6pttSh", "OIyJu4qq"}
                    : new String[]{"trigger", "triggerSpecificType", "triggerReverse"}) {
                skillManager.getMethod(name, triggerType, contextClass, int.class);
            }
            Class<?> actionPacket = Class.forName(shippedNames ? "group.taczexpands.dist.YbrUmxZQ"
                    : "group.taczexpands.common.network.s2c.S2CAction", false, loader);
            Method packetAction = actionPacket.getMethod(shippedNames ? "EIUSALov" : "getAction");
            Object drawAction = packetAction.getReturnType().getField(shippedNames ? "BFQ0W3BG" : "Draw").get(null);
            Object manager = managerClass.getField(shippedNames ? "RtLWCp8E" : "INSTANCE").get(null);
            return new Binding(manager, install, unload, actionPacket, packetAction, drawAction);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new CompatibilityException("Unsupported Arcana attachment API; expected Arcana 1.1.4", failure);
        }
    }

    private static Method findMethod(Class<?> type, String[] names, Class<?>... parameters) {
        for (String name : names) {
            try {
                return type.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                // The released JAR and the open source development build use different names.
            }
        }
        throw new IllegalStateException("Unsupported Arcana context API: " + type.getName());
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Arcana attachment operation failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Arcana attachment API could not be invoked", failure);
        }
    }

    private record Binding(Object manager, Method install, Method unload, Class<?> actionPacket,
                           Method packetAction, Object drawAction) {
    }
}
