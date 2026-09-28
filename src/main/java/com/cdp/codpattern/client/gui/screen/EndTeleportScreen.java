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
import java.util.Objects;
import java.util.UUID;

/** Shared form for a map's end point and the save-local creation default. */
public final class EndTeleportScreen extends Screen {
    private static final String KEY = "screen.codpattern.end_teleport.";
    private static java.lang.ref.WeakReference<EndTeleportScreen> current = new java.lang.ref.WeakReference<>(null);
    private enum Intent { LOAD, CURRENT, FILL_GLOBAL, SAVE }
    private final MapManagementScreen previous;
    private final RoomId room;
    private final UUID session = UUID.randomUUID();
    private final EndTeleportDraft draft = new EndTeleportDraft();
    private MapAdminData.TeleportSettings settings;
    private Screen confirmation;
    private EditBox x, y, z;
    private Button currentButton, globalButton, saveButton, refreshButton;
    private long nextId, pending = -1, started;
    private MapAdminRequestPacket.Operation expectedOperation;
    private Intent intent;
    private boolean initialized, updating;
    private String status = "loading";
    private int left, contentWidth, fieldsY;

    public EndTeleportScreen(MapManagementScreen previous, RoomId room) {
        super(Component.translatable(KEY + (room == null ? "global_title" : "map_title"), room == null ? "" : room.mapName()));
        this.previous = previous;
        this.room = room;
        current = new java.lang.ref.WeakReference<>(this);
    }

    public static void receive(MapAdminResponsePacket response) {
        var owner = current.get();
        var visible = Minecraft.getInstance().screen;
        if (owner != null && (visible == owner || (owner.confirmation != null && visible == owner.confirmation))) owner.accept(response);
    }

    @Override protected void init() {
        contentWidth = Math.min(500, width - 24);
        left = (width - contentWidth) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.codpattern.map_admin.back"), b -> onClose())
                .bounds(width - 70, 5, 60, 20).build());
        if (room != null) {
            addRenderableWidget(Button.builder(text("basic_tab"), b -> onClose()).bounds(left, 32, 92, 20).build());
            var tab = addRenderableWidget(Button.builder(text("map_tab"), b -> {}).bounds(left + 96, 32, 92, 20).build());
            tab.active = false;
        }
        refreshButton = addRenderableWidget(Button.builder(text("refresh"), b -> guard(this::load))
                .bounds(left + contentWidth - 76, 32, 76, 20).build());
        int noteLines = font.split(text(room == null ? "global_note" : "map_note"), contentWidth).size();
        fieldsY = 60 + noteLines * 10 + 17;
        int gap = 8;
        int fieldWidth = (contentWidth - gap * 2) / 3;
        x = field("X", left, fieldWidth, draft.x());
        y = field("Y", left + fieldWidth + gap, fieldWidth, draft.y());
        z = field("Z", left + 2 * (fieldWidth + gap), fieldWidth, draft.z());
        int buttonsY = fieldsY + 57;
        int buttonWidth = room == null ? contentWidth : (contentWidth - 8) / 2;
        currentButton = addRenderableWidget(Button.builder(text("current"), b -> request(Intent.CURRENT,
                        MapAdminRequestPacket.Operation.CURRENT_POSITION, null))
                .bounds(left, buttonsY, buttonWidth, 20).build());
        if (room != null) globalButton = addRenderableWidget(Button.builder(text("global"), b -> request(Intent.FILL_GLOBAL,
                        MapAdminRequestPacket.Operation.DEFAULTS, null))
                .bounds(left + buttonWidth + 8, buttonsY, buttonWidth, 20).build());
        saveButton = addRenderableWidget(Button.builder(text("save"), b -> save())
                .bounds(left + contentWidth - 90, height - 29, 90, 20).build());
        updateButtons();
        if (!initialized) { initialized = true; load(); }
    }

    private EditBox field(String label, int fieldX, int fieldWidth, String value) {
        var field = new EditBox(font, fieldX, fieldsY, fieldWidth, 18, Component.literal(label));
        field.setMaxLength(16);
        field.setValue(value);
        field.setResponder(ignored -> {
            if (!updating && x != null && y != null && z != null) {
                draft.coordinates(x.getValue(), y.getValue(), z.getValue());
                updateButtons();
            }
        });
        return addRenderableWidget(field);
    }

    private void fillFields() {
        updating = true;
        x.setValue(draft.x()); y.setValue(draft.y()); z.setValue(draft.z());
        updating = false;
    }

    private void load() {
        request(Intent.LOAD, room == null ? MapAdminRequestPacket.Operation.DEFAULTS
                : MapAdminRequestPacket.Operation.END_POINT_DETAIL, null);
    }

    private void save() {
        if (pending >= 0 || settings == null || !settings.editable() || !draft.canSave()) return;
        var value = draft.value().orElseThrow();
        request(Intent.SAVE, room == null ? MapAdminRequestPacket.Operation.SAVE_DEFAULTS
                : MapAdminRequestPacket.Operation.SAVE_END_POINT,
                new MapAdminData.EndPoint(value.dimension(), new BlockPos(value.x(), value.y(), value.z()), value.yaw(), 0));
    }

    private void request(Intent purpose, MapAdminRequestPacket.Operation operation, MapAdminData.EndPoint point) {
        if (pending >= 0) return;
        intent = purpose; expectedOperation = operation; pending = ++nextId;
        started = System.currentTimeMillis(); status = "loading";
        ModNetworkChannel.sendToServer(MapAdminRequestPacket.teleport(operation, session, pending, room,
                settings == null ? "" : settings.revision(), point));
        updateButtons();
    }

    private void accept(MapAdminResponsePacket response) {
        if (!session.equals(response.session()) || pending != response.requestId() || expectedOperation != response.operation()) return;
        pending = -1;
        boolean success = response.code() == MapAdminResponsePacket.Code.OK;
        if (intent == Intent.CURRENT && success && response.currentPosition() != null) {
            draft.fill(value(response.currentPosition())); fillFields(); status = "draft_filled";
        } else if (intent == Intent.FILL_GLOBAL && success && response.teleportSettings() != null
                && response.teleportSettings().room() == null) {
            var defaults = response.teleportSettings();
            settings = new MapAdminData.TeleportSettings(settings.room(), settings.point(), defaults.point(), settings.currentPosition(),
                    settings.revision(), settings.editable(), settings.reason(), true);
            if (defaults.point().isPresent()) {
                draft.fill(value(defaults.point().get())); fillFields(); status = "draft_filled";
            } else status = "global_unset";
        } else if ((intent == Intent.LOAD || intent == Intent.SAVE) && response.teleportSettings() != null
                && Objects.equals(room, response.teleportSettings().room())) {
            settings = response.teleportSettings();
            var saved = settings.point().map(EndTeleportScreen::value).orElse(null);
            if (success) {
                draft.load(saved, value(settings.currentPosition())); fillFields();
                status = intent == Intent.SAVE ? "saved" : settings.point().isEmpty() ? "unset" : "ready";
                if (!settings.editable()) status = "blocked";
            } else {
                // Refresh the server baseline while retaining the user's draft for deliberate resubmission.
                draft.baseline(saved);
                status = response.code() == MapAdminResponsePacket.Code.STALE ? "stale" : "blocked";
            }
        } else {
            status = switch (response.code()) {
                case DENIED -> "denied";
                case STALE -> "stale";
                case RATE_LIMIT -> "rate_limited";
                default -> response.result().equals("defaults_unavailable") ? "defaults_unavailable"
                        : response.result().equals("invalid_target") ? "invalid_target" : "error";
            };
            if (intent == Intent.LOAD) settings = null;
            if (response.code() == MapAdminResponsePacket.Code.DENIED) settings = null;
        }
        updateButtons();
    }

    private static EndTeleportDraft.Value value(MapAdminData.EndPoint point) {
        return new EndTeleportDraft.Value(point.dimensionId(), point.position().getX(), point.position().getY(),
                point.position().getZ(), point.yaw());
    }

    private void updateButtons() {
        if (saveButton == null) return;
        boolean ready = pending < 0 && settings != null && settings.editable();
        x.setEditable(ready); y.setEditable(ready); z.setEditable(ready);
        currentButton.active = ready;
        if (globalButton != null) {
            globalButton.active = ready && settings.defaultsAvailable() && settings.defaultPoint().isPresent();
            globalButton.setTooltip(Tooltip.create(text(settings == null || settings.defaultsAvailable() ? "global_unset" : "defaults_unavailable")));
            if (globalButton.active) globalButton.setTooltip(Tooltip.create(text("map_note")));
        }
        saveButton.active = ready && draft.canSave();
        refreshButton.active = pending < 0;
        if (settings != null && !settings.editable()) saveButton.setTooltip(Tooltip.create(blockedReason()));
        else saveButton.setTooltip(null);
    }

    private Component blockedReason() {
        if (settings == null) return text("loading");
        if (settings.reason().equals("unsupported")) return text("unsupported");
        return Component.translatable("screen.codpattern.map_admin.disabled." + settings.reason());
    }

    @Override public void tick() {
        x.tick(); y.tick(); z.tick();
        if (pending >= 0 && System.currentTimeMillis() - started > 15_000) {
            pending = -1; status = "timeout"; updateButtons();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, CodTheme.BG_BOTTOM);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width - 90), 12, 10, CodTheme.TEXT_PRIMARY, false);
        graphics.drawWordWrap(font, text(room == null ? "global_note" : "map_note"), left, 60, contentWidth, CodTheme.TEXT_SECONDARY);
        graphics.drawString(font, "X", x.getX(), fieldsY - 11, CodTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, "Y", y.getX(), fieldsY - 11, CodTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, "Z", z.getX(), fieldsY - 11, CodTheme.TEXT_PRIMARY, false);
        Component dimension = Component.translatable(KEY + "dimension", draft.dimension());
        graphics.drawString(font, font.plainSubstrByWidth(dimension.getString(), contentWidth), left, fieldsY + 25, CodTheme.TEXT_SECONDARY, false);
        graphics.drawString(font, Component.translatable(KEY + "yaw", String.format(java.util.Locale.ROOT, "%.1f", draft.yaw())),
                left, fieldsY + 39, CodTheme.TEXT_SECONDARY, false);
        Component message = status.equals("blocked") ? blockedReason()
                : pending < 0 && settings != null && draft.dirty() && draft.value().isEmpty() ? text("invalid_coordinates") : text(status);
        int messageY = fieldsY + 83;
        graphics.enableScissor(left, messageY, left + contentWidth, height - 34);
        graphics.drawWordWrap(font, message, left, messageY, contentWidth, CodTheme.TEXT_SECONDARY);
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseY >= fieldsY + 25 && mouseY < fieldsY + 35 && mouseX >= left && mouseX < left + contentWidth)
            graphics.renderTooltip(font, dimension, mouseX, mouseY);
        if (mouseY >= messageY && mouseY < height - 34 && mouseX >= left && mouseX < left + contentWidth)
            graphics.renderTooltip(font, font.split(message, contentWidth), mouseX, mouseY);
    }

    private void guard(Runnable action) {
        if (pending >= 0) return;
        if (!draft.dirty()) { action.run(); return; }
        confirmation = new ConfirmScreen(approved -> {
            Minecraft.getInstance().setScreen(this); confirmation = null;
            if (approved) action.run();
        }, text("discard_title"), text("discard_message"));
        Minecraft.getInstance().setScreen(confirmation);
    }

    @Override public void onClose() {
        guard(() -> {
            Minecraft.getInstance().setScreen(previous);
            previous.refreshAfterTeleportEdit();
        });
    }
    @Override public boolean isPauseScreen() { return false; }
    private static Component text(String suffix) { return Component.translatable(KEY + suffix); }
}
