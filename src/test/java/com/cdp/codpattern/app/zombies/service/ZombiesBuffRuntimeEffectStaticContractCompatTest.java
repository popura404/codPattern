package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesBuffRuntimeEffectStaticContractCompatTest {
    private static final Path RUNTIME_EFFECT_SERVICE = Path.of(
            "src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesBuffRuntimeEffectService.java");
    private static final Path BUFF_COMBAT_SERVICE = Path.of(
            "src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesBuffCombatService.java");
    private static final Path ZOMBIES_MAP = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java");
    private static final Path TACZ_HEADSHOT_HANDLER = Path.of(
            "src/main/java/com/cdp/codpattern/compat/tacz/event/TaczHeadshotMultiplierOverrideHandler.java");

    private ZombiesBuffRuntimeEffectStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        speedBoostIsAppliedToMovementAttribute();
        speedBoostIsSyncedAndClearedByMapLifecycle();
        headshotDamageIsAppliedThroughTaczHeadshotEvent();
        System.out.println("PASS zombies buff runtime effect static contract compat");
    }

    private static void speedBoostIsAppliedToMovementAttribute() throws IOException {
        String service = read(RUNTIME_EFFECT_SERVICE);
        requireContains(service,
                "Attributes.MOVEMENT_SPEED",
                "speed_boost must target the player movement speed attribute");
        requireContains(service,
                "ZombiesBuffType.SPEED_BOOST",
                "speed_boost must read the owned soda buff state");
        requireContains(service,
                "movementSpeed.removeModifier(SPEED_BOOST_MODIFIER_ID);",
                "speed_boost must remove the previous modifier before syncing");
        requireContains(service,
                "AttributeModifier.Operation.MULTIPLY_TOTAL",
                "speed_boost must multiply total movement speed instead of replacing base speed");
        requireContains(service,
                "multiplier - 1.0D",
                "speed_boost modifier amount must be derived from the soda multiplier");
    }

    private static void speedBoostIsSyncedAndClearedByMapLifecycle() throws IOException {
        String map = read(ZOMBIES_MAP);
        requireContains(map,
                "private final ZombiesBuffRuntimeEffectService buffRuntimeEffectService;",
                "zombies map must own the runtime buff effect service");
        requireContains(map,
                "this.buffRuntimeEffectService = new ZombiesBuffRuntimeEffectService(playerStateService);",
                "zombies map must construct runtime buff effects from player state");
        requireContains(map,
                "syncBuffRuntimeEffects();",
                "zombies map tick must sync runtime buff effects");
        requireContains(map,
                "buffRuntimeEffectService.syncPlayer(player);",
                "zombies map must sync runtime buff effects on player restore/login/respawn paths");
        requireContains(map,
                "buffRuntimeEffectService.clearPlayer(player);",
                "zombies map must clear runtime buff effects on offline/leave/reset paths");
    }

    private static void headshotDamageIsAppliedThroughTaczHeadshotEvent() throws IOException {
        String combat = read(BUFF_COMBAT_SERVICE);
        requireContains(combat,
                "ZombiesBuffType.HEADSHOT_DAMAGE",
                "headshot_damage must read the owned soda buff state");
        requireContains(combat,
                "sameRoomMonster(hurtEntity).isEmpty()",
                "headshot_damage must only apply against same-room zombies mobs");
        requireContains(combat,
                "scaledHeadshotMultiplier(currentMultiplier, headshotDamageMultiplier(attacker.getUUID()))",
                "headshot_damage must scale the active headshot multiplier");

        String handler = read(TACZ_HEADSHOT_HANDLER);
        requireContains(handler,
                "applyZombiesHeadshotDamageBuff(event);",
                "TaCZ headshot restore handler must invoke zombies headshot buff logic");
        requireContains(handler,
                "event.isHeadShot()",
                "headshot_damage must only run for headshots");
        requireContains(handler,
                "event.getAttacker() instanceof ServerPlayer attacker",
                "headshot_damage must require a server-player attacker");
        requireContains(handler,
                "event.getHurtEntity() instanceof LivingEntity hurtEntity",
                "headshot_damage must require a living hurt entity");
        requireContains(handler,
                "BuiltInGameModes.isZombies(room.gameType())",
                "headshot_damage must only apply in zombies rooms");
        requireContains(handler,
                "ZombiesBuffCombatService.serviceFor(roomId.get())",
                "headshot_damage must route through the room combat service");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
