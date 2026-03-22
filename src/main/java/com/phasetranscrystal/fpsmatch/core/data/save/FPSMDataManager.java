package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
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
    private final Map<Class<?>, Pair<String, ISavePort<?>>> registry = new HashMap<>();
    private final List<Consumer<FPSMDataManager>> writeActions = new ArrayList<>();
    private final File levelData;
    private final File globalData;

    public FPSMDataManager(String levelName) {
        String fixedLevelName = fixName(levelName);
        File root = new File(FMLLoader.getGamePath().toFile(), "fpsmatch");
        this.levelData = new File(root, fixedLevelName);
        this.globalData = resolveGlobalData(root);
        if (!globalData.exists() && !globalData.mkdirs()) {
            throw new RuntimeException("Failed to create global save directory " + globalData);
        }
    }

    public <T> void registerData(Class<T> clazz, String folderName, SaveHolder<T> saveHolder) {
        String fixedFolderName = fixName(folderName);
        registry.put(clazz, Pair.of(fixedFolderName, saveHolder));
        writeActions.add(saveHolder.writeHandler());
        File dataFolder = new File(saveHolder.isGlobal() ? globalData : levelData, fixedFolderName);
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
        File targetDir = new File(savePort.isGlobal() ? globalData : levelData, pair.getFirst());
        savePort.getWriter(data, fixName(fileName), overwrite).accept(targetDir);
    }

    public <T> void saveData(T data, String fileName) {
        saveData(data, fileName, false);
    }

    public void saveData() {
        checkOrCreateFile(levelData);
        checkOrCreateFile(globalData);
        for (Consumer<FPSMDataManager> action : writeActions) {
            action.accept(this);
        }
    }

    public void readData() {
        for (Pair<String, ISavePort<?>> pair : registry.values()) {
            ISavePort<?> savePort = pair.getSecond();
            File targetDir = new File(savePort.isGlobal() ? globalData : levelData, pair.getFirst());
            savePort.getReader().accept(targetDir);
        }
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
