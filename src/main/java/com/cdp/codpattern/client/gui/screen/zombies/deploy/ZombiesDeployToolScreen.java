package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployFieldSchema;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeploySnapshot;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.packet.OpenZombiesDeployToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ZombiesDeployToolActionC2SPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class ZombiesDeployToolScreen extends Screen {
    private static final int PANEL_WIDTH = 820;
    private static final int PANEL_HEIGHT = 430;
    private static final int SCREEN_OVERLAY = 0x5A000000;
    private static final int PANEL_BACKGROUND = 0xD0191D22;
    private static final int PANEL_BORDER = 0xFF5DB36B;
    private static final int PANEL_MUTED = 0xFF1F252B;
    private static final int HIGHLIGHT = 0x80448557;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED_TEXT = 0xFF9DB0B8;
    private static final int LABEL_TEXT = 0xFFD7E8D8;
    private static final int ERROR_TEXT = 0xFFFF7777;
    private static final int WARNING_TEXT = 0xFFFFCC66;
    private static final int INFO_TEXT = 0xFFAED7FF;
    private static final int CONTROL_ROW_Y = 332;
    private static final int FIELD_LABEL_Y = 354;
    private static final int FIELD_INPUT_Y = 368;
    private static final int BOTTOM_ROW_Y = 396;

    private ZombiesDeploySnapshot snapshot;
    private String workspaceStage;
    private String selectedMap;
    private String draftMapName;
    private BlockPos mapPos1;
    private BlockPos mapPos2;
    private String selectedObjectType;
    private String capturePreset;
    private int selectedIndex;
    private String selectedProfile;
    private int selectedFieldIndex;
    private int selectedListRowIndex;
    private String selectedListFieldKey = "";
    private final Map<String, String> draftFields = new LinkedHashMap<>();

    private Button mapButton;
    private Button stageButton;
    private Button capturePresetButton;
    private Button typeButton;
    private Button profileButton;
    private Button objectPrevButton;
    private Button objectNextButton;
    private Button fieldPrevButton;
    private Button fieldNextButton;
    private Button setFieldButton;
    private Button listRowPrevButton;
    private Button listRowNextButton;
    private Button listRowInsertButton;
    private Button listRowUpdateButton;
    private Button listRowDeleteButton;
    private Button updateObjectButton;
    private Button deleteObjectButton;
    private Button createMapButton;
    private Button updateMapAreaButton;
    private EditBox fieldValueBox;
    private EditBox mapNameBox;

    public ZombiesDeployToolScreen(OpenZombiesDeployToolScreenS2CPacket packet) {
        super(Component.translatable("gui.codpattern.zombies.deploy.title"));
        applySnapshot(packet.snapshot());
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();

        this.mapButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleMap())
                .pos(left + 16, top + 30)
                .size(148, 20)
                .build());
        this.stageButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleWorkspaceStage())
                .pos(left + 170, top + 30)
                .size(118, 20)
                .build());
        this.typeButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleObjectType())
                .pos(left + 294, top + 30)
                .size(156, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.refresh"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.REFRESH))
                .pos(left + 464, top + 30)
                .size(78, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.save_selections"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS))
                .pos(left + 548, top + 30)
                .size(114, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.validate"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.VALIDATE_MAP))
                .pos(left + 668, top + 30)
                .size(88, 20)
                .build());
        this.mapNameBox = this.addRenderableWidget(new EditBox(this.font, left + 16, top + 56, 148, 18, Component.empty()));
        this.mapNameBox.setMaxLength(64);
        this.createMapButton = this.addRenderableWidget(new Button.Builder(Component.literal("创建地图"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.CREATE_MAP))
                .pos(left + 170, top + 55)
                .size(78, 20)
                .build());
        this.updateMapAreaButton = this.addRenderableWidget(new Button.Builder(Component.literal("更新范围"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.UPDATE_MAP_AREA))
                .pos(left + 254, top + 55)
                .size(78, 20)
                .build());
        this.capturePresetButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleCapturePreset())
                .pos(left + 338, top + 55)
                .size(112, 20)
                .build());
        this.profileButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleProfile())
                .pos(left + 668, top + 55)
                .size(88, 20)
                .build());

        this.objectPrevButton = this.addRenderableWidget(new Button.Builder(Component.literal("<"), button -> stepObject(-1))
                .pos(left + 16, top + CONTROL_ROW_Y)
                .size(24, 20)
                .build());
        this.objectNextButton = this.addRenderableWidget(new Button.Builder(Component.literal(">"), button -> stepObject(1))
                .pos(left + 44, top + CONTROL_ROW_Y)
                .size(24, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.add"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.ADD_OBJECT))
                .pos(left + 76, top + CONTROL_ROW_Y)
                .size(58, 20)
                .build());
        this.updateObjectButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.update"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.UPDATE_OBJECT))
                .pos(left + 140, top + CONTROL_ROW_Y)
                .size(68, 20)
                .build());
        this.deleteObjectButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.delete"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.DELETE_OBJECT))
                .pos(left + 214, top + CONTROL_ROW_Y)
                .size(68, 20)
                .build());

        this.fieldPrevButton = this.addRenderableWidget(new Button.Builder(Component.literal("<"), button -> stepField(-1))
                .pos(left + 294, top + CONTROL_ROW_Y)
                .size(24, 20)
                .build());
        this.fieldNextButton = this.addRenderableWidget(new Button.Builder(Component.literal(">"), button -> stepField(1))
                .pos(left + 322, top + CONTROL_ROW_Y)
                .size(24, 20)
                .build());
        this.setFieldButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.set_field"), button -> setCurrentField())
                .pos(left + 352, top + CONTROL_ROW_Y)
                .size(86, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.player_pos"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.CAPTURE_PLAYER_POS))
                .pos(left + 444, top + CONTROL_ROW_Y)
                .size(86, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.look_block"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.CAPTURE_LOOK_BLOCK))
                .pos(left + 536, top + CONTROL_ROW_Y)
                .size(86, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.area_1"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.SET_AREA_POS_1))
                .pos(left + 628, top + CONTROL_ROW_Y)
                .size(62, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.area_2"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.SET_AREA_POS_2))
                .pos(left + 696, top + CONTROL_ROW_Y)
                .size(62, 20)
                .build());

        this.fieldValueBox = this.addRenderableWidget(new EditBox(this.font, left + 294, top + FIELD_INPUT_Y, 464, 20, Component.empty()));
        this.fieldValueBox.setMaxLength(2048);

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.close"), button -> onClose())
                .pos(left + 696, top + BOTTOM_ROW_Y)
                .size(62, 20)
                .build());
        this.listRowPrevButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.row_prev"), button -> stepListRow(-1))
                .pos(left + 294, top + BOTTOM_ROW_Y)
                .size(44, 20)
                .build());
        this.listRowNextButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.row_next"), button -> stepListRow(1))
                .pos(left + 342, top + BOTTOM_ROW_Y)
                .size(44, 20)
                .build());
        this.listRowInsertButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.insert"), button -> insertCurrentListRow())
                .pos(left + 394, top + BOTTOM_ROW_Y)
                .size(54, 20)
                .build());
        this.listRowUpdateButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.update"), button -> updateCurrentListRow())
                .pos(left + 452, top + BOTTOM_ROW_Y)
                .size(58, 20)
                .build());
        this.listRowDeleteButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.delete"), button -> deleteCurrentListRow())
                .pos(left + 514, top + BOTTOM_ROW_Y)
                .size(58, 20)
                .build());

        updateWidgets();
    }

    public void applyData(OpenZombiesDeployToolScreenS2CPacket packet) {
        String selectedField = currentFieldKey();
        applySnapshot(packet.snapshot());
        restoreFieldSelection(selectedField);
        updateWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fieldValueBox != null) {
            this.fieldValueBox.tick();
        }
        if (this.mapNameBox != null) {
            this.mapNameBox.tick();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = panelLeft();
        int top = panelTop();
        guiGraphics.fill(0, 0, this.width, this.height, SCREEN_OVERLAY);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BACKGROUND);
        drawBorder(guiGraphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);

        guiGraphics.drawString(this.font, this.title, left + 12, top + 10, TEXT, false);
        drawStatus(guiGraphics, left + 470, top + 10, 338);

        drawStageSummary(guiGraphics, left + 464, top + 55, 190);

        drawSection(guiGraphics, left + 12, top + 82, 270, 242, tr("gui.codpattern.zombies.deploy.objects"));
        drawCompactSteps(guiGraphics, left + 88, top + 88, 188);
        drawObjects(guiGraphics, left + 18, top + 104, 258);

        drawSection(guiGraphics, left + 290, top + 82, 230, 242, tr("gui.codpattern.zombies.deploy.fields"));
        drawFields(guiGraphics, left + 296, top + 104, 218);

        drawSection(guiGraphics, left + 528, top + 82, 280, 242, tr("gui.codpattern.zombies.deploy.validation"));
        boolean showListPreview = isCurrentListField();
        drawValidation(guiGraphics, left + 534, top + 104, 268, showListPreview ? 5 : 14);
        if (showListPreview) {
            drawListPreview(guiGraphics, left + 534, top + 220, 268);
        }

        drawCurrentField(guiGraphics, left + 294, top + FIELD_LABEL_Y, 392);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!isInsidePanel(mouseX, mouseY)
                && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            BlockPos clickedPos = pickBlockPos(mouseX, mouseY);
            if (clickedPos == null) {
                return false;
            }
            boolean first = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
            FPSMatch.sendToServer(new ToolInteractionC2SPacket(
                    first ? ToolInteractionAction.LEFT_CLICK_BLOCK : ToolInteractionAction.RIGHT_CLICK_BLOCK,
                    clickedPos));
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        int left = panelLeft();
        int top = panelTop();
        int objectIndex = listIndexAt(mouseX, mouseY, left + 18, top + 104, visibleObjectCount());
        if (objectIndex >= 0) {
            selectVisibleObject(objectIndex);
            return true;
        }

        int listRowIndex = listRowIndexAt(mouseX, mouseY, left + 534, top + 220, 268);
        if (listRowIndex >= 0) {
            selectListRow(listRowIndex);
            return true;
        }

        int fieldIndex = listIndexAt(mouseX, mouseY, left + 296, top + 104, visibleFieldCount());
        if (fieldIndex >= 0) {
            selectVisibleField(fieldIndex);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            setCurrentField();
            return true;
        }
        boolean editorFocused = this.fieldValueBox != null && this.fieldValueBox.isFocused();
        if (this.fieldValueBox != null && this.fieldValueBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (editorFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            sendAction(ZombiesDeployToolActionC2SPacket.Action.REFRESH);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V) {
            sendAction(ZombiesDeployToolActionC2SPacket.Action.VALIDATE_MAP);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S && Screen.hasControlDown()) {
            sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            stepField(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            stepField(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.fieldValueBox != null && this.fieldValueBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applySnapshot(ZombiesDeploySnapshot nextSnapshot) {
        this.snapshot = nextSnapshot == null ? emptySnapshot() : nextSnapshot;
        this.workspaceStage = this.snapshot.workspaceStage();
        this.selectedMap = this.snapshot.selectedMap();
        this.draftMapName = this.snapshot.draftMapName();
        this.mapPos1 = this.snapshot.mapPos1();
        this.mapPos2 = this.snapshot.mapPos2();
        this.selectedObjectType = this.snapshot.selectedObjectType();
        this.capturePreset = this.snapshot.capturePreset();
        this.selectedIndex = this.snapshot.selectedIndex();
        this.selectedProfile = this.snapshot.profileKey();
        this.draftFields.clear();
        for (ZombiesDeploySnapshot.FieldValue field : this.snapshot.fields()) {
            this.draftFields.put(field.key(), field.value());
        }
        this.selectedFieldIndex = clampIndex(this.selectedFieldIndex, this.snapshot.fields().size());
    }

    private ZombiesDeploySnapshot emptySnapshot() {
        return new ZombiesDeploySnapshot(
                List.of(),
                ZombiesDeployDraft.STAGE_MAP_REGISTRATION,
                "",
                "",
                null,
                null,
                List.of(new ZombiesDeploySnapshot.ObjectTypeOption(ZombiesDeployFieldSchema.INITIAL, "")),
                ZombiesDeployFieldSchema.INITIAL,
                ZombiesDeployDraft.CAPTURE_DEFAULT,
                "mapPos1",
                "mapPos2",
                -1,
                List.of(),
                List.of(),
                ZombiesDeployFieldSchema.PROFILE_MVP1,
                List.of(ZombiesDeployFieldSchema.PROFILE_MVP1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                0,
                "",
                "",
                "");
    }

    private void updateWidgets() {
        if (this.mapButton == null) {
            return;
        }
        this.mapButton.setMessage(Component.literal(labelOrDash(this.selectedMap)));
        this.mapButton.active = !snapshot.availableMaps().isEmpty();

        this.stageButton.setMessage(Component.literal(stageLabel(this.workspaceStage)));
        this.capturePresetButton.setMessage(Component.literal(capturePresetLabel(this.capturePreset)));
        this.capturePresetButton.active = ZombiesDeployFieldSchema.BARRIER.equals(this.selectedObjectType);

        this.typeButton.setMessage(Component.literal(labelOrDash(objectTypeLabel(this.selectedObjectType))));
        this.typeButton.active = !snapshot.objectTypes().isEmpty();

        this.profileButton.setMessage(Component.literal("校验 " + labelOrDash(this.selectedProfile)));
        this.profileButton.active = snapshot.availableProfiles().size() > 1;

        if (this.mapNameBox != null && !this.mapNameBox.isFocused() && !Objects.equals(this.mapNameBox.getValue(), this.draftMapName)) {
            this.mapNameBox.setValue(this.draftMapName);
        }
        boolean inMapStage = ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage);
        this.createMapButton.active = inMapStage && this.mapPos1 != null && this.mapPos2 != null;
        this.updateMapAreaButton.active = inMapStage && !this.selectedMap.isBlank() && this.mapPos1 != null && this.mapPos2 != null;

        boolean hasObjects = !snapshot.objects().isEmpty();
        this.objectPrevButton.active = hasObjects;
        this.objectNextButton.active = hasObjects;
        this.updateObjectButton.active = hasObjects;
        this.deleteObjectButton.active = hasObjects;

        boolean hasFields = !snapshot.fields().isEmpty();
        this.fieldPrevButton.active = hasFields;
        this.fieldNextButton.active = hasFields;
        ZombiesDeploySnapshot.FieldValue field = currentField();
        boolean listField = field != null && field.type() == ZombiesDeployFieldSchema.FieldType.LIST;
        boolean editableField = field != null && field.editable();
        int listRowCount = listField ? listRows(field.key(), draftFields.getOrDefault(field.key(), field.value())).size() : 0;
        this.selectedListRowIndex = clampIndex(this.selectedListRowIndex, listRowCount);
        this.setFieldButton.setMessage(Component.translatable(listField
                ? "gui.codpattern.zombies.deploy.set_row"
                : "gui.codpattern.zombies.deploy.set_field"));
        this.setFieldButton.active = editableField;

        if (this.listRowPrevButton != null) {
            this.listRowPrevButton.active = listField && listRowCount > 0;
            this.listRowNextButton.active = listField && listRowCount > 0;
            this.listRowInsertButton.active = listField && editableField;
            this.listRowUpdateButton.active = listField && editableField && listRowCount > 0;
            this.listRowDeleteButton.active = listField && editableField && listRowCount > 0;
        }

        updateFieldEditor();
    }

    private void updateFieldEditor() {
        if (this.fieldValueBox == null) {
            return;
        }
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null) {
            this.fieldValueBox.setValue("");
            this.fieldValueBox.setEditable(false);
            this.fieldValueBox.setFilter(value -> true);
            return;
        }
        this.fieldValueBox.setFilter(filterFor(field.type()));
        String value = draftFields.getOrDefault(field.key(), field.value());
        if (field.type() == ZombiesDeployFieldSchema.FieldType.LIST) {
            if (!field.key().equals(this.selectedListFieldKey)) {
                this.selectedListFieldKey = field.key();
                this.selectedListRowIndex = 0;
            }
            List<String> rows = listRows(field.key(), value);
            this.selectedListRowIndex = clampIndex(this.selectedListRowIndex, rows.size());
            this.fieldValueBox.setValue(rows.isEmpty() ? "" : rows.get(this.selectedListRowIndex));
        } else {
            this.selectedListFieldKey = "";
            this.fieldValueBox.setValue(value);
        }
        this.fieldValueBox.setEditable(field.editable());
    }

    private Predicate<String> filterFor(ZombiesDeployFieldSchema.FieldType type) {
        return switch (type) {
            case INTEGER -> value -> value.matches("-?\\d*");
            case DECIMAL -> value -> value.matches("-?\\d*(\\.\\d*)?");
            case BOOLEAN -> value -> {
                String lower = value.toLowerCase(Locale.ROOT);
                return lower.isBlank()
                        || "true".startsWith(lower)
                        || "false".startsWith(lower)
                        || lower.equals("1")
                        || lower.equals("0");
            };
            case TEXT, LIST -> value -> true;
        };
    }

    private void cycleMap() {
        List<String> maps = snapshot.availableMaps();
        if (maps.isEmpty()) {
            return;
        }
        int next = nextIndex(maps, this.selectedMap);
        this.selectedMap = maps.get(next);
        this.selectedIndex = -1;
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_MAP);
    }

    private void cycleObjectType() {
        List<ZombiesDeploySnapshot.ObjectTypeOption> types = snapshot.objectTypes();
        if (types.isEmpty()) {
            return;
        }
        List<String> keys = types.stream().map(ZombiesDeploySnapshot.ObjectTypeOption::key).toList();
        int next = nextIndex(keys, this.selectedObjectType);
        this.selectedObjectType = keys.get(next);
        this.selectedIndex = -1;
        this.selectedFieldIndex = 0;
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_OBJECT_TYPE);
    }

    private void cycleWorkspaceStage() {
        this.workspaceStage = ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage)
                ? ZombiesDeployDraft.STAGE_OBJECT_MARKING
                : ZombiesDeployDraft.STAGE_MAP_REGISTRATION;
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_WORKSPACE_STAGE);
    }

    private void cycleCapturePreset() {
        if (!ZombiesDeployFieldSchema.BARRIER.equals(this.selectedObjectType)) {
            this.capturePreset = ZombiesDeployDraft.CAPTURE_DEFAULT;
            return;
        }
        this.capturePreset = ZombiesDeployDraft.CAPTURE_BARRIER_INTERACTION.equals(this.capturePreset)
                ? ZombiesDeployDraft.CAPTURE_BARRIER_AREA
                : ZombiesDeployDraft.CAPTURE_BARRIER_INTERACTION;
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_CAPTURE_PRESET);
    }

    private void cycleProfile() {
        List<String> profiles = snapshot.availableProfiles();
        if (profiles.isEmpty()) {
            return;
        }
        int next = nextIndex(profiles, this.selectedProfile);
        this.selectedProfile = profiles.get(next);
        sendAction(ZombiesDeployToolActionC2SPacket.Action.REFRESH);
    }

    private int nextIndex(List<String> values, String current) {
        int currentIndex = values.indexOf(current);
        return currentIndex < 0 ? 0 : (currentIndex + 1) % values.size();
    }

    private void stepObject(int offset) {
        if (snapshot.objects().isEmpty()) {
            this.selectedIndex = -1;
            updateWidgets();
            return;
        }
        int listPosition = selectedObjectListPosition();
        int nextPosition = Math.max(0, Math.min(listPosition + offset, snapshot.objects().size() - 1));
        this.selectedIndex = snapshot.objects().get(nextPosition).index();
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_OBJECT);
    }

    private void stepField(int offset) {
        this.selectedFieldIndex = clampIndex(this.selectedFieldIndex + offset, snapshot.fields().size());
        updateWidgets();
    }

    private void setCurrentField() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || !field.editable() || this.fieldValueBox == null) {
            return;
        }
        if (field.type() == ZombiesDeployFieldSchema.FieldType.LIST) {
            updateCurrentListRow();
            return;
        }
        String value = this.fieldValueBox.getValue();
        this.draftFields.put(field.key(), value);
        FPSMatch.sendToServer(new ZombiesDeployToolActionC2SPacket(
                ZombiesDeployToolActionC2SPacket.Action.SET_FIELD,
                draft(),
                field.key(),
                value));
    }

    private void sendAction(ZombiesDeployToolActionC2SPacket.Action action) {
        FPSMatch.sendToServer(new ZombiesDeployToolActionC2SPacket(action, draft()));
    }

    private ZombiesDeployDraft draft() {
        Map<String, String> fields = new LinkedHashMap<>(this.draftFields);
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field != null && this.fieldValueBox != null && field.editable()) {
            if (field.type() == ZombiesDeployFieldSchema.FieldType.LIST) {
                fields.put(field.key(), currentListValueWithEditorRow(field));
            } else {
                fields.put(field.key(), this.fieldValueBox.getValue());
            }
        }
        return draftFromFields(fields);
    }

    private ZombiesDeployDraft draftFromFields(Map<String, String> fields) {
        return new ZombiesDeployDraft(
                this.workspaceStage,
                this.selectedMap,
                this.mapNameBox == null ? this.draftMapName : this.mapNameBox.getValue(),
                this.mapPos1,
                this.mapPos2,
                this.selectedObjectType,
                this.capturePreset,
                this.selectedIndex,
                this.selectedProfile,
                fields);
    }

    private void stepListRow(int offset) {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST) {
            return;
        }
        List<String> rows = listRows(field.key(), draftFields.getOrDefault(field.key(), field.value()));
        if (rows.isEmpty()) {
            this.selectedListRowIndex = 0;
            updateWidgets();
            return;
        }
        this.selectedListRowIndex = Math.max(0, Math.min(this.selectedListRowIndex + offset, rows.size() - 1));
        updateWidgets();
    }

    private void insertCurrentListRow() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST || !field.editable() || this.fieldValueBox == null) {
            return;
        }
        String previousValue = draftFields.getOrDefault(field.key(), field.value());
        List<String> rows = new ArrayList<>(listRows(field.key(), previousValue));
        int insertIndex = rows.isEmpty() ? 0 : Math.min(this.selectedListRowIndex + 1, rows.size());
        rows.add(insertIndex, this.fieldValueBox.getValue().trim());
        this.selectedListRowIndex = insertIndex;
        writeListField(field, rows, previousValue);
    }

    private void updateCurrentListRow() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST || !field.editable() || this.fieldValueBox == null) {
            return;
        }
        String previousValue = draftFields.getOrDefault(field.key(), field.value());
        List<String> rows = new ArrayList<>(listRows(field.key(), previousValue));
        if (rows.isEmpty()) {
            rows.add(this.fieldValueBox.getValue().trim());
            this.selectedListRowIndex = 0;
        } else {
            this.selectedListRowIndex = clampIndex(this.selectedListRowIndex, rows.size());
            rows.set(this.selectedListRowIndex, this.fieldValueBox.getValue().trim());
        }
        writeListField(field, rows, previousValue);
    }

    private void deleteCurrentListRow() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST || !field.editable()) {
            return;
        }
        String previousValue = draftFields.getOrDefault(field.key(), field.value());
        List<String> rows = new ArrayList<>(listRows(field.key(), previousValue));
        if (rows.isEmpty()) {
            return;
        }
        this.selectedListRowIndex = clampIndex(this.selectedListRowIndex, rows.size());
        rows.remove(this.selectedListRowIndex);
        this.selectedListRowIndex = clampIndex(this.selectedListRowIndex, rows.size());
        writeListField(field, rows, previousValue);
    }

    private void writeListField(ZombiesDeploySnapshot.FieldValue field, List<String> rows, String previousValue) {
        String value = serializeListRows(field.key(), rows, previousValue);
        this.draftFields.put(field.key(), value);
        Map<String, String> fields = new LinkedHashMap<>(this.draftFields);
        FPSMatch.sendToServer(new ZombiesDeployToolActionC2SPacket(
                ZombiesDeployToolActionC2SPacket.Action.SET_FIELD,
                draftFromFields(fields),
                field.key(),
                value));
        updateWidgets();
    }

    private void drawBorder(GuiGraphics guiGraphics, int left, int top, int width, int height, int color) {
        guiGraphics.fill(left, top, left + width, top + 1, color);
        guiGraphics.fill(left, top + height - 1, left + width, top + height, color);
        guiGraphics.fill(left, top, left + 1, top + height, color);
        guiGraphics.fill(left + width - 1, top, left + width, top + height, color);
    }

    private void drawSection(GuiGraphics guiGraphics, int left, int top, int width, int height, String label) {
        guiGraphics.fill(left, top, left + width, top + height, PANEL_MUTED);
        drawBorder(guiGraphics, left, top, width, height, 0xFF39424B);
        guiGraphics.drawString(this.font, Component.literal(label), left + 6, top + 6, LABEL_TEXT, false);
    }

    private void drawStageSummary(GuiGraphics guiGraphics, int left, int top, int width) {
        String binding = "左键 -> " + labelOrDash(snapshot.captureSlotA()) + "  右键 -> " + labelOrDash(snapshot.captureSlotB());
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage)) {
            binding = "左键 -> mapPos1  右键 -> mapPos2";
        }
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(binding, width)), left, top, INFO_TEXT, false);
        String mapLine = "地图范围 A " + formatPos(this.mapPos1) + " / B " + formatPos(this.mapPos2);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(mapLine, width)), left, top + 10, MUTED_TEXT, false);
        int y = top + 22;
        for (int i = 0; i < Math.min(2, snapshot.stepStatuses().size()); i++) {
            ZombiesDeploySnapshot.StepStatus status = snapshot.stepStatuses().get(i);
            int color = status.complete() ? INFO_TEXT : WARNING_TEXT;
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(status.label() + ": " + status.detail(), width)), left, y, color, false);
            y += 10;
        }
    }

    private void drawCompactSteps(GuiGraphics guiGraphics, int left, int top, int width) {
        if (snapshot.stepStatuses().isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (ZombiesDeploySnapshot.StepStatus status : snapshot.stepStatuses()) {
            parts.add((status.complete() ? "+" : "!") + status.label().substring(0, 1));
        }
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(String.join(" ", parts), width)), left, top, MUTED_TEXT, false);
    }

    private void drawObjects(GuiGraphics guiGraphics, int left, int top, int width) {
        if (snapshot.objects().isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(
                    tr("gui.codpattern.zombies.deploy.no_objects") + " " + labelOrDash(objectTypeLabel(selectedObjectType)),
                    width)), left, top, MUTED_TEXT, false);
            return;
        }
        int start = visibleObjectStart();
        int end = Math.min(snapshot.objects().size(), start + visibleObjectCount());
        for (int i = start; i < end; i++) {
            ZombiesDeploySnapshot.ObjectSummary object = snapshot.objects().get(i);
            int y = top + (i - start) * 22;
            if (object.index() == selectedIndex) {
                guiGraphics.fill(left - 3, y - 2, left + width, y + 18, HIGHLIGHT);
            }
            String first = object.index() + ": " + labelOrDash(object.objectId()) + "  " + labelOrDash(object.primary());
            String second = labelOrDash(object.detail());
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(first, width)), left, y, TEXT, false);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(second, width)), left, y + 10, MUTED_TEXT, false);
        }
    }

    private void drawFields(GuiGraphics guiGraphics, int left, int top, int width) {
        if (snapshot.fields().isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.codpattern.zombies.deploy.no_fields"), left, top, MUTED_TEXT, false);
            return;
        }
        int start = visibleFieldStart();
        int end = Math.min(snapshot.fields().size(), start + visibleFieldCount());
        for (int i = start; i < end; i++) {
            ZombiesDeploySnapshot.FieldValue field = snapshot.fields().get(i);
            int y = top + (i - start) * 22;
            if (i == selectedFieldIndex) {
                guiGraphics.fill(left - 3, y - 2, left + width, y + 18, HIGHLIGHT);
            }
            String value = draftFields.getOrDefault(field.key(), field.value());
            String first = tr(field.labelKey()) + "  " + field.type().name().toLowerCase(Locale.ROOT);
            String second = field.type() == ZombiesDeployFieldSchema.FieldType.LIST
                    ? listFieldSummary(field.key(), value)
                    : (value.isBlank() ? tr("gui.codpattern.zombies.deploy.blank") : value);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(first, width)), left, y, field.editable() ? TEXT : MUTED_TEXT, false);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(second, width)), left, y + 10, MUTED_TEXT, false);
        }
    }

    private void drawValidation(GuiGraphics guiGraphics, int left, int top, int width, int maxLines) {
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(tr("gui.codpattern.zombies.deploy.map") + ": " + labelOrDash(selectedMap), width)), left, top, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(tr("gui.codpattern.zombies.deploy.profile") + ": " + labelOrDash(selectedProfile), width)), left, top + 12, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(
                tr("gui.codpattern.zombies.deploy.active") + ": "
                        + (snapshot.activeMap() ? tr("gui.codpattern.zombies.deploy.yes") : tr("gui.codpattern.zombies.deploy.no"))
                        + "  " + tr("gui.codpattern.zombies.deploy.revision") + ": " + snapshot.revision(),
                width)), left, top + 24, LABEL_TEXT, false);
        int summaryY = top + 36;
        for (ZombiesDeploySnapshot.ValidationSummary summary : snapshot.validationSummaries()) {
            String text = summary.profileKey() + " E" + summary.errors() + " W" + summary.warnings();
            int color = summary.errors() > 0 ? ERROR_TEXT : (summary.warnings() > 0 ? WARNING_TEXT : INFO_TEXT);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(text, width)), left, summaryY, color, false);
            summaryY += 10;
        }

        List<ZombiesDeploySnapshot.ValidationLine> lines = snapshot.validationLines();
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.codpattern.zombies.deploy.no_validation_issues"), left, summaryY + 2, INFO_TEXT, false);
            return;
        }
        int lineLimit = Math.max(1, maxLines - snapshot.validationSummaries().size());
        for (int i = 0; i < Math.min(lines.size(), lineLimit); i++) {
            ZombiesDeploySnapshot.ValidationLine line = lines.get(i);
            int y = summaryY + 2 + i * 12;
            String text = "[" + line.severity() + "] " + line.code() + " " + line.subject() + " " + line.message();
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(text, width)), left, y, colorForSeverity(line.severity()), false);
        }
        if (lines.size() > lineLimit) {
            guiGraphics.drawString(this.font, Component.literal(ta("gui.codpattern.zombies.deploy.more", lines.size() - lineLimit)), left, summaryY + 2 + lineLimit * 12, MUTED_TEXT, false);
        }
    }

    private void drawListPreview(GuiGraphics guiGraphics, int left, int top, int width) {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST) {
            return;
        }
        String value = currentListValueWithEditorRow(field);
        ListFieldPreview preview = listPreview(field.key(), value);
        int statusColor = preview.hasErrors() ? ERROR_TEXT : (preview.hasWarnings() ? WARNING_TEXT : INFO_TEXT);

        guiGraphics.fill(left - 4, top - 4, left + width, top + 96, 0x8014181D);
        drawBorder(guiGraphics, left - 4, top - 4, width + 4, 100, 0xFF39424B);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(tr("gui.codpattern.zombies.deploy.list_helper") + ": " + tr(field.labelKey()), width - 4)), left, top, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(preview.hint(), width - 4)), left, top + 12, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(statusLine(preview), width - 4)), left, top + 24, statusColor, false);

        int y = top + 38;
        int issueLimit = Math.min(2, preview.issues().size());
        for (int i = 0; i < issueLimit; i++) {
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(issueText(preview.issues().get(i)), width - 4)), left, y, statusColor, false);
            y += 10;
        }

        int rowLimit = Math.max(1, Math.min(5, (top + 90 - y) / 10));
        int rowStart = visibleListRowStart(preview.rows().size(), rowLimit);
        int rowEnd = Math.min(preview.rows().size(), rowStart + rowLimit);
        for (int i = rowStart; i < rowEnd; i++) {
            if (i == clampIndex(this.selectedListRowIndex, preview.rows().size())) {
                guiGraphics.fill(left - 2, y - 1, left + width - 4, y + 9, HIGHLIGHT);
            }
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(preview.rows().get(i), width - 4)), left, y, TEXT, false);
            y += 10;
        }
        if (preview.rows().size() > rowLimit) {
            String range = ta("gui.codpattern.zombies.deploy.rows_range", rowStart + 1, rowEnd, preview.rows().size());
            guiGraphics.drawString(this.font, Component.literal(range), left, y, MUTED_TEXT, false);
        }
    }

    private void drawCurrentField(GuiGraphics guiGraphics, int left, int top, int width) {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        String label = field == null
                ? tr("gui.codpattern.zombies.deploy.field_value")
                : tr("gui.codpattern.zombies.deploy.field_value") + ": " + tr(field.labelKey());
        if (field != null && field.type() == ZombiesDeployFieldSchema.FieldType.LIST) {
            List<String> rows = listRows(field.key(), draftFields.getOrDefault(field.key(), field.value()));
            String row = rows.isEmpty() ? "0/0" : (clampIndex(this.selectedListRowIndex, rows.size()) + 1) + "/" + rows.size();
            label = label + "  " + tr("gui.codpattern.zombies.deploy.row") + " " + row;
        }
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(label, width)), left, top, LABEL_TEXT, false);
    }

    private boolean isCurrentListField() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        return field != null && field.type() == ZombiesDeployFieldSchema.FieldType.LIST;
    }

    private String currentFieldEditorValue(ZombiesDeploySnapshot.FieldValue field) {
        if (field == null) {
            return "";
        }
        if (field.type() == ZombiesDeployFieldSchema.FieldType.LIST) {
            return currentListValueWithEditorRow(field);
        }
        if (this.fieldValueBox != null && field.editable()) {
            return this.fieldValueBox.getValue();
        }
        return this.draftFields.getOrDefault(field.key(), field.value());
    }

    private String currentListValueWithEditorRow(ZombiesDeploySnapshot.FieldValue field) {
        String previousValue = this.draftFields.getOrDefault(field.key(), field.value());
        if (field.type() != ZombiesDeployFieldSchema.FieldType.LIST || this.fieldValueBox == null || !field.editable()) {
            return previousValue;
        }
        List<String> rows = new ArrayList<>(listRows(field.key(), previousValue));
        String rowValue = this.fieldValueBox.getValue().trim();
        if (rows.isEmpty()) {
            return rowValue.isEmpty() ? previousValue : serializeListRows(field.key(), List.of(rowValue), previousValue);
        }
        rows.set(clampIndex(this.selectedListRowIndex, rows.size()), rowValue);
        return serializeListRows(field.key(), rows, previousValue);
    }

    private String listFieldSummary(String fieldKey, String value) {
        ListFieldPreview preview = listPreview(fieldKey, value);
        String prefix = ta("gui.codpattern.zombies.deploy.rows_count", preview.rows().size());
        if (preview.hasErrors()) {
            return prefix + "  " + tr("gui.codpattern.zombies.deploy.error");
        }
        if (preview.hasWarnings()) {
            return prefix + "  " + tr("gui.codpattern.zombies.deploy.warning");
        }
        return prefix + "  " + tr("gui.codpattern.zombies.deploy.ok");
    }

    private ListFieldPreview listPreview(String fieldKey, String value) {
        return switch (fieldKey) {
            case "refreshWaves" -> previewRefreshWaves(value);
            case "rarityPools" -> previewRarityPools(value);
            case "weapons" -> previewWeapons(value);
            case "pricesByWeaponLevel" -> previewIntegerMap(value, tr("gui.codpattern.zombies.deploy.hint.prices"), tr("gui.codpattern.zombies.deploy.level"), tr("gui.codpattern.zombies.deploy.cost"));
            case "levels" -> previewUltimateLevels(value);
            default -> previewGenericList(value);
        };
    }

    private ListFieldPreview previewRefreshWaves(String value) {
        List<String> entries = splitLooseEntries(value);
        List<String> rows = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        Set<Integer> duplicates = new LinkedHashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            Integer wave = parseInteger(entry);
            if (wave == null || wave < 1) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.wave_invalid", i + 1));
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            if (!seen.add(wave)) {
                duplicates.add(wave);
            }
            rows.add("#" + (i + 1) + "  wave " + wave);
        }
        if (!duplicates.isEmpty()) {
            issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.duplicate_waves", joinIntegers(duplicates)));
        }
        return new ListFieldPreview(
                tr("gui.codpattern.zombies.deploy.hint.refresh_waves"),
                rows,
                issues,
                containsError(issues),
                containsWarning(issues));
    }

    private ListFieldPreview previewRarityPools(String value) {
        List<String> entries = splitRows(value);
        List<String> rows = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.rarity_format", i + 1));
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            String id = entry.substring(0, equals).trim();
            String[] parts = entry.substring(equals + 1).split(",");
            if (id.isEmpty() || parts.length != 3) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.rarity_format", i + 1));
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            Integer rank = parseInteger(parts[0]);
            Double baseWeight = parseFiniteDouble(parts[1]);
            Double waveFactor = parseFiniteDouble(parts[2]);
            if (rank == null || baseWeight == null || waveFactor == null) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.rarity_numeric", i + 1));
            } else {
                if (!ids.add(id)) {
                    issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.duplicate_rarity", i + 1, id));
                }
                if (!ranks.add(rank)) {
                    issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.duplicate_rank", i + 1, rank));
                }
                rows.add("#" + (i + 1) + "  " + id + "  rank " + rank + "  base " + baseWeight + "  wave " + waveFactor);
                continue;
            }
            rows.add("#" + (i + 1) + "  " + entry);
        }
        return new ListFieldPreview(
                tr("gui.codpattern.zombies.deploy.hint.rarity_pools"),
                rows,
                issues,
                containsError(issues),
                containsWarning(issues));
    }

    private ListFieldPreview previewWeapons(String value) {
        List<String> entries = splitRows(value);
        List<String> rows = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Set<String> rarityIds = rarityIdsFromDraft();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            int separator = entry.indexOf('|');
            if (separator <= 0 || separator == entry.length() - 1) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.weapon_format", i + 1));
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            String gunId = entry.substring(0, separator).trim();
            List<String> weights = splitLooseEntries(entry.substring(separator + 1));
            if (gunId.isEmpty() || weights.isEmpty()) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.weapon_required", i + 1));
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            List<String> parsedWeights = new ArrayList<>();
            for (String weightEntry : weights) {
                int equals = weightEntry.indexOf('=');
                if (equals <= 0 || equals == weightEntry.length() - 1) {
                    issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.weight_format", i + 1));
                    continue;
                }
                String rarity = weightEntry.substring(0, equals).trim();
                Double weight = parseFiniteDouble(weightEntry.substring(equals + 1));
                if (rarity.isEmpty() || weight == null || weight <= 0.0D) {
                    issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.weight_format", i + 1));
                    continue;
                }
                if (!rarityIds.isEmpty() && !rarityIds.contains(rarity)) {
                    issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.unknown_rarity", i + 1, rarity));
                }
                parsedWeights.add(rarity + "=" + weight);
            }
            rows.add("#" + (i + 1) + "  " + gunId + "  " + String.join(", ", parsedWeights));
        }
        return new ListFieldPreview(
                tr("gui.codpattern.zombies.deploy.hint.weapons"),
                rows,
                issues,
                containsError(issues),
                containsWarning(issues));
    }

    private ListFieldPreview previewIntegerMap(String value, String hint, String keyLabel, String valueLabel) {
        List<String> entries = splitLooseEntries(value);
        List<String> rows = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.integer_map_format", i + 1, keyLabel, valueLabel));
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            String key = entry.substring(0, equals).trim();
            Integer parsedKey = parseInteger(key);
            Integer parsedValue = parseInteger(entry.substring(equals + 1));
            if (parsedKey == null || parsedKey < 1 || parsedValue == null || parsedValue < 0) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.integer_map_range", i + 1, keyLabel, valueLabel));
            }
            if (!keys.add(key)) {
                issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.duplicate_key", i + 1, keyLabel, key));
            }
            rows.add("#" + (i + 1) + "  " + keyLabel + " " + key + " -> " + valueLabel + " " + entry.substring(equals + 1).trim());
        }
        return new ListFieldPreview(hint, rows, issues, containsError(issues), containsWarning(issues));
    }

    private ListFieldPreview previewUltimateLevels(String value) {
        List<String> entries = splitLooseEntries(value);
        List<String> rows = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Set<Integer> levels = new LinkedHashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            int equals = entry.indexOf('=');
            int colon = entry.indexOf(':', equals + 1);
            if (equals <= 0 || colon <= equals + 1 || colon == entry.length() - 1) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.ultimate_format", i + 1));
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            String levelText = entry.substring(0, equals).trim();
            String costText = entry.substring(equals + 1, colon).trim();
            String multiplierText = entry.substring(colon + 1).trim();
            Integer level = parseInteger(levelText);
            Integer cost = parseInteger(costText);
            Double multiplier = parseFiniteDouble(multiplierText);
            if (level == null || level < 1 || cost == null || cost < 0 || multiplier == null || multiplier <= 0.0D) {
                issues.add(errorIssue("gui.codpattern.zombies.deploy.issue.ultimate_range", i + 1));
            } else if (!levels.add(level)) {
                issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.duplicate_level", i + 1, level));
            }
            rows.add("#" + (i + 1) + "  level " + levelText + " -> cost " + costText + "  x" + multiplierText);
        }

        Integer maxLevel = parseInteger(this.draftFields.getOrDefault("maxUpgradeLevel", ""));
        if (maxLevel != null && maxLevel > 0 && !containsError(issues)) {
            for (int level = 1; level <= maxLevel; level++) {
                if (!levels.contains(level)) {
                    issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.missing_level", level, maxLevel));
                    break;
                }
            }
            for (Integer level : levels) {
                if (level > maxLevel) {
                    issues.add(warningIssue("gui.codpattern.zombies.deploy.issue.level_above_max", level, maxLevel));
                    break;
                }
            }
        }
        return new ListFieldPreview(
                tr("gui.codpattern.zombies.deploy.hint.ultimate_levels"),
                rows,
                issues,
                containsError(issues),
                containsWarning(issues));
    }

    private ListFieldPreview previewGenericList(String value) {
        List<String> entries = splitRows(value);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            rows.add("#" + (i + 1) + "  " + entries.get(i));
        }
        return new ListFieldPreview(
                tr("gui.codpattern.zombies.deploy.hint.generic_list"),
                rows,
                List.of(),
                false,
                false);
    }

    private List<String> splitLooseEntries(String value) {
        return splitByPattern(value, "[,;\\n\\r]+");
    }

    private List<String> splitRows(String value) {
        return splitByPattern(value, "[;\\n\\r]+");
    }

    private List<String> listRows(String fieldKey, String value) {
        return usesLooseListRows(fieldKey) ? splitLooseEntries(value) : splitRows(value);
    }

    private boolean usesLooseListRows(String fieldKey) {
        return "refreshWaves".equals(fieldKey)
                || "pricesByWeaponLevel".equals(fieldKey)
                || "levels".equals(fieldKey);
    }

    private String serializeListRows(String fieldKey, List<String> rows, String previousValue) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<String> normalized = new ArrayList<>();
        for (String row : rows) {
            String trimmed = Objects.requireNonNullElse(row, "").trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return String.join(listRowDelimiter(fieldKey, previousValue), normalized);
    }

    private String listRowDelimiter(String fieldKey, String previousValue) {
        String value = Objects.requireNonNullElse(previousValue, "");
        if (value.contains("\n") || value.contains("\r")) {
            return "\n";
        }
        if (usesLooseListRows(fieldKey)) {
            return value.contains(";") && !value.contains(",") ? ";" : ",";
        }
        return ";";
    }

    private List<String> splitByPattern(String value, String pattern) {
        String text = value == null ? "" : value;
        if (text.isBlank()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String part : text.split(pattern)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    private Set<String> rarityIdsFromDraft() {
        Set<String> ids = new HashSet<>();
        for (String row : splitRows(this.draftFields.getOrDefault("rarityPools", ""))) {
            int equals = row.indexOf('=');
            if (equals > 0) {
                String id = row.substring(0, equals).trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(Objects.requireNonNullElse(value, "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseFiniteDouble(String value) {
        try {
            double parsed = Double.parseDouble(Objects.requireNonNullElse(value, "").trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean containsError(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.startsWith("E|"));
    }

    private boolean containsWarning(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.startsWith("W|"));
    }

    private String errorIssue(String key, Object... args) {
        return "E|" + ta(key, args);
    }

    private String warningIssue(String key, Object... args) {
        return "W|" + ta(key, args);
    }

    private String issueText(String issue) {
        if (issue == null) {
            return "";
        }
        return issue.startsWith("E|") || issue.startsWith("W|") ? issue.substring(2) : issue;
    }

    private String statusLine(ListFieldPreview preview) {
        String count = ta("gui.codpattern.zombies.deploy.parsed_rows", preview.rows().size());
        if (preview.hasErrors()) {
            return tr("gui.codpattern.zombies.deploy.error") + ": " + count;
        }
        if (preview.hasWarnings()) {
            return tr("gui.codpattern.zombies.deploy.warning") + ": " + count;
        }
        return tr("gui.codpattern.zombies.deploy.ok") + ": " + count;
    }

    private String joinIntegers(Set<Integer> values) {
        List<String> parts = new ArrayList<>();
        for (Integer value : values) {
            parts.add(Integer.toString(value));
        }
        return String.join(", ", parts);
    }

    private void drawStatus(GuiGraphics guiGraphics, int left, int top, int width) {
        List<String> parts = new ArrayList<>();
        if (!snapshot.statusCode().isBlank()) {
            parts.add(snapshot.statusCode());
        }
        if (!snapshot.statusDetail().isBlank()) {
            parts.add(snapshot.statusDetail());
        }
        if (parts.isEmpty() && !snapshot.statusKey().isBlank()) {
            parts.add(tr(snapshot.statusKey()));
        }
        String status = parts.isEmpty() ? tr("gui.codpattern.zombies.deploy.ready") : String.join(" ", parts);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(tr("gui.codpattern.zombies.deploy.status") + ": " + status, width)), left, top, INFO_TEXT, false);
    }

    private int colorForSeverity(String severity) {
        String normalized = severity == null ? "" : severity.toLowerCase(Locale.ROOT);
        if ("error".equals(normalized)) {
            return ERROR_TEXT;
        }
        if ("warning".equals(normalized)) {
            return WARNING_TEXT;
        }
        return INFO_TEXT;
    }

    private int visibleObjectStart() {
        int count = snapshot.objects().size();
        if (count <= visibleObjectCount()) {
            return 0;
        }
        int position = selectedObjectListPosition();
        return Math.max(0, Math.min(position - visibleObjectCount() / 2, count - visibleObjectCount()));
    }

    private int visibleFieldStart() {
        int count = snapshot.fields().size();
        if (count <= visibleFieldCount()) {
            return 0;
        }
        return Math.max(0, Math.min(selectedFieldIndex - visibleFieldCount() / 2, count - visibleFieldCount()));
    }

    private int visibleListRowStart(int rowCount, int rowLimit) {
        if (rowCount <= rowLimit) {
            return 0;
        }
        int selected = clampIndex(this.selectedListRowIndex, rowCount);
        return Math.max(0, Math.min(selected - rowLimit / 2, rowCount - rowLimit));
    }

    private int visibleObjectCount() {
        return 10;
    }

    private int visibleFieldCount() {
        return 10;
    }

    private int selectedObjectListPosition() {
        for (int i = 0; i < snapshot.objects().size(); i++) {
            if (snapshot.objects().get(i).index() == selectedIndex) {
                return i;
            }
        }
        return 0;
    }

    private int listIndexAt(double mouseX, double mouseY, int left, int top, int visibleCount) {
        if (mouseX < left - 3 || mouseX > left + 270 || mouseY < top - 2) {
            return -1;
        }
        int row = (int) ((mouseY - top) / 22);
        return row >= 0 && row < visibleCount ? row : -1;
    }

    private int listRowIndexAt(double mouseX, double mouseY, int left, int top, int width) {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST) {
            return -1;
        }
        if (mouseX < left - 4 || mouseX > left + width || mouseY < top - 4 || mouseY > top + 96) {
            return -1;
        }
        ListFieldPreview preview = listPreview(field.key(), currentListValueWithEditorRow(field));
        int y = top + 38 + Math.min(2, preview.issues().size()) * 10;
        int rowLimit = Math.max(1, Math.min(5, (top + 90 - y) / 10));
        int rowStart = visibleListRowStart(preview.rows().size(), rowLimit);
        int visibleRows = Math.min(rowLimit, preview.rows().size() - rowStart);
        if (visibleRows <= 0 || mouseY < y || mouseY >= y + visibleRows * 10) {
            return -1;
        }
        int row = rowStart + (int) ((mouseY - y) / 10);
        return row >= 0 && row < preview.rows().size() ? row : -1;
    }

    private void selectVisibleObject(int visibleRow) {
        int index = visibleObjectStart() + visibleRow;
        if (index < 0 || index >= snapshot.objects().size()) {
            return;
        }
        this.selectedIndex = snapshot.objects().get(index).index();
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_OBJECT);
    }

    private void selectVisibleField(int visibleRow) {
        int index = visibleFieldStart() + visibleRow;
        if (index < 0 || index >= snapshot.fields().size()) {
            return;
        }
        this.selectedFieldIndex = index;
        updateWidgets();
    }

    private void selectListRow(int rowIndex) {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST) {
            return;
        }
        List<String> rows = listRows(field.key(), draftFields.getOrDefault(field.key(), field.value()));
        this.selectedListRowIndex = clampIndex(rowIndex, rows.size());
        updateWidgets();
    }

    private ZombiesDeploySnapshot.FieldValue currentField() {
        if (snapshot.fields().isEmpty() || selectedFieldIndex < 0 || selectedFieldIndex >= snapshot.fields().size()) {
            return null;
        }
        return snapshot.fields().get(selectedFieldIndex);
    }

    private String currentFieldKey() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        return field == null ? "" : field.key();
    }

    private void restoreFieldSelection(String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            this.selectedFieldIndex = clampIndex(this.selectedFieldIndex, snapshot.fields().size());
            return;
        }
        for (int i = 0; i < snapshot.fields().size(); i++) {
            if (fieldKey.equals(snapshot.fields().get(i).key())) {
                this.selectedFieldIndex = i;
                return;
            }
        }
        this.selectedFieldIndex = clampIndex(this.selectedFieldIndex, snapshot.fields().size());
    }

    private int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, size - 1));
    }

    private String labelOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String objectTypeLabel(String objectType) {
        return ZombiesDeployFieldSchema.objectType(objectType)
                .map(type -> tr(type.labelKey()))
                .orElse(labelOrDash(objectType));
    }

    private String stageLabel(String stage) {
        return ZombiesDeployDraft.STAGE_OBJECT_MARKING.equals(stage) ? "功能点标注" : "地图注册";
    }

    private String capturePresetLabel(String preset) {
        if (ZombiesDeployDraft.CAPTURE_BARRIER_INTERACTION.equals(preset)) {
            return "屏障交互";
        }
        if (ZombiesDeployDraft.CAPTURE_BARRIER_AREA.equals(preset)) {
            return "屏障范围";
        }
        return "默认捕获";
    }

    private String formatPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private String ta(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String trimToWidth(String value, int width) {
        String text = value == null ? "" : value;
        if (this.font.width(text) <= width) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0 && this.font.width(text.substring(0, end) + suffix) > width) {
            end--;
        }
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }

    private int panelLeft() {
        return Math.max(8, (this.width - PANEL_WIDTH) / 2);
    }

    private int panelTop() {
        return Math.max(8, (this.height - PANEL_HEIGHT) / 2);
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        return mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= top && mouseY < top + PANEL_HEIGHT;
    }

    private BlockPos pickBlockPos(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eyePosition = camera.getPosition();
        Vec3 direction = getRayDirection(camera, mouseX, mouseY);
        double reach = minecraft.player.getBlockReach();
        BlockHitResult hitResult = minecraft.level.clip(new ClipContext(
                eyePosition,
                eyePosition.add(direction.scale(reach)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));
        return hitResult.getType() == HitResult.Type.BLOCK ? hitResult.getBlockPos() : null;
    }

    private Vec3 getRayDirection(Camera camera, double mouseX, double mouseY) {
        double normalizedX = mouseX / (double) this.width * 2.0D - 1.0D;
        double normalizedY = 1.0D - mouseY / (double) this.height * 2.0D;
        double aspect = (double) this.width / (double) this.height;
        double tanHalfFov = Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2.0D);
        double horizontalScale = normalizedX * aspect * tanHalfFov;
        double verticalScale = normalizedY * tanHalfFov;

        Vec3 look = toVec3(camera.getLookVector());
        Vec3 up = toVec3(camera.getUpVector());
        Vec3 left = toVec3(camera.getLeftVector());
        return look.add(left.scale(-horizontalScale)).add(up.scale(verticalScale)).normalize();
    }

    private static Vec3 toVec3(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private record ListFieldPreview(
            String hint,
            List<String> rows,
            List<String> issues,
            boolean hasErrors,
            boolean hasWarnings
    ) {
        private ListFieldPreview {
            hint = Objects.requireNonNullElse(hint, "");
            rows = rows == null ? List.of() : List.copyOf(rows);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}
