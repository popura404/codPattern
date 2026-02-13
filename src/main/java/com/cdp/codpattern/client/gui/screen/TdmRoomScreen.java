package com.cdp.codpattern.client.gui.screen;

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
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TDM 房间选择界面
 * 显示所有可用房间和房间内的玩家信息
 */
public class TdmRoomScreen extends Screen {
    private final TdmRoomSessionState roomState = new TdmRoomSessionState();
    private final TdmRoomUiState uiState = new TdmRoomUiState();
    private final TdmRoomActionController actionController;

    // 房间列表区域
    private int roomListX;
    private int roomListY;
    private int roomListWidth;
    private int roomListHeight;
    private final int roomItemHeight = 34;
    private List<String> roomNames = new ArrayList<>();

    // 队伍选择按钮
    private Button kortacButton;
    private Button specgruButton;
    private Button joinButton;
    private Button leaveButton;
    private Button readyButton;
    private Button voteStartButton;
    private Button voteEndButton;
    private int infoActionBottomY;

    public TdmRoomScreen() {
        super(Component.translatable("screen.codpattern.tdm_room.title"));
        this.actionController = new TdmRoomActionController(roomState, uiState, this::updateButtonStates);
    }

    @Override
    protected void init() {
        super.init();

        // 计算稳定的布局尺寸
        int padding = 15;
        int headerHeight = 45;
        int footerHeight = 50;

        // 左侧房间列表面板 (占屏幕 35%)
        roomListX = padding;
        roomListY = headerHeight;
        roomListWidth = (int) (this.width * 0.35) - padding * 2;
        roomListHeight = this.height - headerHeight - footerHeight - padding;

        // 右侧面板起始位置
        int rightPanelX = roomListX + roomListWidth + padding * 2;
        int rightPanelWidth = this.width - rightPanelX - padding;

        // 请求房间列表
        actionController.requestRoomList();

        // 添加 UI 按钮
        addButtons(rightPanelX, rightPanelWidth);
    }

    /**
     * 添加 UI 按钮
     */
    private void addButtons(int rightPanelX, int rightPanelWidth) {
        int buttonHeight = 20;
        int spacing = 8;

        // 队伍选择按钮 (在右侧面板内)
        int teamButtonY = roomListY + 46;
        int teamButtonWidth = (rightPanelWidth - spacing) / 2;
        teamButtonWidth = Math.max(1, Math.min(teamButtonWidth, 150));

        // KORTAC 队伍按钮
        kortacButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX,
                teamButtonY,
                teamButtonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.join_kortac"),
                btn -> actionController.selectTeam(TdmTeamNames.KORTAC),
                0xFFE35A5A));

        // SPECGRU 队伍按钮
        specgruButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX + teamButtonWidth + spacing,
                teamButtonY,
                teamButtonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.join_specgru"),
                btn -> actionController.selectTeam(TdmTeamNames.SPECGRU),
                0xFF66A6FF));

        // Ready 按钮
        int readyY = teamButtonY + buttonHeight + spacing;
        int voteStartWidth = Math.max(1, Math.min(180, rightPanelWidth));
        readyButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX,
                readyY,
                voteStartWidth,
                buttonHeight,
                Component.literal("准备"),
                btn -> actionController.toggleReady(),
                0xFF6CCF8A));

        // 将“发起开始投票/发起结束投票”放到房间信息区
        int voteStartY = readyY + buttonHeight + spacing;
        voteStartButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX,
                voteStartY,
                voteStartWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.vote_start"),
                btn -> actionController.voteStart(),
                CodTheme.SELECTED_BORDER));
        int voteEndY = voteStartY + buttonHeight + spacing;
        voteEndButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX,
                voteEndY,
                voteStartWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.vote_end"),
                btn -> actionController.voteEnd(),
                CodTheme.TEXT_DANGER));
        infoActionBottomY = voteEndY + buttonHeight;

        // 刷新按钮移动到房间列表区标题栏右侧
        int refreshWidth = 74;
        int refreshHeight = 18;
        int refreshX = roomListX + roomListWidth - refreshWidth;
        int refreshY = roomListY - 22;
        addRenderableWidget(new TdmRoomActionButton(
                refreshX,
                refreshY,
                refreshWidth,
                refreshHeight,
                Component.translatable("screen.codpattern.common.refresh"),
                btn -> actionController.requestRoomList()));

        // 底部按钮栏
        int buttonWidth = 120;
        int bottomY = this.height - 34;
        int totalButtons = 3;
        int totalWidth = totalButtons * buttonWidth + (totalButtons - 1) * spacing;
        int startX = (this.width - totalWidth) / 2;

        // 加入房间按钮
        joinButton = addRenderableWidget(new TdmRoomActionButton(
                startX,
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.join_room"),
                btn -> actionController.joinSelectedRoom(),
                CodTheme.HOVER_BORDER));

        // 离开房间按钮
        leaveButton = addRenderableWidget(new TdmRoomActionButton(
                startX + (buttonWidth + spacing),
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.leave_room"),
                btn -> actionController.leaveRoom(),
                CodTheme.TEXT_DANGER));

        // 返回按钮
        addRenderableWidget(new TdmRoomActionButton(
                startX + 2 * (buttonWidth + spacing),
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.common.back"),
                btn -> onClose(),
                0xFF9AA5B1));

        // 更新按钮状态
        updateButtonStates();
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        String currentRoomState = actionController.currentRoomState();
        boolean localPlayerReady = TdmRoomStateEvaluator.isLocalPlayerReady(
                Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID(),
                roomState.teamPlayers());
        TdmRoomButtonStateBinder.refresh(
                joinButton,
                leaveButton,
                readyButton,
                voteStartButton,
                voteEndButton,
                kortacButton,
                specgruButton,
                roomState.selectedRoom() != null,
                roomState.joinedRoom() != null,
                actionController.hasPendingAction(),
                currentRoomState,
                actionController.pendingAction(),
                actionController.isLeavePending(),
                actionController.leaveSecondsRemaining(),
                localPlayerReady);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        Minecraft mc = Minecraft.getInstance();

        // 标题
        graphics.drawCenteredString(mc.font, Component.translatable("screen.codpattern.tdm_room.header"),
                this.width / 2, 20, 0xFFFFFF);

        // 渲染房间列表面板
        renderRoomListPanel(graphics, mc, mouseX, mouseY);

        // 渲染右侧信息面板
        renderInfoPanel(graphics, mc);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        actionController.tick();
    }

    /**
     * 渲染房间列表面板
     */
    private void renderRoomListPanel(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        roomNames = TdmRoomListRenderer.render(
                graphics,
                mc,
                roomListX,
                roomListY,
                roomListWidth,
                roomListHeight,
                roomItemHeight,
                roomState.rooms(),
                roomState.selectedRoom(),
                roomState.joinedRoom(),
                mouseX,
                mouseY
        );
    }

    /**
     * 渲染右侧信息面板
     */
    private void renderInfoPanel(GuiGraphics graphics, Minecraft mc) {
        TdmRoomInfoPanelRenderer.render(
                graphics,
                mc,
                this.width,
                this.height,
                roomListX,
                roomListWidth,
                infoActionBottomY,
                roomState.joinedRoom(),
                roomState.selectedRoom(),
                roomState.rooms(),
                roomState.teamPlayers(),
                actionController.isLeavePending(),
                actionController.hasRoomNotice(),
                actionController.roomNoticeText(),
                actionController.roomNoticeColor()
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了房间列表
        if (button == 0 && mouseX >= roomListX && mouseX <= roomListX + roomListWidth
                && mouseY >= roomListY && mouseY < roomListY + roomListHeight) {

            int index = (int) ((mouseY - roomListY) / roomItemHeight);
            if (index >= 0 && index < roomNames.size()) {
                roomState.setSelectedRoom(roomNames.get(index));
                updateButtonStates();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, CodTheme.BG_TOP, CodTheme.BG_BOTTOM);
    }

    // 数据更新方法（由网络包处理器调用）

    /**
     * 更新房间列表
     */
    public void updateRoomList(Map<String, TdmRoomData> rooms) {
        actionController.updateRoomList(rooms);
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

    @Override
    public void onClose() {
        actionController.reset();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
