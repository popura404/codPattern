package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.sync.ZombiesObjectStateKeys;
import com.cdp.codpattern.client.ClientMatchState;
import com.cdp.codpattern.client.ClientModeObjectState;
import com.cdp.codpattern.client.zombies.ZombiesRarityDisplay;
import com.cdp.codpattern.client.zombies.ClientZombiesState;
import com.cdp.codpattern.common.block.CodPatternBlockRegister;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesObjectLabelWorldRenderer {
    private static final double MAX_RENDER_DISTANCE = 24.0D;
    private static final double MAX_RENDER_DISTANCE_SQR = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    private static final double MIN_RENDER_DEPTH = 0.05D;
    private static final int MAX_RENDERED_LABELS = 32;
    private static final float LABEL_SCALE = 1.35F;
    private static final int TITLE_COLOR = 0xFFFFF1C2;
    private static final int ACTIVE_COLOR = 0xFFE8F4FF;
    private static final int DISABLED_COLOR = 0xFFFF7777;
    private static final int READY_COLOR = 0xFF86EFAC;
    private static final int TEXT_BACKGROUND_COLOR = 0x8D000000;
    private static final String PAYLOAD_TYPE_BARRIER = "barrier";
    private static final String PAYLOAD_TYPE_WEAPON_WALL = "weapon_wall";
    private static final String PAYLOAD_TYPE_AMMO_BOX = "ammo_box";
    private static final String PAYLOAD_TYPE_ARMOR_STATION = "armor_station";
    private static final String PAYLOAD_TYPE_POWER_SWITCH = "power_switch";
    private static final String PAYLOAD_TYPE_SODA_MACHINE = "soda_machine";
    private static final String PAYLOAD_TYPE_ULTIMATE_MACHINE = "ultimate_machine";
    private static final String PAYLOAD_CLEARED = "cleared";
    private static final String PAYLOAD_GROUP = "group";
    private static final String PAYLOAD_RARITY_ID = "rarityId";
    private static final String PAYLOAD_GUN_ID = "gunId";
    private static final String PAYLOAD_PRICES_BY_WEAPON_LEVEL = "pricesByWeaponLevel";
    private static final String PAYLOAD_ARMOR_LEVEL = "armorLevel";
    private static final String PAYLOAD_REQUIRES_POWER = "requiresPower";
    private static final String PAYLOAD_POWER_ON = "powerOn";
    private static final String PAYLOAD_BUFF_ID = "buffId";
    private static final String PAYLOAD_MAX_UPGRADE_LEVEL = "maxUpgradeLevel";
    private static final String PAYLOAD_AREA_FROM_X = "areaFromX";
    private static final String PAYLOAD_AREA_FROM_Y = "areaFromY";
    private static final String PAYLOAD_AREA_FROM_Z = "areaFromZ";
    private static final String PAYLOAD_AREA_TO_X = "areaToX";
    private static final String PAYLOAD_AREA_TO_Y = "areaToY";
    private static final String PAYLOAD_AREA_TO_Z = "areaToZ";

    private ZombiesObjectLabelWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !ClientZombiesState.shouldRenderHud()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        String roomKey = ClientMatchState.roomContextName();
        if (localPlayer == null
                || level == null
                || camera == null
                || !camera.isInitialized()
                || roomKey == null
                || roomKey.isBlank()) {
            return;
        }

        List<RenderCandidate> candidates = collectCandidates(event, level, localPlayer, camera, roomKey);
        if (candidates.isEmpty()) {
            return;
        }

        int screenHeight = Math.max(1, minecraft.getWindow().getHeight());
        double tanHalfFov = event.getProjectionMatrix().m11() == 0.0F
                ? 0.0D
                : Math.abs(1.0D / event.getProjectionMatrix().m11());
        if (tanHalfFov <= 0.0D) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        Vec3 cameraForward = new Vec3(camera.getLookVector().x(), camera.getLookVector().y(), camera.getLookVector().z())
                .normalize();
        PoseStack poseStack = event.getPoseStack();
        Font font = minecraft.font;
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        try {
            int rendered = 0;
            for (RenderCandidate candidate : candidates) {
                Vec3 relative = candidate.anchor().subtract(cameraPos);
                double depth = relative.dot(cameraForward);
                if (depth <= MIN_RENDER_DEPTH) {
                    continue;
                }

                float pixelScale = (float) ((2.0D * depth * tanHalfFov) / screenHeight);
                if (!Float.isFinite(pixelScale) || pixelScale <= 0.0F) {
                    continue;
                }

                renderLabel(poseStack, bufferSource, font, minecraft, relative, pixelScale, candidate.label());
                rendered++;
                if (rendered >= MAX_RENDERED_LABELS) {
                    break;
                }
            }
            bufferSource.endBatch();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static List<RenderCandidate> collectCandidates(
            RenderLevelStageEvent event,
            ClientLevel level,
            LocalPlayer localPlayer,
            Camera camera,
            String roomKey
    ) {
        Vec3 cameraPos = camera.getPosition();
        List<RenderCandidate> candidates = new ArrayList<>();
        for (ModeObjectState state : ClientModeObjectState.roomStates(roomKey).values()) {
            if (state == null || state.position() == null) {
                continue;
            }
            CompoundTag payload = state.payload();
            String type = payload.getString(ZombiesObjectStateKeys.PAYLOAD_TYPE);
            if (!isLabelObjectType(type) || !hasExpectedRuntimeBlock(level, state.position(), payload, type)) {
                continue;
            }
            ObjectLabel label = labelFor(type, payload);
            if (label == null || label.title().isBlank()) {
                continue;
            }
            Vec3 anchor = labelAnchor(state.position(), type);
            double distanceSqr = cameraPos.distanceToSqr(anchor);
            if (distanceSqr > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            if (!event.getFrustum().isVisible(new AABB(anchor, anchor).inflate(0.45D))
                    || !hasClearView(level, localPlayer, cameraPos, anchor, state.position(), payload, type)) {
                continue;
            }
            candidates.add(new RenderCandidate(anchor, distanceSqr, label));
        }
        candidates.sort(Comparator.comparingDouble(RenderCandidate::distanceSqr));
        return candidates;
    }

    private static void renderLabel(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            Minecraft minecraft,
            Vec3 relative,
            float pixelScale,
            ObjectLabel label
    ) {
        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-pixelScale * LABEL_SCALE, -pixelScale * LABEL_SCALE, pixelScale * LABEL_SCALE);

        drawCenteredLine(poseStack, bufferSource, font, label.title(), -font.lineHeight - 2, label.titleColor());
        if (!label.detail().isBlank()) {
            drawCenteredLine(poseStack, bufferSource, font, label.detail(), 1, label.detailColor());
        }

        poseStack.popPose();
    }

    private static void drawCenteredLine(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            String text,
            int y,
            int color
    ) {
        String safeText = trimToWidth(font, text == null ? "" : text, 160);
        if (safeText.isBlank()) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(
                safeText,
                -font.width(safeText) / 2.0F,
                y,
                color,
                false,
                matrix,
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                TEXT_BACKGROUND_COLOR,
                LightTexture.FULL_BRIGHT);
    }

    private static ObjectLabel labelFor(String type, CompoundTag payload) {
        return switch (type) {
            case PAYLOAD_TYPE_BARRIER -> barrierLabel(payload);
            case PAYLOAD_TYPE_WEAPON_WALL -> weaponWallLabel(payload);
            case PAYLOAD_TYPE_AMMO_BOX -> ammoBoxLabel(payload);
            case PAYLOAD_TYPE_ARMOR_STATION -> armorStationLabel(payload);
            case PAYLOAD_TYPE_POWER_SWITCH -> powerSwitchLabel(payload);
            case PAYLOAD_TYPE_SODA_MACHINE -> sodaMachineLabel(payload);
            case PAYLOAD_TYPE_ULTIMATE_MACHINE -> ultimateMachineLabel(payload);
            default -> null;
        };
    }

    private static ObjectLabel barrierLabel(CompoundTag payload) {
        int group = Math.max(0, payload.getInt(PAYLOAD_GROUP));
        String title = group > 0 ? "屏障组 " + group : "屏障";
        if (payload.getBoolean(PAYLOAD_CLEARED)) {
            return new ObjectLabel(title, "已开启", READY_COLOR);
        }
        return pricedLabel(title, "开启", payload, true);
    }

    private static ObjectLabel weaponWallLabel(CompoundTag payload) {
        String gunId = payload.getString(PAYLOAD_GUN_ID).trim();
        String rarityId = payload.getString(PAYLOAD_RARITY_ID).trim();
        if (gunId.isBlank()) {
            return new ObjectLabel("墙枪", "暂无可购买武器", DISABLED_COLOR);
        }
        ZombiesRarityDisplay.Entry rarity = ZombiesRarityDisplay.fromRarityId(rarityId).orElse(null);
        String title = rarity == null ? gunId : rarity.label() + " " + gunId;
        int titleColor = rarity == null ? TITLE_COLOR : rarity.color();
        return pricedLabel(title, "购买", payload, payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED), titleColor);
    }

    private static ObjectLabel ammoBoxLabel(CompoundTag payload) {
        boolean enabled = payload.contains(PAYLOAD_PRICES_BY_WEAPON_LEVEL, Tag.TAG_COMPOUND)
                && !payload.getCompound(PAYLOAD_PRICES_BY_WEAPON_LEVEL).isEmpty();
        return pricedLabel("弹药箱", enabled ? "补满弹药" : "无补弹价格", payload, enabled);
    }

    private static ObjectLabel armorStationLabel(CompoundTag payload) {
        int armorLevel = Math.max(0, payload.getInt(PAYLOAD_ARMOR_LEVEL));
        String title = armorLevel > 0 ? armorLevel + "级护甲站" : "护甲站";
        boolean enabled = payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED)
                && (armorLevel <= 0 || ClientZombiesState.armorLevel() < armorLevel);
        if (!enabled && armorLevel > 0 && ClientZombiesState.armorLevel() >= armorLevel) {
            return new ObjectLabel(title, "已拥有", READY_COLOR);
        }
        return pricedLabel(title, "购买", payload, enabled);
    }

    private static ObjectLabel powerSwitchLabel(CompoundTag payload) {
        if (payload.getBoolean(PAYLOAD_POWER_ON)) {
            return new ObjectLabel("电源开关", "已开启", READY_COLOR);
        }
        return pricedLabel("电源开关", "开启电源", payload, payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED));
    }

    private static ObjectLabel sodaMachineLabel(CompoundTag payload) {
        String buffId = payload.getString(PAYLOAD_BUFF_ID).trim();
        String title = buffId.isBlank() ? "汽水机" : "汽水机 " + buffId;
        if (!buffId.isBlank() && ClientZombiesState.buffEnabled(buffId)) {
            return new ObjectLabel(title, "已拥有", READY_COLOR);
        }
        boolean powerRequired = payload.getBoolean(PAYLOAD_REQUIRES_POWER) && !payload.getBoolean(PAYLOAD_POWER_ON);
        if (powerRequired) {
            return pricedLabel(title, "需要电源", payload, false);
        }
        return pricedLabel(title, "购买", payload, payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED));
    }

    private static ObjectLabel ultimateMachineLabel(CompoundTag payload) {
        boolean powerRequired = payload.getBoolean(PAYLOAD_REQUIRES_POWER) && !payload.getBoolean(PAYLOAD_POWER_ON);
        if (powerRequired) {
            return pricedLabel("强化机", "需要电源", payload, false);
        }
        boolean enabled = payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED)
                && Math.max(0, payload.getInt(PAYLOAD_MAX_UPGRADE_LEVEL)) > 0;
        return pricedLabel("强化机", enabled ? "强化武器" : "不可用", payload, enabled);
    }

    private static ObjectLabel pricedLabel(String title, String action, CompoundTag payload, boolean enabled) {
        return pricedLabel(title, action, payload, enabled, TITLE_COLOR);
    }

    private static ObjectLabel pricedLabel(
            String title,
            String action,
            CompoundTag payload,
            boolean enabled,
            int titleColor
    ) {
        String detail = action + " - " + Math.max(0, payload.getInt(ZombiesObjectStateKeys.PAYLOAD_COST)) + "点";
        return new ObjectLabel(title, detail, titleColor, enabled ? ACTIVE_COLOR : DISABLED_COLOR);
    }

    private static Vec3 labelAnchor(BlockPos pos, String type) {
        double yOffset = PAYLOAD_TYPE_BARRIER.equals(type) ? 1.15D : 1.35D;
        return Vec3.atCenterOf(pos).add(0.0D, yOffset, 0.0D);
    }

    private static boolean isLabelObjectType(String type) {
        return PAYLOAD_TYPE_BARRIER.equals(type)
                || PAYLOAD_TYPE_WEAPON_WALL.equals(type)
                || PAYLOAD_TYPE_AMMO_BOX.equals(type)
                || PAYLOAD_TYPE_ARMOR_STATION.equals(type)
                || PAYLOAD_TYPE_POWER_SWITCH.equals(type)
                || PAYLOAD_TYPE_SODA_MACHINE.equals(type)
                || PAYLOAD_TYPE_ULTIMATE_MACHINE.equals(type);
    }

    private static boolean hasExpectedRuntimeBlock(ClientLevel level, BlockPos pos, CompoundTag payload, String type) {
        if (PAYLOAD_TYPE_BARRIER.equals(type)) {
            return !payload.getBoolean(PAYLOAD_CLEARED);
        }
        Block expected = expectedBlock(type);
        return expected != null && level.getBlockState(pos).is(expected);
    }

    private static Block expectedBlock(String type) {
        return switch (type) {
            case PAYLOAD_TYPE_WEAPON_WALL -> CodPatternBlockRegister.ZOMBIES_WEAPON_WALL_BOX.get();
            case PAYLOAD_TYPE_AMMO_BOX -> CodPatternBlockRegister.ZOMBIES_AMMO_BOX.get();
            case PAYLOAD_TYPE_ARMOR_STATION -> CodPatternBlockRegister.ZOMBIES_ARMOR_STATION_BOX.get();
            case PAYLOAD_TYPE_POWER_SWITCH -> CodPatternBlockRegister.ZOMBIES_POWER_SWITCH.get();
            case PAYLOAD_TYPE_SODA_MACHINE -> CodPatternBlockRegister.ZOMBIES_SODA_MACHINE_BOX.get();
            case PAYLOAD_TYPE_ULTIMATE_MACHINE -> CodPatternBlockRegister.ZOMBIES_ULTIMATE_MACHINE_BOX.get();
            default -> null;
        };
    }

    private static boolean hasClearView(
            ClientLevel level,
            LocalPlayer localPlayer,
            Vec3 cameraPos,
            Vec3 anchor,
            BlockPos objectPos,
            CompoundTag payload,
            String type
    ) {
        HitResult result = level.clip(new ClipContext(
                cameraPos,
                anchor,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                localPlayer));
        if (result == null || result.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (!(result instanceof BlockHitResult blockHit)) {
            return false;
        }
        if (blockHit.getLocation().distanceToSqr(anchor) <= 0.35D) {
            return true;
        }
        BlockPos hitPos = blockHit.getBlockPos();
        return hitPos.equals(objectPos) || (PAYLOAD_TYPE_BARRIER.equals(type) && barrierContains(payload, hitPos));
    }

    private static boolean barrierContains(CompoundTag payload, BlockPos pos) {
        if (payload == null || pos == null
                || !payload.contains(PAYLOAD_AREA_FROM_X, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_FROM_Y, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_FROM_Z, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_TO_X, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_TO_Y, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_TO_Z, Tag.TAG_INT)) {
            return false;
        }
        int minX = Math.min(payload.getInt(PAYLOAD_AREA_FROM_X), payload.getInt(PAYLOAD_AREA_TO_X));
        int maxX = Math.max(payload.getInt(PAYLOAD_AREA_FROM_X), payload.getInt(PAYLOAD_AREA_TO_X));
        int minY = Math.min(payload.getInt(PAYLOAD_AREA_FROM_Y), payload.getInt(PAYLOAD_AREA_TO_Y));
        int maxY = Math.max(payload.getInt(PAYLOAD_AREA_FROM_Y), payload.getInt(PAYLOAD_AREA_TO_Y));
        int minZ = Math.min(payload.getInt(PAYLOAD_AREA_FROM_Z), payload.getInt(PAYLOAD_AREA_TO_Z));
        int maxZ = Math.max(payload.getInt(PAYLOAD_AREA_FROM_Z), payload.getInt(PAYLOAD_AREA_TO_Z));
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        String safeText = text == null ? "" : text.trim();
        if (font.width(safeText) <= maxWidth) {
            return safeText;
        }
        String ellipsis = "...";
        int targetWidth = Math.max(0, maxWidth - font.width(ellipsis));
        while (!safeText.isEmpty() && font.width(safeText) > targetWidth) {
            safeText = safeText.substring(0, safeText.length() - 1);
        }
        return safeText + ellipsis;
    }

    private record RenderCandidate(Vec3 anchor, double distanceSqr, ObjectLabel label) {
    }

    private record ObjectLabel(String title, String detail, int titleColor, int detailColor) {
        private ObjectLabel(String title, String detail, int detailColor) {
            this(title, detail, TITLE_COLOR, detailColor);
        }

        private ObjectLabel {
            title = title == null ? "" : title;
            detail = detail == null ? "" : detail;
        }
    }
}
