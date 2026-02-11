package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.refit.TdmRoomActionButton;
import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.cdp.codpattern.network.tdm.JoinRoomPacket;
import com.cdp.codpattern.network.tdm.LeaveRoomPacket;
import com.cdp.codpattern.network.tdm.RequestRoomListPacket;
import com.cdp.codpattern.network.tdm.SetReadyStatePacket;
import com.cdp.codpattern.network.tdm.SelectTeamPacket;
import com.cdp.codpattern.network.tdm.VoteStartPacket;
import com.cdp.codpattern.network.tdm.VoteEndPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TDM 房间选择界面
 * 显示所有可用房间和房间内的玩家信息
 */
public class TdmRoomScreen extends Screen {
    private enum PendingAction {
        NONE,
        JOINING,
        LEAVING
    }

    // 房间列表数据 (将通过网络包更新)
    private Map<String, RoomData> rooms = new HashMap<>();

    // 当前选中的房间
    private String selectedRoom = null;

    // 当前已加入的房间
    private String joinedRoom = null;

    // 当前房间的玩家列表 (将通过网络包更新)
    private Map<String, List<PlayerInfo>> teamPlayers = new HashMap<>();

    // 房间列表区域
    private int roomListX, roomListY, roomListWidth, roomListHeight;
    private int roomItemHeight = 34;
    private List<String> roomNames = new ArrayList<>();
    private static final long LEAVE_CONFIRM_WINDOW_MS = 3000L;
    private static final long ACTION_ACK_TIMEOUT_MS = 5000L;
    private static final long ROOM_NOTICE_DURATION_MS = 4500L;
    private long pendingLeaveDeadlineMs = 0L;
    private long pendingActionDeadlineMs = 0L;
    private PendingAction pendingAction = PendingAction.NONE;
    private String pendingRoomName = null;
    private String roomNoticeText = "";
    private int roomNoticeColor = CodTheme.TEXT_SECONDARY;
    private long roomNoticeExpireAtMs = 0L;

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
        requestRoomList();

        // 添加UI按钮
        addButtons(rightPanelX, rightPanelWidth);
    }

    /**
     * 添加UI按钮
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
                btn -> selectTeam(CodTdmMap.TEAM_KORTAC),
                0xFFE35A5A));

        // SPECGRU 队伍按钮
        specgruButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX + teamButtonWidth + spacing,
                teamButtonY,
                teamButtonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.join_specgru"),
                btn -> selectTeam(CodTdmMap.TEAM_SPECGRU),
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
                btn -> toggleReady(),
                0xFF6CCF8A));

        // 将“发起开始投票/发起结束投票”放到房间信息区
        int voteStartY = readyY + buttonHeight + spacing;
        voteStartButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX,
                voteStartY,
                voteStartWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.vote_start"),
                btn -> voteStart(),
                CodTheme.SELECTED_BORDER));
        int voteEndY = voteStartY + buttonHeight + spacing;
        voteEndButton = addRenderableWidget(new TdmRoomActionButton(
                rightPanelX,
                voteEndY,
                voteStartWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.vote_end"),
                btn -> voteEnd(),
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
                btn -> requestRoomList()));

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
                btn -> joinSelectedRoom(),
                CodTheme.HOVER_BORDER));

        // 离开房间按钮
        leaveButton = addRenderableWidget(new TdmRoomActionButton(
                startX + (buttonWidth + spacing),
                bottomY,
                buttonWidth,
                buttonHeight,
                Component.translatable("screen.codpattern.tdm_room.leave_room"),
                btn -> leaveRoom(),
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
        boolean hasSelectedRoom = selectedRoom != null;
        boolean hasJoinedRoom = joinedRoom != null;
        boolean hasPendingAction = pendingAction != PendingAction.NONE;
        boolean canSwitchTeam = isTeamSwitchAllowed();
        boolean canStartVote = canStartVote();
        boolean canEndVote = canEndVote();

        if (joinButton != null) {
            joinButton.active = hasSelectedRoom && !hasJoinedRoom && !hasPendingAction;
        }
        if (leaveButton != null) {
            leaveButton.active = hasJoinedRoom && !hasPendingAction;
        }
        if (readyButton != null) {
            readyButton.active = hasJoinedRoom && "WAITING".equals(getCurrentRoomState()) && !hasPendingAction;
        }
        if (voteStartButton != null) {
            voteStartButton.active = hasJoinedRoom && canStartVote && !hasPendingAction;
        }
        if (voteEndButton != null) {
            voteEndButton.active = hasJoinedRoom && canEndVote && !hasPendingAction;
        }
        if (kortacButton != null) {
            kortacButton.active = canSwitchTeam && !hasPendingAction;
        }
        if (specgruButton != null) {
            specgruButton.active = canSwitchTeam && !hasPendingAction;
        }
        updateJoinButtonLabel();
        updateLeaveButtonLabel();
        updateReadyButtonLabel();
    }

    private boolean isTeamSwitchAllowed() {
        String state = getCurrentRoomState();
        if (state == null) {
            return false;
        }
        return "WAITING".equals(state);
    }

    private boolean canStartVote() {
        String state = getCurrentRoomState();
        return "WAITING".equals(state);
    }

    private boolean canEndVote() {
        String state = getCurrentRoomState();
        return "WARMUP".equals(state) || "PLAYING".equals(state);
    }

    private String getCurrentRoomState() {
        if (joinedRoom == null) {
            return null;
        }
        RoomData joined = rooms.get(joinedRoom);
        if (joined != null) {
            return joined.state;
        }
        return ClientTdmState.currentPhase;
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
        long now = System.currentTimeMillis();
        if (roomNoticeExpireAtMs > 0L && now >= roomNoticeExpireAtMs) {
            clearRoomNotice();
        }
        if (pendingLeaveDeadlineMs > 0L) {
            if (now >= pendingLeaveDeadlineMs) {
                executeLeaveRoom();
            } else {
                updateLeaveButtonLabel();
            }
        }
        if (pendingAction != PendingAction.NONE && pendingActionDeadlineMs > 0L && now >= pendingActionDeadlineMs) {
            PendingAction expiredAction = pendingAction;
            clearPendingAction();
            showRoomNotice(expiredAction == PendingAction.JOINING ? "加入房间请求超时" : "离开房间请求超时",
                    CodTheme.TEXT_DANGER);
            updateButtonStates();
        }
    }

    /**
     * 渲染房间列表面板
     */
    private void renderRoomListPanel(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        int panelLeft = roomListX - 5;
        int panelTop = roomListY - 25;
        int panelRight = roomListX + roomListWidth + 5;
        int panelBottom = roomListY + roomListHeight + 5;

        // 面板背景与边框
        graphics.fillGradient(panelLeft, panelTop, panelRight, panelBottom, CodTheme.PANEL_BG, 0xCC101010);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, CodTheme.BORDER_SUBTLE);

        // 标题
        graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.available_rooms"), roomListX,
                roomListY - 20, CodTheme.TEXT_PRIMARY);

        if (rooms.isEmpty()) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.no_rooms"), roomListX,
                    roomListY + 10, CodTheme.TEXT_SECONDARY);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.create_hint"), roomListX,
                    roomListY + 25, CodTheme.TEXT_DIM);
            graphics.drawString(mc.font, "§b/fpsm map create cdptdm <名称> <从> <到>", roomListX, roomListY + 40, CodTheme.TEXT_DIM);
            return;
        }

        // 渲染房间列表
        roomNames.clear();
        int y = roomListY;
        for (Map.Entry<String, RoomData> entry : rooms.entrySet()) {
            if (y + roomItemHeight > roomListY + roomListHeight) {
                break;
            }
            String mapName = entry.getKey();
            RoomData room = entry.getValue();
            roomNames.add(mapName);

            // 判断是否悬停
            boolean hovered = mouseX >= roomListX && mouseX <= roomListX + roomListWidth &&
                    mouseY >= y && mouseY < y + roomItemHeight;

            // 判断是否选中
            boolean selected = mapName.equals(selectedRoom);
            boolean joined = mapName.equals(joinedRoom);

            // 背景颜色
            int bgColor;
            int edgeColor = 0;
            if (joined) {
                bgColor = withAlpha(CodTheme.HOVER_BG_BOTTOM, 150);
                edgeColor = CodTheme.HOVER_BORDER;
            } else if (selected) {
                bgColor = withAlpha(CodTheme.CARD_BG_TOP, 180);
                edgeColor = CodTheme.SELECTED_BORDER;
            } else if (hovered) {
                bgColor = withAlpha(CodTheme.HOVER_BG_TOP, 110);
            } else {
                bgColor = withAlpha(CodTheme.CARD_BG_TOP, 70);
            }
            graphics.fill(roomListX, y, roomListX + roomListWidth, y + roomItemHeight - 2, bgColor);
            if (edgeColor != 0) {
                graphics.fill(roomListX, y, roomListX + 2, y + roomItemHeight - 2, edgeColor);
            }

            // 房间信息
            String statusIcon = getStatusIcon(room.state);
            String roomText = String.format("%s %s", statusIcon, mapName);
            if (!room.hasMatchEndTeleportPoint) {
                roomText += " §c!";
            }
            String infoText = String.format("%d/%d", room.playerCount, room.maxPlayers);

            int textColor = joined ? CodTheme.TEXT_HOVER : (selected ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_PRIMARY);
            graphics.drawString(mc.font, roomText, roomListX + 5, y + 4, textColor);
            graphics.drawString(mc.font, infoText, roomListX + roomListWidth - mc.font.width(infoText) - 5, y + 4,
                    CodTheme.TEXT_SECONDARY);

            String statusText = buildRoomListStatusText(room);
            graphics.drawString(mc.font, statusText, roomListX + 5, y + 18, 0xFFAFAFAF);

            y += roomItemHeight;
        }

    }

    /**
     * 获取状态图标
     */
    private String getStatusIcon(String state) {
        return switch (state) {
            case "WAITING" -> "§a●";
            case "COUNTDOWN", "WARMUP" -> "§e●";
            case "PLAYING" -> "§c●";
            case "ENDED" -> "§7●";
            default -> "§f●";
        };
    }

    private String buildRoomListStatusText(RoomData room) {
        String phaseText = Component
                .translatable("screen.codpattern.tdm_room.phase." + room.state.toLowerCase(Locale.ROOT))
                .getString();
        String scoreText = buildTeamScoreText(room);
        if (room.remainingTimeTicks > 0) {
            return String.format("§7[%s %s]  %s", phaseText, formatTime(room.remainingTimeTicks), scoreText);
        }
        return String.format("§7[%s]  %s", phaseText, scoreText);
    }

    private String buildTeamScoreText(RoomData room) {
        int kortacScore = room.teamScores.getOrDefault(CodTdmMap.TEAM_KORTAC, 0);
        int specgruScore = room.teamScores.getOrDefault(CodTdmMap.TEAM_SPECGRU, 0);
        return String.format("§cK:%d §7| §9S:%d", kortacScore, specgruScore);
    }

    private String buildPhaseStatusText(RoomData room) {
        String phaseText = Component
                .translatable("screen.codpattern.tdm_room.phase." + room.state.toLowerCase(Locale.ROOT))
                .getString();
        if (room.remainingTimeTicks > 0) {
            return phaseText + " " + formatTime(room.remainingTimeTicks);
        }
        return phaseText;
    }

    private String formatTime(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private int getLeaveSecondsRemaining() {
        long remainMs = pendingLeaveDeadlineMs - System.currentTimeMillis();
        if (remainMs <= 0) {
            return 0;
        }
        return (int) Math.ceil(remainMs / 1000.0);
    }

    private boolean isLeavePending() {
        return pendingLeaveDeadlineMs > 0L && pendingLeaveDeadlineMs > System.currentTimeMillis();
    }

    private void clearPendingLeave() {
        pendingLeaveDeadlineMs = 0L;
        updateLeaveButtonLabel();
    }

    private void updateJoinButtonLabel() {
        if (joinButton == null) {
            return;
        }
        if (pendingAction == PendingAction.JOINING) {
            joinButton.setMessage(Component.literal("加入中..."));
            return;
        }
        joinButton.setMessage(Component.translatable("screen.codpattern.tdm_room.join_room"));
    }

    private void updateLeaveButtonLabel() {
        if (leaveButton == null) {
            return;
        }
        if (pendingAction == PendingAction.LEAVING) {
            leaveButton.setMessage(Component.literal("离开中..."));
            return;
        }
        if (isLeavePending()) {
            leaveButton.setMessage(Component.translatable("screen.codpattern.tdm_room.leave_room_pending",
                    getLeaveSecondsRemaining()));
            return;
        }
        leaveButton.setMessage(Component.translatable("screen.codpattern.tdm_room.leave_room"));
    }

    private void updateReadyButtonLabel() {
        if (readyButton == null) {
            return;
        }
        boolean ready = isLocalPlayerReady();
        readyButton.setMessage(Component.literal(ready ? "取消准备" : "准备"));
    }

    private boolean isLocalPlayerReady() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        java.util.UUID localPlayer = mc.player.getUUID();
        for (List<PlayerInfo> players : teamPlayers.values()) {
            for (PlayerInfo info : players) {
                if (info.uuid().equals(localPlayer)) {
                    return info.isReady();
                }
            }
        }
        return false;
    }

    /**
     * 渲染右侧信息面板
     */
    private void renderInfoPanel(GuiGraphics graphics, Minecraft mc) {
        // 使用与 init 一致的布局计算
        int padding = 15;
        int headerHeight = 45;
        int panelX = roomListX + roomListWidth + padding * 2;
        int panelY = headerHeight;
        int panelWidth = this.width - panelX - padding;
        int panelHeight = this.height - headerHeight - 50 - padding;

        // 面板背景与边框
        graphics.fillGradient(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5,
                CodTheme.PANEL_BG, 0xCC101010);
        graphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY - 4, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelX - 5, panelY + panelHeight + 4, panelX + panelWidth + 5, panelY + panelHeight + 5,
                CodTheme.BORDER_SUBTLE);
        graphics.fill(panelX - 5, panelY - 5, panelX - 4, panelY + panelHeight + 5, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelX + panelWidth + 4, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5,
                CodTheme.BORDER_SUBTLE);

        // 标题
        if (joinedRoom != null) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.current_room", joinedRoom),
                    panelX, panelY, CodTheme.TEXT_PRIMARY);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.select_team"), panelX,
                    panelY + 15, CodTheme.TEXT_SECONDARY);
        } else if (selectedRoom != null) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room", selectedRoom), panelX, panelY,
                    CodTheme.TEXT_PRIMARY);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.join_hint"), panelX,
                    panelY + 15, CodTheme.TEXT_SECONDARY);
        } else {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.select_hint"), panelX,
                    panelY, CodTheme.TEXT_SECONDARY);
        }

        graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.room_actions"), panelX,
                panelY + 30, CodTheme.TEXT_SECONDARY);
        int actionDividerRight = panelX + Math.max(20, Math.min(190, panelWidth - 8));
        graphics.fill(panelX, panelY + 40, actionDividerRight, panelY + 41, CodTheme.DIVIDER);

        int infoY = Math.max(panelY + 78, infoActionBottomY + 12);
        if (joinedRoom != null) {
            RoomData joined = rooms.get(joinedRoom);
            if (joined != null) {
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_score", buildTeamScoreText(joined)),
                        panelX, infoY, 0xFFE5E5E5);
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_phase", buildPhaseStatusText(joined)),
                        panelX, infoY + 12, 0xFFB0B0B0);
                infoY += 26;
            }
        }

        RoomData selected = null;
        if (joinedRoom != null) {
            selected = rooms.get(joinedRoom);
        } else if (selectedRoom != null) {
            selected = rooms.get(selectedRoom);
        }
        if (selected != null && !selected.hasMatchEndTeleportPoint) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp"),
                    panelX, infoY, CodTheme.TEXT_DANGER);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp_hint"),
                    panelX, infoY + 12, 0xFFAA55);
            infoY += 24;
        }

        // 渲染队伍玩家列表（含 ID / KD / 延迟）
        int rosterTop = Math.max(infoActionBottomY + 18, infoY + 8);
        int rosterBottom = panelY + panelHeight - (isLeavePending() ? 38 : 24);
        if (joinedRoom != null && rosterTop < rosterBottom) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.roster_title"),
                    panelX, rosterTop - 11, CodTheme.TEXT_SECONDARY);
            renderTeamRosters(graphics, mc, panelX, panelWidth, rosterTop, rosterBottom);
        }

        if (isLeavePending()) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.leave_room_cancel_hint"),
                    panelX, panelY + panelHeight - 24, 0xFFFFD75E);
        }
        if (hasRoomNotice()) {
            int noticeY = panelY + panelHeight - (isLeavePending() ? 36 : 24);
            graphics.drawString(mc.font, roomNoticeText, panelX, noticeY, roomNoticeColor);
        }
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private void renderTeamRosters(GuiGraphics graphics, Minecraft mc, int panelX, int panelWidth, int startY,
            int maxY) {
        List<String> teamOrder = new ArrayList<>();
        teamOrder.add(CodTdmMap.TEAM_KORTAC);
        teamOrder.add(CodTdmMap.TEAM_SPECGRU);
        for (String key : teamPlayers.keySet()) {
            if (!teamOrder.contains(key)) {
                teamOrder.add(key);
            }
        }

        int y = startY;
        for (String teamName : teamOrder) {
            List<PlayerInfo> players = teamPlayers.getOrDefault(teamName, List.of());
            y = renderSingleTeamRoster(graphics, mc, panelX, panelWidth, y, maxY, teamName, players);
            if (y > maxY) {
                break;
            }
        }
    }

    private int renderSingleTeamRoster(GuiGraphics graphics, Minecraft mc, int panelX, int panelWidth, int startY, int maxY,
            String teamName, List<PlayerInfo> players) {
        int accent = getTeamAccentColor(teamName);
        int headerHeight = 14;
        if (startY + headerHeight > maxY) {
            return maxY + 1;
        }

        graphics.fill(panelX, startY, panelX + panelWidth, startY + headerHeight, withAlpha(accent, 40));
        graphics.fill(panelX, startY + headerHeight - 1, panelX + panelWidth, startY + headerHeight, withAlpha(accent, 160));

        String teamKey = "screen.codpattern.tdm_room.team." + teamName.toLowerCase(Locale.ROOT);
        String teamLabel = Component.translatable(teamKey).getString();
        if (teamLabel.equals(teamKey)) {
            teamLabel = teamName.toUpperCase(Locale.ROOT);
        }
        String headerText = teamLabel + "  (" + players.size() + ")";
        graphics.drawString(mc.font, headerText, panelX + 5, startY + 3, accent);

        int y = startY + headerHeight + 3;
        if (players.isEmpty()) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.no_players"), panelX + 5, y,
                    CodTheme.TEXT_DIM);
            return y + 14;
        }

        int rowHeight = 24;
        for (PlayerInfo player : players) {
            if (y + rowHeight > maxY) {
                graphics.drawString(mc.font, "...", panelX + panelWidth - 14, Math.max(startY + 2, maxY - 9),
                        CodTheme.TEXT_DIM);
                return maxY + 1;
            }
            renderPlayerStatCard(graphics, mc, panelX, panelWidth, y, rowHeight, player, accent);
            y += rowHeight + 3;
        }
        return y + 4;
    }

    private void renderPlayerStatCard(GuiGraphics graphics, Minecraft mc, int x, int width, int y, int height,
            PlayerInfo player, int teamColor) {
        int cardTop = player.isAlive() ? withAlpha(teamColor, 30) : 0x66331515;
        int cardBottom = player.isAlive() ? withAlpha(0xFF0F1114, 190) : 0x661A1212;
        int lifeColor = player.isAlive() ? 0xFF4DFF8A : 0xFFFF6B6B;
        int cardRight = x + width;

        graphics.fillGradient(x, y, cardRight, y + height, cardTop, cardBottom);
        graphics.fill(x, y, x + 2, y + height, lifeColor);

        String aliveMark = player.isAlive() ? "● " : "✖ ";
        String readyMark = player.isReady() ? "§a[R] " : "§7[ ] ";
        String kdText = formatKd(player.kills(), player.deaths());
        String headline = readyMark + aliveMark + player.name();
        String scoreText = "K/D " + player.kills() + "/" + player.deaths();
        String meta = Component.translatable("screen.codpattern.tdm_room.player_meta",
                shortPlayerId(player.uuid()), kdText, Math.max(0, player.pingMs())).getString();

        graphics.drawString(mc.font, headline, x + 6, y + 3, 0xFFF4F4F4);
        graphics.drawString(mc.font, scoreText, cardRight - mc.font.width(scoreText) - 5, y + 3, 0xFFCCCCCC);
        graphics.drawString(mc.font, meta, x + 6, y + 13, 0xFFB5B5B5);
    }

    private int getTeamAccentColor(String teamName) {
        if (CodTdmMap.TEAM_KORTAC.equalsIgnoreCase(teamName)) {
            return 0xFFE35A5A;
        }
        if (CodTdmMap.TEAM_SPECGRU.equalsIgnoreCase(teamName)) {
            return 0xFF66A6FF;
        }
        return 0xFFB4C1CE;
    }

    private String shortPlayerId(java.util.UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(raw.length(), 8)).toUpperCase(Locale.ROOT);
    }

    private String formatKd(int kills, int deaths) {
        if (kills <= 0 && deaths <= 0) {
            return "0.00";
        }
        if (deaths <= 0) {
            return kills + ".00";
        }
        return String.format(Locale.ROOT, "%.2f", (double) kills / (double) deaths);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了房间列表
        if (button == 0 && mouseX >= roomListX && mouseX <= roomListX + roomListWidth &&
                mouseY >= roomListY && mouseY < roomListY + roomListHeight) {

            int index = (int) ((mouseY - roomListY) / roomItemHeight);
            if (index >= 0 && index < roomNames.size()) {
                selectedRoom = roomNames.get(index);
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

    // ========== 网络交互方法 ==========

    /**
     * 请求房间列表
     */
    private void requestRoomList() {
        PacketHandler.sendToServer(new RequestRoomListPacket());
    }

    private void startPendingAction(PendingAction action, String roomName) {
        pendingAction = action;
        pendingRoomName = roomName;
        pendingActionDeadlineMs = System.currentTimeMillis() + ACTION_ACK_TIMEOUT_MS;
    }

    private void clearPendingAction() {
        pendingAction = PendingAction.NONE;
        pendingRoomName = null;
        pendingActionDeadlineMs = 0L;
    }

    private void showRoomNotice(String message, int color) {
        showRoomNotice(message, color, ROOM_NOTICE_DURATION_MS);
    }

    private void showRoomNotice(String message, int color, long durationMs) {
        if (message == null || message.isBlank()) {
            return;
        }
        roomNoticeText = message;
        roomNoticeColor = color;
        roomNoticeExpireAtMs = System.currentTimeMillis() + Math.max(800L, durationMs);
    }

    private boolean hasRoomNotice() {
        return !roomNoticeText.isBlank();
    }

    private void clearRoomNotice() {
        roomNoticeText = "";
        roomNoticeExpireAtMs = 0L;
    }

    /**
     * 加入选中的房间
     */
    private void joinSelectedRoom() {
        if (selectedRoom == null || selectedRoom.isEmpty()) {
            return;
        }
        if (pendingAction != PendingAction.NONE) {
            return;
        }
        clearPendingLeave();
        clearRoomNotice();
        // 先加入房间（不指定队伍，由后续选择决定）
        PacketHandler.sendToServer(new JoinRoomPacket(selectedRoom, null));
        startPendingAction(PendingAction.JOINING, selectedRoom);
        updateButtonStates();
    }

    /**
     * 选择队伍
     */
    private void selectTeam(String teamName) {
        if (joinedRoom == null)
            return;
        if (!isTeamSwitchAllowed()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
            }
            return;
        }
        PacketHandler.sendToServer(new SelectTeamPacket(teamName));
    }

    /**
     * 离开当前房间
     */
    private void leaveRoom() {
        if (joinedRoom == null) {
            return;
        }
        if (pendingAction != PendingAction.NONE) {
            return;
        }
        if (isLeavePending()) {
            clearPendingLeave();
            return;
        }
        pendingLeaveDeadlineMs = System.currentTimeMillis() + LEAVE_CONFIRM_WINDOW_MS;
        updateLeaveButtonLabel();
    }

    private void executeLeaveRoom() {
        clearPendingLeave();
        PacketHandler.sendToServer(new LeaveRoomPacket());
        startPendingAction(PendingAction.LEAVING, joinedRoom);
        updateButtonStates();
    }

    private void toggleReady() {
        if (joinedRoom == null || pendingAction != PendingAction.NONE) {
            return;
        }
        if (!"WAITING".equals(getCurrentRoomState())) {
            return;
        }
        PacketHandler.sendToServer(new SetReadyStatePacket(!isLocalPlayerReady()));
    }

    /**
     * 投票开始游戏
     */
    private void voteStart() {
        PacketHandler.sendToServer(new VoteStartPacket());
    }

    /**
     * 投票结束游戏
     */
    private void voteEnd() {
        PacketHandler.sendToServer(new VoteEndPacket());
    }

    // ========== 数据更新方法 (由网络包处理器调用) ==========

    /**
     * 更新房间列表
     */
    public void updateRoomList(Map<String, RoomData> rooms) {
        this.rooms = rooms;
        if (joinedRoom != null && !rooms.containsKey(joinedRoom) && pendingAction != PendingAction.JOINING) {
            joinedRoom = null;
            teamPlayers.clear();
        }
        updateButtonStates();
    }

    /**
     * 更新当前房间的玩家列表
     */
    public void updatePlayerList(String mapName, Map<String, List<PlayerInfo>> teamPlayers) {
        this.joinedRoom = mapName;
        this.teamPlayers = teamPlayers;
        if (pendingAction == PendingAction.JOINING && mapName.equals(pendingRoomName)) {
            clearPendingAction();
        }
        updateButtonStates();
    }

    /**
     * 设置已加入的房间
     */
    public void setJoinedRoom(String roomName) {
        this.joinedRoom = roomName;
        this.selectedRoom = roomName;
        clearPendingAction();
        updateButtonStates();
    }

    public void handleJoinResult(boolean success, String mapName, String reasonCode, String reasonMessage) {
        if (pendingAction == PendingAction.JOINING) {
            clearPendingAction();
        }
        if (success) {
            joinedRoom = mapName;
            selectedRoom = mapName;
            clearRoomNotice();
            updateButtonStates();
            return;
        }
        String message = (reasonMessage == null || reasonMessage.isBlank())
                ? "加入房间失败: " + (reasonCode == null ? "UNKNOWN" : reasonCode)
                : "加入房间失败: " + reasonMessage;
        showRoomNotice(message, CodTheme.TEXT_DANGER);
        updateButtonStates();
    }

    public void handleLeaveResult(boolean success, String roomName, String reasonCode, String reasonMessage) {
        if (pendingAction == PendingAction.LEAVING) {
            clearPendingAction();
        }
        if (success) {
            joinedRoom = null;
            teamPlayers.clear();
            ClientTdmState.resetMatchState();
            showRoomNotice("已离开房间", CodTheme.TEXT_SECONDARY);
            if (roomName != null && roomName.equals(selectedRoom)) {
                // 保留选中，便于快速重新加入
                selectedRoom = roomName;
            }
        } else {
            String message = (reasonMessage == null || reasonMessage.isBlank())
                    ? "离开房间失败: " + (reasonCode == null ? "UNKNOWN" : reasonCode)
                    : "离开房间失败: " + reasonMessage;
            showRoomNotice(message, CodTheme.TEXT_DANGER);
        }
        updateButtonStates();
    }

    @Override
    public void onClose() {
        clearPendingLeave();
        clearPendingAction();
        clearRoomNotice();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 内部数据类 ==========

    /**
     * 房间数据
     */
    public static class RoomData {
        public String mapName;
        public String state;
        public int playerCount;
        public int maxPlayers;
        public Map<String, Integer> teamPlayerCounts;
        public Map<String, Integer> teamScores;
        public int remainingTimeTicks;
        public boolean hasMatchEndTeleportPoint;

        public RoomData(String mapName, String state, int playerCount, int maxPlayers,
                Map<String, Integer> teamPlayerCounts, Map<String, Integer> teamScores, int remainingTimeTicks,
                boolean hasMatchEndTeleportPoint) {
            this.mapName = mapName;
            this.state = state;
            this.playerCount = playerCount;
            this.maxPlayers = maxPlayers;
            this.teamPlayerCounts = teamPlayerCounts;
            this.teamScores = teamScores;
            this.remainingTimeTicks = remainingTimeTicks;
            this.hasMatchEndTeleportPoint = hasMatchEndTeleportPoint;
        }
    }
}
