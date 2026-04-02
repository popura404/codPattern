package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
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
import com.cdp.codpattern.network.tdm.RequestRoomRosterResyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

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
    private static final int BASE_PAGE_PADDING = 16;
    private static final int BASE_PANEL_GAP = 14;
    private static final int BASE_HEADER_HEIGHT = 48;
    private static final int BASE_FOOTER_HEIGHT = 20;
    private static final int BASE_ROOM_ITEM_HEIGHT = 38;
    private static final float LEFT_PANEL_WIDTH_RATIO = 0.25f;
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
    private TdmRoomListRenderer.RenderResult roomListRenderResult = TdmRoomListRenderer.RenderResult.empty();

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
    private long pendingRoomListSnapshotVersion = 0L;
    private long pendingRoomListReceivedAtMs = 0L;
    private String infoContextKey = "";
    private long infoContentTransitionAtMs = 0L;

    // 房内操作按钮
    private Button kortacButton;
    private Button specgruButton;
    private Button readyButton;
    private Button voteStartButton;
    private Button voteEndButton;
    private int infoActionBottomY;
    private int collapsedActionBottomY;
    private int fullActionBottomY;

    public TdmRoomScreen() {
        super(Component.translatable("screen.codpattern.room.title"));
        this.actionController = new TdmRoomActionController(roomState, uiState, this::updateButtonStates);
    }

    @Override
    protected void init() {
        super.init();

        int pagePadding = scaled(BASE_PAGE_PADDING);
        int panelGap = scaled(BASE_PANEL_GAP);
        int headerHeight = scaled(BASE_HEADER_HEIGHT);
        int footerHeight = scaled(BASE_FOOTER_HEIGHT);

        int contentTop = headerHeight;
        int contentBottom = Math.max(contentTop + scaled(156), this.height - footerHeight);
        int contentHeight = Math.max(scaled(160), contentBottom - contentTop);

        roomListX = pagePadding;
        roomListY = contentTop;
        roomListHeight = contentHeight;

        int availableContentWidth = Math.max(scaled(280), this.width - pagePadding * 2 - panelGap);
        int minRightPanelWidth = scaled(268);
        int desiredLeftWidth = (int) (availableContentWidth * LEFT_PANEL_WIDTH_RATIO);
        int maxLeftWidth = Math.max(scaled(148), availableContentWidth - minRightPanelWidth);
        roomListWidth = clamp(desiredLeftWidth, scaled(150), maxLeftWidth);

        rightPanelX = roomListX + roomListWidth + panelGap;
        rightPanelY = contentTop;
        rightPanelWidth = this.width - rightPanelX - pagePadding;
        rightPanelHeight = contentHeight;

        if (rightPanelWidth < minRightPanelWidth) {
            roomListWidth = Math.max(scaled(150), availableContentWidth - minRightPanelWidth);
            rightPanelX = roomListX + roomListWidth + panelGap;
            rightPanelWidth = this.width - rightPanelX - pagePadding;
        }

        roomListScrollOffset = 0;
        roomListMaxScrollOffset = 0;
        roomListRenderResult = TdmRoomListRenderer.RenderResult.empty();
        openedAtMs = System.currentTimeMillis();
        infoContextKey = currentInfoContextKey();
        infoContentTransitionAtMs = openedAtMs;
        restoreJoinedRoomFromClientStateIfNeeded();

        // 请求房间列表
        actionController.requestRoomList();

        // 添加 UI 按钮
        addButtons();
    }

    /**
     * 添加 UI 按钮
     */
    private void addButtons() {
        int buttonHeight = scaled(20);
        int spacing = scaled(6);
        int actionPadding = scaled(10);

        int actionX = rightPanelX + actionPadding;
        int actionWidth = Math.max(scaled(120), rightPanelWidth - actionPadding * 2);
        int halfWidth = Math.max(1, (actionWidth - spacing) / 2);
        int headerY = rightPanelY + scaled(4);
        int headerBottom = headerY + scaled(26);
        int teamButtonY = headerBottom + scaled(8);

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
        collapsedActionBottomY = headerBottom;
        fullActionBottomY = voteY + buttonHeight;
        infoActionBottomY = collapsedActionBottomY;

        updateButtonStates();
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        String currentRoomState = actionController.currentRoomState();
        boolean hasJoinedRoom = roomState.joinedRoom() != null;
        boolean previewingOtherRoom = actionController.isPreviewingOtherRoom();
        boolean localPlayerReady = TdmRoomStateEvaluator.isLocalPlayerReady(
                Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID(),
                roomState.teamPlayers());
        TdmRoomButtonStateBinder.refresh(
                readyButton,
                voteStartButton,
                voteEndButton,
                kortacButton,
                specgruButton,
                hasJoinedRoom,
                actionController.hasPendingAction(),
                currentRoomState,
                localPlayerReady);
        boolean showCurrentRoomControls = hasJoinedRoom && !previewingOtherRoom;
        if (kortacButton != null) {
            kortacButton.visible = showCurrentRoomControls;
        }
        if (specgruButton != null) {
            specgruButton.visible = showCurrentRoomControls;
        }
        if (readyButton != null) {
            readyButton.visible = showCurrentRoomControls;
        }
        if (voteStartButton != null) {
            voteStartButton.visible = showCurrentRoomControls;
        }
        if (voteEndButton != null) {
            voteEndButton.visible = showCurrentRoomControls;
        }
        infoActionBottomY = showCurrentRoomControls ? fullActionBottomY : collapsedActionBottomY;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        Minecraft mc = Minecraft.getInstance();
        float enterProgress = enterProgress();
        int titleColor = withAlpha(0xFFFFFFFF, Math.max(85, (int) (255.0f * enterProgress)));

        GuiTextHelper.drawReferenceCenteredString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.room.header"),
                this.width / 2,
                scaled(20),
                titleColor,
                false);

        renderRoomListPanel(graphics, mc, mouseX, mouseY, enterProgress);
        refreshInfoContextTransition(false);
        renderInfoPanel(graphics, mc, enterProgress);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        actionController.tick();
        restoreJoinedRoomFromClientStateIfNeeded();
        roomState.refreshJoinedRoomLiveState();
        flushPendingRoomListUpdate(false);
        refreshInfoContextTransition(false);
        updateButtonStates();
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

        roomListRenderResult = TdmRoomListRenderer.render(
                graphics,
                mc,
                roomListX,
                roomListY,
                roomListWidth,
                roomListHeight,
                roomItemHeight(),
                roomState.lobbySummaryState(),
                roomState.selectedRoom(),
                roomState.joinedRoom(),
                roomListScrollOffset,
                roomHighlightProgress,
                roomEnteredAtMs,
                System.currentTimeMillis(),
                mouseX,
                mouseY,
                actionController.hasPendingAction(),
                actionController.isLeavePending(),
                actionController.pendingSwitchTargetRoom(),
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
                roomState.lobbySummaryState(),
                roomState.joinedRoomLiveState(),
                roomState.selectedRoomPreviewState(),
                roomState.joinedRoom(),
                roomState.selectedRoom(),
                actionController.hasConfirmPending(),
                actionController.confirmHintText(),
                actionController.confirmHintColor(),
                actionController.hasRoomNotice(),
                actionController.roomNoticeText(),
                actionController.roomNoticeColor(),
                enterProgress,
                infoContentFadeProgress());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            TdmRoomListRenderer.ActionHitbox actionHitbox = roomListRenderResult.actionAt(mouseX, mouseY);
            if (actionHitbox != null) {
                handleRoomActionClick(actionHitbox);
                return true;
            }
            String roomName = roomListRenderResult.roomAt(mouseX, mouseY);
            if (roomName != null) {
                actionController.selectRoom(roomName);
                refreshInfoContextTransition(true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleRoomActionClick(TdmRoomListRenderer.ActionHitbox actionHitbox) {
        if (!actionHitbox.enabled()) {
            return;
        }
        if (actionHitbox.type() != TdmRoomListRenderer.ActionType.LEAVE) {
            actionController.selectRoom(actionHitbox.roomName());
            refreshInfoContextTransition(true);
        }
        switch (actionHitbox.type()) {
            case JOIN -> actionController.joinSelectedRoom();
            case LEAVE -> actionController.leaveRoom();
            case SWITCH -> actionController.switchToRoom(actionHitbox.roomName());
        }
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
    public void updateRoomList(long snapshotVersion, Map<String, TdmRoomData> rooms) {
        pendingRoomListUpdate = rooms == null ? new HashMap<>() : new HashMap<>(rooms);
        pendingRoomListSnapshotVersion = Math.max(0L, snapshotVersion);
        pendingRoomListReceivedAtMs = System.currentTimeMillis();
        if (roomState.rooms().isEmpty()) {
            flushPendingRoomListUpdate(true);
        }
    }

    private void applyRoomListUpdate(Map<String, TdmRoomData> rooms) {
        Set<String> previous = new HashSet<>(roomState.rooms().keySet());
        actionController.updateRoomList(pendingRoomListSnapshotVersion, rooms);

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
    public void updatePlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        actionController.updatePlayerList(roomKey, rosterVersion, teamPlayers);
    }

    public void updatePlayerDelta(String roomKey, int rosterVersion) {
        actionController.updatePlayerDelta(roomKey, rosterVersion);
    }

    public void updatePreviewPlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        actionController.updatePreviewPlayerList(roomKey, rosterVersion, teamPlayers);
    }

    /**
     * 设置已加入的房间
     */
    public void setJoinedRoom(String roomKey) {
        actionController.setJoinedRoom(roomKey);
    }

    public void handleJoinResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        actionController.handleJoinResult(success, roomKey, reasonCode, reasonMessage);
    }

    public void handleLeaveResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        actionController.handleLeaveResult(success, roomKey, reasonCode, reasonMessage);
    }
    @Override
    public void onClose() {
        pendingRoomListUpdate = null;
        pendingRoomListSnapshotVersion = 0L;
        roomListRenderResult = TdmRoomListRenderer.RenderResult.empty();
        actionController.unsubscribeRoomList();
        actionController.reset();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    private void restoreJoinedRoomFromClientStateIfNeeded() {
        if (!roomState.restoreJoinedRoomFromClientState()) {
            return;
        }
        String restoredJoinedRoom = roomState.joinedRoom();
        if ((roomState.selectedRoom() == null || roomState.selectedRoom().isBlank())
                && restoredJoinedRoom != null
                && !restoredJoinedRoom.isBlank()) {
            roomState.setSelectedRoom(restoredJoinedRoom);
        }
        roomState.refreshJoinedRoomLiveState();
        ModNetworkChannel.sendToServer(new RequestRoomRosterResyncPacket());
        refreshInfoContextTransition(true);
    }

    private String currentInfoContextKey() {
        String joined = roomState.joinedRoom();
        String selected = roomState.selectedRoom();
        if (joined != null && !joined.isBlank()
                && selected != null && !selected.isBlank()
                && !selected.equals(joined)) {
            return "J:" + joined + "|S:" + selected;
        }
        if (joined != null && !joined.isBlank()) {
            return "J:" + joined;
        }
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
        return Math.max(1, TdmRoomListRenderer.listViewportHeight(roomListHeight) / roomItemHeight());
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

    private static int scaled(int value) {
        return GuiTextHelper.referenceScaled(value);
    }

    private static int roomItemHeight() {
        return scaled(BASE_ROOM_ITEM_HEIGHT);
    }
}
