package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.refit.TdmRoomActionButton;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomActionController;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomButtonStateBinder;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomData;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomInfoPanelRenderer;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomListRenderer;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomSessionState;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomStateEvaluator;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomUiState;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TDM 房间选择界面
 * 显示所有可用房间和房间内的玩家信息
 */
public class TdmRoomScreen extends Screen {
    private static final int PAGE_PADDING = 14;
    private static final int PANEL_GAP = 12;
    private static final int HEADER_HEIGHT = 44;
    private static final int FOOTER_HEIGHT = 50;
    private static final int ROOM_ITEM_HEIGHT = 36;
    private static final long ENTER_ANIMATION_MS = 180L;
    private static final long ROOM_LIST_APPLY_DEBOUNCE_MS = 60L;
    private static final long INFO_CONTENT_FADE_MS = 85L;

    private final TdmRoomSessionState roomState = new TdmRoomSessionState();
    private final TdmRoomUiState uiState = new TdmRoomUiState();
    private final TdmRoomActionController actionController;

    // 房间列表区域
    private int roomListX;
    private int roomListY;
    private int roomListWidth;
    private int roomListHeight;
    private int roomListScrollOffset = 0;
    private int roomListMaxScrollOffset = 0;
    private List<String> roomNames = new ArrayList<>();

    // 右侧信息面板区域
    private int rightPanelX;
    private int rightPanelY;
    private int rightPanelWidth;
    private int rightPanelHeight;

    // 视觉状态
    private long openedAtMs = 0L;
    private final Map<String, Float> roomHighlightProgress = new HashMap<>();
    private final Map<String, Long> roomEnteredAtMs = new HashMap<>();
    private Map<String, TdmRoomData> pendingRoomListUpdate = null;
    private long pendingRoomListReceivedAtMs = 0L;
    private String infoContextKey = "";
    private long infoContentTransitionAtMs = 0L;

    // 队伍选择按钮
    private Button kortacButton;
    private Button specgruButton;
    private Button joinButton;
    private Button leaveButton;
    private Button readyButton;
    private Button voteStartButton;
    private Button voteEndButton;
    private int infoActionBottomY;
    private int bottomActionBarX;
    private int bottomActionBarY;
    private int bottomActionBarWidth;
    private int bottomActionBarHeight;

    public TdmRoomScreen() {
        super(Component.translatable("screen.codpattern.tdm_room.title"));
        this.actionController = new TdmRoomActionController(roomState, uiState, this::updateButtonStates);
    }

    @Override
    protected void init() {
        super.init();

        int contentTop = HEADER_HEIGHT;
        int contentBottom = Math.max(contentTop + 140, this.height - FOOTER_HEIGHT);
        int contentHeight = Math.max(120, contentBottom - contentTop);

        roomListX = PAGE_PADDING;
        roomListY = contentTop;
        roomListHeight = contentHeight;

        int availableContentWidth = Math.max(280, this.width - PAGE_PADDING * 2 - PANEL_GAP);
        int minRightPanelWidth = 250;
        int desiredLeftWidth = (int) (availableContentWidth * 0.36f);
        int maxLeftWidth = Math.max(170, availableContentWidth - minRightPanelWidth);
        roomListWidth = clamp(desiredLeftWidth, 180, maxLeftWidth);

        rightPanelX = roomListX + roomListWidth + PANEL_GAP;
        rightPanelY = contentTop;
        rightPanelWidth = this.width - rightPanelX - PAGE_PADDING;
        rightPanelHeight = contentHeight;

        if (rightPanelWidth < minRightPanelWidth) {
            roomListWidth = Math.max(160, availableContentWidth - minRightPanelWidth);
            rightPanelX = roomListX + roomListWidth + PANEL_GAP;
            rightPanelWidth = this.width - rightPanelX - PAGE_PADDING;
        }

        roomListScrollOffset = 0;
        roomListMaxScrollOffset = 0;
        openedAtMs = System.currentTimeMillis();
        infoContextKey = currentInfoContextKey();
        infoContentTransitionAtMs = openedAtMs;

        // 请求房间列表
        actionController.requestRoomList();

        // 添加 UI 按钮
        addButtons();
    }

    /**
     * 添加 UI 按钮
     */
    private void addButtons() {
        int buttonHeight = 20;
        int spacing = 6;
        int actionPadding = 8;

        int actionX = rightPanelX + actionPadding;
        int actionWidth = Math.max(120, rightPanelWidth - actionPadding * 2);
        int halfWidth = Math.max(1, (actionWidth - spacing) / 2);
        int teamButtonY = rightPanelY + 88;

        // KORTAC 队伍按钮
        kortacButton = addRenderableWidget(new TdmRoomActionButton(
                actionX,
                teamButtonY,
                halfWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.join_kortac"),
                btn -> actionController.selectTeam(TdmTeamNames.KORTAC),
                0xFFE35A5A));

        // SPECGRU 队伍按钮
        specgruButton = addRenderableWidget(new TdmRoomActionButton(
                actionX + halfWidth + spacing,
                teamButtonY,
                halfWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.join_specgru"),
                btn -> actionController.selectTeam(TdmTeamNames.SPECGRU),
                0xFF66A6FF));

        // Ready 按钮
        int readyY = teamButtonY + buttonHeight + spacing;
        readyButton = addRenderableWidget(new TdmRoomActionButton(
                actionX,
                readyY,
                actionWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm.ready"),
                btn -> actionController.toggleReady(),
                0xFF6CCF8A));

        // 开始/结束投票按钮
        int voteY = readyY + buttonHeight + spacing;
        voteStartButton = addRenderableWidget(new TdmRoomActionButton(
                actionX,
                voteY,
                halfWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.vote_start"),
                btn -> actionController.voteStart(),
                CodTheme.SELECTED_BORDER));
        voteEndButton = addRenderableWidget(new TdmRoomActionButton(
                actionX + halfWidth + spacing,
                voteY,
                halfWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.vote_end"),
                btn -> actionController.voteEnd(),
                CodTheme.TEXT_DANGER));
        infoActionBottomY = voteY + buttonHeight;

        // 底部按钮栏
        int stripOuterPadding = 2;
        bottomActionBarX = roomListX - stripOuterPadding;
        bottomActionBarWidth = (rightPanelX + rightPanelWidth) - bottomActionBarX + stripOuterPadding;
        bottomActionBarHeight = 34;
        bottomActionBarY = this.height - bottomActionBarHeight - 8;

        int stripInnerPadding = 8;
        int buttonWidth = Math.max(80, Math.min(130, (bottomActionBarWidth - stripInnerPadding * 2 - spacing * 3) / 4));
        int bottomY = bottomActionBarY + 8;
        int totalWidth = 4 * buttonWidth + 3 * spacing;
        int startX = bottomActionBarX + (bottomActionBarWidth - totalWidth) / 2;

        joinButton = addRenderableWidget(new TdmRoomActionButton(
                startX,
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.join_room"),
                btn -> handlePrimaryJoinAction(),
                CodTheme.HOVER_BORDER));

        leaveButton = addRenderableWidget(new TdmRoomActionButton(
                startX + buttonWidth + spacing,
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.leave_room"),
                btn -> actionController.leaveRoom(),
                CodTheme.TEXT_DANGER));

        addRenderableWidget(new TdmRoomActionButton(
                startX + 2 * (buttonWidth + spacing),
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.common.refresh"),
                btn -> actionController.requestRoomList(),
                CodTheme.SELECTED_BORDER));

        addRenderableWidget(new TdmRoomActionButton(
                startX + 3 * (buttonWidth + spacing),
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.common.back"),
                btn -> onClose(),
                0xFF9AA5B1));

        updateButtonStates();
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        String currentRoomState = actionController.currentRoomState();
        boolean selectedDifferentRoom = roomState.selectedRoom() != null
                && roomState.joinedRoom() != null
                && !roomState.selectedRoom().equals(roomState.joinedRoom());
        boolean localPlayerReady = TdmRoomStateEvaluator.isLocalPlayerReady(
                Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID(),
                roomState.teamPlayers());
        boolean localSpectatorInPlaying = actionController.isLocalSpectatorInPlaying();
        TdmRoomButtonStateBinder.refresh(
                joinButton,
                leaveButton,
                readyButton,
                voteStartButton,
                voteEndButton,
                kortacButton,
                specgruButton,
                roomState.selectedRoom() != null,
                selectedDifferentRoom,
                roomState.joinedRoom() != null,
                actionController.hasPendingAction(),
                currentRoomState,
                actionController.pendingAction(),
                actionController.isLeavePending(),
                actionController.leaveSecondsRemaining(),
                localPlayerReady,
                localSpectatorInPlaying,
                actionController.isJoinGamePending(),
                actionController.joinGameSecondsRemaining());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        Minecraft mc = Minecraft.getInstance();
        float enterProgress = enterProgress();
        int titleColor = withAlpha(0xFFFFFFFF, Math.max(85, (int) (255.0f * enterProgress)));

        graphics.drawCenteredString(
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.header"),
                this.width / 2,
                20,
                titleColor);

        renderRoomListPanel(graphics, mc, mouseX, mouseY, enterProgress);
        refreshInfoContextTransition(false);
        renderInfoPanel(graphics, mc, enterProgress);
        renderBottomActionStrip(graphics, mc, enterProgress);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        actionController.tick();
        flushPendingRoomListUpdate(false);
        refreshInfoContextTransition(false);
    }

    /**
     * 渲染房间列表面板
     */
    private void renderRoomListPanel(
            GuiGraphics graphics,
            Minecraft mc,
            int mouseX,
            int mouseY,
            float enterProgress) {
        roomListMaxScrollOffset = Math.max(0, roomState.rooms().size() - visibleRoomCapacity());
        roomListScrollOffset = clamp(roomListScrollOffset, 0, roomListMaxScrollOffset);

        roomNames = TdmRoomListRenderer.render(
                graphics,
                mc,
                roomListX,
                roomListY,
                roomListWidth,
                roomListHeight,
                ROOM_ITEM_HEIGHT,
                roomState.rooms(),
                roomState.selectedRoom(),
                roomState.joinedRoom(),
                roomListScrollOffset,
                roomHighlightProgress,
                roomEnteredAtMs,
                System.currentTimeMillis(),
                mouseX,
                mouseY,
                enterProgress);
    }

    /**
     * 渲染右侧信息面板
     */
    private void renderInfoPanel(GuiGraphics graphics, Minecraft mc, float enterProgress) {
        TdmRoomInfoPanelRenderer.render(
                graphics,
                mc,
                rightPanelX,
                rightPanelY,
                rightPanelWidth,
                rightPanelHeight,
                infoActionBottomY,
                roomState.joinedRoom(),
                roomState.selectedRoom(),
                roomState.rooms(),
                roomState.teamPlayers(),
                actionController.isLeavePending(),
                actionController.isJoinGamePending(),
                actionController.hasRoomNotice(),
                actionController.roomNoticeText(),
                actionController.roomNoticeColor(),
                enterProgress,
                infoContentFadeProgress());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = roomListY;
        if (button == 0
                && mouseX >= roomListX
                && mouseX <= roomListX + roomListWidth
                && mouseY >= listTop
                && mouseY < listTop + roomListHeight) {

            int index = roomListScrollOffset + (int) ((mouseY - listTop) / ROOM_ITEM_HEIGHT);
            if (index >= 0 && index < roomNames.size()) {
                roomState.setSelectedRoom(roomNames.get(index));
                refreshInfoContextTransition(true);
                updateButtonStates();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = roomListY;
        if (mouseX >= roomListX
                && mouseX <= roomListX + roomListWidth
                && mouseY >= listTop
                && mouseY < listTop + roomListHeight) {
            if (delta > 0 && roomListScrollOffset > 0) {
                roomListScrollOffset--;
                return true;
            }
            if (delta < 0 && roomListScrollOffset < roomListMaxScrollOffset) {
                roomListScrollOffset++;
                return true;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, CodTheme.BG_TOP, CodTheme.BG_BOTTOM);
    }

    /**
     * 更新房间列表
     */
    public void updateRoomList(Map<String, TdmRoomData> rooms) {
        pendingRoomListUpdate = rooms == null ? new HashMap<>() : new HashMap<>(rooms);
        pendingRoomListReceivedAtMs = System.currentTimeMillis();
        if (roomState.rooms().isEmpty()) {
            flushPendingRoomListUpdate(true);
        }
    }

    private void applyRoomListUpdate(Map<String, TdmRoomData> rooms) {
        Set<String> previous = new HashSet<>(roomState.rooms().keySet());
        actionController.updateRoomList(rooms);

        long now = System.currentTimeMillis();
        for (String roomName : roomState.rooms().keySet()) {
            if (!previous.contains(roomName)) {
                roomEnteredAtMs.put(roomName, now);
            }
        }
        roomEnteredAtMs.keySet().retainAll(roomState.rooms().keySet());
        roomHighlightProgress.keySet().retainAll(roomState.rooms().keySet());

        roomListMaxScrollOffset = Math.max(0, roomState.rooms().size() - visibleRoomCapacity());
        roomListScrollOffset = clamp(roomListScrollOffset, 0, roomListMaxScrollOffset);
    }

    /**
     * 更新当前房间的玩家列表
     */
    public void updatePlayerList(String mapName, Map<String, List<PlayerInfo>> teamPlayers) {
        actionController.updatePlayerList(mapName, teamPlayers);
    }

    /**
     * 设置已加入的房间
     */
    public void setJoinedRoom(String roomName) {
        actionController.setJoinedRoom(roomName);
    }

    public void handleJoinResult(boolean success, String mapName, String reasonCode, String reasonMessage) {
        actionController.handleJoinResult(success, mapName, reasonCode, reasonMessage);
    }

    public void handleLeaveResult(boolean success, String roomName, String reasonCode, String reasonMessage) {
        actionController.handleLeaveResult(success, roomName, reasonCode, reasonMessage);
    }

    public void handleJoinGameResult(boolean success, long requestId, String mapName, String reasonCode,
            String reasonMessage) {
        actionController.handleJoinGameResult(success, requestId, mapName, reasonCode, reasonMessage);
    }

    @Override
    public void onClose() {
        pendingRoomListUpdate = null;
        actionController.reset();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderBottomActionStrip(GuiGraphics graphics, Minecraft mc, float enterProgress) {
        int stripTop = bottomActionBarY - 4;
        int stripBottom = bottomActionBarY + bottomActionBarHeight + 4;
        int stripLeft = bottomActionBarX;
        int stripRight = bottomActionBarX + bottomActionBarWidth;

        graphics.fillGradient(stripLeft, stripTop, stripRight, stripBottom,
                withAlpha(CodTheme.PANEL_BG, Math.max(60, (int) (170 * enterProgress))),
                withAlpha(CodTheme.CARD_BG_BOTTOM, Math.max(70, (int) (190 * enterProgress))));
        graphics.fill(stripLeft, stripTop, stripRight, stripTop + 1, withAlpha(CodTheme.BORDER_SUBTLE, 160));
        graphics.fill(stripLeft, stripBottom - 1, stripRight, stripBottom, withAlpha(CodTheme.BORDER_SUBTLE, 160));
        graphics.fill(stripLeft, stripTop, stripLeft + 1, stripBottom, withAlpha(CodTheme.BORDER_SUBTLE, 160));
        graphics.fill(stripRight - 1, stripTop, stripRight, stripBottom, withAlpha(CodTheme.BORDER_SUBTLE, 160));

        Component hint = actionHintText();
        graphics.drawString(
                mc.font,
                hint,
                stripLeft + 8,
                stripTop - 10,
                withAlpha(CodTheme.TEXT_SECONDARY, Math.max(70, (int) (230 * enterProgress))));
    }

    private Component actionHintText() {
        String joinedRoom = roomState.joinedRoom();
        String selectedRoom = roomState.selectedRoom();
        if (joinedRoom != null && selectedRoom != null && !joinedRoom.equals(selectedRoom)) {
            return Component.translatable("screen.codpattern.tdm_room.action_hint_switch", joinedRoom, selectedRoom);
        }
        if (joinedRoom != null) {
            return Component.translatable("screen.codpattern.tdm_room.action_hint_joined", joinedRoom);
        }
        if (selectedRoom != null) {
            return Component.translatable("screen.codpattern.tdm_room.action_hint_selected", selectedRoom);
        }
        return Component.translatable("screen.codpattern.tdm_room.action_hint_none");
    }

    private void handlePrimaryJoinAction() {
        String joinedRoom = roomState.joinedRoom();
        String selectedRoom = roomState.selectedRoom();
        if (joinedRoom != null) {
            if (selectedRoom != null && !selectedRoom.equals(joinedRoom)) {
                actionController.switchToRoom(selectedRoom);
            }
            return;
        }
        actionController.joinSelectedRoom();
    }

    private void flushPendingRoomListUpdate(boolean force) {
        if (pendingRoomListUpdate == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - pendingRoomListReceivedAtMs < ROOM_LIST_APPLY_DEBOUNCE_MS) {
            return;
        }
        Map<String, TdmRoomData> toApply = pendingRoomListUpdate;
        pendingRoomListUpdate = null;
        pendingRoomListReceivedAtMs = 0L;
        applyRoomListUpdate(toApply);
    }

    private void refreshInfoContextTransition(boolean force) {
        String currentKey = currentInfoContextKey();
        if (force || !currentKey.equals(infoContextKey)) {
            infoContextKey = currentKey;
            infoContentTransitionAtMs = System.currentTimeMillis();
        }
    }

    private String currentInfoContextKey() {
        String joined = roomState.joinedRoom();
        if (joined != null && !joined.isBlank()) {
            return "J:" + joined;
        }
        String selected = roomState.selectedRoom();
        if (selected != null && !selected.isBlank()) {
            return "S:" + selected;
        }
        return "";
    }

    private float infoContentFadeProgress() {
        if (infoContentTransitionAtMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - infoContentTransitionAtMs;
        float raw = Math.min(1.0f, Math.max(0.0f, elapsed / (float) INFO_CONTENT_FADE_MS));
        return 0.35f + (raw * 0.65f);
    }

    private int visibleRoomCapacity() {
        return Math.max(1, roomListHeight / ROOM_ITEM_HEIGHT);
    }

    private float enterProgress() {
        if (openedAtMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float raw = Math.min(1.0f, Math.max(0.0f, elapsed / (float) ENTER_ANIMATION_MS));
        return 0.2f + (raw * 0.8f);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
