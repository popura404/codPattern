package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.cdp.codpattern.config.storage.*;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.util.Pair;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FPSMDataManager {
    public enum DeleteStatus {
        DELETED,
        NOT_FOUND,
        FAILED
    }

    private final Map<Class<?>, Pair<String, ISavePort<?>>> registry = new HashMap<>();
    private final List<Consumer<FPSMDataManager>> writeActions = new ArrayList<>();
    private final File levelData;
    private File globalData;
    private final File legacyRoot;
    private final ServerMapStorage storage;
    private final Map<Class<?>, String> mapTypes = new HashMap<>();

    public FPSMDataManager(MinecraftServer server) {
        storage = ServerMapStorage.get(server);
        legacyRoot = storage.paths().legacy().toFile();
        levelData = new File(legacyRoot, fixName(server.getWorldData().getLevelName()));
    }

    /** Kept for existing callers; new storage is always bound to the active save. */
    public FPSMDataManager(String levelName) {
        this(java.util.Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "Server not ready"));
    }

    private File directory(String folder, ISavePort<?> port) {
        if (!port.isGlobal()) return new File(levelData, folder);
        if (globalData == null) globalData = resolveGlobalData(legacyRoot);
        return new File(globalData, folder);
    }

    public <T> void registerMapData(Class<T> clazz, MapStorageRegistration registration, SaveHolder<T> holder) {
        if (holder.isGlobal() || registry.containsKey(clazz)) throw new IllegalArgumentException("Invalid map registration");
        storage.migration().register(registration);
        registry.put(clazz, Pair.of(registration.directory(), holder));
        mapTypes.put(clazz, registration.mode());
        writeActions.add(manager -> {
            if (!storage.blocked(registration.mode())) holder.writeHandler().accept(manager);
        });
    }

    public <T> void registerData(Class<T> clazz, String folderName, SaveHolder<T> saveHolder) {
        String fixedFolderName = fixName(folderName);
        registry.put(clazz, Pair.of(fixedFolderName, saveHolder));
        writeActions.add(saveHolder.writeHandler());
        File dataFolder = directory(fixedFolderName, saveHolder);
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new RuntimeException("Failed to create data folder " + dataFolder);
        }
    }

    public <T> void saveData(T data, String fileName, boolean overwrite) {
        Pair<String, ISavePort<?>> pair = registry.get(data.getClass());
        if (pair == null) {
            throw new RuntimeException("Unregistered save data " + data.getClass().getName());
        }
        ISavePort<T> savePort = (ISavePort<T>) pair.getSecond();
        if (mapTypes.containsKey(data.getClass())) {
            String mode = mapTypes.get(data.getClass());
            storage.requireAvailable(mode);
            if (storage.migration().blocksMap(mode, fileName)) throw new IllegalStateException("Map requires migration: " + fileName);
            Path file = storage.paths().map(pair.getFirst(), fileName).resolve("map.json");
            try {
                T value = data;
                var encoded = savePort.encodeToJson(data).getAsJsonObject();
                if (!fileName.equals(encoded.get("mapName").getAsString())) throw new IllegalArgumentException("Map filename/identity mismatch");
                if (Files.exists(file)) {
                    StorageFiles.checkPath(file);
                    T old = savePort.decodeFromJson(JsonParser.parseString(Files.readString(file)));
                    if (!overwrite) value = savePort.mergeHandler(old, data);
                }
                StorageFiles.write(file, new GsonBuilder().setPrettyPrinting().create().toJson(savePort.encodeToJson(value)));
            } catch (Exception e) { throw new IllegalStateException("Failed to save map " + file, e); }
            return;
        }
        File targetDir = directory(pair.getFirst(), savePort);
        savePort.getWriter(data, fixName(fileName), overwrite).accept(targetDir);
    }

    public <T> void saveData(T data, String fileName) {
        saveData(data, fileName, false);
    }

    public DeleteStatus deleteData(Class<?> clazz, String fileName) {
        Pair<String, ISavePort<?>> pair = registry.get(clazz);
        if (pair == null) {
            throw new RuntimeException("Unregistered save data " + clazz.getName());
        }
        ISavePort<?> savePort = pair.getSecond();
        if (mapTypes.containsKey(clazz)) {
            String mode = mapTypes.get(clazz);
            try {
                storage.requireAvailable(mode);
                Path file = storage.paths().map(pair.getFirst(), fileName).resolve("map.json");
                if (!Files.exists(file)) return DeleteStatus.NOT_FOUND;
                storage.migration().archive(mode, fileName);
                return DeleteStatus.DELETED;
            } catch (Exception e) {
                LogUtils.getLogger().error("Failed to archive map {}/{}", mode, fileName, e);
                return DeleteStatus.FAILED;
            }
        }
        File targetDir = directory(pair.getFirst(), savePort);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return DeleteStatus.NOT_FOUND;
        }
        File targetFile = new File(targetDir, fixName(fileName) + "." + savePort.getFileType());
        if (!targetFile.exists()) {
            return DeleteStatus.NOT_FOUND;
        }
        return targetFile.delete() ? DeleteStatus.DELETED : DeleteStatus.FAILED;
    }

    public void saveData() {
        for (Consumer<FPSMDataManager> action : writeActions) {
            action.accept(this);
        }
    }

    public void readData() {
        for (var entry : registry.entrySet()) {
            if (mapTypes.containsKey(entry.getKey())) {
                readMaps(mapTypes.get(entry.getKey()), entry.getValue().getFirst(), entry.getValue().getSecond());
            } else {
                ISavePort<?> port = entry.getValue().getSecond();
                port.getReader().accept(directory(entry.getValue().getFirst(), port));
            }
        }
        storage.refresh();
    }

    private <T> void readMaps(String mode, String directory, ISavePort<T> port) {
        if (storage.blocked(mode)) return;
        Path root = storage.paths().mode(directory);
        try {
            StorageFiles.checkPath(root);
            if (!Files.isDirectory(root)) return;
            try (var folders = Files.list(root)) {
                for (Path folder : folders.toList()) {
                    Path file = folder.resolve("map.json");
                    if (!folder.getFileName().toString().startsWith("m-") || !Files.isRegularFile(file)) continue;
                    try {
                        StorageFiles.checkPath(file);
                        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                        String name = json.get("mapName").getAsString();
                        if (!storage.paths().map(directory, name).equals(folder)) throw new IllegalStateException("Map directory/name mismatch");
                        if (storage.migration().blocksMap(mode, name)) throw new IllegalStateException("Legacy rules or incomplete migration");
                        port.readHandler().accept(port.decodeFromJson(json));
                    } catch (Exception e) { LogUtils.getLogger().error("Cannot load map {}", file, e); }
                }
            }
        } catch (Exception e) { LogUtils.getLogger().error("Cannot scan mode maps {}", root, e); }
    }

    public static boolean checkOrCreateFile(File file) {
        return file.exists() || file.mkdirs();
    }

    public static String fixName(String fileName) {
        String fixed = fileName;
        for (String charToReplace : new String[]{"\\", "/", ":", "*", "?", "\"", "<", ">", "|"}) {
            fixed = fixed.replace(charToReplace, "");
        }
        return fixed;
    }

    private static File resolveGlobalData(File root) {
        File configFile = new File(root, "config.json");
        try {
            if (!configFile.exists()) {
                if (!root.exists() && !root.mkdirs()) {
                    throw new RuntimeException("Failed to create " + root);
                }
                File defaultGlobal = new File(root, "global");
                Map<String, String> config = Map.of("globalDataPath", defaultGlobal.getCanonicalPath());
                Files.writeString(configFile.toPath(), new Gson().toJson(config));
                return defaultGlobal;
            }
            Map<String, String> config = new Gson().fromJson(
                    Files.readString(configFile.toPath()),
                    new TypeToken<Map<String, String>>() {
                    }.getType()
            );
            String dataPath = config.get("globalDataPath");
            if (dataPath == null || dataPath.isBlank()) {
                throw new RuntimeException("Missing globalDataPath");
            }
            return new File(dataPath);
        } catch (Exception ignored) {
            return new File(root, "global");
        }
    }
}
