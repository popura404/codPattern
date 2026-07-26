package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.ModeCapability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Diagnostic-only validation for capabilities with a characterized direct room-port mapping. */
public final class ModeRoomHandleCapabilityAudit {
    private ModeRoomHandleCapabilityAudit() {
    }

    public static List<MissingPort> findMissingPorts(
            GameModeDefinition definition,
            ModeRoomHandle handle
    ) {
        Objects.requireNonNull(definition, "definition");
        return findMissingPorts(definition.capabilities(), handle);
    }

    public static List<MissingPort> findMissingPorts(
            Set<ModeCapability> capabilities,
            ModeRoomHandle handle
    ) {
        Objects.requireNonNull(handle, "handle");
        Set<ModeCapability> declared = capabilities == null ? Set.of() : capabilities;
        List<MissingPort> missing = new ArrayList<>();
        requirePort(declared, ModeCapability.TEAM_SELECTION, handle.teamPort().isPresent(), "teamPort", missing);
        requirePort(declared, ModeCapability.TEAM_BALANCE, handle.teamPort().isPresent(), "teamPort", missing);
        requirePort(declared, ModeCapability.READY_STATE, handle.readyPort().isPresent(), "readyPort", missing);
        requirePort(declared, ModeCapability.START_VOTE, handle.votePort().isPresent(), "votePort", missing);
        requirePort(declared, ModeCapability.END_VOTE, handle.votePort().isPresent(), "votePort", missing);
        return List.copyOf(missing);
    }

    private static void requirePort(
            Set<ModeCapability> capabilities,
            ModeCapability capability,
            boolean portPresent,
            String portName,
            List<MissingPort> missing
    ) {
        if (capabilities.contains(capability) && !portPresent) {
            missing.add(new MissingPort(capability, portName));
        }
    }

    public record MissingPort(ModeCapability capability, String portName) {
        public MissingPort {
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(portName, "portName");
        }
    }
}
