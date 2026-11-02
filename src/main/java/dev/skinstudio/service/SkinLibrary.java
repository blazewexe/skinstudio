package dev.skinstudio.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.skinstudio.model.SkinEntry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SkinLibrary {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<SkinEntry>>() {}.getType();
    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("skin-studio");
    private final Path cache = root.resolve("cache/skins");
    private final Path libraryFile = root.resolve("library.json");
    private List<SkinEntry> entries = new ArrayList<>();

    public SkinLibrary() {
        try {
            Files.createDirectories(cache);
            if (Files.exists(libraryFile)) {
                List<SkinEntry> loaded = GSON.fromJson(Files.readString(libraryFile), LIST_TYPE);
                if (loaded != null) entries = new ArrayList<>(loaded);
            }
        } catch (Exception ignored) { entries = new ArrayList<>(); }
    }

    public Path cacheDirectory() { return cache; }
    public Path rootDirectory() { return root; }

    public synchronized List<SkinEntry> entries() {
        return entries.stream().sorted(Comparator.comparingLong((SkinEntry e) -> e.lastUsedAt).reversed()).toList();
    }

    public synchronized void add(SkinEntry entry) {
        entries.removeIf(existing -> existing.id.equals(entry.id));
        entries.add(entry);
        save();
    }

    public synchronized void setFavorite(String id, boolean favorite) {
        entries.stream().filter(e -> e.id.equals(id)).findFirst().ifPresent(e -> e.favorite = favorite);
        save();
    }

    public synchronized void remove(String id) {
        SkinEntry entry = entries.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
        entries.removeIf(e -> e.id.equals(id));
        if (entry != null) {
            try { Files.deleteIfExists(cache.resolve(entry.cacheFile + ".png")); }
            catch (IOException ignored) { }
        }
        save();
    }

    public Path assetPath(SkinEntry entry) { return cache.resolve(entry.cacheFile + ".png"); }

    private void save() {
        try {
            Files.createDirectories(root);
            Path temporary = libraryFile.resolveSibling("library.json.tmp");
            Files.writeString(temporary, GSON.toJson(entries, LIST_TYPE));
            try { Files.move(temporary, libraryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(temporary, libraryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ignored) { }
    }
}
