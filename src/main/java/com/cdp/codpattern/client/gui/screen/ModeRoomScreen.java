package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.client.ClientMatchState;
import com.cdp.codpattern.client.gui.BackNavigation;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.refit.ModeRoomActionButton;
import com.cdp.codpattern.client.gui.screen.match.LobbyBackButton;
import com.cdp.codpattern.client.gui.screen.match.LobbyCanvas;
import com.cdp.codpattern.client.gui.screen.match.ModeLobbyCaption;
import com.cdp.codpattern.client.gui.screen.match.ModePreviewPanel;
import com.cdp.codpattern.client.network.ModeRoomClientPackets;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomActionController;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomButtonStateBinder;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomData;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomInfoPanelRenderer;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomListRenderer;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomSessionState;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomStateEvaluator;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomUiState;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModeRoomScreen extends Screen {
    private static final String LEGACY_KORTAC_TEAM = "kortac";
    private static final String LEGACY_SPECGRU_TEAM = "specgru";

    private static final int BASE_ROOM_HEADER_HEIGHT = 38;
    private static final int BACKGROUND_SCRIM = 0x50000000;
    private static final long ENTER_ANIMATION_MS = 180L;
    private static final long ROOM_LIST_APPLY_DEBOUNCE_MS = 60L;
    private static final long INFO_CONTENT_FADE_MS = 85L;

    private final ModeRoomSessionState roomState = new ModeRoomSessionState();
    private final ModeRoomUiState uiState = new ModeRoomUiState();
    private final ModeRoomActionController actionController;
    private final String modeFilterGameType;
    private final Screen previousScreen;
    private final ModeRoomListRenderer roomListRenderer = new ModeRoomListRenderer();
    private final ModeRoomInfoPanelRenderer infoPanel = new ModeRoomInfoPanelRenderer();
    private ModeDescriptor backdropMode;
    private ModeLobbyCaption emptyCaption;
    private Component header;
    private Component roomCount = Component.empty();
    private int headerY;
    private boolean compact;
    private boolean detailsOpen;

    // 房间列表区域
    private int roomListX;
    private int roomListY;
    private int roomListWidth;
    private int roomListHeight;
    private int roomListScrollOffset = 0;
    private int roomListMaxScrollOffset = 0;
    private ModeRoomListRenderer.RenderResult roomListRenderResult = ModeRoomListRenderer.RenderResult.empty();

    // 右侧信息面板区域
    private int rightPanelX;
    private int rightPanelY;
    private int rightPanelWidth;
    private int rightPanelHeight;
    private int infoScrollOffset;
    private int infoMaxScrollOffset;

    // 视觉状态
    private long openedAtMs = 0L;
    private final Map<String, Long> roomEnteredAtMs = new HashMap<>();
    private Map<String, ModeRoomData> pendingRoomListUpdate = null;
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
    private ModeRoomActionButton membershipButton;
    private Button listFocus;
    private Button cardLeaveButton;
    private List<Button> teamActions = List.of();
    private List<Button> matchActions = List.of();
    private int actionTopY;

    public ModeRoomScreen() {
        this(null, null);
    }

    public ModeRoomScreen(String modeFilterGameType) {
        this(modeFilterGameType, null);
    }

    public ModeRoomScreen(String modeFilterGameType, Screen previousScreen) {
        super(Component.translatable("screen.codpattern.room.title"));
        this.modeFilterGameType = normalizeModeFilter(modeFilterGameType);
        this.previousScreen = previousScreen;
        this.actionController = new ModeRoomActionController(roomState, uiState, this::updateButtonStates);
    }

    @Override
    protected void init() {
        super.init();

        compact = width < scaled(460);
        roomListX = Math.max(scaled(6), Math.round(width * 0.035f));
        headerY = Math.max(scaled(4), Math.round(height * 0.035f));
        roomListY = headerY + GuiTextHelper.referenceLineHeight(font, 1.5f) + scaled(7);
        ScreenRectangle backBounds = BackNavigation.bounds(
                GuiTextHelper.referenceViewportSize(minecraft.getWindow().getWidth()),
                GuiTextHelper.referenceViewportSize(minecraft.getWindow().getHeight()));
        int contentBottom = LobbyCanvas.toGui(backBounds.top()) - scaled(4);
        int contentWidth = width - 2 * roomListX;
        roomListHeight = Math.max(1, contentBottom - roomListY);
        roomListWidth = compact ? contentWidth : clamp(Math.round(width * 0.30f), scaled(164), scaled(300));
        rightPanelX = compact ? roomListX : roomListX + roomListWidth + scaled(14);
        rightPanelWidth = width - roomListX - rightPanelX;
        rightPanelY = roomListY;
        rightPanelHeight = compact ? roomListHeight : ModeRoomListRenderer.listViewportHeight(roomListHeight);

        backdropMode = modeFilterGameType == null ? null : GameModeRegistry.find(modeFilterGameType).orElse(null);
        header = Component.translatable(resolveRoomHeaderKey());
        emptyCaption = null;
        if (!compact && backdropMode != null) {
            int captionWidth = (int) LobbyCanvas.toReference(rightPanelWidth);
            emptyCaption = ModeLobbyCaption.layout(font, backdropMode, captionWidth, captionWidth,
                    (int) LobbyCanvas.toReference(rightPanelHeight));
        }
        roomListRenderResult = ModeRoomListRenderer.RenderResult.empty();
        openedAtMs = System.currentTimeMillis();
        restoreJoinedRoomFromClientStateIfNeeded();
        actionController.requestRoomList();
        addButtons();
        addRenderableWidget(new LobbyBackButton(backBounds, btn -> onClose()));
        if (listFocus.visible && minecraft.getLastInputType().isKeyboard()) {
            setFocused(listFocus);
        }
    }

    /**
     * 添加 UI 按钮
     */
    private void addButtons() {
        listFocus = listControl(roomListX, roomListY, roomListWidth, roomListHeight, header, btn -> openSelectedRoom());
        cardLeaveButton = listControl(0, 0, 0, 0, Component.translatable("screen.codpattern.room.action.leave"),
                btn -> actionController.leaveRoom());
        cardLeaveButton.visible = false;
        kortacButton = actionButton("join_kortac", btn -> actionController.selectTeam(LEGACY_KORTAC_TEAM), 0xFFE88279);
        specgruButton = actionButton("join_specgru", btn -> actionController.selectTeam(LEGACY_SPECGRU_TEAM), 0xFF91BFF0);
        readyButton = actionButton("ready", btn -> actionController.toggleReady(), 0xFF98DDB7);
        voteStartButton = actionButton("vote_start", btn -> actionController.voteStart(), 0xFFEAC489);
        voteEndButton = actionButton("vote_end", btn -> actionController.voteEnd(), 0xFFE88279);
        membershipButton = actionButton("action.join", btn -> activateMembership(), 0xFFCEDDDB);
        membershipButton.setPrimaryStyle(true);
        teamActions = List.of(kortacButton, specgruButton);
        matchActions = List.of(readyButton, voteStartButton, voteEndButton, membershipButton);
        updateButtonStates();
    }

    private Button listControl(int x, int y, int width, int height, Component label, Button.OnPress press) {
        return addRenderableWidget(new Button(x, y, width, height, label, press, narration -> narration.get()) {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return false; // Only the visible room rows accept pointer input.
            }

            @Override
            public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (isFocused() && minecraft.getLastInputType().isKeyboard()) {
                    graphics.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xBBFFFFFF);
                }
            }
        });
    }

    private ModeRoomActionButton actionButton(String key, Button.OnPress press, int accent) {
        int height = scaled(rightPanelHeight < scaled(175) ? 14 : 20);
        return addRenderableWidget(new ModeRoomActionButton(0, 0, 1, height,
                Component.translatable("screen.codpattern.room." + key), press, accent));
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        if (membershipButton == null) {
            return;
        }
        String currentRoomState = actionController.currentRoomState();
        boolean hasJoinedRoom = roomState.joinedRoom() != null;
        String target = displayedRoomKey();
        boolean viewingJoined = hasJoinedRoom && roomState.joinedRoom().equals(target);
        boolean showDetails = !compact || detailsOpen;
        boolean showCurrentRoomControls = showDetails && viewingJoined;
        boolean hasTeamSelection = currentJoinedRoomHasCapability(ModeCapability.TEAM_SELECTION);
        boolean hasReadyState = currentJoinedRoomHasCapability(ModeCapability.READY_STATE);
        boolean hasStartVote = currentJoinedRoomHasCapability(ModeCapability.START_VOTE);
        boolean hasEndVote = currentJoinedRoomHasCapability(ModeCapability.END_VOTE);
        boolean localPlayerReady = ModeRoomStateEvaluator.isLocalPlayerReady(
                Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID(),
                roomState.teamPlayers());
        ModeRoomButtonStateBinder.refresh(
                readyButton,
                voteStartButton,
                voteEndButton,
                kortacButton,
                specgruButton,
                hasJoinedRoom,
                actionController.hasPendingAction(),
                currentRoomState,
                localPlayerReady,
                hasTeamSelection,
                hasReadyState,
                hasStartVote,
                hasEndVote);
        kortacButton.visible = specgruButton.visible = showCurrentRoomControls && hasTeamSelection;
        readyButton.visible = showCurrentRoomControls && hasReadyState;
        voteStartButton.visible = showCurrentRoomControls && hasStartVote;
        voteEndButton.visible = showCurrentRoomControls && hasEndVote;
        membershipButton.visible = target != null && !viewingJoined && (compact ? detailsOpen : hasJoinedRoom);
        membershipButton.active = membershipButton.visible && !actionController.hasPendingAction();
        boolean cancel = target != null && target.equals(actionController.pendingSwitchTargetRoom());
        String actionKey = hasJoinedRoom ? "screen.codpattern.room.action.switch" : "screen.codpattern.room.action.join";
        membershipButton.setMessage(Component.translatable(cancel ? "gui.cancel" : actionKey));
        ModeRoomData selected = roomState.rooms().get(target);
        int modeAccent = selected == null ? 0xFFCEDDDB : ModePreviewPanel.accentColor(selected.gameType);
        membershipButton.setAccentColor(cancel ? 0xFFFFC68C
                : FastColor.ARGB32.lerp(0.25f, modeAccent | 0xFF000000, 0xFFFFFFFF));
        listFocus.visible = !compact || !detailsOpen;
        listFocus.active = !roomState.rooms().isEmpty();
        listFocus.setMessage(selected == null ? header : Component.literal(selected.mapName));
        if (!listFocus.visible || !hasJoinedRoom) cardLeaveButton.visible = false;
        cardLeaveButton.active = !actionController.hasPendingAction();
        cardLeaveButton.setMessage(Component.translatable(actionController.isLeavePending()
                ? "gui.cancel" : "screen.codpattern.room.action.leave"));
        if (getFocused() instanceof Button focused && (!focused.visible || !focused.active)) {
            if (listFocus.visible && listFocus.active) setFocused(listFocus);
            else focusDetailAction();
        }
        layoutActions();
    }

    private String displayedRoomKey() {
        String selected = roomState.selectedRoom();
        return selected != null && roomState.rooms().containsKey(selected) ? selected : roomState.joinedRoom();
    }

    private void activateMembership() {
        String target = displayedRoomKey();
        if (target == null || actionController.hasPendingAction()) {
            return;
        }
        if (roomState.joinedRoom() != null) {
            actionController.switchToRoom(target);
        } else {
            actionController.joinSelectedRoom();
        }
    }

    private void layoutActions() {
        int bottom = rightPanelY + rightPanelHeight;
        boolean showTeams = kortacButton.visible;
        List<Button> lower = matchActions.stream().filter(button -> button.visible).toList();
        if (!showTeams && lower.isEmpty()) {
            actionTopY = bottom;
            return;
        }
        int padding = scaled(8);
        int gap = scaled(3);
        int width = rightPanelWidth - padding * 2;
        int widestLabel = lower.stream().mapToInt(button -> GuiTextHelper.referenceWidth(font, button.getMessage()))
                .max().orElse(0);
        int maxColumns = clamp((width + gap) / (widestLabel + scaled(8) + gap), 1, 3);
        int rows = (showTeams ? 1 : 0) + (lower.size() + maxColumns - 1) / maxColumns;
        int height = membershipButton.getHeight();
        actionTopY = bottom - padding - rows * (height + gap) + gap;
        int y = actionTopY;
        if (showTeams) {
            placeActionRow(teamActions, rightPanelX + padding, y, width, gap);
            y += height + gap;
        }
        for (int i = 0; i < lower.size(); i += maxColumns) {
            placeActionRow(lower.subList(i, Math.min(i + maxColumns, lower.size())), rightPanelX + padding, y, width, gap);
            y += height + gap;
        }
    }

    private static void placeActionRow(List<Button> buttons, int x, int y, int width, int gap) {
        for (int col = 0; col < buttons.size(); col++) {
            int left = x + col * (width + gap) / buttons.size();
            int right = x + (col + 1) * (width + gap) / buttons.size() - gap;
            Button button = buttons.get(col);
            button.setX(left);
            button.setY(y);
            button.setWidth(right - left);
        }
    }

    private void focusDetailAction() {
        for (Button team : teamActions) {
            if (team.visible && team.active) {
                setFocused(team);
                return;
            }
        }
        setFocused(matchActions.stream().filter(button -> button.visible && button.active).findFirst().orElse(null));
    }

    private boolean currentJoinedRoomHasCapability(ModeCapability capability) {
        String joinedRoom = roomState.joinedRoom();
        if (joinedRoom == null || joinedRoom.isBlank()) {
            return false;
        }
        ModeRoomData roomData = roomState.rooms().get(joinedRoom);
        if (roomData != null) {
            return roomData.hasCapability(capability);
        }
        try {
            return GameModeRegistry.hasCapability(RoomId.decode(joinedRoom).gameType(), capability);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        Minecraft mc = Minecraft.getInstance();
        float enterProgress = enterProgress();
        int titleColor = withAlpha(0xFFFFFFFF, Math.max(85, (int) (255.0f * enterProgress)));

        int countWidth = GuiTextHelper.referenceWidth(font, roomCount);
        GuiTextHelper.drawReferenceScaledEllipsizedString(graphics, font, header, roomListX, headerY,
                roomListWidth - countWidth - scaled(6), 1.5f, titleColor, true);
        if (countWidth > 0) {
            GuiTextHelper.drawReferenceString(graphics, font, roomCount, roomListX + roomListWidth - countWidth,
                    headerY + scaled(4), withAlpha(0xFFD5DFDB, (int) (255 * enterProgress)), true);
        }

        if (listFocus.visible) {
            renderRoomListPanel(graphics, mc, mouseX, mouseY, enterProgress);
        }
        refreshInfoContextTransition(false);
        if (!compact || detailsOpen) {
            if (displayedRoomKey() != null || actionController.hasRoomNotice() || actionController.hasConfirmPending()
                    || emptyCaption == null) {
                renderInfoPanel(graphics, mc, enterProgress);
            } else {
                LobbyCanvas.begin(graphics);
                emptyCaption.draw(graphics, (int) LobbyCanvas.toReference(rightPanelX + rightPanelWidth),
                        (int) LobbyCanvas.toReference(rightPanelY + rightPanelHeight));
                graphics.pose().popPose();
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        actionController.tick();
        restoreJoinedRoomFromClientStateIfNeeded();
        refreshJoinedRoomLiveState();
        flushPendingRoomListUpdate(false);
        sanitizeRoomStateForModeFilter();
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
        roomListRenderResult = roomListRenderer.render(
                graphics,
                mc,
                roomListX,
                roomListY,
                roomListWidth,
                roomListHeight,
                scaled(BASE_ROOM_HEADER_HEIGHT),
                roomState.lobbySummaryState(),
                modeFilterGameType,
                roomState.selectedRoom(),
                roomState.joinedRoom(),
                roomListScrollOffset,
                roomEnteredAtMs,
                System.currentTimeMillis(),
                mouseX,
                mouseY,
                actionController.hasPendingAction(),
                actionController.isLeavePending(),
                actionController.pendingSwitchTargetRoom(),
                enterProgress);
        roomListScrollOffset = roomListRenderResult.scrollOffset();
        roomListMaxScrollOffset = roomListRenderResult.maxScrollOffset();
        // The renderer paints the card action. Expose that same clipped rectangle to keyboard navigation.
        cardLeaveButton.visible = false;
        for (ModeRoomListRenderer.ActionHitbox hit : roomListRenderResult.actionHitboxes()) {
            if (hit.type() == ModeRoomListRenderer.ActionType.LEAVE) {
                cardLeaveButton.setX(hit.x());
                cardLeaveButton.setY(hit.y());
                cardLeaveButton.setWidth(hit.width());
                cardLeaveButton.setHeight(hit.height());
                cardLeaveButton.visible = true;
                break;
            }
        }
        if (getFocused() == cardLeaveButton && !cardLeaveButton.visible) setFocused(listFocus);
    }

    /**
     * 渲染右侧信息面板
     */
    private void renderInfoPanel(GuiGraphics graphics, Minecraft mc, float enterProgress) {
        infoMaxScrollOffset = infoPanel.render(
                graphics,
                mc,
                rightPanelX,
                rightPanelY,
                rightPanelWidth,
                rightPanelHeight,
                actionTopY,
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
                infoContentFadeProgress(),
                infoScrollOffset);
        infoScrollOffset = clamp(infoScrollOffset, 0, infoMaxScrollOffset);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && listFocus.visible) {
            ModeRoomListRenderer.ActionHitbox actionHitbox = roomListRenderResult.actionAt(mouseX, mouseY);
            if (actionHitbox != null) {
                handleRoomActionClick(actionHitbox);
                return true;
            }
            String roomName = roomListRenderResult.roomAt(mouseX, mouseY);
            if (roomName != null) {
                selectRoom(roomName);
                if (compact) {
                    openSelectedRoom();
                } else {
                    setFocused(listFocus);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleRoomActionClick(ModeRoomListRenderer.ActionHitbox actionHitbox) {
        if (!actionHitbox.enabled()) {
            return;
        }
        if (actionHitbox.type() != ModeRoomListRenderer.ActionType.LEAVE || compact) {
            selectRoom(actionHitbox.roomName());
        }
        switch (actionHitbox.type()) {
            case JOIN -> actionController.joinSelectedRoom();
            case LEAVE -> actionController.leaveRoom();
            case SWITCH -> actionController.switchToRoom(actionHitbox.roomName());
        }
        if (compact && actionHitbox.type() != ModeRoomListRenderer.ActionType.LEAVE) {
            openSelectedRoom();
        }
    }

    private void selectRoom(String roomKey) {
        if (!roomKey.equals(roomState.selectedRoom())) {
            actionController.selectRoom(roomKey);
            refreshInfoContextTransition(false);
        }
    }

    private void openSelectedRoom() {
        if (displayedRoomKey() == null && !roomListRenderResult.roomNames().isEmpty()) {
            selectRoom(roomListRenderResult.roomNames().get(0));
        }
        if (displayedRoomKey() == null) {
            return;
        }
        if (!compact && roomState.joinedRoom() == null) {
            actionController.joinSelectedRoom();
            return;
        }
        if (!compact && displayedRoomKey().equals(roomState.joinedRoom())) {
            actionController.leaveRoom();
            return;
        }
        detailsOpen = compact;
        updateButtonStates();
        focusDetailAction();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (listFocus.visible && (getFocused() == listFocus || getFocused() == null)
                && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            List<String> names = roomListRenderResult.roomNames();
            if (!names.isEmpty()) {
                int index = names.indexOf(roomState.selectedRoom());
                String roomKey = names.get(clamp(index + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, names.size() - 1));
                setFocused(listFocus);
                selectRoom(roomKey);
                roomListScrollOffset = roomListRenderer.scrollToRoom(roomKey, roomListScrollOffset, roomListHeight);
                return true;
            }
        }
        if ((!compact || detailsOpen) && (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP)) {
            int step = Math.max(scaled(20), actionTopY - rightPanelY - scaled(40));
            infoScrollOffset = clamp(infoScrollOffset + (key == GLFW.GLFW_KEY_PAGE_DOWN ? step : -step),
                    0, infoMaxScrollOffset);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) Math.copySign(Math.ceil(Math.abs(delta) * scaled(24)), delta);
        if (listFocus.visible && mouseX >= roomListX && mouseX < roomListX + roomListWidth
                && mouseY >= roomListY && mouseY < roomListY + roomListHeight) {
            roomListScrollOffset = clamp(roomListScrollOffset - step, 0, roomListMaxScrollOffset);
            roomListRenderResult = ModeRoomListRenderer.RenderResult.empty();
            return true;
        }
        if ((!compact || detailsOpen) && mouseX >= rightPanelX && mouseX < rightPanelX + rightPanelWidth
                && mouseY >= rightPanelY && mouseY < actionTopY) {
            infoScrollOffset = clamp(infoScrollOffset - step, 0, infoMaxScrollOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics) {
        ModePreviewPanel.renderFullscreenBase(graphics, width, height, 1f);
        ModeDescriptor mode = backdropMode;
        if (modeFilterGameType == null) {
            ModeRoomData room = roomState.rooms().get(displayedRoomKey());
            if (room != null) {
                mode = GameModeRegistry.find(room.gameType).orElse(null);
            }
        }
        ModePreviewPanel.renderFullscreenModeLayer(graphics, width, height, mode, 1f);
        ModePreviewPanel.renderLobbyShade(graphics, width, height);
        graphics.fill(0, 0, width, height, BACKGROUND_SCRIM);
    }

    /**
     * 更新房间列表
     */
    public void updateRoomList(long snapshotVersion, Map<String, ModeRoomData> rooms) {
        pendingRoomListUpdate = rooms == null ? new HashMap<>() : new HashMap<>(rooms);
        pendingRoomListSnapshotVersion = Math.max(0L, snapshotVersion);
        pendingRoomListReceivedAtMs = System.currentTimeMillis();
        if (roomState.rooms().isEmpty()) {
            flushPendingRoomListUpdate(true);
        }
    }

    private void applyRoomListUpdate(Map<String, ModeRoomData> rooms) {
        Set<String> previous = new HashSet<>(roomState.rooms().keySet());
        actionController.updateRoomList(pendingRoomListSnapshotVersion, filterRoomsByMode(rooms));
        sanitizeRoomStateForModeFilter();
        roomCount = Component.translatable("screen.codpattern.room.room_count", roomState.rooms().size());
        if (displayedRoomKey() == null) {
            if (!compact && !roomState.rooms().isEmpty()) {
                selectRoom(ModeRoomListRenderer.orderedRoomNames(roomState.rooms(), null).get(0));
            } else if (!actionController.hasRoomNotice()) {
                detailsOpen = false;
            }
        }
        updateButtonStates();

        long now = System.currentTimeMillis();
        for (String roomName : roomState.rooms().keySet()) {
            if (!previous.contains(roomName)) {
                roomEnteredAtMs.put(roomName, now);
            }
        }
        roomEnteredAtMs.keySet().retainAll(roomState.rooms().keySet());

        roomListRenderResult = ModeRoomListRenderer.RenderResult.empty();
    }

    /**
     * 更新当前房间的玩家列表
     */
    public void updatePlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        if (!matchesModeFilter(roomKey, roomState.rooms().get(roomKey))) {
            sanitizeRoomStateForModeFilter();
            return;
        }
        actionController.updatePlayerList(roomKey, rosterVersion, teamPlayers);
        sanitizeRoomStateForModeFilter();
    }

    public void updatePlayerDelta(String roomKey, int rosterVersion) {
        if (!matchesModeFilter(roomKey, roomState.rooms().get(roomKey))) {
            sanitizeRoomStateForModeFilter();
            return;
        }
        actionController.updatePlayerDelta(roomKey, rosterVersion);
        sanitizeRoomStateForModeFilter();
    }

    public void updatePreviewPlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        if (!matchesModeFilter(roomKey, roomState.rooms().get(roomKey))) {
            return;
        }
        actionController.updatePreviewPlayerList(roomKey, rosterVersion, teamPlayers);
    }

    /**
     * 设置已加入的房间
     */
    public void setJoinedRoom(String roomKey) {
        actionController.setJoinedRoom(roomKey);
        sanitizeRoomStateForModeFilter();
    }

    public void handleJoinResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        actionController.handleJoinResult(success, roomKey, reasonCode, reasonMessage);
        sanitizeRoomStateForModeFilter();
    }

    public void handleLeaveResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        actionController.handleLeaveResult(success, roomKey, reasonCode, reasonMessage);
        sanitizeRoomStateForModeFilter();
    }

    @Override
    public void onClose() {
        if (compact && detailsOpen) {
            detailsOpen = false;
            updateButtonStates();
            setFocused(listFocus);
            return;
        }
        pendingRoomListUpdate = null;
        pendingRoomListSnapshotVersion = 0L;
        roomListRenderResult = ModeRoomListRenderer.RenderResult.empty();
        actionController.unsubscribeRoomList();
        actionController.reset();
        if (this.minecraft != null && previousScreen != null) {
            this.minecraft.setScreen(previousScreen);
            return;
        }
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
        Map<String, ModeRoomData> toApply = pendingRoomListUpdate;
        pendingRoomListUpdate = null;
        pendingRoomListReceivedAtMs = 0L;
        applyRoomListUpdate(toApply);
    }

    private void refreshInfoContextTransition(boolean force) {
        String currentKey = currentInfoContextKey();
        if (force || !currentKey.equals(infoContextKey)) {
            infoContextKey = currentKey;
            infoContentTransitionAtMs = System.currentTimeMillis();
            infoScrollOffset = 0;
            infoMaxScrollOffset = 0;
        }
    }

    private void restoreJoinedRoomFromClientStateIfNeeded() {
        if (roomState.joinedRoom() != null && !roomState.joinedRoom().isBlank()) {
            sanitizeRoomStateForModeFilter();
            return;
        }
        String restoredJoinedRoom = ClientMatchState.roomContextName();
        if (restoredJoinedRoom == null || restoredJoinedRoom.isBlank()
                || !matchesModeFilter(restoredJoinedRoom, roomState.rooms().get(restoredJoinedRoom))) {
            return;
        }
        roomState.setJoinedRoom(restoredJoinedRoom);
        if ((roomState.selectedRoom() == null || roomState.selectedRoom().isBlank())
                && restoredJoinedRoom != null
                && !restoredJoinedRoom.isBlank()) {
            roomState.setSelectedRoom(restoredJoinedRoom);
        }
        refreshJoinedRoomLiveState();
        ModeRoomClientPackets.requestRoomRosterResync();
        refreshInfoContextTransition(true);
    }

    private void refreshJoinedRoomLiveState() {
        String joinedRoom = roomState.joinedRoom();
        if (joinedRoom == null || joinedRoom.isBlank()) {
            roomState.joinedRoomLiveState().clear();
            return;
        }
        String clientRoomContext = ClientMatchState.roomContextName();
        if (clientRoomContext != null
                && !clientRoomContext.isBlank()
                && !joinedRoom.equals(clientRoomContext)) {
            if (!matchesModeFilter(clientRoomContext, roomState.rooms().get(clientRoomContext))) {
                clearJoinedRoomForFilterMismatch(joinedRoom);
                return;
            }
            roomState.setJoinedRoom(clientRoomContext);
            joinedRoom = clientRoomContext;
        }
        if (!matchesModeFilter(joinedRoom, roomState.rooms().get(joinedRoom))) {
            clearJoinedRoomForFilterMismatch(joinedRoom);
            return;
        }
        roomState.joinedRoomLiveState().setRoomKey(joinedRoom);
        roomState.joinedRoomLiveState().refreshFromClientState();
    }

    private void sanitizeRoomStateForModeFilter() {
        if (modeFilterGameType == null || modeFilterGameType.isBlank()) {
            return;
        }
        String joinedRoom = roomState.joinedRoom();
        if (joinedRoom != null
                && !joinedRoom.isBlank()
                && !matchesModeFilter(joinedRoom, roomState.rooms().get(joinedRoom))) {
            clearJoinedRoomForFilterMismatch(joinedRoom);
        }
        String selectedRoom = roomState.selectedRoom();
        if (selectedRoom != null
                && !selectedRoom.isBlank()
                && !matchesModeFilter(selectedRoom, roomState.rooms().get(selectedRoom))) {
            roomState.setSelectedRoom(null);
        }
        if ((roomState.selectedRoom() == null || roomState.selectedRoom().isBlank())
                && roomState.joinedRoom() != null
                && !roomState.joinedRoom().isBlank()) {
            roomState.setSelectedRoom(roomState.joinedRoom());
        }
    }

    private void clearJoinedRoomForFilterMismatch(String joinedRoom) {
        roomState.setJoinedRoom(null);
        roomState.joinedRoomLiveState().clear();
        if (joinedRoom != null && joinedRoom.equals(roomState.selectedRoom())) {
            roomState.setSelectedRoom(null);
        }
    }

    private Map<String, ModeRoomData> filterRoomsByMode(Map<String, ModeRoomData> rooms) {
        if (modeFilterGameType == null || modeFilterGameType.isBlank() || rooms == null || rooms.isEmpty()) {
            return rooms == null ? new HashMap<>() : new HashMap<>(rooms);
        }
        Map<String, ModeRoomData> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ModeRoomData> entry : rooms.entrySet()) {
            if (matchesModeFilter(entry.getKey(), entry.getValue())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private boolean matchesModeFilter(String roomKey, ModeRoomData roomData) {
        if (modeFilterGameType == null || modeFilterGameType.isBlank()) {
            return true;
        }
        String roomGameType = roomData == null ? null : roomData.gameType;
        if (roomGameType == null || roomGameType.isBlank()) {
            try {
                roomGameType = RoomId.decode(roomKey).gameType();
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return modeFilterGameType.equals(GameModeRegistry.canonicalize(roomGameType));
    }

    private String resolveRoomHeaderKey() {
        if (modeFilterGameType == null || modeFilterGameType.isBlank()) {
            return "screen.codpattern.room.header";
        }
        return GameModeRegistry.getOrDefault(modeFilterGameType).roomHeaderKey();
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

    private float enterProgress() {
        if (openedAtMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float raw = Math.min(1.0f, Math.max(0.0f, elapsed / (float) ENTER_ANIMATION_MS));
        return 0.2f + (raw * 0.8f);
    }

    private static String normalizeModeFilter(String modeFilterGameType) {
        if (modeFilterGameType == null || modeFilterGameType.isBlank()) {
            return null;
        }
        String canonical = GameModeRegistry.canonicalize(modeFilterGameType);
        return canonical.isBlank() ? null : canonical;
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

}
