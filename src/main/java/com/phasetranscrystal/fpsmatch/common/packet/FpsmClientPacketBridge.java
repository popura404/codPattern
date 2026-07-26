package com.phasetranscrystal.fpsmatch.common.packet;

public final class FpsmClientPacketBridge {
    private static final Handler NOOP = new Handler() {
    };

    private static volatile Handler handler = NOOP;

    private FpsmClientPacketBridge() {
    }

    public static void install(Handler handler) {
        FpsmClientPacketBridge.handler = handler == null ? NOOP : handler;
    }

    public static void openMapCreatorToolScreen(OpenMapCreatorToolScreenS2CPacket packet) {
        handler.openMapCreatorToolScreen(packet);
    }

    public static void openSpawnPointToolScreen(OpenSpawnPointToolScreenS2CPacket packet) {
        handler.openSpawnPointToolScreen(packet);
    }

    public interface Handler {
        default void openMapCreatorToolScreen(OpenMapCreatorToolScreenS2CPacket packet) {
        }

        default void openSpawnPointToolScreen(OpenSpawnPointToolScreenS2CPacket packet) {
        }
    }
}
