package com.cdp.codpattern.config.weaponfilter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class WeaponFilterConfigRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeaponFilterConfigRepository.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path serverConfigPath;
    private static WeaponFilterConfig serverConfig;
    private static RepositoryException loadFailure;

    public enum Operation { READ, PARSE, WRITE }

    public static final class RepositoryException extends IllegalStateException {
        private final Operation operation;
        private final Path path;

        private RepositoryException(Operation operation, Path path, Throwable cause) {
            super("Cannot " + operation.name().toLowerCase(java.util.Locale.ROOT)
                    + " weapon filter configuration at " + path, cause);
            this.operation = operation;
            this.path = path;
        }

        public Operation getOperation() { return operation; }
        public Path getPath() { return path; }
    }

    private WeaponFilterConfigRepository() {
    }

    public static WeaponFilterConfig loadOrCreate(Path path) {
        try {
            WeaponFilterConfig loaded = readOrCreate(path);
            serverConfigPath = path;
            serverConfig = loaded;
            loadFailure = null;
            return loaded;
        } catch (RepositoryException failure) {
            serverConfigPath = null;
            serverConfig = null;
            loadFailure = failure;
            throw failure;
        }
    }

    private static WeaponFilterConfig readOrCreate(Path path) {
        String configJson;
        try {
            configJson = Files.readString(path);
        } catch (NoSuchFileException missing) {
            WeaponFilterConfig created = new WeaponFilterConfig();
            created.normalize();
            write(path, created);
            return created;
        } catch (IOException | SecurityException failure) {
            throw failure(Operation.READ, path, failure);
        }

        WeaponFilterConfig loaded;
        String normalizedJson;
        try {
            loaded = GSON.fromJson(configJson, WeaponFilterConfig.class);
            if (loaded == null) {
                throw new JsonParseException("Expected a weapon filter configuration object");
            }
            loaded.normalize();
            normalizedJson = GSON.toJson(loaded);
        } catch (RuntimeException failure) {
            throw failure(Operation.PARSE, path, failure);
        }
        if (!normalizedJson.equals(configJson)) {
            write(path, loaded);
        }
        return loaded;
    }

    public static WeaponFilterConfig getConfig() {
        if (loadFailure != null) {
            throw loadFailure;
        }
        return serverConfig;
    }

    public static void setConfig(WeaponFilterConfig config) {
        serverConfig = config;
    }

    public static void save(WeaponFilterConfig config) {
        if (loadFailure != null) {
            throw loadFailure;
        }
        if (config == null || serverConfigPath == null) {
            throw failure(Operation.WRITE, serverConfigPath,
                    new IllegalStateException("Weapon filter configuration has not been loaded"));
        }
        write(serverConfigPath, config);
        serverConfig = config;
    }

    private static void write(Path path, WeaponFilterConfig config) {
        try {
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
        LOGGER.error("Weapon filter configuration {} failed at {}", operation, path, cause);
        return failure;
    }
}
