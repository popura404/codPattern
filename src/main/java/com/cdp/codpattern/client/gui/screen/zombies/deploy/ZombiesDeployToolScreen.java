package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployFieldSchema;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeploySnapshot;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.OpenZombiesDeployToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ZombiesDeployToolActionC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

    private ZombiesDeploySnapshot snapshot;
    private String selectedMap;
    private String selectedObjectType;
    private int selectedIndex;
    private String selectedProfile;
    private int selectedFieldIndex;
    private int selectedListRowIndex;
    private String selectedListFieldKey = "";
    private final Map<String, String> draftFields = new LinkedHashMap<>();

    private Button mapButton;
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
    private EditBox fieldValueBox;

    public ZombiesDeployToolScreen(OpenZombiesDeployToolScreenS2CPacket packet) {
        super(Component.literal("Zombies Deploy"));
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
        this.typeButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleObjectType())
                .pos(left + 170, top + 30)
                .size(156, 20)
                .build());
        this.profileButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleProfile())
                .pos(left + 332, top + 30)
                .size(126, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Refresh"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.REFRESH))
                .pos(left + 464, top + 30)
                .size(78, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Save selections"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS))
                .pos(left + 548, top + 30)
                .size(114, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Validate"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.VALIDATE_MAP))
                .pos(left + 668, top + 30)
                .size(88, 20)
                .build());

        this.objectPrevButton = this.addRenderableWidget(new Button.Builder(Component.literal("<"), button -> stepObject(-1))
                .pos(left + 16, top + 332)
                .size(24, 20)
                .build());
        this.objectNextButton = this.addRenderableWidget(new Button.Builder(Component.literal(">"), button -> stepObject(1))
                .pos(left + 44, top + 332)
                .size(24, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Add"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.ADD_OBJECT))
                .pos(left + 76, top + 332)
                .size(58, 20)
                .build());
        this.updateObjectButton = this.addRenderableWidget(new Button.Builder(Component.literal("Update"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.UPDATE_OBJECT))
                .pos(left + 140, top + 332)
                .size(68, 20)
                .build());
        this.deleteObjectButton = this.addRenderableWidget(new Button.Builder(Component.literal("Delete"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.DELETE_OBJECT))
                .pos(left + 214, top + 332)
                .size(68, 20)
                .build());

        this.fieldPrevButton = this.addRenderableWidget(new Button.Builder(Component.literal("<"), button -> stepField(-1))
                .pos(left + 294, top + 332)
                .size(24, 20)
                .build());
        this.fieldNextButton = this.addRenderableWidget(new Button.Builder(Component.literal(">"), button -> stepField(1))
                .pos(left + 322, top + 332)
                .size(24, 20)
                .build());
        this.setFieldButton = this.addRenderableWidget(new Button.Builder(Component.literal("Set field"), button -> setCurrentField())
                .pos(left + 352, top + 332)
                .size(86, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Player pos"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.CAPTURE_PLAYER_POS))
                .pos(left + 444, top + 332)
                .size(86, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Look block"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.CAPTURE_LOOK_BLOCK))
                .pos(left + 536, top + 332)
                .size(86, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Area 1"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.SET_AREA_POS_1))
                .pos(left + 628, top + 332)
                .size(62, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.literal("Area 2"), button -> sendAction(ZombiesDeployToolActionC2SPacket.Action.SET_AREA_POS_2))
                .pos(left + 696, top + 332)
                .size(62, 20)
                .build());

        this.fieldValueBox = this.addRenderableWidget(new EditBox(this.font, left + 294, top + 360, 464, 20, Component.empty()));
        this.fieldValueBox.setMaxLength(2048);

        this.addRenderableWidget(new Button.Builder(Component.literal("Close"), button -> onClose())
                .pos(left + 696, top + 396)
                .size(62, 20)
                .build());
        this.listRowPrevButton = this.addRenderableWidget(new Button.Builder(Component.literal("Row <"), button -> stepListRow(-1))
                .pos(left + 294, top + 396)
                .size(44, 20)
                .build());
        this.listRowNextButton = this.addRenderableWidget(new Button.Builder(Component.literal("Row >"), button -> stepListRow(1))
                .pos(left + 342, top + 396)
                .size(44, 20)
                .build());
        this.listRowInsertButton = this.addRenderableWidget(new Button.Builder(Component.literal("Insert"), button -> insertCurrentListRow())
                .pos(left + 394, top + 396)
                .size(54, 20)
                .build());
        this.listRowUpdateButton = this.addRenderableWidget(new Button.Builder(Component.literal("Update"), button -> updateCurrentListRow())
                .pos(left + 452, top + 396)
                .size(58, 20)
                .build());
        this.listRowDeleteButton = this.addRenderableWidget(new Button.Builder(Component.literal("Delete"), button -> deleteCurrentListRow())
                .pos(left + 514, top + 396)
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

        drawSection(guiGraphics, left + 12, top + 62, 270, 262, "Objects");
        drawObjects(guiGraphics, left + 18, top + 82, 258);

        drawSection(guiGraphics, left + 290, top + 62, 230, 262, "Fields");
        drawFields(guiGraphics, left + 296, top + 82, 218);

        drawSection(guiGraphics, left + 528, top + 62, 280, 262, "Validation");
        boolean showListPreview = isCurrentListField();
        drawValidation(guiGraphics, left + 534, top + 82, 268, showListPreview ? 7 : 16);
        if (showListPreview) {
            drawListPreview(guiGraphics, left + 534, top + 220, 268);
        }

        drawCurrentField(guiGraphics, left + 294, top + 356, 392);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        int left = panelLeft();
        int top = panelTop();
        int objectIndex = listIndexAt(mouseX, mouseY, left + 18, top + 82, visibleObjectCount());
        if (objectIndex >= 0) {
            selectVisibleObject(objectIndex);
            return true;
        }

        int listRowIndex = listRowIndexAt(mouseX, mouseY, left + 534, top + 220, 268);
        if (listRowIndex >= 0) {
            selectListRow(listRowIndex);
            return true;
        }

        int fieldIndex = listIndexAt(mouseX, mouseY, left + 296, top + 82, visibleFieldCount());
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
        if (this.fieldValueBox != null && this.fieldValueBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
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
        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applySnapshot(ZombiesDeploySnapshot nextSnapshot) {
        this.snapshot = nextSnapshot == null ? emptySnapshot() : nextSnapshot;
        this.selectedMap = this.snapshot.selectedMap();
        this.selectedObjectType = this.snapshot.selectedObjectType();
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
                "",
                List.of(new ZombiesDeploySnapshot.ObjectTypeOption(ZombiesDeployFieldSchema.INITIAL, "")),
                ZombiesDeployFieldSchema.INITIAL,
                -1,
                List.of(),
                List.of(),
                ZombiesDeployFieldSchema.PROFILE_MVP1,
                List.of(ZombiesDeployFieldSchema.PROFILE_MVP1),
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

        this.typeButton.setMessage(Component.literal(labelOrDash(this.selectedObjectType)));
        this.typeButton.active = !snapshot.objectTypes().isEmpty();

        this.profileButton.setMessage(Component.literal(labelOrDash(this.selectedProfile)));
        this.profileButton.active = snapshot.availableProfiles().size() > 1;

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
        this.setFieldButton.setMessage(Component.literal(listField ? "Set row" : "Set field"));
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
                this.selectedMap,
                this.selectedObjectType,
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

    private void drawObjects(GuiGraphics guiGraphics, int left, int top, int width) {
        if (snapshot.objects().isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("No objects for " + labelOrDash(selectedObjectType)), left, top, MUTED_TEXT, false);
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
            guiGraphics.drawString(this.font, Component.literal("No fields"), left, top, MUTED_TEXT, false);
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
            String first = field.key() + "  " + field.type().name().toLowerCase(Locale.ROOT);
            String second = field.type() == ZombiesDeployFieldSchema.FieldType.LIST
                    ? listFieldSummary(field.key(), value)
                    : (value.isBlank() ? "(blank)" : value);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(first, width)), left, y, field.editable() ? TEXT : MUTED_TEXT, false);
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(second, width)), left, y + 10, MUTED_TEXT, false);
        }
    }

    private void drawValidation(GuiGraphics guiGraphics, int left, int top, int width, int maxLines) {
        guiGraphics.drawString(this.font, Component.literal("Map: " + labelOrDash(selectedMap)), left, top, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal("Profile: " + labelOrDash(selectedProfile)), left, top + 12, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal("Active: " + (snapshot.activeMap() ? "yes" : "no") + "  Rev: " + snapshot.revision()), left, top + 24, LABEL_TEXT, false);

        List<ZombiesDeploySnapshot.ValidationLine> lines = snapshot.validationLines();
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("No validation issues"), left, top + 46, INFO_TEXT, false);
            return;
        }
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            ZombiesDeploySnapshot.ValidationLine line = lines.get(i);
            int y = top + 46 + i * 12;
            String text = "[" + line.severity() + "] " + line.code() + " " + line.subject() + " " + line.message();
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(text, width)), left, y, colorForSeverity(line.severity()), false);
        }
        if (lines.size() > maxLines) {
            guiGraphics.drawString(this.font, Component.literal("+" + (lines.size() - maxLines) + " more"), left, top + 46 + maxLines * 12, MUTED_TEXT, false);
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
        guiGraphics.drawString(this.font, Component.literal(trimToWidth("List helper: " + field.key(), width - 4)), left, top, LABEL_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(preview.hint(), width - 4)), left, top + 12, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth(preview.statusLine(), width - 4)), left, top + 24, statusColor, false);

        int y = top + 38;
        int issueLimit = Math.min(2, preview.issues().size());
        for (int i = 0; i < issueLimit; i++) {
            guiGraphics.drawString(this.font, Component.literal(trimToWidth(preview.issues().get(i), width - 4)), left, y, statusColor, false);
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
            String range = "Rows " + (rowStart + 1) + "-" + rowEnd + " of " + preview.rows().size();
            guiGraphics.drawString(this.font, Component.literal(range), left, y, MUTED_TEXT, false);
        }
    }

    private void drawCurrentField(GuiGraphics guiGraphics, int left, int top, int width) {
        ZombiesDeploySnapshot.FieldValue field = currentField();
        String label = field == null ? "Field value" : "Field value: " + field.key();
        if (field != null && field.type() == ZombiesDeployFieldSchema.FieldType.LIST) {
            List<String> rows = listRows(field.key(), draftFields.getOrDefault(field.key(), field.value()));
            String row = rows.isEmpty() ? "0/0" : (clampIndex(this.selectedListRowIndex, rows.size()) + 1) + "/" + rows.size();
            label = label + " row " + row;
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
        String prefix = preview.rows().size() == 1 ? "1 row" : preview.rows().size() + " rows";
        if (preview.hasErrors()) {
            return prefix + "  error";
        }
        if (preview.hasWarnings()) {
            return prefix + "  warning";
        }
        return prefix + "  ok";
    }

    private ListFieldPreview listPreview(String fieldKey, String value) {
        return switch (fieldKey) {
            case "refreshWaves" -> previewRefreshWaves(value);
            case "rarityPools" -> previewRarityPools(value);
            case "weapons" -> previewWeapons(value);
            case "pricesByWeaponLevel" -> previewIntegerMap(value, "Use level=cost rows separated by comma or semicolon.", "level", "cost");
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
                issues.add("Error row " + (i + 1) + ": wave must be an integer >= 1.");
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            if (!seen.add(wave)) {
                duplicates.add(wave);
            }
            rows.add("#" + (i + 1) + "  wave " + wave);
        }
        if (!duplicates.isEmpty()) {
            issues.add("Warning: duplicate waves " + joinIntegers(duplicates) + ".");
        }
        return new ListFieldPreview(
                "Use comma/semicolon separated waves, e.g. 1,4,7.",
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
                issues.add("Error row " + (i + 1) + ": use id=rank,baseWeight,waveFactor.");
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            String id = entry.substring(0, equals).trim();
            String[] parts = entry.substring(equals + 1).split(",");
            if (id.isEmpty() || parts.length != 3) {
                issues.add("Error row " + (i + 1) + ": use id=rank,baseWeight,waveFactor.");
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            Integer rank = parseInteger(parts[0]);
            Double baseWeight = parseFiniteDouble(parts[1]);
            Double waveFactor = parseFiniteDouble(parts[2]);
            if (rank == null || baseWeight == null || waveFactor == null) {
                issues.add("Error row " + (i + 1) + ": rank/base/wave must be numeric.");
            } else {
                if (!ids.add(id)) {
                    issues.add("Warning row " + (i + 1) + ": duplicate rarity id " + id + ".");
                }
                if (!ranks.add(rank)) {
                    issues.add("Warning row " + (i + 1) + ": duplicate rank " + rank + ".");
                }
                rows.add("#" + (i + 1) + "  " + id + "  rank " + rank + "  base " + baseWeight + "  wave " + waveFactor);
                continue;
            }
            rows.add("#" + (i + 1) + "  " + entry);
        }
        return new ListFieldPreview(
                "Use ; between rows: id=rank,baseWeight,waveFactor.",
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
                issues.add("Error row " + (i + 1) + ": use gunId|rarity=weight,rarity=weight.");
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            String gunId = entry.substring(0, separator).trim();
            List<String> weights = splitLooseEntries(entry.substring(separator + 1));
            if (gunId.isEmpty() || weights.isEmpty()) {
                issues.add("Error row " + (i + 1) + ": gunId and weights are required.");
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            List<String> parsedWeights = new ArrayList<>();
            for (String weightEntry : weights) {
                int equals = weightEntry.indexOf('=');
                if (equals <= 0 || equals == weightEntry.length() - 1) {
                    issues.add("Error row " + (i + 1) + ": weight must be rarity=positiveDecimal.");
                    continue;
                }
                String rarity = weightEntry.substring(0, equals).trim();
                Double weight = parseFiniteDouble(weightEntry.substring(equals + 1));
                if (rarity.isEmpty() || weight == null || weight <= 0.0D) {
                    issues.add("Error row " + (i + 1) + ": weight must be rarity=positiveDecimal.");
                    continue;
                }
                if (!rarityIds.isEmpty() && !rarityIds.contains(rarity)) {
                    issues.add("Warning row " + (i + 1) + ": unknown rarity " + rarity + ".");
                }
                parsedWeights.add(rarity + "=" + weight);
            }
            rows.add("#" + (i + 1) + "  " + gunId + "  " + String.join(", ", parsedWeights));
        }
        return new ListFieldPreview(
                "Use ; between rows: gunId|rarity=weight,rarity=weight.",
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
                issues.add("Error row " + (i + 1) + ": use " + keyLabel + "=" + valueLabel + ".");
                rows.add("#" + (i + 1) + "  " + entry);
                continue;
            }
            String key = entry.substring(0, equals).trim();
            Integer parsedKey = parseInteger(key);
            Integer parsedValue = parseInteger(entry.substring(equals + 1));
            if (parsedKey == null || parsedKey < 1 || parsedValue == null || parsedValue < 0) {
                issues.add("Error row " + (i + 1) + ": " + keyLabel + " must be >= 1 and " + valueLabel + " >= 0.");
            }
            if (!keys.add(key)) {
                issues.add("Warning row " + (i + 1) + ": duplicate " + keyLabel + " " + key + ".");
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
                issues.add("Error row " + (i + 1) + ": use level=cost:damageMultiplier.");
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
                issues.add("Error row " + (i + 1) + ": level >= 1, cost >= 0, multiplier > 0.");
            } else if (!levels.add(level)) {
                issues.add("Warning row " + (i + 1) + ": duplicate level " + level + ".");
            }
            rows.add("#" + (i + 1) + "  level " + levelText + " -> cost " + costText + "  x" + multiplierText);
        }

        Integer maxLevel = parseInteger(this.draftFields.getOrDefault("maxUpgradeLevel", ""));
        if (maxLevel != null && maxLevel > 0 && !containsError(issues)) {
            for (int level = 1; level <= maxLevel; level++) {
                if (!levels.contains(level)) {
                    issues.add("Warning: missing level " + level + " for maxUpgradeLevel " + maxLevel + ".");
                    break;
                }
            }
            for (Integer level : levels) {
                if (level > maxLevel) {
                    issues.add("Warning: level " + level + " is above maxUpgradeLevel " + maxLevel + ".");
                    break;
                }
            }
        }
        return new ListFieldPreview(
                "Use comma/semicolon rows: level=cost:damageMultiplier.",
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
                "Use semicolon or newline between rows.",
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
        return issues.stream().anyMatch(issue -> issue.startsWith("Error"));
    }

    private boolean containsWarning(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.startsWith("Warning"));
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
            parts.add(snapshot.statusKey());
        }
        String status = parts.isEmpty() ? "ready" : String.join(" ", parts);
        guiGraphics.drawString(this.font, Component.literal(trimToWidth("Status: " + status, width)), left, top, INFO_TEXT, false);
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

        private String statusLine() {
            String count = rows.size() == 1 ? "1 parsed row" : rows.size() + " parsed rows";
            if (hasErrors) {
                return "Error: " + count;
            }
            if (hasWarnings) {
                return "Warning: " + count;
            }
            return "OK: " + count;
        }
    }
}
