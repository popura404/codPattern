package com.cdp.codpattern.event;

import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.Entity;

@Mod.EventBusSubscriber(modid = "codpattern")
public final class RoomTerminationEvents {
    @SubscribeEvent
    public static void commands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("roomforceend")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(net.minecraft.commands.Commands.argument("map", com.mojang.brigadier.arguments.StringArgumentType.string())
                .executes(context -> {
                    var source = context.getSource();
                    var service = RoomTerminationService.get(source.getServer());
                    var room = com.cdp.codpattern.app.match.model.RoomId.of(
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mode"),
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "map"));
                    try {
                        var report = service.forceEnd(source, room, service.generation(room));
                        return report.outcome() == com.cdp.codpattern.app.match.runtime.termination.ForceEndCoordinator.Outcome.COMPLETED ? 1 : 0;
                    } catch (IllegalArgumentException failure) {
                        source.sendFailure(net.minecraft.network.chat.Component.translatable("message.codpattern.force_end.unknown", room.encode()));
                        return 0;
                    }
                }))));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RoomTerminationService.get(event.getServer()).tick();
            com.cdp.codpattern.app.match.management.MapDeletionCoordinator.get(event.getServer()).tick();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RoomTerminationService.get(player.server).recoverOnLogin(player);
    }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RoomTerminationService.get(player.server).recoverOnLogin(player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entityLoad(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        RoomTerminationService.current().ifPresent(service -> {
            if (service.entityMustBeReclaimed(event.getEntity())) {
                event.setCanceled(true);
                service.reclaimLoadedEntity(event.getEntity());
            }
        });
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressTerminatedEntityTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        RoomTerminationService.current().ifPresent(service -> {
            if (service.entityMustBeReclaimed(event.getEntity())) event.setCanceled(true);
        });
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressTerminatedEntityDamage(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        RoomTerminationService.current().ifPresent(service -> {
            Entity owner = event.getSource().getEntity();
            Entity direct = event.getSource().getDirectEntity();
            if (owner != null && service.entityMustBeReclaimed(owner)
                    || direct != null && service.entityMustBeReclaimed(direct)) event.setCanceled(true);
        });
    }
    @SubscribeEvent
    public static void entityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        var reason = event.getEntity().getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
            RoomTerminationService.current().ifPresent(service -> service.acknowledgeEntity(event.getEntity().getUUID()));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stop(ServerStoppingEvent event) { RoomTerminationService.close(event.getServer());
        com.cdp.codpattern.app.match.management.MapDeletionCoordinator.close(event.getServer()); }
}
