package com.cdp.codpattern.architecture.gametest;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployObjectEditorCompatTest;
import com.cdp.codpattern.network.SyncThrowableInventoryPacket;
import com.cdp.codpattern.network.match.KillFeedPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.Locale;

/** Runtime-only complement to the pure-JVM Phase 0 packet fixture verifier. */
@GameTestHolder(CodPattern.MODID)
@PrefixGameTestTemplate(false)
public final class ModeSplitPacketCodecGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String BATCH = "mode_split_packet_codec";

    private ModeSplitPacketCodecGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 20, required = true)
    public static void itemStackPacketCodecsMatchGoldenBytes(GameTestHelper helper) {
        try {
            SyncThrowableInventoryPacket throwable = new SyncThrowableInventoryPacket(
                    new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY}, 7);
            verifyRoundTrip(
                    "SyncThrowableInventoryPacket",
                    "000000000007",
                    throwable,
                    SyncThrowableInventoryPacket::encode,
                    SyncThrowableInventoryPacket::decode);

            KillFeedPacket killFeed = new KillFeedPacket(
                    "fixture", "fixture", ItemStack.EMPTY, true);
            verifyRoundTrip(
                    "KillFeedPacket",
                    "076669787475726507666978747572650001",
                    killFeed,
                    KillFeedPacket::encode,
                    KillFeedPacket::decode);
            helper.succeed();
        } catch (Throwable error) {
            helper.fail("Phase 0 ItemStack packet codec fixture failed: " + error);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = BATCH, timeoutTicks = 40, required = true)
    public static void deployObjectEditorCompatExecutesInBootstrappedRuntime(GameTestHelper helper) {
        try {
            ZombiesDeployObjectEditorCompatTest.runAll();
            helper.succeed();
        } catch (Throwable error) {
            helper.fail("Zombies deploy object-editor runtime compatibility failed: " + error);
        }
    }

    private static <T> void verifyRoundTrip(
            String name,
            String expectedHex,
            T packet,
            Encoder<T> encoder,
            Decoder<T> decoder
    ) {
        byte[] encoded = encode(packet, encoder);
        require(expectedHex.equals(hex(encoded)), name + " canonical bytes drifted");

        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        T decoded;
        try {
            decoded = decoder.decode(input);
            require(input.readableBytes() == 0, name + " decoder left unread bytes");
        } finally {
            input.release();
        }
        require(Arrays.equals(encoded, encode(decoded, encoder)), name + " round trip drifted");
    }

    private static <T> byte[] encode(T packet, Encoder<T> encoder) {
        FriendlyByteBuf output = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoder.encode(packet, output);
            byte[] bytes = new byte[output.readableBytes()];
            output.getBytes(output.readerIndex(), bytes);
            return bytes;
        } finally {
            output.release();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface Encoder<T> {
        void encode(T packet, FriendlyByteBuf buffer);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(FriendlyByteBuf buffer);
    }
}
