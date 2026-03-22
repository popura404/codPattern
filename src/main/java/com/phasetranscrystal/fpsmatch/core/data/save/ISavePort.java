package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.function.Consumer;

public interface ISavePort<T> {
    Codec<T> codec();

    default Consumer<T> readHandler() {
        return data -> {
        };
    }

    default T decodeFromJson(JsonElement json) {
        return codec().decode(JsonOps.INSTANCE, json)
                .getOrThrow(false, error -> {
                    throw new RuntimeException(error);
                })
                .getFirst();
    }

    default JsonElement encodeToJson(T data) {
        return codec().encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow(false, error -> {
                    throw new RuntimeException(error);
                });
    }

    default T readSpecificFile(File directory, String fileName) {
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }
        File file = new File(directory, fileName + "." + getFileType());
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try (FileReader reader = new FileReader(file)) {
            JsonElement element = new Gson().fromJson((Reader) reader, JsonElement.class);
            return decodeFromJson(element);
        } catch (Exception ignored) {
            return null;
        }
    }

    default boolean createNewDataFile(File directory, String fileName, T initialData) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            return false;
        }
        File file = new File(directory, fileName + "." + getFileType());
        if (file.exists() || !file.createNewFile()) {
            return false;
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(gson.toJson(encodeToJson(initialData)));
        }
        return true;
    }

    default Consumer<File> getReader() {
        return directory -> {
            if (!directory.exists()) {
                directory.mkdirs();
                return;
            }
            if (!directory.isDirectory()) {
                return;
            }
            for (File file : Objects.requireNonNull(directory.listFiles())) {
                if (!file.isFile() || !file.getName().endsWith("." + getFileType())) {
                    continue;
                }
                try (FileReader reader = new FileReader(file)) {
                    JsonElement element = new Gson().fromJson((Reader) reader, JsonElement.class);
                    T data = decodeFromJson(element);
                    readHandler().accept(data);
                } catch (Exception ignored) {
                }
            }
        };
    }

    default Consumer<File> getWriter(T data, String fileName, boolean overwrite) {
        return directory -> {
            if (!directory.exists() && !directory.mkdirs()) {
                throw new RuntimeException("Failed to create save directory " + directory);
            }
            if (!directory.isDirectory()) {
                throw new RuntimeException(directory + " is not a directory");
            }
            File file = new File(directory, fileName + "." + getFileType());
            try {
                if (!file.exists() && !file.createNewFile()) {
                    throw new RuntimeException("Failed to create save file " + file);
                }
                Object merged = data;
                if (!overwrite && file.length() > 0L) {
                    try (FileReader reader = new FileReader(file)) {
                        JsonElement element = new Gson().fromJson((Reader) reader, JsonElement.class);
                        if (element != null) {
                            T oldData = decodeFromJson(element);
                            merged = mergeHandler(oldData, data);
                        }
                    }
                }
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(gson.toJson(encodeToJson((T) merged)));
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to write save file " + file, e);
            }
        };
    }

    default boolean isGlobal() {
        return false;
    }

    default T mergeHandler(T oldData, T newData) {
        return newData;
    }

    default String getFileType() {
        return "json";
    }
}
