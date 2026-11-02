package dev.skinstudio.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skinstudio.model.SkinEntry;
import dev.skinstudio.model.SkinModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SkinResolver implements AutoCloseable {
    public enum Source { MOJANG, ELY_BY, URL, LOCAL }
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "Skin Studio network"); thread.setDaemon(true); return thread;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final SkinLibrary library;
    private final SkinValidator validator = new SkinValidator();

    public SkinResolver(SkinLibrary library) { this.library = library; }

    public CompletableFuture<SkinEntry> resolve(Source source, String input) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String ref = input.trim();
                String name = ref;
                byte[] bytes;
                switch (source) {
                    case MOJANG -> {
                        if (!ref.matches("[A-Za-z0-9_]{3,16}")) throw new IOException("Enter a valid Minecraft username (3–16 letters, digits, or underscores).");
                        JsonObject profile = getJson(URI.create("https://api.mojang.com/users/profiles/minecraft/" + ref));
                        String uuid = profile.get("id").getAsString();
                        JsonObject details = getJson(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=true"));
                        String encoded = details.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                        JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8))
                                .getAsJsonObject().getAsJsonObject("textures");
                        if (textures == null || !textures.has("SKIN")) throw new IOException("That Minecraft profile has no skin.");
                        String texture = textures.getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
                        URI textureUri = URI.create(texture);
                        if (!"textures.minecraft.net".equalsIgnoreCase(textureUri.getHost())) throw new IOException("Mojang returned an unexpected skin texture host.");
                        bytes = getBytes(textureUri);
                        name = details.has("name") ? details.get("name").getAsString() : ref;
                    }
                    case ELY_BY -> {
                        String nick = ref;
                        if (ref.startsWith("http://") || ref.startsWith("https://")) {
                            URI uri = URI.create(ref);
                            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase("skinsystem.ely.by") || !uri.getPath().matches("/skins/[A-Za-z0-9_]{1,16}(\\.png)?")) {
                                throw new IOException("Use an Ely.by username or a skinsystem.ely.by/skins/<username>.png URL.");
                            }
                            nick = uri.getPath().substring("/skins/".length()).replaceFirst("\\.png$", "");
                        }
                        if (!nick.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("Enter a valid Ely.by username.");
                        URI uri = URI.create("https://skinsystem.ely.by/skins/" + URLEncoder.encode(nick, StandardCharsets.UTF_8) + ".png");
                        bytes = getBytes(uri);
                        name = nick + " (Ely.by)";
                    }
                    case URL -> {
                        URI uri = parseSafeUri(ref);
                        bytes = getBytes(uri);
                        name = uri.getHost();
                    }
                    case LOCAL -> {
                        Path path = Path.of(ref).toAbsolutePath().normalize();
                        if (!Files.isRegularFile(path)) throw new IOException("That local file does not exist.");
                        if (Files.size(path) > SkinValidator.MAX_BYTES) throw new IOException("PNG must be 2 MB or smaller.");
                        bytes = Files.readAllBytes(path);
                        name = path.getFileName().toString();
                    }
                    default -> throw new IOException("Unsupported source.");
                }
                SkinValidator.Validated valid = validator.validate(bytes);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                Path target = library.cacheDirectory().resolve(hash + ".png");
                if (!Files.exists(target)) Files.write(target, bytes);
                SkinEntry entry = new SkinEntry(name, source.name(), input.trim(), hash, valid.model(), valid.width(), valid.height());
                library.add(entry);
                return entry;
            } catch (Exception e) {
                throw new RuntimeException(messageFor(e), e);
            }
        }, executor);
    }

    private JsonObject getJson(URI uri) throws Exception {
        byte[] bytes = getBytes(uri);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private byte[] getBytes(URI initial) throws Exception {
        URI uri = initial;
        for (int redirect = 0; redirect <= 3; redirect++) {
            validatePublicHttpUri(uri);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "SkinStudio/0.1.0 (Minecraft client mod)").GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status <= 399) {
                try (InputStream ignored = response.body()) { }
                if (redirect == 3) throw new IOException("The source redirected too many times.");
                String location = response.headers().firstValue("location").orElseThrow(() -> new IOException("The source returned an invalid redirect."));
                URI next = uri.resolve(location);
                validatePublicHttpUri(next);
                uri = next;
                continue;
            }
            if (status == 404 || status == 204) throw new IOException("No skin was found for that input.");
            if (status < 200 || status >= 300) throw new IOException("The skin source returned HTTP " + status + ".");
            try (InputStream body = response.body(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int total = 0; int read;
                while ((read = body.read(buffer)) >= 0) {
                    total += read;
                    if (total > SkinValidator.MAX_BYTES) throw new IOException("The downloaded file is larger than 2 MB.");
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        }
        throw new IOException("The source could not be reached.");
    }

    private URI parseSafeUri(String raw) throws IOException {
        try {
            URI uri = URI.create(raw);
            validatePublicHttpUri(uri);
            return uri;
        } catch (IllegalArgumentException e) { throw new IOException("Enter a complete HTTP or HTTPS image URL."); }
    }

    private void validatePublicHttpUri(URI uri) throws IOException {
        String scheme = uri.getScheme(); String host = uri.getHost();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || host == null ||
                uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
            throw new IOException("Only public HTTP/HTTPS URLs on standard ports are allowed.");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.endsWith(".localhost") || normalized.endsWith(".local") || normalized.endsWith(".internal")) {
            throw new IOException("Local and private network addresses are blocked.");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IOException("Local and private network addresses are blocked.");
                }
            }
        } catch (UnknownHostException e) { throw new IOException("The URL host could not be resolved."); }
    }

    private String messageFor(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? "Could not import skin." : message;
    }

    @Override public void close() { executor.shutdownNow(); }
}
