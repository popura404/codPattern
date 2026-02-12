package com.cdp.codpattern.client.gui.screen.backpack;

public final class BackpackGridLayout {
    public record ButtonPosition(int x, int y) {
    }

    private BackpackGridLayout() {
    }

    public static ButtonPosition selectButtonPosition(int buttonIndex, int unitLength, int screenHeight) {
        int x;
        int y;
        if (buttonIndex <= 5) {
            x = unitLength * 8 + ((buttonIndex - 1) * unitLength * 21);
            y = unitLength * 22;
        } else {
            x = unitLength * 8 + ((buttonIndex - 6) * unitLength * 21);
            y = unitLength * 13;
        }
        return new ButtonPosition(x, screenHeight - y);
    }
}
