package com.cdp.codpattern.app.zombies.gametest;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.ModeObjectInteractionContext;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.app.zombies.service.ZombiesActiveSpawnGroupService;
import com.cdp.codpattern.app.zombies.service.ZombiesAmmoBoxService;
import com.cdp.codpattern.app.zombies.service.ZombiesArmorService;
import com.cdp.codpattern.app.zombies.service.ZombiesBarrierMovementService;
import com.cdp.codpattern.app.zombies.service.ZombiesBarrierService;
import com.cdp.codpattern.app.zombies.service.ZombiesBuffCombatService;
import com.cdp.codpattern.app.zombies.service.ZombiesBuffService;
import com.cdp.codpattern.app.zombies.service.ZombiesCrashRecoveryService;
import com.cdp.codpattern.app.zombies.service.ZombiesCleanupParticipant;
import com.cdp.codpattern.app.zombies.service.ZombiesCleanupService;
import com.cdp.codpattern.app.zombies.service.ZombiesEconomyService;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;
import com.cdp.codpattern.app.zombies.service.ZombiesMapOccupancyService;
import com.cdp.codpattern.app.zombies.service.ZombiesObjectInteractionService;
import com.cdp.codpattern.app.zombies.service.ZombiesObjectStateStore;
import com.cdp.codpattern.app.zombies.service.ZombiesPlayerStateService;
import com.cdp.codpattern.app.zombies.service.ZombiesPowerService;
import com.cdp.codpattern.app.zombies.service.ZombiesPowerSwitchBlockStateService;
import com.cdp.codpattern.app.zombies.service.ZombiesServiceResult;
import com.cdp.codpattern.app.zombies.service.ZombiesActiveMobCounter;
import com.cdp.codpattern.app.zombies.service.ZombiesUltimateMachineService;
import com.cdp.codpattern.app.zombies.service.ZombiesWeaponInventoryService;
import com.cdp.codpattern.app.zombies.service.ZombiesWeaponInstanceService;
import com.cdp.codpattern.app.zombies.service.ZombiesWeaponItemStackService;
import com.cdp.codpattern.common.block.CodPatternBlockRegister;
import com.cdp.codpattern.common.block.ZombiesPowerSwitchBlock;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(CodPatternConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombiesRuntimeGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String BATCH = "zombies_runtime_smoke";
    private static final String MAP_START_CLEANUP_BATCH = "zombies_map_start_cleanup";
    private static final String SERVER_START_CLEANUP_BATCH = "zombies_server_start_cleanup";

    private ZombiesRuntimeGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 20, required = false)
    public static void cleanupServiceRunsClearHooks(GameTestHelper helper) {
        RoomId roomId = roomId("cleanup-hooks");
        ModeEntityOwnershipRegistry.instance().clearRoom(roomId);
        ZombiesMapOccupancyService occupancyService = ZombiesMapOccupancyService.instance();
        occupancyService.forceRelease(roomId.gameType(), roomId.mapName());
        ZombiesServiceResult<Void> acquireResult = occupancyService.acquire(roomId);
        helper.assertTrue(acquireResult.success(), "GameTest setup should acquire the test map occupancy");

        TrackingCleanupHooks hooks = new TrackingCleanupHooks();
        ZombiesCleanupService cleanupService = new ZombiesCleanupService(
                ModeEntityOwnershipRegistry.instance(),
                occupancyService,
                hooks,
                List.of());

        ZombiesServiceResult<ZombiesCleanupService.CleanupSummary> cleanupResult =
                cleanupService.cleanup(roomId, "gametest", dimension -> null);

        helper.assertTrue(cleanupResult.success(), "cleanup should succeed");
        ZombiesCleanupService.CleanupSummary summary = cleanupResult.value().orElseThrow();
        helper.assertTrue(summary.occupancyReleased(), "cleanup should release map occupancy");
        helper.assertFalse(occupancyService.isOccupied(roomId), "test map should not stay occupied after cleanup");
        helper.assertTrue(hooks.clearHooksRan(), "cleanup should call every runtime clear hook");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 60, required = false)
    public static void cleanupServiceRemovesRegisteredRoomEntities(GameTestHelper helper) {
        RoomId roomId = roomId("cleanup-entities");
        RoomId otherRoomId = roomId("cleanup-entities-other");
        ModeEntityOwnershipRegistry ownershipRegistry = ModeEntityOwnershipRegistry.instance();
        ownershipRegistry.clearRoom(roomId);
        ownershipRegistry.clearRoom(otherRoomId);
        Zombie sameRoomZombie = null;
        Zombie otherRoomZombie = null;

        try {
            sameRoomZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.0D, 2.0D, 2.0D));
            otherRoomZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.0D, 2.0D, 2.0D));
            ownershipRegistry.register(roomId, sameRoomZombie);
            ownershipRegistry.register(otherRoomId, otherRoomZombie);

            ZombiesCleanupService cleanupService = new ZombiesCleanupService(
                    ownershipRegistry,
                    ZombiesMapOccupancyService.instance(),
                    ZombiesCleanupService.Hooks.noop(),
                    List.of());
            ZombiesServiceResult<ZombiesCleanupService.CleanupSummary> cleanupResult =
                    cleanupService.cleanup(roomId, "gametest.entities", dimension -> helper.getLevel());

            helper.assertTrue(cleanupResult.success(), "entity cleanup should succeed");
            ZombiesCleanupService.CleanupSummary summary = cleanupResult.value().orElseThrow();
            helper.assertTrue(summary.entities().registeredEntries() == 1, "cleanup should inspect one room entity");
            helper.assertTrue(summary.entities().removedEntities() == 1, "cleanup should remove the registered room entity");
            helper.assertTrue(summary.entities().missingEntities() == 0, "registered entity should be present in the level");
            helper.assertTrue(sameRoomZombie.isRemoved(), "same-room zombie should be removed");
            helper.assertFalse(otherRoomZombie.isRemoved(), "other-room zombie should not be removed");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(roomId).isEmpty(), "same-room ownership should be cleared");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(otherRoomId).size() == 1,
                    "other-room ownership should remain isolated");
        } finally {
            ownershipRegistry.clearRoom(roomId);
            ownershipRegistry.clearRoom(otherRoomId);
            if (sameRoomZombie != null) {
                sameRoomZombie.discard();
            }
            if (otherRoomZombie != null) {
                otherRoomZombie.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = MAP_START_CLEANUP_BATCH, timeoutTicks = 60, required = false)
    public static void mapStartCleanupRemovesOnlyMatchingZombiesRoomNpc(GameTestHelper helper) {
        RoomId targetRoom = roomId("map-start-cleanup-target");
        RoomId otherZombiesRoom = roomId("map-start-cleanup-other");
        RoomId otherModeRoom = RoomId.of(BuiltInGameModes.TEAM_DEATHMATCH, "map-start-cleanup-tdm");
        ModeEntityOwnershipRegistry ownershipRegistry = ModeEntityOwnershipRegistry.instance();
        ZombiesActiveMobCounter activeMobCounter = new ZombiesActiveMobCounter();
        Zombie targetZombie = null;
        Zombie otherZombiesNpc = null;
        Zombie otherModeNpc = null;

        try {
            targetZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.0D, 2.0D, 2.0D));
            otherZombiesNpc = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.0D, 2.0D, 2.0D));
            otherModeNpc = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(6.0D, 2.0D, 2.0D));
            ownershipRegistry.register(targetRoom, targetZombie);
            ownershipRegistry.register(otherZombiesRoom, otherZombiesNpc);
            ownershipRegistry.register(otherModeRoom, otherModeNpc);
            activeMobCounter.register(targetRoom, targetZombie.getUUID());
            activeMobCounter.register(otherZombiesRoom, otherZombiesNpc.getUUID());

            ZombiesCrashRecoveryService recoveryService = new ZombiesCrashRecoveryService(
                    ownershipRegistry,
                    new ZombiesMapOccupancyService(),
                    activeMobCounter);
            ZombiesCrashRecoveryService.ResidualEntityCleanupSummary summary =
                    recoveryService.cleanupResidualTaggedEntitiesForRoom(
                            helper.getLevel().getServer(),
                            targetRoom);

            helper.assertTrue(summary.removedEntities() == 1,
                    "map startup cleanup should remove exactly its matching zombies NPC");
            helper.assertTrue(targetZombie.isRemoved(), "matching zombies room NPC should be removed");
            helper.assertFalse(otherZombiesNpc.isRemoved(), "other zombies map NPC should remain");
            helper.assertFalse(otherModeNpc.isRemoved(), "non-zombies mode NPC should remain");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(targetRoom).isEmpty(),
                    "matching room ownership should be cleared");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(otherZombiesRoom).size() == 1,
                    "other zombies room ownership should remain");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(otherModeRoom).size() == 1,
                    "global ownership for another mode should remain");
            helper.assertTrue(activeMobCounter.roomCount(targetRoom) == 0,
                    "matching zombies active counter should be cleared");
            helper.assertTrue(activeMobCounter.roomCount(otherZombiesRoom) == 1,
                    "other zombies room active counter should remain");
        } finally {
            ownershipRegistry.clearRoom(targetRoom);
            ownershipRegistry.clearRoom(otherZombiesRoom);
            ownershipRegistry.clearRoom(otherModeRoom);
            activeMobCounter.clear();
            if (targetZombie != null) {
                targetZombie.discard();
            }
            if (otherZombiesNpc != null) {
                otherZombiesNpc.discard();
            }
            if (otherModeNpc != null) {
                otherModeNpc.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = SERVER_START_CLEANUP_BATCH, timeoutTicks = 60, required = false)
    public static void serverStartCleanupRemovesAllZombiesNpcButPreservesOtherModes(GameTestHelper helper) {
        RoomId firstZombiesRoom = roomId("server-start-cleanup-first");
        RoomId secondZombiesRoom = roomId("server-start-cleanup-second");
        RoomId otherModeRoom = RoomId.of(BuiltInGameModes.TEAM_DEATHMATCH, "server-start-cleanup-tdm");
        ModeEntityOwnershipRegistry ownershipRegistry = ModeEntityOwnershipRegistry.instance();
        ZombiesActiveMobCounter activeMobCounter = new ZombiesActiveMobCounter();
        ZombiesMapOccupancyService occupancyService = new ZombiesMapOccupancyService();
        Zombie firstZombie = null;
        Zombie secondZombie = null;
        Zombie otherModeNpc = null;

        try {
            firstZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.0D, 2.0D, 2.0D));
            secondZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.0D, 2.0D, 2.0D));
            otherModeNpc = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(6.0D, 2.0D, 2.0D));
            ownershipRegistry.register(firstZombiesRoom, firstZombie);
            ownershipRegistry.register(secondZombiesRoom, secondZombie);
            ownershipRegistry.register(otherModeRoom, otherModeNpc);
            activeMobCounter.register(firstZombiesRoom, firstZombie.getUUID());
            activeMobCounter.register(secondZombiesRoom, secondZombie.getUUID());
            occupancyService.acquire(firstZombiesRoom);
            occupancyService.acquire(secondZombiesRoom);

            ZombiesCrashRecoveryService recoveryService = new ZombiesCrashRecoveryService(
                    ownershipRegistry,
                    occupancyService,
                    activeMobCounter);
            ZombiesCrashRecoveryService.ServerStartupRecoverySummary summary =
                    recoveryService.recoverServerStartup(helper.getLevel().getServer());

            helper.assertTrue(summary.entities().removedEntities() == 2,
                    "server startup cleanup should remove every zombies-owned NPC in the test");
            helper.assertTrue(firstZombie.isRemoved(), "first zombies NPC should be removed on server startup");
            helper.assertTrue(secondZombie.isRemoved(), "second zombies NPC should be removed on server startup");
            helper.assertFalse(otherModeNpc.isRemoved(), "non-zombies mode NPC should survive server startup cleanup");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(firstZombiesRoom).isEmpty(),
                    "first zombies room ownership should be cleared");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(secondZombiesRoom).isEmpty(),
                    "second zombies room ownership should be cleared");
            helper.assertTrue(ownershipRegistry.entitiesInRoom(otherModeRoom).size() == 1,
                    "other mode ownership should remain in the global registry");
            helper.assertTrue(activeMobCounter.totalCount() == 0,
                    "server startup cleanup should clear zombies active counters");
            helper.assertFalse(occupancyService.isOccupied(firstZombiesRoom),
                    "server startup cleanup should clear zombies map occupancy");
            helper.assertFalse(occupancyService.isOccupied(secondZombiesRoom),
                    "server startup cleanup should clear every zombies map occupancy");
        } finally {
            ownershipRegistry.clearRoom(firstZombiesRoom);
            ownershipRegistry.clearRoom(secondZombiesRoom);
            ownershipRegistry.clearRoom(otherModeRoom);
            activeMobCounter.clear();
            occupancyService.clear();
            if (firstZombie != null) {
                firstZombie.discard();
            }
            if (secondZombie != null) {
                secondZombie.discard();
            }
            if (otherModeNpc != null) {
                otherModeNpc.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 20, required = false)
    public static void weaponInventoryTagsPrimaryStack(GameTestHelper helper) {
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();
        ZombiesWeaponInventoryService inventoryService = new ZombiesWeaponInventoryService(
                itemStackService,
                ignoredGunId -> new ItemStack(Items.CROSSBOW));
        RoomId roomId = roomId("weapon-tags");
        ZombiesWeaponInstanceState weapon = ZombiesWeaponInstanceState.primary(
                "tacz:m4a1",
                2,
                1.25D,
                210);

        ZombiesServiceResult<ZombiesWeaponInventoryService.PreparedWeaponStack> prepared =
                inventoryService.preparePurchasedPrimaryWeapon(roomId, weapon);

        helper.assertTrue(prepared.success(), "primary stack should prepare under Forge runtime");
        ItemStack stack = prepared.value().orElseThrow().itemStack();
        ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = itemStackService.readWeaponTags(stack)
                .value()
                .orElseThrow();
        helper.assertTrue(roomId.encode().equals(tag.roomId()), "prepared stack should be tagged for the room");
        helper.assertFalse(tag.instanceId().isBlank(), "prepared stack should have an instance id");
        helper.assertTrue(tag.slot() == ZombiesEquipmentSlot.PRIMARY, "prepared stack should be tagged as primary");
        helper.assertTrue("tacz:m4a1".equals(tag.gunId()), "prepared stack should preserve gun id");
        helper.assertTrue(tag.weaponLevel() == 2, "prepared stack should preserve weapon level");
        helper.assertTrue(tag.reserveAmmo() == 210, "prepared stack should start with full reserve ammo");
        helper.assertTrue(tag.maxReserveAmmo() == 210, "prepared stack should preserve max reserve ammo");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 20, required = false)
    public static void weaponInventoryRejectsWrongSlotSync(GameTestHelper helper) {
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();
        ZombiesWeaponInventoryService inventoryService = new ZombiesWeaponInventoryService(
                itemStackService,
                ignoredGunId -> new ItemStack(Items.CROSSBOW));
        RoomId roomId = roomId("weapon-sync");
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        ZombiesWeaponInstanceState starterWeapon = new ZombiesWeaponInstanceState(
                "tacz:glock_17",
                0,
                0,
                1.0D,
                7,
                84);
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> setup =
                itemStackService.writeWeaponTags(
                        stack,
                        roomId,
                        "starter-instance",
                        ZombiesEquipmentSlot.STARTER,
                        starterWeapon);
        helper.assertTrue(setup.success(), "starter tag setup should succeed");

        ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> sync =
                inventoryService.syncReserveAmmo(
                        stack,
                        roomId,
                        ZombiesEquipmentSlot.PRIMARY,
                        ZombiesWeaponInstanceState.primary("tacz:m4a1", 2, 1.25D, 210));

        helper.assertFalse(sync.success(), "syncing the wrong slot should fail");
        helper.assertTrue(
                ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON.equals(sync.code()),
                "wrong slot sync should return weapon.invalid_current_weapon");
        ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = itemStackService.readWeaponTags(stack)
                .value()
                .orElseThrow();
        helper.assertTrue("starter-instance".equals(tag.instanceId()), "failed sync should keep instance id");
        helper.assertTrue(tag.slot() == ZombiesEquipmentSlot.STARTER, "failed sync should keep starter slot");
        helper.assertTrue(tag.reserveAmmo() == 7, "failed sync should not change reserve ammo");
        helper.assertTrue(tag.maxReserveAmmo() == 84, "failed sync should not change max reserve ammo");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 20, required = false)
    public static void weaponItemStackDamageMultiplierIsSameRoomScoped(GameTestHelper helper) {
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();
        RoomId roomId = roomId("weapon-multiplier");
        RoomId otherRoomId = roomId("weapon-multiplier-other");
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        ZombiesWeaponInstanceState upgradedWeapon = new ZombiesWeaponInstanceState(
                "tacz:m4a1",
                3,
                2,
                1.50D,
                2.00D,
                90,
                180);

        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> write =
                itemStackService.writeWeaponTags(
                        stack,
                        roomId,
                        "upgraded-instance",
                        ZombiesEquipmentSlot.PRIMARY,
                        upgradedWeapon);

        helper.assertTrue(write.success(), "upgraded weapon tag setup should succeed");
        ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = itemStackService.readWeaponTags(stack)
                .value()
                .orElseThrow();
        helper.assertTrue(tag.weaponLevel() == 3, "real stack NBT should preserve weapon level");
        helper.assertTrue(tag.upgradeLevel() == 2, "real stack NBT should preserve upgrade level");
        helper.assertTrue(Math.abs(tag.levelDamageMultiplier() - 1.50D) < 0.0001D,
                "real stack NBT should preserve level multiplier");
        helper.assertTrue(Math.abs(tag.upgradeDamageMultiplier() - 2.00D) < 0.0001D,
                "real stack NBT should preserve upgrade multiplier");
        helper.assertTrue(Math.abs(itemStackService.damageMultiplier(tag) - 3.00D) < 0.0001D,
                "tag damage multiplier should combine level and upgrade multipliers");
        helper.assertTrue(Math.abs(itemStackService.sameRoomDamageMultiplier(stack, roomId) - 3.00D) < 0.0001D,
                "same-room weapon damage multiplier should apply");
        helper.assertTrue(Math.abs(itemStackService.sameRoomDamageMultiplier(stack, otherRoomId) - 1.00D) < 0.0001D,
                "other-room weapon damage multiplier should not apply");

        ItemStack copy = stack.copy();
        helper.assertTrue(itemStackService.isZombiesWeapon(copy), "copied real stack should retain zombies NBT");
        itemStackService.stripWeaponTags(copy);
        helper.assertFalse(itemStackService.isZombiesWeapon(copy), "stripped copy should no longer be a zombies weapon");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 40, required = false)
    public static void weaponInventoryAppliesPrimaryToRealPlayerInventoryAndReplacesOldPrimary(GameTestHelper helper) {
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();
        ZombiesWeaponInventoryService inventoryService = new ZombiesWeaponInventoryService(
                itemStackService,
                ignoredGunId -> new ItemStack(Items.CROSSBOW));
        RoomId roomId = roomId("weapon-player-inventory");
        ServerPlayer player = detachedServerPlayer(helper);
        player.getInventory().clearContent();

        try {
            ZombiesWeaponInstanceState oldPrimary =
                    ZombiesWeaponInstanceState.primary("tacz:old_primary", 1, 1.10D, 80);
            ZombiesServiceResult<ZombiesWeaponInventoryService.PreparedWeaponStack> oldPrepared =
                    inventoryService.preparePurchasedPrimaryWeapon(roomId, oldPrimary);
            helper.assertTrue(oldPrepared.success(), "old primary setup should prepare");
            ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> oldApply =
                    inventoryService.applyPreparedPrimaryWeapon(
                            player.getInventory(),
                            roomId,
                            oldPrepared.value().orElseThrow(),
                            oldPrimary);
            helper.assertTrue(oldApply.success(), "old primary setup should apply to player inventory");

            ZombiesWeaponInstanceState newPrimary =
                    ZombiesWeaponInstanceState.primary("tacz:new_primary", 2, 1.35D, 120);
            ZombiesServiceResult<ZombiesWeaponInventoryService.PreparedWeaponStack> newPrepared =
                    inventoryService.preparePurchasedPrimaryWeapon(roomId, newPrimary);
            helper.assertTrue(newPrepared.success(), "new primary should prepare");
            ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> newApply =
                    inventoryService.applyPreparedPrimaryWeapon(
                            player.getInventory(),
                            roomId,
                            newPrepared.value().orElseThrow(),
                            newPrimary);

            helper.assertTrue(newApply.success(), "new primary should apply to player inventory");
            helper.assertTrue(
                    newApply.value().orElseThrow().inventorySlot() == ZombiesEquipmentSlot.PRIMARY.defaultInventorySlot(),
                    "primary weapon should use the configured player inventory slot");
            ItemStack primaryStack = player.getInventory().getItem(ZombiesEquipmentSlot.PRIMARY.defaultInventorySlot());
            ZombiesWeaponItemStackService.ZombiesWeaponTagData currentTag =
                    itemStackService.readWeaponTags(primaryStack).value().orElseThrow();
            helper.assertTrue("tacz:new_primary".equals(currentTag.gunId()),
                    "player inventory primary slot should contain the replacement weapon");
            helper.assertTrue(countRoomPrimaryWeapons(player, itemStackService, roomId) == 1,
                    "replacing primary should remove the old same-room primary");
        } finally {
            player.getInventory().clearContent();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 40, required = false)
    public static void powerSwitchBlockStateServiceSetsAndResetsPoweredState(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        helper.setBlock(relativePos, CodPatternBlockRegister.ZOMBIES_POWER_SWITCH.get().defaultBlockState());
        ZombiesPowerSwitchData powerSwitch = new ZombiesPowerSwitchData(
                "gametest-power-switch",
                "codpattern:zombies_power_switch",
                0,
                helper.getLevel().dimension(),
                helper.absolutePos(relativePos),
                Optional.empty());
        ZombiesPowerSwitchBlockStateService blockStateService =
                new ZombiesPowerSwitchBlockStateService(dimension -> helper.getLevel().dimension().equals(dimension)
                        ? helper.getLevel()
                        : null);

        try {
            helper.assertBlockProperty(relativePos, ZombiesPowerSwitchBlock.POWERED, Boolean.FALSE);
            helper.assertTrue(
                    blockStateService.setPowered(Optional.of(powerSwitch), true),
                    "power switch service should power the placed block");
            helper.assertBlockProperty(relativePos, ZombiesPowerSwitchBlock.POWERED, Boolean.TRUE);
            helper.assertTrue(
                    blockStateService.setPowered(powerSwitch, false),
                    "cleanup reset should unpower the placed block");
            helper.assertBlockProperty(relativePos, ZombiesPowerSwitchBlock.POWERED, Boolean.FALSE);
        } finally {
            helper.setBlock(relativePos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 60, required = false)
    public static void reactiveExplosionDefaultExecutorDamagesOnlySameRoomMonsters(GameTestHelper helper) {
        RoomId roomId = roomId("reactive-explosion");
        RoomId otherRoomId = roomId("reactive-explosion-other");
        ModeEntityOwnershipRegistry ownershipRegistry = ModeEntityOwnershipRegistry.instance();
        ownershipRegistry.clearRoom(roomId);
        ownershipRegistry.clearRoom(otherRoomId);
        ServerPlayer triggerPlayer = detachedServerPlayer(helper);
        Zombie sameRoomZombie = null;
        Zombie otherRoomZombie = null;

        try {
            Vec3 triggerPos = helper.absoluteVec(new Vec3(2.0D, 2.0D, 2.0D));
            triggerPlayer.moveTo(triggerPos.x(), triggerPos.y(), triggerPos.z(), 0.0F, 0.0F);
            sameRoomZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.0D, 2.0D, 2.0D));
            otherRoomZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 2.0D));
            sameRoomZombie.setHealth(sameRoomZombie.getMaxHealth());
            otherRoomZombie.setHealth(otherRoomZombie.getMaxHealth());
            ownershipRegistry.register(roomId, sameRoomZombie);
            ownershipRegistry.register(otherRoomId, otherRoomZombie);

            ZombiesPlayerStateService playerStateService = new ZombiesPlayerStateService();
            UUID playerId = triggerPlayer.getUUID();
            playerStateService.getOrCreate(playerId)
                    .addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.REACTIVE_EXPLOSION));
            ZombiesBuffCombatService combatService =
                    new ZombiesBuffCombatService(roomId, playerStateService, ownershipRegistry);

            ZombiesBuffCombatService.DamageApplicationResult result =
                    combatService.applyRoomMonsterDamage(playerId, 12.0F, 40L, triggerPlayer);

            helper.assertTrue(result.roomMonsterDamage(), "same-room monster damage should be classified");
            ZombiesBuffCombatService.ExplosionResult explosion = result.explosionResult();
            helper.assertTrue(explosion.requested(), "reactive explosion buff should request an AOE");
            helper.assertTrue(explosion.aoeApplied(), "default executor should apply the AOE in Forge runtime");
            helper.assertTrue(
                    sameRoomZombie.getHealth() < sameRoomZombie.getMaxHealth(),
                    "same-room registered zombie should take reactive explosion damage");
            helper.assertTrue(
                    Float.compare(otherRoomZombie.getHealth(), otherRoomZombie.getMaxHealth()) == 0,
                    "other-room registered zombie should be isolated from reactive explosion damage");
        } finally {
            ownershipRegistry.clearRoom(roomId);
            ownershipRegistry.clearRoom(otherRoomId);
            if (sameRoomZombie != null) {
                sameRoomZombie.discard();
            }
            if (otherRoomZombie != null) {
                otherRoomZombie.discard();
            }
            triggerPlayer.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 20, required = false)
    public static void reactiveExplosionDefaultExecutorFailsWithoutServerPlayerContext(GameTestHelper helper) {
        RoomId roomId = roomId("reactive-explosion-missing-context");
        ZombiesPlayerStateService playerStateService = new ZombiesPlayerStateService();
        UUID playerId = UUID.randomUUID();
        playerStateService.getOrCreate(playerId)
                .addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.REACTIVE_EXPLOSION));
        ZombiesBuffCombatService combatService =
                new ZombiesBuffCombatService(roomId, playerStateService, ModeEntityOwnershipRegistry.instance());

        ZombiesBuffCombatService.DamageApplicationResult result =
                combatService.applyRoomMonsterDamage(playerId, 12.0F, 40L);

        ZombiesBuffCombatService.ExplosionResult explosion = result.explosionResult();
        helper.assertTrue(explosion.requested(), "reactive explosion should be requested for buffed player");
        helper.assertFalse(explosion.aoeApplied(), "default executor should not apply AOE without ServerPlayer");
        helper.assertTrue(
                explosion.status().contains("missing_server_player"),
                "missing ServerPlayer context should be reported");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 20, required = false)
    public static void barrierMovementFallbackPushesOutsideActiveArea(GameTestHelper helper) {
        ZombiesBarrierMovementService movementService = new ZombiesBarrierMovementService();
        ZombiesBarrierData barrier = new ZombiesBarrierData(
                "gametest-barrier-fallback",
                2,
                750,
                true,
                helper.getLevel().dimension(),
                new BlockPos(5, 64, 5),
                new BlockPos(5, 66, 7),
                new BlockPos(5, 65, 5));
        ZombiesBarrierMovementService.PositionSample current =
                new ZombiesBarrierMovementService.PositionSample(
                        helper.getLevel().dimension(),
                        5.30D,
                        65.0D,
                        6.0D,
                        0.0F,
                        0.0F);

        ZombiesBarrierMovementService.MovementDecision blocked = movementService.decideMovement(
                current,
                ZombiesGamePhase.WAVE_ACTIVE,
                List.of(barrier),
                ignored -> false,
                true,
                Optional.empty());

        helper.assertTrue(blocked.eligible(), "alive member in active wave should be eligible for barrier checks");
        helper.assertTrue(blocked.blocked(), "position inside active barrier should be blocked");
        helper.assertTrue(blocked.target().isPresent(), "blocked movement should have a fallback target");
        helper.assertTrue(
                "gametest-barrier-fallback".equals(blocked.barrierObjectId()),
                "decision should expose the blocking barrier object id");

        ZombiesBarrierMovementService.MovementDecision fallbackCheck = movementService.decideMovement(
                blocked.target().orElseThrow().toPositionSample(),
                ZombiesGamePhase.WAVE_ACTIVE,
                List.of(barrier),
                ignored -> false,
                true,
                Optional.empty());
        helper.assertTrue(fallbackCheck.eligible(), "fallback target should remain eligible for checks");
        helper.assertFalse(fallbackCheck.blocked(), "fallback target should be outside the active barrier area");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 40, required = false)
    public static void objectInteractionPowerSwitchPowersPlacedBlockAndDeductsPoints(GameTestHelper helper) {
        RoomId roomId = roomId("object-power-interact");
        BlockPos relativePos = new BlockPos(1, 1, 1);
        BlockPos absolutePos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos, CodPatternBlockRegister.ZOMBIES_POWER_SWITCH.get().defaultBlockState());
        ServerPlayer player = silentDetachedServerPlayer(helper);

        try {
            Vec3 playerPos = helper.absoluteVec(new Vec3(1.5D, 1.0D, 1.5D));
            player.moveTo(playerPos.x(), playerPos.y(), playerPos.z(), 0.0F, 0.0F);
            ZombiesPowerSwitchData powerSwitch = new ZombiesPowerSwitchData(
                    "gametest-object-power-switch",
                    "codpattern:zombies_power_switch",
                    300,
                    helper.getLevel().dimension(),
                    absolutePos,
                    Optional.empty());
            ZombiesPlayerStateService playerStateService = new ZombiesPlayerStateService();
            ZombiesEconomyService economyService = new ZombiesEconomyService(playerStateService);
            economyService.addPoints(player.getUUID(), 500.0D);
            ZombiesPowerSwitchBlockStateService blockStateService =
                    new ZombiesPowerSwitchBlockStateService(dimension -> helper.getLevel().dimension().equals(dimension)
                            ? helper.getLevel()
                            : null);
            ZombiesPowerService powerService = new ZombiesPowerService(
                    economyService,
                    powered -> blockStateService.setPowered(powerSwitch, powered));
            ZombiesObjectStateStore objectStateStore = new ZombiesObjectStateStore(powerService::isPowerOn);
            objectStateStore.resetObjects(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Optional.of(powerSwitch),
                    List.of(),
                    List.of(),
                    1,
                    1);
            ZombiesObjectInteractionService interactionService = objectInteractionService(
                    roomId,
                    playerStateService,
                    economyService,
                    powerService,
                    objectStateStore,
                    new ZombiesWeaponInventoryService(new ZombiesWeaponItemStackService(), ignored -> new ItemStack(Items.CROSSBOW)),
                    List.of(),
                    Optional.of(powerSwitch));

            InteractionResult result = interactionService.interact(
                    player,
                    new ModeObjectInteractionContext(
                            roomId,
                            InteractionHand.MAIN_HAND,
                            absolutePos,
                            Direction.UP,
                            null,
                            ItemStack.EMPTY));

            helper.assertTrue(result == InteractionResult.SUCCESS, "power switch interaction should succeed");
            helper.assertTrue(powerService.isPowerOn(), "server power state should be on after interaction");
            helper.assertTrue(
                    playerStateService.get(player.getUUID()).orElseThrow().displayPoints() == 200,
                    "power interaction should deduct the configured cost once");
            helper.assertBlockProperty(relativePos, ZombiesPowerSwitchBlock.POWERED, Boolean.TRUE);
            helper.assertTrue(
                    interactionService.objectStatesForClient(player)
                            .stream()
                            .anyMatch(state -> "gametest-object-power-switch".equals(state.objectKey())
                                    && state.revision() > 0L
                                    && state.payload().getBoolean("powerOn")),
                    "object state should expose powered runtime state and bumped revision");
        } finally {
            helper.setBlock(relativePos, Blocks.AIR.defaultBlockState());
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 40, required = false)
    public static void objectInteractionAmmoBoxSyncsTaggedPrimaryStackInPlayerInventory(GameTestHelper helper) {
        RoomId roomId = roomId("object-ammo-interact");
        BlockPos relativePos = new BlockPos(2, 1, 1);
        BlockPos absolutePos = helper.absolutePos(relativePos);
        ServerPlayer player = silentDetachedServerPlayer(helper);
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();

        try {
            Vec3 playerPos = helper.absoluteVec(new Vec3(2.5D, 1.0D, 1.5D));
            player.moveTo(playerPos.x(), playerPos.y(), playerPos.z(), 0.0F, 0.0F);
            player.getInventory().clearContent();
            player.getInventory().selected = ZombiesEquipmentSlot.PRIMARY.defaultInventorySlot();

            ZombiesPlayerStateService playerStateService = new ZombiesPlayerStateService();
            ZombiesEconomyService economyService = new ZombiesEconomyService(playerStateService);
            economyService.addPoints(player.getUUID(), 1_000.0D);
            ZombiesWeaponInstanceState lowReservePrimary = new ZombiesWeaponInstanceState(
                    "tacz:ammo_primary",
                    2,
                    0,
                    1.25D,
                    12,
                    120);
            playerStateService.getOrCreate(player.getUUID()).setPrimaryWeapon(lowReservePrimary);
            ItemStack primaryStack = new ItemStack(Items.CROSSBOW);
            ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagSetup =
                    itemStackService.writeWeaponTags(
                            primaryStack,
                            roomId,
                            "ammo-primary-instance",
                            ZombiesEquipmentSlot.PRIMARY,
                            lowReservePrimary);
            helper.assertTrue(tagSetup.success(), "primary weapon tag setup should succeed");
            player.getInventory().setItem(ZombiesEquipmentSlot.PRIMARY.defaultInventorySlot(), primaryStack);

            ZombiesAmmoBoxData ammoBox = new ZombiesAmmoBoxData(
                    "gametest-ammo-box",
                    Map.of("2", 350),
                    helper.getLevel().dimension(),
                    absolutePos,
                    Optional.empty());
            ZombiesPowerService powerService = new ZombiesPowerService(economyService);
            ZombiesObjectStateStore objectStateStore = new ZombiesObjectStateStore(powerService::isPowerOn);
            objectStateStore.resetObjects(List.of(), List.of(), List.of(ammoBox), List.of());
            ZombiesObjectInteractionService interactionService = objectInteractionService(
                    roomId,
                    playerStateService,
                    economyService,
                    powerService,
                    objectStateStore,
                    new ZombiesWeaponInventoryService(itemStackService, ignored -> new ItemStack(Items.CROSSBOW)),
                    List.of(ammoBox),
                    Optional.empty());

            InteractionResult result = interactionService.interact(
                    player,
                    new ModeObjectInteractionContext(
                            roomId,
                            InteractionHand.MAIN_HAND,
                            absolutePos,
                            Direction.UP,
                            null,
                            player.getMainHandItem()));

            helper.assertTrue(result == InteractionResult.SUCCESS, "ammo box interaction should succeed");
            helper.assertTrue(
                    playerStateService.get(player.getUUID()).orElseThrow().displayPoints() == 650,
                    "ammo box interaction should deduct the configured weapon-level price");
            ZombiesWeaponInstanceState refilledState = playerStateService.get(player.getUUID())
                    .flatMap(state -> state.primaryWeapon())
                    .orElseThrow();
            helper.assertTrue(refilledState.reserveAmmo() == 120, "runtime primary reserve should be refilled");
            ItemStack syncedStack = player.getInventory().getItem(ZombiesEquipmentSlot.PRIMARY.defaultInventorySlot());
            ZombiesWeaponItemStackService.ZombiesWeaponTagData syncedTag = itemStackService.readWeaponTags(syncedStack)
                    .value()
                    .orElseThrow();
            helper.assertTrue("ammo-primary-instance".equals(syncedTag.instanceId()),
                    "ammo sync should preserve the tagged weapon instance id");
            helper.assertTrue(syncedTag.reserveAmmo() == 120, "real primary stack NBT reserve should be refilled");
            helper.assertTrue(syncedTag.maxReserveAmmo() == 120, "real primary stack NBT max reserve should be preserved");
            helper.assertTrue(
                    interactionService.objectStatesForClient(player)
                            .stream()
                            .anyMatch(state -> "gametest-ammo-box".equals(state.objectKey()) && state.revision() > 0L),
                    "ammo box object state revision should advance after use");
        } finally {
            player.getInventory().clearContent();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 40, required = false)
    public static void barrierMovementEnforceTeleportsRealPlayerToLastLegalPosition(GameTestHelper helper) {
        ZombiesBarrierMovementService movementService = new ZombiesBarrierMovementService();
        ServerPlayer player = silentDetachedServerPlayer(helper);
        BlockPos areaFrom = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos areaTo = helper.absolutePos(new BlockPos(4, 3, 6));
        Vec3 legalPosition = helper.absoluteVec(new Vec3(2.0D, 2.0D, 5.0D));
        Vec3 blockedPosition = helper.absoluteVec(new Vec3(4.5D, 2.0D, 5.0D));
        ZombiesBarrierData barrier = new ZombiesBarrierData(
                "gametest-barrier-enforce",
                3,
                750,
                true,
                helper.getLevel().dimension(),
                areaFrom,
                areaTo,
                helper.absolutePos(new BlockPos(4, 2, 5)));

        try {
            player.moveTo(legalPosition.x(), legalPosition.y(), legalPosition.z(), 0.0F, 0.0F);
            ZombiesBarrierMovementService.EnforcementResult allowed = movementService.enforce(
                    player,
                    ZombiesGamePhase.WAVE_ACTIVE,
                    List.of(barrier),
                    ignored -> false,
                    player.getUUID()::equals);
            helper.assertTrue(
                    allowed == ZombiesBarrierMovementService.EnforcementResult.ALLOWED,
                    "first legal sample should be allowed and remembered");

            player.moveTo(blockedPosition.x(), blockedPosition.y(), blockedPosition.z(), 0.0F, 0.0F);
            ZombiesBarrierMovementService.EnforcementResult blocked = movementService.enforce(
                    player,
                    ZombiesGamePhase.WAVE_ACTIVE,
                    List.of(barrier),
                    ignored -> false,
                    player.getUUID()::equals);

            helper.assertTrue(
                    blocked == ZombiesBarrierMovementService.EnforcementResult.BLOCKED_RESTORED,
                    "blocked real player should be restored to the last legal position");
            helper.assertTrue(
                    distanceToSqr(player.position(), legalPosition) < 0.0001D,
                    "real player should be teleported back to the previous legal position");
        } finally {
            movementService.clear(player.getUUID());
            player.discard();
        }
        helper.succeed();
    }

    private static RoomId roomId(String mapName) {
        return RoomId.of(BuiltInGameModes.ZOMBIES, "gametest-" + mapName);
    }

    private static ZombiesObjectInteractionService objectInteractionService(
            RoomId roomId,
            ZombiesPlayerStateService playerStateService,
            ZombiesEconomyService economyService,
            ZombiesPowerService powerService,
            ZombiesObjectStateStore objectStateStore,
            ZombiesWeaponInventoryService weaponInventoryService,
            Collection<ZombiesAmmoBoxData> ammoBoxes,
            Optional<ZombiesPowerSwitchData> powerSwitch
    ) {
        return new ZombiesObjectInteractionService(
                roomId,
                () -> List.of(),
                () -> List.of(),
                () -> ammoBoxes == null ? List.of() : ammoBoxes,
                () -> List.of(),
                () -> powerSwitch == null ? Optional.empty() : powerSwitch,
                () -> List.of(),
                () -> List.of(),
                new ZombiesBarrierService(
                        roomId,
                        () -> List.of(),
                        economyService,
                        objectStateStore,
                        new ZombiesActiveSpawnGroupService(),
                        ignored -> true,
                        () -> ZombiesGamePhase.WAVE_ACTIVE),
                new ZombiesWeaponInstanceService(economyService),
                new ZombiesAmmoBoxService(economyService),
                new ZombiesArmorService(economyService),
                powerService,
                new ZombiesBuffService(economyService, powerService),
                new ZombiesUltimateMachineService(economyService, powerService),
                weaponInventoryService,
                objectStateStore);
    }

    private static ServerPlayer detachedServerPlayer(GameTestHelper helper) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-detached-player"));
    }

    private static ServerPlayer silentDetachedServerPlayer(GameTestHelper helper) {
        return new SilentDetachedServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-silent-player"));
    }

    private static int countRoomPrimaryWeapons(
            ServerPlayer player,
            ZombiesWeaponItemStackService itemStackService,
            RoomId roomId
    ) {
        int count = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            ItemStack stack = player.getInventory().getItem(index);
            Optional<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tag =
                    itemStackService.readWeaponTags(stack).value();
            if (tag.isPresent()
                    && tag.get().slot() == ZombiesEquipmentSlot.PRIMARY
                    && roomId.encode().equals(tag.get().roomId())) {
                count++;
            }
        }
        return count;
    }

    private static double distanceToSqr(Vec3 left, Vec3 right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        double dz = left.z() - right.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class SilentDetachedServerPlayer extends ServerPlayer {
        private SilentDetachedServerPlayer(
                net.minecraft.server.MinecraftServer server,
                ServerLevel level,
                GameProfile profile
        ) {
            super(server, level, profile);
            this.connection = new SilentPacketListener(server, this);
        }

        @Override
        public void sendSystemMessage(Component message) {
            // GameTest detached players do not have a live network channel.
        }

        @Override
        public void sendSystemMessage(Component message, boolean actionBar) {
            // GameTest detached players do not have a live network channel.
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            // GameTest detached players do not have a live network channel.
        }

        @Override
        public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            moveTo(x, y, z, yaw, pitch);
        }

        @Override
        public boolean teleportTo(
                ServerLevel level,
                double x,
                double y,
                double z,
                Set<net.minecraft.world.entity.RelativeMovement> relativeMovements,
                float yaw,
                float pitch
        ) {
            moveTo(x, y, z, yaw, pitch);
            return true;
        }
    }

    private static final class SilentPacketListener extends ServerGamePacketListenerImpl {
        private SilentPacketListener(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player);
        }

        @Override
        public void send(Packet<?> packet) {
            // GameTest detached players do not have a live network channel.
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {
            // GameTest detached players do not have a live network channel.
        }

        @Override
        public void teleport(double x, double y, double z, float yaw, float pitch) {
            player.moveTo(x, y, z, yaw, pitch);
        }

        @Override
        public void teleport(
                double x,
                double y,
                double z,
                float yaw,
                float pitch,
                Set<net.minecraft.world.entity.RelativeMovement> relativeMovements
        ) {
            player.moveTo(x, y, z, yaw, pitch);
        }
    }

    private static final class TrackingCleanupHooks implements ZombiesCleanupService.Hooks {
        private boolean objectRuntimeCleared;
        private boolean playerRuntimeCleared;
        private boolean readyStateCleared;
        private boolean startVoteCleared;
        private boolean lifecycleRuntimeCleared;
        private boolean hudStateCleared;

        @Override
        public void clearObjectRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            objectRuntimeCleared = true;
        }

        @Override
        public void clearPlayerRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            playerRuntimeCleared = true;
        }

        @Override
        public void clearReadyState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            readyStateCleared = true;
        }

        @Override
        public void clearStartVote(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            startVoteCleared = true;
        }

        @Override
        public void clearLifecycleRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            lifecycleRuntimeCleared = true;
        }

        @Override
        public void clearHudState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            hudStateCleared = true;
        }

        private boolean clearHooksRan() {
            return objectRuntimeCleared
                    && playerRuntimeCleared
                    && readyStateCleared
                    && startVoteCleared
                    && lifecycleRuntimeCleared
                    && hudStateCleared;
        }
    }
}
