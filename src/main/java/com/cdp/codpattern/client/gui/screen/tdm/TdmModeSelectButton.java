package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.screen.match.ModeSelectButton;

/**
 * Legacy button name retained for older callers. New code should use {@link ModeSelectButton}.
 */
@Deprecated(forRemoval = false)
public class TdmModeSelectButton extends ModeSelectButton {
    public TdmModeSelectButton(
            int x,
            int y,
            int width,
            int height,
            ModeDescriptor descriptor,
            int accentColor,
            OnPress onPress) {
        super(x, y, width, height, descriptor, accentColor, onPress);
    }
}
