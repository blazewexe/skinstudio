package dev.skinstudio;

import dev.skinstudio.gui.SkinStudioScreen;
import dev.skinstudio.service.MineSkinProvider;
import dev.skinstudio.service.ServerProfiles;
import dev.skinstudio.service.SkinLibrary;
import dev.skinstudio.service.SkinResolver;
import dev.skinstudio.model.SkinEntry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class SkinStudioClient implements ClientModInitializer {
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath("skin_studio", "main"));
    public static final SkinLibrary LIBRARY = new SkinLibrary();
    public static final SkinResolver RESOLVER = new SkinResolver(LIBRARY);
    public static final MineSkinProvider UPLOADER = new MineSkinProvider();
    public static final ServerProfiles SERVER_PROFILES = new ServerProfiles();
    public static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Skin Studio upload"); thread.setDaemon(true); return thread;
    });
    private KeyMapping openKey;

    @Override public void onInitializeClient() {
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skin_studio.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_RIGHT_ALT, KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openKey.consumeClick()) client.gui.setScreen(new SkinStudioScreen(null));
        });
        // Keep a reliable entry point on Escape even if the user's keybind is changed.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                screen.addRenderableWidget(Button.builder(Component.literal("Skin Studio"), button ->
                        client.gui.setScreen(new SkinStudioScreen(null)))
                        .bounds(scaledWidth / 2 - 100, scaledHeight - 32, 200, 20).build());
            }
        });
    }

    public static String serverAddress() {
        Minecraft client = Minecraft.getInstance();
        if (client.getCurrentServer() != null) return client.getCurrentServer().ip;
        return "singleplayer";
    }

    public static void upload(SkinEntry entry, Consumer<String> result) {
        CompletableFuture.supplyAsync(() -> {
            try { return UPLOADER.upload(entry, LIBRARY.assetPath(entry)); }
            catch (Exception e) { throw new RuntimeException(e.getMessage() == null ? "Upload failed." : e.getMessage(), e); }
        }, WORKER).whenComplete((url, error) -> Minecraft.getInstance().execute(() -> {
            Throwable cause = error;
            while (cause != null && cause.getCause() != null) cause = cause.getCause();
            result.accept(error == null ? url : (cause.getMessage() == null ? "Upload failed." : cause.getMessage()));
        }));
    }

    public static void toast(String message) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("[Skin Studio] " + message), false);
    }
}
