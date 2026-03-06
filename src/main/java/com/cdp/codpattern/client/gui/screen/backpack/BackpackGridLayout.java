package com.cdp.codpattern.client.gui.screen.backpack;

public final class BackpackGridLayout {
    public record PanelBounds(int left, int top, int right, int bottom) {
    }

    public record LayoutMetrics(
            PanelBounds panelBounds,
            int buttonWidth,
            int buttonHeight,
            int columnGap,
            int topRowY,
            int bottomRowY) {
        public int panelWidth() {
            return panelBounds.right() - panelBounds.left();
        }
    }

    public record ButtonPosition(int x, int y) {
    }

    private static final int MAX_COLUMNS = 5;

    private BackpackGridLayout() {
    }

    public static LayoutMetrics metrics(int screenWidth, int screenHeight, int unitLength) {
        int panelLeft = unitLength * 7;
        int panelTop = screenHeight - unitLength * 24;
        int panelRight = screenWidth - unitLength * 7;
        int panelBottom = screenHeight - unitLength * 7;
        return new LayoutMetrics(
                new PanelBounds(panelLeft, panelTop, panelRight, panelBottom),
                unitLength * 20,
                unitLength * 5,
                unitLength,
                panelTop + unitLength * 2,
                panelTop + unitLength * 11
        );
    }

    public static ButtonPosition selectButtonPosition(
            int buttonIndex,
            int totalButtons,
            int screenWidth,
            int screenHeight,
            int unitLength) {
        LayoutMetrics metrics = metrics(screenWidth, screenHeight, unitLength);
        int clampedButtonIndex = Math.max(1, Math.min(buttonIndex, MAX_COLUMNS * 2));
        int clampedTotalButtons = Math.max(1, Math.min(totalButtons, MAX_COLUMNS * 2));
        int rowIndex = clampedButtonIndex <= MAX_COLUMNS ? 0 : 1;
        int rowCount = buttonsInRow(rowIndex, clampedTotalButtons);
        int indexWithinRow = rowIndex == 0 ? clampedButtonIndex - 1 : clampedButtonIndex - MAX_COLUMNS - 1;
        int rowWidth = rowCount * metrics.buttonWidth() + Math.max(0, rowCount - 1) * metrics.columnGap();
        int startX = metrics.panelBounds().left() + Math.max(0, (metrics.panelWidth() - rowWidth) / 2);
        int x = startX + indexWithinRow * (metrics.buttonWidth() + metrics.columnGap());
        int y = rowIndex == 0 ? metrics.topRowY() : metrics.bottomRowY();
        return new ButtonPosition(x, y);
    }

    private static int buttonsInRow(int rowIndex, int totalButtons) {
        if (rowIndex == 0) {
            return Math.min(totalButtons, MAX_COLUMNS);
        }
        return Math.max(0, Math.min(totalButtons - MAX_COLUMNS, MAX_COLUMNS));
    }
}
