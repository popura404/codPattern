package com.cdp.codpattern.compat.modesplit;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.service.ZombiesServiceResult;
import com.cdp.codpattern.app.zombies.service.ZombiesWeaponItemStackService;
import com.cdp.codpattern.compat.fpsmatch.data.CodTacticalTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.data.ZombiesMapData;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.config.zombies.ZombiesWeaponFilterConfig;
import com.cdp.codpattern.config.zombies.ZombiesWeaponFilterRepository;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Executable Phase 0 characterization for persisted map/config/NBT/ItemStack data.
 *
 * <p>The source fixtures intentionally retain mixed case, legacy fields, stable
 * ordering, and unknown fields. A read/write path is allowed to omit unknown
 * fields only where the current production serializer already does so; the
 * untouched source fixture remains the reviewable input contract.</p>
 */
public final class Phase0DataFixtureCompatTest {
    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/mode-split/phase0");

    private Phase0DataFixtureCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        bootstrapRegistriesForPureJvmFixtures();
        mapCodecFixturesAreStable();
        configFixturesCharacterizeCurrentReadWriteRules();
        playerNbtFixtureRoundTripsExactly();
        zombiesItemStackFixtureRoundTripsAndPreservesForeignTags();
        System.out.println("PASS phase0 data fixture compat");
    }

    private static void bootstrapRegistriesForPureJvmFixtures() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        // Forge's full Bootstrap.bootStrap() initializes NetworkHooks and is not
        // safe in a plain JavaExec test process. Mark the vanilla bootstrap guard
        // before loading built-in registries; the fixture only needs codecs and
        // vanilla ItemStack lookup, not a live Forge channel/event bus.
        Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null, true);
        require(!BuiltInRegistries.REGISTRY.keySet().isEmpty(), "built-in registries must load for fixture codecs");
        require(Items.CROSSBOW != Items.AIR, "vanilla item constants must initialize for ItemStack fixtures");
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

        assertCodecFixture(
                "maps/zombies-current.json",
                ZombiesMapData.MapData.CODEC,
                data -> {
                    require(data.schemaVersion() == 1, "zombies schema version must survive");
                    require("zombies".equals(data.gameType()), "zombies game type must survive");
                    require("Zombies_MixedCase_03".equals(data.mapName()), "zombies mapName case must survive");
                    require(data.initialSpawns().size() == 1, "initial spawn fixture must decode");
                    require(data.zombieSpawns().size() == 1, "zombie spawn fixture must decode");
                    require(data.barriers().size() == 1, "barrier fixture must decode");
                    require(data.weaponWalls().size() == 1, "weapon wall fixture must decode");
                    require(data.ammoBoxes().size() == 1, "ammo box fixture must decode");
                    require(data.armorStations().size() == 1, "armor station fixture must decode");
                    require(data.powerSwitch().isPresent(), "power switch fixture must decode");
                    require(data.sodaMachines().size() == 1, "soda machine fixture must decode");
                    require(data.ultimateMachines().size() == 1, "ultimate machine fixture must decode");
                    require(data.mysteryBoxes().size() == 1, "mystery box fixture must decode");
                    require(data.windows().size() == 1, "window fixture must decode");
                    require("Barrier_A".equals(data.barriers().get(0).objectId()),
                            "zombies object ID case must survive");
                    require(Float.compare(data.endtp().orElseThrow().getPitch(), -8.0F) == 0,
                            "zombies end-teleport pitch must survive round trip");
                },
                "\"schemaVersion\"",
                "\"gameType\"",
                "\"mapName\": \"Zombies_MixedCase_03\"",
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
        require(firstEncoded.equals(secondEncoded), relativePath + " encoded form must be stable after one migration pass");
        require(source.equals(readFixture(relativePath)), relativePath + " source fixture must remain byte-for-byte untouched");
    }

    private static void configFixturesCharacterizeCurrentReadWriteRules() throws Exception {
        Path tempRoot = Files.createTempDirectory("phase0-config-fixtures-");
        try {
            characterizeWeaponFilter(tempRoot);
            characterizeZombiesWeaponFilter(tempRoot);
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

    private static void characterizeZombiesWeaponFilter(Path tempRoot) throws IOException {
        String source = readFixture("config/zombies-weapon-filter-mixed-case.json");
        assertOrdered(source, "zombies filter source", "\"weaponTabs\"", "\"blockedItemNamespaces\"",
                "\"UnknownLegacyOption\"");
        Path target = copyFixture(tempRoot, "config/zombies-weapon-filter-mixed-case.json");

        ZombiesWeaponFilterConfig config = ZombiesWeaponFilterRepository.loadOrCreate(target);

        require(source.equals(Files.readString(target)),
                "zombies filter's current load path must not rewrite a valid file before explicit save");
        require(config.getWeaponTabs().equals(List.of("pistol", "rifle")),
                "zombies filter tabs currently trim/lowercase/deduplicate in memory");
        require(config.getBlockedItemNamespaces().equals(List.of("legacypack")),
                "zombies filter blocked namespaces currently trim/lowercase/deduplicate");
        require(Math.abs(config.getAmmunitionPerMagazineMultiple() - 12.5D) < 0.0001D,
                "zombies filter ammunition multiple must survive");

        ZombiesWeaponFilterRepository.save(config);
        String saved = Files.readString(target);
        require(!saved.contains("UnknownLegacyOption"),
                "zombies filter's current explicit save path drops unknown fields");
        ZombiesWeaponFilterConfig reloaded = ZombiesWeaponFilterRepository.loadOrCreate(target);
        require(reloaded.getWeaponTabs().equals(List.of("pistol", "rifle")),
                "zombies filter canonical output must reload semantically");
        require(saved.equals(Files.readString(target)),
                "zombies filter canonical output must remain stable on reload");
        require(source.equals(readFixture("config/zombies-weapon-filter-mixed-case.json")),
                "zombies filter source fixture must remain untouched");
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

    private static void playerNbtFixtureRoundTripsExactly() throws Exception {
        String source = readFixture("nbt/player-marker.snbt");
        assertOrdered(source, "player marker source", "\"UnrelatedSibling\"", "\"codpattern.zombies\"",
                "\"roomId\"", "\"state\"", "\"endtp\"");
        CompoundTag tag = TagParser.parseTag(source);
        CompoundTag root = tag.getCompound("codpattern.zombies");
        require(root.contains("roomId", Tag.TAG_STRING), "player marker roomId key/type must remain exact");
        require(root.contains("state", Tag.TAG_STRING), "player marker state key/type must remain exact");
        require(root.contains("endtp", Tag.TAG_COMPOUND), "player marker endtp key/type must remain exact");
        RoomId roomId = RoomId.decode(root.getString("roomId"));
        require("zombies".equals(roomId.gameType()), "player marker game type must decode");
        require("Zombies_MixedCase_03".equals(roomId.mapName()), "player marker map-name case must survive");
        require("pending_endtp".equals(root.getString("state")), "player marker state spelling must survive");
        require(tag.getCompound("UnrelatedSibling").getString("MixedCaseKey").equals("KeepMe"),
                "unrelated player persistent NBT must stay represented in the fixture");

        Path tempFile = Files.createTempFile("phase0-player-marker-", ".dat");
        try {
            NbtIo.writeCompressed(tag, tempFile.toFile());
            CompoundTag restored = NbtIo.readCompressed(tempFile.toFile());
            require(tag.equals(restored), "player marker NBT must survive binary compressed read/write exactly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
        require(source.equals(readFixture("nbt/player-marker.snbt")),
                "player marker source fixture must remain untouched");
    }

    private static void zombiesItemStackFixtureRoundTripsAndPreservesForeignTags() throws Exception {
        String source = readFixture("nbt/zombies-item-stack.snbt");
        assertOrdered(source, "item stack source", "\"GunId\"", "\"UnknownTaCZField\"",
                "\"codpattern.zombies.roomId\"", "\"codpattern.zombies.maxReserveAmmo\"");
        CompoundTag serialized = TagParser.parseTag(source);
        ItemStack stack = ItemStack.of(serialized);
        require(!stack.isEmpty(), "fixture ItemStack must decode");
        CompoundTag saved = stack.save(new CompoundTag());
        require(serialized.equals(saved), "ItemStack must preserve its exact serialized compound on read/write");

        ZombiesWeaponItemStackService service = new ZombiesWeaponItemStackService();
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> result =
                service.readWeaponTags(stack.getTag());
        require(result.success(), "zombies ItemStack tags must decode through the production service");
        ZombiesWeaponItemStackService.ZombiesWeaponTagData data = result.value().orElseThrow();
        require("zombies|Zombies_MixedCase_03".equals(data.roomId()), "ItemStack room key case must survive");
        require("fixture-instance-01".equals(data.instanceId()), "ItemStack instance ID must survive");
        require("tacz:m4a1".equals(data.gunId()), "ItemStack gun ID must survive");
        require("Epic_MixedCase".equals(data.rarityId()), "ItemStack rarity case must survive");
        require(data.weaponLevel() == 3 && data.upgradeLevel() == 2,
                "ItemStack weapon and upgrade levels must survive");
        require(data.reserveAmmo() == 77 && data.maxReserveAmmo() == 140,
                "ItemStack reserve-ammo fields must survive");

        CompoundTag itemTag = stack.getTag();
        require(itemTag != null, "fixture ItemStack must retain a tag");
        CompoundTag foreignBefore = itemTag.getCompound("UnknownTaCZField").copy();
        service.writeTag(itemTag, data);
        require(foreignBefore.equals(itemTag.getCompound("UnknownTaCZField")),
                "rewriting zombies tags must preserve unknown TaCZ fields");
        require("tacz:m4a1".equals(itemTag.getString("GunId")),
                "rewriting zombies tags must preserve the foreign TaCZ GunId key");
        service.stripWeaponTags(stack);
        require(foreignBefore.equals(Objects.requireNonNull(stack.getTag()).getCompound("UnknownTaCZField")),
                "stripping zombies tags must preserve unknown TaCZ fields");
        require("tacz:m4a1".equals(stack.getTag().getString("GunId")),
                "stripping zombies tags must preserve the foreign TaCZ GunId key");
        require(!stack.getTag().contains(ZombiesWeaponItemStackService.TAG_ROOM_ID),
                "strip must remove zombies room tag");
        require(!stack.getTag().contains(ZombiesWeaponItemStackService.TAG_MAX_RESERVE_AMMO),
                "strip must remove zombies max-reserve tag");
        require(source.equals(readFixture("nbt/zombies-item-stack.snbt")),
                "ItemStack source fixture must remain untouched");
    }

    private static Path copyFixture(Path tempRoot, String relativePath) throws IOException {
        Path target = tempRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, readFixture(relativePath));
        return target;
    }

    private static String readFixture(String relativePath) throws IOException {
        return Files.readString(FIXTURE_ROOT.resolve(relativePath));
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
