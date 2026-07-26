package com.cdp.codpattern.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executable source-level characterization of packet authorization, threading, and recipients. */
public final class ModeSplitPacketSendRouteCompatTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path REGISTRATION =
            Path.of("docs/mode-split/phase0/packet-registration-baseline.tsv");
    private static final Path ROUTES =
            Path.of("docs/mode-split/phase0/packet-send-route-baseline.tsv");

    private ModeSplitPacketSendRouteCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, PacketRegistration> packets = readPacketRegistrations();
        verifyEveryPacketHandlerThreadContract(packets);
        verifyReviewableRouteManifest(packets);
        verifyRoomListRoutes();
        verifyDispatchRoutes();
        verifyTdmRosterRoutes();
        verifyZombiesRosterRoutes();
        System.out.println("PASS mode split packet send-route baseline: 58 handler contracts, 12 recipient routes");
    }

    private static void verifyEveryPacketHandlerThreadContract(Map<String, PacketRegistration> packets)
            throws IOException {
        require(packets.size() == 58, "packet registration baseline must contain 58 classes");
        long c2s = 0;
        for (PacketRegistration packet : packets.values()) {
            String source = source(packet.className());
            require(source.contains("enqueueWork("),
                    packet.className() + " must continue scheduling handler work");
            require(source.contains("setPacketHandled(true)"),
                    packet.className() + " must continue marking the packet handled");
            if (packet.direction().equals("C2S")) {
                c2s++;
                require(source.contains("getSender()"),
                        packet.className() + " C2S handler must derive authority from the network sender");
            }
        }
        require(c2s == 28, "C2S handler count must remain 28");
    }

    private static void verifyReviewableRouteManifest(Map<String, PacketRegistration> packets) throws IOException {
        List<String> rows = dataLines(ROUTES);
        require(rows.size() == 12, "send-route baseline must contain 12 characterized routes");
        List<String> expectedIds = List.of(
                "room-list-request", "room-list-subscribe", "room-list-unsubscribe",
                "roster-resync-dispatch", "roster-preview-dispatch",
                "tdm-roster-full", "tdm-roster-delta", "tdm-roster-resync", "tdm-roster-preview",
                "zombies-roster-full", "zombies-roster-resync", "zombies-roster-preview");
        List<String> actualIds = new ArrayList<>();
        for (String row : rows) {
            String[] fields = row.split("\\t", -1);
            require(fields.length == 7, "invalid send-route row: " + row);
            actualIds.add(fields[0]);
            PacketRegistration packet = packets.get(fields[1]);
            require(packet != null, "route references an unregistered packet: " + fields[1]);
            require(packet.direction().equals(fields[2]),
                    "route direction differs from packet registration for " + fields[0]);
            for (int i = 3; i < fields.length; i++) {
                require(!fields[i].isBlank(), "blank send-route contract field for " + fields[0]);
            }
        }
        require(actualIds.equals(expectedIds), "send-route review order drifted: " + actualIds);
    }

    private static void verifyRoomListRoutes() throws IOException {
        requireTokens(source("com.cdp.codpattern.network.match.RequestRoomListPacket"),
                "ServerPlayer player = ctx.get().getSender();",
                "if (player != null)",
                "syncRoomListToClient(player)");
        requireTokens(source("com.cdp.codpattern.network.match.SubscribeRoomListPacket"),
                "ServerPlayer player = ctx.get().getSender();",
                "subscribeLobbySummary(player)");
        requireTokens(source("com.cdp.codpattern.network.match.UnsubscribeRoomListPacket"),
                "ServerPlayer player = ctx.get().getSender();",
                "unsubscribeLobbySummary(player)");

        String manager = source("com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager");
        requireTokens(manager,
                "lobbySubscribers.add(player.getUUID());",
                "syncRoomListToClient(player);",
                "lobbySubscribers.remove(player.getUUID());",
                "lobbySubscribers.retainAll(onlinePlayers.keySet());",
                "for (UUID subscriberId : lobbySubscribers)",
                "ModNetworkChannel.sendToPlayer(packet, player);");
    }

    private static void verifyDispatchRoutes() throws IOException {
        String resync = source("com.cdp.codpattern.network.match.RequestRoomRosterResyncPacket");
        requireTokens(resync,
                "ServerPlayer player = ctx.get().getSender();",
                ".findPlayerRosterPort(player)",
                ".ifPresent(port -> port.requestRosterResync(player))");

        String preview = source("com.cdp.codpattern.network.match.RequestRoomPreviewRosterPacket");
        requireTokens(preview,
                "ServerPlayer player = ctx.get().getSender();",
                "if (player == null || roomKey.isBlank())",
                "roomId = RoomId.decode(roomKey);",
                ".findRoomRosterPort(roomId)",
                ".ifPresent(port -> port.requestRosterPreview(player))");
    }

    private static void verifyTdmRosterRoutes() throws IOException {
        String source = source("com.cdp.codpattern.compat.fpsmatch.map.CodTdmClientSyncCoordinator");
        requireTokens(source,
                "rosterCoordinator.requestResync(player);",
                "return currentRecipients().stream().anyMatch(player -> player.getUUID().equals(requesterId));",
                "rosterCoordinator.requestPreview(player);",
                "new RoomPreviewRosterPacket(roomKey, version, snapshot)",
                "for (ServerPlayer player : port.getJoinedPlayers())",
                "for (ServerPlayer player : port.getSpectatorPlayers())",
                "new TeamPlayerListPacket(roomKey, version, snapshot)",
                "new RoomPlayerDeltaPacket(roomKey, version, updates)");

        int previewMethod = source.indexOf("void requestRosterPreview(ServerPlayer player)");
        int recipientsMethod = source.indexOf("private Collection<ServerPlayer> currentRecipients", previewMethod);
        require(previewMethod >= 0 && recipientsMethod > previewMethod,
                "TDM preview method boundary must remain discoverable");
        String previewBody = source.substring(previewMethod, recipientsMethod);
        require(!previewBody.contains("currentRecipients()") && !previewBody.contains("containsKey(player.getUUID())"),
                "TDM requester-only preview must not acquire the live-recipient membership gate");
    }

    private static void verifyZombiesRosterRoutes() throws IOException {
        String factory = source("com.cdp.codpattern.compat.fpsmatch.map.ZombiesRoomHandleFactory");
        requireTokens(factory,
                "return requester != null && map.hasSurvivor(requester.getUUID());",
                "rosterCoordinator.requestResync(player);",
                "public void requestRosterPreview(ServerPlayer player)",
                "rosterCoordinator.requestPreview(player);",
                "new RoomPreviewRosterPacket(roomKey, version, snapshot)",
                "return map.survivorPlayers();",
                "new TeamPlayerListPacket(roomKey, version, snapshot)",
                "for (ServerPlayer survivor : recipients)",
                "for (ServerPlayer player : map.survivorPlayers())");

        int previewMethod = factory.indexOf("public void requestRosterPreview(ServerPlayer player)");
        int runtimeMethod = factory.indexOf("public ModeRuntimeStateSnapshot runtimeStateSnapshot", previewMethod);
        require(previewMethod >= 0 && runtimeMethod > previewMethod,
                "Zombies preview method boundary must remain discoverable");
        String previewBody = factory.substring(previewMethod, runtimeMethod);
        require(!previewBody.contains("hasSurvivor"),
                "Zombies requester-only preview must not acquire the survivor-membership gate");

        String map = source("com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap");
        requireTokens(map,
                "getMapTeams().getJoinedPlayers().forEach(playerData -> playerData.getPlayer().ifPresent(players::add));",
                "Set<UUID> survivorPlayerIds()",
                "Set<UUID> onlineSurvivorPlayerIds()");
    }

    private static Map<String, PacketRegistration> readPacketRegistrations() throws IOException {
        Map<String, PacketRegistration> packets = new LinkedHashMap<>();
        for (String row : dataLines(REGISTRATION)) {
            String[] fields = row.split("\\t", -1);
            require(fields.length == 6, "invalid packet registration row: " + row);
            PacketRegistration previous = packets.put(fields[2], new PacketRegistration(fields[1], fields[2]));
            require(previous == null, "duplicate packet class in registration baseline: " + fields[2]);
        }
        return packets;
    }

    private static List<String> dataLines(Path path) throws IOException {
        require(Files.isRegularFile(path), "missing Phase 0 baseline: " + path);
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private static String source(String className) throws IOException {
        Path path = MAIN_JAVA.resolve(className.replace('.', '/') + ".java");
        require(Files.isRegularFile(path), "missing source for " + className + ": " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void requireTokens(String source, String... tokens) {
        for (String token : tokens) {
            require(source.contains(token), "missing send-route source token: " + token);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record PacketRegistration(String direction, String className) {
    }
}
