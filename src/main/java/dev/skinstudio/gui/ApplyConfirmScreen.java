package dev.skinstudio.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ApplyConfirmScreen extends Screen {
    private final Screen parent;
    private final String command;
    private final Runnable apply;

    public ApplyConfirmScreen(Screen parent, String command, Runnable apply) {
        super(Component.literal("Confirm skin command")); this.parent = parent; this.command = command; this.apply = apply;
    }
    @Override protected void init() {
        int x = width / 2 - 96;
        addRenderableWidget(Button.builder(Component.literal("Send command"), b -> { apply.run(); Minecraft.getInstance().gui.setScreen(parent); })
                .bounds(x, height / 2 + 45, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().gui.setScreen(parent))
                .bounds(x + 100, height / 2 + 45, 92, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        context.centeredText(font, "Send this command to the current server?", width / 2, height / 2 - 38, 0xFFFFFF);
        context.centeredText(font, "The server may deny it or reject the skin URL.", width / 2, height / 2 - 20, 0xB9C4D0);
        context.fill(width / 2 - 190, height / 2 - 2, width / 2 + 190, height / 2 + 32, 0xFF101722);
        context.centeredText(font, command, width / 2, height / 2 + 10, 0x8FE0C6);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
}
