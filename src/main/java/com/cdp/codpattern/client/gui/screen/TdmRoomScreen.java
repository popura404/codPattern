package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.cdp.codpattern.network.tdm.JoinRoomPacket;
import com.cdp.codpattern.network.tdm.LeaveRoomPacket;
import com.cdp.codpattern.network.tdm.RequestRoomListPacket;
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
    private long pendingLeaveDeadlineMs = 0L;

    // 队伍选择按钮
    private Button kortacButton;
    private Button specgruButton;
    private Button joinButton;
    private Button leaveButton;

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
        int buttonWidth = 110;
        int buttonHeight = 20;
        int spacing = 8;

        // 队伍选择按钮 (在右侧面板内)
        int teamButtonY = 90;
        int teamButtonWidth = (rightPanelWidth - spacing) / 2;
        teamButtonWidth = Math.min(teamButtonWidth, 130);

        // KORTAC 队伍按钮
        kortacButton = Button.builder(
                Component.translatable("screen.codpattern.tdm_room.join_kortac"),
                btn -> selectTeam(CodTdmMap.TEAM_KORTAC))
                .bounds(rightPanelX, teamButtonY, teamButtonWidth, buttonHeight).build();
        addRenderableWidget(kortacButton);

        // SPECGRU 队伍按钮
        specgruButton = Button.builder(
                Component.translatable("screen.codpattern.tdm_room.join_specgru"),
                btn -> selectTeam(CodTdmMap.TEAM_SPECGRU))
                .bounds(rightPanelX + teamButtonWidth + spacing, teamButtonY, teamButtonWidth, buttonHeight).build();
        addRenderableWidget(specgruButton);

        // 底部按钮栏
        int bottomY = this.height - 35;
        int totalButtons = 5;
        int totalWidth = totalButtons * buttonWidth + (totalButtons - 1) * spacing;
        int startX = (this.width - totalWidth) / 2;

        // 刷新按钮
        addRenderableWidget(Button.builder(
                Component.translatable("screen.codpattern.common.refresh"),
                btn -> requestRoomList())
                .bounds(startX, bottomY, buttonWidth, buttonHeight).build());

        // 加入房间按钮
        joinButton = Button.builder(
                Component.translatable("screen.codpattern.tdm_room.join_room"),
                btn -> joinSelectedRoom())
                .bounds(startX + (buttonWidth + spacing), bottomY, buttonWidth, buttonHeight).build();
        addRenderableWidget(joinButton);

        // 离开房间按钮
        leaveButton = Button.builder(
                Component.translatable("screen.codpattern.tdm_room.leave_room"),
                btn -> leaveRoom())
                .bounds(startX + 2 * (buttonWidth + spacing), bottomY, buttonWidth, buttonHeight).build();
        addRenderableWidget(leaveButton);

        // 投票开始按钮
        addRenderableWidget(Button.builder(
                Component.translatable("screen.codpattern.tdm_room.vote_start"),
                btn -> voteStart())
                .bounds(startX + 3 * (buttonWidth + spacing), bottomY, buttonWidth, buttonHeight).build());

        // 返回按钮
        addRenderableWidget(Button.builder(
                Component.translatable("screen.codpattern.common.back"),
                btn -> onClose())
                .bounds(startX + 4 * (buttonWidth + spacing), bottomY, buttonWidth, buttonHeight).build());

        // 更新按钮状态
        updateButtonStates();
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        boolean hasSelectedRoom = selectedRoom != null;
        boolean hasJoinedRoom = joinedRoom != null;

        joinButton.active = hasSelectedRoom && !hasJoinedRoom;
        leaveButton.active = hasJoinedRoom;
        kortacButton.active = hasJoinedRoom;
        specgruButton.active = hasJoinedRoom;
        updateLeaveButtonLabel();
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
        if (pendingLeaveDeadlineMs <= 0L) {
            return;
        }
        if (System.currentTimeMillis() >= pendingLeaveDeadlineMs) {
            executeLeaveRoom();
            return;
        }
        updateLeaveButtonLabel();
    }

    /**
     * 渲染房间列表面板
     */
    private void renderRoomListPanel(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        // 面板背景
        graphics.fill(roomListX - 5, roomListY - 25, roomListX + roomListWidth + 5,
                roomListY + roomListHeight + 5, 0x80000000);

        // 标题
        graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.available_rooms"), roomListX,
                roomListY - 20, 0xFFFFFF);

        if (rooms.isEmpty()) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.no_rooms"), roomListX,
                    roomListY + 10, 0xAAAAAA);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.create_hint"), roomListX,
                    roomListY + 25, 0x888888);
            graphics.drawString(mc.font, "§b/fpsm map create cdptdm <名称> <从> <到>", roomListX, roomListY + 40, 0x888888);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.command_scope_hint"), roomListX,
                    roomListY + 55, 0xE0AA00);
            return;
        }

        // 渲染房间列表
        roomNames.clear();
        int y = roomListY;
        for (Map.Entry<String, RoomData> entry : rooms.entrySet()) {
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
            if (joined) {
                bgColor = 0x6000FF00; // 绿色 - 已加入
            } else if (selected) {
                bgColor = 0x60FFFF00; // 黄色 - 选中
            } else if (hovered) {
                bgColor = 0x40FFFFFF; // 白色 - 悬停
            } else {
                bgColor = 0x20FFFFFF; // 透明
            }
            graphics.fill(roomListX, y, roomListX + roomListWidth, y + roomItemHeight - 2, bgColor);

            // 房间信息
            String statusIcon = getStatusIcon(room.state);
            String roomText = String.format("%s %s", statusIcon, mapName);
            if (!room.hasMatchEndTeleportPoint) {
                roomText += " §c!";
            }
            String infoText = String.format("%d/%d", room.playerCount, room.maxPlayers);

            int textColor = joined ? 0x00FF00 : (selected ? 0xFFFF00 : 0xFFFFFF);
            graphics.drawString(mc.font, roomText, roomListX + 5, y + 4, textColor);
            graphics.drawString(mc.font, infoText, roomListX + roomListWidth - mc.font.width(infoText) - 5, y + 4,
                    0xAAAAAA);

            String statusText = buildRoomListStatusText(room);
            graphics.drawString(mc.font, statusText, roomListX + 5, y + 18, 0xAFAFAF);

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

    private void updateLeaveButtonLabel() {
        if (leaveButton == null) {
            return;
        }
        if (isLeavePending()) {
            leaveButton.setMessage(Component.translatable("screen.codpattern.tdm_room.leave_room_pending",
                    getLeaveSecondsRemaining()));
            return;
        }
        leaveButton.setMessage(Component.translatable("screen.codpattern.tdm_room.leave_room"));
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

        // 面板背景
        graphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5, 0x80000000);

        // 标题
        if (joinedRoom != null) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.current_room", joinedRoom),
                    panelX, panelY, 0xFFFFFF);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.select_team"), panelX,
                    panelY + 15, 0xAAAAAA);
        } else if (selectedRoom != null) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room", selectedRoom), panelX, panelY,
                    0xFFFFFF);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.join_hint"), panelX,
                    panelY + 15, 0xAAAAAA);
        } else {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.select_hint"), panelX,
                    panelY, 0xAAAAAA);
        }

        int infoY = panelY + 30;
        if (joinedRoom != null) {
            RoomData joined = rooms.get(joinedRoom);
            if (joined != null) {
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_score", buildTeamScoreText(joined)),
                        panelX, infoY, 0xE5E5E5);
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_phase", buildPhaseStatusText(joined)),
                        panelX, infoY + 12, 0xB0B0B0);
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
                    panelX, infoY, 0xFF5555);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp_hint"),
                    panelX, infoY + 12, 0xFFAA55);
            infoY += 24;
        }

        // 渲染队伍玩家列表 (在按钮下方)
        if (joinedRoom != null && !teamPlayers.isEmpty()) {
            int yOffset = Math.max(panelY + 80, infoY + 8); // 留出按钮空间

            for (Map.Entry<String, List<PlayerInfo>> entry : teamPlayers.entrySet()) {
                String teamName = entry.getKey();
                List<PlayerInfo> players = entry.getValue();

                // 队伍标题颜色
                String teamColor = teamName.equals(CodTdmMap.TEAM_KORTAC) ? "§c" : "§9";
                graphics.drawString(mc.font, teamColor + teamName.toUpperCase() + " §7(" + players.size() + ")",
                        panelX, yOffset, 0xFFFFFF);
                yOffset += 15;

                // 玩家列表
                if (players.isEmpty()) {
                    graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.no_players"),
                            panelX, yOffset, 0x888888);
                    yOffset += 12;
                } else {
                    for (PlayerInfo player : players) {
                        String status = player.isAlive() ? "§a" : "§c";
                        String playerText = String.format("  %s%s §7K:%d D:%d",
                                status, player.name(), player.kills(), player.deaths());
                        graphics.drawString(mc.font, playerText, panelX, yOffset, 0xFFFFFF);
                        yOffset += 12;
                    }
                }
                yOffset += 10;
            }
        }

        if (isLeavePending()) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.leave_room_cancel_hint"),
                    panelX, panelY + panelHeight - 24, 0xFFD75E);
        }
        graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.command_scope_hint"),
                panelX, panelY + panelHeight - 12, 0xE0AA00);
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

    /**
     * 加入选中的房间
     */
    private void joinSelectedRoom() {
        if (selectedRoom == null || selectedRoom.isEmpty()) {
            return;
        }
        clearPendingLeave();
        // 先加入房间（不指定队伍，由后续选择决定）
        PacketHandler.sendToServer(new JoinRoomPacket(selectedRoom, null));
        joinedRoom = selectedRoom;
        updateButtonStates();
    }

    /**
     * 选择队伍
     */
    private void selectTeam(String teamName) {
        if (joinedRoom == null)
            return;
        PacketHandler.sendToServer(new SelectTeamPacket(teamName));
    }

    /**
     * 离开当前房间
     */
    private void leaveRoom() {
        if (joinedRoom == null) {
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
        joinedRoom = null;
        teamPlayers.clear();
        ClientTdmState.resetMatchState();
        updateButtonStates();
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
    }

    /**
     * 更新当前房间的玩家列表
     */
    public void updatePlayerList(String mapName, Map<String, List<PlayerInfo>> teamPlayers) {
        this.joinedRoom = mapName;
        this.teamPlayers = teamPlayers;
        clearPendingLeave();
        updateButtonStates();
    }

    /**
     * 设置已加入的房间
     */
    public void setJoinedRoom(String roomName) {
        this.joinedRoom = roomName;
        this.selectedRoom = roomName;
        clearPendingLeave();
        updateButtonStates();
    }

    @Override
    public void onClose() {
        clearPendingLeave();
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
