package com.cdp.codpattern.client.gui;

/**
 * COD2022 UI 主题常量
 */
public final class CodTheme {
    private CodTheme() {}

    // ============ 背景色 ============
    /** 主背景 - 顶部 */
    public static final int BG_TOP = 0xE8101010;
    /** 主背景 - 底部 */
    public static final int BG_BOTTOM = 0xF8000000;
    /** 卡片/按钮背景 - 顶部 */
    public static final int CARD_BG_TOP = 0xE8202020;
    /** 卡片/按钮背景 - 底部 */
    public static final int CARD_BG_BOTTOM = 0xF0181818;
    /** 面板背景 */
    public static final int PANEL_BG = 0xD0151515;

    // ============ 悬停/选中色 ============
    /** 悬停背景 - 顶部（暗绿） */
    public static final int HOVER_BG_TOP = 0xD0182018;
    /** 悬停背景 - 底部（暗绿） */
    public static final int HOVER_BG_BOTTOM = 0xD8283028;
    /** 悬停边框 - 荧光绿 */
    public static final int HOVER_BORDER = 0xFF7FFF00;
    /** 悬停边框 - 半透明荧光绿 */
    public static final int HOVER_BORDER_SEMI = 0xB07FFF00;
    /** 选中边框 - 金色 */
    public static final int SELECTED_BORDER = 0xFFFFD700;
    /** 选中文字色 */
    public static final int SELECTED_TEXT = 0xFFFFD700;

    // ============ 文字色 ============
    /** 主文字 - 白色 */
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    /** 次级文字 - 灰色 */
    public static final int TEXT_SECONDARY = 0xFFAAAAAA;
    /** 暗淡文字 */
    public static final int TEXT_DIM = 0xFF707070;
    /** 悬停文字 - 亮绿 */
    public static final int TEXT_HOVER = 0xFF7FFF00;
    /** 警告/删除文字 - 红色 */
    public static final int TEXT_DANGER = 0xFFFF5555;

    // ============ 边框/分隔线 ============
    /** 细边框 - 暗灰 */
    public static final int BORDER_SUBTLE = 0x50FFFFFF;
    /** 分隔线 */
    public static final int DIVIDER = 0x40FFFFFF;
    /** 阴影 */
    public static final int SHADOW = 0xC0000000;

    // ============ 按钮状态 ============
    /** 禁用背景 */
    public static final int DISABLED_BG = 0xE0303030;
    /** 禁用文字 */
    public static final int DISABLED_TEXT = 0xFF606060;
    /** 按下背景 */
    public static final int PRESSED_BG = 0xF0101810;

    // ============ 上下文菜单 ============
    /** 菜单背景 */
    public static final int MENU_BG = 0xF0101010;
    /** 菜单边框 */
    public static final int MENU_BORDER = 0xFF404040;
    /** 菜单项悬停背景 */
    public static final int MENU_ITEM_HOVER = 0xFF203020;
    /** 菜单项悬停条 */
    public static final int MENU_ITEM_HOVER_BAR = 0xFF7FFF00;

    // ============ 尺寸常量 ============
    /** 边框宽度 */
    public static final int BORDER_WIDTH = 1;
    /** 菜单项高度（像素，非 UNIT） */
    public static final int MENU_ITEM_HEIGHT = 22;
    /** 菜单宽度（像素，非 UNIT） */
    public static final int MENU_WIDTH = 100;
}
