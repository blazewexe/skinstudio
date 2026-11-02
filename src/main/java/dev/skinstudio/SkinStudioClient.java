package dev.skinstudio;

import dev.skinstudio.gui.SkinStudioScreen;
import dev.skinstudio.service.MineSkinProvider;
import dev.skinstudio.service.ServerProfiles;
import dev.skinstudio.service.SkinLibrary;
import dev.skinstudio.service.SkinResolver;
import dev.skinstudio.model.SkinEntry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class SkinStudioClient implements ClientModInitializer {
    public static final SkinLibrary LIBRARY = new SkinLibrary();
    public static final SkinResolver RESOLVER = new SkinResolver(LIBRARY);
    public static final MineSkinProvider UPLOADER = new MineSkinProvider();
    public static final ServerProfiles SERVER_PROFILES = new ServerProfiles();
    public static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Skin Studio upload"); thread.setDaemon(true); return thread;
    });
    private KeyBinding openKey;

    @Override public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.skin_studio.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.skin_studio"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openKey.wasPressed()) client.setScreen(new SkinStudioScreen(null));
        });
    }

    public static String serverAddress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) return client.getCurrentServerEntry().address;
        return "singleplayer";
    }

    public static void upload(SkinEntry entry, Consumer<String> result) {
        CompletableFuture.supplyAsync(() -> {
            try { return UPLOADER.upload(entry, LIBRARY.assetPath(entry)); }
            catch (Exception e) { throw new RuntimeException(e.getMessage() == null ? "Upload failed." : e.getMessage(), e); }
        }, WORKER).whenComplete((url, error) -> MinecraftClient.getInstance().execute(() -> {
            Throwable cause = error;
            while (cause != null && cause.getCause() != null) cause = cause.getCause();
            result.accept(error == null ? url : (cause.getMessage() == null ? "Upload failed." : cause.getMessage()));
        }));
    }

    public static void toast(String message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("[Skin Studio] " + message));
    }
}
