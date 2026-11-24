package com.cdp.codpattern.compat.tacz.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Locale;

/** Projects a pack's refit cameras into the loadout's orthographic FIXED-item preview. */
public final class RefitPreviewCamera {
    private record View(Vector3f anchor, @Nullable Quaternionf itemRotation, float zoom) {
        private Quaternionf rotationOr(Quaternionf overviewRotation) {
            return itemRotation == null ? overviewRotation : itemRotation;
        }
    }

    public static final class Pose {
        public static final Pose OVERVIEW = new Pose(new View(new Vector3f(), null, 1));
        private final View from;
        private final View to;
        private final float progress;

        private Pose(View view) {
            this(view, view, 1);
        }

        private Pose(View from, View to, float progress) {
            this.from = from;
            this.to = to;
            this.progress = progress;
        }

        public void apply(PoseStack pose, Quaternionf overviewRotation) {
            var rotation = to.rotationOr(overviewRotation);
            if (from.itemRotation() != to.itemRotation()) {
                rotation = from.rotationOr(overviewRotation).slerp(rotation, progress, new Quaternionf());
            }
            pose.mulPose(rotation);
            if (this == OVERVIEW) return;
            float zoom = Mth.lerp(progress, from.zoom(), to.zoom());
            pose.scale(zoom, zoom, zoom);
            var start = from.anchor();
            var end = to.anchor();
            pose.translate(-Mth.lerp(progress, start.x, end.x), -Mth.lerp(progress, start.y, end.y),
                    -Mth.lerp(progress, start.z, end.z));
        }

        private Pose interpolate(Pose next, float t) {
            if (this == next || t <= 0) return this;
            if (t >= 1) return next;
            return new Pose(to, next.to, t);
        }
    }

    private final EnumMap<AttachmentType, Pose> slots = new EnumMap<>(AttachmentType.class);
    private GunDisplayInstance display;

    public Pose sample(ItemStack gun) {
        var before = RefitTransform.getOldTransformType();
        var after = RefitTransform.getCurrentTransformType();
        float progress = Mth.clamp(RefitTransform.getTransformProgress(), 0, 1);
        if (after == AttachmentType.NONE && (before == AttachmentType.NONE || progress >= 1)) return Pose.OVERVIEW;
        var current = TimelessAPI.getGunDisplay(gun).orElse(null);
        if (current != display) {
            display = current;
            slots.clear();
        }
        if (display == null || display.getGunModel() == null) return Pose.OVERVIEW;
        Pose to = slots.computeIfAbsent(after, this::slotPose);
        if (progress >= 1) return to;
        float remaining = 1 - progress;
        return slots.computeIfAbsent(before, this::slotPose).interpolate(to, 1 - remaining * remaining * remaining);
    }

    private Pose slotPose(AttachmentType type) {
        if (type == AttachmentType.NONE) return Pose.OVERVIEW;
        var model = display.getGunModel();
        var cameraPath = model.getRefitAttachmentViewPath(type);
        var mount = mount(model, type);
        if ((cameraPath == null || cameraPath.isEmpty()) && mount == null) return Pose.OVERVIEW;

        Vector3f unit = new Vector3f(1);
        var fixed = model.getNode("fixed");
        Matrix4f fixedFrame = frame(fixed, unit);
        Vector3f origin = fixedFrame.getTranslation(new Vector3f());
        Vector3f anchor = mount == null ? null : frame(mount, unit).getTranslation(new Vector3f());
        Quaternionf rotation = null;
        float zoom = 1.55f;
        if (cameraPath != null && !cameraPath.isEmpty()) {
            var camera = frame(cameraPath.get(cameraPath.size() - 1), unit);
            var eye = camera.getTranslation(new Vector3f());
            if (anchor == null) {
                // Packs may supply only the refit camera. Follow its viewing ray to the gun's depth.
                var ray = camera.transformDirection(new Vector3f(0, 0, -1)).normalize();
                float depth = Math.max(0, origin.sub(eye, new Vector3f()).dot(ray));
                anchor = new Vector3f(eye).fma(depth, ray);
            }
            // Convert from FIXED-item axes back to model axes, then use TaCZ's first-person camera axes.
            rotation = new Quaternionf().rotationZ(Mth.PI)
                    .mul(camera.getNormalizedRotation(new Quaternionf()).conjugate())
                    .mul(fixedFrame.getNormalizedRotation(new Quaternionf()))
                    .rotateZ(-Mth.PI).normalize();
            var overviewPath = model.getRefitAttachmentViewPath(AttachmentType.NONE);
            float distance = eye.distance(anchor);
            if (overviewPath != null && !overviewPath.isEmpty() && distance > .001f) {
                var overviewEye = frame(overviewPath.get(overviewPath.size() - 1), unit).getTranslation(new Vector3f());
                zoom = Mth.clamp(overviewEye.distance(origin) / distance, 1.35f, 2f);
            }
        }
        // TaCZ's FIXED renderer mirrors X/Y and subtracts the scaled item-frame origin before drawing.
        var fixedScale = display.getTransform().getScale().getFixed();
        var scale = fixedScale == null ? unit : fixedScale;
        var toItem = new Matrix4f().rotationZ(Mth.PI).mul(frame(fixed, scale).invert()).scale(scale);
        toItem.transformPosition(anchor);
        if (!anchor.isFinite() || (rotation != null && !rotation.isFinite()) || !Float.isFinite(zoom)) {
            return Pose.OVERVIEW;
        }
        return new Pose(new View(anchor, rotation, zoom));
    }

    private static BedrockPart mount(BedrockGunModel model, AttachmentType type) {
        BedrockPart node = modelNode(model, type.name().toLowerCase(Locale.ROOT) + "_pos");
        if (node != null) return node;
        return switch (type) {
            case EXTENDED_MAG -> modelNode(model, "magazine");
            case MUZZLE -> modelNode(model, "muzzle_flash");
            default -> null;
        };
    }

    private static BedrockPart modelNode(BedrockGunModel model, String name) {
        // TaCZ registers zero-position renderer placeholders even when a pack omits the bone.
        return model.getBone(name) == null ? null : model.getNode(name);
    }

    /** Bedrock bind frame, in blocks relative to the same 24-pixel origin as TaCZ's positioning nodes. */
    private static Matrix4f frame(BedrockPart part, Vector3f scale) {
        if (part == null) return new Matrix4f();
        var parent = part.getParent();
        return frame(parent, scale)
                .translate(part.x / 16f * scale.x, (part.y / 16f - (parent == null ? 1.5f : 0)) * scale.y,
                        part.z / 16f * scale.z)
                .rotateZYX(part.getInitRotZ(), part.getInitRotY(), part.getInitRotX());
    }
}
