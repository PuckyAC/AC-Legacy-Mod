package dev.adventurecraft.awakening.mixin.client.util;

import net.minecraft.client.util.MouseHelper;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.awt.Component;

@Mixin(MouseHelper.class)
public abstract class MixinMouseHelper {

    @Redirect(method = "ungrabCursor", at = @At(
            value = "INVOKE",
            target = "Ljava/awt/Component;getWidth()I"
    ))
    private int fix_ungrabWidth(Component instance) {
        return Display.getWidth();
    }

    @Redirect(method = "ungrabCursor", at = @At(
            value = "INVOKE",
            target = "Ljava/awt/Component;getHeight()I"
    ))
    private int fix_ungrabHeight(Component instance) {
        return Display.getHeight();
    }
}
