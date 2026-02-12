package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.compat.lrtactical.LrTacticalClientApi;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.SelectBackpackPacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 背包选择按钮 - COD2022风格
 */
public class BackPackSelectButton extends Button {

    private final Integer BAGSERIAL;
    private int focusedtimes = 0;
    private int hoverTicks = 0;
    private final BackpackConfig.Backpack backpack;
    private final boolean isCurrentlySelected;

    // 武器信息缓存
    private Map<String, WeaponInfo> weaponInfoCache = new HashMap<>();

    public static class WeaponInfo {
        public ResourceLocation texture;
        public Component weaponName;
        public Component packName;
        public ItemStack itemStack;

        public WeaponInfo(ResourceLocation texture, Component weaponName, Component packName, ItemStack itemStack) {
            this.texture = texture;
            this.weaponName = weaponName;
            this.packName = packName;
            this.itemStack = itemStack;
        }
    }

    public BackPackSelectButton(int x, int y, int width, int height, int bagserial) {
        this(x, y, width, height, bagserial, null, false);
    }

    public BackPackSelectButton(int x, int y, int width, int height, int bagserial, BackpackConfig.Backpack backpack, boolean isSelected) {
        super(x, y, width, height, Component.literal("choose your bag"), button -> {
            ModNetworkChannel.sendToServer(new SelectBackpackPacket(bagserial));
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(null);
            });
        }, DEFAULT_NARRATION);

        this.BAGSERIAL = bagserial;
        this.backpack = backpack;
        this.isCurrentlySelected = isSelected;

        if (backpack != null) {
            initWeaponInfo();
            // Tooltip暂时禁用
            // this.setTooltip(createBackpackTooltip());
        }
    }

    private void initWeaponInfo() {
        if (backpack == null || backpack.getItem_MAP() == null) return;

        for (Map.Entry<String, BackpackConfig.Backpack.ItemData> entry : backpack.getItem_MAP().entrySet()) {
            String type = entry.getKey();
            BackpackConfig.Backpack.ItemData itemData = entry.getValue();

            try {
                ResourceLocation itemId = ResourceLocation.tryParse(itemData.getItem());
                if (itemId == null) {
                    continue;
                }
                ItemStack weaponStack = new ItemStack(
                        Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(itemId)));

                if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                    try {
                        CompoundTag tag = TagParser.parseTag(itemData.getNbt());
                        weaponStack.setTag(tag);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                weaponStack.setCount(itemData.getCount());

                WeaponInfo info = weaponStack.getItem() instanceof IGun
                        ? extractWeaponInfo(weaponStack)
                        : extractItemInfo(weaponStack);
                if (info != null) {
                    weaponInfoCache.put(type, info);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private WeaponInfo extractWeaponInfo(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) return null;

        try {
            ResourceLocation texture = null;
            var displayOpt = TimelessAPI.getGunDisplay(weapon);
            if (displayOpt.isPresent()) {
                texture = displayOpt.get().getHUDTexture();
            }

            Component packName = null;
            if (weapon.getItem() instanceof IGun iGun) {
                ResourceLocation gunId = iGun.getGunId(weapon);
                PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(gunId);
                if (packInfoObject != null) {
                    packName = Component.translatable(packInfoObject.getName())
                            .withStyle(ChatFormatting.BLUE)
                            .withStyle(ChatFormatting.ITALIC);
                }
            }

            Component weaponName = weapon.getHoverName();

            return new WeaponInfo(texture, weaponName, packName, weapon);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** 非 TaCZ 枪械物品（近战、投掷物等）的信息提取 */
    private WeaponInfo extractItemInfo(ItemStack item) {
        if (item == null || item.isEmpty()) return null;

        try {
            Component weaponName = item.getHoverName();
            Component packName = LrTacticalClientApi.getLrItemPackName(item);
            return new WeaponInfo(null, weaponName, packName, item);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<String, WeaponInfo> getWeaponInfoCache() {
        return weaponInfoCache;
    }

    private Tooltip createBackpackTooltip() {
        List<Component> tooltipLines = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        int maxWidth = 180;

        String displayName = getDisplayNameRaw();
        tooltipLines.add(Component.literal("§e§l" + displayName + " §7(#" + BAGSERIAL + ")"));
        tooltipLines.add(Component.literal("§7----------------"));
        String primaryName = getWeaponDisplayName("primary");
        String secondaryName = getWeaponDisplayName("secondary");
        if (primaryName != null || secondaryName != null) {
            tooltipLines.add(Component.literal("§7组合:"));
            addWrappedTooltipLine(tooltipLines, minecraft, "§c主: §f", primaryName == null ? "空" : primaryName, maxWidth, "§7   §f");
            addWrappedTooltipLine(tooltipLines, minecraft, "§9副: §f", secondaryName == null ? "空" : secondaryName, maxWidth, "§7   §f");
            tooltipLines.add(Component.literal("§7----------------"));
        }

        // 添加右键提示
        tooltipLines.add(Component.literal("§a右键 §7打开更多选项"));

        if (isCurrentlySelected) {
            tooltipLines.add(Component.literal("§a✔ 当前选中"));
        }

        Component combined = tooltipLines.get(0);
        for (int i = 1; i < tooltipLines.size(); i++) {
            combined = Component.empty().append(combined).append("\n").append(tooltipLines.get(i));
        }

        return Tooltip.create(combined);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        // 悬停音效处理
        if (this.isHoveredOrFocused() && focusedtimes == 0) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 0.5f, 1.2f);
                }
            });
            focusedtimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedtimes = 0;
        }
        if (this.isHoveredOrFocused()) {
            hoverTicks = Math.min(6, hoverTicks + 1);
        } else {
            hoverTicks = Math.max(0, hoverTicks - 1);
        }

        // 渲染基础按钮背景 - MWII 深色风格
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                CodTheme.CARD_BG_TOP, CodTheme.CARD_BG_BOTTOM);

        // 悬停效果
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }

        // 如果是选中的背包，渲染高亮边框
        if (isCurrentlySelected) {
            renderSelectedHighlight(graphics);
        }

        // 渲染背包名称编号
        if (backpack != null) {
            renderBackpackInfo(graphics);
        }
    }

    /**
     * 渲染选中状态的高亮边框 - MWII 金色
     */
    protected void renderSelectedHighlight(GuiGraphics graphics) {
        int borderWidth = CodTheme.BORDER_WIDTH;
        int color = CodTheme.SELECTED_BORDER;
        // 上
        graphics.fill(this.getX() - borderWidth, this.getY() - borderWidth,
                this.getX() + this.width + borderWidth, this.getY(), color);
        // 下
        graphics.fill(this.getX() - borderWidth, this.getY() + this.height,
                this.getX() + this.width + borderWidth, this.getY() + this.height + borderWidth, color);
        // 左
        graphics.fill(this.getX() - borderWidth, this.getY(),
                this.getX(), this.getY() + this.height, color);
        // 右
        graphics.fill(this.getX() + this.width, this.getY(),
                this.getX() + this.width + borderWidth, this.getY() + this.height, color);
    }

    /**
     * 渲染背包信息
     */
    protected void renderBackpackInfo(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();

        // 背包编号（左上角）
        String idText = "#" + BAGSERIAL;
        int idColor = isCurrentlySelected ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_SECONDARY;
        graphics.drawString(minecraft.font, idText, this.getX() + 4, this.getY() + 4, idColor, true);

        // 背包名称（居中）
        String name = getDisplayNameForWidth(minecraft, getDisplayNameRaw(), this.width - 8);
        int textWidth = minecraft.font.width(name);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;

        int textColor;
        if (isCurrentlySelected) {
            textColor = CodTheme.SELECTED_TEXT;
        } else if (this.isHoveredOrFocused()) {
            textColor = CodTheme.TEXT_HOVER;
        } else {
            textColor = CodTheme.TEXT_PRIMARY;
        }

        graphics.drawString(minecraft.font, name, textX, textY, textColor, true);
    }

    /**
     * 悬停效果 - MWII 风格细边框 + 微亮背景
     */
    protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
        // 微亮背景
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                CodTheme.HOVER_BG_TOP, CodTheme.HOVER_BG_BOTTOM);

        if (hoverTicks > 0) {
            int alphaTop = Math.min(180, 18 * hoverTicks);
            int alphaBottom = Math.min(200, 22 * hoverTicks);
            graphics.fillGradient(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    withAlpha(CodTheme.HOVER_BG_TOP, alphaTop),
                    withAlpha(CodTheme.HOVER_BG_BOTTOM, alphaBottom));
        }

        // 顶部荧光绿边框
        graphics.fill(this.getX(), this.getY() - 1,
                this.getX() + this.width, this.getY(),
                CodTheme.HOVER_BORDER);

        // 底部荧光绿边框（半透明）
        graphics.fill(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 1,
                CodTheme.HOVER_BORDER_SEMI);
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.5f, 2.2f);
            }
        });
        super.playDownSound(pHandler);
    }

    public Integer getBAGSERIAL() {
        return BAGSERIAL;
    }

    public BackpackConfig.Backpack getBackpack() {
        return backpack;
    }

    public String getDisplayNameRaw() {
        String customName = getCustomName();
        if (customName != null && !customName.isBlank()) {
            return customName.trim();
        }
        String combo = getCombinationNameRaw();
        return combo == null || combo.isBlank() ? "未配置" : combo;
    }

    public String getCombinationNameRaw() {
        String primary = getWeaponDisplayName("primary");
        String secondary = getWeaponDisplayName("secondary");
        if (primary == null && secondary == null) {
            return "";
        }
        if (primary == null) {
            primary = "空";
        }
        if (secondary == null) {
            secondary = "空";
        }
        return primary + " + " + secondary;
    }

    private String getCustomName() {
        if (backpack == null) {
            return "";
        }
        String name = backpack.getName();
        return name == null ? "" : name;
    }

    private String getWeaponDisplayName(String slot) {
        if (weaponInfoCache == null) {
            return null;
        }
        WeaponInfo info = weaponInfoCache.get(slot);
        if (info == null || info.weaponName == null) {
            return null;
        }
        String name = info.weaponName.getString();
        return name == null || name.isBlank() ? null : name;
    }

    private String getDisplayNameForWidth(Minecraft minecraft, String raw, int maxWidth) {
        if (minecraft.font.width(raw) <= maxWidth) {
            return raw;
        }
        String ellipsis = "...";
        int maxTextWidth = maxWidth - minecraft.font.width(ellipsis);
        if (maxTextWidth <= 0) {
            return ellipsis;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            builder.append(raw.charAt(i));
            if (minecraft.font.width(builder.toString()) > maxTextWidth) {
                builder.setLength(builder.length() - 1);
                break;
            }
        }
        return builder + ellipsis;
    }

    private void addWrappedTooltipLine(List<Component> tooltipLines, Minecraft minecraft, String prefix, String text, int maxWidth, String continuationPrefix) {
        String plainPrefix = stripFormatting(prefix);
        int availableWidth = maxWidth - minecraft.font.width(plainPrefix);
        if (availableWidth <= 0) {
            tooltipLines.add(Component.literal(prefix + text));
            return;
        }
        List<String> parts = wrapPlainText(minecraft, text, availableWidth);
        if (parts.isEmpty()) {
            tooltipLines.add(Component.literal(prefix));
            return;
        }
        tooltipLines.add(Component.literal(prefix + parts.get(0)));
        String plainContinuation = stripFormatting(continuationPrefix);
        int continuationWidth = maxWidth - minecraft.font.width(plainContinuation);
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);
            if (continuationWidth > 0 && minecraft.font.width(part) > continuationWidth) {
                for (String chunk : wrapPlainText(minecraft, part, continuationWidth)) {
                    tooltipLines.add(Component.literal(continuationPrefix + chunk));
                }
            } else {
                tooltipLines.add(Component.literal(continuationPrefix + part));
            }
        }
    }

    private List<String> wrapPlainText(Minecraft minecraft, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        String remaining = text.trim();
        while (!remaining.isEmpty()) {
            int cut = remaining.length();
            while (cut > 0 && minecraft.font.width(remaining.substring(0, cut)) > maxWidth) {
                cut--;
            }
            if (cut <= 0) {
                cut = 1;
            }
            String part = remaining.substring(0, cut).trim();
            if (!part.isEmpty()) {
                lines.add(part);
            }
            remaining = remaining.substring(cut).trim();
        }
        return lines;
    }

    private String stripFormatting(String text) {
        String stripped = ChatFormatting.stripFormatting(text);
        return stripped == null ? text : stripped;
    }
}
