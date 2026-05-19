package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
        barrierStateExposesAreaPayloadForHoverPrompts();
        purchaseObjectRevisionsOnlyChangeWhenMarkedSuccessful();
        weaponWallCurrentOfferRefreshesDeterministically();
        maxWaveWeaponWallUsesHighestRankAndFallbackStaysSafe();
    }

    private static void purchaseObjectsExposeRequiredModeObjectStatePayload() {
        Fixtures fixtures = fixtures();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(
                () -> false,
                fixedOfferService("common", "tacz:m4a1", 600, 210, 1.25D, 5));
        store.resetObjects(List.of(), List.of(fixtures.weaponWall()), List.of(fixtures.ammoBox()), List.of(fixtures.armorStation()));

        List<ModeObjectState> states = store.objectStates(
                List.of(),
                List.of(fixtures.weaponWall()),
                List.of(fixtures.ammoBox()),
                List.of(fixtures.armorStation()));

        ModeObjectState wallState = state(states, "wall-1");
        require(new BlockPos(2, 64, 1).equals(wallState.position()), "wall should use interactionPos before pos");
        require(wallState.revision() > 0L, "wall initial revision should advance on reset");
        requirePayload(wallState.payload(), "weapon_wall", "wall-1", true, 600);
        require("tacz:m4a1".equals(wallState.payload().getString("gunId")), "wall state should expose selected gunId");
        require(!wallState.payload().contains("weaponLevel"), "wall state should not expose internal weapon level");
        require("common".equals(wallState.payload().getString("rarityId")), "wall state should expose rarity id");

        ModeObjectState ammoState = state(states, "ammo-1");
        require(new BlockPos(3, 64, 1).equals(ammoState.position()), "ammo box should fall back to pos");
        require(ammoState.revision() > wallState.revision(), "ammo initial revision should follow wall reset revision");
        requirePayload(ammoState.payload(), "ammo_box", "ammo-1", true, 350);
        require(ammoState.payload().contains("pricesByWeaponLevel", Tag.TAG_COMPOUND),
                "ammo state should expose pricesByWeaponLevel for exact hover prompts");
        require(ammoState.payload().getCompound("pricesByWeaponLevel").getInt("2") == 350,
                "ammo state should expose exact level 2 refill price");

        ModeObjectState armorState = state(states, "armor-1");
        require(armorState.revision() > ammoState.revision(), "armor initial revision should follow ammo reset revision");
        requirePayload(armorState.payload(), "armor_station", "armor-1", true, 750);
        require(armorState.payload().getInt("armorLevel") == 2, "armor state should expose armor level");
    }

    private static void barrierStateExposesAreaPayloadForHoverPrompts() {
        ZombiesBarrierData barrier = new ZombiesBarrierData(
                "barrier-1",
                3,
                1250,
                true,
                dimension(),
                new BlockPos(10, 64, 1),
                new BlockPos(10, 66, 4),
                new BlockPos(10, 64, 0));
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        store.resetBarriers(List.of(barrier));

        ModeObjectState barrierState = state(store.barrierStates(List.of(barrier)), "barrier-1");
        require(new BlockPos(10, 64, 0).equals(barrierState.position()),
                "barrier state should keep interactionPos for legacy click targeting");
        requirePayload(barrierState.payload(), "barrier", "barrier-1", true, 1250);
        require(barrierState.payload().getInt("group") == 3, "barrier state should expose group");
        require(barrierState.payload().getInt("areaFromX") == 10, "barrier state should expose areaFromX");
        require(barrierState.payload().getInt("areaFromY") == 64, "barrier state should expose areaFromY");
        require(barrierState.payload().getInt("areaFromZ") == 1, "barrier state should expose areaFromZ");
        require(barrierState.payload().getInt("areaToX") == 10, "barrier state should expose areaToX");
        require(barrierState.payload().getInt("areaToY") == 66, "barrier state should expose areaToY");
        require(barrierState.payload().getInt("areaToZ") == 4, "barrier state should expose areaToZ");
    }

    private static void purchaseObjectRevisionsOnlyChangeWhenMarkedSuccessful() {
        Fixtures fixtures = fixtures();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(
                () -> false,
                fixedOfferService("common", "tacz:m4a1", 600, 210, 1.25D, 5));
        store.resetObjects(List.of(), List.of(fixtures.weaponWall()), List.of(fixtures.ammoBox()), List.of(fixtures.armorStation()));

        List<ModeObjectState> initialStates = store.objectStates(
                List.of(),
                List.of(fixtures.weaponWall()),
                List.of(fixtures.ammoBox()),
                List.of(fixtures.armorStation()));
        long initialWallRevision = state(initialStates, "wall-1").revision();
        long initialAmmoRevision = state(initialStates, "ammo-1").revision();
        long initialArmorRevision = state(initialStates, "armor-1").revision();
        require(initialWallRevision > 0L, "wall initial revision should advance on reset");
        require(initialAmmoRevision > initialWallRevision, "ammo initial revision should follow wall reset revision");
        require(initialArmorRevision > initialAmmoRevision, "armor initial revision should follow ammo reset revision");

        long wallRevision = store.markWeaponWallPurchased(fixtures.weaponWall());
        long ammoRevision = store.markAmmoBoxUsed(fixtures.ammoBox());
        long armorRevision = store.markArmorStationPurchased(fixtures.armorStation());
        require(wallRevision > initialArmorRevision, "successful wall purchase should advance past reset revisions");
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
                dimension(),
                new BlockPos(5, 64, 1),
                Optional.empty());
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(
                () -> false,
                fixedOfferService("common", "tacz:first", 600, 210, 1.25D, 2));
        store.resetObjects(List.of(), List.of(wall), List.of(), List.of(), 1, 5);

        ModeObjectState initial = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(initial.revision() > 0L, "initial wall offer revision should advance on reset");
        require("tacz:first".equals(initial.payload().getString("gunId")),
                "wave 1 offer should use weighted current-wave candidate");

        store.refreshWeaponWallOffersForWave(List.of(wall), 2, 5);
        ModeObjectState unchanged = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(unchanged.revision() == initial.revision(), "non-refresh wave should keep wall revision stable");
        require("tacz:first".equals(unchanged.payload().getString("gunId")),
                "non-refresh wave should keep current offer stable");

        store.refreshWeaponWallOffersForWave(List.of(wall), 3, 5);
        ModeObjectState refreshed = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(refreshed.revision() > unchanged.revision(), "configured refresh wave should advance wall revision");
        require("tacz:first".equals(refreshed.payload().getString("gunId")),
                "refresh wave should keep deterministic injected offer");

        store.refreshWeaponWallOffersForWave(List.of(wall), 3, 5);
        ModeObjectState repeated = state(store.objectStates(List.of(), List.of(wall), List.of(), List.of()), "wall-refresh");
        require(repeated.revision() == refreshed.revision(), "same refresh wave should not advance wall revision twice");
    }

    private static void maxWaveWeaponWallUsesHighestRankAndFallbackStaysSafe() {
        ZombiesWeaponWallData maxWall = new ZombiesWeaponWallData(
                "wall-max",
                dimension(),
                new BlockPos(6, 64, 1),
                Optional.empty());
        ZombiesWeaponWallData fallbackWall = new ZombiesWeaponWallData(
                "wall-fallback",
                dimension(),
                new BlockPos(7, 64, 1),
                Optional.empty());
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(
                () -> false,
                fixedOfferService("epic", "tacz:rules_pick", 1500, 300, 1.60D, 5));
        store.resetObjects(List.of(), List.of(maxWall, fallbackWall), List.of(), List.of(), 1, 5);

        store.refreshWeaponWallOffersForWave(List.of(maxWall, fallbackWall), 5, 5);
        List<ModeObjectState> states = store.objectStates(List.of(), List.of(maxWall, fallbackWall), List.of(), List.of());
        require("tacz:rules_pick".equals(state(states, "wall-max").payload().getString("gunId")),
                "wall offer should come from shared rules service");
        require("tacz:rules_pick".equals(state(states, "wall-fallback").payload().getString("gunId")),
                "all wall points should use shared rules service rather than per-object gun pools");
    }

    private static Fixtures fixtures() {
        ZombiesWeaponWallData weaponWall = new ZombiesWeaponWallData(
                "wall-1",
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

    private static ZombiesWeaponWallOfferService fixedOfferService(
            String rarityId,
            String gunId,
            int price,
            int maxReserveAmmo,
            double damageMultiplier,
            int refreshIntervalWaves
    ) {
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        config.getWeaponWall().setRefreshIntervalWaves(refreshIntervalWaves);
        return new ZombiesWeaponWallOfferService(
                () -> config,
                new java.util.Random(0L),
                ignored -> ItemStack.EMPTY) {
            @Override
            public ZombiesObjectStateStore.WeaponWallOffer createOffer(
                    ZombiesWeaponWallData weaponWall,
                    int currentWave
            ) {
                return new ZombiesObjectStateStore.WeaponWallOffer(
                        weaponWall.objectId(),
                        rarityId,
                        gunId,
                        price,
                        maxReserveAmmo,
                        damageMultiplier);
            }
        };
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
