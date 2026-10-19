package com.cdp.codpattern.config.backpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class BackpackConfigRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackpackConfigRepository.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .create();

    private static Path serverConfigPath;
    private static BackpackConfig serverConfig;
    private static RepositoryException loadFailure;

    public enum Operation { READ, PARSE, WRITE }

    public static final class RepositoryException extends IllegalStateException {
        private final Operation operation;
        private final Path path;

        private RepositoryException(Operation operation, Path path, Throwable cause) {
            super("Cannot " + operation.name().toLowerCase(java.util.Locale.ROOT)
                    + " backpack configuration at " + path, cause);
            this.operation = operation;
            this.path = path;
        }

        public Operation getOperation() { return operation; }
        public Path getPath() { return path; }
    }

    private BackpackConfigRepository() {
    }

    public static BackpackConfig loadOrCreate(Path path) {
        try {
            BackpackConfig loaded = readOrCreate(path);
            serverConfigPath = path;
            serverConfig = loaded;
            loadFailure = null;
            return loaded;
        } catch (RepositoryException failure) {
            // Never let a later cached read/save replace an unreadable configuration.
            serverConfigPath = null;
            serverConfig = null;
            loadFailure = failure;
            throw failure;
        }
    }

    private static BackpackConfig readOrCreate(Path path) {
        String json;
        try {
            json = Files.readString(path);
        } catch (NoSuchFileException missing) {
            BackpackConfig created = new BackpackConfig();
            write(path, created);
            return created;
        } catch (IOException | SecurityException failure) {
            throw failure(Operation.READ, path, failure);
        }
        try {
            BackpackConfig loaded = GSON.fromJson(json, BackpackConfig.class);
            if (loaded == null || loaded.getPlayerData() == null) {
                throw new JsonParseException("Expected a backpack configuration object with player data");
            }
            for (BackpackConfig.PlayerBackpackData player : loaded.getPlayerData().values()) {
                if (player == null || player.getBackpacks_MAP() == null) {
                    throw new JsonParseException("Player backpack data must contain a backpack map");
                }
                for (BackpackConfig.Backpack backpack : player.getBackpacks_MAP().values()) {
                    if (backpack == null || backpack.getItem_MAP() == null) {
                        throw new JsonParseException("Backpack data must contain an item map");
                    }
                }
            }
            return loaded;
        } catch (RuntimeException failure) {
            throw failure(Operation.PARSE, path, failure);
        }
    }

    public static BackpackConfig.PlayerBackpackData loadOrCreatePlayer(String uuid, Path path) {
        ensureLoaded(path);
        BackpackConfig.PlayerBackpackData playerData = serverConfig.getOrCreatePlayerData(uuid);
        save();
        return playerData;
    }

    public static BackpackConfig getConfig() {
        if (loadFailure != null) {
            throw loadFailure;
        }
        if (serverConfig == null) {
            serverConfig = new BackpackConfig();
        }
        return serverConfig;
    }

    public static void setConfig(BackpackConfig config) {
        serverConfig = config;
    }

    public static BackpackConfig.PlayerBackpackData getOrCreatePlayerData(String uuid) {
        return getConfig().getOrCreatePlayerData(uuid);
    }

    public static int addCustomBackpack(String uuid) {
        BackpackConfig.PlayerBackpackData playerData = getConfig().getOrCreatePlayerData(uuid);
        int selectedBeforeAdd = playerData.getSelectedBackpack();
        int id = playerData.getNextAvailableId();
        if (id == 0) {
            return -1;
        }

        BackpackConfig.Backpack newBackpack = new BackpackConfig.Backpack("");
        newBackpack.setItem_MAP("primary", BackpackConfig.getItemDataADDP());
        newBackpack.setItem_MAP("secondary", BackpackConfig.getItemDataADDS());
        newBackpack.setItem_MAP("tactical", BackpackConfig.getItemDataTactical());
        newBackpack.setItem_MAP("lethal", BackpackConfig.getItemDataLethal());

        if (playerData.addBackpack(id, newBackpack)) {
            if (playerData.getBackpacks_MAP().containsKey(selectedBeforeAdd)) {
                playerData.setSelectedBackpack(selectedBeforeAdd);
            }
            try {
                save();
            } catch (RepositoryException failure) {
                playerData.getBackpacks_MAP().remove(id);
                playerData.setSelectedBackpack(selectedBeforeAdd);
                throw failure;
            }
            return id;
        }
        return -1;
    }

    public static void save() {
        if (loadFailure != null) {
            throw loadFailure;
        }
        if (serverConfigPath == null || serverConfig == null) {
            throw failure(Operation.WRITE, serverConfigPath,
                    new IllegalStateException("Backpack configuration has not been loaded"));
        }
        write(serverConfigPath, serverConfig);
    }

    private static void write(Path path, BackpackConfig config) {
        try {
            // Serialize before opening the destination so encoding failures cannot truncate it.
            String json = GSON.toJson(config);
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, json);
        } catch (IOException | RuntimeException failure) {
            throw failure(Operation.WRITE, path, failure);
        }
    }

    private static RepositoryException failure(Operation operation, Path path, Throwable cause) {
        RepositoryException failure = new RepositoryException(operation, path, cause);
        LOGGER.error("Backpack configuration {} failed at {}", operation, path, cause);
        return failure;
    }

    private static void ensureLoaded(Path path) {
        if (serverConfig == null || serverConfigPath == null || !serverConfigPath.equals(path)) {
            loadOrCreate(path);
        }
    }
}
