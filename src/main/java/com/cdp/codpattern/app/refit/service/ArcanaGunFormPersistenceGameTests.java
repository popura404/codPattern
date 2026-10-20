package com.cdp.codpattern.app.refit.service;

import com.cdp.codpattern.app.backpack.service.BackpackAttachmentFilter;
import com.cdp.codpattern.app.backpack.service.BackpackDistributor;
import com.cdp.codpattern.compat.arcana.ArcanaGunFormCompat;
import com.cdp.codpattern.compat.arcana.ArcanaGunFormRules;
import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitAction;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Real Vanguard rules and resources are required; missing fixtures are failures. */
@GameTestHolder("codpattern_arcana_fixtures")
@PrefixGameTestTemplate(false)
public final class ArcanaGunFormPersistenceGameTests {
    private static final String BASE = "ts:ak308", FORM = "ts:ak308_gpr20", MODULE = "ts:gpr20obr28";
    private ArcanaGunFormPersistenceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_gun_forms", timeoutTicks = 100)
    public static void officialFormSavesReopensAndReversesInBothSlots(GameTestHelper helper) throws Exception {
        ItemStack module = attachment(MODULE);
        AttachmentType type = AttachmentType.valueOf("MODULE");
        // Positive control uses Arcana's unmodified, ordinary skill dispatch outside a CDP session.
        ServerPlayer normal = player(helper);
        ItemStack normalGun = gun(BASE);
        normal.getInventory().setItem(0, normalGun);
        normal.getInventory().setItem(9, module.copy());
        normal.getInventory().selected = 0;
        Class<?> managerType = Class.forName("group.taczexpands.dist.jlkX1CVq");
        Object manager = managerType.getField("RtLWCp8E").get(null);
        boolean installed = (boolean) managerType.getMethod("nwLBQEs8", AttachmentType.class, ItemStack.class,
                Integer.class, ItemStack.class, ServerPlayer.class, boolean.class, Inventory.class)
                .invoke(manager, type, module.copy(), 9, normalGun, normal, false, normal.getInventory());
        helper.assertTrue(installed && FORM.equals(id(normalGun)), "ordinary real Arcana executes the official AK308 conversion");

        for (String slot : List.of("primary", "secondary")) {
            ServerPlayer player = player(helper);
            player.getInventory().setItem(4, new ItemStack(Items.EMERALD, 7));
            player.getInventory().selected = 4;
            try (var files = new AttachmentPresetPersistenceGameTests.RepositoryFiles();
                 var paths = new AttachmentPresetPersistenceGameTests.RequestPaths(player, files)) {
                ItemStack base = gun(BASE);
                base.getOrCreateTag().putInt("PreservedFormMarker", 719);
                CompoundTag originalNbt = base.getTag().copy();
                var backpack = files.backpack.getOrCreatePlayerData(player.getStringUUID()).getBackpacks_MAP().get(1);
                backpack.getItem_MAP().put(slot, item(base));
                BackpackConfigRepository.save();

                var prepared = AttachmentPresetRequestService.prepare(player, UUID.randomUUID(), 1, slot);
                helper.assertTrue(prepared.isPresent(), "real request opens the configured base gun");
                AttachmentEditSession session = AttachmentEditSessionManager.getSession(player.getUUID());
                List<ItemStack> directory = session.getRefitInventory().snapshotCandidates();
                ItemStack scope = directory.stream().filter(item -> com.tacz.guns.api.item.IAttachment
                                .getIAttachmentOrNull(item).getType(item) == AttachmentType.SCOPE).findFirst().orElseThrow();
                String scopeId = TaczGatewayProvider.gateway().resolveAttachmentId(scope).orElseThrow();
                helper.assertTrue(RefitActionService.execute(player, session.getSessionId(),
                        RefitAction.install(candidateSlot(directory, scopeId), session.getEditHotbarSlot(), AttachmentType.SCOPE)).success(),
                        "install a real scope before conversion hides its slot");
                int sourceSlot = candidateSlot(directory, MODULE);
                var result = RefitActionService.execute(player, session.getSessionId(),
                        RefitAction.install(sourceSlot, session.getEditHotbarSlot(), type));
                helper.assertTrue(result.success() && FORM.equals(id(result.gun()))
                                && session.matchesGunIdentity(session.getRefitInventory().getSelected()),
                        "CDP derives the same official form and accepts only its validated new identity");
                helper.assertTrue(directory.equals(session.getRefitInventory().snapshotCandidates())
                                || directoryIds(directory).equals(directoryIds(session.getRefitInventory().snapshotCandidates())),
                        "form conversion retains every candidate identity and slot");
                helper.assertTrue(RefitActionService.execute(player, session.getSessionId(), RefitAction.save()).success(),
                        "converted gun saves successfully");
                var saved = BackpackConfigRepository.loadOrCreate(files.backpackPath).getPlayerData()
                        .get(player.getStringUUID()).getBackpacks_MAP().get(1).getItem_MAP().get(slot);
                CompoundTag expected = originalNbt.copy();
                expected.putString("GunId", FORM);
                helper.assertTrue(expected.equals(TagParser.parseTag(saved.getNbt())),
                        "save changes only root GunId in the original configuration NBT");
                ItemStack given = BackpackItemStackFactory.create(saved);
                helper.assertTrue(FORM.equals(id(given)) && MODULE.equals(TaczGatewayProvider.gateway()
                                .resolveAttachmentId(IGun.getIGunOrNull(given).getAttachment(given, type)).orElse("")),
                        "disk reload and the real issue factory retain both the transformed gun and module; gun="
                                + given.getTag() + " preset=" + saved.getAttachmentPreset());
                helper.assertTrue(scopeId.equals(TaczGatewayProvider.gateway().resolveAttachmentId(
                                ArcanaGunFormCompat.readStoredAttachment(given, AttachmentType.SCOPE)).orElse("")),
                        "the hidden source-form scope survives preset capture and the real issue factory");
                WeaponFilterConfig hiddenScopeFilter = new WeaponFilterConfig();
                hiddenScopeFilter.setBlockedAttachmentIds(List.of(scopeId));
                hiddenScopeFilter.setBlockedAttachmentNamespaces(List.of());
                helper.assertTrue(BackpackAttachmentFilter.findFirstBlockedInstalledAttachmentId(hiddenScopeFilter, given).isPresent(),
                        "hidden installed slots still participate in save-time blacklist validation");
                ItemStack cleaned = given.copy();
                helper.assertTrue(BackpackAttachmentFilter.removeBlockedInstalledAttachments(hiddenScopeFilter, cleaned)
                                && ArcanaGunFormCompat.readStoredAttachment(cleaned, AttachmentType.SCOPE).isEmpty()
                                && FORM.equals(id(cleaned)),
                        "blacklist cleanup removes the dormant scope without removing the allowed conversion module");
                helper.assertTrue(AttachmentPresetRequestService.prepare(player, UUID.randomUUID(), 1, slot).isPresent(),
                        "converted form reopens through the real request service");
                session = AttachmentEditSessionManager.getSession(player.getUUID());
                helper.assertTrue(FORM.equals(id(session.getRefitInventory().getSelected())), "reopen keeps the derived form");
                var removed = RefitActionService.execute(player, session.getSessionId(),
                        RefitAction.unload(session.getEditHotbarSlot(), type));
                helper.assertTrue(removed.success() && BASE.equals(id(removed.gun())), "unloading applies the official reverse rule");
                helper.assertTrue(scopeId.equals(TaczGatewayProvider.gateway().resolveAttachmentId(
                                IGun.getIGunOrNull(removed.gun()).getAttachment(removed.gun(), AttachmentType.SCOPE)).orElse("")),
                        "inverse form reveals the original scope instead of losing it");
                helper.assertTrue(RefitActionService.execute(player, session.getSessionId(), RefitAction.save()).success(),
                        "reverse transformation also saves successfully");
                saved = BackpackConfigRepository.loadOrCreate(files.backpackPath).getPlayerData()
                        .get(player.getStringUUID()).getBackpacks_MAP().get(1).getItem_MAP().get(slot);
                helper.assertTrue(originalNbt.equals(TagParser.parseTag(saved.getNbt()))
                                && BASE.equals(id(BackpackItemStackFactory.create(saved))),
                        "reverse save restores the original base identity without importing working ammunition or Extras");
                helper.assertTrue(player.getInventory().selected == 4 && player.getInventory().getItem(4).is(Items.EMERALD)
                                && player.getInventory().getItem(4).getCount() == 7,
                        "both saved sessions restore the original player inventory");
            } finally { AttachmentEditSessionManager.endSession(player); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_gun_forms", timeoutTicks = 100)
    public static void cleanupCannotIssueAForbiddenReverseForm(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        try (var files = new AttachmentPresetPersistenceGameTests.RepositoryFiles();
             var paths = new AttachmentPresetPersistenceGameTests.RequestPaths(player, files)) {
            ItemStack converted = gun(BASE);
            IGun.getIGunOrNull(converted).installAttachment(converted, attachment(MODULE));
            ArcanaGunFormCompat.normalize(converted);
            helper.assertTrue(FORM.equals(id(converted)), "fixture is the allowed transformed gun before cleanup");
            var backpack = files.backpack.getOrCreatePlayerData(player.getStringUUID()).getBackpacks_MAP().get(1);
            backpack.getItem_MAP().put("primary", new BackpackConfig.Backpack.ItemData(
                    converted.getItem().builtInRegistryHolder().key().location().toString(), 1,
                    gun(FORM).getTag().toString(), TaczGatewayProvider.gateway().buildAttachmentPreset(converted).toString()));
            BackpackConfigRepository.save();
            var filter = WeaponFilterConfigRepository.loadOrCreate(files.filterPath);
            filter.setBlockedWeaponIds(List.of(BASE));
            filter.setBlockedAttachmentIds(List.of(MODULE));
            WeaponFilterConfigRepository.save(filter);
            BackpackDistributor.forceDistributeBackpackItemsSilently(player);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                    "final gun blacklist check rejects the base identity produced by removing a forbidden module");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_gun_forms", timeoutTicks = 100)
    public static void blacklistedModuleReversesFormAndClearsOldUnderbarrel(GameTestHelper helper) throws Exception {
        ItemStack gun = gun(BASE);
        IGun.getIGunOrNull(gun).installAttachment(gun, attachment(MODULE));
        helper.assertTrue(ArcanaGunFormCompat.normalize(gun).status() == ArcanaGunFormRules.Status.CHANGED
                && FORM.equals(id(gun)), "actual module derives the form before blacklist cleanup");
        Class<?> extras = Class.forName("group.taczexpands.dist.cSFSPogO");
        extras.getMethod("sTzrnRrP", ItemStack.class, boolean.class).invoke(extras.getField("lYcekq0C").get(null), gun, true);
        helper.assertTrue(ArcanaRefitCompat.underbarrelRootForPreset(gun) != null,
                "official transformed gun provides a real built-in underbarrel");
        gun.getOrCreateTag().getCompound("Extras").putInt("KeepFormExtra", 29);
        WeaponFilterConfig filter = new WeaponFilterConfig();
        filter.setBlockedAttachmentIds(List.of(MODULE));
        filter.setBlockedAttachmentNamespaces(List.of());
        helper.assertTrue(BackpackAttachmentFilter.removeBlockedInstalledAttachments(filter, gun)
                && BASE.equals(id(gun)), "removing the forbidden module also reverses its derived form");
        CompoundTag state = gun.getTag().getCompound("Extras");
        helper.assertTrue(!state.getBoolean("UsingUnderBarrel") && !state.contains("UnderBarrelRoot")
                && state.getInt("KeepFormExtra") == 29, "reverse form clears obsolete underbarrel state and preserves other Extras");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_gun_forms", timeoutTicks = 100)
    public static void complexQtsConversionIsRejectedBeforeMutation(GameTestHelper helper) throws Exception {
        ItemStack gun = gun("ts:railgun");
        ItemStack candidate = attachment("ts:spec_heavycrossbow");
        var type = com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(candidate).getType(candidate);
        ServerPlayer player = player(helper);
        try (var files = new AttachmentPresetPersistenceGameTests.RepositoryFiles()) {
            var session = AttachmentEditSessionManager.startSession(player, 1, "primary", gun, List.of(candidate));
            CompoundTag before = session.getRefitInventory().getSelected().getTag().copy();
            var result = RefitActionService.execute(player, session.getSessionId(), RefitAction.install(9, session.getEditHotbarSlot(), type));
            helper.assertTrue(!result.success() && result.message().getContents() instanceof TranslatableContents contents
                            && contents.getKey().endsWith("operation_arcana_form_unsupported"),
                    "complex reverse SetAttachment+NBTEdit is explicitly rejected, including its otherwise simple forward half");
            helper.assertTrue(before.equals(session.getRefitInventory().getSelected().getTag()),
                    "unsupported form conversion does not partially install, drain ammunition or change identity");
        } finally { AttachmentEditSessionManager.endSession(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_arcana_fixtures", batch = "arcana_gun_forms", timeoutTicks = 100)
    public static void formSaveCannotOverwriteAReplacedConfiguredGun(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        try (var files = new AttachmentPresetPersistenceGameTests.RepositoryFiles();
             var paths = new AttachmentPresetPersistenceGameTests.RequestPaths(player, files)) {
            var backpack = files.backpack.getOrCreatePlayerData(player.getStringUUID()).getBackpacks_MAP().get(1);
            backpack.getItem_MAP().put("primary", item(gun(BASE)));
            BackpackConfigRepository.save();
            helper.assertTrue(AttachmentPresetRequestService.prepare(player, UUID.randomUUID(), 1, "primary").isPresent(),
                    "base gun opens before the configuration is replaced");
            var session = AttachmentEditSessionManager.getSession(player.getUUID());
            helper.assertTrue(RefitActionService.execute(player, session.getSessionId(), RefitAction.install(
                    candidateSlot(session.getRefitInventory().snapshotCandidates(), MODULE),
                    session.getEditHotbarSlot(), AttachmentType.valueOf("MODULE"))).success(), "working form converts");
            var replacement = item(gun("tacz:ak47"));
            backpack.getItem_MAP().put("primary", replacement);
            BackpackConfigRepository.save();
            var result = RefitActionService.execute(player, session.getSessionId(), RefitAction.save());
            helper.assertTrue(!result.success() && result.finished()
                            && result.message().getContents() instanceof TranslatableContents contents
                            && contents.getKey().endsWith("operation_wrong_gun"),
                    "a changed configured gun is rejected instead of inheriting the old session's derived identity");
            var stored = BackpackConfigRepository.loadOrCreate(files.backpackPath).getPlayerData()
                    .get(player.getStringUUID()).getBackpacks_MAP().get(1).getItem_MAP().get("primary");
            helper.assertTrue(replacement.getNbt().equals(stored.getNbt()), "rejected save preserves the replacement configuration");
        } finally { AttachmentEditSessionManager.endSession(player); }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "arcana-form"));
    }
    private static ItemStack gun(String id) {
        return GunItemBuilder.create().setId(new ResourceLocation(id)).setAmmoCount(7).setAmmoInBarrel(true).build();
    }
    private static ItemStack attachment(String id) {
        return TaczGatewayProvider.gateway().listAttachmentCandidates().stream()
                .filter(item -> id.equals(TaczGatewayProvider.gateway().resolveAttachmentId(item).orElse("")))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing required Vanguard attachment " + id));
    }
    private static String id(ItemStack gun) { return TaczGatewayProvider.gateway().resolveRefitGunId(gun).orElse(""); }
    private static BackpackConfig.Backpack.ItemData item(ItemStack gun) {
        return new BackpackConfig.Backpack.ItemData(gun.getItem().builtInRegistryHolder().key().location().toString(),
                1, gun.getTag().toString(), null);
    }
    private static List<String> directoryIds(List<ItemStack> items) {
        return items.stream().map(item -> TaczGatewayProvider.gateway().resolveAttachmentId(item).orElse("")).toList();
    }
    private static int candidateSlot(List<ItemStack> items, String id) {
        int index = directoryIds(items).indexOf(id);
        if (index < 0) throw new IllegalStateException("Required module is absent from the request catalog");
        return index + 9;
    }
}
