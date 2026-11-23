package com.cdp.codpattern.config;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses temporary files only; exercises the actual repository error boundaries. */
public final class ConfigRepositoryFailureCompatTest {
    private static final String BACKPACK_JSON = """
            {"playerData":{"saved-player":{"selectedBackpack":1,"backpacks_MAP":{
              "1":{"name":"original","item_MAP":{"primary":{
                "item":"tacz:modern_kinetic_gun","count":1,"nbt":"{GunId:'tacz:m4a1'}",
                "attachmentPreset":"{scope:'tacz:test_scope'}"}}}}}}}
            """;

    private ConfigRepositoryFailureCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<Field, Object> state = captureRepositoryState();
        Path root = Files.createTempDirectory("cdp-config-failure-test-");
        try {
            verifyBackpackRoundTrip(root.resolve("backpack-normal.json"));
            verifyBackpackSlotMigration(root.resolve("backpack-slots.json"));
            verifyFilterRoundTrip(root.resolve("filter-normal.json"));
            verifyMissingFilesAreCreated(root.resolve("missing"));
            verifyReadFailures(root.resolve("unreadable-directory"));
            verifyBackpackParseFailures(root.resolve("backpack-invalid.json"));
            verifyFilterParseFailures(root.resolve("filter-invalid.json"));
            verifyBackpackWriteFailureReachesCaller(root.resolve("backpack-write.json"));
            verifyAddBackpackWriteFailureRollsBack(root.resolve("backpack-add.json"));
            verifyFilterWriteFailureDoesNotPublish(root.resolve("filter-write.json"));
            System.out.println("PASS configuration repository read, parse, write failure propagation and recovery");
        } finally {
            for (Map.Entry<Field, Object> entry : state.entrySet()) {
                entry.getKey().set(null, entry.getValue());
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyBackpackRoundTrip(Path path) throws Exception {
        Files.writeString(path, BACKPACK_JSON);
        BackpackConfig config = BackpackConfigRepository.loadOrCreate(path);
        var backpack = config.getPlayerData().get("saved-player").getBackpacks_MAP().get(1);
        backpack.setName("saved");
        BackpackConfigRepository.save();
        BackpackConfig restored = BackpackConfigRepository.loadOrCreate(path);
        var saved = restored.getPlayerData().get("saved-player").getBackpacks_MAP().get(1);
        require("saved".equals(saved.getName()), "normal backpack write was not persisted");
        require("{scope:'tacz:test_scope'}".equals(saved.getItem_MAP().get("primary").getAttachmentPreset()),
                "attachment preset was not preserved by repository round trip");
    }

    private static void verifyFilterRoundTrip(Path path) throws Exception {
        Files.writeString(path, "{\"blockedAttachmentIds\":[\" TEST:Scope \",\"test:scope\"]}");
        WeaponFilterConfig config = WeaponFilterConfigRepository.loadOrCreate(path);
        require(config.getBlockedAttachmentIds().equals(java.util.List.of("test:scope")),
                "normal filter normalization changed");
        config.setThrowablesEnabled(false);
        WeaponFilterConfigRepository.save(config);
        require(!WeaponFilterConfigRepository.loadOrCreate(path).isThrowablesEnabled(),
                "normal filter write was not persisted");
    }

    private static void verifyBackpackSlotMigration(Path path) throws Exception {
        String legacy = """
                {"playerData":{"legacy":{"selectedBackpack":4,"backpacks_MAP":{
                  "4":{"name":"Custom","item_MAP":{
                    "primary":{"item":"tacz:modern_kinetic_gun","count":1,"attachmentPreset":"KeepCase"},
                    "secondary":{"item":"lrtactical:melee","count":1,"nbt":"{MeleeWeaponId:'pack:Knife'}"},
                    "tactical":{"item":"lrtactical:throwable","count":2,"nbt":"{ThrowableId:'pack:One'}"},
                    "lethal":{"item":"lrtactical:throwable","count":3,"nbt":"{ThrowableId:'pack:Two'}"}
                  }}}}}}
                """;
        Files.writeString(path, legacy);
        var config = BackpackConfigRepository.loadOrCreate(path);
        require(Files.readString(path).equals(legacy), "reading legacy slots must not rewrite the save");
        for (int i = 0; i < 2; i++) {
            var player = config.getPlayerData().get("legacy");
            var bag = player.getBackpacks_MAP().get(player.getSelectedBackpack());
            require(bag != null && bag.getName().equals("Custom")
                            && bag.getItem_MAP().keySet().equals(java.util.Set.of("primary", "secondary", "melee", "item1", "item2")),
                    "legacy backpack identity and all five canonical slots must survive");
            require(bag.getItem(BackpackSlot.PRIMARY).getAttachmentPreset().equals("KeepCase")
                            && bag.getItem(BackpackSlot.SECONDARY).getItem().isEmpty()
                            && bag.getItem(BackpackSlot.MELEE).getNbt().equals("{MeleeWeaponId:'pack:Knife'}"),
                    "legacy melee must move out of its former weapon slot without losing its data");
            require(bag.getItem(BackpackSlot.ITEM1).getCount() == 2
                            && bag.getItem(BackpackSlot.ITEM1).getNbt().equals("{ThrowableId:'pack:One'}")
                            && bag.getItem(BackpackSlot.ITEM2).getCount() == 3
                            && bag.getItem(BackpackSlot.ITEM2).getNbt().equals("{ThrowableId:'pack:Two'}"),
                    "equipment migration must preserve order, counts and NBT");
            BackpackConfigRepository.save();
            config = BackpackConfigRepository.loadOrCreate(path);
        }
        var defaults = config.getOrCreatePlayerData("new");
        int custom = BackpackConfigRepository.addCustomBackpack("new");
        require(custom == 4 && defaults.getSelectedBackpack() == 1, "adding a backpack changed the selection");
        for (var bag : defaults.getBackpacks_MAP().values()) {
            for (var slot : BackpackSlot.values()) {
                require(bag.getItem(slot) != null, "preset is missing slot " + slot);
            }
        }
        var customBag = defaults.getBackpacks_MAP().get(custom);
        var equipment = new BackpackConfig.Backpack.ItemData("lrtactical:throwable", 2, "{ThrowableId:'pack:One'}");
        customBag.setItem_MAP("tactical", equipment);
        customBag.setItem_MAP("perk", equipment);
        require(customBag.getItem(BackpackSlot.ITEM1) == equipment && customBag.getItem_MAP().size() == 5,
                "legacy addon writes must update their equipment slot without adding extra categories");

        String doubleMelee = legacy.replace("tacz:modern_kinetic_gun", "lrtactical:flash_shield");
        Files.writeString(path, doubleMelee);
        var doubleBag = BackpackConfigRepository.loadOrCreate(path).getPlayerData().get("legacy").getBackpacks_MAP().get(4);
        require(doubleBag.getItem(BackpackSlot.PRIMARY).getItem().isEmpty()
                        && doubleBag.getItem(BackpackSlot.SECONDARY).getItem().isEmpty()
                        && doubleBag.getItem(BackpackSlot.MELEE).getNbt().equals("{MeleeWeaponId:'pack:Knife'}")
                        && doubleBag.getItem_MAP().size() == 5,
                "double-melee loadouts must move the secondary and free both firearm slots");
        BackpackConfigRepository.save();
        var savedBag = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonObject("playerData").getAsJsonObject("legacy").getAsJsonObject("backpacks_MAP").getAsJsonObject("4");
        var displaced = savedBag.getAsJsonObject("displacedLegacyMelee").getAsJsonObject("primary");
        require(displaced.get("item").getAsString().equals("lrtactical:flash_shield")
                        && displaced.get("attachmentPreset").getAsString().equals("KeepCase"),
                "the surplus melee must retain its original data outside the active equipment map");
    }

    private static void verifyMissingFilesAreCreated(Path root) {
        Path backpack = root.resolve("backpack.json");
        Path filter = root.resolve("filter.json");
        require(BackpackConfigRepository.loadOrCreate(backpack) != null && Files.isRegularFile(backpack),
                "a genuinely missing backpack file must still receive defaults");
        require(WeaponFilterConfigRepository.loadOrCreate(filter) != null && Files.isRegularFile(filter),
                "a genuinely missing filter file must still receive defaults");
    }

    private static void verifyReadFailures(Path path) throws Exception {
        Files.createDirectory(path);
        expectBackpackFailure(() -> BackpackConfigRepository.loadOrCreate(path),
                BackpackConfigRepository.Operation.READ, path);
        expectFilterFailure(() -> WeaponFilterConfigRepository.loadOrCreate(path),
                WeaponFilterConfigRepository.Operation.READ, path);
        require(Files.isDirectory(path), "failed reads must not replace the unreadable source");
    }

    private static void verifyBackpackParseFailures(Path path) throws Exception {
        for (String broken : new String[]{"{\"playerData\":", "null", "", "{\"playerData\":null}"}) {
            Files.writeString(path, BACKPACK_JSON);
            BackpackConfigRepository.loadOrCreate(path);
            Files.writeString(path, broken);
            expectBackpackFailure(() -> BackpackConfigRepository.loadOrCreate(path),
                    BackpackConfigRepository.Operation.PARSE, path);
            expectBackpackFailure(BackpackConfigRepository::save,
                    BackpackConfigRepository.Operation.PARSE, path);
            expectBackpackFailure(() -> BackpackConfigRepository.loadOrCreatePlayer("saved-player", path),
                    BackpackConfigRepository.Operation.PARSE, path);
            require(broken.equals(Files.readString(path)),
                    "failed parse/retry must not overwrite a broken backpack file with cached data/defaults");
        }
        Files.writeString(path, BACKPACK_JSON);
        require(BackpackConfigRepository.loadOrCreate(path).getPlayerData().containsKey("saved-player"),
                "backpack repository must recover after the file is repaired");
    }

    private static void verifyFilterParseFailures(Path path) throws Exception {
        for (String broken : new String[]{"{\"blockedAttachmentIds\":", "null", "", "{\"blockedAttachmentIds\":42}"}) {
            Files.writeString(path, "{}");
            WeaponFilterConfig original = WeaponFilterConfigRepository.loadOrCreate(path);
            Files.writeString(path, broken);
            expectFilterFailure(() -> WeaponFilterConfigRepository.loadOrCreate(path),
                    WeaponFilterConfigRepository.Operation.PARSE, path);
            expectFilterFailure(() -> WeaponFilterConfigRepository.save(original),
                    WeaponFilterConfigRepository.Operation.PARSE, path);
            expectFilterFailure(() -> WeaponFilterConfigRepository.loadOrCreate(path),
                    WeaponFilterConfigRepository.Operation.PARSE, path);
            require(broken.equals(Files.readString(path)),
                    "failed parse/retry must not overwrite a broken filter file with cached data/defaults");
        }
        Files.writeString(path, "{}");
        require(WeaponFilterConfigRepository.loadOrCreate(path) != null,
                "filter repository must recover after the file is repaired");
    }

    private static void verifyBackpackWriteFailureReachesCaller(Path path) throws Exception {
        Files.writeString(path, BACKPACK_JSON);
        BackpackConfig config = BackpackConfigRepository.loadOrCreate(path);
        var backpack = config.getPlayerData().get("saved-player").getBackpacks_MAP().get(1);
        Files.delete(path);
        Files.createDirectory(path);
        String previous = backpack.getName();
        backpack.setName("unsaved mutation");
        try {
            expectBackpackFailure(BackpackConfigRepository::save,
                    BackpackConfigRepository.Operation.WRITE, path);
        } finally {
            // The save service owns this rollback; the repository must let the failure reach it.
            backpack.setName(previous);
        }
        require(BackpackConfigRepository.getConfig() == config && "original".equals(backpack.getName()),
                "write failure must preserve the existing in-memory object for caller rollback");
        Files.delete(path);
        BackpackConfigRepository.save();
        require("original".equals(BackpackConfigRepository.loadOrCreate(path)
                        .getPlayerData().get("saved-player").getBackpacks_MAP().get(1).getName()),
                "backpack save must recover once the write destination is repaired");
    }

    private static void verifyFilterWriteFailureDoesNotPublish(Path path) throws Exception {
        WeaponFilterConfig original = WeaponFilterConfigRepository.loadOrCreate(path);
        Files.delete(path);
        Files.createDirectory(path);
        WeaponFilterConfig unsaved = new WeaponFilterConfig();
        unsaved.setThrowablesEnabled(false);
        expectFilterFailure(() -> WeaponFilterConfigRepository.save(unsaved),
                WeaponFilterConfigRepository.Operation.WRITE, path);
        require(WeaponFilterConfigRepository.getConfig() == original,
                "failed filter save must not publish the unsaved replacement");
        Files.delete(path);
        WeaponFilterConfigRepository.save(unsaved);
        require(!WeaponFilterConfigRepository.loadOrCreate(path).isThrowablesEnabled(),
                "filter save must recover once the write destination is repaired");
    }

    private static void verifyAddBackpackWriteFailureRollsBack(Path path) throws Exception {
        Files.writeString(path, BACKPACK_JSON);
        BackpackConfig config = BackpackConfigRepository.loadOrCreate(path);
        var playerData = config.getPlayerData().get("saved-player");
        int previousCount = playerData.getBackpackCount();
        int previousSelected = playerData.getSelectedBackpack();
        Files.delete(path);
        Files.createDirectory(path);
        expectBackpackFailure(() -> BackpackConfigRepository.addCustomBackpack("saved-player"),
                BackpackConfigRepository.Operation.WRITE, path);
        require(playerData.getBackpackCount() == previousCount
                        && playerData.getSelectedBackpack() == previousSelected,
                "failed add must not leave an unsaved backpack or alter selection");
    }

    private static void expectBackpackFailure(Runnable action, BackpackConfigRepository.Operation operation, Path path) {
        try {
            action.run();
            throw new AssertionError("backpack " + operation + " failure was swallowed");
        } catch (BackpackConfigRepository.RepositoryException failure) {
            require(failure.getOperation() == operation, "wrong backpack failure classification");
            require(path.equals(failure.getPath()) && failure.getCause() != null,
                    "backpack failure must carry its path and original cause");
            verifyFeedback(failure, operation.name());
        }
    }

    private static void expectFilterFailure(Runnable action, WeaponFilterConfigRepository.Operation operation, Path path) {
        try {
            action.run();
            throw new AssertionError("filter " + operation + " failure was swallowed");
        } catch (WeaponFilterConfigRepository.RepositoryException failure) {
            require(failure.getOperation() == operation, "wrong filter failure classification");
            require(path.equals(failure.getPath()) && failure.getCause() != null,
                    "filter failure must carry its path and original cause");
            verifyFeedback(failure, operation.name());
        }
    }

    private static void verifyFeedback(Throwable failure, String operation) {
        require(ConfigFailureFeedback.translationKey(failure).equals(
                        "message.codpattern.config." + operation.toLowerCase(java.util.Locale.ROOT) + "_failed"),
                "player feedback must distinguish reading, parsing and saving without exposing the path");
        require(ConfigFailureFeedback.resultCode(failure).equals("CONFIG_" + operation + "_FAILED"),
                "failure reply must preserve the operation classification");
    }

    private static Map<Field, Object> captureRepositoryState() throws ReflectiveOperationException {
        Map<Field, Object> result = new LinkedHashMap<>();
        for (Class<?> type : new Class<?>[]{BackpackConfigRepository.class, WeaponFilterConfigRepository.class}) {
            for (String name : new String[]{"serverConfigPath", "serverConfig", "loadFailure"}) {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                result.put(field, field.get(null));
            }
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
