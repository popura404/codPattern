package com.cdp.codpattern.app.refit.gametest;

import com.cdp.codpattern.app.refit.service.RefitActionService;
import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitAction;
import com.cdp.codpattern.core.refit.RefitPreviewContext;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Run explicitly with the released Arcana 1.1.4 JAR; no Arcana compile dependency is required. */
@GameTestHolder("codpattern_arcana")
@PrefixGameTestTemplate(false)
public final class ArcanaRefitGameTests {
    private ArcanaRefitGameTests() { }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana", batch = "arcana_refit", timeoutTicks = 100)
    public static void actualExtendedMagBackendClearsChamberWithoutReturningItems(GameTestHelper helper) throws Exception {
        requireArcana(helper);
        Fixture fixture = findFixture(helper, AttachmentType.EXTENDED_MAG);
        ServerPlayer player = player(helper);
        try (FilterState ignored = new FilterState()) {
            AttachmentEditSession session = start(player, fixture);
            ItemStack gun = session.getRefitInventory().getSelected();
            IGun api = IGun.getIGunOrNull(gun);
            api.setCurrentAmmoCount(gun, 7);
            api.setBulletInBarrel(gun, true);
            AABB area = new AABB(player.blockPosition()).inflate(8);
            int drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
            install(helper, player, session, fixture);
            helper.assertTrue(!api.hasBulletInBarrel(gun),
                    "Arcana's actual EXTENDED_MAG path must clear the chamber (standard TaCZ only drops magazine ammo)");
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (slot != session.getEditHotbarSlot()) helper.assertTrue(player.getInventory().getItem(slot).isEmpty(),
                        "preview ammo/attachment returns must not enter the real player inventory");
            }
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size() == drops,
                    "preview ammo returns must not create item entities");
            helper.assertTrue(!RefitPreviewContext.isActiveFor(player), "operation scope must close after Arcana returns");
            var removed = RefitActionService.execute(player, session.getSessionId(),
                    RefitAction.unload(session.getEditHotbarSlot(), fixture.type()));
            helper.assertTrue(removed.success() && api.getAttachment(gun, fixture.type()).isEmpty(),
                    "actual Arcana unload must remove the selected attachment");
            helper.assertTrue(session.getRefitInventory().getCandidateCount() == 1
                            && attachmentId(session.getRefitInventory().getItem(9)).equals(attachmentId(fixture.attachment())),
                    "Arcana inventory writes must not consume or renumber the stable catalog");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana", batch = "arcana_refit", timeoutTicks = 100)
    public static void actualSkillEntryIsGatedForOnlyTheEditingPlayer(GameTestHelper helper) throws Exception {
        requireArcana(helper);
        Fixture fixture = findFixture(helper, AttachmentType.SCOPE);
        ServerPlayer player = player(helper);
        ServerPlayer other = player(helper);
        Class<?> contextClass = Class.forName("group.taczexpands.dist.kMbDq4yQ");
        Object context = contextClass.getConstructor(ServerPlayer.class).newInstance(player);
        Object otherContext = contextClass.getConstructor(ServerPlayer.class).newInstance(other);
        Class<?> managerClass = Class.forName("group.taczexpands.dist.Gcj8PhOz");
        Object manager = managerClass.getField("cLHuUMKr").get(null);
        var trigger = managerClass.getMethod("rTlp7UQU", Class.forName("group.taczexpands.dist.ob4IlMnJ"), contextClass, int.class);
        helper.assertTrue(!ArcanaRefitCompat.suppressSkill(context), "no session leaves gameplay skills enabled");
        try {
            AttachmentEditSession session = start(player, fixture);
            helper.assertTrue(ArcanaRefitCompat.suppressSkill(context) && !ArcanaRefitCompat.suppressSkill(otherContext),
                    "session gate applies only to the player editing, including ticks outside an operation scope");
            // A null trigger would dereference in the real method body; returning proves the actual Mixin intercepts it.
            trigger.invoke(manager, null, context, 0);
            boolean otherReachedBody = false;
            try {
                trigger.invoke(manager, null, otherContext, 0);
            } catch (InvocationTargetException failure) {
                otherReachedBody = failure.getCause() instanceof NullPointerException;
            }
            helper.assertTrue(otherReachedBody, "another player still reaches Arcana's actual trigger body");
            AttachmentEditSessionManager.endSession(player);
            helper.assertTrue(!ArcanaRefitCompat.suppressSkill(context), "closing the session restores gameplay skill dispatch");
            try (var ignored = RefitPreviewContext.open(player, session.getRefitInventory().getSelected())) {
                trigger.invoke(manager, null, context, 0);
                helper.assertTrue(!ArcanaRefitCompat.suppressSkill(otherContext), "operation scope also remains player-local");
            }
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana", batch = "arcana_refit", timeoutTicks = 100)
    public static void attachmentLaserCopyPersistsAndWrongRootCannotSave(GameTestHelper helper) throws Exception {
        requireArcana(helper);
        Fixture fixture = findFixture(helper, AttachmentType.SCOPE);
        ServerPlayer player = player(helper);
        try (FilterState ignored = new FilterState()) {
            AttachmentEditSession session = start(player, fixture);
            install(helper, player, session, fixture);
            var colored = RefitActionService.execute(player, session.getSessionId(),
                    RefitAction.laser(session.getEditHotbarSlot(), Map.of(fixture.type().name(), 0x123456), false, 0));
            ItemStack gun = session.getRefitInventory().getSelected();
            ItemStack installed = IGun.getIGunOrNull(gun).getAttachment(gun, fixture.type());
            helper.assertTrue(colored.success() && IAttachment.getIAttachmentOrNull(installed).getLaserColor(installed) == 0x123456,
                    "laser edits persist into the attachment stored in the gun, not just a detached ItemStack copy");
            gun.getOrCreateTag().putString(GunItemDataAccessor.GUN_ID_TAG, "codpattern:changed_root");
            var rejected = RefitActionService.execute(player, session.getSessionId(), RefitAction.save());
            helper.assertTrue(!rejected.success() && rejected.finished()
                            && !AttachmentEditSessionManager.hasSession(player.getUUID()),
                    "SAVE rejects a different root gun identity before touching any persisted configuration");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana", batch = "arcana_refit", timeoutTicks = 100)
    public static void syntheticNestedColorContractPreservesUnrelatedExtras(GameTestHelper helper) {
        requireArcana(helper);
        // This tests the NBT persistence contract only. A real underbarrel fixture is required in the separate fixture suite.
        ItemStack source = findFixture(helper, AttachmentType.SCOPE).gun().copy();
        source.getOrCreateTag().putInt(GunItemDataAccessor.LASER_COLOR_TAG, 0x123456);
        CompoundTag nested = new CompoundTag();
        nested.putInt(GunItemDataAccessor.LASER_COLOR_TAG, 0x654321);
        nested.putInt("AmmoCurrent", 31);
        CompoundTag extras = new CompoundTag();
        extras.put("UnderBarrelRoot", nested);
        extras.putString("UnrelatedExtra", "source");
        source.getOrCreateTag().put("Extras", extras);
        var gateway = TaczGatewayProvider.gateway();
        CompoundTag preset = gateway.buildAttachmentPreset(source);
        ItemStack target = source.copy();
        CompoundTag targetExtras = target.getTag().getCompound("Extras");
        targetExtras.putString("UnrelatedExtra", "target");
        targetExtras.getCompound("UnderBarrelRoot").putInt("AmmoCurrent", 5);
        targetExtras.getCompound("UnderBarrelRoot").putInt(GunItemDataAccessor.LASER_COLOR_TAG, 0);
        target.getTag().putInt(GunItemDataAccessor.LASER_COLOR_TAG, 0);
        gateway.applyAttachmentPreset(target, preset);
        helper.assertTrue(target.getTag().getInt(GunItemDataAccessor.LASER_COLOR_TAG) == 0x123456
                        && targetExtras.getCompound("UnderBarrelRoot").getInt(GunItemDataAccessor.LASER_COLOR_TAG) == 0x654321,
                "root and nested laser colors round-trip independently");
        helper.assertTrue("target".equals(targetExtras.getString("UnrelatedExtra"))
                        && targetExtras.getCompound("UnderBarrelRoot").getInt("AmmoCurrent") == 5,
                "a preset does not replace Extras or copy ammunition from the preview gun");
        ItemStack noUnderbarrel = source.copy();
        noUnderbarrel.getTag().remove("Extras");
        gateway.applyAttachmentPreset(noUnderbarrel, preset);
        helper.assertTrue(!noUnderbarrel.getTag().contains("Extras"), "a preset cannot invent an underbarrel root");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana", batch = "arcana_refit", timeoutTicks = 100)
    public static void syntheticSkillExercisesActualReverseRepeatAndDelayedEntrypoints(GameTestHelper helper) throws Exception {
        requireArcana(helper);
        var player = player(helper);
        var fixture = findFixture(helper, AttachmentType.SCOPE);
        ClassLoader loader = ArcanaRefitGameTests.class.getClassLoader();
        Class<?> skillClass = Class.forName("group.taczexpands.dist.FkeGEJ2j");
        Class<?> contextClass = Class.forName("group.taczexpands.dist.kMbDq4yQ");
        Class<?> actionClass = Class.forName("group.taczexpands.dist.QEMFAKxS");
        Class<?> flagsClass = Class.forName("group.taczexpands.dist.xAhWMMn8");
        Class<?> configClass = Class.forName("group.taczexpands.dist.O2GST6JZ");
        Class<?> taskClass = Class.forName("group.taczexpands.dist.cvNhRCT1");
        Class<?> functionClass = Class.forName("kotlin.jvm.functions.Function1");
        Class<?> function0Class = Class.forName("kotlin.jvm.functions.Function0");
        Class<?> counterClass = Class.forName("kotlin.jvm.internal.Ref$IntRef");
        Object unit = Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        int[] actionCalls = {0}, dueCalls = {0}, clearCalls = {0};
        Object action = Proxy.newProxyInstance(loader, new Class<?>[]{actionClass}, (proxy, method, args) -> {
            if (method.getName().equals("qUyEZ9qy")) actionCalls[0]++;
            return null;
        });
        Object flags = flagsClass.getConstructor(int.class, String.class, int.class, int.class, boolean.class,
                        int.class, int.class, int.class, String.class, Set.class, boolean.class, boolean.class)
                .newInstance(100, null, 0, 0, false, 0, 3, 2, "ALL_TRUE", Set.of(), false, true);
        Object config = configClass.getConstructor(String.class, String.class, List.class, List.class, String.class,
                        flagsClass, List.class, List.class, List.class)
                .newInstance("cdp-synthetic-entry-test", "", null, null, "ON_ACTION_1", flags,
                        List.of(), List.of(action), List.of(action));
        Object skill = skillClass.getConstructor(configClass).newInstance(config);
        Object context = contextClass.getConstructor(ServerPlayer.class).newInstance(player);
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{functionClass}, (proxy, method, args) -> {
            if (method.getName().equals("invoke")) dueCalls[0]++;
            return unit;
        });
        Object clear = Proxy.newProxyInstance(loader, new Class<?>[]{function0Class}, (proxy, method, args) -> {
            if (method.getName().equals("invoke")) clearCalls[0]++;
            return unit;
        });
        var taskConstructor = taskClass.getConstructor(skillClass, ServerPlayer.class, functionClass,
                int.class, boolean.class, functionClass);
        Object task = taskConstructor.newInstance(skill, player, callback, 1, false, null);
        Object repeatedTask = taskConstructor.newInstance(skill, null, callback, 1, false, null);
        Object counter = counterClass.getConstructor().newInstance();
        var repeat = skillClass.getDeclaredMethod("UpBotgoC", skillClass, contextClass, counterClass, taskClass);
        repeat.setAccessible(true);
        var reverse = skillClass.getMethod("wtAAjD2V", contextClass);
        var taskTick = taskClass.getMethod("ddDNMnwx");
        var remaining = taskClass.getMethod("U0tzZBt1");
        var effectTick = Class.forName("group.taczexpands.dist.jZItwoCj").getDeclaredMethod("ZJsYraWz", function0Class, taskClass);
        effectTick.setAccessible(true);
        try {
            start(player, fixture);
            reverse.invoke(skill, context);
            repeat.invoke(null, skill, context, counter, repeatedTask);
            helper.assertTrue(actionCalls[0] == 0 && counterClass.getField("element").getInt(counter) == 1
                            && (int) remaining.invoke(repeatedTask) == 2,
                    "actual reverse/repeat entries suppress actions while preserving repeat counts and rescheduling");
            helper.assertTrue((boolean) taskTick.invoke(task) && (int) remaining.invoke(task) == 0 && dueCalls[0] == 0,
                    "actual delayed task still counts down and completes while its due gameplay action is suppressed");
            effectTick.invoke(null, clear, repeatedTask);
            helper.assertTrue(clearCalls[0] == 1,
                    "an ownerless task must not be treated as this player's Effect task");
            effectTick.invoke(null, clear, task);
            helper.assertTrue(clearCalls[0] == 1 && (int) remaining.invoke(task) == -1,
                    "actual Effect.CLEAR timing callback suppresses only its side effect and still decrements time");
            AttachmentEditSessionManager.endSession(player);
            reverse.invoke(skill, context);
            repeat.invoke(null, skill, context, counter, repeatedTask);
            taskTick.invoke(taskConstructor.newInstance(skill, player, callback, 1, false, null));
            effectTick.invoke(null, clear, task);
            helper.assertTrue(actionCalls[0] == 2 && dueCalls[0] == 1 && clearCalls[0] == 2,
                    "closing the session restores actual reverse, repeat, delayed and effect action execution");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    static void requireArcana(GameTestHelper helper) {
        helper.assertTrue(ArcanaRefitCompat.isLoaded(), "This namespace requires the actual Arcana 1.1.4 JAR");
    }

    static Fixture findFixture(GameTestHelper helper, AttachmentType type) {
        List<ItemStack> attachments = TaczGatewayProvider.gateway().listAttachmentCandidates();
        for (var entry : TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList()) {
            ItemStack gun = GunItemBuilder.create().setId(entry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            IGun api = IGun.getIGunOrNull(gun);
            if (api == null || !api.allowAttachmentType(gun, type)) continue;
            for (ItemStack attachment : attachments) {
                IAttachment attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                if (attachmentApi != null && attachmentApi.getType(attachment) == type && api.allowAttachment(gun, attachment)) {
                    LogUtils.getLogger().info("Arcana real resource fixture: gun={} type={} attachment={}",
                            entry.getKey(), type, attachmentId(attachment));
                    return new Fixture(gun, attachment, type);
                }
            }
        }
        helper.fail("Required real gunpack fixture missing: compatible gun and " + type + " attachment");
        throw new AssertionError("required actual gunpack fixture missing");
    }

    static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "arcana-refit"));
    }

    static AttachmentEditSession start(ServerPlayer player, Fixture fixture) {
        return AttachmentEditSessionManager.startSession(player, UUID.randomUUID(), 1, "primary", fixture.gun(), List.of(fixture.attachment()));
    }

    static void install(GameTestHelper helper, ServerPlayer player, AttachmentEditSession session, Fixture fixture) {
        var result = RefitActionService.execute(player, session.getSessionId(), RefitAction.install(9, session.getEditHotbarSlot(), fixture.type()));
        helper.assertTrue(result.success(), "actual Arcana install must succeed: " + result.message().getString());
        ItemStack gun = session.getRefitInventory().getSelected();
        helper.assertTrue(attachmentId(fixture.attachment()).equals(attachmentId(IGun.getIGunOrNull(gun).getAttachment(gun, fixture.type()))),
                "actual target attachment equals the selected catalog entry");
    }

    static String attachmentId(ItemStack stack) {
        return TaczGatewayProvider.gateway().resolveAttachmentId(stack).orElse("");
    }

    record Fixture(ItemStack gun, ItemStack attachment, AttachmentType type) { }

    static final class FilterState implements AutoCloseable {
        private final Map<Field, Object> previous = new LinkedHashMap<>();
        private final Path root;
        FilterState() throws Exception {
            for (String name : List.of("serverConfigPath", "serverConfig", "loadFailure")) {
                Field field = WeaponFilterConfigRepository.class.getDeclaredField(name);
                field.setAccessible(true);
                previous.put(field, field.get(null));
            }
            root = Files.createTempDirectory("cdp-arcana-refit-");
            var filter = WeaponFilterConfigRepository.loadOrCreate(root.resolve("filter.json"));
            filter.setBlockedAttachmentIds(List.of());
            filter.setBlockedAttachmentNamespaces(List.of());
        }
        public void close() throws Exception {
            for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
