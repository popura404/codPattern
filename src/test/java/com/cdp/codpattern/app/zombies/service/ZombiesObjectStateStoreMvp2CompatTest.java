package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ZombiesObjectStateStoreMvp2CompatTest {
    private ZombiesObjectStateStoreMvp2CompatTest() {
    }

    public static void main(String[] args) {
        purchaseObjectsExposeRequiredModeObjectStatePayload();
        purchaseObjectRevisionsOnlyChangeWhenMarkedSuccessful();
        weaponWallCurrentOfferRefreshesDeterministically();
        maxWaveWeaponWallUsesHighestRankAndFallbackStaysSafe();
    }

    private static void purchaseObjectsExposeRequiredModeObjectStatePayload() {
        Fixtures fixtures = fixtures();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        store.resetObjects(List.of(), List.of(fixtures.weaponWall()), List.of(fixtures.ammoBox()), List.of(fixtures.armorStation()));

        List<ModeObjectState> states = store.objectStates(
                List.of(),
                List.of(fixtures.weaponWall()),
                List.of(fixtures.ammoBox()),
                List.of(fixtures.armorStation()));

        ModeObjectState wallState = state(states, "wall-1");
        require(new BlockPos(2, 64, 1).equals(wallState.position()), "wall should use interactionPos before pos");
        require(wallState.revision() == 0L, "wall initial revision should be stable zero");
        requirePayload(wallState.payload(), "weapon_wall", "wall-1", true, 600);
        require("tacz:m4a1".equals(wallState.payload().getString("gunId")), "wall state should expose selected gunId");
        require(wallState.payload().getInt("weaponLevel") == 2, "wall state should expose weapon level");

        ModeObjectState ammoState = state(states, "ammo-1");
        require(new BlockPos(3, 64, 1).equals(ammoState.position()), "ammo box should fall back to pos");
        requirePayload(ammoState.payload(), "ammo_box", "ammo-1", true, 350);

        ModeObjectState armorState = state(states, "armor-1");
        requirePayload(armorState.payload(), "armor_station", "armor-1", true, 750);
        require(armorState.payload().getInt("armorLevel") == 2, "armor state should expose armor level");
    }

    private static void purchaseObjectRevisionsOnlyChangeWhenMarkedSuccessful() {
        Fixtures fixtures = fixtures();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        store.resetObjects(List.of(), List.of(fixtures.weaponWall()), List.of(fixtures.ammoBox()), List.of(fixtures.armorStation()));

        List<ModeObjectState> initialStates = store.objectStates(
                List.of(),
                List.of(fixtures.weaponWall()),
                List.of(fixtures.ammoBox()),
                List.of(fixtures.armorStation()));
        require(state(initialStates, "wall-1").revision() == 0L, "wall initial revision should be zero");
        require(state(initialStates, "ammo-1").revision() == 0L, "ammo initial revision should be zero");
        require(state(initialStates, "armor-1").revision() == 0L, "armor initial revision should be zero");

        long wallRevision = store.markWeaponWallPurchased(fixtures.weaponWall());
        long ammoRevision = store.markAmmoBoxUsed(fixtures.ammoBox());
        long armorRevision = store.markArmorStationPurchased(fixtures.armorStation());
        require(wallRevision > 0L, "successful wall purchase should get positive revision");
        require(ammoRevision > wallRevision, "successful ammo refill should advance revision");
        require(armorRevision > ammoRevision, "successful armor purchase should advance revision");

        List<ModeObjectState> purchasedStates = store.objectStates(
                List.of(),
                List.of(fixtures.weaponWall()),
                List.of(fixtures.ammoBox()),
                List.of(fixtures.armorStation()));
        require(state(purchasedStates, "wall-1").revision() == wallRevision,
                "wall revision should reflect successful purchase mark");
        require(state(purchasedStates, "ammo-1").revision() == ammoRevision,
                "ammo revision should reflect successful refill mark");
        require(state(purchasedStates, "armor-1").revision() == armorRevision,
                "armor revision should reflect successful purchase mark");

        List<ModeObjectState> unchangedStates = store.objectStates(
                List.of(),
                List.of(fixtures.weaponWall()),
                List.of(fixtures.ammoBox()),
                List.of(fixtures.armorStation()));
        require(state(unchangedStates, "wall-1").revision() == wallRevision,
                "failed or skipped wall purchase should not advance revision without mark");
    }

    private static void weaponWallCurrentOfferRefreshesDeterministically() {
        ZombiesWeaponWallData wall = new ZombiesWeaponWallData(
                "wall-refresh",
                2,
                1.25D,
                600,
                210,
                List.of(3),
                List.of(
                        new ZombiesWeaponWallData.RarityPoolData("common", 1, 10.0D, 0.0D),
                        new ZombiesWeaponWallData.RarityPoolData("rare", 2, 0.0D, 5.0D)),
                List.of(
                        new ZombiesWeaponWallData.WeaponCandidateData("tacz:first", Map.of("common", 1.0D)),
                        new ZombiesWeaponWallData.WeaponCandidateData("tacz:rare_pick", Map.of("rare", 4.0D))),
                dimension(),
                new BlockPos(5, 64, 1),
                Optional.empty());
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        store.resetObjects(List.of(), List.of(wall), List.of(), List.of(), 1, 5);

        ModeObjectState initial = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(initial.revision() == 0L, "initial wall offer revision should be stable zero");
        require("tacz:first".equals(initial.payload().getString("gunId")),
                "wave 1 offer should use weighted current-wave candidate");

        store.refreshWeaponWallOffersForWave(List.of(wall), 2, 5);
        ModeObjectState unchanged = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(unchanged.revision() == initial.revision(), "non-refresh wave should keep wall revision stable");
        require("tacz:first".equals(unchanged.payload().getString("gunId")),
                "non-refresh wave should keep current offer stable");

        store.refreshWeaponWallOffersForWave(List.of(wall), 3, 5);
        ModeObjectState refreshed = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(refreshed.revision() > unchanged.revision(), "refresh wave should advance wall revision");
        require("tacz:rare_pick".equals(refreshed.payload().getString("gunId")),
                "refresh wave should replace current offer using weighted rarity pool");

        store.refreshWeaponWallOffersForWave(List.of(wall), 3, 5);
        ModeObjectState repeated = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(repeated.revision() == refreshed.revision(), "same refresh wave should not advance wall revision twice");
    }

    private static void maxWaveWeaponWallUsesHighestRankAndFallbackStaysSafe() {
        ZombiesWeaponWallData maxWall = new ZombiesWeaponWallData(
                "wall-max",
                3,
                1.75D,
                900,
                300,
                List.of(),
                List.of(
                        new ZombiesWeaponWallData.RarityPoolData("common", 1, 100.0D, 0.0D),
                        new ZombiesWeaponWallData.RarityPoolData("legendary", 5, 0.0D, 0.0D)),
                List.of(
                        new ZombiesWeaponWallData.WeaponCandidateData("tacz:first", Map.of("common", 9.0D)),
                        new ZombiesWeaponWallData.WeaponCandidateData("tacz:legendary_pick", Map.of("legendary", 1.0D))),
                dimension(),
                new BlockPos(6, 64, 1),
                Optional.empty());
        ZombiesWeaponWallData fallbackWall = new ZombiesWeaponWallData(
                "wall-fallback",
                3,
                1.75D,
                900,
                300,
                List.of(),
                List.of(
                        new ZombiesWeaponWallData.RarityPoolData("common", 1, 100.0D, 0.0D),
                        new ZombiesWeaponWallData.RarityPoolData("legendary", 5, 0.0D, 0.0D)),
                List.of(new ZombiesWeaponWallData.WeaponCandidateData("tacz:fallback", Map.of("common", 9.0D))),
                dimension(),
                new BlockPos(7, 64, 1),
                Optional.empty());
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        store.resetObjects(List.of(), List.of(maxWall, fallbackWall), List.of(), List.of(), 1, 5);

        store.refreshWeaponWallOffersForWave(List.of(maxWall, fallbackWall), 5, 5);
        List<ModeObjectState> states = store.objectStates(List.of(), List.of(maxWall, fallbackWall), List.of(), List.of());
        require("tacz:legendary_pick".equals(state(states, "wall-max").payload().getString("gunId")),
                "max wave should use highest-rank rarity candidate");
        require("tacz:fallback".equals(state(states, "wall-fallback").payload().getString("gunId")),
                "max wave without usable top-rank candidate should fall back to first non-empty gunId");
    }

    private static Fixtures fixtures() {
        ZombiesWeaponWallData weaponWall = new ZombiesWeaponWallData(
                "wall-1",
                2,
                1.25D,
                600,
                210,
                List.of(),
                List.of(),
                List.of(
                        new ZombiesWeaponWallData.WeaponCandidateData("", Map.of()),
                        new ZombiesWeaponWallData.WeaponCandidateData("tacz:m4a1", Map.of())),
                dimension(),
                new BlockPos(1, 64, 1),
                Optional.of(new BlockPos(2, 64, 1)));
        ZombiesAmmoBoxData ammoBox = new ZombiesAmmoBoxData(
                "ammo-1",
                Map.of("2", 350),
                dimension(),
                new BlockPos(3, 64, 1),
                Optional.empty());
        ZombiesArmorStationData armorStation = new ZombiesArmorStationData(
                "armor-1",
                2,
                750,
                0.50D,
                dimension(),
                new BlockPos(4, 64, 1),
                Optional.empty());
        return new Fixtures(weaponWall, ammoBox, armorStation);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceKey<Level> dimension() {
        try {
            // Avoid Level/Registries constants here; pure Java compat tests do not bootstrap Minecraft registries.
            Constructor<ResourceKey> constructor =
                    ResourceKey.class.getDeclaredConstructor(ResourceLocation.class, ResourceLocation.class);
            constructor.setAccessible(true);
            return (ResourceKey<Level>) constructor.newInstance(
                    resourceLocation("minecraft:dimension"),
                    resourceLocation("minecraft:overworld"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to create test dimension key", exception);
        }
    }

    private static ResourceLocation resourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new AssertionError("invalid resource location " + value);
        }
        return location;
    }

    private static ModeObjectState state(List<ModeObjectState> states, String objectKey) {
        return states.stream()
                .filter(state -> objectKey.equals(state.objectKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing object state " + objectKey));
    }

    private static void requirePayload(
            CompoundTag payload,
            String expectedType,
            String expectedObjectId,
            boolean expectedEnabled,
            int expectedCost
    ) {
        require(expectedType.equals(payload.getString("type")),
                "expected type " + expectedType + " but was " + payload.getString("type"));
        require(expectedObjectId.equals(payload.getString("objectId")),
                "expected objectId " + expectedObjectId + " but was " + payload.getString("objectId"));
        require(payload.getBoolean("enabled") == expectedEnabled,
                "expected enabled " + expectedEnabled + " but was " + payload.getBoolean("enabled"));
        require(payload.getInt("cost") == expectedCost,
                "expected cost " + expectedCost + " but was " + payload.getInt("cost"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Fixtures(
            ZombiesWeaponWallData weaponWall,
            ZombiesAmmoBoxData ammoBox,
            ZombiesArmorStationData armorStation
    ) {
    }
}
