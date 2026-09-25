package com.cdp.codpattern.app.match.gametest;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.termination.*;
import com.cdp.codpattern.app.tdm.service.WarmupMovementLockService;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("codpattern")
@PrefixGameTestTemplate(false)
public final class RoomTerminationGameTests {
    private RoomTerminationGameTests() { }

    @GameTest(template = "empty", batch = "room_termination", timeoutTicks = 100)
    public static void recoverySurvivesModeAndCustomFailures(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(helper);
        BlockPos landing = helper.absolutePos(new BlockPos(0, 2, 0));
        level.setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(landing, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(landing.above(), Blocks.AIR.defaultBlockState());
        player.moveTo(landing.getX()+.5, landing.getY(), landing.getZ()+.5, 0, 0);
        player.setGameMode(GameType.ADVENTURE);
        var state = new ForceEndCoordinator.State();
        var room = RoomId.of("fixture", "player-recovery");
        var record = PlayerRecoveryRecord.capture(player, room.encode(), state.generation);
        record.armed = true; record.recoveryPending = true; record.clearInventory = true;
        record.endTarget = new PlayerRecoveryRecord.Target("fixture:missing_dimension",0,0,0,0,0);
        record.customActions.put("fixture:unavailable", "");
        var own = new AttributeModifier(UUID.randomUUID(), "fixture-owned", .2, AttributeModifier.Operation.ADDITION);
        var foreign = new AttributeModifier(UUID.randomUUID(), "other-mod", .1, AttributeModifier.Operation.ADDITION);
        record.attributes.put(own.getId().toString(), new PlayerRecoveryRecord.AttributeUndo("minecraft:generic.movement_speed", own.getName(), own.getAmount(), own.getOperation().toValue()));
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        speed.addTransientModifier(own); speed.addTransientModifier(foreign);
        WarmupMovementLockService.lock(player);
        player.setGameMode(GameType.SPECTATOR);
        player.moveTo(landing.getX()+5, landing.getY(), landing.getZ()+5, 0, 0);
        player.getInventory().setItem(0, new ItemStack(Items.STICK));
        var executor = new PlayerRecoveryExecutor(level.getServer(), () -> { });
        var handler = new ModeForceEndHandler() {
            public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> stop(ForceEndContext c) { throw new IllegalStateException("injected stop"); }
            public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> cleanup(ForceEndContext c) { throw new IllegalStateException("injected cleanup"); }
        };
        new ForceEndCoordinator().execute(state, new ForceEndContext(room, state.generation, UUID.randomUUID(), "console", "ADMIN_FORCE_END"),
                handler, () -> { }, () -> executor.recover(record, player), () -> { });
        helper.assertTrue(state.terminated && !state.complete, "broken mode must remain terminated");
        helper.assertTrue(player.gameMode.getGameModeForPlayer()==GameType.ADVENTURE, "main executor restores spectator mode independently");
        helper.assertTrue(!player.noPhysics && player.getInventory().isEmpty(), "basic control and equipment recovery must run");
        helper.assertTrue(player.position().distanceToSqr(landing.getX()+.5,landing.getY(),landing.getZ()+.5)<.01, "missing end dimension must use verified return location");
        helper.assertTrue(speed.getModifier(own.getId())==null && speed.getModifier(foreign.getId())!=null, "only owned attribute is removed");
        helper.assertTrue(!record.recovered && record.failures.containsKey("custom:fixture:unavailable"), "missing addon action remains pending");
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
        executor.recover(record, player);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND), "retry must not repeat completed equipment cleanup");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "room_termination", timeoutTicks = 100)
    public static void consoleTerminationCancelsCountdownAndReclaimsOwnedEntity(GameTestHelper helper) {
        var level = helper.getLevel();
        String name = "force-end-" + UUID.randomUUID().toString().substring(0,8);
        var map = new CodTdmMap(level, name, new AreaData(BlockPos.ZERO, new BlockPos(10,10,10)));
        FPSMCore.getInstance().registerMap(map.getGameType(),map);
        var service = RoomTerminationService.get(level.getServer());
        var room = RoomTerminationService.id(map);
        try {
            map.startGame();
            var generation=service.generation(room);
            try {
                service.forceEnd(level.getServer().createCommandSourceStack().withPermission(0),room,generation);
                throw new AssertionError("Unauthorized request accepted");
            } catch (SecurityException expected) { }
            helper.assertFalse(service.terminated(room), "unauthorized request cannot terminate a match");
            boolean[] oldTaskRan = {false};
            Runnable oldTask = service.guard(room, () -> oldTaskRan[0]=true);
            var entity = EntityType.ARMOR_STAND.create(level);
            entity.moveTo(helper.absolutePos(BlockPos.ZERO).getCenter());
            com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry.instance().register(room,entity);
            level.addFreshEntity(entity);
            var delayed = EntityType.ARMOR_STAND.create(level);
            delayed.moveTo(helper.absolutePos(new BlockPos(0,1,0)).getCenter());
            com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry.instance().register(room, delayed);
            var retryRemoval = new net.minecraft.world.entity.decoration.ArmorStand(EntityType.ARMOR_STAND, level) {
                private boolean failOnce = true;
                @Override public void remove(RemovalReason reason) {
                    if (failOnce) { failOnce = false; throw new IllegalStateException("injected entity removal failure"); }
                    super.remove(reason);
                }
            };
            retryRemoval.moveTo(helper.absolutePos(new BlockPos(1,1,0)).getCenter());
            com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry.instance().register(room, retryRemoval);
            var unrelated = EntityType.ARMOR_STAND.create(level);
            unrelated.moveTo(helper.absolutePos(new BlockPos(0,2,0)).getCenter());
            level.addFreshEntity(unrelated);
            var pending=service.forceEnd(level.getServer().createCommandSourceStack(),room,generation);
            helper.assertTrue(pending.outcome()==ForceEndCoordinator.Outcome.PENDING && service.hasLease(room), "unloaded entity keeps occupancy and retry evidence");
            helper.assertFalse(unrelated.isRemoved(), "unregistered entities remain untouched");
            var tick = new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(delayed);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(tick);
            helper.assertTrue(tick.isCanceled(), "pending entity cannot keep ticking after termination");
            var attack = new net.minecraftforge.event.entity.living.LivingAttackEvent(unrelated,
                    level.damageSources().mobAttack(delayed), 1);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(attack);
            helper.assertTrue(attack.isCanceled(), "pending entity cannot damage players or other entities");
            level.addFreshEntity(delayed);
            helper.assertTrue(delayed.isRemoved(), "late entity load is reclaimed before it can act");
            helper.assertFalse(level.addFreshEntity(retryRemoval), "failed late removal must still cancel entity admission");
            helper.assertTrue(!retryRemoval.isRemoved() && service.entityMustBeReclaimed(retryRemoval),
                    "removal failure must retain retry evidence without aborting entity loading");
            service.reclaimLoadedEntity(retryRemoval);
            helper.assertTrue(retryRemoval.isRemoved(), "later removal retry must complete");
            unrelated.discard();
            var result=service.forceEnd(level.getServer().createCommandSourceStack(),room,generation);
            helper.assertTrue(result.outcome()==ForceEndCoordinator.Outcome.COMPLETED, "console can terminate countdown: "+result.failures());
            helper.assertTrue(entity.isRemoved() && !service.hasLease(room), "owned entity and lease must be reclaimed");
            oldTask.run(); helper.assertFalse(oldTaskRan[0], "old-generation task must be isolated");
            map.startGame(); var newer=service.generation(room);
            helper.assertTrue(!newer.equals(generation), "new match has a new generation");
            helper.assertTrue(service.forceEnd(level.getServer().createCommandSourceStack(),room,generation).outcome()==ForceEndCoordinator.Outcome.STALE, "old admin request cannot end new match");
            helper.assertFalse(service.releaseLease(room,generation), "old cleanup cannot release new occupancy");
            service.forceEnd(level.getServer().createCommandSourceStack(),room,newer);
            helper.succeed();
        } finally { FPSMCore.getInstance().unregisterMap(map); }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "recovery-test"));
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { }
            @Override public void teleport(double x,double y,double z,float yaw,float pitch) { player.moveTo(x,y,z,yaw,pitch); }
            @Override public void teleport(double x,double y,double z,float yaw,float pitch, Set<RelativeMovement> flags) { player.moveTo(x,y,z,yaw,pitch); }
        };
        return player;
    }
}
