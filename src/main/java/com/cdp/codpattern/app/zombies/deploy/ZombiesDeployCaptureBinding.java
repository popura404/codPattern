package com.cdp.codpattern.app.zombies.deploy;

import java.util.Objects;

public record ZombiesDeployCaptureBinding(
        String slotA,
        String slotB
) {
    public enum CaptureSlot {
        A,
        B
    }

    public ZombiesDeployCaptureBinding {
        slotA = Objects.requireNonNullElse(slotA, "").trim();
        slotB = Objects.requireNonNullElse(slotB, "").trim();
    }

    public static ZombiesDeployCaptureBinding forDraft(ZombiesDeployDraft draft) {
        if (draft == null) {
            return new ZombiesDeployCaptureBinding("", "");
        }
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {
            return mapRegistration();
        }
        return forObject(draft.objectType(), draft.capturePreset());
    }

    public static ZombiesDeployCaptureBinding forObject(String objectType, String capturePreset) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        if (ZombiesDeployFieldSchema.BARRIER.equals(type)) {
            String preset = ZombiesDeployDraft.normalizeCapturePreset(capturePreset, type);
            if (ZombiesDeployDraft.CAPTURE_BARRIER_INTERACTION.equals(preset)) {
                return new ZombiesDeployCaptureBinding("interaction", "");
            }
            return new ZombiesDeployCaptureBinding("areaFrom", "areaTo");
        }
        return switch (type) {
            case ZombiesDeployFieldSchema.WEAPON_WALL,
                 ZombiesDeployFieldSchema.AMMO_BOX,
                 ZombiesDeployFieldSchema.ARMOR_STATION,
                 ZombiesDeployFieldSchema.SODA_MACHINE,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> new ZombiesDeployCaptureBinding("pos", "interaction");
            case ZombiesDeployFieldSchema.INITIAL,
                 ZombiesDeployFieldSchema.ZOMBIE_SPAWN,
                 ZombiesDeployFieldSchema.POWER_SWITCH -> new ZombiesDeployCaptureBinding("pos", "lookAt");
            default -> new ZombiesDeployCaptureBinding("", "");
        };
    }

    public static ZombiesDeployCaptureBinding mapRegistration() {
        return new ZombiesDeployCaptureBinding("mapPos1", "mapPos2");
    }

    public String target(CaptureSlot slot) {
        return slot == CaptureSlot.A ? slotA : slotB;
    }
}
