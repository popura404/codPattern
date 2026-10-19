package com.cdp.codpattern.config;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
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
