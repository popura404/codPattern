package com.cdp.codpattern.compat.modesplit;

import com.cdp.codpattern.architecture.ModeSplitVerificationRoots;
import com.cdp.codpattern.compat.fpsmatch.data.CodTacticalTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Main-owned Phase 0 map, shared-config, and backpack fixture runner. */
public final class Phase0MainDataFixtureCompatTest {
    private Phase0MainDataFixtureCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("PASS phase0 main data fixture compat");
    }

    public static void runAll() throws Exception {
        bootstrapRegistriesForPureJvmFixtures();
        mapCodecFixturesAreStable();
        configFixturesCharacterizeCurrentReadWriteRules();
    }

    private static void bootstrapRegistriesForPureJvmFixtures() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null, true);
        require(!BuiltInRegistries.REGISTRY.keySet().isEmpty(),
                "built-in registries must load for fixture codecs");
        require(Items.CROSSBOW != Items.AIR,
                "vanilla item constants must initialize for fixture codecs");
    }

    private static void mapCodecFixturesAreStable() throws IOException {
        assertCodecFixture(
                "maps/frontline-legacy.json",
                CodTdmMapData.MapData.CODEC,
                data -> {
                    require("LegacyCase_Map-01".equals(data.mapName()), "frontline mapName case must survive");
                    require(List.copyOf(data.teams().keySet()).equals(List.of("RedTeam", "BLUE_team")),
                            "frontline team insertion order and case must survive decode");
                    CodTdmMapData.TeamData legacyTeam = data.teams().get("RedTeam");
                    require(legacyTeam != null && legacyTeam.initialSpawnPoints().size() == 1,
                            "legacy spawnPoints must migrate to initialSpawnPoints");
                    require(legacyTeam.dynamicSpawnCandidates().isEmpty(),
                            "legacy spawnPoints must not create dynamic candidates");
                    require(Float.compare(legacyTeam.initialSpawnPoints().get(0).getPitch(), -12.5F) == 0,
                            "legacy spawn pitch must survive even though SpawnPointData.equals omits pitch");
                },
                "\"mapName\": \"LegacyCase_Map-01\"",
                "\"RedTeam\"",
                "\"BLUE_team\"",
                "\"unknownTopLevel\"");

        assertCodecFixture(
                "maps/team-deathmatch-current.json",
                CodTacticalTdmMapData.MapData.CODEC,
                data -> {
                    require("TDM_MixedCase_02".equals(data.mapName()), "TDM mapName case must survive");
                    require(List.copyOf(data.teams().keySet()).equals(List.of("SAS", "Spetsnaz")),
                            "TDM team insertion order and case must survive decode");
                    require(data.teams().get("Spetsnaz").dynamicSpawnCandidates().get(0).getKind()
                                    == SpawnPointKind.DYNAMIC_CANDIDATE,
                            "lower-case dynamic spawn kind must decode compatibly");
                    require(Float.compare(data.matchEndTeleportPoint().orElseThrow().getPitch(), 11.0F) == 0,
                            "TDM end-teleport pitch must survive round trip");
                },
                "\"mapName\": \"TDM_MixedCase_02\"",
                "\"SAS\"",
                "\"Spetsnaz\"",
                "\"unknownTopLevel\"");
    }

    private static <T> void assertCodecFixture(
            String relativePath,
            Codec<T> codec,
            Consumer<T> assertions,
            String... orderedSourceTokens
    ) throws IOException {
        String source = readFixture(relativePath);
        assertOrdered(source, relativePath, orderedSourceTokens);
        JsonElement input = JsonParser.parseString(source);
        T decoded = codec.parse(JsonOps.INSTANCE, input)
                .getOrThrow(false, error -> {
                    throw new AssertionError(relativePath + " decode failed: " + error);
                });
        assertions.accept(decoded);

        JsonElement firstEncoded = codec.encodeStart(JsonOps.INSTANCE, decoded)
                .getOrThrow(false, error -> {
                    throw new AssertionError(relativePath + " encode failed: " + error);
                });
        require(!firstEncoded.toString().contains("unknownTopLevel"),
                relativePath + " current codec must continue ignoring unknown top-level data");

        T secondDecoded = codec.parse(JsonOps.INSTANCE, firstEncoded)
                .getOrThrow(false, error -> {
                    throw new AssertionError(relativePath + " re-decode failed: " + error);
                });
        JsonElement secondEncoded = codec.encodeStart(JsonOps.INSTANCE, secondDecoded)
                .getOrThrow(false, error -> {
                    throw new AssertionError(relativePath + " re-encode failed: " + error);
                });
        require(firstEncoded.equals(secondEncoded),
                relativePath + " encoded form must be stable after one migration pass");
        require(source.equals(readFixture(relativePath)),
                relativePath + " source fixture must remain byte-for-byte untouched");
    }

    private static void configFixturesCharacterizeCurrentReadWriteRules() throws Exception {
        Path tempRoot = Files.createTempDirectory("phase0-main-config-fixtures-");
        try {
            characterizeWeaponFilter(tempRoot);
            characterizeBackpackConfig(tempRoot);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void characterizeWeaponFilter(Path tempRoot) throws IOException {
        String source = readFixture("config/weapon-filter-mixed-case.json");
        assertOrdered(source, "weapon-filter source", "\"primaryWeaponTabs\"", "\"blockedItemNamespaces\"",
                "\"UnknownLegacyOption\"");
        Path target = copyFixture(tempRoot, "config/weapon-filter-mixed-case.json");

        WeaponFilterConfig config = WeaponFilterConfigRepository.loadOrCreate(target);

        require(config.getPrimaryWeaponTabs().equals(List.of("Rifle", "SNIPER")),
                "general filter primary tab case is currently retained");
        require(config.getSecondaryWeaponTabs().equals(List.of("Pistol", "MELEE")),
                "general filter secondary tab case is currently retained");
        require(config.getBlockedItemNamespaces().equals(List.of("legacypack", "otherpack")),
                "general filter blocked namespaces currently trim/lowercase/deduplicate");
        require(config.getBlockedWeaponIds().equals(List.of("example:gun_a", "example:gun_b")),
                "general filter blocked weapon IDs currently trim/lowercase/deduplicate");
        String saved = Files.readString(target);
        require(!saved.contains("UnknownLegacyOption"),
                "general filter's current load-normalize-save path drops unknown fields");
        require(!saved.equals(source), "general filter's current load path rewrites non-canonical input");
        String onceNormalized = saved;
        WeaponFilterConfigRepository.loadOrCreate(target);
        require(onceNormalized.equals(Files.readString(target)), "general filter canonical output must stabilize");
        require(source.equals(readFixture("config/weapon-filter-mixed-case.json")),
                "general filter source fixture must remain untouched");
    }

    private static void characterizeBackpackConfig(Path tempRoot) throws IOException {
        String source = readFixture("config/backpack-legacy.json");
        assertOrdered(source, "backpack source", "\"secondary\"", "\"primary\"", "\"UnknownBackpackField\"",
                "\"UnknownRootField\"");
        Path target = copyFixture(tempRoot, "config/backpack-legacy.json");

        BackpackConfig config = BackpackConfigRepository.loadOrCreate(target);

        require(source.equals(Files.readString(target)),
                "backpack's current load path must not rewrite a valid file before explicit save");
        BackpackConfig.PlayerBackpackData player = config.getPlayerData()
                .get("00000000-0000-0000-0000-000000000042");
        require(player != null && player.getSelectedBackpack() == 7, "legacy selected backpack must decode");
        BackpackConfig.Backpack backpack = player.getBackpacks_MAP().get(7);
        require(backpack != null && "Legacy MixedCase Loadout".equals(backpack.getName()),
                "legacy mixed-case backpack name must decode");
        String primaryNbt = backpack.getItem_MAP().get("primary").getNbt();
        require(primaryNbt.contains("UnknownTaCZField:\"MixedCase\""),
                "embedded legacy SNBT spelling/case must survive config decode");

        BackpackConfigRepository.save();
        String saved = Files.readString(target);
        require(!saved.contains("UnknownRootField") && !saved.contains("UnknownBackpackField"),
                "backpack's current explicit save path drops unknown JSON fields");
        require(saved.contains("UnknownTaCZField"),
                "backpack explicit save must preserve embedded SNBT as an opaque string");
        BackpackConfig reloaded = BackpackConfigRepository.loadOrCreate(target);
        require(reloaded.getPlayerData().get("00000000-0000-0000-0000-000000000042")
                        .getBackpacks_MAP().get(7).getItem_MAP().get("primary").getNbt().equals(primaryNbt),
                "backpack canonical output must preserve embedded SNBT exactly");
        require(source.equals(readFixture("config/backpack-legacy.json")),
                "backpack source fixture must remain untouched");
    }

    private static Path copyFixture(Path tempRoot, String relativePath) throws IOException {
        Path target = tempRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, readFixture(relativePath));
        return target;
    }

    private static String readFixture(String relativePath) throws IOException {
        return Files.readString(ModeSplitVerificationRoots.testResource("mode-split/phase0/" + relativePath));
    }

    private static void assertOrdered(String source, String label, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token);
            require(current >= 0, label + " must contain source token " + token);
            require(current > previous, label + " must retain source token ordering at " + token);
            previous = current;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
