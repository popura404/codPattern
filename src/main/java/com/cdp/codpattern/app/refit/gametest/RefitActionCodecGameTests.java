package com.cdp.codpattern.app.refit.gametest;

import com.cdp.codpattern.compat.tacz.TaczRefitActionCodec;
import com.cdp.codpattern.core.refit.RefitAction;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder("codpattern_refit")
@PrefixGameTestTemplate(false)
public final class RefitActionCodecGameTests {
    private RefitActionCodecGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_actions", timeoutTicks = 100)
    public static void refitActionCodecRetainsAllRuntimeAttachmentTypes(GameTestHelper helper) {
        for (AttachmentType type : AttachmentType.values()) {
            var install = new ClientMessageRefitGun(4097, 5, type);
            var unload = new ClientMessageUnloadAttachment(5, type);
            helper.assertTrue(TaczRefitActionCodec.supports(install) && TaczRefitActionCodec.supports(unload),
                    "the bridge recognizes real TaCZ install and unload packets");
            RefitAction installAction = TaczRefitActionCodec.decode(install);
            helper.assertTrue(installAction.equals(RefitAction.install(4097, 5, type)),
                    "install encoding preserves high candidate slot, gun slot, and runtime type " + type.name());
            RefitAction unloadAction = TaczRefitActionCodec.decode(unload);
            helper.assertTrue(unloadAction.equals(RefitAction.unload(5, type)),
                    "unload encoding preserves runtime type " + type.name());
            assertRoundTrip(helper, installAction);
            assertRoundTrip(helper, unloadAction);
        }
        helper.assertTrue(!TaczRefitActionCodec.supports(new Object()),
                "unrelated TaCZ messages are not redirected by this refit codec");
        assertRoundTrip(helper, RefitAction.save());
        assertRoundTrip(helper, RefitAction.cancel());
        LogUtils.getLogger().info("Refit action codec verified all {} runtime attachment types",
                AttachmentType.values().length);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "codpattern_refit", batch = "refit_actions", timeoutTicks = 100)
    public static void refitActionCodecRetainsLaserColorsForDynamicSlots(GameTestHelper helper) {
        Map<AttachmentType, Integer> colors = new LinkedHashMap<>();
        Map<String, Integer> expectedColors = new LinkedHashMap<>();
        for (AttachmentType type : AttachmentType.values()) {
            int color = 0x102030 + type.ordinal();
            colors.put(type, color);
            expectedColors.put(type.name(), color);
        }
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoded.writeMap(colors, FriendlyByteBuf::writeEnum, FriendlyByteBuf::writeInt);
            encoded.writeBoolean(true);
            encoded.writeInt(0xA1B2C3);
            encoded.writeInt(6);
            // Exercise the installed TaCZ decoder AND encoder, including Arcana's enum additions.
            ClientMessageLaserColor packet = ClientMessageLaserColor.decode(encoded);
            helper.assertTrue(!encoded.isReadable() && TaczRefitActionCodec.supports(packet),
                    "installed TaCZ accepts the complete laser packet");
            RefitAction action = TaczRefitActionCodec.decode(packet);
            helper.assertTrue(action.equals(RefitAction.laser(6, expectedColors, true, 0xA1B2C3)),
                    "laser conversion keeps every slot color and the custom gun color");
            assertRoundTrip(helper, action);
        } finally {
            encoded.release();
        }
        helper.succeed();
    }

    private static void assertRoundTrip(GameTestHelper helper, RefitAction action) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            action.encode(buffer);
            helper.assertTrue(action.equals(RefitAction.decode(buffer)) && !buffer.isReadable(),
                    "CDP request round trip preserves " + action.kind());
        } finally {
            buffer.release();
        }
    }
}
