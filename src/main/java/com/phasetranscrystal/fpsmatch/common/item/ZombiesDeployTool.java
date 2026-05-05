package com.phasetranscrystal.fpsmatch.common.item;

import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployFieldSchema;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployDraft;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployPreviewService;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployServiceResult;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.CreatorToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ZombiesDeployToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ZombiesDeployTool extends CreatorToolItem implements WorldToolItem {
    private static final String OBJECT_TYPE_TAG = "SelectedZombiesObjectType";
    private static final String SELECTED_INDEX_TAG = "SelectedZombiesObjectIndex";
    private static final String PROFILE_TAG = "SelectedZombiesValidationProfile";
    private static final String DRAFT_FIELDS_TAG = "ZombiesDeployDraftFields";
    private static final String AREA_POS_1_TAG = "ZombiesDeployAreaPos1";
    private static final String AREA_POS_2_TAG = "ZombiesDeployAreaPos2";
    private static final String HELD_AREA_PREVIEW_STATE_TAG = "HeldZombiesDeployAreaPreviewState";
    private static final int AREA_PREVIEW_COLOR = 0xFFFFFFFF;
    private static final int HELD_AREA_PREVIEW_REFRESH_INTERVAL = 10;

    public ZombiesDeployTool(Properties properties) {
        super(properties);
    }

    @Override
    public void handleWorldInteraction(ServerPlayer player, ItemStack stack, ToolInteractionAction action, BlockPos clickedPos) {
        switch (action) {
            case CTRL_RIGHT_CLICK -> ZombiesDeployToolActionC2SPacket.sendScreen(player, stack, getDraft(stack));
            case LEFT_CLICK_BLOCK -> {
                if (clickedPos == null) {
                    return;
                }
                captureAreaPosition(player, stack, clickedPos, true);
                player.displayClientMessage(Component.translatable(
                        "message.codpattern.zombies.deploy.area_pos1",
                        MapCreatorTool.formatPos(clickedPos)).withStyle(ChatFormatting.AQUA), true);
            }
            case RIGHT_CLICK_BLOCK -> {
                if (clickedPos == null) {
                    return;
                }
                captureAreaPosition(player, stack, clickedPos, false);
                player.displayClientMessage(Component.translatable(
                        "message.codpattern.zombies.deploy.area_pos2",
                        MapCreatorTool.formatPos(clickedPos)).withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    public void syncHeldPreview(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || !(stack.getItem() instanceof ZombiesDeployTool)) {
            clearHeldPreview(player);
            return;
        }
        ZombiesDeployServiceResult<Void> result = ZombiesDeployPreviewService.instance().refreshPreview(player, getDraft(stack));
        if (result.success()) {
            player.getPersistentData().remove(HELD_AREA_PREVIEW_STATE_TAG);
            return;
        }
        syncStandaloneAreaPreview(player, stack);
    }

    public static void clearHeldPreview(ServerPlayer player) {
        ZombiesDeployPreviewService.clearHeldPreview(player);
        clearStandaloneAreaPreview(player);
    }

    private void captureAreaPosition(ServerPlayer player, ItemStack stack, BlockPos pos, boolean first) {
        if (first) {
            setAreaPos1(stack, pos);
        } else {
            setAreaPos2(stack, pos);
        }

        ZombiesDeployDraft draft = getDraft(stack);
        Map<String, String> fields = mergeDraftFields(draft);
        fields.put("dimension", player.serverLevel().dimension().location().toString());
        setPositionField(fields, first ? "areaFrom" : "areaTo", pos);
        saveDraft(stack, draft.withFields(fields));
    }

    private Map<String, String> mergeDraftFields(ZombiesDeployDraft draft) {
        ZombiesDeployDraft resolved = draft == null ? ZombiesDeployDraft.empty() : draft;
        Map<String, String> fields = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(resolved.objectType()));
        resolved.fields().forEach((key, value) -> {
            if (fields.containsKey(key)) {
                fields.put(key, value == null ? "" : value);
            }
        });
        return fields;
    }

    private void setPositionField(Map<String, String> fields, String prefix, BlockPos pos) {
        if (!fields.containsKey(prefix + "X")) {
            return;
        }
        fields.put(prefix + "X", Integer.toString(pos.getX()));
        fields.put(prefix + "Y", Integer.toString(pos.getY()));
        fields.put(prefix + "Z", Integer.toString(pos.getZ()));
    }

    private void syncStandaloneAreaPreview(ServerPlayer player, ItemStack stack) {
        BlockPos pos1 = getAreaPos1(stack);
        BlockPos pos2 = getAreaPos2(stack);
        if (pos1 == null || pos2 == null) {
            clearStandaloneAreaPreview(player);
            return;
        }

        String signature = pos1.asLong() + "|" + pos2.asLong();
        String previousSignature = player.getPersistentData().getString(HELD_AREA_PREVIEW_STATE_TAG);
        if (signature.equals(previousSignature) && player.tickCount % HELD_AREA_PREVIEW_REFRESH_INTERVAL != 0) {
            return;
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                getHeldAreaPreviewKey(player),
                Component.literal("Zombies area draft"),
                AREA_PREVIEW_COLOR,
                new AreaData(pos1, pos2)));
        player.getPersistentData().putString(HELD_AREA_PREVIEW_STATE_TAG, signature);
    }

    private static void clearStandaloneAreaPreview(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!player.getPersistentData().contains(HELD_AREA_PREVIEW_STATE_TAG)) {
            return;
        }
        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        player.getPersistentData().remove(HELD_AREA_PREVIEW_STATE_TAG);
    }

    private static String getHeldPreviewPrefix(ServerPlayer player) {
        return "held_tool_preview:zombies_deploy:" + player.getUUID() + ":";
    }

    private static String getHeldAreaPreviewKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "area_draft";
    }

    public static ZombiesDeployDraft getDraft(ItemStack stack) {
        return new ZombiesDeployDraft(
                getSelectedMap(stack),
                getSelectedObjectType(stack),
                getSelectedIndex(stack),
                getProfileKey(stack),
                getDraftFields(stack));
    }

    public static void saveDraft(ItemStack stack, ZombiesDeployDraft draft) {
        ZombiesDeployDraft resolved = draft == null ? ZombiesDeployDraft.empty() : draft;
        setSelectedMap(stack, resolved.selectedMap());
        setSelectedObjectType(stack, resolved.objectType());
        setSelectedIndex(stack, resolved.selectedIndex());
        setProfileKey(stack, resolved.profileKey());
        setDraftFields(stack, resolved.fields());
    }

    public static void setSelectedMap(ItemStack stack, String selectedMap) {
        setStringTag(stack, MAP_TAG, selectedMap);
    }

    public static String getSelectedMap(ItemStack stack) {
        return getStringTag(stack, MAP_TAG);
    }

    public static void setSelectedObjectType(ItemStack stack, String objectType) {
        setStringTag(stack, OBJECT_TYPE_TAG, ZombiesDeployFieldSchema.normalizeObjectType(objectType));
    }

    public static String getSelectedObjectType(ItemStack stack) {
        return ZombiesDeployFieldSchema.normalizeObjectType(getStringTag(stack, OBJECT_TYPE_TAG));
    }

    public static void setSelectedIndex(ItemStack stack, int selectedIndex) {
        stack.getOrCreateTag().putInt(SELECTED_INDEX_TAG, Math.max(-1, selectedIndex));
    }

    public static int getSelectedIndex(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(SELECTED_INDEX_TAG, Tag.TAG_INT)
                ? -1
                : tag.getInt(SELECTED_INDEX_TAG);
    }

    public static void setProfileKey(ItemStack stack, String profileKey) {
        setStringTag(stack, PROFILE_TAG, ZombiesDeployFieldSchema.normalizeProfile(profileKey));
    }

    public static String getProfileKey(ItemStack stack) {
        return ZombiesDeployFieldSchema.normalizeProfile(getStringTag(stack, PROFILE_TAG));
    }

    public static void setDraftFields(ItemStack stack, Map<String, String> fields) {
        CompoundTag draftTag = new CompoundTag();
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    draftTag.putString(key, value == null ? "" : value);
                }
            });
        }
        stack.getOrCreateTag().put(DRAFT_FIELDS_TAG, draftTag);
    }

    public static Map<String, String> getDraftFields(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DRAFT_FIELDS_TAG, Tag.TAG_COMPOUND)) {
            return Map.of();
        }
        CompoundTag draftTag = tag.getCompound(DRAFT_FIELDS_TAG);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String key : draftTag.getAllKeys()) {
            fields.put(key, draftTag.getString(key));
        }
        return fields;
    }

    public static void setAreaPos1(ItemStack stack, BlockPos pos) {
        setBlockPos(stack, AREA_POS_1_TAG, pos);
    }

    public static void setAreaPos2(ItemStack stack, BlockPos pos) {
        setBlockPos(stack, AREA_POS_2_TAG, pos);
    }

    public static BlockPos getAreaPos1(ItemStack stack) {
        return getBlockPos(stack, AREA_POS_1_TAG);
    }

    public static BlockPos getAreaPos2(ItemStack stack) {
        return getBlockPos(stack, AREA_POS_2_TAG);
    }

    private static void setBlockPos(ItemStack stack, String tag, BlockPos pos) {
        CompoundTag compoundTag = stack.getOrCreateTag();
        if (pos == null) {
            compoundTag.remove(tag);
            return;
        }
        compoundTag.putLong(tag, pos.asLong());
    }

    private static BlockPos getBlockPos(ItemStack stack, String tag) {
        CompoundTag compoundTag = stack.getTag();
        if (compoundTag == null || !compoundTag.contains(tag, Tag.TAG_LONG)) {
            return null;
        }
        return BlockPos.of(compoundTag.getLong(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltip, isAdvanced);
        tooltip.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.selected_map")
                .append(": ")
                .append(Component.literal(getSelectedMap(stack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedMap(stack)).withStyle(ChatFormatting.GREEN)));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.selected_type")
                .append(": ")
                .append(Component.literal(getSelectedObjectType(stack)).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.selected_index")
                .append(": ")
                .append(Component.literal(Integer.toString(getSelectedIndex(stack))).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.area_pos1")
                .append(": ")
                .append(Component.literal(MapCreatorTool.formatPos(getAreaPos1(stack))).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.area_pos2")
                .append(": ")
                .append(Component.literal(MapCreatorTool.formatPos(getAreaPos2(stack))).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.left_click"));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.right_click"));
        tooltip.add(Component.translatable("tooltip.codpattern.zombies_deploy.ctrl_right_click"));
    }
}
