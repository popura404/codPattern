package com.cdp.codpattern.app.refit.gametest;

import com.cdp.codpattern.app.refit.service.RefitActionService;
import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitAction;
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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Deliberately separate: missing real MODULE/underbarrel gunpack resources fail, never silently pass. */
@GameTestHolder("codpattern_arcana_fixtures")
@PrefixGameTestTemplate(false)
public final class ArcanaFixtureGameTests {
    private ArcanaFixtureGameTests() { }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_fixtures", timeoutTicks = 100)
    public static void actualModuleInstallsPersistsAndUnloads(GameTestHelper helper) throws Exception {
        ArcanaRefitGameTests.requireArcana(helper);
        AttachmentType module = AttachmentType.valueOf("MODULE");
        var fixture = ArcanaRefitGameTests.findFixture(helper, module);
        var player = ArcanaRefitGameTests.player(helper);
        try (var ignored = new ArcanaRefitGameTests.FilterState()) {
            var session = ArcanaRefitGameTests.start(player, fixture);
            ArcanaRefitGameTests.install(helper, player, session, fixture);
            var gateway = TaczGatewayProvider.gateway();
            ItemStack reopened = fixture.gun().copy();
            gateway.applyAttachmentPreset(reopened, gateway.buildAttachmentPreset(session.getRefitInventory().getSelected()));
            helper.assertTrue(ArcanaRefitGameTests.attachmentId(fixture.attachment()).equals(
                            ArcanaRefitGameTests.attachmentId(IGun.getIGunOrNull(reopened).getAttachment(reopened, module))),
                    "runtime-added MODULE survives preset capture and reapply");
            var removed = RefitActionService.execute(player, session.getSessionId(), RefitAction.unload(session.getEditHotbarSlot(), module));
            helper.assertTrue(removed.success() && IGun.getIGunOrNull(removed.gun()).getAttachment(removed.gun(), module).isEmpty(),
                    "actual Arcana MODULE unload succeeds without assuming the base TaCZ enum length");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_fixtures", timeoutTicks = 100)
    public static void actualUnderbarrelKeepsRootCatalogAndIndependentColors(GameTestHelper helper) throws Exception {
        ArcanaRefitGameTests.requireArcana(helper);
        Class<?> extrasClass = Class.forName("group.taczexpands.dist.cSFSPogO");
        Object extrasApi = extrasClass.getField("lYcekq0C").get(null);
        Method underbarrel = extrasClass.getMethod("SMuDyDJd", ItemStack.class);
        Method setActive = extrasClass.getMethod("sTzrnRrP", ItemStack.class, boolean.class);
        var fixture = findUnderbarrelFixture(helper, extrasApi, underbarrel, setActive);
        var gateway = TaczGatewayProvider.gateway();
        ItemStack base = fixture.gun().copy();
        setActive.invoke(extrasApi, base, false);
        String rootId = gateway.resolveGunId(base).orElseThrow();
        List<String> expectedCandidates = compatibleIds(base);
        IGun baseApi = IGun.getIGunOrNull(base);
        baseApi.setLaserColor(base, 0x102030);
        setActive.invoke(extrasApi, base, true);
        baseApi.setLaserColor(base, 0x405060);
        helper.assertTrue(!rootId.equals(gateway.resolveGunId(base).orElseThrow())
                        && rootId.equals(gateway.resolveRefitGunId(base).orElseThrow()),
                "battle identity still uses the real underbarrel while CDP configuration identity uses the root");
        helper.assertTrue(expectedCandidates.equals(compatibleIds(base)),
                "actual Arcana allowAttachment/type uses root data and preserves the same candidate list in either mode");
        var player = ArcanaRefitGameTests.player(helper);
        var activeFixture = new ArcanaRefitGameTests.Fixture(base, fixture.attachment(), fixture.type());
        try (var ignored = new ArcanaRefitGameTests.FilterState()) {
            var session = ArcanaRefitGameTests.start(player, activeFixture);
            ArcanaRefitGameTests.install(helper, player, session, activeFixture);
            ItemStack gun = session.getRefitInventory().getSelected();
            helper.assertTrue(session.matchesGunIdentity(gun), "active underbarrel keeps the same CDP editing identity");
            var colored = RefitActionService.execute(player, session.getSessionId(),
                    RefitAction.laser(session.getEditHotbarSlot(), Map.of(), true, 0x708090));
            helper.assertTrue(colored.success() && IGun.getIGunOrNull(gun).getLaserColor(gun) == 0x708090,
                    "gun-color action applies to the currently active underbarrel");
            helper.assertTrue(gun.getTag().getInt(GunItemDataAccessor.LASER_COLOR_TAG) == 0x102030,
                    "underbarrel color editing preserves the main gun color");
            CompoundTag preset = gateway.buildAttachmentPreset(gun);
            ItemStack reopened = base.copy();
            CompoundTag savedExtras = reopened.getTag().getCompound("Extras").copy();
            gateway.applyAttachmentPreset(reopened, preset);
            helper.assertTrue(IGun.getIGunOrNull(reopened).getLaserColor(reopened) == 0x708090
                            && reopened.getTag().getInt(GunItemDataAccessor.LASER_COLOR_TAG) == 0x102030,
                    "preset reapply preserves independent main and underbarrel colors");
            savedExtras.getCompound("UnderBarrelRoot").putInt(GunItemDataAccessor.LASER_COLOR_TAG, 0x708090);
            helper.assertTrue(savedExtras.equals(reopened.getTag().getCompound("Extras")),
                    "reapplying attachment configuration preserves every unrelated actual Extras field");
            helper.assertTrue(ArcanaRefitGameTests.attachmentId(fixture.attachment()).equals(
                            ArcanaRefitGameTests.attachmentId(IGun.getIGunOrNull(reopened).getAttachment(reopened, fixture.type()))),
                    "main/shared attachment state survives editing while the underbarrel is active");
            ItemStack lazyReopened = base.copy();
            lazyReopened.getTag().getCompound("Extras").remove("UnderBarrelRoot");
            gateway.applyAttachmentPreset(lazyReopened, preset);
            helper.assertTrue(IGun.getIGunOrNull(lazyReopened).getLaserColor(lazyReopened) == 0x708090,
                    "first-time underbarrel color survives reapplying to base NBT that never had a nested root");
        } finally {
            AttachmentEditSessionManager.endSession(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_fixtures", timeoutTicks = 100)
    public static void actualGripReplacementAndRemovalResetOldUnderbarrelState(GameTestHelper helper) throws Exception {
        ArcanaRefitGameTests.requireArcana(helper);
        Class<?> extrasClass = Class.forName("group.taczexpands.dist.cSFSPogO");
        Object extrasApi = extrasClass.getField("lYcekq0C").get(null);
        Method setActive = extrasClass.getMethod("sTzrnRrP", ItemStack.class, boolean.class);
        var transition = findGripTransition(helper);
        for (ItemStack replacement : List.of(ItemStack.EMPTY, transition.replacement())) {
            var player = ArcanaRefitGameTests.player(helper);
            ItemStack original = transition.gun().copy();
            setActive.invoke(extrasApi, original, true);
            CompoundTag root = ArcanaRefitCompat.underbarrelRootForPreset(original);
            root.putInt("OldUnderbarrelMarker", 73);
            root.putInt("AmmoCurrent", 13);
            original.getTag().getCompound("Extras").putInt("UnrelatedState", 82);
            try (var ignored = new ArcanaRefitGameTests.FilterState()) {
                var fixture = new ArcanaRefitGameTests.Fixture(original, replacement, AttachmentType.GRIP);
                var session = ArcanaRefitGameTests.start(player, fixture);
                var operation = replacement.isEmpty() ? RefitAction.unload(session.getEditHotbarSlot(), AttachmentType.GRIP)
                        : RefitAction.install(9, session.getEditHotbarSlot(), AttachmentType.GRIP);
                var result = RefitActionService.execute(player, session.getSessionId(), operation);
                helper.assertTrue(result.success(), "actual Arcana GRIP transition must succeed");
                var gateway = TaczGatewayProvider.gateway();
                ItemStack reopened = original.copy();
                gateway.applyAttachmentPreset(reopened, gateway.buildAttachmentPreset(result.gun()));
                CompoundTag extras = reopened.getTag().getCompound("Extras");
                helper.assertTrue(!extras.getBoolean("UsingUnderBarrel")
                                && !extras.getCompound("UnderBarrelRoot").contains("OldUnderbarrelMarker"),
                        "reapplying changed/removed GRIP resets old underbarrel mode and state as the actual manager did");
                helper.assertTrue(extras.getInt("UnrelatedState") == 82,
                        "GRIP reset preserves unrelated original Extras fields");
            } finally {
                AttachmentEditSessionManager.endSession(player);
            }
        }
        helper.succeed();
    }

    private static GripTransition findGripTransition(GameTestHelper helper) throws Exception {
        var gateway = TaczGatewayProvider.gateway();
        Class<?> managerClass = Class.forName("group.taczexpands.dist.jlkX1CVq");
        Object manager = managerClass.getField("RtLWCp8E").get(null);
        Class<?> underbarrelClass = Class.forName("group.taczexpands.dist.UpCVRNnO");
        Method same = managerClass.getDeclaredMethod("PQ3jy0GO", underbarrelClass, underbarrelClass);
        same.setAccessible(true);
        var grips = gateway.listAttachmentCandidates().stream()
                .filter(attachment -> IAttachment.getIAttachmentOrNull(attachment).getType(attachment) == AttachmentType.GRIP).toList();
        for (var entry : TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList()) {
            ItemStack base = GunItemBuilder.create().setId(entry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            IGun api = IGun.getIGunOrNull(base);
            if (api == null) continue;
            for (ItemStack grip : grips) {
                if (!api.allowAttachment(base, grip)) continue;
                ItemStack gun = base.copy();
                api.installAttachment(gun, grip);
                Object current = ArcanaRefitCompat.captureUnderbarrel(gun);
                if (current == null || (boolean) same.invoke(manager, current, ArcanaRefitCompat.captureUnderbarrel(base))) continue;
                for (ItemStack replacement : grips) {
                    if (!api.allowAttachment(base, replacement)) continue;
                    ItemStack changed = base.copy();
                    api.installAttachment(changed, replacement);
                    if (!(boolean) same.invoke(manager, current, ArcanaRefitCompat.captureUnderbarrel(changed))) {
                        LogUtils.getLogger().info("Arcana actual GRIP transition fixture: gun={} old={} replacement={}",
                                entry.getKey(), ArcanaRefitGameTests.attachmentId(grip), ArcanaRefitGameTests.attachmentId(replacement));
                        return new GripTransition(gun, replacement);
                    }
                }
            }
        }
        helper.fail("Required real GRIP transition fixture missing: removable underbarrel grip and a compatible different replacement");
        throw new AssertionError("required actual GRIP transition fixture missing");
    }

    private record GripTransition(ItemStack gun, ItemStack replacement) { }

    private static ArcanaRefitGameTests.Fixture findUnderbarrelFixture(GameTestHelper helper, Object extrasApi,
            Method underbarrel, Method setActive) throws Exception {
        var attachments = TaczGatewayProvider.gateway().listAttachmentCandidates();
        for (var entry : TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList()) {
            ItemStack gun = GunItemBuilder.create().setId(entry.getKey()).setAmmoCount(0).setAmmoInBarrel(false).build();
            IGun api = IGun.getIGunOrNull(gun);
            if (api == null) continue;
            List<ItemStack> grips = new ArrayList<>();
            grips.add(ItemStack.EMPTY); // Also accept a built-in or gun-data underbarrel.
            for (ItemStack attachment : attachments) {
                var attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                if (attachmentApi != null && attachmentApi.getType(attachment) == AttachmentType.GRIP
                        && api.allowAttachment(gun, attachment)) grips.add(attachment);
            }
            for (ItemStack grip : grips) {
                ItemStack candidate = gun.copy();
                if (!grip.isEmpty()) api.installAttachment(candidate, grip);
                if (underbarrel.invoke(extrasApi, candidate) == null) continue;
                setActive.invoke(extrasApi, candidate, true);
                if (entry.getKey().equals(api.getGunId(candidate))) continue;
                for (ItemStack attachment : attachments) {
                    var attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                    if (attachmentApi != null && attachmentApi.getType(attachment) == AttachmentType.SCOPE
                            && api.allowAttachment(candidate, attachment)) {
                        LogUtils.getLogger().info("Arcana actual underbarrel fixture: root={} active={} grip={} scope={}",
                                entry.getKey(), api.getGunId(candidate), ArcanaRefitGameTests.attachmentId(grip),
                                ArcanaRefitGameTests.attachmentId(attachment));
                        return new ArcanaRefitGameTests.Fixture(candidate, attachment, AttachmentType.SCOPE);
                    }
                }
            }
        }
        helper.fail("Required real underbarrel gunpack fixture missing: valid different underbarrel gun ID and compatible scope; synthetic NBT is not a substitute");
        throw new AssertionError("required actual underbarrel fixture missing");
    }

    private static List<String> compatibleIds(ItemStack gun) {
        var gateway = TaczGatewayProvider.gateway();
        return gateway.listAttachmentCandidates().stream().filter(attachment -> gateway.canAttach(gun, attachment))
                .map(ArcanaRefitGameTests::attachmentId).toList();
    }
}
