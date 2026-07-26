package com.cdp.codpattern.architecture;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Phase 0 characterization for the one legacy {@code codpattern:main} channel.
 *
 * <p>This test deliberately parses registrar source instead of invoking
 * {@code ModNetworkChannel.register()}: the production registrar owns a static,
 * monotonically increasing discriminator counter and cannot safely be called
 * twice in one JVM.</p>
 */
public final class ModeSplitPacketCodecCompatTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path REGISTRATION_BASELINE =
            Path.of("docs/mode-split/phase0/packet-registration-baseline.tsv");
    private static final Path HELPER_BASELINE =
            Path.of("docs/mode-split/phase0/packet-codec-helper-baseline.tsv");
    private static final Path LEGACY_BASELINE =
            Path.of("docs/mode-split/phase0/packet-legacy-decode-baseline.tsv");

    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+);$", Pattern.MULTILINE);
    private static final Pattern MESSAGE_BUILDER = Pattern.compile(
            "messageBuilder\\(\\s*(\\w+)\\.class\\s*,\\s*ModNetworkChannel\\.nextMessageId\\(\\)\\s*,"
                    + "\\s*NetworkDirection\\.(PLAY_TO_SERVER|PLAY_TO_CLIENT)");
    private static final Pattern MODE_PACKET_SLOT = Pattern.compile(
            "ModeNetworkPacketContributions\\.registerOrReserve\\(\\s*"
                    + "ModeNetworkPacketSlots\\.(FPSM_MODE_TOOL_ACTION|FPSM_MODE_TOOL_SCREEN)");
    private static final Pattern REGISTER_CALL = Pattern.compile(
            "(BackpackPacketRegistrar|ThrowablePacketRegistrar|RefitPacketRegistrar|ModeRoomPacketRegistrar|"
                    + "ModeRuntimePacketRegistrar|FpsmPacketRegistrar)\\.(register(?:InitialRoomPackets|"
                    + "RoomFeedbackPackets|LateRoomPackets)?|register)\\(\\);");

    private static final List<RegistrarCall> EXPECTED_REGISTRAR_CALLS = List.of(
            new RegistrarCall("BackpackPacketRegistrar", "register"),
            new RegistrarCall("ThrowablePacketRegistrar", "register"),
            new RegistrarCall("RefitPacketRegistrar", "register"),
            new RegistrarCall("ModeRoomPacketRegistrar", "registerInitialRoomPackets"),
            new RegistrarCall("ModeRoomPacketRegistrar", "registerRoomFeedbackPackets"),
            new RegistrarCall("ModeRuntimePacketRegistrar", "register"),
            new RegistrarCall("ModeRoomPacketRegistrar", "registerLateRoomPackets"),
            new RegistrarCall("FpsmPacketRegistrar", "register")
    );
    private static final Map<String, String> RUNTIME_ONLY_ITEM_STACK_FIXTURES = Map.of(
            "com.cdp.codpattern.network.SyncThrowableInventoryPacket", "000000000007",
            "com.cdp.codpattern.network.match.KillFeedPacket",
            "076669787475726507666978747572650001"
    );
    private static final Map<String, SlotPacket> MODE_PACKET_SLOTS = Map.of(
            "FPSM_MODE_TOOL_ACTION",
            new SlotPacket(
                    "com.phasetranscrystal.fpsmatch.common.packet.ZombiesDeployToolActionC2SPacket",
                    "C2S",
                    "PLAY_TO_SERVER"),
            "FPSM_MODE_TOOL_SCREEN",
            new SlotPacket(
                    "com.phasetranscrystal.fpsmatch.common.packet.OpenZombiesDeployToolScreenS2CPacket",
                    "S2C",
                    "PLAY_TO_CLIENT")
    );

    private ModeSplitPacketCodecCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        List<Registration> registrations = scanRegistrationOrder();
        if (Arrays.asList(args).contains("--emit-fixtures")) {
            emitFixtures(registrations);
            return;
        }

        verifyRegistrationBaseline(registrations);
        verifyPacketInventory(registrations);
        verifyCodecHelpers(registrations);
        verifyCanonicalFixtures(registrations);
        verifyLegacyDecodeOnlyFixtures();

        long c2s = registrations.stream().filter(row -> row.direction().equals("C2S")).count();
        long s2c = registrations.stream().filter(row -> row.direction().equals("S2C")).count();
        System.out.println("PASS mode split packet codec baseline: " + registrations.size()
                + " packets (" + c2s + " C2S, " + s2c + " S2C), 2 legacy decode-only fixtures");
    }

    private static List<Registration> scanRegistrationOrder() throws IOException {
        Path channelPath = MAIN_JAVA.resolve(
                "com/cdp/codpattern/adapter/forge/network/ModNetworkChannel.java");
        String channelSource = Files.readString(channelPath, StandardCharsets.UTF_8);
        String registerBody = methodBody(channelSource, "public static void register()");
        List<RegistrarCall> actualCalls = new ArrayList<>();
        Matcher callMatcher = REGISTER_CALL.matcher(registerBody);
        while (callMatcher.find()) {
            actualCalls.add(new RegistrarCall(callMatcher.group(1), callMatcher.group(2)));
        }
        requireEquals(EXPECTED_REGISTRAR_CALLS, actualCalls,
                "ModNetworkChannel registrar call sequence drifted");

        List<Registration> registrations = new ArrayList<>();
        int discriminator = 0;
        String zombiesPacketContributor = Files.readString(MAIN_JAVA.resolve(
                "com/cdp/codpattern/app/zombies/bootstrap/ZombiesNetworkPacketContributor.java"));
        for (RegistrarCall call : actualCalls) {
            Path registrarPath = MAIN_JAVA.resolve(
                    "com/cdp/codpattern/adapter/forge/network/" + call.className() + ".java");
            String source = Files.readString(registrarPath, StandardCharsets.UTF_8);
            Map<String, String> imports = imports(source);
            String body = methodBody(source, "static void " + call.methodName() + "()");
            List<LocatedRegistration> locatedRegistrations = new ArrayList<>();
            Matcher packetMatcher = MESSAGE_BUILDER.matcher(body);
            while (packetMatcher.find()) {
                String simpleName = packetMatcher.group(1);
                String className = imports.get(simpleName);
                require(className != null, "missing explicit packet import for " + simpleName
                        + " in " + registrarPath);
                String direction = switch (packetMatcher.group(2)) {
                    case "PLAY_TO_SERVER" -> "C2S";
                    case "PLAY_TO_CLIENT" -> "S2C";
                    default -> throw new IllegalStateException("unexpected direction");
                };
                locatedRegistrations.add(new LocatedRegistration(
                        packetMatcher.start(), direction, className, "future-main"));
            }
            Matcher slotMatcher = MODE_PACKET_SLOT.matcher(body);
            while (slotMatcher.find()) {
                String slotId = slotMatcher.group(1);
                SlotPacket slotPacket = MODE_PACKET_SLOTS.get(slotId);
                require(slotPacket != null, "unknown deterministic mode packet slot: " + slotId);
                String simpleName = slotPacket.className().substring(slotPacket.className().lastIndexOf('.') + 1);
                requireContains(zombiesPacketContributor,
                        "ModeNetworkPacketContributions.install(ModeNetworkPacketSlots." + slotId,
                        "Zombies packet contribution is not installed in legacy slot " + slotId);
                requireContains(zombiesPacketContributor, simpleName + ".class",
                        "Zombies packet contribution class drifted for slot " + slotId);
                requireContains(zombiesPacketContributor, "NetworkDirection." + slotPacket.networkDirection(),
                        "Zombies packet contribution direction drifted for slot " + slotId);
                locatedRegistrations.add(new LocatedRegistration(
                        slotMatcher.start(), slotPacket.direction(), slotPacket.className(), "zombies-addon"));
            }
            locatedRegistrations.sort(Comparator.comparingInt(LocatedRegistration::position));
            for (LocatedRegistration located : locatedRegistrations) {
                registrations.add(new Registration(
                        discriminator++,
                        located.direction(),
                        located.className(),
                        located.owner()));
            }
        }

        requireEquals(58, registrations.size(), "registered packet count drifted");
        requireEquals(28L,
                registrations.stream().filter(row -> row.direction().equals("C2S")).count(),
                "C2S packet count drifted");
        requireEquals(30L,
                registrations.stream().filter(row -> row.direction().equals("S2C")).count(),
                "S2C packet count drifted");
        return registrations;
    }

    private static void verifyRegistrationBaseline(List<Registration> actual) throws IOException {
        List<FixtureRow> expected = readFixtureRows();
        requireEquals(58, expected.size(), "packet registration baseline must contain 58 rows");
        for (int i = 0; i < expected.size(); i++) {
            FixtureRow fixture = expected.get(i);
            Registration registration = actual.get(i);
            requireEquals(i, fixture.discriminator(), "packet discriminator sequence is not contiguous");
            requireEquals(registration.discriminator(), fixture.discriminator(),
                    "packet discriminator drifted at row " + i);
            requireEquals(registration.direction(), fixture.direction(),
                    "packet direction drifted at discriminator " + i);
            requireEquals(registration.className(), fixture.className(),
                    "packet class drifted at discriminator " + i);
            requireEquals(registration.owner(), fixture.owner(),
                    "packet provisional owner drifted at discriminator " + i);
        }
        Set<Integer> addonSlots = new TreeSet<>();
        expected.stream()
                .filter(row -> row.owner().equals("zombies-addon"))
                .forEach(row -> addonSlots.add(row.discriminator()));
        requireEquals(Set.of(51, 57), addonSlots,
                "Zombies packet slots are interleaved and must remain exactly 51 and 57");
    }

    private static void verifyPacketInventory(List<Registration> registrations) throws IOException {
        Set<String> registered = new TreeSet<>();
        registrations.forEach(row -> registered.add(row.className()));
        Set<String> packetSources = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            paths.filter(path -> path.getFileName().toString().endsWith("Packet.java"))
                    .forEach(path -> packetSources.add(className(path)));
        }
        requireEquals(registered, packetSources,
                "production *Packet.java inventory and channel registration differ");
    }

    private static void verifyCodecHelpers(List<Registration> registrations) throws IOException {
        Set<String> registered = new HashSet<>();
        registrations.forEach(row -> registered.add(row.className()));
        Set<String> actualHelpers = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8).contains("FriendlyByteBuf");
                        } catch (IOException error) {
                            throw new RuntimeException(error);
                        }
                    })
                    .map(ModeSplitPacketCodecCompatTest::className)
                    .filter(name -> !registered.contains(name))
                    .filter(name -> !name.contains(".architecture.gametest."))
                    .forEach(actualHelpers::add);
        }

        Set<String> expectedHelpers = new TreeSet<>();
        for (String line : dataLines(HELPER_BASELINE)) {
            String[] fields = line.split("\\t", -1);
            requireEquals(4, fields.length, "invalid packet codec helper baseline row: " + line);
            requireEquals("codec-helper", fields[0], "unknown packet helper classification");
            expectedHelpers.add(fields[1]);
        }
        requireEquals(expectedHelpers, actualHelpers,
                "unregistered FriendlyByteBuf helper inventory drifted");
    }

    private static void verifyCanonicalFixtures(List<Registration> registrations) throws Exception {
        List<FixtureRow> fixtureRows = readFixtureRows();
        Map<String, FixtureRow> fixtures = new LinkedHashMap<>();
        fixtureRows.forEach(row -> fixtures.put(row.className(), row));

        for (Registration registration : registrations) {
            FixtureRow fixture = fixtures.get(registration.className());
            require(fixture != null, "missing codec fixture for " + registration.className());
            String runtimeOnlyHex = RUNTIME_ONLY_ITEM_STACK_FIXTURES.get(registration.className());
            if (runtimeOnlyHex != null) {
                requireEquals(runtimeOnlyHex, fixture.canonicalHex(),
                        "runtime ItemStack fixture bytes drifted in the approved manifest");
                continue;
            }
            Class<?> packetClass = Class.forName(registration.className());
            Object representative = representativePacket(packetClass);
            byte[] encoded = encode(packetClass, representative);
            requireEquals(fixture.canonicalHex(), hex(encoded),
                    "canonical packet bytes drifted for discriminator " + registration.discriminator()
                            + " " + registration.className());

            DecodeResult decoded = decode(packetClass, encoded);
            requireEquals(0, decoded.remainingBytes(),
                    "canonical decoder left unread bytes for " + registration.className());
            require(Arrays.equals(encoded, encode(packetClass, decoded.packet())),
                    "canonical decode/encode round trip drifted for " + registration.className());
        }
    }

    private static void verifyLegacyDecodeOnlyFixtures() throws Exception {
        List<String> rows = dataLines(LEGACY_BASELINE);
        requireEquals(2, rows.size(), "legacy packet baseline must contain exactly two fixtures");
        Set<String> seen = new HashSet<>();
        for (String row : rows) {
            String[] fields = row.split("\\t", -1);
            requireEquals(5, fields.length, "invalid legacy packet baseline row: " + row);
            String className = fields[0];
            String fixtureName = fields[1];
            byte[] legacyBytes = unhex(fields[2]);
            Class<?> packetClass = Class.forName(className);
            DecodeResult decoded = decode(packetClass, legacyBytes);
            requireEquals(0, decoded.remainingBytes(),
                    "legacy decoder left unread bytes for " + fixtureName);
            byte[] currentBytes = encode(packetClass, decoded.packet());
            require(!Arrays.equals(legacyBytes, currentBytes),
                    "legacy decode-only fixture unexpectedly re-encoded to its old wire format: " + fixtureName);
            DecodeResult currentDecoded = decode(packetClass, currentBytes);
            requireEquals(0, currentDecoded.remainingBytes(),
                    "current re-encoding of legacy fixture did not decode cleanly: " + fixtureName);
            verifyLegacySemantics(className, decoded.packet(), fields[3], fixtureName);
            requireEquals("decode-only", fields[4], "legacy fixture policy drifted");
            seen.add(className);
        }
        requireEquals(Set.of(
                "com.cdp.codpattern.network.match.DeathCamPacket",
                "com.cdp.codpattern.network.match.ScoreUpdatePacket"), seen,
                "legacy decode-only fixture classes drifted");
    }

    private static void verifyLegacySemantics(
            String className,
            Object packet,
            String expectation,
            String fixtureName
    ) throws Exception {
        if (className.endsWith("DeathCamPacket")) {
            requireEquals("deathTicksCopiedToRespawnDelay;rotationAbsent", expectation,
                    "DeathCam legacy expectation drifted");
            int deathTicks = (int) field(packet, "deathCamTicks");
            int respawnDelay = (int) field(packet, "respawnDelayTicks");
            float yaw = (float) field(packet, "lockedYaw");
            float pitch = (float) field(packet, "lockedPitch");
            requireEquals(deathTicks, respawnDelay,
                    "legacy DeathCam duration no longer supplies respawn delay");
            require(Float.isNaN(yaw) && Float.isNaN(pitch),
                    "legacy DeathCam payload unexpectedly supplies locked rotation");
            return;
        }
        if (className.endsWith("ScoreUpdatePacket")) {
            requireEquals("legacyTeamScoresSynthesized", expectation,
                    "ScoreUpdate legacy expectation drifted");
            int team1 = (int) field(packet, "team1Score");
            int team2 = (int) field(packet, "team2Score");
            @SuppressWarnings("unchecked")
            Map<String, Integer> scores = (Map<String, Integer>) field(packet, "teamScores");
            requireEquals(team1, scores.get("kortac"),
                    "legacy ScoreUpdate no longer synthesizes kortac score");
            requireEquals(team2, scores.get("specgru"),
                    "legacy ScoreUpdate no longer synthesizes specgru score");
            return;
        }
        throw new AssertionError("unknown legacy fixture " + fixtureName);
    }

    private static Object representativePacket(Class<?> packetClass) throws Exception {
        if (packetClass.getName().equals("com.cdp.codpattern.network.match.ScoreUpdatePacket")) {
            Constructor<?> constructor = packetClass.getConstructor(Map.class, int.class);
            return constructor.newInstance(Map.of("fixture", 7), 7);
        }
        return instantiate(packetClass, 0);
    }

    private static Object instantiate(Class<?> type, int depth) throws Exception {
        require(depth < 12, "fixture constructor recursion exceeded for " + type.getName());
        Constructor<?> constructor = Arrays.stream(type.getConstructors())
                .filter(candidate -> Arrays.stream(candidate.getParameterTypes())
                        .noneMatch(FriendlyByteBuf.class::equals))
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow(() -> new AssertionError("no representative constructor for " + type.getName()));
        Type[] genericTypes = constructor.getGenericParameterTypes();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            arguments[i] = defaultValue(parameterTypes[i], genericTypes[i], depth + 1);
        }
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw new AssertionError("representative construction failed for " + type.getName()
                    + ": " + cause, cause);
        }
    }

    private static Object defaultValue(Class<?> rawType, Type genericType, int depth) throws Exception {
        if (rawType == String.class) {
            return "fixture";
        }
        if (rawType == int.class || rawType == Integer.class) {
            return 7;
        }
        if (rawType == long.class || rawType == Long.class) {
            return 11L;
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return true;
        }
        if (rawType == float.class || rawType == Float.class) {
            return 1.25F;
        }
        if (rawType == double.class || rawType == Double.class) {
            return 2.5D;
        }
        if (rawType == byte.class || rawType == Byte.class) {
            return (byte) 3;
        }
        if (rawType == short.class || rawType == Short.class) {
            return (short) 4;
        }
        if (rawType == char.class || rawType == Character.class) {
            return 'x';
        }
        if (rawType == UUID.class) {
            return UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        }
        if (rawType == Component.class) {
            return Component.literal("fixture");
        }
        if (rawType == ItemStack.class) {
            return ItemStack.EMPTY;
        }
        if (rawType == BlockPos.class) {
            return new BlockPos(1, 2, 3);
        }
        if (rawType == Vec3.class) {
            return new Vec3(1.25D, 2.5D, 3.75D);
        }
        if (rawType == ResourceLocation.class) {
            return ResourceLocation.fromNamespaceAndPath("codpattern", "fixture");
        }
        if (rawType == Optional.class) {
            return Optional.empty();
        }
        if (rawType == List.class) {
            return List.of();
        }
        if (rawType == Set.class) {
            return Set.of();
        }
        if (rawType == Map.class) {
            return Map.of();
        }
        if (rawType.isArray()) {
            return java.lang.reflect.Array.newInstance(rawType.getComponentType(), 0);
        }
        if (rawType.isEnum()) {
            Object[] constants = rawType.getEnumConstants();
            require(constants.length > 0, "empty enum cannot supply fixture value: " + rawType.getName());
            return constants[0];
        }
        if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) {
            throw new AssertionError("no fixture default for abstract type " + rawType.getName()
                    + " from " + genericType.getTypeName());
        }
        return instantiate(rawType, depth);
    }

    private static byte[] encode(Class<?> packetClass, Object packet) throws Exception {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Method instanceEncoder = null;
            try {
                instanceEncoder = packetClass.getMethod("encode", FriendlyByteBuf.class);
            } catch (NoSuchMethodException ignored) {
                // Static encoder form is handled below.
            }
            if (instanceEncoder != null && !Modifier.isStatic(instanceEncoder.getModifiers())) {
                instanceEncoder.invoke(packet, buffer);
            } else {
                Method staticEncoder = packetClass.getMethod("encode", packetClass, FriendlyByteBuf.class);
                staticEncoder.invoke(null, packet, buffer);
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw new AssertionError("packet encode failed for " + packetClass.getName() + ": " + cause, cause);
        } finally {
            buffer.release();
        }
    }

    private static DecodeResult decode(Class<?> packetClass, byte[] bytes) throws Exception {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            Method decoder = packetClass.getMethod("decode", FriendlyByteBuf.class);
            Object packet = decoder.invoke(null, buffer);
            return new DecodeResult(packet, buffer.readableBytes());
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw new AssertionError("packet decode failed for " + packetClass.getName() + ": " + cause, cause);
        } finally {
            buffer.release();
        }
    }

    private static void emitFixtures(List<Registration> registrations) throws Exception {
        System.out.println("# MODE_SPLIT_PACKET_REGISTRATION_BASELINE_V1");
        System.out.println("# Columns: discriminator, direction, packet_class, provisional_owner, canonical_hex, fixture_profile");
        for (Registration registration : registrations) {
            String bytes = RUNTIME_ONLY_ITEM_STACK_FIXTURES.get(registration.className());
            if (bytes == null) {
                Class<?> packetClass = Class.forName(registration.className());
                bytes = hex(encode(packetClass, representativePacket(packetClass)));
            }
            System.out.println(registration.discriminator() + "\t" + registration.direction() + "\t"
                    + registration.className() + "\t" + registration.owner() + "\t" + bytes
                    + "\tauto-representative-v1");
        }
    }

    private static List<FixtureRow> readFixtureRows() throws IOException {
        List<FixtureRow> rows = new ArrayList<>();
        for (String line : dataLines(REGISTRATION_BASELINE)) {
            String[] fields = line.split("\\t", -1);
            requireEquals(6, fields.length, "invalid packet registration baseline row: " + line);
            requireEquals("auto-representative-v1", fields[5], "unknown packet fixture profile");
            rows.add(new FixtureRow(
                    Integer.parseInt(fields[0]),
                    fields[1],
                    fields[2],
                    fields[3],
                    fields[4],
                    fields[5]));
        }
        return rows;
    }

    private static List<String> dataLines(Path path) throws IOException {
        require(Files.isRegularFile(path), "missing Phase 0 baseline: " + path);
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        require(signatureStart >= 0, "missing method signature: " + signature);
        int openBrace = source.indexOf('{', signatureStart + signature.length());
        require(openBrace >= 0, "missing method body: " + signature);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char character = source.charAt(i);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }
        throw new AssertionError("unterminated method body: " + signature);
    }

    private static Map<String, String> imports(String source) {
        Map<String, String> imports = new HashMap<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            String className = matcher.group(1);
            imports.put(className.substring(className.lastIndexOf('.') + 1), className);
        }
        return imports;
    }

    private static String className(Path source) {
        String relative = MAIN_JAVA.relativize(source).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static byte[] unhex(String value) {
        require(value.length() % 2 == 0, "hex fixture has odd length");
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            require(high >= 0 && low >= 0, "invalid hex fixture: " + value);
            bytes[i / 2] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private record RegistrarCall(String className, String methodName) {
    }

    private record Registration(int discriminator, String direction, String className, String owner) {
    }

    private record LocatedRegistration(int position, String direction, String className, String owner) {
    }

    private record SlotPacket(String className, String direction, String networkDirection) {
    }

    private record FixtureRow(
            int discriminator,
            String direction,
            String className,
            String owner,
            String canonicalHex,
            String profile
    ) {
    }

    private record DecodeResult(Object packet, int remainingBytes) {
    }
}
