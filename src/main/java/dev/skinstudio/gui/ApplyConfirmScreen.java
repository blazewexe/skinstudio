package dev.skinstudio.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ApplyConfirmScreen extends Screen {
    private final Screen parent;
    private final String command;
    private final Runnable apply;

    public ApplyConfirmScreen(Screen parent, String command, Runnable apply) {
        super(Text.literal("Confirm skin command")); this.parent = parent; this.command = command; this.apply = apply;
    }
    @Override protected void init() {
        int x = width / 2 - 96;
        addDrawableChild(ButtonWidget.builder(Text.literal("Send command"), b -> { apply.run(); client.setScreen(parent); })
                .dimensions(x, height / 2 + 45, 92, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent))
                .dimensions(x + 100, height / 2 + 45, 92, 20).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, "Send this command to the current server?", width / 2, height / 2 - 38, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "The server may deny it or reject the skin URL.", width / 2, height / 2 - 20, 0xB9C4D0);
        context.fill(width / 2 - 190, height / 2 - 2, width / 2 + 190, height / 2 + 32, 0xFF101722);
        context.drawCenteredTextWithShadow(textRenderer, command, width / 2, height / 2 + 10, 0x8FE0C6);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
