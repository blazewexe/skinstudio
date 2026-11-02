package dev.skinstudio.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skinstudio.model.SkinEntry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;

public final class MineSkinProvider {
    private static final URI QUEUE = URI.create("https://api.mineskin.org/v2/queue");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String upload(SkinEntry entry, java.nio.file.Path file) throws Exception {
        String key = System.getenv("SKINSTUDIO_MINESKIN_API_KEY");
        if (key == null || key.isBlank()) throw new IOException("Set SKINSTUDIO_MINESKIN_API_KEY and restart Minecraft to enable MineSkin uploads. The key is never saved in the mod config.");
        String boundary = "SkinStudio" + UUID.randomUUID().toString().replace("-", "");
        byte[] image = Files.readAllBytes(file);
        String model = entry.skinModel().commandValue();
        String prefix = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + model + "\r\n" +
                "--" + boundary + "\r\nContent-Disposition: form-data; name=\"visibility\"\r\n\r\nunlisted\r\n" +
                "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n";
        byte[] start = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] end = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[start.length + image.length + end.length];
        System.arraycopy(start, 0, body, 0, start.length); System.arraycopy(image, 0, body, start.length, image.length);
        System.arraycopy(end, 0, body, start.length + image.length, end.length);
        HttpRequest request = HttpRequest.newBuilder(QUEUE).timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + key)
                .header("User-Agent", "SkinStudio/0.1.0 (Minecraft client mod)")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) throw new IOException("MineSkin rate limit reached. Wait before uploading again.");
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("MineSkin rejected the upload (HTTP " + response.statusCode() + "). Check the API key and try later.");
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject skin = data.has("skin") ? data.getAsJsonObject("skin") : null;
        if (skin != null) return textureUrl(skin);
        JsonObject job = data.has("job") ? data.getAsJsonObject("job") : null;
        if (job == null || !job.has("id")) throw new IOException("MineSkin returned no job identifier.");
        String id = job.get("id").getAsString();
        for (int i = 0; i < 40; i++) {
            Thread.sleep(1500);
            HttpRequest poll = HttpRequest.newBuilder(URI.create("https://api.mineskin.org/v2/queue/" + id)).timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + key).header("User-Agent", "SkinStudio/0.1.0 (Minecraft client mod)").GET().build();
            HttpResponse<String> result = http.send(poll, HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() == 429) throw new IOException("MineSkin rate limit reached while checking the upload.");
            if (result.statusCode() < 200 || result.statusCode() >= 300) throw new IOException("Could not check MineSkin upload status (HTTP " + result.statusCode() + ").");
            JsonObject status = JsonParser.parseString(result.body()).getAsJsonObject();
            if (status.has("skin")) return textureUrl(status.getAsJsonObject("skin"));
            String jobStatus = status.has("job") ? status.getAsJsonObject("job").get("status").getAsString() : "unknown";
            if (jobStatus.equals("failed")) throw new IOException("MineSkin could not process this skin.");
        }
        throw new IOException("MineSkin is still processing. Wait a moment and import/upload again.");
    }

    private String textureUrl(JsonObject skin) throws IOException {
        if (!skin.has("texture")) throw new IOException("MineSkin returned no texture data.");
        JsonObject texture = skin.getAsJsonObject("texture");
        if (!texture.has("url")) throw new IOException("MineSkin returned no texture URL for the server command.");
        JsonObject urls = texture.getAsJsonObject("url");
        if (!urls.has("skin")) throw new IOException("MineSkin returned no skin texture URL for the server command.");
        String url = urls.get("skin").getAsString();
        if (!url.startsWith("https://textures.minecraft.net/texture/")) throw new IOException("MineSkin returned an unexpected texture URL.");
        return url;
    }
}
