package com.cdp.codpattern.architecture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ModeSplitCompatibilityManifestCompatTest {
    private static final Path MANIFEST_PATH =
            Path.of("docs/mode-split/phase0/compatibility-manifest.json");
    private static final Path EVENT_BASELINE_PATH =
            Path.of("docs/mode-split/phase0/event-handler-baseline.tsv");
    private static final Path MAIN_JAVA = Path.of("src/main/java");

    private static final Pattern COMMAND_LITERAL =
            Pattern.compile("Commands\\.literal\\(\\\"([^\\\"]+)\\\"\\)");
    private static final Pattern FORMAT_TOKEN = Pattern.compile(
            "%(?:(?:\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAtTn%]");
    private static final Pattern EVENT_SUBSCRIBER = Pattern.compile(
            "@Mod\\.EventBusSubscriber\\s*\\((.*?)\\)", Pattern.DOTALL);
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "@SubscribeEvent(?:\\s*\\((.*?)\\))?\\s*"
                    + "(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?"
                    + "[\\w<>.?\\[\\], ]+\\s+(\\w+)\\s*\\((.*?)\\)\\s*\\{",
            Pattern.DOTALL);
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE = Pattern.compile(
            "\\b(?:public\\s+)?(?:final\\s+|abstract\\s+)?(?:class|enum|record)\\s+(\\w+)");

    private ModeSplitCompatibilityManifestCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        JsonObject manifest = readJson(MANIFEST_PATH);
        require(manifest.get("schemaVersion").getAsInt() == 1,
                "unsupported compatibility manifest schema");

        verifyLoader(manifest.getAsJsonObject("loader"));
        verifyModeIdentity(manifest.getAsJsonObject("modeIdentity"));
        verifyRoomIdentity(manifest.getAsJsonObject("roomIdentity"));
        verifyRegistries(manifest.getAsJsonObject("registries"));
        verifyCommands(manifest.getAsJsonObject("commands"));
        verifyPersistenceAndConfiguration(
                manifest.getAsJsonObject("persistence"),
                manifest.getAsJsonObject("configuration"));
        verifyNbt(manifest.getAsJsonObject("nbt"));
        verifyNetwork(manifest.getAsJsonObject("network"));
        verifyTranslations(manifest.getAsJsonArray("translations"));
        verifyEventHandlers(manifest.getAsJsonObject("eventHandlers"));
        verifyCombinedBootstrapSequence(manifest.getAsJsonArray("combinedBootstrapSequence"));

        System.out.println("PASS mode split compatibility manifest baseline");
    }

    private static void verifyLoader(JsonObject loader) throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        requireEquals(loader.get("minecraftVersion").getAsString(),
                properties.getProperty("minecraft_version"), "Minecraft version drifted");
        requireEquals(loader.get("forgeVersion").getAsString(),
                properties.getProperty("forge_version"), "Forge version drifted");
        requireEquals(loader.get("modId").getAsString(),
                properties.getProperty("mod_id"), "mod id drifted");

        String build = read(Path.of("build.gradle"));
        requireContains(build, "java.toolchain.languageVersion = JavaLanguageVersion.of(17)",
                "Forge 1.20.1 must stay on Java 17");
        requireContains(build, "id 'net.minecraftforge.gradle'", "ForgeGradle plugin disappeared");

        Path metadata = Path.of(loader.get("metadataFile").getAsString());
        require(Files.exists(metadata), "Forge mods.toml metadata file is missing");
        String modsToml = read(metadata);
        requireContains(modsToml, "modLoader=\"javafml\"", "Forge loader metadata drifted");
        requireContains(modsToml, "modId=\"${mod_id}\"", "templated mod id metadata drifted");

        String entryPoint = read(Path.of(
                "src/main/java/" + loader.get("entryPoint").getAsString().replace('.', '/') + ".java"));
        requireContains(entryPoint, "@Mod(CodPatternConstants.MOD_ID)",
                "Forge entry point annotation drifted");
        requireContains(entryPoint, "public static final String MODID = CodPatternConstants.MOD_ID;",
                "Forge entry point compatibility mod id drifted");
        String identityConstants = read(Path.of("src/main/java/com/cdp/codpattern/CodPatternConstants.java"));
        requireContains(identityConstants,
                "public static final String MOD_ID = \"" + loader.get("modId").getAsString() + "\";",
                "Forge entry point mod id drifted");
    }

    private static void verifyModeIdentity(JsonObject modeIdentity) throws Exception {
        String builtIn = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/match/BuiltInGameModes.java"));
        List<String> installedOrder = strings(modeIdentity.getAsJsonArray("installedOrder"));
        requireEquals(List.of("frontline", "teamdeathmatch", "zombies"), installedOrder,
                "manifest installed-mode order changed unexpectedly");
        for (String gameType : installedOrder) {
            requireContains(builtIn, "= \"" + gameType + "\";",
                    "missing built-in game type " + gameType);
        }

        JsonObject aliases = modeIdentity.getAsJsonObject("aliases");
        for (Map.Entry<String, JsonElement> alias : aliases.entrySet()) {
            requireContains(builtIn, "= \"" + alias.getKey() + "\";",
                    "missing built-in alias " + alias.getKey());
        }

        String tdmDefinitions = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/tdm/model/TdmGameModeDefinitions.java"));
        String tdmPolicies = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/tdm/model/TdmTeamMatchPolicies.java"));
        String zombiesDefinitions = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/model/ZombiesGameModeDefinitions.java"));
        requireBefore(tdmDefinitions, "List.of(frontline(), teamDeathmatch())",
                "private static GameModeDefinition frontline()",
                "Frontline and Team Deathmatch definition order drifted");
        requireContains(tdmPolicies, "List.of(BuiltInGameModes.LEGACY_CDP_TDM)",
                "Frontline legacy alias wiring drifted");
        requireContains(tdmPolicies, "List.of(BuiltInGameModes.LEGACY_CDP_TACTICAL_TDM)",
                "Team Deathmatch legacy alias wiring drifted");

        JsonObject templates = modeIdentity.getAsJsonObject("createCommandTemplates");
        requireContains(tdmPolicies, quote(templates.get("frontline").getAsString()),
                "Frontline create command template drifted");
        requireContains(tdmPolicies, quote(templates.get("teamdeathmatch").getAsString()),
                "Team Deathmatch create command template drifted");
        requireContains(zombiesDefinitions, quote(templates.get("zombies").getAsString()),
                "Zombies create command template drifted");
    }

    private static void verifyRoomIdentity(JsonObject roomIdentity) throws Exception {
        String roomId = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/match/model/RoomId.java"));
        requireContains(roomId,
                "private static final String SEPARATOR = \"" + roomIdentity.get("separator").getAsString() + "\";",
                "RoomId separator drifted");
        requireContains(roomId, "return Objects.requireNonNullElse(value, \"\").trim();",
                "RoomId trimming behavior drifted");
        requireNotContains(roomId, "toLowerCase(", "RoomId must remain case preserving");

        String registry = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/match/GameModeRegistry.java"));
        requireContains(registry, ".trim().toLowerCase(Locale.ROOT)",
                "game-type canonicalization drifted");

        String occupancy = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesMapOccupancyService.java"));
        requireContains(occupancy, ".trim().toLowerCase(Locale.ROOT)",
                "Zombies occupancy map normalization drifted");
    }

    private static void verifyRegistries(JsonObject registries) throws Exception {
        String namespace = registries.get("namespace").getAsString();
        String blockRegister = read(Path.of(
                "src/main/java/com/cdp/codpattern/common/block/CodPatternBlockRegister.java"));
        String itemRegister = read(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/item/FPSMItemRegister.java"));
        String zombiesItemRegister = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesItemRegister.java"));
        requireContains(blockRegister, "DeferredRegister.create(ForgeRegistries.BLOCKS, CodPatternConstants.MOD_ID)",
                "block registry namespace or Forge registry drifted");
        requireContains(blockRegister, "DeferredRegister.create(ForgeRegistries.ITEMS, CodPatternConstants.MOD_ID)",
                "block-item registry namespace or Forge registry drifted");
        requireContains(itemRegister, "DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID)",
                "FPSMatch item registry namespace or Forge registry drifted");
        requireContains(zombiesItemRegister, "DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID)",
                "Zombies deploy item registry namespace or Forge registry drifted");
        requireEquals("codpattern", namespace, "registry namespace drifted");

        Set<String> actualBlocks = captures(blockRegister,
                Pattern.compile("BLOCKS\\.register\\(\\s*\"([^\"]+)\""));
        Set<String> actualItems = new HashSet<>(captures(blockRegister,
                Pattern.compile("ITEMS\\.register\\(\\s*\"([^\"]+)\"")));
        actualItems.addAll(captures(itemRegister,
                Pattern.compile("ITEMS\\.register\\(\\s*\"([^\"]+)\"")));
        actualItems.addAll(captures(zombiesItemRegister,
                Pattern.compile("ITEMS\\.register\\(\\s*\"([^\"]+)\"")));
        requireEquals(new HashSet<>(strings(registries.getAsJsonArray("blocks"))), actualBlocks,
                "registered block IDs drifted");
        requireEquals(new HashSet<>(strings(registries.getAsJsonArray("items"))), actualItems,
                "registered item IDs drifted");
    }

    private static void verifyCommands(JsonObject commands) throws Exception {
        requireEquals("cdp", commands.get("root").getAsString(), "command root drifted");
        require(commands.getAsJsonArray("paths").size() == 23,
                "public command path inventory is incomplete");

        JsonObject sourceLiterals = commands.getAsJsonObject("sourceLiterals");
        for (Map.Entry<String, JsonElement> entry : sourceLiterals.entrySet()) {
            String source = read(Path.of(entry.getKey()));
            Set<String> actual = captures(source, COMMAND_LITERAL);
            Set<String> expected = new HashSet<>(strings(entry.getValue().getAsJsonArray()));
            requireEquals(expected, actual, "command literals drifted in " + entry.getKey());
        }

        String registration = read(Path.of(
                "src/main/java/com/cdp/codpattern/command/CommandRegistration.java"));
        requireContains(registration, ".then(ModeDebugCommand.buildCommand())",
                "mode debug command is no longer attached to /cdp");
        requireContains(registration, ".then(MapManagementCommand.buildCommand())",
                "map command is no longer attached to /cdp");
        requireContains(registration, ".then(MainMenuScreenCommand.buildCommand())",
                "screen command is no longer attached to /cdp");
        requireContains(registration, ".then(UpdateWeaponFilterConfigCommand.buildCommand())",
                "weapon-filter command is no longer attached to /cdp");
        requireContains(registration, ".then(DistributeBackpackItemsCommand.buildCommand())",
                "backpack distribution command is no longer attached to /cdp");
    }

    private static void verifyPersistenceAndConfiguration(
            JsonObject persistence,
            JsonObject configuration
    ) throws Exception {
        String manager = read(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/data/save/FPSMDataManager.java"));
        requireContains(manager, "new File(FMLLoader.getGamePath().toFile(), \""
                        + persistence.get("mapSaveRoot").getAsString() + "\")",
                "map save root drifted");
        requireContains(manager, "new File(root, \"config.json\")",
                "global path configuration file drifted");
        requireContains(manager, quote(persistence.get("globalPathConfigKey").getAsString()),
                "global path configuration key drifted");

        JsonObject folders = persistence.getAsJsonObject("worldScopedFolders");
        requirePersistenceFolder(
                "src/main/java/com/cdp/codpattern/compat/fpsmatch/data/CodTdmMapData.java",
                "BuiltInGameModes.FRONTLINE", folders.get("frontline").getAsString());
        requirePersistenceFolder(
                "src/main/java/com/cdp/codpattern/compat/fpsmatch/data/CodTacticalTdmMapData.java",
                "BuiltInGameModes.TEAM_DEATHMATCH", folders.get("teamdeathmatch").getAsString());
        requirePersistenceFolder(
                "src/main/java/com/cdp/codpattern/compat/fpsmatch/data/ZombiesMapData.java",
                "BuiltInGameModes.ZOMBIES", folders.get("zombies").getAsString());

        String configPath = read(Path.of(
                "src/main/java/com/cdp/codpattern/config/path/ConfigPath.java"));
        String zombiesConfigPaths = read(Path.of(
                "src/main/java/com/cdp/codpattern/config/zombies/ZombiesConfigPaths.java"));
        for (Map.Entry<String, JsonElement> entry : configuration.entrySet()) {
            String source = entry.getKey().startsWith("zombies") ? zombiesConfigPaths : configPath;
            requireContains(source, quote(entry.getValue().getAsString()),
                    "configuration path drifted: " + entry.getKey());
        }
    }

    private static void requirePersistenceFolder(String path, String identityConstant, String folder)
            throws IOException {
        String source = read(Path.of(path));
        requireContains(source, "event.registerData(MapData.class, " + identityConstant,
                "persistence registration drifted in " + path);
        requireContains(source, ".isGlobal(false)", "map persistence became global in " + path);
        requireEquals(folder, folder.trim().toLowerCase(Locale.ROOT),
                "manifest persistence folder is not canonical");
    }

    private static void verifyNbt(JsonObject nbt) throws Exception {
        String marker = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesPlayerRuntimeMarkerService.java"));
        requireContains(marker, "ROOT_TAG = \"" + nbt.get("playerRoot").getAsString() + "\";",
                "Zombies player NBT root drifted");
        for (String field : strings(nbt.getAsJsonArray("playerRootFields"))) {
            requireContains(marker, quote(field), "Zombies player marker field drifted: " + field);
        }
        for (String field : strings(nbt.getAsJsonArray("playerEndTeleportFields"))) {
            requireContains(marker, quote(field), "Zombies end-teleport field drifted: " + field);
        }
        for (String state : strings(nbt.getAsJsonArray("playerStates"))) {
            requireContains(marker, quote(state), "Zombies player marker state drifted: " + state);
        }

        String ownership = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/match/runtime/ModeEntityOwnershipRegistry.java"));
        requireContains(ownership, quote(nbt.get("entityOwnershipTag").getAsString()),
                "entity ownership NBT tag drifted");

        Set<String> expectedEntityTags = new HashSet<>(strings(nbt.getAsJsonArray("entityRuntimeTags")));
        Set<String> actualEntityTags = new HashSet<>();
        actualEntityTags.addAll(captures(ownership,
                Pattern.compile("\"(codpattern(?:_|\\.)[^\"]+)\"")));
        String mobSpawn = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesMobSpawnService.java"));
        actualEntityTags.addAll(captures(mobSpawn,
                Pattern.compile("\"(codpattern(?:_|\\.)[^\"]+)\"")));
        requireEquals(expectedEntityTags, actualEntityTags, "entity runtime NBT tags drifted");

        JsonObject throwable = nbt.getAsJsonObject("throwableCapability");
        String throwableAttach = read(Path.of(
                "src/main/java/com/cdp/codpattern/event/ThrowableCapabilityAttachHandler.java"));
        String[] capabilityId = throwable.get("id").getAsString().split(":", 2);
        requireContains(throwableAttach, "ResourceLocation.fromNamespaceAndPath(CodPatternConstants.MOD_ID, \""
                        + capabilityId[1] + "\")",
                "throwable player capability id drifted");
        String throwableState = read(Path.of(
                "src/main/java/com/cdp/codpattern/core/throwable/ThrowableInventoryState.java"));
        requireContains(throwableState, "SLOT_COUNT = " + throwable.get("slotCount").getAsInt() + ";",
                "throwable player capability slot count drifted");
        requireContains(throwableState, quote(throwable.get("slotKeyPrefix").getAsString()),
                "throwable player capability slot-key prefix drifted");

        Set<String> actualTransientTags = new HashSet<>();
        Set<String> actualToolTags = new HashSet<>();
        collectToolTags(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/item/tool/FPSMToolItem.java",
                actualTransientTags, actualToolTags);
        collectToolTags(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/item/MapCreatorTool.java",
                actualTransientTags, actualToolTags);
        collectToolTags(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/item/SpawnPointTool.java",
                actualTransientTags, actualToolTags);
        collectToolTags(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/item/ZombiesDeployTool.java",
                actualTransientTags, actualToolTags);
        String deployPreview = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployPreviewService.java"));
        actualTransientTags.addAll(captures(deployPreview,
                Pattern.compile("HELD_[A-Z_]*TAG\\s*=\\s*\"([^\"]+)\"")));
        requireEquals(new HashSet<>(strings(nbt.getAsJsonArray("playerTransientTags"))),
                actualTransientTags, "tool preview player tags drifted");
        requireEquals(new HashSet<>(strings(nbt.getAsJsonArray("fpsmToolItemStackTags"))),
                actualToolTags, "FPSMatch tool ItemStack tags drifted");

        String backpackUpdate = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/backpack/service/UpdateWeaponService.java"));
        Set<String> actualBackpackTags = captures(backpackUpdate,
                Pattern.compile("(?:THROWABLE|MELEE_WEAPON)_ID_TAG\\s*=\\s*\"([^\"]+)\""));
        requireEquals(new HashSet<>(strings(nbt.getAsJsonArray("backpackItemStackTags"))),
                actualBackpackTags, "backpack ItemStack tags drifted");

        String weapons = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesWeaponItemStackService.java"));
        Set<String> actualWeaponTags = captures(weapons,
                Pattern.compile("\"(codpattern\\.zombies\\.[^\"]+)\""));
        Set<String> expectedWeaponTags =
                new HashSet<>(strings(nbt.getAsJsonArray("zombiesItemStackTags")));
        requireEquals(expectedWeaponTags, actualWeaponTags, "Zombies ItemStack NBT tags drifted");
    }

    private static void collectToolTags(
            String path,
            Set<String> transientTags,
            Set<String> itemStackTags
    ) throws IOException {
        String source = read(Path.of(path));
        Matcher matcher = Pattern.compile("(?:public|private) static final String ([A-Z0-9_]*TAG[A-Z0-9_]*)\\s*=\\s*\"([^\"]+)\"")
                .matcher(source);
        while (matcher.find()) {
            if (matcher.group(1).startsWith("HELD_")) {
                transientTags.add(matcher.group(2));
            } else {
                itemStackTags.add(matcher.group(2));
            }
        }
    }

    private static void verifyNetwork(JsonObject network) throws Exception {
        String source = read(Path.of(
                "src/main/java/com/cdp/codpattern/adapter/forge/network/ModNetworkChannel.java"));
        requireContains(source, "private static final String PROTOCOL_VERSION = \""
                        + network.get("protocol").getAsString() + "\";",
                "network protocol value drifted");
        String[] channel = network.get("channel").getAsString().split(":", 2);
        requireContains(source, "ResourceLocation.fromNamespaceAndPath(\"" + channel[0]
                        + "\", \"" + channel[1] + "\")",
                "network channel identity drifted");
    }

    private static void verifyTranslations(JsonArray translations) throws Exception {
        for (JsonElement element : translations) {
            JsonObject expected = element.getAsJsonObject();
            JsonObject translationsJson = readJson(Path.of(expected.get("file").getAsString()));
            TreeMap<String, String> values = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : translationsJson.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }

            StringBuilder keys = new StringBuilder();
            StringBuilder signatures = new StringBuilder();
            int formattedKeyCount = 0;
            int argumentTokenCount = 0;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                keys.append(entry.getKey()).append('\n');
                Matcher tokenMatcher = FORMAT_TOKEN.matcher(entry.getValue());
                List<String> tokens = new ArrayList<>();
                while (tokenMatcher.find()) {
                    String token = tokenMatcher.group();
                    if (!"%%".equals(token) && !"%n".equals(token)) {
                        tokens.add(token);
                    }
                }
                if (!tokens.isEmpty()) {
                    formattedKeyCount++;
                    argumentTokenCount += tokens.size();
                }
                signatures.append(entry.getKey()).append('\t')
                        .append(String.join(",", tokens)).append('\n');
            }

            requireEquals(expected.get("keyCount").getAsInt(), values.size(),
                    "translation key count drifted in " + expected.get("file").getAsString());
            requireEquals(expected.get("formattedKeyCount").getAsInt(), formattedKeyCount,
                    "formatted translation key count drifted in " + expected.get("file").getAsString());
            requireEquals(expected.get("argumentTokenCount").getAsInt(), argumentTokenCount,
                    "translation argument count drifted in " + expected.get("file").getAsString());
            requireEquals(expected.get("keysSha256").getAsString(), sha256(keys.toString()),
                    "translation key set drifted in " + expected.get("file").getAsString());
            requireEquals(expected.get("placeholderSignaturesSha256").getAsString(),
                    sha256(signatures.toString()),
                    "translation placeholder order/arity drifted in " + expected.get("file").getAsString());
        }
    }

    private static void verifyEventHandlers(JsonObject eventHandlers) throws Exception {
        List<String> actualRows = extractEventHandlerRows();
        List<String> expectedRows = Files.readAllLines(EVENT_BASELINE_PATH, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        requireEquals(expectedRows, actualRows, "Forge event subscriber metadata drifted");

        Set<String> classes = new HashSet<>();
        actualRows.forEach(row -> classes.add(row.substring(0, row.indexOf('\t'))));
        requireEquals(eventHandlers.get("subscriberClassCount").getAsInt(), classes.size(),
                "Forge event subscriber class count drifted");
        requireEquals(eventHandlers.get("handlerCount").getAsInt(), actualRows.size(),
                "Forge event handler count drifted");
        String canonical = String.join("\n", actualRows) + "\n";
        requireEquals(eventHandlers.get("sha256").getAsString(), sha256(canonical),
                "Forge event handler digest drifted");
    }

    private static List<String> extractEventHandlerRows() throws Exception {
        List<String> rows = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                String source = read(path);
                Matcher subscriber = EVENT_SUBSCRIBER.matcher(source);
                if (!subscriber.find()) {
                    continue;
                }
                String subscriberArgs = normalizeWhitespace(subscriber.group(1));
                String modId = eventSubscriberModId(subscriberArgs);
                String bus = subscriberArgs.contains("Bus.MOD") ? "MOD" : "FORGE";
                String dist = subscriberArgs.contains("Dist.CLIENT") ? "CLIENT" : "BOTH";
                String className = requiredGroup(PACKAGE, source, 1, "package in " + path)
                        + "." + requiredGroup(TYPE, source, 1, "type in " + path);

                Matcher handler = EVENT_HANDLER.matcher(source);
                while (handler.find()) {
                    String eventArgs = normalizeWhitespace(handler.group(1));
                    String eventParameter = normalizeWhitespace(handler.group(3));
                    String eventType = eventParameter.replaceFirst("\\s+\\w+$", "");
                    String priority = captureOrDefault(eventArgs,
                            Pattern.compile("priority\\s*=\\s*EventPriority\\.(\\w+)"), "NORMAL");
                    String receiveCanceled = captureOrDefault(eventArgs,
                            Pattern.compile("receiveCanceled\\s*=\\s*(true|false)"), "false");
                    rows.add(String.join("\t", className, modId, bus, dist, handler.group(2), eventType,
                            priority, receiveCanceled));
                }
            }
        }
        rows.sort(String::compareTo);
        return rows;
    }

    private static String eventSubscriberModId(String subscriberArgs) {
        Matcher matcher = Pattern.compile("modid\\s*=\\s*([^,]+)").matcher(subscriberArgs);
        require(matcher.find(), "event subscriber is missing an explicit modid: " + subscriberArgs);
        String expression = matcher.group(1).trim();
        if ("CodPattern.MODID".equals(expression)
                || "CodPatternConstants.MOD_ID".equals(expression)
                || "FPSMatch.MODID".equals(expression)) {
            return "codpattern";
        }
        if (expression.startsWith("\"") && expression.endsWith("\"") && expression.length() >= 2) {
            return expression.substring(1, expression.length() - 1);
        }
        return expression;
    }

    private static void verifyCombinedBootstrapSequence(JsonArray sequence) throws Exception {
        String source = read(Path.of("src/main/java/com/cdp/codpattern/CodPattern.java"));
        int previous = -1;
        for (String call : strings(sequence)) {
            int current = source.indexOf(call);
            require(current >= 0, "combined bootstrap call is missing: " + call);
            require(current > previous, "combined bootstrap order drifted at: " + call);
            previous = current;
        }
        String core = read(Path.of("src/main/java/com/cdp/codpattern/bootstrap/CoreBootstrap.java"));
        String zombies = read(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesBootstrap.java"));
        requireContains(core, "TdmGameModeDefinitions.registerDefaults();",
                "core mode-definition bootstrap slot drifted");
        requireContains(core, "modEventBus.addListener(CoreBootstrap::onCommonSetup);",
                "common-setup network lifecycle slot drifted");
        requireContains(core, "ModNetworkChannel.register();",
                "network registration is no longer called from common setup");
        requireContains(core, "MinecraftForge.EVENT_BUS.addListener(CoreBootstrap::onServerStarting);",
                "server-starting handler lifecycle slot drifted");
        requireContains(core, "MinecraftForge.EVENT_BUS.addListener(CoreBootstrap::onRegisterCommands);",
                "command-registration handler lifecycle slot drifted");
        requireContains(core, "FPSMItemRegister.ITEMS.register(modEventBus);",
                "generic FPSMatch item lifecycle slot drifted");
        requireContains(zombies, "ZombiesGameModeDefinitions.registerDefaults();",
                "Zombies mode-definition bootstrap slot drifted");
        requireContains(zombies, "CodPatternBlockRegister.BLOCKS.register(modEventBus);",
                "Zombies block registration lifecycle slot drifted");
        requireContains(zombies, "CodPatternBlockRegister.ITEMS.register(modEventBus);",
                "Zombies block-item registration lifecycle slot drifted");
        requireContains(zombies, "ZombiesItemRegister.ITEMS.register(modEventBus);",
                "Zombies deploy-tool item registration lifecycle slot drifted");
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<String> strings(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return List.copyOf(result);
    }

    private static Set<String> captures(String source, Pattern pattern) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static String requiredGroup(Pattern pattern, String source, int group, String description) {
        Matcher matcher = pattern.matcher(source);
        require(matcher.find(), "missing " + description);
        return matcher.group(group);
    }

    private static String captureOrDefault(String source, Pattern pattern, String defaultValue) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", part & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void requireBefore(String source, String first, String second, String message) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        require(firstIndex >= 0 && secondIndex >= 0 && firstIndex < secondIndex, message);
    }

    private static void requireContains(String source, String expected, String message) {
        require(source.contains(expected), message + ": missing `" + expected + "`");
    }

    private static void requireNotContains(String source, String unexpected, String message) {
        require(!source.contains(unexpected), message + ": found `" + unexpected + "`");
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected `" + expected + "`, got `" + actual + "`");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
