package com.cdp.codpattern.app.zombies.deploy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZombiesDeployGuiStaticContractCompatTest {
    private static final Path SCREEN = Path.of("src/main/java/com/cdp/codpattern/client/gui/screen/zombies/deploy/ZombiesDeployToolScreen.java");
    private static final Path SERVICE = Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployToolService.java");
    private static final Path PREVIEW = Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployPreviewService.java");
    private static final Path TOOL = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/ZombiesDeployTool.java");
    private static final Path PACKET = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/ZombiesDeployToolActionC2SPacket.java");
    private static final Path TOOL_INTERACTION_PACKET = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/ToolInteractionC2SPacket.java");
    private static final Path TOOL_INTERACTION_HANDLER = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/tool/ToolInteractionClientHandler.java");
    private static final Path WORLD_TOOL_ITEM = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/tool/WorldToolItem.java");
    private static final Path TOOL_INTERACTION_HIT = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/tool/ToolInteractionHit.java");
    private static final Path FIELD_SCHEMA = Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployFieldSchema.java");
    private static final List<Path> KEY_SOURCE_FILES = List.of(
            Path.of("src/main/java/com/cdp/codpattern/client/gui/screen/zombies/deploy/ZombiesDeployToolScreen.java"),
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployToolService.java"),
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployPreviewService.java"),
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployServiceResult.java"),
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/deploy/ZombiesDeployFieldSchema.java"),
            Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/ZombiesDeployTool.java"),
            Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/ZombiesDeployToolActionC2SPacket.java"));
    private static final List<Path> LANG_FILES = List.of(
            Path.of("src/main/resources/assets/codpattern/lang/en_us.json"),
            Path.of("src/main/resources/assets/codpattern/lang/zh_cn.json"),
            Path.of("src/main/resources/assets/codpattern/lang/ja_jp.json"),
            Path.of("src/main/resources/assets/codpattern/lang/zh_tw.json"));
    private static final Pattern JAVA_DEPLOY_KEY = Pattern.compile("\"((?:gui\\.codpattern\\.zombies\\.deploy|message\\.codpattern\\.zombies\\.deploy|tooltip\\.codpattern\\.zombies_deploy)\\.[^\"]+)\"");
    private static final Pattern JSON_DEPLOY_KEY = Pattern.compile("\"((?:gui\\.codpattern\\.zombies\\.deploy|message\\.codpattern\\.zombies\\.deploy|tooltip\\.codpattern\\.zombies_deploy)\\.[^\"]+)\"\\s*:");
    private static final Pattern JSON_DEPLOY_ENTRY = Pattern.compile("\"((?:gui\\.codpattern\\.zombies\\.deploy|message\\.codpattern\\.zombies\\.deploy|tooltip\\.codpattern\\.zombies_deploy)\\.[^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    private ZombiesDeployGuiStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        String screen = read(SCREEN);
        String service = read(SERVICE);
        String preview = read(PREVIEW);
        String tool = read(TOOL);
        String packet = read(PACKET);
        String toolInteractionPacket = read(TOOL_INTERACTION_PACKET);
        String toolInteractionHandler = read(TOOL_INTERACTION_HANDLER);
        String worldToolItem = read(WORLD_TOOL_ITEM);
        String toolInteractionHit = read(TOOL_INTERACTION_HIT);
        String fieldSchema = read(FIELD_SCHEMA);
        requireAbsent(screen, "newDraftButton", "new draft button must stay removed");
        requireAbsent(screen, "saveObjectButton", "save object button must stay removed");
        requireAbsent(screen, "newDraft()", "new draft action must stay removed");
        requireAbsent(screen, "saveCurrentObject()", "explicit object save action must stay removed");
        requireAbsent(screen, "section.objects_draft", "visible objects/draft section key must stay renamed");
        requireAbsent(screen, "draftDirty()", "screen draft dirty state must stay removed");
        requireAbsent(screen, "localEditorDirty()", "screen local editor dirty state must stay removed");
        requireAbsent(screen, "GLFW_KEY_S", "Ctrl+S or S-key explicit object save shortcut must stay removed");
        requireAbsent(screen, "hasControlDown", "control-key explicit object save shortcut must stay removed");
        requireAbsent(screen, "CAPTURE_PLAYER_POS", "GUI capture-player-position action must stay removed from visible controls");
        requireAbsent(screen, "CAPTURE_LOOK_BLOCK", "GUI capture-look-block action must stay removed from visible controls");
        requireAbsent(screen, "SET_AREA_POS", "GUI set-area-position action must stay removed from visible controls");

        requireContains(screen, "section.objects_properties", "objects/properties merged section must be rendered");
        requireContains(screen, "private boolean canEditObjectFields()", "field editing must support selected and pending objects");
        String canEditObjectFieldsBody = methodBody(screen, "private boolean canEditObjectFields");
        requireContains(canEditObjectFieldsBody, "&& !this.selectedMap.isBlank();",
                "field editing must still require an object-stage map selection");
        requireAbsent(canEditObjectFieldsBody, "selectedIndex",
                "field editing must allow pending new-object fields before the first world click");
        requireContains(screen, "private int visibleObjectCount() {\n        return 5;\n    }", "object list should expose enough rows before scrolling");
        requireContains(screen, "private int visibleFieldCount() {\n        return 8;\n    }", "properties area should expose common point fields before scrolling");
        requireContains(screen, "private static final int PANEL_WIDTH = 820;", "deploy GUI panel width should stay fixed for layout checks");
        requireContains(screen, "private static final int PANEL_HEIGHT = 560;", "deploy GUI panel height should reserve space for scrollable object/properties lists");
        requirePanelFitsMinimumResolution(screen, 1024, 768);
        requireContains(screen, "private static final int CENTER_SECTION_HEIGHT = 390;", "merged objects/properties panel should keep expanded height");
        requireContains(screen, "private static final int OBJECT_LIST_Y = 136;", "object list should remain inside merged center panel");
        requireContains(screen, "private static final int FIELD_LIST_Y = 274;", "field list should remain below the expanded object list");
        requireContains(screen, "private static final int FIELD_INPUT_Y = 492;", "field input should remain below property rows");
        requireContains(screen, "private static final int BOTTOM_ROW_Y = 524;", "bottom controls should remain below field input");
        requireContains(screen, "if (\"pitch\".equals(snapshot.fields().get(i).key()))", "pitch must stay hidden from object properties");
        requireContains(screen, "case \"posX\", \"posY\", \"posZ\" -> 1;",
                "position coordinates should stay near the top of the properties list");
        requireContains(screen, "case \"interactionX\", \"interactionY\", \"interactionZ\" -> 2;",
                "interaction coordinates should stay near the top of the properties list");
        requireContains(screen, "case \"areaFromX\", \"areaFromY\", \"areaFromZ\", \"areaToX\", \"areaToY\", \"areaToZ\" -> 3;",
                "barrier area coordinates should stay visible in the properties priority order");
        requireContains(screen, "case \"group\", \"weight\", \"cost\", \"price\", \"weaponLevel\", \"armorLevel\", \"buyCost\", \"buffId\", \"requiresPower\", \"maxUpgradeLevel\" -> 4;",
                "group, weight, price, and purchase fields should stay in the high-priority properties group");
        requireContains(screen, "if (\"yaw\".equals(field.key())) {\n                first = first + \" (\" + tr(\"gui.codpattern.zombies.deploy.yaw_only_short\") + \")\";\n            }",
                "yaw field should keep an explicit horizontal-only label");
        requireContains(screen, "return isSelectionOnlyAction(action)\n                ? selectionDraft()\n                : draft();",
                "selection-only actions must not carry edited object fields");
        requireContains(screen, "return action == ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS\n                || action == ZombiesDeployToolActionC2SPacket.Action.SELECT_OBJECT_TYPE;",
                "object type switches must use selection-only packets");
        requireContains(screen, "private ZombiesDeployDraft selectionDraft() {\n        return draftFromFields(Map.of());\n    }",
                "selection draft must use empty fields so object switches do not save object data");
        requireContains(screen, "button.active = !inMapStage;",
                "map registration stage must disable object type buttons");
        requireContains(screen, "tr(\"gui.codpattern.zombies.deploy.no_registered_maps\")",
                "empty zombies map lists must show an explicit no-registered-maps label");
        requireContains(screen,
                "tr(\"gui.codpattern.zombies.deploy.map_slot_a\"),\n                    tr(\"gui.codpattern.zombies.deploy.map_slot_b\")",
                "map registration binding should use map point labels, not old slot labels");
        requireContains(screen, "int y = top + (i - start) * 22;", "object/property rows should keep stable 22px row spacing");
        requireContains(screen, "guiGraphics.drawString(this.font, Component.literal(trimToWidth(second, width)), left, y + 10, MUTED_TEXT, false);",
                "two-line object/property rows should keep readable subline spacing");
        requireSingleObjectPolicy(fieldSchema);
        String initBody = methodBody(screen, "protected void init");
        requireAbsent(initBody, "SAVE_SELECTIONS", "visible controls should not expose a save-selection/save-object button");
        requireAbsent(initBody, "ADD_OBJECT", "visible controls should not expose explicit add-object button");
        requireAbsent(initBody, "UPDATE_OBJECT", "visible controls should not expose explicit update-object button");
        requireAbsent(initBody, "CAPTURE", "visible controls should not expose old capture buttons");
        requireAbsent(initBody, "SET_AREA", "visible controls should not expose old area capture buttons");

        String keyPressedBody = methodBody(screen, "public boolean keyPressed");
        requireContains(keyPressedBody, "if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {\n            setCurrentField();\n            return true;\n        }",
                "Enter should remain the only direct field commit shortcut");
        requireContains(keyPressedBody, "if (keyCode == GLFW.GLFW_KEY_R) {\n            sendAction(ZombiesDeployToolActionC2SPacket.Action.REFRESH);\n            return true;\n        }",
                "R refresh shortcut should remain non-persistent");
        requireContains(keyPressedBody, "if (keyCode == GLFW.GLFW_KEY_V) {\n            sendAction(ZombiesDeployToolActionC2SPacket.Action.VALIDATE_MAP);\n            return true;\n        }",
                "V validate shortcut should remain non-object-save");
        requireAbsent(keyPressedBody, "SAVE_SELECTIONS", "keyboard shortcuts must not save object selections as map objects");
        requireAbsent(keyPressedBody, "ADD_OBJECT", "keyboard shortcuts must not expose explicit add-object save");
        requireAbsent(keyPressedBody, "UPDATE_OBJECT", "keyboard shortcuts must not expose explicit update-object save");

        String mouseClickedBody = methodBody(screen, "public boolean mouseClicked");
        requireContains(mouseClickedBody, "blurAndCommitFieldEditorIfNeeded(mouseX, mouseY);",
                "screen world-click path should commit/blur field editor before outside-panel deployment");
        requireContains(mouseClickedBody, "if (super.mouseClicked(mouseX, mouseY, button)) {\n            commitFieldEditorOnBlur(fieldFocusedBefore);\n            return true;\n        }",
                "widget clicks should commit the field editor only on actual blur");
        requireContains(mouseClickedBody, "if (!isInsidePanel(mouseX, mouseY)) {\n            return false;\n        }",
                "screen outside-panel clicks must not become deploy input");
        requireAbsent(mouseClickedBody, "ToolInteractionC2SPacket",
                "screen outside-panel clicks must not send common tool interaction packets");
        requireAbsent(mouseClickedBody, "pickBlockPos",
                "screen outside-panel clicks must not ray-pick world blocks");
        requireAbsent(mouseClickedBody, "SAVE_SELECTIONS", "screen outside-panel world clicks must not only save selection state");
        requireAbsent(mouseClickedBody, "ADD_OBJECT", "screen outside-panel world clicks must not use old explicit add-object action");
        requireAbsent(mouseClickedBody, "UPDATE_OBJECT", "screen outside-panel world clicks must not use old explicit update-object action");

        String mouseScrolledBody = methodBody(screen, "public boolean mouseScrolled");
        requireContains(mouseScrolledBody, "if (scrollObjectList(mouseX, mouseY, delta)) {\n            return true;\n        }",
                "mouse wheel should scroll the object list when hovered");
        requireContains(mouseScrolledBody, "if (scrollFieldList(mouseX, mouseY, delta)) {\n            return true;\n        }",
                "mouse wheel should scroll the field list when hovered");
        requireAbsent(mouseScrolledBody, "sendAction", "mouse wheel scrolling must not send persistence packets");
        requireAbsent(mouseScrolledBody, "FPSMatch.sendToServer", "mouse wheel scrolling must stay client-local");

        String visibleObjectStartBody = methodBody(screen, "private int visibleObjectStart");
        requireContains(visibleObjectStartBody, "return clampListStart(this.objectScrollStart, snapshot.objects().size(), visibleObjectCount());",
                "object list visible start should come from scroll state");
        String visibleFieldStartBody = methodBody(screen, "private int visibleFieldStart");
        requireContains(visibleFieldStartBody, "return clampListStart(this.fieldScrollStart, fieldDisplayOrder().size(), visibleFieldCount());",
                "field list visible start should come from scroll state");

        String selectVisibleFieldBody = methodBody(screen, "private void selectVisibleField");
        requireContains(selectVisibleFieldBody, "this.selectedFieldIndex = order.get(index);\n        updateWidgets();",
                "field selection should only update local focus/widgets");
        requireAbsent(selectVisibleFieldBody, "sendAction", "field focus changes must not send persistence packets");
        requireAbsent(selectVisibleFieldBody, "FPSMatch.sendToServer", "field focus changes must not send network packets");

        String selectVisibleMapBody = methodBody(screen, "private void selectVisibleMap");
        requireContains(selectVisibleMapBody, "this.selectedIndex = -1;\n        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);",
                "map selection should clear object selection and save selection state only");
        requireAbsent(selectVisibleMapBody, "SET_FIELD", "map selection must not persist object fields");
        requireAbsent(selectVisibleMapBody, "ADD_OBJECT", "map selection must not add objects");
        requireAbsent(selectVisibleMapBody, "UPDATE_OBJECT", "map selection must not update objects");

        String selectVisibleObjectBody = methodBody(screen, "private void selectVisibleObject");
        requireContains(selectVisibleObjectBody, "this.selectedIndex = snapshot.objects().get(index).index();\n        sendAction(ZombiesDeployToolActionC2SPacket.Action.SAVE_SELECTIONS);",
                "object selection should save selection state only");
        requireAbsent(selectVisibleObjectBody, "SET_FIELD", "object selection must not persist object fields");
        requireAbsent(selectVisibleObjectBody, "ADD_OBJECT", "object selection must not add objects");
        requireAbsent(selectVisibleObjectBody, "UPDATE_OBJECT", "object selection must not update objects");

        String selectObjectTypeBody = methodBody(screen, "private void selectObjectType");
        requireContains(selectObjectTypeBody, "if (isMapRegistrationStage()) {\n            return;\n        }",
                "map registration stage must block object type selection");
        requireContains(selectObjectTypeBody, "sendAction(ZombiesDeployToolActionC2SPacket.Action.SELECT_OBJECT_TYPE);",
                "object type selection should use the object-type selection action");
        requireAbsent(selectObjectTypeBody, "SAVE_SELECTIONS", "object type selection must not use the generic save-selection action");
        requireAbsent(selectObjectTypeBody, "ADD_OBJECT", "object type selection must not add map objects");
        requireAbsent(selectObjectTypeBody, "UPDATE_OBJECT", "object type selection must not update map objects");

        String onCloseBody = methodBody(screen, "public void onClose");
        requireAbsent(onCloseBody, "sendAction", "closing deploy GUI must not trigger object or selection save actions");
        requireAbsent(onCloseBody, "FPSMatch.sendToServer", "closing deploy GUI must not send persistence packets");

        String toolWorldInteractionBody = methodBody(tool, "public void handleWorldInteraction");
        requireContains(toolWorldInteractionBody, "case LEFT_CLICK_BLOCK ->",
                "deploy tool must handle world left click");
        requireContains(toolWorldInteractionBody, "captureWorldClick(player, stack, hit.placementPos(), true);",
                "deploy tool left click must route placement position through service world-click handling");
        requireContains(toolWorldInteractionBody, "case RIGHT_CLICK_BLOCK ->",
                "deploy tool must handle world right click");
        requireContains(toolWorldInteractionBody, "captureWorldClick(player, stack, hit.placementPos(), false);",
                "deploy tool right click must route placement position through service world-click handling");
        requireAbsent(toolWorldInteractionBody, "saveDraft", "deploy tool world click should not only save local selection state");

        String toolCaptureBody = methodBody(tool, "private void captureWorldClick");
        requireAbsent(toolCaptureBody, "OpenZombiesDeployToolScreenS2CPacket",
                "deploy world clicks must not open or refresh the deploy GUI");
        requireContains(toolCaptureBody, ".captureWorldClick(player, stack, getDraft(stack), pos, leftClick);",
                "deploy world clicks should still call the service for persistence");

        String packetDispatchBody = methodBody(packet, "private ZombiesDeployServiceResult<ZombiesDeploySnapshot> dispatch");
        requireContains(packetDispatchBody, "case REFRESH, SELECT_MAP, SELECT_OBJECT -> service.snapshot(",
                "read-only selection packet actions should only refresh snapshots");
        requireContains(packetDispatchBody, "case SELECT_OBJECT_TYPE -> service.selectObjectType(player, stack, draft);",
                "object type selection should save only type context and refresh");
        requireContains(packetDispatchBody, "case SAVE_SELECTIONS -> service.saveSelections(player, stack, draft);",
                "SAVE_SELECTIONS packet action should route to selection-only service path");
        requireContains(packetDispatchBody, "case SET_FIELD -> service.setField(player, stack, draft, fieldKey, fieldValue);",
                "SET_FIELD packet action should route to selected-object field update service path");
        requireContains(packetDispatchBody, "case ADD_OBJECT -> service.addObject(player, stack, draft);",
                "ADD_OBJECT compatibility action should route through object editor service");
        requireContains(packetDispatchBody, "case UPDATE_OBJECT -> service.updateObject(player, stack, draft);",
                "UPDATE_OBJECT compatibility action should route through object editor service");
        requireContains(packetDispatchBody, "case DUPLICATE_OBJECT -> service.duplicateObject(player, stack, draft);",
                "DUPLICATE_OBJECT action should route through object editor service");
        requireContains(packetDispatchBody, "case DELETE_OBJECT -> service.deleteObject(player, stack, draft);",
                "DELETE_OBJECT action should route through object editor service");
        requireAbsent(packetDispatchBody, "ZombiesDeployTool.saveDraft", "packet dispatch must not bypass service selection/object rules");
        requireAbsent(packetDispatchBody, "ZombiesDeployObjectEditor", "packet dispatch must not invoke editor directly");
        requireAbsent(packetDispatchBody, "CodMapPersistence", "packet dispatch must not persist objects directly");

        String saveSelectionsBody = methodBody(service, "public ZombiesDeployServiceResult<ZombiesDeploySnapshot> saveSelections");
        requireContains(saveSelectionsBody, "ZombiesDeployDraft draft = selectionStateDraft(player, stack, request);",
                "saveSelections should normalize to selection state without unsaved player-position fields");
        requireContains(saveSelectionsBody, "ZombiesDeployTool.saveDraft(stack, draft);",
                "saveSelections should persist only tool selection state");
        requireContains(saveSelectionsBody, "\"message.codpattern.zombies.deploy.selections_saved\"",
                "saveSelections should use selection-save status message");
        requireAbsent(saveSelectionsBody, "editObject(", "saveSelections must not edit map objects");
        requireAbsent(saveSelectionsBody, "CodMapPersistence", "saveSelections must not persist map objects");
        requireAbsent(saveSelectionsBody, "ZombiesDeployObjectEditor", "saveSelections must not invoke object editor");

        String selectionStateDraftBody = methodBody(service, "private ZombiesDeployDraft selectionStateDraft");
        requireContains(selectionStateDraftBody, "return draft.selectedIndex() < 0 ? draft.withFields(Map.of()) : draft;",
                "unselected object type switches must clear transient player-position fields");

        String selectObjectTypeServiceBody = methodBody(service, "public ZombiesDeployServiceResult<ZombiesDeploySnapshot> selectObjectType");
        requireContains(selectObjectTypeServiceBody, "ZombiesDeployDraft draft = selectionStateDraft(player, stack, request);",
                "selectObjectType should reuse selection-only normalization");
        requireContains(selectObjectTypeServiceBody, "ZombiesDeployTool.setAreaPos1(stack, null);\n        ZombiesDeployTool.setAreaPos2(stack, null);",
                "selectObjectType should clear stale two-point capture state");
        requireAbsent(selectObjectTypeServiceBody, "editObject(", "selectObjectType must not edit map objects");
        requireAbsent(selectObjectTypeServiceBody, "CodMapPersistence", "selectObjectType must not persist map objects");
        requireAbsent(selectObjectTypeServiceBody, "ZombiesDeployObjectEditor", "selectObjectType must not invoke object editor");

        String setFieldBody = methodBody(service, "public ZombiesDeployServiceResult<ZombiesDeploySnapshot> setField");
        requireContains(setFieldBody, "ZombiesDeployDraft draft = normalizeDraft(player, stack, request);",
                "setField must normalize server-side selection state");
        requireContains(setFieldBody, "if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {\n            return failure(player, stack, draft, \"select_object_first\", \"message.codpattern.zombies.deploy.select_object_first\", \"\");\n        }",
                "setField must still reject map-registration field edits");
        requireContains(setFieldBody, "fields.put(fieldKey.trim(), fieldValue == null ? \"\" : fieldValue);",
                "setField must apply the requested field value to a copy of current fields");
        requireContains(setFieldBody, "ZombiesDeployDraft updated = draft.withFields(fields);",
                "setField must stage the merged fields before choosing pending or selected behavior");
        requireContains(setFieldBody, "if (draft.selectedIndex() < 0) {\n            ZombiesDeployTool.saveDraft(stack, updated);",
                "setField must stage fields for the next new object when no saved object is selected");
        requireContains(setFieldBody, "\"object.field_staged\"",
                "pending field edits must have a distinct staged status code");
        requireContains(setFieldBody, "return editObject(player, stack, updated, ZombiesDeployObjectEditor.Operation.UPDATE, \"object.field_updated\");",
                "selected-object field edits must still persist via UPDATE immediately");

        String normalizeDraftBody = methodBody(service, "private ZombiesDeployDraft normalizeDraft");
        requireContains(normalizeDraftBody, "int selectedIndex = count <= 0 || base.selectedIndex() < 0\n                ? -1\n                : Math.min(base.selectedIndex(), count - 1);",
                "normalizeDraft must preserve no-selection as -1 instead of clamping to first object");
        requireContains(normalizeDraftBody, "? ZombiesDeployObjectEditor.fieldsForSnapshotSelection(selectedObjects, objectType, selectedIndex)\n                        : defaultFields(player, objectType)",
                "normalizeDraft should use selected object fields only when an object is selected");

        String stepStatusesBody = methodBody(service, "private List<ZombiesDeploySnapshot.StepStatus> stepStatuses");
        requireContains(stepStatusesBody, "boolean hasPowerSwitch = resolved.powerSwitch().isPresent();",
                "step status may count deployed power switches");
        requireContains(stepStatusesBody, "boolean interactionComplete = hasWeaponWall\n                && hasAmmoBox\n                && hasArmorStation\n                && hasUltimateMachine\n                && hasSodaMachine;",
                "interaction completion must not require power switch");
        requireContains(stepStatusesBody, "+ \";powerSwitch=\" + (hasPowerSwitch ? \"1\" : \"0\")",
                "step status detail should report optional power switch count");
        requireAbsent(stepStatusesBody, "&& hasPowerSwitch", "power switch must not become an interaction blocker");
        requireAbsent(stepStatusesBody, "missing_power_switch", "step status must not report missing power as blocker");

        String captureWorldClickBody = methodBody(service, "public ZombiesDeployServiceResult<ZombiesDeploySnapshot> captureWorldClick");
        requireContains(captureWorldClickBody, "return deployWorldClick(player, stack, draft, placementPos, leftClick);",
                "object-stage world clicks must route to immediate deploy/update");
        requireContains(captureWorldClickBody, "ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())",
                "map registration clicks may still save map corner selection state");

        String deployWorldClickBody = methodBody(service, "private ZombiesDeployServiceResult<ZombiesDeploySnapshot> deployWorldClick");
        requireContains(deployWorldClickBody, "return deployBarrierWorldClick(player, stack, draft, objects, selectedIndex, placementPos, leftClick);",
                "barrier world clicks must use the two-endpoint barrier path");
        requireContains(deployWorldClickBody, "int selectedIndex = deployTargetIndex(\n                objectType,\n                normalizeTargetIndex(objects, objectType, draft.selectedIndex()),\n                leftClick);",
                "left-click deploy must be able to force repeatable object types into append mode");
        requireContains(deployWorldClickBody, "Map<String, String> fields = fieldsForWorldClickBase(player, objects, objectType, selectedIndex, draft);",
                "world-click adds must reuse pending property fields such as group and weight");
        requireContains(deployWorldClickBody, "applyWorldClickFields(player, objectType, fields, placementPos, leftClick, selectedIndex < 0);",
                "world clicks must write placement position into object fields before saving");
        requireContains(deployWorldClickBody, "ZombiesDeployObjectEditor.Operation operation = selectedIndex >= 0\n                ? ZombiesDeployObjectEditor.Operation.UPDATE\n                : ZombiesDeployObjectEditor.Operation.ADD;",
                "world clicks must decide between update and add from the resolved deploy target index");
        requireContains(deployWorldClickBody, "return editObject(player, stack, updated, operation, leftClick ? \"object.left_click_deployed\" : \"object.right_click_deployed\");",
                "world clicks must persist through editObject instead of only saving tool selection state");
        requireContains(deployWorldClickBody, "\"message.codpattern.zombies.deploy.right_click_noop\"",
                "right-click no-op object types must return an explicit no-op message");

        String deployBarrierWorldClickBody = methodBody(service, "private ZombiesDeployServiceResult<ZombiesDeploySnapshot> deployBarrierWorldClick");
        requireContains(deployBarrierWorldClickBody, "Map<String, String> fields = fieldsForWorldClickBase(player, objects, ZombiesDeployFieldSchema.BARRIER, selectedIndex, draft);",
                "barrier world clicks must reuse staged group/cost fields across the two-click add path");
        requireContains(deployBarrierWorldClickBody, "if (selectedIndex < 0) {\n            if (leftClick) {",
                "unselected barrier left click should be handled before creating an object");
        requireContains(deployBarrierWorldClickBody, "ZombiesDeployTool.setAreaPos1(stack, placementPos);",
                "unselected barrier left click should store the first endpoint on the held tool");
        requireContains(deployBarrierWorldClickBody, "BlockPos first = ZombiesDeployTool.getAreaPos1(stack);",
                "unselected barrier right click should read the stored first endpoint");
        requireContains(deployBarrierWorldClickBody, "ZombiesDeployObjectEditor.Operation operation = selectedIndex >= 0\n                ? ZombiesDeployObjectEditor.Operation.UPDATE\n                : ZombiesDeployObjectEditor.Operation.ADD;",
                "barrier right click with a stored endpoint should create, while selected barriers update");
        requireContains(deployBarrierWorldClickBody, "\"message.codpattern.zombies.deploy.barrier_area_first_required\"",
                "barrier right click without a first endpoint should explain the missing left click");

        String fieldsForWorldClickBaseBody = methodBody(service, "private Map<String, String> fieldsForWorldClickBase");
        requireContains(fieldsForWorldClickBaseBody, "draft.fields().isEmpty()\n                ? defaultFields(player, type)\n                : mergeDefaults(type, draft.fields())",
                "new object world-click fields must come from pending GUI edits when present");
        requireContains(fieldsForWorldClickBaseBody, "fields.computeIfPresent(\"objectId\", (key, ignored) -> \"\");",
                "forced add from an existing selection must not reuse the selected object's objectId");

        String applyWorldClickFieldsBody = methodBody(service, "private void applyWorldClickFields");
        requireContains(applyWorldClickFieldsBody, "case ZombiesDeployFieldSchema.INITIAL,\n                 ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {\n                setPosition(fields, \"pos\", placementPos);\n                applyHorizontalYawFromPlayer(fields, player);\n            }",
                "world click should use placement position and capture horizontal yaw for initial and zombie spawn points");
        requireContains(applyWorldClickFieldsBody, "case ZombiesDeployFieldSchema.BARRIER -> {\n                if (newObject) {\n                    setPosition(fields, \"areaFrom\", placementPos);\n                    setPosition(fields, \"areaTo\", placementPos);\n                    setPosition(fields, \"interaction\", placementPos);\n                } else {\n                    setPosition(fields, leftClick ? \"areaFrom\" : \"areaTo\", placementPos);\n                }\n            }",
                "selected barrier world clicks must update the selected endpoint using placement position");
        requireContains(applyWorldClickFieldsBody, "case ZombiesDeployFieldSchema.WEAPON_WALL,\n                 ZombiesDeployFieldSchema.AMMO_BOX,\n                 ZombiesDeployFieldSchema.ARMOR_STATION,\n                 ZombiesDeployFieldSchema.SODA_MACHINE,\n                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {\n                if (leftClick) {\n                    setPosition(fields, \"pos\", placementPos);\n                    setPosition(fields, \"interaction\", placementPos);\n                }\n            }",
                "single-point interaction objects must use left click for both pos and default interaction placement");
        requireContains(applyWorldClickFieldsBody, "case ZombiesDeployFieldSchema.POWER_SWITCH -> {\n                if (leftClick) {\n                    setPosition(fields, \"pos\", placementPos);\n                }\n            }",
                "power switch world clicks must only update position on left click using placement position");
        requireAbsent(applyWorldClickFieldsBody, ".above()", "world-click deploy path must not use clicked block above");
        requireAbsent(applyWorldClickFieldsBody, "else {\n                    setPosition(fields, \"interaction\"", "single-point right click must not update interaction fields");
        requireAbsent(applyWorldClickFieldsBody, "\"pitch\"", "world-click deploy path must not update pitch");

        String applyHorizontalYawBody = methodBody(service, "private void applyHorizontalYawFromPlayer");
        requireContains(applyHorizontalYawBody, "fields.put(\"yaw\", Float.toString(player.getYRot()));",
                "horizontal yaw helper must write yaw from player rotation");
        requireAbsent(applyHorizontalYawBody, "pitch", "horizontal yaw helper must not write pitch");

        String previewFromDraftBody = methodBody(preview, "public ZombiesDeployServiceResult<Void> refreshPreview");
        requireContains(previewFromDraftBody, "return refreshMapRegistrationPreview(player, draft);",
                "map registration stage must render all registered zombies map bounds through its own preview path");
        requireContains(previewFromDraftBody, "boolean showDraft = draft.selectedIndex() >= 0 || !draft.fields().isEmpty();",
                "unselected empty selection state must not generate a player-position draft preview");
        requireContains(previewFromDraftBody, ": Map.of();",
                "preview fields should stay empty when no object draft exists");

        String mapRegistrationPreviewBody = methodBody(preview, "private ZombiesDeployServiceResult<Void> refreshMapRegistrationPreview");
        requireContains(mapRegistrationPreviewBody, "List<ZombiesMap> maps = availableZombiesMaps();",
                "map registration preview must use all valid registered zombies maps");
        requireContains(mapRegistrationPreviewBody, "for (int i = 0; i < maps.size(); i++)",
                "map registration preview must send every zombies map boundary");
        requireContains(mapRegistrationPreviewBody, "getHeldPreviewMapKey(player, i)",
                "map registration preview map boundaries must use per-map preview keys");
        requireContains(mapRegistrationPreviewBody, "getHeldPreviewMapDraftKey(player)",
                "map registration preview should still show the in-progress map area when both points are set");

        String objectStagePreviewBody = methodBody(preview, "private ZombiesDeployServiceResult<Void> refreshPreview");
        requireContains(objectStagePreviewBody, "FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(\n                getHeldPreviewMapKey(player),",
                "object-stage preview must send only the selected map boundary");
        requireContains(objectStagePreviewBody, "sendCurrentObjectList(player, request, map.objects());",
                "object-stage preview must send all deployed positions for the selected object type in the selected map");

        String editObjectBody = methodBody(service, "private ZombiesDeployServiceResult<ZombiesDeploySnapshot> editObject");
        requireContains(editObjectBody, "ZombiesMapObjects previousObjects = map.objects();",
                "object edits must snapshot previous objects for rollback");
        requireContains(editObjectBody, "map.applyObjects(edit.objects());",
                "object edits must apply edited objects before persistence");
        requireContains(editObjectBody, "CodMapPersistence.saveMapOrRollback(map, () -> {",
                "object edits must save with rollback protection");
        requireContains(editObjectBody, "map.applyObjects(previousObjects);",
                "object edit rollback must restore the previous object graph");
        requireContains(editObjectBody, "\"message.codpattern.zombies.deploy.save_failed_rollback\"",
                "object edits must return a rollback failure message when persistence fails");
        requireContains(editObjectBody, "shouldRejectOccupiedPositionConflict(resolvedOperation)",
                "object edits must run occupied-position conflict validation before persistence");
        requireContains(editObjectBody, "findOccupiedPositionConflict(edit.objects())",
                "occupied-position conflicts must be detected from the edited object graph");
        requireContains(editObjectBody, "\"message.codpattern.zombies.deploy.duplicate_position\"",
                "duplicate-position failures must use a dedicated message");
        requireContains(editObjectBody, "PowerSwitchPlacementRollback powerSwitchRollback = null;",
                "power switch deployment must track block-placement rollback state");
        requireContains(editObjectBody, "placePowerSwitchBlock(player.serverLevel(), edit.objects().powerSwitch().get().pos())",
                "power switch deployment must place the configured switch block at the saved position");
        requireContains(editObjectBody, "restorePowerSwitchBlock(rollback);",
                "persistence rollback must restore the previous power switch block state");
        requireContains(editObjectBody, "restorePowerSwitchBlock(powerSwitchRollback);",
                "save exceptions must restore the previous power switch block state");

        String occupiedPositionConflictBody = firstExistingMethodBody(
                service,
                "private Optional<DuplicatePosition> findOccupiedPositionConflict",
                "private Optional<DuplicatePosition> findDuplicateSinglePoint");
        requireContains(occupiedPositionConflictBody, "interactionPos()",
                "occupied-position checks must consider interactionPos, not only object pos");
        requireContains(occupiedPositionConflictBody, "areaFrom()",
                "occupied-position checks must consider barrier areaFrom");
        requireContains(occupiedPositionConflictBody, "areaTo()",
                "occupied-position checks must consider barrier areaTo");
        requireContains(occupiedPositionConflictBody, "ZombiesDeployFieldSchema.BARRIER",
                "occupied-position checks must include barrier area/interaction positions");

        String powerSwitchPlacementBody = methodBody(service, "private PowerSwitchPlacementRollback placePowerSwitchBlock");
        requireContains(powerSwitchPlacementBody, "BlockState previousState = level.getBlockState(pos);",
                "power switch placement must snapshot the previous block state");
        requireContains(powerSwitchPlacementBody, "CodPatternBlockRegister.ZOMBIES_POWER_SWITCH.get().defaultBlockState()",
                "power switch placement must use the registered zombies power switch block");
        requireContains(powerSwitchPlacementBody, "level.setBlock(pos,",
                "power switch placement must write the block into the world");

        String restorePowerSwitchBlockBody = methodBody(service, "private void restorePowerSwitchBlock");
        requireContains(restorePowerSwitchBlockBody, "rollback.previousState()",
                "power switch rollback must restore the captured previous block state");
        requireContains(restorePowerSwitchBlockBody, "setBlock(rollback.pos(), rollback.previousState()",
                "power switch rollback must write the previous block back into the world");

        requireContains(worldToolItem, "ToolInteractionHit hit",
                "world tool interface must receive the clicked-face hit context");
        requireContains(toolInteractionHit, "BlockPos placementPos",
                "tool hit context must expose placement position");
        requireContains(toolInteractionHit, "clickedBlockPos.relative(clickedFace)",
                "placement position must be computed as clicked block relative to clicked face");
        requireContains(toolInteractionPacket, "private final Direction clickedFace;",
                "tool interaction packet must carry clicked face");
        requireContains(toolInteractionPacket, "buf.writeEnum(clickedFace == null ? Direction.UP : clickedFace);",
                "tool interaction packet encoder must write clicked face");
        requireContains(toolInteractionPacket, "face = buf.readEnum(Direction.class);",
                "tool interaction packet decoder must read clicked face");
        requireContains(toolInteractionPacket, "ToolInteractionHit.fromClicked(clickedPos, clickedFace)",
                "tool interaction packet handler must convert clicked block and face into hit context");
        requireContains(toolInteractionHandler, "target.getDirection()",
                "client interaction handler must send the hit face from block ray results");
        requireContains(toolInteractionHandler, "Direction face = event.getFace();",
                "client interaction handler must send the hit face from block interaction events");
        requireContains(toolInteractionHandler, "&& clickedFace == lastSentFace",
                "client de-duplication must include clicked face");

        Set<String> javaKeys = deployKeysFromJava();
        Map<Path, Set<String>> langKeysByPath = new LinkedHashMap<>();
        Map<Path, Map<String, String>> langValuesByPath = new LinkedHashMap<>();
        for (Path lang : LANG_FILES) {
            String json = read(lang);
            Set<String> langKeys = deployKeysFromJson(json);
            langKeysByPath.put(lang, langKeys);
            langValuesByPath.put(lang, deployValuesFromJson(json));
            for (String key : javaKeys) {
                if (key.endsWith(".")) {
                    continue;
                }
                requireContains(langKeys, key, lang + " must define Java-referenced deploy key");
            }
            requireContains(json, "\"gui.codpattern.zombies.deploy.section.objects_properties\"", lang + " must define objects/properties title");
            requireContains(json, "\"gui.codpattern.zombies.deploy.no_registered_maps\"", lang + " must define empty zombies map list label");
            requireContains(json, "\"gui.codpattern.zombies.deploy.click_to_deploy\"", lang + " must define click-to-deploy hint");
            requireContains(json, "\"gui.codpattern.zombies.deploy.legend.left_click\"", lang + " must define left-click legend");
            requireContains(json, "\"gui.codpattern.zombies.deploy.legend.right_click\"", lang + " must define right-click legend");
            requireContains(json, "\"tooltip.codpattern.zombies_deploy.left_click\"", lang + " must define deploy-tool left-click tooltip");
            requireContains(json, "\"tooltip.codpattern.zombies_deploy.right_click\"", lang + " must define deploy-tool right-click tooltip");
            requireContains(json, "\"message.codpattern.zombies.deploy.selections_saved\"", lang + " must define selection save message");
            requireContains(json, "\"message.codpattern.zombies.deploy.select_object_first\"", lang + " must define selected-object warning");
            requireContains(json, "\"message.codpattern.zombies.deploy.right_click_noop\"", lang + " must define right-click no-op message");
            requireContains(json, "\"message.codpattern.zombies.deploy.duplicate_position\"", lang + " must define duplicate-position message");
            requireContains(json, "\"message.codpattern.zombies.deploy.barrier_area_from\"", lang + " must define barrier first-point message");
            requireContains(json, "\"message.codpattern.zombies.deploy.barrier_area_first_required\"", lang + " must define barrier first-point requirement message");
            requireAbsent(json, "capture slot", lang + " must not expose old slot-capture wording");
            requireAbsent(json, "slot A", lang + " must not expose old slot A wording");
            requireAbsent(json, "slot B", lang + " must not expose old slot B wording");
            requireAbsent(json, "槽位 A", lang + " must not expose old slot A wording");
            requireAbsent(json, "槽位 B", lang + " must not expose old slot B wording");
            requireAbsent(json, "スロット A", lang + " must not expose old slot A wording");
            requireAbsent(json, "スロット B", lang + " must not expose old slot B wording");
            requireAbsent(json, "gui.codpattern.zombies.deploy.section.objects_draft", lang + " must not expose old draft section key");
            requireAbsent(json, "gui.codpattern.zombies.deploy.new_draft", lang + " must not expose new draft label");
            requireAbsent(json, "gui.codpattern.zombies.deploy.save_object", lang + " must not expose save object label");
            requireAbsent(json, "gui.codpattern.zombies.deploy.draft_unsaved", lang + " must not expose draft unsaved label");
            requireAbsent(json, "gui.codpattern.zombies.deploy.draft_synced", lang + " must not expose draft synced label");
            requireAbsent(json, "gui.codpattern.zombies.deploy.status.draft_unsaved", lang + " must not expose draft status label");
            requireAbsent(json, "message.codpattern.zombies.deploy.draft_saved", lang + " must not expose old draft saved message");
            for (String staleKey : List.of(
                    "gui.codpattern.zombies.deploy.refresh",
                    "gui.codpattern.zombies.deploy.save_selections",
                    "gui.codpattern.zombies.deploy.save_validate_mvp1",
                    "gui.codpattern.zombies.deploy.objects",
                    "gui.codpattern.zombies.deploy.fields",
                    "gui.codpattern.zombies.deploy.validation",
                    "gui.codpattern.zombies.deploy.step.interact_detail",
                    "gui.codpattern.zombies.deploy.map_existing_count",
                    "gui.codpattern.zombies.deploy.update",
                    "gui.codpattern.zombies.deploy.duplicate",
                    "gui.codpattern.zombies.deploy.insert",
                    "gui.codpattern.zombies.deploy.legend.slot_a",
                    "gui.codpattern.zombies.deploy.legend.slot_b",
                    "gui.codpattern.zombies.deploy.profile",
                    "gui.codpattern.zombies.deploy.yes",
                    "gui.codpattern.zombies.deploy.no",
                    "gui.codpattern.zombies.deploy.common",
                    "gui.codpattern.zombies.deploy.advanced",
                    "gui.codpattern.zombies.deploy.advanced_on",
                    "gui.codpattern.zombies.deploy.advanced_off",
                    "gui.codpattern.zombies.deploy.parsed_rows",
                    "message.codpattern.zombies.deploy.captured_player_pos",
                    "message.codpattern.zombies.deploy.captured_look_block",
                    "message.codpattern.zombies.deploy.action_not_implemented",
                    "message.codpattern.zombies.deploy.preview_not_implemented")) {
                requireAbsent(json, "\"" + staleKey + "\"", lang + " must not expose stale deploy localization key " + staleKey);
            }
        }
        assertSameDeployKeySet(langKeysByPath);
        assertSameDeployPlaceholderCounts(langValuesByPath);

        System.out.println("PASS zombies deploy GUI static contract compat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static Set<String> deployKeysFromJava() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (Path path : KEY_SOURCE_FILES) {
            Matcher matcher = JAVA_DEPLOY_KEY.matcher(read(path));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    private static Set<String> deployKeysFromJson(String json) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = JSON_DEPLOY_KEY.matcher(json);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static Map<String, String> deployValuesFromJson(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = JSON_DEPLOY_ENTRY.matcher(json);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        return values;
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private static void requireContains(Set<String> values, String expected, String message) {
        if (!values.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private static void requireAbsent(String text, String unexpected, String message) {
        if (text.contains(unexpected)) {
            throw new AssertionError(message + ": found `" + unexpected + "`");
        }
    }

    private static void assertSameDeployKeySet(Map<Path, Set<String>> langKeysByPath) {
        Path baselinePath = null;
        Set<String> baseline = null;
        for (Map.Entry<Path, Set<String>> entry : langKeysByPath.entrySet()) {
            if (baseline == null) {
                baselinePath = entry.getKey();
                baseline = entry.getValue();
                continue;
            }
            Set<String> missing = new LinkedHashSet<>(baseline);
            missing.removeAll(entry.getValue());
            Set<String> extra = new LinkedHashSet<>(entry.getValue());
            extra.removeAll(baseline);
            if (!missing.isEmpty() || !extra.isEmpty()) {
                throw new AssertionError(entry.getKey() + " deploy key set differs from " + baselinePath
                        + "; missing=" + missing + "; extra=" + extra);
            }
        }
    }

    private static void assertSameDeployPlaceholderCounts(Map<Path, Map<String, String>> langValuesByPath) {
        Path baselinePath = null;
        Map<String, String> baseline = null;
        for (Map.Entry<Path, Map<String, String>> entry : langValuesByPath.entrySet()) {
            if (baseline == null) {
                baselinePath = entry.getKey();
                baseline = entry.getValue();
                continue;
            }
            for (Map.Entry<String, String> baselineEntry : baseline.entrySet()) {
                String key = baselineEntry.getKey();
                String value = entry.getValue().get(key);
                if (value == null) {
                    continue;
                }
                int expected = placeholderCount(baselineEntry.getValue());
                int actual = placeholderCount(value);
                if (expected != actual) {
                    throw new AssertionError(entry.getKey() + " placeholder count differs from "
                            + baselinePath + " for " + key + "; expected=" + expected + "; actual=" + actual);
                }
            }
        }
    }

    private static void requireSingleObjectPolicy(String fieldSchema) {
        Map<String, Boolean> expected = Map.of(
                "INITIAL", false,
                "ZOMBIE_SPAWN", false,
                "BARRIER", false,
                "WEAPON_WALL", false,
                "AMMO_BOX", false,
                "ARMOR_STATION", false,
                "POWER_SWITCH", true,
                "SODA_MACHINE", false,
                "ULTIMATE_MACHINE", false);
        for (Map.Entry<String, Boolean> entry : expected.entrySet()) {
            Pattern schemaEntry = Pattern.compile(
                    "new ObjectTypeSchema\\(" + entry.getKey()
                            + ",\\s*\"[^\"]+\",\\s*(?:true|false),\\s*"
                            + entry.getValue()
                            + ",\\s*List\\.of\\(",
                    Pattern.DOTALL);
            if (!schemaEntry.matcher(fieldSchema).find()) {
                throw new AssertionError(entry.getKey() + " singleObject policy must be "
                        + entry.getValue());
            }
        }
    }

    private static void requirePanelFitsMinimumResolution(String screen, int minimumWidth, int minimumHeight) {
        int panelWidth = intConstant(screen, "PANEL_WIDTH");
        int panelHeight = intConstant(screen, "PANEL_HEIGHT");
        if (panelWidth + 16 > minimumWidth) {
            throw new AssertionError("deploy GUI panel width " + panelWidth
                    + " should fit minimum width " + minimumWidth + " with 8px margins");
        }
        if (panelHeight + 16 > minimumHeight) {
            throw new AssertionError("deploy GUI panel height " + panelHeight
                    + " should fit minimum height " + minimumHeight + " with 8px margins");
        }
        requireContains(screen,
                "return Math.max(8, (this.width - PANEL_WIDTH) / 2);",
                "panel left anchor should preserve an 8px fallback margin");
        requireContains(screen,
                "return Math.max(8, (this.height - PANEL_HEIGHT) / 2);",
                "panel top anchor should preserve an 8px fallback margin");
    }

    private static int intConstant(String text, String constantName) {
        Pattern pattern = Pattern.compile("private static final int " + constantName + " = (\\d+);");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("missing int constant `" + constantName + "`");
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static int placeholderCount(String value) {
        int count = 0;
        for (int index = 0; index < value.length() - 1; index++) {
            if (value.charAt(index) == '%' && value.charAt(index + 1) == 's') {
                count++;
                index++;
            }
        }
        return count;
    }

    private static String methodBody(String text, String signaturePrefix) {
        int signature = text.indexOf(signaturePrefix);
        if (signature < 0) {
            throw new AssertionError("missing method signature `" + signaturePrefix + "`");
        }
        int openBrace = text.indexOf('{', signature);
        if (openBrace < 0) {
            throw new AssertionError("missing method body for `" + signaturePrefix + "`");
        }
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(openBrace, i + 1);
                }
            }
        }
        throw new AssertionError("unterminated method body for `" + signaturePrefix + "`");
    }

    private static String firstExistingMethodBody(String text, String... signaturePrefixes) {
        for (String signaturePrefix : signaturePrefixes) {
            if (text.contains(signaturePrefix)) {
                return methodBody(text, signaturePrefix);
            }
        }
        throw new AssertionError("missing method signature from " + List.of(signaturePrefixes));
    }
}
