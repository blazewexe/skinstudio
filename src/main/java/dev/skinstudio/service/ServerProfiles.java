package dev.skinstudio.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;

public final class ServerProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("skin-studio/servers.json");
    private static final Type PROFILE_MAP = new TypeToken<Map<String, Profile>>() {}.getType();
    private Map<String, Profile> profiles = new HashMap<>();
    public static final class Profile {
        public String adapter = "skinsrestorer";
        public String commandTemplate = "/skin url \"{value}\" {model}";
    }
    public ServerProfiles() {
        try { if (Files.exists(file)) { Map<String, Profile> saved = GSON.fromJson(Files.readString(file), PROFILE_MAP); if (saved != null) profiles = saved; } }
        catch (Exception ignored) { profiles = new HashMap<>(); }
    }
    public synchronized Profile get(String address) {
        Object value = profiles.get(address);
        if (value instanceof Profile profile) return profile;
        Profile profile = new Profile(); profiles.put(address, profile); return profile;
    }
    public synchronized void put(String address, String adapter, String template) {
        Profile profile = new Profile(); profile.adapter = adapter; profile.commandTemplate = template;
        profiles.put(address, profile); save();
    }
    private void save() {
        try { Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(profiles)); }
        catch (Exception ignored) { }
    }
}
