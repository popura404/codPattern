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
import java.util.Comparator;
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
    private static final int SLOT_A_LEGEND = 0xFF4EE0B5;
    private static final int SLOT_B_LEGEND = 0xFFFFAD5A;
    private static final int CONTROL_ROW_Y = 332;
    private static final int FIELD_LABEL_Y = 354;
    private static final int FIELD_INPUT_Y = 368;
    private static final int BOTTOM_ROW_Y = 396;
    private static final int LEFT_COLUMN_X = 16;
    private static final int LEFT_COLUMN_Y = 84;
    private static final int LEFT_COLUMN_WIDTH = 154;
    private static final int CENTER_X = 178;
    private static final int CENTER_WIDTH = 340;
    private static final int RIGHT_X = 528;
    private static final int RIGHT_WIDTH = 280;
    private static final List<String> INTERACT_DEFAULT_ORDER = List.of(
            ZombiesDeployFieldSchema.WEAPON_WALL,
            ZombiesDeployFieldSchema.AMMO_BOX,
            ZombiesDeployFieldSchema.ARMOR_STATION,
            ZombiesDeployFieldSchema.SODA_MACHINE,
            ZombiesDeployFieldSchema.ULTIMATE_MACHINE,
            ZombiesDeployFieldSchema.POWER_SWITCH
    );

    private ZombiesDeploySnapshot snapshot;
    private String workspaceStage;
    private String workflowStep;
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
    private Button typeButton;
    private final List<Button> objectTypeButtons = new ArrayList<>();
    private Button profileButton;
    private Button nextStepButton;
    private Button saveValidateButton;
    private Button newDraftButton;
    private Button saveObjectButton;
    private Button listRowInsertButton;
    private Button listRowDeleteButton;
    private Button deleteObjectButton;
    private Button createMapButton;
    private EditBox fieldValueBox;
    private EditBox mapNameBox;
    private boolean fieldValueBoxWasFocused;

    public ZombiesDeployToolScreen(OpenZombiesDeployToolScreenS2CPacket packet) {
        super(Component.translatable("gui.codpattern.zombies.deploy.title"));
        applySnapshot(packet.snapshot());
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();
        this.objectTypeButtons.clear();

        this.mapButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> {
        })
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
        this.nextStepButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> goNextStep())
                .pos(left + 468, top + 30)
                .size(116, 20)
                .build());
        this.mapNameBox = this.addRenderableWidget(new EditBox(this.font, left + 16, top + 56, 148, 18, Component.empty()));
        this.mapNameBox.setMaxLength(64);
        this.createMapButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.create_map"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.CREATE_MAP))
                .pos(left + 170, top + 55)
                .size(78, 20)
                .build());
        this.profileButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleProfile())
                .pos(left + RIGHT_X + 174, top + 302)
                .size(96, 20)
                .build());
        this.saveValidateButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.validate"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.VALIDATE_MAP))
                .pos(left + RIGHT_X, top + 326)
                .size(270, 20)
                .build());

        for (int i = 0; i < this.snapshot.objectTypes().size(); i++) {
            ZombiesDeploySnapshot.ObjectTypeOption option = this.snapshot.objectTypes().get(i);
            int column = i % 2;
            int row = i / 2;
            Button button = this.addRenderableWidget(new Button.Builder(Component.empty(), ignored -> selectObjectType(option.key()))
                    .pos(left + LEFT_COLUMN_X + 6 + column * 70, top + 204 + row * 20)
                    .size(66, 18)
                    .build());
            this.objectTypeButtons.add(button);
        }

        this.newDraftButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.new_draft"), button -> newDraft())
                .pos(left + CENTER_X, top + CONTROL_ROW_Y)
                .size(84, 20)
                .build());
        this.saveObjectButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.add"), button -> saveCurrentObject())
                .pos(left + CENTER_X + 88, top + CONTROL_ROW_Y)
                .size(96, 20)
                .build());
        this.deleteObjectButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.delete"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.DELETE_OBJECT))
                .pos(left + CENTER_X + 188, top + CONTROL_ROW_Y)
                .size(72, 20)
                .build());

        this.fieldValueBox = this.addRenderableWidget(new EditBox(this.font, left + CENTER_X, top + FIELD_INPUT_Y, CENTER_WIDTH, 20, Component.empty()));
        this.fieldValueBox.setMaxLength(2048);

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.close"), button -> onClose())
                .pos(left + 696, top + BOTTOM_ROW_Y)
                .size(62, 20)
                .build());
        this.listRowInsertButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.add"), button -> insertListRowAtEnd())
                .pos(left + CENTER_X, top + BOTTOM_ROW_Y)
                .size(64, 20)
                .build());
        this.listRowDeleteButton = this.addRenderableWidget(new Button.Builder(Component.translatable("gui.codpattern.zombies.deploy.remove"), button -> deleteCurrentListRow())
                .pos(left + CENTER_X + 68, top + BOTTOM_ROW_Y)
                .size(64, 20)
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
            boolean focused = this.fieldValueBox.isFocused();
            if (this.fieldValueBoxWasFocused && !focused) {
                setCurrentField();
            }
            this.fieldValueBoxWasFocused = focused;
        } else {
            this.fieldValueBoxWasFocused = false;
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

        drawSection(guiGraphics, left + LEFT_COLUMN_X - 4, top + LEFT_COLUMN_Y - 2, LEFT_COLUMN_WIDTH, 240, tr("gui.codpattern.zombies.deploy.section.workflow"));
        drawWorkflowAndTypes(guiGraphics, left + LEFT_COLUMN_X + 2, top + LEFT_COLUMN_Y + 18, LEFT_COLUMN_WIDTH - 10);

        drawSection(guiGraphics, left + CENTER_X - 4, top + 82, CENTER_WIDTH + 8, 118, tr("gui.codpattern.zombies.deploy.section.objects_draft"));
        drawObjectDraftSummary(guiGraphics, left + CENTER_X + 6, top + 100, CENTER_WIDTH - 12);
        drawObjects(guiGraphics, left + CENTER_X + 6, top + 136, CENTER_WIDTH - 12);

        drawSection(guiGraphics, left + CENTER_X - 4, top + 206, CENTER_WIDTH + 8, 118, tr("gui.codpattern.zombies.deploy.section.properties"));
        drawFields(guiGraphics, left + CENTER_X + 6, top + 228, CENTER_WIDTH - 12);

        drawSection(guiGraphics, left + RIGHT_X, top + 82, RIGHT_WIDTH, 242, tr("gui.codpattern.zombies.deploy.section.validation_status"));
        boolean showListPreview = isCurrentListField();
        drawValidation(guiGraphics, left + RIGHT_X + 6, top + 104, RIGHT_WIDTH - 12, showListPreview ? 5 : 14);
        if (showListPreview) {
            drawListPreview(guiGraphics, left + RIGHT_X + 6, top + 220, RIGHT_WIDTH - 12);
        }
        drawValidationProfileLabel(guiGraphics, left + RIGHT_X + 6, top + 306, 164);

        drawStatusBar(guiGraphics, left + RIGHT_X, top + 350, RIGHT_WIDTH);
        drawCurrentField(guiGraphics, left + CENTER_X, top + FIELD_LABEL_Y, CENTER_WIDTH);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean fieldFocusedBefore = this.fieldValueBox != null && this.fieldValueBox.isFocused();
        if (super.mouseClicked(mouseX, mouseY, button)) {
            commitFieldEditorOnBlur(fieldFocusedBefore);
            return true;
        }
        blurAndCommitFieldEditorIfNeeded(mouseX, mouseY);
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
        int objectIndex = listIndexAt(mouseX, mouseY, left + CENTER_X + 6, top + 136, visibleObjectCount(), CENTER_WIDTH - 12);
        if (objectIndex >= 0) {
            selectVisibleObject(objectIndex);
            return true;
        }

        int mapIndex = mapIndexAt(mouseX, mouseY, left + LEFT_COLUMN_X + 2, top + LEFT_COLUMN_Y + 18, LEFT_COLUMN_WIDTH - 10);
        if (mapIndex >= 0) {
            selectVisibleMap(mapIndex);
            return true;
        }

        int stepIndex = workflowStepIndexAt(mouseX, mouseY, left + LEFT_COLUMN_X + 2, top + LEFT_COLUMN_Y + 18, LEFT_COLUMN_WIDTH - 10);
        if (stepIndex >= 0) {
            selectWorkflowStep(stepIndex);
            return true;
        }

        boolean showListPreview = isCurrentListField();
        int issueIndex = validationIssueIndexAt(
                mouseX,
                mouseY,
                left + RIGHT_X + 6,
                top + 104,
                RIGHT_WIDTH - 12,
                showListPreview ? 5 : 14);
        if (issueIndex >= 0) {
            jumpToValidationIssue(issueIndex);
            return true;
        }

        int listRowIndex = listRowIndexAt(mouseX, mouseY, left + RIGHT_X + 6, top + 220, RIGHT_WIDTH - 12);
        if (listRowIndex >= 0) {
            selectListRow(listRowIndex);
            return true;
        }

        int fieldIndex = listIndexAt(mouseX, mouseY, left + CENTER_X + 6, top + 228, visibleFieldCount(), CENTER_WIDTH - 12);
        if (fieldIndex >= 0) {
            selectVisibleField(fieldIndex);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean mapNameFocused = this.mapNameBox != null && this.mapNameBox.isFocused();
        if (mapNameFocused && this.mapNameBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (mapNameFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
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
            saveCurrentObject();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.mapNameBox != null && this.mapNameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
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
        this.workflowStep = this.snapshot.currentWorkflowStep();
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
                ZombiesDeployDraft.WORKFLOW_MAP,
                ZombiesDeployDraft.WORKFLOW_INITIAL,
                "",
                "gui.codpattern.zombies.deploy.next_step",
                false,
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
                List.of(),
                false,
                "",
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
        this.mapButton.active = false;

        this.stageButton.setMessage(Component.literal(stageLabel(this.workspaceStage)));

        this.typeButton.setMessage(Component.literal(trimButton(ta("gui.codpattern.zombies.deploy.current_type", labelOrDash(objectTypeLabel(this.selectedObjectType))), 148)));
        this.typeButton.active = false;
        for (int i = 0; i < this.objectTypeButtons.size() && i < snapshot.objectTypes().size(); i++) {
            ZombiesDeploySnapshot.ObjectTypeOption option = snapshot.objectTypes().get(i);
            int count = objectCount(option.key());
            String marker = option.key().equals(this.selectedObjectType) ? "> " : "";
            this.objectTypeButtons.get(i).setMessage(Component.literal(trimButton(marker + shortObjectTypeLabel(option.key()) + " " + count, 60)));
        }

        this.profileButton.setMessage(Component.literal(ta("gui.codpattern.zombies.deploy.view_profile", profileShortLabel(this.selectedProfile))));
        this.profileButton.active = snapshot.availableProfiles().size() > 1;
        this.saveValidateButton.active = !this.selectedMap.isBlank();
        String nextActionLabel = snapshot.nextActionLabel().isBlank()
                ? tr("gui.codpattern.zombies.deploy.next_step")
                : tr(snapshot.nextActionLabel());
        this.nextStepButton.setMessage(Component.literal(trimButton(nextActionLabel, 110)));
        this.nextStepButton.active = snapshot.nextActionEnabled();

        if (this.mapNameBox != null && !this.mapNameBox.isFocused() && !Objects.equals(this.mapNameBox.getValue(), this.draftMapName)) {
            this.mapNameBox.setValue(this.draftMapName);
        }
        boolean inMapStage = ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage);
        this.createMapButton.active = inMapStage && this.mapPos1 != null && this.mapPos2 != null;

        boolean hasObjects = !snapshot.objects().isEmpty();
        this.newDraftButton.active = !inMapStage;
        this.saveObjectButton.active = !inMapStage && !this.selectedMap.isBlank();
        this.saveObjectButton.setMessage(Component.translatable(this.selectedIndex < 0
                ? "gui.codpattern.zombies.deploy.add"
                : "gui.codpattern.zombies.deploy.save_object"));
        this.deleteObjectButton.active = !inMapStage && hasObjects && this.selectedIndex >= 0;

        ensureVisibleFieldSelected();
        ZombiesDeploySnapshot.FieldValue field = currentField();
        boolean listField = field != null && field.type() == ZombiesDeployFieldSchema.FieldType.LIST;
        boolean editableField = field != null && field.editable();
        int listRowCount = listField ? listRows(field.key(), draftFields.getOrDefault(field.key(), field.value())).size() : 0;
        this.selectedListRowIndex = clampIndex(this.selectedListRowIndex, listRowCount);

        if (this.listRowInsertButton != null) {
            this.listRowInsertButton.active = listField && editableField;
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
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
    }

    private void selectObjectType(String objectType) {
        String normalized = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        if (normalized.equals(this.selectedObjectType)) {
            return;
        }
        this.selectedObjectType = normalized;
        this.selectedIndex = -1;
        this.selectedFieldIndex = 0;
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
    }

    private void cycleWorkspaceStage() {
        this.workspaceStage = ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage)
                ? ZombiesDeployDraft.STAGE_OBJECT_MARKING
                : ZombiesDeployDraft.STAGE_MAP_REGISTRATION;
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_WORKSPACE_STAGE);
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

    private void goNextStep() {
        applyWorkflowStep(snapshot.nextWorkflowStep());
    }

    private void newDraft() {
        this.selectedIndex = -1;
        this.selectedFieldIndex = 0;
        this.selectedListFieldKey = "";
        this.selectedListRowIndex = 0;
        this.draftFields.clear();
        FPSMatch.sendToServer(new ZombiesDeployToolActionC2SPacket(
                ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS,
                draftFromFields(Map.of())));
    }

    private void saveCurrentObject() {
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage) || this.selectedMap.isBlank()) {
            return;
        }
        sendAction(this.selectedIndex < 0
                ? ZombiesDeployToolActionC2SPacket.Action.ADD_OBJECT
                : ZombiesDeployToolActionC2SPacket.Action.UPDATE_OBJECT);
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
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
    }

    private void stepField(int offset) {
        List<Integer> order = fieldDisplayOrder();
        if (order.isEmpty()) {
            this.selectedFieldIndex = 0;
            updateWidgets();
            return;
        }
        int position = order.indexOf(this.selectedFieldIndex);
        if (position < 0) {
            position = 0;
        }
        int next = Math.max(0, Math.min(position + offset, order.size() - 1));
        this.selectedFieldIndex = order.get(next);
        updateWidgets();
    }

    private void setCurrentField() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || !field.editable() || this.fieldValueBox == null) {
            return;
        }
        if (field.type() == ZombiesDeployFieldSchema.FieldType.LIST) {
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
                this.workflowStep,
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

    private void insertListRowAtEnd() {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field == null || field.type() != ZombiesDeployFieldSchema.FieldType.LIST || !field.editable() || this.fieldValueBox == null) {
            return;
        }
        String previousValue = draftFields.getOrDefault(field.key(), field.value());
        List<String> rows = new ArrayList<>(listRows(field.key(), previousValue));
        rows.add(this.fieldValueBox.getValue().trim());
        this.selectedListRowIndex = rows.size() - 1;
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
        String binding = ta("gui.codpattern.zombies.deploy.binding", captureSlotLabel(snapshot.captureSlotA()), captureSlotLabel(snapshot.captureSlotB()));
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage)) {
            binding = ta("gui.codpattern.zombies.deploy.binding", "mapPos1", "mapPos2");
        }
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(binding, width)), left, top, INFO_TEXT, false);
        String mapLine = ta("gui.codpattern.zombies.deploy.map_area_ab", formatPos(this.mapPos1), formatPos(this.mapPos2));
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(mapLine, width)), left, top + 10, MUTED_TEXT, false);
        int y = top + 22;
        for (int i = 0; i < Math.min(2, snapshot.stepStatuses().size()); i++) {
            ZombiesDeploySnapshot.StepStatus status = snapshot.stepStatuses().get(i);
            int color = status.complete() ? INFO_TEXT : WARNING_TEXT;
            String line = stepLabel(status) + ": " + stepDetail(status);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(line, width)), left, y, color, false);
            y += 10;
        }
        if (!snapshot.blockingReason().isBlank()) {
            guiGraphics.drawString(
                    this.font,
                    Component.literal(trimToWidth(tr("gui.codpattern.zombies.deploy.blocking") + ": " + blockingReasonLabel(), width)),
                    left,
                    y,
                    WARNING_TEXT,
                    false);
        }
    }

    private void drawWorkflowAndTypes(GuiGraphics guiGraphics, int left, int top, int width) {
        int y = top;
        guiGraphics.drawString(this.font, Component.literal(ta("gui.codpattern.zombies.deploy.stage_line", stageLabel(this.workspaceStage))), left, y, LABEL_TEXT, false);
        y += 13;
        y = drawMapList(guiGraphics, left, y, width);
        y += 4;
        for (ZombiesDeploySnapshot.StepStatus status : snapshot.stepStatuses()) {
            if (status != null && status.key().equals(this.workflowStep)) {
                guiGraphics.fill(left - 3, y - 2, left + width, y + 9, HIGHLIGHT);
            }
            int color = status.complete() ? INFO_TEXT : WARNING_TEXT;
            String line = (status.complete()
                    ? tr("gui.codpattern.zombies.deploy.step_done")
                    : tr("gui.codpattern.zombies.deploy.step_missing"))
                    + " " + stepLabel(status) + " " + stepDetail(status);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(line, width)), left, y, color, false);
            y += 11;
        }
        y += 6;
        guiGraphics.drawString(this.font, Component.translatable("gui.codpattern.zombies.deploy.object_types"), left, y, LABEL_TEXT, false);
    }

    private int drawMapList(GuiGraphics guiGraphics, int left, int y, int width) {
        guiGraphics.drawString(this.font, Component.translatable("gui.codpattern.zombies.deploy.map"), left, y, LABEL_TEXT, false);
        y += 11;
        List<String> maps = snapshot.availableMaps();
        if (maps.isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("-"), left, y, MUTED_TEXT, false);
            return y + 11;
        }
        int start = visibleMapStart();
        int visible = visibleMapCount();
        for (int i = 0; i < visible; i++) {
            int mapIndex = start + i;
            String mapName = maps.get(mapIndex);
            if (mapName.equals(this.selectedMap)) {
                guiGraphics.fill(left - 3, y - 2, left + width, y + 9, HIGHLIGHT);
            }
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(mapName, width)), left, y, mapName.equals(this.selectedMap) ? INFO_TEXT : MUTED_TEXT, false);
            y += 11;
        }
        if (maps.size() > visible) {
            guiGraphics.drawString(this.font, Component.literal(ta("gui.codpattern.zombies.deploy.more", maps.size() - visible)), left, y, MUTED_TEXT, false);
            y += 11;
        }
        return y;
    }

    private void drawObjectDraftSummary(GuiGraphics guiGraphics, int left, int top, int width) {
        String selected = selectedIndex < 0
                ? tr("gui.codpattern.zombies.deploy.new_draft")
                : ta("gui.codpattern.zombies.deploy.object_index", selectedIndex);
        String dirty = draftDirty()
                ? tr("gui.codpattern.zombies.deploy.draft_unsaved")
                : tr("gui.codpattern.zombies.deploy.draft_synced");
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(objectTypeLabel(selectedObjectType) + " / " + selected, width)), left, top, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(dirty + "  " + captureBindingLabel(), width)), left, top + 12, draftDirty() ? WARNING_TEXT : INFO_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(formatNearestObjectHint(snapshot.nearestObjectHint()), width)), left, top + 24, MUTED_TEXT, false);
    }

    private void drawStatusBar(GuiGraphics guiGraphics, int left, int top, int width) {
        int height = 34;
        guiGraphics.fill(left, top, left + width, top + height, 0xA014181D);
        drawBorder(guiGraphics, left, top, width, height, 0xFF39424B);
        String saveState = draftDirty()
                ? tr("gui.codpattern.zombies.deploy.status.draft_unsaved")
                : (snapshot.activeMap()
                ? tr("gui.codpattern.zombies.deploy.status.saved_active")
                : tr("gui.codpattern.zombies.deploy.status.saved_ready"));
        String line = saveState + "  |  " + captureBindingLabel();
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(line, width - 10)), left + 5, top + 7, draftDirty() ? WARNING_TEXT : INFO_TEXT, false);

        int legendX = left + 5;
        int legendY = top + 20;
        String legendA = tr("gui.codpattern.zombies.deploy.legend.slot_a");
        String legendB = tr("gui.codpattern.zombies.deploy.legend.slot_b");
        guiGraphics.drawString(this.font, Component.literal(legendA), legendX, legendY, SLOT_A_LEGEND, false);
        legendX += this.font.width(legendA) + 10;
        guiGraphics.drawString(this.font, Component.literal(legendB), legendX, legendY, SLOT_B_LEGEND, false);
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
        List<Integer> order = fieldDisplayOrder();
        int start = visibleFieldStart();
        int end = Math.min(order.size(), start + visibleFieldCount());
        for (int i = start; i < end; i++) {
            int fieldIndex = order.get(i);
            ZombiesDeploySnapshot.FieldValue field = snapshot.fields().get(fieldIndex);
            int y = top + (i - start) * 22;
            if (fieldIndex == selectedFieldIndex) {
                guiGraphics.fill(left - 3, y - 2, left + width, y + 18, HIGHLIGHT);
            }
            String value = draftFields.getOrDefault(field.key(), field.value());
            String first = tr(field.labelKey());
            if ("yaw".equals(field.key())) {
                first = first + " (" + tr("gui.codpattern.zombies.deploy.yaw_only_short") + ")";
            }
            String second = field.type() == ZombiesDeployFieldSchema.FieldType.LIST
                    ? ta("gui.codpattern.zombies.deploy.rows_count", listRows(field.key(), value).size())
                    : (value.isBlank() ? tr("gui.codpattern.zombies.deploy.blank") : value);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(first, width)), left, y, field.editable() ? TEXT : MUTED_TEXT, false);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(second, width)), left, y + 10, MUTED_TEXT, false);
        }
    }

    private void drawValidationProfileLabel(GuiGraphics guiGraphics, int left, int top, int width) {
        String line = tr("gui.codpattern.zombies.deploy.validation_view") + ": " + profileShortLabel(this.selectedProfile);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(line, width)), left, top + 5, LABEL_TEXT, false);
    }

    private void drawValidation(GuiGraphics guiGraphics, int left, int top, int width, int maxLines) {
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(tr("gui.codpattern.zombies.deploy.map") + ": " + labelOrDash(selectedMap), width)), left, top, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(
                tr("gui.codpattern.zombies.deploy.active") + ": "
                        + (snapshot.activeMap() ? tr("gui.codpattern.zombies.deploy.yes") : tr("gui.codpattern.zombies.deploy.no"))
                        + "  " + tr("gui.codpattern.zombies.deploy.revision") + ": " + snapshot.revision(),
                width)), left, top + 12, LABEL_TEXT, false);
        int summaryY = top + 28;
        drawValidationCards(guiGraphics, left, summaryY, width);
        summaryY += 48;

        List<ZombiesDeploySnapshot.ValidationLine> lines = snapshot.validationLines();
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.codpattern.zombies.deploy.no_validation_issues"), left, summaryY + 2, INFO_TEXT, false);
            return;
        }
        int lineLimit = Math.max(1, maxLines - snapshot.validationSummaries().size());
        for (int i = 0; i < Math.min(lines.size(), lineLimit); i++) {
            ZombiesDeploySnapshot.ValidationLine line = lines.get(i);
            int y = summaryY + 2 + i * 12;
            String jumpText = "[" + tr("gui.codpattern.zombies.deploy.jump_to_issue_short") + "]";
            int jumpBoxWidth = this.font.width(jumpText) + 6;
            guiGraphics.fill(left, y - 1, left + jumpBoxWidth, y + 9, 0xFF2A5A38);
            drawBorder(guiGraphics, left, y - 1, jumpBoxWidth, 10, 0xFF69C07B);
            guiGraphics.drawString(this.font, Component.literal(jumpText), left + 3, y, TEXT, false);
            String text = "[" + line.severity() + "] "
                    + line.code() + " "
                    + line.subject() + " "
                    + line.message();
            guiGraphics.drawString(
                    this.font,
                    Component.literal(trimToWidth(text, Math.max(12, width - jumpBoxWidth - 4))),
                    left + jumpBoxWidth + 4,
                    y,
                    colorForSeverity(line.severity()),
                    false);
        }
        if (lines.size() > lineLimit) {
            guiGraphics.drawString(this.font, Component.literal(ta("gui.codpattern.zombies.deploy.more", lines.size() - lineLimit)), left, summaryY + 2 + lineLimit * 12, MUTED_TEXT, false);
        }
    }

    private void drawValidationCards(GuiGraphics guiGraphics, int left, int top, int width) {
        int cardWidth = Math.max(72, (width - 8) / 3);
        for (int i = 0; i < Math.min(3, snapshot.validationSummaries().size()); i++) {
            ZombiesDeploySnapshot.ValidationSummary summary = snapshot.validationSummaries().get(i);
            int x = left + i * (cardWidth + 4);
            int color = summary.errors() > 0 ? ERROR_TEXT : (summary.warnings() > 0 ? WARNING_TEXT : INFO_TEXT);
            guiGraphics.fill(x, top, x + cardWidth, top + 40, 0x8014181D);
            drawBorder(guiGraphics, x, top, cardWidth, 40, color);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(profileShortLabel(summary.profileKey()), cardWidth - 8)), x + 4, top + 5, LABEL_TEXT, false);
            guiGraphics.drawString(this.font, Component.literal("E" + summary.errors() + " W" + summary.warnings()), x + 4, top + 19, color, false);
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
        String status = tr("gui.codpattern.zombies.deploy.ready");
        if (!snapshot.statusKey().isBlank()) {
            status = tr(snapshot.statusKey());
        } else if (!snapshot.statusDetail().isBlank()) {
            status = snapshot.statusDetail();
        }
        if (!snapshot.statusDetail().isBlank() && !snapshot.statusDetail().equals(status)) {
            status = status + " " + snapshot.statusDetail();
        }
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
        List<Integer> order = fieldDisplayOrder();
        int count = order.size();
        if (count <= visibleFieldCount()) {
            return 0;
        }
        int position = order.indexOf(selectedFieldIndex);
        if (position < 0) {
            position = 0;
        }
        return Math.max(0, Math.min(position - visibleFieldCount() / 2, count - visibleFieldCount()));
    }

    private int visibleListRowStart(int rowCount, int rowLimit) {
        if (rowCount <= rowLimit) {
            return 0;
        }
        int selected = clampIndex(this.selectedListRowIndex, rowCount);
        return Math.max(0, Math.min(selected - rowLimit / 2, rowCount - rowLimit));
    }

    private int visibleObjectCount() {
        return 3;
    }

    private int visibleMapCount() {
        return Math.min(3, snapshot.availableMaps().size());
    }

    private int visibleMapStart() {
        List<String> maps = snapshot.availableMaps();
        int count = maps.size();
        int visible = visibleMapCount();
        if (count <= visible) {
            return 0;
        }
        int selectedPosition = maps.indexOf(this.selectedMap);
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        return Math.max(0, Math.min(selectedPosition - visible / 2, count - visible));
    }

    private int workflowStepStartY(int top) {
        int y = top + 13;
        y += 11;
        int visibleMaps = visibleMapCount();
        y += (visibleMaps <= 0 ? 1 : visibleMaps) * 11;
        if (snapshot.availableMaps().size() > visibleMaps) {
            y += 11;
        }
        y += 4;
        return y;
    }

    private int visibleFieldCount() {
        return 4;
    }

    private List<Integer> fieldDisplayOrder() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < snapshot.fields().size(); i++) {
            if ("pitch".equals(snapshot.fields().get(i).key())) {
                continue;
            }
            order.add(i);
        }
        order.sort(Comparator
                .comparingInt((Integer index) -> fieldPriority(snapshot.fields().get(index).key()))
                .thenComparingInt(Integer::intValue));
        return order;
    }

    private int fieldPriority(String fieldKey) {
        String key = Objects.requireNonNullElse(fieldKey, "");
        return switch (key) {
            case "objectId" -> 0;
            case "posX", "posY", "posZ" -> 1;
            case "interactionX", "interactionY", "interactionZ" -> 2;
            case "areaFromX", "areaFromY", "areaFromZ", "areaToX", "areaToY", "areaToZ" -> 3;
            case "group", "weight", "cost", "price", "weaponLevel", "armorLevel", "buyCost", "buffId", "requiresPower", "maxUpgradeLevel" -> 4;
            case "dimension", "yaw" -> 5;
            default -> 8;
        };
    }

    private void ensureVisibleFieldSelected() {
        List<Integer> order = fieldDisplayOrder();
        if (order.isEmpty()) {
            this.selectedFieldIndex = 0;
            return;
        }
        if (!order.contains(this.selectedFieldIndex)) {
            this.selectedFieldIndex = order.get(0);
        }
    }

    private int selectedObjectListPosition() {
        for (int i = 0; i < snapshot.objects().size(); i++) {
            if (snapshot.objects().get(i).index() == selectedIndex) {
                return i;
            }
        }
        return 0;
    }

    private int listIndexAt(double mouseX, double mouseY, int left, int top, int visibleCount, int width) {
        if (mouseX < left - 3 || mouseX > left + width || mouseY < top - 2) {
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

    private int mapIndexAt(double mouseX, double mouseY, int left, int top, int width) {
        if (mouseX < left - 3 || mouseX > left + width) {
            return -1;
        }
        List<String> maps = snapshot.availableMaps();
        if (maps.isEmpty()) {
            return -1;
        }
        int rowStartY = top + 24;
        int visible = visibleMapCount();
        if (mouseY < rowStartY || mouseY >= rowStartY + visible * 11) {
            return -1;
        }
        int row = (int) ((mouseY - rowStartY) / 11);
        int mapIndex = visibleMapStart() + row;
        return row >= 0 && row < visible && mapIndex < maps.size() ? mapIndex : -1;
    }

    private int workflowStepIndexAt(double mouseX, double mouseY, int left, int top, int width) {
        if (mouseX < left - 3 || mouseX > left + width) {
            return -1;
        }
        int rowStartY = workflowStepStartY(top);
        int rowCount = snapshot.stepStatuses().size();
        if (rowCount <= 0 || mouseY < rowStartY || mouseY >= rowStartY + rowCount * 11) {
            return -1;
        }
        int row = (int) ((mouseY - rowStartY) / 11);
        return row >= 0 && row < rowCount ? row : -1;
    }

    private void selectWorkflowStep(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= snapshot.stepStatuses().size()) {
            return;
        }
        ZombiesDeploySnapshot.StepStatus step = snapshot.stepStatuses().get(stepIndex);
        String key = step == null ? "" : step.key();
        String target = switch (key) {
            case "map" -> ZombiesDeployDraft.WORKFLOW_MAP;
            case "initial" -> ZombiesDeployDraft.WORKFLOW_INITIAL;
            case "zombie_spawn" -> ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN;
            case "barrier" -> ZombiesDeployDraft.WORKFLOW_BARRIER;
            case "interact" -> ZombiesDeployDraft.WORKFLOW_INTERACT;
            case "validate" -> ZombiesDeployDraft.WORKFLOW_VALIDATE;
            default -> "";
        };
        if (target.isBlank() || target.equals(this.workflowStep)) {
            return;
        }
        applyWorkflowStep(target);
    }

    private void applyWorkflowStep(String targetStep) {
        String target = ZombiesDeployDraft.normalizeWorkflowStep(targetStep);
        if (target.isBlank()) {
            return;
        }
        if (ZombiesDeployDraft.WORKFLOW_MAP.equals(target)) {
            this.workspaceStage = ZombiesDeployDraft.STAGE_MAP_REGISTRATION;
            this.workflowStep = ZombiesDeployDraft.WORKFLOW_MAP;
            this.selectedIndex = -1;
            sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
            return;
        }
        this.workspaceStage = ZombiesDeployDraft.STAGE_OBJECT_MARKING;
        this.workflowStep = target;
        this.selectedIndex = -1;
        switch (target) {
            case ZombiesDeployDraft.WORKFLOW_INITIAL -> this.selectedObjectType = ZombiesDeployFieldSchema.INITIAL;
            case ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN -> this.selectedObjectType = ZombiesDeployFieldSchema.ZOMBIE_SPAWN;
            case ZombiesDeployDraft.WORKFLOW_BARRIER -> this.selectedObjectType = ZombiesDeployFieldSchema.BARRIER;
            case ZombiesDeployDraft.WORKFLOW_INTERACT -> this.selectedObjectType = preferredInteractObjectType();
            default -> {
            }
        }
        this.capturePreset = ZombiesDeployDraft.normalizeCapturePreset(this.capturePreset, this.selectedObjectType);
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
    }

    private int validationIssueIndexAt(double mouseX, double mouseY, int left, int top, int width, int maxLines) {
        List<ZombiesDeploySnapshot.ValidationLine> lines = snapshot.validationLines();
        if (lines.isEmpty()) {
            return -1;
        }
        String jumpText = "[" + tr("gui.codpattern.zombies.deploy.jump_to_issue_short") + "]";
        int jumpBoxWidth = this.font.width(jumpText) + 6;
        if (mouseX < left || mouseX > left + jumpBoxWidth) {
            return -1;
        }
        int summaryY = top + 28 + 48;
        int lineStartY = summaryY + 2;
        int lineLimit = Math.max(1, maxLines - snapshot.validationSummaries().size());
        int visible = Math.min(lines.size(), lineLimit);
        if (visible <= 0 || mouseY < lineStartY || mouseY >= lineStartY + visible * 12) {
            return -1;
        }
        int row = (int) ((mouseY - lineStartY) / 12);
        return row >= 0 && row < visible ? row : -1;
    }

    private void jumpToValidationIssue(int issueIndex) {
        if (issueIndex < 0) {
            return;
        }
        ZombiesDeploySnapshot.IssueTarget target = issueTargetAt(issueIndex);
        if (target == null) {
            return;
        }
        if (target.mapStage()) {
            this.workspaceStage = ZombiesDeployDraft.STAGE_MAP_REGISTRATION;
            this.workflowStep = ZombiesDeployDraft.WORKFLOW_MAP;
            this.selectedIndex = -1;
            sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
            return;
        }
        String targetObjectType = ZombiesDeployFieldSchema.normalizeObjectType(target.targetObjectType());
        if (!targetObjectType.isBlank()) {
            this.selectedObjectType = targetObjectType;
        }
        this.workspaceStage = ZombiesDeployDraft.STAGE_OBJECT_MARKING;
        String targetStep = ZombiesDeployDraft.normalizeWorkflowStep(target.workflowStep());
        this.workflowStep = ZombiesDeployDraft.WORKFLOW_MAP.equals(targetStep)
                ? ZombiesDeployDraft.workflowStepForObjectType(this.selectedObjectType)
                : targetStep;
        this.capturePreset = ZombiesDeployDraft.normalizeCapturePreset(this.capturePreset, this.selectedObjectType);
        this.selectedIndex = Math.max(-1, target.targetIndex());
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
    }

    private ZombiesDeploySnapshot.IssueTarget issueTargetAt(int issueIndex) {
        if (issueIndex < 0 || issueIndex >= snapshot.issueTargets().size()) {
            return null;
        }
        return snapshot.issueTargets().get(issueIndex);
    }

    private void selectVisibleMap(int mapIndex) {
        List<String> maps = snapshot.availableMaps();
        if (mapIndex < 0 || mapIndex >= maps.size()) {
            return;
        }
        String nextMap = maps.get(mapIndex);
        if (Objects.equals(nextMap, this.selectedMap)) {
            return;
        }
        this.selectedMap = nextMap;
        this.selectedIndex = -1;
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
    }

    private void selectVisibleObject(int visibleRow) {
        int index = visibleObjectStart() + visibleRow;
        if (index < 0 || index >= snapshot.objects().size()) {
            return;
        }
        this.selectedIndex = snapshot.objects().get(index).index();
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
    }

    private void selectVisibleField(int visibleRow) {
        List<Integer> order = fieldDisplayOrder();
        int index = visibleFieldStart() + visibleRow;
        if (index < 0 || index >= order.size()) {
            return;
        }
        this.selectedFieldIndex = order.get(index);
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

    private boolean draftDirty() {
        if (snapshot.dirty()) {
            return true;
        }
        return localEditorDirty();
    }

    private boolean localEditorDirty() {
        if (this.mapNameBox != null && !Objects.equals(this.mapNameBox.getValue(), this.draftMapName)) {
            return true;
        }
        ZombiesDeploySnapshot.FieldValue field = currentField();
        if (field != null && this.fieldValueBox != null && field.editable()) {
            String current = currentFieldEditorValue(field);
            String stored = this.draftFields.getOrDefault(field.key(), field.value());
            if (!Objects.equals(current, stored)) {
                return true;
            }
        }
        for (ZombiesDeploySnapshot.FieldValue value : snapshot.fields()) {
            if (!Objects.equals(this.draftFields.get(value.key()), value.value())) {
                return true;
            }
        }
        return false;
    }

    private int objectCount(String objectType) {
        String normalized = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        for (ZombiesDeploySnapshot.ObjectTypeCount count : snapshot.objectCounts()) {
            if (count.objectType().equals(normalized)) {
                return count.count();
            }
        }
        return 0;
    }

    private String preferredInteractObjectType() {
        for (String objectType : INTERACT_DEFAULT_ORDER) {
            if (objectCount(objectType) <= 0) {
                return objectType;
            }
        }
        return INTERACT_DEFAULT_ORDER.get(0);
    }

    private String captureBindingLabel() {
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(this.workspaceStage)) {
            return ta("gui.codpattern.zombies.deploy.binding_short",
                    tr("gui.codpattern.zombies.deploy.map_slot_a"),
                    tr("gui.codpattern.zombies.deploy.map_slot_b"));
        }
        return ta("gui.codpattern.zombies.deploy.binding_short",
                captureSlotLabel(snapshot.captureSlotA()),
                captureSlotLabel(snapshot.captureSlotB()));
    }

    private String captureSlotLabel(String slotKey) {
        String key = Objects.requireNonNullElse(slotKey, "").trim();
        if (key.isEmpty()) {
            return "-";
        }
        if ("lookAt".equals(key)) {
            return tr("gui.codpattern.zombies.deploy.binding.look_at");
        }
        return key;
    }

    private String stepLabel(ZombiesDeploySnapshot.StepStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status.key()) {
            case "map" -> tr("gui.codpattern.zombies.deploy.step.map");
            case "initial" -> tr("gui.codpattern.zombies.deploy.step.initial");
            case "zombie_spawn" -> tr("gui.codpattern.zombies.deploy.step.zombie_spawn");
            case "barrier" -> tr("gui.codpattern.zombies.deploy.step.barrier");
            case "interact" -> tr("gui.codpattern.zombies.deploy.step.interact");
            case "validate" -> tr("gui.codpattern.zombies.deploy.step.validate");
            default -> labelOrDash(status.label());
        };
    }

    private String stepDetail(ZombiesDeploySnapshot.StepStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status.key()) {
            case "map" -> "1".equals(status.detail())
                    ? tr("gui.codpattern.zombies.deploy.step.state.created")
                    : tr("gui.codpattern.zombies.deploy.step.state.missing");
            case "validate" -> tr("gui.codpattern.zombies.deploy.step.state.view_validation");
            case "barrier" -> formatBarrierStepDetail(status.detail());
            case "interact" -> formatInteractStepDetail(status.detail());
            default -> labelOrDash(status.detail());
        };
    }

    private String formatInteractStepDetail(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "-";
        }
        Map<String, String> values = parseStepDetailValues(encoded);
        if (values.isEmpty()) {
            return labelOrDash(encoded);
        }
        boolean hasPower = detailFlag(values, "powerSwitch");
        String power = hasPower
                ? tr("gui.codpattern.zombies.deploy.step.state.placed")
                : tr("gui.codpattern.zombies.deploy.optional");
        String ultimate = detailFlag(values, "ultimateMachine")
                ? tr("gui.codpattern.zombies.deploy.step.state.placed")
                : tr("gui.codpattern.zombies.deploy.step.state.missing");
        int sodaCount = parseNonNegativeInt(firstDetailValue(values, "sodaMachine", "sodaMachines", "sodaCount"), 0);
        boolean hasSoda = sodaCount > 0 || detailFlag(values, "sodaMachine");
        String soda = hasSoda
                ? tr("gui.codpattern.zombies.deploy.step.state.placed")
                : tr("gui.codpattern.zombies.deploy.step.state.missing");
        if (sodaCount > 1) {
            soda = soda + " x" + sodaCount;
        }
        String powerLabel = tr("gui.codpattern.zombies.deploy.type.power_switch");
        String sodaLabel = tr("gui.codpattern.zombies.deploy.type.soda_machine");
        String ultimateLabel = tr("gui.codpattern.zombies.deploy.type.ultimate_machine");
        String total = firstDetailValue(values, "total", "interactionTotal", "interactTotal");
        if (total.isBlank()) {
            total = "0";
        }
        return powerLabel + ":" + power
                + "  " + sodaLabel + ":" + soda
                + "  " + ultimateLabel + ":" + ultimate
                + "  total:" + total;
    }

    private String formatBarrierStepDetail(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "-";
        }
        Map<String, String> values = parseStepDetailValues(encoded);
        if (values.isEmpty()) {
            return labelOrDash(encoded);
        }
        String barrierGroups = normalizeGroupList(firstDetailValue(
                values,
                "barrierGroups",
                "barrierGroup",
                "barrier_groups",
                "barrier_group"));
        String zombieSpawnGroups = normalizeGroupList(firstDetailValue(
                values,
                "zombieSpawnGroups",
                "zombieSpawnGroup",
                "spawnGroups",
                "spawnGroup",
                "zombie_groups",
                "zombie_group"));
        String missingGroups = normalizeGroupList(firstDetailValue(
                values,
                "missingZombieSpawnGroups",
                "missingSpawnGroups",
                "missingGroups",
                "missing",
                "unmatchedGroups",
                "unmatched"));
        String total = firstDetailValue(values, "total", "barrierTotal", "barrierCount", "barriers");
        List<String> parts = new ArrayList<>();
        if (!barrierGroups.isBlank()) {
            parts.add(tr("gui.codpattern.zombies.deploy.type.barrier")
                    + " " + tr("gui.codpattern.zombies.deploy.field.group")
                    + ":" + barrierGroups);
        }
        if (!zombieSpawnGroups.isBlank()) {
            parts.add(tr("gui.codpattern.zombies.deploy.type.zombie_spawn")
                    + " " + tr("gui.codpattern.zombies.deploy.field.group")
                    + ":" + zombieSpawnGroups);
        }
        if (!missingGroups.isBlank()) {
            parts.add(tr("gui.codpattern.zombies.deploy.step.state.missing") + ":" + missingGroups);
        }
        if (!total.isBlank()) {
            parts.add("total:" + total);
        }
        return parts.isEmpty() ? labelOrDash(encoded) : String.join("  ", parts);
    }

    private Map<String, String> parseStepDetailValues(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String token : encoded.split(";")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int idx = trimmed.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = trimmed.substring(0, idx).trim();
            String value = trimmed.substring(idx + 1).trim();
            if (!key.isEmpty()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private boolean detailFlag(Map<String, String> values, String... keys) {
        String value = firstDetailValue(values, keys).toLowerCase(Locale.ROOT);
        return value.equals("1") || value.equals("true") || value.equals("yes");
    }

    private String firstDetailValue(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private int parseNonNegativeInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String normalizeGroupList(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value
                .replace("[", "")
                .replace("]", "")
                .replace("{", "")
                .replace("}", "")
                .replace("(", "")
                .replace(")", "");
        String[] tokens = cleaned.split("[,|/\\\\]");
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                groups.add(trimmed);
            }
        }
        return String.join(",", groups);
    }

    private String formatNearestObjectHint(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return tr("gui.codpattern.zombies.deploy.nearest_none");
        }
        int split = encoded.lastIndexOf('|');
        if (split <= 0 || split >= encoded.length() - 1) {
            return encoded;
        }
        String label = encoded.substring(0, split);
        String distance = encoded.substring(split + 1);
        return ta("gui.codpattern.zombies.deploy.nearest", label, distance);
    }

    private String blockingReasonLabel() {
        return switch (snapshot.blockingReason()) {
            case "missing_map" -> tr("gui.codpattern.zombies.deploy.blocking.missing_map");
            case "missing_initial" -> tr("gui.codpattern.zombies.deploy.blocking.missing_initial");
            case "missing_zombie_spawn" -> tr("gui.codpattern.zombies.deploy.blocking.missing_zombie_spawn");
            case "missing_barrier" -> tr("gui.codpattern.zombies.deploy.blocking.missing_barrier");
            case "missing_weapon_wall" -> tr("gui.codpattern.zombies.deploy.blocking.missing_weapon_wall");
            case "missing_ammo_box" -> tr("gui.codpattern.zombies.deploy.blocking.missing_ammo_box");
            case "missing_armor_station" -> tr("gui.codpattern.zombies.deploy.blocking.missing_armor_station");
            case "missing_soda_machine" -> tr("gui.codpattern.zombies.deploy.blocking.missing_soda_machine");
            case "missing_ultimate_machine" -> tr("gui.codpattern.zombies.deploy.blocking.missing_ultimate_machine");
            case "mvp1_has_errors" -> tr("gui.codpattern.zombies.deploy.blocking.mvp1_has_errors");
            default -> snapshot.blockingReason();
        };
    }

    private void commitFieldEditorOnBlur(boolean wasFocused) {
        if (!wasFocused || this.fieldValueBox == null || this.fieldValueBox.isFocused()) {
            return;
        }
        setCurrentField();
    }

    private void blurAndCommitFieldEditorIfNeeded(double mouseX, double mouseY) {
        if (this.fieldValueBox == null || !this.fieldValueBox.isFocused() || this.fieldValueBox.isMouseOver(mouseX, mouseY)) {
            return;
        }
        this.fieldValueBox.setFocused(false);
        this.fieldValueBoxWasFocused = false;
        setCurrentField();
    }

    private String objectTypeLabel(String objectType) {
        return ZombiesDeployFieldSchema.objectType(objectType)
                .map(type -> tr(type.labelKey()))
                .orElse(labelOrDash(objectType));
    }

    private String shortObjectTypeLabel(String objectType) {
        return objectTypeLabel(objectType);
    }

    private String profileShortLabel(String profileKey) {
        return switch (ZombiesDeployFieldSchema.normalizeProfile(profileKey)) {
            case ZombiesDeployFieldSchema.PROFILE_MVP2 -> "MVP2";
            case ZombiesDeployFieldSchema.PROFILE_MVP3 -> "MVP3";
            default -> "MVP1";
        };
    }

    private String stageLabel(String stage) {
        return ZombiesDeployDraft.STAGE_OBJECT_MARKING.equals(stage)
                ? tr("gui.codpattern.zombies.deploy.stage.object_marking")
                : tr("gui.codpattern.zombies.deploy.stage.map_registration");
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

    private String trimButton(String value, int width) {
        return trimToWidth(value, Math.max(20, width));
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
