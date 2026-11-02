package dev.skinstudio.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skinstudio.model.SkinEntry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.UUID;

public final class MineSkinProvider {
    private static final URI QUEUE = URI.create("https://api.mineskin.org/v2/queue");
    private static final Duration MAX_UPLOAD_TIME = Duration.ofSeconds(45);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String upload(SkinEntry entry, java.nio.file.Path file, String key, Consumer<String> progress) throws Exception {
        if (key == null || key.isBlank()) throw new IOException("MineSkin API key is not set. Paste it in Skin Studio → Settings, or set SKINSTUDIO_MINESKIN_API_KEY in Windows.");
        long deadline = System.nanoTime() + MAX_UPLOAD_TIME.toNanos();
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
        progress.accept("Sending your skin to MineSkin…");
        HttpRequest request = HttpRequest.newBuilder(QUEUE).timeout(requestTimeout(deadline, 25))
                .header("Authorization", "Bearer " + key)
                .header("User-Agent", "SkinStudio/0.1.0 (Minecraft client mod)")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 429) throw new IOException("MineSkin rate limit reached. Wait before uploading again.");
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("MineSkin rejected the upload (HTTP " + response.statusCode() + "). Check the API key and try later.");
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject skin = data.has("skin") ? data.getAsJsonObject("skin") : null;
        if (skin != null) return textureUrl(skin);
        JsonObject job = data.has("job") ? data.getAsJsonObject("job") : null;
        if (job == null || !job.has("id")) throw new IOException("MineSkin returned no job identifier.");
        String id = job.get("id").getAsString();
        progress.accept("MineSkin accepted the upload. Waiting for it to process…");
        int checks = 0;
        while (System.nanoTime() < deadline) {
            long remainingMillis = Math.max(1, Duration.ofNanos(deadline - System.nanoTime()).toMillis());
            Thread.sleep(Math.min(1000, remainingMillis));
            HttpRequest poll = HttpRequest.newBuilder(URI.create("https://api.mineskin.org/v2/queue/" + id)).timeout(requestTimeout(deadline, 8))
                    .header("Authorization", "Bearer " + key).header("User-Agent", "SkinStudio/0.1.0 (Minecraft client mod)").GET().build();
            HttpResponse<String> result = send(poll);
            if (result.statusCode() == 429) throw new IOException("MineSkin rate limit reached while checking the upload.");
            if (result.statusCode() < 200 || result.statusCode() >= 300) throw new IOException("Could not check MineSkin upload status (HTTP " + result.statusCode() + ").");
            JsonObject status = JsonParser.parseString(result.body()).getAsJsonObject();
            if (status.has("skin")) return textureUrl(status.getAsJsonObject("skin"));
            String jobStatus = status.has("job") ? status.getAsJsonObject("job").get("status").getAsString() : "unknown";
            if (jobStatus.equals("failed")) throw new IOException("MineSkin could not process this skin.");
            checks++;
            if (checks % 3 == 0) progress.accept("MineSkin is still processing your skin…");
        }
        throw new IOException("MineSkin did not finish within 45 seconds. Wait a little before trying again; the queued upload may still complete.");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        try { return http.send(request, HttpResponse.BodyHandlers.ofString()); }
        catch (HttpTimeoutException e) { throw new IOException("MineSkin timed out while processing the request. Check your connection and try again.", e); }
    }

    private Duration requestTimeout(long deadline, int maximumSeconds) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException("MineSkin did not finish within 45 seconds. Wait a little before trying again; the queued upload may still complete.");
        return Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(maximumSeconds).toNanos()));
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
