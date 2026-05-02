package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.SpawnPointTool;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SpawnPointToolScreen extends Screen {
    private static final int PANEL_WIDTH = 326;
    private static final int PANEL_HEIGHT = 304;
    private static final int SCREEN_OVERLAY = 0x5A000000;
    private static final int PANEL_BACKGROUND = 0xD0191D22;
    private static final int PANEL_BORDER = 0xFFB58A42;

    private List<String> availableTypes;
    private List<String> availableMaps;
    private List<String> availableTeams;
    private List<String> availableKinds;
    private List<String> availableAreaLayers;
    private List<SpawnPointData> spawnPoints;
    private List<AreaData> areas;
    private String selectedType;
    private String selectedMap;
    private String selectedTeam;
    private String selectedKind;
    private int selectedIndex;
    private String editMode;
    private String selectedAreaLayer;
    private int selectedAreaIndex;
    private BlockPos areaPos1;
    private BlockPos areaPos2;

    private Button modeButton;
    private Button typeButton;
    private Button mapButton;
    private Button teamButton;
    private Button kindButton;
    private Button prevButton;
    private Button nextButton;
    private Button addAreaButton;
    private Button deleteButton;
    private Button clearButton;
    private Button mergeButton;

    public SpawnPointToolScreen(OpenSpawnPointToolScreenS2CPacket data) {
        super(Component.translatable("gui.fpsm.spawn_point_tool.title"));
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.availableMaps = new ArrayList<>(data.availableMaps());
        this.availableTeams = new ArrayList<>(data.availableTeams());
        this.availableKinds = new ArrayList<>(data.availableKinds());
        this.availableAreaLayers = new ArrayList<>(data.availableAreaLayers());
        this.spawnPoints = new ArrayList<>(data.spawnPoints());
        this.areas = new ArrayList<>(data.areas());
        this.selectedType = data.selectedType();
        this.selectedMap = data.selectedMap();
        this.selectedTeam = data.selectedTeam();
        this.selectedKind = data.selectedKind();
        this.selectedIndex = data.selectedIndex();
        this.editMode = data.editMode();
        this.selectedAreaLayer = data.selectedAreaLayer();
        this.selectedAreaIndex = data.selectedAreaIndex();
        this.areaPos1 = data.areaPos1();
        this.areaPos2 = data.areaPos2();
    }

    @Override
    protected void init() {
        int left = 18;
        int top = Math.max(18, (this.height - PANEL_HEIGHT) / 2);

        this.modeButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleMode())
                .pos(left + 124, top + 24)
                .size(184, 20)
                .build());
        this.typeButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleType())
                .pos(left + 124, top + 54)
                .size(184, 20)
                .build());
        this.mapButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleMap())
                .pos(left + 124, top + 84)
                .size(184, 20)
                .build());
        this.teamButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleTeam())
                .pos(left + 124, top + 114)
                .size(184, 20)
                .build());
        this.kindButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleKind())
                .pos(left + 124, top + 144)
                .size(184, 20)
                .build());

        this.prevButton = this.addRenderableWidget(new Button.Builder(Component.literal("<"), button -> stepIndex(-1))
                .pos(left + 124, top + 174)
                .size(24, 20)
                .build());
        this.nextButton = this.addRenderableWidget(new Button.Builder(Component.literal(">"), button -> stepIndex(1))
                .pos(left + 284, top + 174)
                .size(24, 20)
                .build());

        this.addAreaButton = this.addRenderableWidget(new Button.Builder(
                Component.literal("Add area"),
                button -> sendAction(SpawnPointToolActionC2SPacket.Action.ADD_AREA))
                .pos(left + 18, top + 204)
                .size(290, 20)
                .build());
        this.deleteButton = this.addRenderableWidget(new Button.Builder(
                Component.translatable("gui.fpsm.spawn_point_tool.delete"),
                button -> sendAction(SpawnPointToolActionC2SPacket.Action.DELETE_SELECTED))
                .pos(left + 18, top + 228)
                .size(94, 20)
                .build());
        this.clearButton = this.addRenderableWidget(new Button.Builder(
                Component.translatable("gui.fpsm.spawn_point_tool.clear"),
                button -> sendAction(SpawnPointToolActionC2SPacket.Action.CLEAR_TEAM))
                .pos(left + 116, top + 228)
                .size(94, 20)
                .build());
        this.mergeButton = this.addRenderableWidget(new Button.Builder(
                Component.translatable("gui.fpsm.spawn_point_tool.merge"),
                button -> sendAction(SpawnPointToolActionC2SPacket.Action.MERGE_DYNAMIC))
                .pos(left + 214, top + 228)
                .size(94, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.close"), button -> onClose())
                .pos(left + 18, top + 276)
                .size(290, 20)
                .build());

        updateButtonLabels();
    }

    public void applyData(OpenSpawnPointToolScreenS2CPacket data) {
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.availableMaps = new ArrayList<>(data.availableMaps());
        this.availableTeams = new ArrayList<>(data.availableTeams());
        this.availableKinds = new ArrayList<>(data.availableKinds());
        this.availableAreaLayers = new ArrayList<>(data.availableAreaLayers());
        this.spawnPoints = new ArrayList<>(data.spawnPoints());
        this.areas = new ArrayList<>(data.areas());
        this.selectedType = data.selectedType();
        this.selectedMap = data.selectedMap();
        this.selectedTeam = data.selectedTeam();
        this.selectedKind = data.selectedKind();
        this.selectedIndex = data.selectedIndex();
        this.editMode = data.editMode();
        this.selectedAreaLayer = data.selectedAreaLayer();
        this.selectedAreaIndex = data.selectedAreaIndex();
        this.areaPos1 = data.areaPos1();
        this.areaPos2 = data.areaPos2();
        updateButtonLabels();
    }

    @Override
    public void onClose() {
        sendAction(SpawnPointToolActionC2SPacket.Action.SAVE_SELECTIONS);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = 18;
        int top = Math.max(18, (this.height - PANEL_HEIGHT) / 2);
        guiGraphics.fill(0, 0, this.width, this.height, SCREEN_OVERLAY);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BACKGROUND);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + 1, PANEL_BORDER);
        guiGraphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(left, top, left + 1, top + PANEL_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BORDER);

        guiGraphics.drawString(this.font, this.title, left + 10, top + 8, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.literal("Mode"), left + 12, top + 30, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.spawn_point_tool.type"), left + 12, top + 60, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.spawn_point_tool.map"), left + 12, top + 90, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, modeSpecificTeamLabel(), left + 12, top + 120, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, modeSpecificLayerLabel(), left + 12, top + 150, 0xF1D9B0, false);
        guiGraphics.drawString(this.font, currentCountLabel(), left + 12, top + 180, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, currentEntryLabel(), left + 160, top + 180, 0xD7E3EA, false);
        guiGraphics.drawString(this.font, currentEntryDetail(), left + 12, top + 198, 0xA4C4D3, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void cycleMode() {
        if (isAreaMode()) {
            this.editMode = SpawnPointTool.EDIT_MODE_POINT;
        } else if (!availableAreaLayers.isEmpty()) {
            this.editMode = SpawnPointTool.EDIT_MODE_AREA;
        }
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleType() {
        if (availableTypes.isEmpty()) {
            return;
        }
        int currentIndex = availableTypes.indexOf(selectedType);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableTypes.size();
        this.selectedType = availableTypes.get(nextIndex);
        this.selectedMap = "";
        this.selectedTeam = "";
        this.selectedAreaLayer = "";
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleMap() {
        if (availableMaps.isEmpty()) {
            return;
        }
        int currentIndex = availableMaps.indexOf(selectedMap);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableMaps.size();
        this.selectedMap = availableMaps.get(nextIndex);
        this.selectedTeam = "";
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleTeam() {
        if (isAreaMode()) {
            return;
        }
        if (availableTeams.isEmpty()) {
            return;
        }
        int currentIndex = availableTeams.indexOf(selectedTeam);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableTeams.size();
        this.selectedTeam = availableTeams.get(nextIndex);
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleKind() {
        if (isAreaMode()) {
            cycleAreaLayer();
            return;
        }
        if (availableKinds.isEmpty()) {
            return;
        }
        int currentIndex = availableKinds.indexOf(selectedKind);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableKinds.size();
        this.selectedKind = availableKinds.get(nextIndex);
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void cycleAreaLayer() {
        if (availableAreaLayers.isEmpty()) {
            return;
        }
        int currentIndex = availableAreaLayers.indexOf(selectedAreaLayer);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % availableAreaLayers.size();
        this.selectedAreaLayer = availableAreaLayers.get(nextIndex);
        this.selectedAreaIndex = -1;
        sendAction(SpawnPointToolActionC2SPacket.Action.REFRESH);
    }

    private void stepIndex(int offset) {
        if (isAreaMode()) {
            if (areas.isEmpty()) {
                selectedAreaIndex = -1;
            } else {
                int base = selectedAreaIndex < 0 ? 0 : selectedAreaIndex;
                selectedAreaIndex = Math.max(0, Math.min(base + offset, areas.size() - 1));
            }
            updateButtonLabels();
            return;
        }
        if (spawnPoints.isEmpty()) {
            selectedIndex = -1;
        } else {
            int base = selectedIndex < 0 ? 0 : selectedIndex;
            selectedIndex = Math.max(0, Math.min(base + offset, spawnPoints.size() - 1));
        }
        updateButtonLabels();
    }

    private void updateButtonLabels() {
        if (this.typeButton == null) {
            return;
        }
        boolean areaMode = isAreaMode();
        this.modeButton.setMessage(Component.literal(areaMode ? "Area" : "Point"));
        this.modeButton.active = !availableAreaLayers.isEmpty();
        this.typeButton.setMessage(Component.literal(selectedType.isBlank() ? "-" : selectedType));
        this.mapButton.setMessage(Component.literal(selectedMap.isBlank() ? "-" : selectedMap));
        this.teamButton.setMessage(Component.literal(areaMode ? areaPositionLabel() : selectedTeam.isBlank() ? "-" : selectedTeam));
        this.teamButton.active = !areaMode && !availableTeams.isEmpty();
        this.kindButton.setMessage(Component.literal(areaMode
                ? selectedAreaLayer.isBlank() ? "-" : selectedAreaLayer
                : selectedKind.isBlank() ? "-" : selectedKind));
        this.kindButton.active = areaMode ? availableAreaLayers.size() > 1 : availableKinds.size() > 1;
        boolean hasEntries = areaMode ? !areas.isEmpty() : !spawnPoints.isEmpty();
        this.prevButton.active = hasEntries;
        this.nextButton.active = hasEntries;
        this.deleteButton.active = hasEntries;
        this.clearButton.active = hasEntries;
        this.addAreaButton.visible = areaMode;
        this.addAreaButton.active = areaMode && !selectedAreaLayer.isBlank() && areaPos1 != null && areaPos2 != null;
        this.mergeButton.visible = !areaMode;
        this.mergeButton.active = !areaMode && canMergeDynamicPoints();
    }

    private Component modeSpecificTeamLabel() {
        return isAreaMode()
                ? Component.literal("Draft area")
                : Component.translatable("gui.fpsm.spawn_point_tool.team");
    }

    private Component modeSpecificLayerLabel() {
        return isAreaMode()
                ? Component.literal("Area layer")
                : Component.translatable("gui.fpsm.spawn_point_tool.kind");
    }

    private Component currentCountLabel() {
        return isAreaMode()
                ? Component.literal("Areas: " + areas.size())
                : Component.translatable("gui.fpsm.spawn_point_tool.count", this.spawnPoints.size());
    }

    private Component currentEntryLabel() {
        if (isAreaMode()) {
            if (areas.isEmpty() || selectedAreaIndex < 0 || selectedAreaIndex >= areas.size()) {
                return Component.translatable("gui.fpsm.spawn_point_tool.current", "-");
            }
            return Component.translatable("gui.fpsm.spawn_point_tool.current", (selectedAreaIndex + 1) + "/" + areas.size());
        }
        if (spawnPoints.isEmpty() || selectedIndex < 0 || selectedIndex >= spawnPoints.size()) {
            return Component.translatable("gui.fpsm.spawn_point_tool.current", "-");
        }
        return Component.translatable("gui.fpsm.spawn_point_tool.current", (selectedIndex + 1) + "/" + spawnPoints.size());
    }

    private Component currentEntryDetail() {
        if (isAreaMode()) {
            if (areas.isEmpty() || selectedAreaIndex < 0 || selectedAreaIndex >= areas.size()) {
                return Component.literal(areaPositionLabel());
            }
            AreaData area = areas.get(selectedAreaIndex);
            return Component.literal("from " + MapCreatorTool.formatPos(area.pos1())
                    + " to " + MapCreatorTool.formatPos(area.pos2()));
        }
        if (spawnPoints.isEmpty() || selectedIndex < 0 || selectedIndex >= spawnPoints.size()) {
            return Component.translatable("gui.fpsm.spawn_point_tool.no_point");
        }
        SpawnPointData data = spawnPoints.get(selectedIndex);
        return Component.literal(String.format(
                "X %d Y %d Z %d  Yaw %.1f",
                data.getX(), data.getY(), data.getZ(), data.getYaw()
        ));
    }

    private void sendAction(SpawnPointToolActionC2SPacket.Action action) {
        FPSMatch.sendToServer(new SpawnPointToolActionC2SPacket(
                action,
                this.selectedType,
                this.selectedMap,
                this.selectedTeam,
                this.selectedKind,
                this.selectedIndex,
                this.editMode,
                this.selectedAreaLayer,
                this.selectedAreaIndex
        ));
    }

    private boolean canMergeDynamicPoints() {
        return ModeMapEditorSchemas.supportsDynamicRespawnMerge(selectedType)
                && SpawnPointKind.DYNAMIC_CANDIDATE.serializedName().equals(selectedKind)
                && !selectedMap.isBlank()
                && availableTeams.size() == 2;
    }

    private boolean isAreaMode() {
        return SpawnPointTool.EDIT_MODE_AREA.equals(editMode);
    }

    private String areaPositionLabel() {
        return MapCreatorTool.formatPos(areaPos1) + " -> " + MapCreatorTool.formatPos(areaPos2);
    }
}
