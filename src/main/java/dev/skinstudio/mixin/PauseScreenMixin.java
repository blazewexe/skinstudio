package dev.skinstudio.mixin;

import dev.skinstudio.gui.SkinStudioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void skinStudio$addButton(CallbackInfo callbackInfo) {
        addRenderableWidget(Button.builder(Component.literal("Skin Studio"), button ->
                Minecraft.getInstance().gui.setScreen(new SkinStudioScreen(null)))
                .bounds(width / 2 - 100, height - 32, 200, 20).build());
    }
}
