package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.ModeObjectInteractionContext;
import com.cdp.codpattern.app.match.model.ModeObjectState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.List;

public interface ModeInteractableObjectPort extends ModeRoomIdentityPort {
    InteractionResult interact(ServerPlayer player, ModeObjectInteractionContext context);

    List<ModeObjectState> objectStatesForClient(ServerPlayer player);
}
