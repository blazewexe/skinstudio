package dev.skinstudio.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Stores the user's MineSkin API key in the local Minecraft config directory. */
public final class MineSkinSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir()
            .resolve("skin-studio/mineskin.json");
    private String apiKey;

    public MineSkinSettings() {
        try {
            if (Files.exists(file)) {
                SavedSettings saved = GSON.fromJson(Files.readString(file), SavedSettings.class);
                if (saved != null && saved.apiKey != null && !saved.apiKey.isBlank()) {
                    apiKey = saved.apiKey;
                }
            }
        } catch (Exception ignored) {
            apiKey = null;
        }
    }

    public synchronized String getApiKey() {
        return apiKey;
    }

    public synchronized boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public synchronized void setApiKey(String value) throws Exception {
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (normalized == null) {
            clearApiKey();
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(new SavedSettings(normalized)));
        apiKey = normalized;
    }

    public synchronized void clearApiKey() throws Exception {
        Files.deleteIfExists(file);
        apiKey = null;
    }

    private record SavedSettings(String apiKey) { }
}
