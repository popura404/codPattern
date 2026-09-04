package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.ModeModules;
import com.cdp.codpattern.app.tdm.TdmModeModule;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every new FPSMatch instance receives every frozen provider exactly once. */
public final class GameModeFpsRegistrationCompatTest {
    private GameModeFpsRegistrationCompatTest() {
    }

    public static void main(String[] args) {
        ModeModules.contribute(TdmModeModule.INSTANCE);
        ModeModules.freeze();

        CountingEvent firstServer = new CountingEvent();
        CountingEvent secondServer = new CountingEvent();
        GameModeBootstrap.onRegisterFPSMap(firstServer);
        GameModeBootstrap.onRegisterFPSMap(secondServer);

        Map<String, Integer> expected = Map.of("frontline", 1, "teamdeathmatch", 1);
        require(firstServer.calls.equals(expected),
                "first FPSMatch instance should receive each provider once: " + firstServer.calls);
        require(secondServer.calls.equals(expected),
                "second FPSMatch instance should independently receive each provider once: " + secondServer.calls);
        System.out.println("PASS frozen catalog FPSMatch registration per server instance");
    }

    private static final class CountingEvent extends RegisterFPSMapEvent {
        private final Map<String, Integer> calls = new LinkedHashMap<>();

        private CountingEvent() {
            super(null);
        }

        @Override
        public void registerGameType(
                String typeName,
                Function3<ServerLevel, String, AreaData, BaseMap> mapFactory
        ) {
            calls.merge(typeName, 1, Integer::sum);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
