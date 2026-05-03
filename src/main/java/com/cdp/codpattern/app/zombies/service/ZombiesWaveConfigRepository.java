package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import com.cdp.codpattern.config.zombies.ZombiesRulesRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZombiesWaveConfigRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern WAVE_FILE_PATTERN = Pattern.compile("^wave_(\\d+).*\\.json$");

    private final Path wavesDirectory;
    private final ZombiesWaveValidator validator;
    private final ZombiesRulesConfig.Defaults defaults;

    public ZombiesWaveConfigRepository(Path wavesDirectory) {
        this(wavesDirectory, ZombiesRulesRepository.getConfig().getDefaults(), new ZombiesWaveValidator());
    }

    public ZombiesWaveConfigRepository(Path wavesDirectory, ZombiesWaveValidator validator) {
        this(wavesDirectory, ZombiesRulesRepository.getConfig().getDefaults(), validator);
    }

    public ZombiesWaveConfigRepository(Path wavesDirectory, ZombiesRulesConfig.Defaults defaults) {
        this(wavesDirectory, defaults, new ZombiesWaveValidator());
    }

    public ZombiesWaveConfigRepository(
            Path wavesDirectory,
            ZombiesRulesConfig.Defaults defaults,
            ZombiesWaveValidator validator) {
        this.wavesDirectory = wavesDirectory;
        this.defaults = defaults != null ? defaults : new ZombiesRulesConfig.Defaults();
        this.validator = validator != null ? validator : new ZombiesWaveValidator();
    }

    public LoadResult load() {
        List<ZombiesWaveDefinition> waves = new ArrayList<>();
        List<ZombiesWaveValidator.ValidationIssue> loadIssues = new ArrayList<>();

        if (wavesDirectory != null && Files.isDirectory(wavesDirectory)) {
            try (var stream = Files.list(wavesDirectory)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> WAVE_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();
                for (Path file : files) {
                    readWave(file).ifPresentOrElse(waves::add, () -> loadIssues.add(new ZombiesWaveValidator.ValidationIssue(
                            "rules.wave_invalid",
                            file,
                            "Wave file could not be parsed")));
                }
            } catch (IOException e) {
                loadIssues.add(new ZombiesWaveValidator.ValidationIssue(
                        "rules.wave_invalid",
                        wavesDirectory,
                        "Wave directory could not be scanned: " + e.getMessage()));
            }
        }

        waves.sort(Comparator.comparingInt(ZombiesWaveDefinition::getWave));
        ZombiesWaveValidator.ValidationReport validationReport = validator.validate(waves);
        List<ZombiesWaveValidator.ValidationIssue> issues = new ArrayList<>(loadIssues);
        issues.addAll(validationReport.getIssues());
        return new LoadResult(waves, issues);
    }

    public Path getWavesDirectory() {
        return wavesDirectory;
    }

    private Optional<ZombiesWaveDefinition> readWave(Path file) {
        Matcher matcher = WAVE_FILE_PATTERN.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        int fileWave = Integer.parseInt(matcher.group(1));
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement element = GSON.fromJson(reader, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = element.getAsJsonObject();
            ZombiesWaveDefinition wave = GSON.fromJson(object, ZombiesWaveDefinition.class);
            if (wave == null) {
                return Optional.empty();
            }
            wave.attachSource(file, fileWave, object.has("mobs"));
            wave.applyDefaults(defaults);
            return Optional.of(wave);
        } catch (IOException | JsonParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static final class LoadResult {
        private final List<ZombiesWaveDefinition> waves;
        private final List<ZombiesWaveValidator.ValidationIssue> issues;

        private LoadResult(List<ZombiesWaveDefinition> waves, List<ZombiesWaveValidator.ValidationIssue> issues) {
            this.waves = List.copyOf(waves);
            this.issues = List.copyOf(issues);
        }

        public boolean isValid() {
            return issues.isEmpty();
        }

        public List<ZombiesWaveDefinition> getWaves() {
            return waves;
        }

        public List<ZombiesWaveValidator.ValidationIssue> getIssues() {
            return issues;
        }

        public int getMaxWave() {
            return waves.stream()
                    .filter(wave -> wave.hasMobsField() && !wave.hasWaveConflict())
                    .mapToInt(ZombiesWaveDefinition::getWave)
                    .max()
                    .orElse(0);
        }
    }
}
