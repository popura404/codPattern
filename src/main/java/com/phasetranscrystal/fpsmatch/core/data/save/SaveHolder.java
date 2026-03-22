package com.phasetranscrystal.fpsmatch.core.data.save;

import com.mojang.serialization.Codec;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class SaveHolder<T> implements ISavePort<T> {
    private final Codec<T> codec;
    private final Consumer<T> readHandler;
    private final Consumer<FPSMDataManager> writeHandler;
    private final boolean global;
    private final BiFunction<T, T, T> mergeHandler;
    private final String fileType;

    private SaveHolder(
            Codec<T> codec,
            Consumer<T> readHandler,
            Consumer<FPSMDataManager> writeHandler,
            boolean global,
            BiFunction<T, T, T> mergeHandler,
            String fileType
    ) {
        this.codec = codec;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.global = global;
        this.mergeHandler = mergeHandler;
        this.fileType = fileType;
    }

    @Override
    public Codec<T> codec() {
        return codec;
    }

    @Override
    public Consumer<T> readHandler() {
        return readHandler;
    }

    public Consumer<FPSMDataManager> writeHandler() {
        return writeHandler;
    }

    @Override
    public boolean isGlobal() {
        return global;
    }

    @Override
    public T mergeHandler(T oldData, T newData) {
        return mergeHandler == null ? newData : mergeHandler.apply(oldData, newData);
    }

    @Override
    public String getFileType() {
        return fileType;
    }

    public static class Builder<T> {
        private final Codec<T> codec;
        private Consumer<T> readHandler = data -> {
        };
        private Consumer<FPSMDataManager> writeHandler = manager -> {
        };
        private boolean global;
        private BiFunction<T, T, T> mergeHandler = (oldData, newData) -> newData;
        private String fileType = "json";

        public Builder(Codec<T> codec) {
            this.codec = codec;
        }

        public Builder<T> withReadHandler(Consumer<T> readHandler) {
            this.readHandler = readHandler;
            return this;
        }

        public Builder<T> withWriteHandler(Consumer<FPSMDataManager> writeHandler) {
            this.writeHandler = writeHandler;
            return this;
        }

        public Builder<T> isGlobal(boolean global) {
            this.global = global;
            return this;
        }

        public Builder<T> withMergeHandler(BiFunction<T, T, T> mergeHandler) {
            this.mergeHandler = mergeHandler;
            return this;
        }

        public Builder<T> withFileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public SaveHolder<T> build() {
            return new SaveHolder<>(codec, readHandler, writeHandler, global, mergeHandler, fileType);
        }
    }
}
