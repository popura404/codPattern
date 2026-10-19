package com.cdp.codpattern.mixin.tacz;

import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

/** TaCZ 1.1.6/1.1.8 invoke apply only from the slider's value-change callback. */
@Mixin(value = HSVSliderGroup.class, remap = false)
public abstract class HSVSliderGroupMixin {
    @Shadow @Final private int gunItemIndex;
    @Shadow @Final private AttachmentType type;
    @Shadow @Final private HSVSliderGroup.LaserColorSlider hueSlider;
    @Shadow @Final private HSVSliderGroup.LaserColorSlider saturationSlider;
    @Unique private boolean codpattern$sentColor;
    @Unique private int codpattern$lastColor;

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 1)
    private void codpattern$sendChangedColor(CallbackInfo ci) {
        if (!AttachmentRefitClientState.acceptsTaczOperations()) return;
        ci.cancel();
        int color = Color.HSBtoRGB((float) hueSlider.getValue(), (float) saturationSlider.getValue(), 1F);
        if (codpattern$sentColor && color == codpattern$lastColor) return;
        codpattern$sentColor = true;
        codpattern$lastColor = color;
        AttachmentRefitClientState.onLaserColorEdited(gunItemIndex, type, color);
    }
}
