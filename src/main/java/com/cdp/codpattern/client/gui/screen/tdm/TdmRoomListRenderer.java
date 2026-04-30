package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.screen.match.ModeRoomListRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy renderer name retained for older callers. New code should use {@link ModeRoomListRenderer}.
 */
@Deprecated(forRemoval = false)
public final class TdmRoomListRenderer {
    private TdmRoomListRenderer() {
    }

    public enum ActionType {JOIN, LEAVE, SWITCH}

    public record ActionHitbox(String roomName, ActionType type, int x, int y, int width,
            int height,
            boolean enabled
    ) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public record RoomHitbox(String roomName, int x, int y, int width, int height
    ) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public record RenderResult(
            List<String> roomNames,
            List<RoomHitbox> roomHitboxes,
            List<ActionHitbox> actionHitboxes
    ) {
        public static RenderResult empty() {
            return new RenderResult(List.of(), List.of(), List.of());
        }

        public ActionHitbox actionAt(double mouseX, double mouseY) {
            for (ActionHitbox hitbox : actionHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    return hitbox;
                }
            }
            return null;
        }

        public String roomAt(double mouseX, double mouseY) {
            for (RoomHitbox hitbox : roomHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    return hitbox.roomName();
                }
            }
            return null;
        }
    }

    public static int listViewportHeight(int panelHeight) {
        return ModeRoomListRenderer.listViewportHeight(panelHeight);
    }

    public static RenderResult render(
            GuiGraphics graphics,
            Minecraft mc,
            int roomListX,
            int roomListY,
            int roomListWidth,
            int roomListHeight,
            int roomItemHeight,
            LobbySummaryState lobbySummaryState,
            String modeFilterGameType,
            String selectedRoom,
            String joinedRoom,
            int scrollOffset,
            Map<String, Float> highlightProgress,
            Map<String, Long> roomEnteredAtMs,
            long nowMs,
            int mouseX,
            int mouseY,
            boolean hasPendingAction,
            boolean leavePending,
            String pendingSwitchTargetRoom,
            float panelAlphaFactor) {
        ModeRoomListRenderer.RenderResult result = ModeRoomListRenderer.render(
                graphics,
                mc,
                roomListX,
                roomListY,
                roomListWidth,
                roomListHeight,
                roomItemHeight,
                lobbySummaryState,
                modeFilterGameType,
                selectedRoom,
                joinedRoom,
                scrollOffset,
                highlightProgress,
                roomEnteredAtMs,
                nowMs,
                mouseX,
                mouseY,
                hasPendingAction,
                leavePending,
                pendingSwitchTargetRoom,
                panelAlphaFactor);
        return convert(result);
    }

    private static RenderResult convert(ModeRoomListRenderer.RenderResult result) {
        if (result == null) {
            return RenderResult.empty();
        }
        List<RoomHitbox> roomHitboxes = new ArrayList<>(result.roomHitboxes().size());
        for (ModeRoomListRenderer.RoomHitbox hitbox : result.roomHitboxes()) {
            roomHitboxes.add(new RoomHitbox(
                    hitbox.roomName(),
                    hitbox.x(),
                    hitbox.y(),
                    hitbox.width(),
                    hitbox.height()));
        }

        List<ActionHitbox> actionHitboxes = new ArrayList<>(result.actionHitboxes().size());
        for (ModeRoomListRenderer.ActionHitbox hitbox : result.actionHitboxes()) {
            actionHitboxes.add(new ActionHitbox(
                    hitbox.roomName(),
                    ActionType.valueOf(hitbox.type().name()),
                    hitbox.x(),
                    hitbox.y(),
                    hitbox.width(),
                    hitbox.height(),
                    hitbox.enabled()));
        }

        return new RenderResult(result.roomNames(), roomHitboxes, actionHitboxes);
    }
}
