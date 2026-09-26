package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.network.map.MapAdminData;
import com.cdp.codpattern.network.map.MapAdminRequestPacket;
import com.cdp.codpattern.network.map.MapAdminResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Administrator view of registered maps. All reads and mutations go through the server. */
public final class MapManagementScreen extends Screen {
    private static final String KEY = "screen.codpattern.map_admin.";
    private static final int MARGIN = 10;
    private static final int PANEL_TOP = 57;
    private static final int ROW_HEIGHT = 31;
    private static final int INFO_LINE_HEIGHT = 15;

    private static java.lang.ref.WeakReference<MapManagementScreen> current = new java.lang.ref.WeakReference<>(null);
    private Screen confirmation;
    private final Screen previous;
    private final UUID session = UUID.randomUUID();
    private final List<MapAdminData.ModeRow> modes = new ArrayList<>();
    private final List<MapAdminData.MapRow> maps = new ArrayList<>();
    private String filter = "";
    private RoomId selected;
    private MapAdminData.DetailRow detail;
    private String draftName = "";
    private String statusKey = "loading";
    private String actionResult = "";
    private int partialErrors;
    private int listScroll;
    private int detailScroll;
    private int expectedOffset;
    private int listFingerprint;
    private long nextRequestId;
    private long pendingList = -1;
    private long pendingDetail = -1;
    private long pendingAction = -1;
    private long pendingStatus = -1;
    private long pendingStartedAt;
    private long readStartedAt;
    private long statusStartedAt;
    private int pendingOnline;
    private int pendingOffline;
    private int pendingEntities;
    private int pollTicks;
    private UUID pendingEndGeneration;
    private boolean initialized;
    private boolean loadingList;
    private boolean loadingDetail;
    private boolean preserveDraft;

    private EditBox nameField;
    private Button modeButton;
    private Button refreshButton;
    private Button saveButton;
    private Button deleteButton;
    private Button endButton;
    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int panelBottom;

    public MapManagementScreen(Screen previous) {
        super(Component.translatable(KEY + "title"));
        this.previous = previous;
        current = new java.lang.ref.WeakReference<>(this);
    }

    public static void receive(MapAdminResponsePacket response) {
        MapManagementScreen owner = current.get();
        Screen visible = Minecraft.getInstance().screen;
        if (owner != null && (visible == owner || (owner.confirmation != null && visible == owner.confirmation)))
            owner.accept(response);
    }

    @Override
    protected void init() {
        leftX = MARGIN;
        leftWidth = Math.max(108, Math.min(215, (width - MARGIN * 3) / 3));
        rightX = leftX + leftWidth + MARGIN;
        rightWidth = Math.max(100, width - rightX - MARGIN);
        panelBottom = Math.max(PANEL_TOP + 95, height - MARGIN);

        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> guardDraft(this::cycleMode))
                .bounds(leftX, 29, Math.min(165, leftWidth), 20).build());
        refreshButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "refresh"),
                button -> guardDraft(this::requestList))
                .bounds(rightX, 29, Math.min(78, rightWidth / 2), 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "back"), button -> onClose())
                .bounds(width - MARGIN - 61, 5, 61, 20).build());

        nameField = new EditBox(font, rightX + 8, PANEL_TOP + 28,
                Math.max(80, rightWidth - 16), 18, Component.translatable(KEY + "name"));
        nameField.setMaxLength(100);
        nameField.setValue(draftName);
        nameField.setResponder(value -> {
            draftName = value;
            updateButtons();
        });
        addRenderableWidget(nameField);

        int gap = 4;
        int actionWidth = Math.max(31, (rightWidth - 16 - gap * 2) / 3);
        int actionY = panelBottom - 25;
        saveButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "save"),
                button -> rename()).bounds(rightX + 8, actionY, actionWidth, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "delete"),
                button -> confirmDelete()).bounds(rightX + 8 + actionWidth + gap, actionY, actionWidth, 20).build());
        endButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "force_end"),
                button -> confirmForceEnd()).bounds(rightX + 8 + (actionWidth + gap) * 2,
                actionY, actionWidth, 20).build());
        updateFilterButton();
        updateButtons();
        if (!initialized) {
            initialized = true;
            requestList();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (nameField != null) nameField.tick();
        if (pendingAction >= 0 && System.currentTimeMillis() - pendingStartedAt > 15_000L) {
            pendingAction = -1;
            statusKey = "request_timeout";
            actionResult = "";
            updateButtons();
        }
        if ((loadingList || loadingDetail) && System.currentTimeMillis() - readStartedAt > 15_000L) {
            pendingList = pendingDetail = -1;
            loadingList = loadingDetail = false;
            detail = null;
            statusKey = "request_timeout";
            actionResult = "";
            updateButtons();
        }
        if (pendingStatus >= 0 && System.currentTimeMillis() - statusStartedAt > 15_000L) {
            pendingStatus = -1;
            pendingEndGeneration = null;
            statusKey = "request_timeout";
        }
        if (pendingEndGeneration != null && selected != null && pendingStatus < 0 && pendingAction < 0) {
            if (++pollTicks >= 60) {
                pollTicks = 0;
                pendingStatus = nextRequest();
                statusStartedAt = System.currentTimeMillis();
                send(MapAdminRequestPacket.endStatus(session, pendingStatus, selected, pendingEndGeneration));
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, CodTheme.BG_BOTTOM);
        graphics.drawString(font, title, MARGIN, 9, CodTheme.TEXT_PRIMARY, false);
        graphics.fill(leftX, PANEL_TOP, leftX + leftWidth, panelBottom, CodTheme.PANEL_BG);
        graphics.fill(rightX, PANEL_TOP, rightX + rightWidth, panelBottom, CodTheme.PANEL_BG);
        graphics.fill(leftX, PANEL_TOP, leftX + leftWidth, PANEL_TOP + 1, CodTheme.HOVER_BORDER_SEMI);
        graphics.fill(rightX, PANEL_TOP, rightX + rightWidth, PANEL_TOP + 1, CodTheme.HOVER_BORDER_SEMI);

        Component banner = actionResult.isEmpty()
                ? Component.translatable(KEY + statusKey)
                : Component.translatable(KEY + "result." + actionResult.toLowerCase(Locale.ROOT));
        graphics.drawString(font, font.plainSubstrByWidth(banner.getString(), Math.max(70, width - 24)),
                MARGIN, 47, statusKey.equals("denied") ? CodTheme.TEXT_DANGER : CodTheme.TEXT_SECONDARY, false);
        drawList(graphics, mouseX, mouseY);
        drawDetails(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTooltips(graphics, mouseX, mouseY);
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = PANEL_TOP + 8;
        int bottom = panelBottom - 7;
        List<MapAdminData.MapRow> visible = filteredMaps();
        if (loadingList && maps.isEmpty()) {
            graphics.drawString(font, Component.translatable(KEY + "loading"), leftX + 7, top,
                    CodTheme.TEXT_SECONDARY, false);
            return;
        }
        if (visible.isEmpty()) {
            graphics.drawString(font, Component.translatable(KEY + "empty"), leftX + 7, top,
                    CodTheme.TEXT_SECONDARY, false);
            return;
        }
        int rows = Math.max(1, (bottom - top) / ROW_HEIGHT);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, visible.size() - rows)));
        graphics.enableScissor(leftX + 2, top, leftX + leftWidth - 2, bottom);
        for (int i = listScroll; i < Math.min(visible.size(), listScroll + rows); i++) {
            MapAdminData.MapRow row = visible.get(i);
            int y = top + (i - listScroll) * ROW_HEIGHT;
            boolean chosen = row.roomId().equals(selected);
            boolean hovered = mouseX >= leftX + 4 && mouseX < leftX + leftWidth - 4
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (chosen || hovered) {
                graphics.fill(leftX + 3, y, leftX + leftWidth - 3, y + ROW_HEIGHT - 2,
                        chosen ? 0x80384824 : 0x50404540);
            }
            String name = row.roomId().mapName();
            graphics.drawString(font, font.plainSubstrByWidth(name, leftWidth - 14), leftX + 7, y + 3,
                    chosen ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_PRIMARY, false);
            String secondary = modeName(row.modeNameKey(), row.roomId().gameType()) + " · " +
                    Component.translatable(KEY + "status." + row.status()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(secondary, leftWidth - 14), leftX + 7, y + 16,
                    CodTheme.TEXT_SECONDARY, false);
        }
        graphics.disableScissor();
    }

    private void drawDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int textX = rightX + 8;
        graphics.drawString(font, Component.translatable(KEY + "name"), textX, PANEL_TOP + 9,
                CodTheme.TEXT_SECONDARY, false);
        int contentTop = PANEL_TOP + 53;
        int contentBottom = panelBottom - 31;
        graphics.enableScissor(rightX + 4, contentTop, rightX + rightWidth - 4, contentBottom);
        if (selected == null) {
            graphics.drawString(font, Component.translatable(KEY + "select"), textX, contentTop + 3,
                    CodTheme.TEXT_SECONDARY, false);
        } else if (loadingDetail || detail == null || !detail.summary().roomId().equals(selected)) {
            graphics.drawString(font, Component.translatable(KEY + "loading_detail"), textX, contentTop + 3,
                    CodTheme.TEXT_SECONDARY, false);
        } else {
            List<Component> lines = detailLines();
            int maxScroll = Math.max(0, lines.size() - Math.max(1, (contentBottom - contentTop) / INFO_LINE_HEIGHT));
            detailScroll = Math.max(0, Math.min(detailScroll, maxScroll));
            for (int i = detailScroll; i < lines.size(); i++) {
                int y = contentTop + (i - detailScroll) * INFO_LINE_HEIGHT;
                if (y >= contentBottom) break;
                graphics.drawString(font, font.plainSubstrByWidth(lines.get(i).getString(), rightWidth - 16),
                        textX, y + 2, CodTheme.TEXT_PRIMARY, false);
            }
        }
        graphics.disableScissor();
    }

    private List<Component> detailLines() {
        List<Component> lines = new ArrayList<>();
        MapAdminData.MapRow row = detail.summary();
        lines.add(Component.translatable(KEY + "mode", modeName(row.modeNameKey(), row.roomId().gameType())));
        lines.add(Component.translatable(KEY + "dimension", detail.dimensionId()));
        lines.add(Component.translatable(KEY + "lifecycle", Component.translatable(detail.lifecycleStateKey())));
        lines.add(Component.translatable(KEY + "status", Component.translatable(KEY + "status." + row.status())));
        lines.add(Component.translatable(KEY + "first_corner", pos(detail.pos1())));
        lines.add(Component.translatable(KEY + "second_corner", pos(detail.pos2())));
        lines.add(Component.translatable(KEY + "size", detail.sizeX(), detail.sizeY(), detail.sizeZ()));
        if (!detail.endPointSupported()) {
            lines.add(Component.translatable(KEY + "end_point_unsupported"));
        } else if (detail.endPoint().isEmpty()) {
            lines.add(Component.translatable(KEY + "end_point_unset"));
        } else {
            var point = detail.endPoint().get();
            lines.add(Component.translatable(KEY + "end_point", pos(point.position())));
            lines.add(Component.translatable(KEY + "end_dimension", point.dimensionId()));
            lines.add(Component.translatable(KEY + "end_orientation", point.yaw(), point.pitch()));
        }
        if (pendingEndGeneration != null) {
            lines.add(Component.translatable(KEY + "pending_counts", pendingOnline, pendingOffline, pendingEntities));
        }
        if (partialErrors > 0) lines.add(Component.translatable(KEY + "partial", partialErrors));
        return lines;
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentTop = PANEL_TOP + 53;
        if (detail != null && !loadingDetail && mouseX >= rightX + 4 && mouseX < rightX + rightWidth - 4
                && mouseY >= contentTop && mouseY < panelBottom - 31) {
            var lines = detailLines();
            int index = detailScroll + (mouseY - contentTop) / INFO_LINE_HEIGHT;
            if (index >= 0 && index < lines.size() && font.width(lines.get(index)) > rightWidth - 16)
                graphics.renderTooltip(font, font.split(lines.get(index), Math.max(100, width - 24)), mouseX, mouseY);
        }
        int top = PANEL_TOP + 8;
        if (mouseX >= leftX && mouseX < leftX + leftWidth && mouseY >= top && mouseY < panelBottom - 7) {
            int rowIndex = listScroll + (mouseY - top) / ROW_HEIGHT;
            List<MapAdminData.MapRow> rows = filteredMaps();
            int visibleRows = Math.max(1, (panelBottom - 7 - top) / ROW_HEIGHT);
            if (rowIndex >= listScroll && rowIndex < listScroll + visibleRows && rowIndex < rows.size()) {
                var row = rows.get(rowIndex);
                if (font.width(row.roomId().mapName()) > leftWidth - 14) {
                    graphics.renderTooltip(font, Component.literal(row.roomId().mapName()), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = PANEL_TOP + 8;
        if (button == 0 && mouseX >= leftX && mouseX < leftX + leftWidth
                && mouseY >= top && mouseY < panelBottom - 7) {
            if (pendingAction >= 0 || loadingList) return true;
            int index = listScroll + (int) (mouseY - top) / ROW_HEIGHT;
            List<MapAdminData.MapRow> rows = filteredMaps();
            int visibleRows = Math.max(1, (panelBottom - 7 - top) / ROW_HEIGHT);
            if (index >= listScroll && index < listScroll + visibleRows && index < rows.size()) {
                RoomId target = rows.get(index).roomId();
                if (!target.equals(selected)) guardDraft(() -> select(target));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftX && mouseX < leftX + leftWidth && mouseY >= PANEL_TOP) {
            listScroll = Math.max(0, listScroll - (delta > 0 ? 1 : -1));
            return true;
        }
        if (mouseX >= rightX && mouseX < rightX + rightWidth && mouseY >= PANEL_TOP) {
            detailScroll = Math.max(0, detailScroll - (delta > 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        guardDraft(() -> Minecraft.getInstance().setScreen(previous));
    }

    public void accept(MapAdminResponsePacket response) {
        if (!session.equals(response.session())) return;
        switch (response.operation()) {
            case LIST -> acceptList(response);
            case DETAIL -> acceptDetail(response);
            case RENAME, DELETE, FORCE_END -> acceptAction(response);
            case END_STATUS -> acceptEndStatus(response);
        }
    }

    private void acceptList(MapAdminResponsePacket response) {
        if (pendingList != response.requestId()) return;
        pendingList = -1;
        if (response.code() != MapAdminResponsePacket.Code.OK
                && response.code() != MapAdminResponsePacket.Code.PARTIAL) {
            loadingList = false;
            statusKey = errorKey(response);
            updateButtons();
            return;
        }
        if (response.offset() != expectedOffset
                || (expectedOffset > 0 && response.fingerprint() != listFingerprint)) {
            loadingList = false;
            statusKey = "list_changed";
            updateButtons();
            return;
        }
        if (expectedOffset == 0) {
            maps.clear();
            modes.clear();
            modes.addAll(response.modes());
            listFingerprint = response.fingerprint();
            updateFilterButton();
        }
        maps.addAll(response.maps());
        partialErrors = response.errorCount();
        expectedOffset = maps.size();
        if (expectedOffset < response.total()) {
            pendingList = nextRequest();
            send(MapAdminRequestPacket.list(session, pendingList, expectedOffset, listFingerprint));
            return;
        }
        loadingList = false;
        statusKey = partialErrors > 0 ? "partial_banner" : "ready";
        if (selected != null && maps.stream().noneMatch(row -> row.roomId().equals(selected))) {
            selected = null;
            detail = null;
            draftName = "";
        }
        if (selected == null || filteredMaps().stream().noneMatch(row -> row.roomId().equals(selected))) {
            List<MapAdminData.MapRow> visible = filteredMaps();
            if (!visible.isEmpty()) select(visible.get(0).roomId());
            else { selected = null; detail = null; draftName = ""; nameField.setValue(""); updateButtons(); }
        } else {
            requestDetail();
        }
    }

    private void acceptDetail(MapAdminResponsePacket response) {
        if (pendingDetail != response.requestId()) return;
        pendingDetail = -1;
        loadingDetail = false;
        if (response.code() != MapAdminResponsePacket.Code.OK || response.detail() == null
                || selected == null || !response.detail().summary().roomId().equals(selected)) {
            detail = null;
            statusKey = errorKey(response);
            updateButtons();
            return;
        }
        detail = response.detail();
        if (!preserveDraft) draftName = selected.mapName();
        preserveDraft = false;
        nameField.setValue(draftName);
        detailScroll = 0;
        updateButtons();
    }

    private void acceptAction(MapAdminResponsePacket response) {
        if (pendingAction != response.requestId()) return;
        pendingAction = -1;
        actionResult = response.result().matches("[A-Z_]+") ? response.result() : errorKey(response);
        if (response.operation() == MapAdminRequestPacket.Operation.RENAME
                && response.code() == MapAdminResponsePacket.Code.OK) {
            if (response.newRoom() != null) selected = response.newRoom();
            draftName = selected == null ? "" : selected.mapName();
            nameField.setValue(draftName);
            detail = null;
            preserveDraft = false;
            requestList();
        } else if (response.operation() == MapAdminRequestPacket.Operation.DELETE
                && response.code() == MapAdminResponsePacket.Code.OK) {
            selected = null;
            detail = null;
            draftName = "";
            requestList();
        } else if (response.operation() == MapAdminRequestPacket.Operation.FORCE_END) {
            applyEndResult(response);
            requestList();
        } else {
            if (response.code() == MapAdminResponsePacket.Code.STALE) {
                preserveDraft = true;
                requestDetail();
            }
        }
        updateButtons();
    }

    private void acceptEndStatus(MapAdminResponsePacket response) {
        if (pendingStatus != response.requestId()) return;
        pendingStatus = -1;
        if (response.code() == MapAdminResponsePacket.Code.STALE) {
            pendingEndGeneration = null;
            actionResult = "STALE";
            requestList();
        } else if (response.code() == MapAdminResponsePacket.Code.OK) {
            applyEndResult(response);
            if (pendingEndGeneration == null) requestList();
        } else {
            statusKey = errorKey(response);
        }
    }

    private void applyEndResult(MapAdminResponsePacket response) {
        pendingOnline = response.onlinePending();
        pendingOffline = response.offlinePending();
        pendingEntities = response.entitiesPending();
        actionResult = response.result().matches("[A-Z_]+") ? response.result() : errorKey(response);
        if (!"PENDING".equals(response.result()) && !"IN_PROGRESS".equals(response.result())) {
            pendingEndGeneration = null;
        }
        updateButtons();
    }

    private void requestList() {
        loadingList = true;
        readStartedAt = System.currentTimeMillis();
        updateButtons();
        statusKey = "loading";
        expectedOffset = 0;
        listFingerprint = 0;
        pendingList = nextRequest();
        send(MapAdminRequestPacket.list(session, pendingList, 0, 0));
    }

    private void select(RoomId room) {
        selected = room;
        detail = null;
        draftName = room.mapName();
        pendingEndGeneration = null;
        pendingStatus = -1;
        detailScroll = 0;
        actionResult = "";
        nameField.setValue(draftName);
        requestDetail();
    }

    private void requestDetail() {
        if (selected == null) return;
        loadingDetail = true;
        preserveDraft |= dirtyName();
        readStartedAt = System.currentTimeMillis();
        updateButtons();
        pendingDetail = nextRequest();
        send(MapAdminRequestPacket.detail(session, pendingDetail, selected));
    }

    private void rename() {
        if (detail == null || !dirtyName() || pendingAction >= 0 || loadingList || loadingDetail || !detail.summary().canRename()) return;
        pendingAction = nextRequest();
        pendingStartedAt = System.currentTimeMillis();
        send(MapAdminRequestPacket.rename(session, pendingAction, selected, detail.revision(), draftName));
        updateButtons();
    }

    private void confirmDelete() {
        if (detail == null || pendingAction >= 0 || !detail.summary().canDelete()) return;
        RoomId target = selected;
        String revision = detail.revision();
        confirm(Component.translatable(KEY + "confirm_delete_title"),
                Component.translatable(KEY + "confirm_delete", target.mapName(), modeName(detail.summary().modeNameKey(), target.gameType())),
                () -> {
                    pendingAction = nextRequest();
                    pendingStartedAt = System.currentTimeMillis();
                    send(MapAdminRequestPacket.delete(session, pendingAction, target, revision));
                    updateButtons();
                });
    }

    private void confirmForceEnd() {
        if (detail == null || pendingAction >= 0) return;
        RoomId target = selected;
        UUID generation = pendingEndGeneration == null ? detail.generation() : pendingEndGeneration;
        confirm(Component.translatable(KEY + "confirm_end_title"),
                Component.translatable(KEY + "confirm_end", target.mapName(), modeName(detail.summary().modeNameKey(), target.gameType())),
                () -> {
                    pendingEndGeneration = generation;
                    pendingStatus = -1;
                    pendingAction = nextRequest();
                    pendingStartedAt = System.currentTimeMillis();
                    send(MapAdminRequestPacket.forceEnd(session, pendingAction, target, generation));
                    updateButtons();
                });
    }

    private void cycleMode() {
        int current = -1;
        for (int i = 0; i < modes.size(); i++) if (modes.get(i).id().equals(filter)) current = i;
        int next = current + 1;
        filter = next >= modes.size() ? "" : modes.get(next).id();
        listScroll = 0;
        updateFilterButton();
        List<MapAdminData.MapRow> visible = filteredMaps();
        if (selected == null || visible.stream().noneMatch(row -> row.roomId().equals(selected))) {
            if (visible.isEmpty()) {
                selected = null;
                detail = null;
                draftName = "";
                nameField.setValue("");
                updateButtons();
            } else select(visible.get(0).roomId());
        }
    }

    private void guardDraft(Runnable action) {
        if (!dirtyName()) { action.run(); return; }
        confirm(Component.translatable(KEY + "discard_title"),
                Component.translatable(KEY + "discard_message"), () -> {
                    draftName = selected == null ? "" : selected.mapName();
                    if (nameField != null) nameField.setValue(draftName);
                    action.run();
                });
    }

    private void confirm(Component heading, Component message, Runnable action) {
        confirmation = new ConfirmScreen(approved -> {
            Minecraft.getInstance().setScreen(this);
            confirmation = null;
            if (approved) action.run();
        }, heading, message, Component.translatable(KEY + "confirm"),
                Component.translatable(KEY + "cancel"));
        Minecraft.getInstance().setScreen(confirmation);
    }

    private void updateFilterButton() {
        if (modeButton == null) return;
        String name = filter.isEmpty() ? Component.translatable(KEY + "all_modes").getString()
                : modes.stream().filter(mode -> mode.id().equals(filter)).findFirst()
                .map(mode -> modeName(mode.displayNameKey(), mode.id())).orElse(filter);
        modeButton.setMessage(Component.literal(font.plainSubstrByWidth(name, modeButton.getWidth() - 12)));
        modeButton.setTooltip(Tooltip.create(Component.literal(name)));
    }

    private void updateButtons() {
        if (nameField == null || saveButton == null) return;
        boolean ready = detail != null && selected != null && detail.summary().roomId().equals(selected)
                && pendingAction < 0 && !loadingList && !loadingDetail;
        nameField.setEditable(ready && detail.summary().canRename());
        saveButton.active = ready && detail.summary().canRename() && dirtyName();
        deleteButton.active = ready && detail.summary().canDelete();
        endButton.active = ready && !detail.summary().disabledReason().equals("management_pending");
        endButton.setTooltip(detail != null && detail.summary().disabledReason().equals("management_pending")
                ? Tooltip.create(Component.translatable(KEY + "disabled.management_pending")) : null);
        modeButton.active = pendingAction < 0 && !loadingList;
        refreshButton.active = pendingAction < 0 && !loadingList;
        endButton.setMessage(Component.translatable(KEY + (pendingEndGeneration == null ? "force_end" : "retry_end")));
        String disabled = detail == null ? "loading_detail" : detail.summary().disabledReason();
        saveButton.setTooltip(null);
        deleteButton.setTooltip(null);
        if (!disabled.isEmpty()) {
            Component tip = Component.translatable(KEY + "disabled." + disabled);
            saveButton.setTooltip(Tooltip.create(tip));
            deleteButton.setTooltip(Tooltip.create(tip));
        }
    }

    private boolean dirtyName() {
        return selected != null && !draftName.equals(selected.mapName());
    }

    private List<MapAdminData.MapRow> filteredMaps() {
        return filter.isEmpty() ? maps : maps.stream()
                .filter(row -> filter.equals(row.roomId().gameType())).toList();
    }

    private long nextRequest() {
        return ++nextRequestId;
    }

    private void send(MapAdminRequestPacket packet) {
        ModNetworkChannel.sendToServer(packet);
    }

    private static String pos(BlockPos point) {
        return point.getX() + ", " + point.getY() + ", " + point.getZ();
    }

    private static String modeName(String key, String fallback) {
        if (key == null || key.isBlank()) return fallback;
        String rendered = Component.translatable(key).getString();
        return rendered.equals(key) ? fallback : rendered;
    }

    private static String errorKey(MapAdminResponsePacket response) {
        return switch (response.code()) {
            case DENIED -> "denied";
            case MISSING -> "missing";
            case STALE -> "stale";
            case RATE_LIMIT -> "rate_limited";
            default -> "error";
        };
    }
}
