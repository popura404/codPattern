package com.cdp.codpattern.mixin.tacz;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.network.SyncAttachmentCandidatesPacket;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientMessageRefitGun.class, remap = false)
public abstract class ClientMessageRefitGunMixin {
    @Redirect(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"
            )
    )
    private static Inventory codpattern$useRefitInventory(ServerPlayer player) {
        Inventory refitInventory = AttachmentEditSessionManager.getRefitInventory(player);
        return refitInventory == null ? player.getInventory() : refitInventory;
    }

    @Inject(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/network/NetworkHandler;sendToClientPlayer(Ljava/lang/Object;Lnet/minecraft/world/entity/player/Player;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private static void codpattern$syncCandidatesBeforeRefresh(NetworkEvent.Context context,
            ClientMessageRefitGun message,
            CallbackInfo ci) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        AttachmentEditSession session = AttachmentEditSessionManager.getSession(player.getUUID());
        if (session == null) {
            return;
        }
        ModNetworkChannel.sendToPlayer(new SyncAttachmentCandidatesPacket(
                session.getBagId(),
                session.getSlot(),
                session.snapshotAttachmentCandidates()), player);
    }
}
