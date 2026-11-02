package dev.skinstudio.gui;

import dev.skinstudio.SkinStudioClient;
import dev.skinstudio.model.SkinEntry;
import dev.skinstudio.model.SkinModel;
import dev.skinstudio.service.ServerProfiles;
import dev.skinstudio.service.SkinsRestorerAdapter;
import dev.skinstudio.service.SkinResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.Blaze3D;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SkinStudioScreen extends Screen {
    private enum Page { IMPORT, LIBRARY, SETTINGS }
    private Page page = Page.IMPORT;
    private SkinEntry selected;
    private SkinResolver.Source source = SkinResolver.Source.MOJANG;
    private EditBox input;
    private EditBox template;
    private String status = "Choose an input, then import a skin.";
    private boolean busy;
    private boolean favoriteOnly;
    private int[] previewPixels;
    private int previewHeight;

    public SkinStudioScreen(SkinEntry selected) { super(Component.literal("Skin Studio")); this.selected = selected; }

    @Override protected void init() {
        if (selected != null && previewPixels == null) loadPreview();
        int left = width / 2 - 205;
        addRenderableWidget(Button.builder(Component.literal("Import"), b -> changePage(Page.IMPORT)).bounds(left, 45, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Library"), b -> changePage(Page.LIBRARY)).bounds(left + 96, 45, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> changePage(Page.SETTINGS)).bounds(left + 192, 45, 90, 20).build());
        if (page == Page.IMPORT) initImport(left);
        if (page == Page.LIBRARY) initLibrary(left);
        if (page == Page.SETTINGS) initSettings(left);
    }

    private void initImport(int left) {
        input = new EditBox(font, left, 92, 300, 20, Component.literal("Username, URL, or file path"));
        input.setMaxLength(512); addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal(sourceLabel()), b -> {
            source = SkinResolver.Source.values()[(source.ordinal() + 1) % SkinResolver.Source.values().length]; b.setMessage(Component.literal(sourceLabel()));
        }).bounds(left + 306, 92, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(busy ? "Importing…" : "Import skin"), b -> resolve())
                .bounds(left, 118, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Local PNG path"), b -> { source = SkinResolver.Source.LOCAL; input.setValue(""); status = "Enter the full path to a PNG file."; })
                .bounds(left + 126, 118, 120, 20).build());
        if (selected != null) addSelectedControls(left, 184);
    }

    private void addSelectedControls(int left, int y) {
        addRenderableWidget(Button.builder(Component.literal("Model: " + modelLabel()), b -> {
            selected.model = selected.skinModel() == SkinModel.CLASSIC ? SkinModel.SLIM.name() : SkinModel.CLASSIC.name();
            uploadValue = null; uploadedSkinId = null;
            SkinStudioClient.LIBRARY.add(selected); loadPreview(); b.setMessage(Component.literal("Model: " + modelLabel()));
        }).bounds(left, y, 132, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Upload to MineSkin"), b -> confirmUpload())
                .bounds(left + 138, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply uploaded skin"), b -> applyUploaded())
                .bounds(left + 294, y, 112, 20).build());
    }

    private void initLibrary(int left) {
        addRenderableWidget(Button.builder(Component.literal(favoriteOnly ? "Show all" : "Favorites"), b -> {
            favoriteOnly = !favoriteOnly; rebuildWidgets();
        }).bounds(left + 310, 78, 96, 20).build());
        List<SkinEntry> entries = SkinStudioClient.LIBRARY.entries().stream().filter(e -> !favoriteOnly || e.favorite).limit(7).toList();
        int y = 106;
        for (SkinEntry entry : entries) {
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal((entry.favorite ? "★ " : "☆ ") + shorten(entry.displayName, 21)), b -> {
                SkinStudioClient.LIBRARY.setFavorite(entry.id, !entry.favorite); rebuildWidgets();
            }).bounds(left, rowY, 200, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Select"), b -> { selected = entry; page = Page.IMPORT; previewPixels = null; uploadValue = null; uploadedSkinId = null; rebuildWidgets(); })
                    .bounds(left + 206, rowY, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Remove"), b -> { SkinStudioClient.LIBRARY.remove(entry.id); if (selected == entry) selected = null; rebuildWidgets(); })
                    .bounds(left + 282, rowY, 75, 20).build());
            y += 24;
        }
        if (entries.isEmpty()) status = favoriteOnly ? "No favorites yet." : "Your library is empty. Import a skin to get started.";
    }

    private void initSettings(int left) {
        ServerProfiles.Profile profile = SkinStudioClient.SERVER_PROFILES.get(SkinStudioClient.serverAddress());
        template = new EditBox(font, left, 110, 400, 20, Component.literal("/skin url \"{value}\" {model}"));
        template.setMaxLength(256); template.setValue(profile.commandTemplate); addRenderableWidget(template);
        addRenderableWidget(Button.builder(Component.literal("Save server profile"), b -> saveSettings())
                .bounds(left, 138, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Website"), b -> openLink("https://github.com/blazewexe/skinstudio"))
                .bounds(left, 218, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Report an issue"), b -> openLink("https://github.com/blazewexe/skinstudio/issues"))
                .bounds(left + 100, 218, 110, 20).build());
    }

    private String sourceLabel() { return switch (source) { case MOJANG -> "Minecraft name"; case ELY_BY -> "Ely.by name"; case URL -> "Direct URL"; case LOCAL -> "Local PNG path"; }; }
    private String modelLabel() { return selected == null ? "Unknown" : selected.skinModel().name().toLowerCase(); }

    private void resolve() {
        if (busy || input == null || input.getValue().isBlank()) { status = "Enter a username, URL, or local PNG path first."; return; }
        busy = true; status = "Resolving and validating skin…";
        SkinResolver.Source selectedSource = source;
        SkinStudioClient.RESOLVER.resolve(selectedSource, input.getValue()).whenComplete((entry, error) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            if (error != null) status = rootMessage(error);
            else { selected = entry; uploadValue = null; uploadedSkinId = null; status = "Validated " + entry.width + "×" + entry.height + " PNG from " + entry.sourceType + "."; loadPreview(); rebuildWidgets(); }
        }));
    }

    private void loadPreview() {
        SkinEntry entry = selected; if (entry == null) return;
        CompletableFuture.supplyAsync(() -> {
            try { BufferedImage image = ImageIO.read(SkinStudioClient.LIBRARY.assetPath(entry).toFile()); int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); return new Object[]{pixels, image.getHeight()}; }
            catch (Exception e) { return null; }
        }, SkinStudioClient.WORKER).thenAccept(data -> Minecraft.getInstance().execute(() -> {
            if (selected == entry && data != null) { previewPixels = (int[]) data[0]; previewHeight = (int) data[1]; }
        }));
    }

    private void confirmUpload() {
        if (selected == null) { status = "Select a skin first."; return; }
        if (selected.skinModel() == SkinModel.UNKNOWN) { status = "Choose Classic or Slim before uploading this 64×64 skin."; return; }
        if (busy) return;
        // Consent is deliberate each time because the PNG is sent to an external provider.
        Minecraft.getInstance().gui.setScreen(new ConsentScreen(this, selected.displayName, () -> {
            busy = true; status = "Uploading PNG to api.mineskin.org…";
            SkinEntry uploading = selected;
            SkinStudioClient.upload(uploading, text -> { busy = false; if (text.startsWith("https://") || text.startsWith("http://")) { status = "MineSkin returned a texture URL. Review it on the server command screen."; uploadValue = text; uploadedSkinId = uploading.id; } else { status = text; uploadValue = null; uploadedSkinId = null; } });
        }));
    }
    private String uploadValue;
    private String uploadedSkinId;

    private void applyUploaded() {
        if (uploadValue == null || selected == null || !selected.id.equals(uploadedSkinId)) { status = "Upload the selected skin first. No command has been sent."; return; }
        ServerProfiles.Profile profile = SkinStudioClient.SERVER_PROFILES.get(SkinStudioClient.serverAddress());
        String ready;
        try { ready = new SkinsRestorerAdapter().buildApplyCommand(uploadValue, selected.skinModel(), profile.commandTemplate).command(); }
        catch (IllegalArgumentException e) { status = e.getMessage(); return; }
        Minecraft.getInstance().gui.setScreen(new ApplyConfirmScreen(this, "/" + ready, () -> {
            if (Minecraft.getInstance().getConnection() == null) { status = "Connect to a multiplayer server before applying."; return; }
            Minecraft.getInstance().getConnection().sendCommand(ready);
            selected.lastUsedAt = System.currentTimeMillis(); SkinStudioClient.LIBRARY.add(selected);
            status = "Command sent. The server has not confirmed that the skin was applied.";
            SkinStudioClient.toast("Command sent; check the server response for success or permission errors.");
        }));
    }

    private void saveSettings() {
        String value = template.getValue().trim();
        if (!value.contains("{value}") || value.contains("\n") || value.contains("\r")) { status = "Command template must include {value} and fit on one line."; return; }
        SkinStudioClient.SERVER_PROFILES.put(SkinStudioClient.serverAddress(), "skinsrestorer", value);
        status = "Saved command profile for " + SkinStudioClient.serverAddress() + ".";
    }

    private void openLink(String address) {
        try { Blaze3D.openUri(java.net.URI.create(address)); }
        catch (Exception e) { status = "Could not open the link. Visit " + address; }
    }

    private void changePage(Page next) { page = next; rebuildWidgets(); }
    private String rootMessage(Throwable error) { Throwable value = error; while (value.getCause() != null) value = value.getCause(); return value.getMessage() == null ? "Import failed." : value.getMessage(); }
    private String shorten(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Avoid requesting a second blur pass; Iris and other 26.3 UI mods may
        // already have added one to this frame's render state.
        context.fill(0, 0, width, height, 0xFF0B1119);
        int left = width / 2 - 205;
        context.fill(left - 12, 18, left + 418, height - 18, 0xEF111923);
        context.text(font, "SKIN STUDIO", left, 24, 0x8FE0C6);
        context.text(font, "Client-side skin library", left + 116, 24, 0xAFBAC8);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.textWithWordWrap(font, Component.literal(status), left, height - 54, 410, 0xC4D0DE);
        if (page == Page.IMPORT && selected != null) {
            context.text(font, selected.displayName + " · " + selected.width + "×" + selected.height + " · " + selected.sourceType,
                    left, 156, 0xFFFFFF);
            renderSkinSheet(context, left + 270, 180);
        }
        if (page == Page.SETTINGS) {
            context.text(font, "Server: " + SkinStudioClient.serverAddress(), left, 78, 0xDDE6F1);
            context.text(font, "SkinsRestorer command template. Use {value} and {model}.", left, 94, 0xAEBBC9);
            context.text(font, "MineSkin API key comes from SKINSTUDIO_MINESKIN_API_KEY.", left, 174, 0xAEBBC9);
            context.text(font, "The key is not written to the config directory.", left, 190, 0xAEBBC9);
            context.text(font, "Open Skin Studio: Right Alt (change in Options → Controls).", left, 202, 0xAEBBC9);
        }
    }

    private void renderSkinSheet(GuiGraphicsExtractor context, int x, int y) {
        if (previewPixels == null) { context.text(font, "2D skin sheet", x, y, 0xB7C4D0); return; }
        int drawWidth = 128; int scale = drawWidth / 64; int drawHeight = previewHeight * scale;
        context.fill(x - 2, y - 2, x + drawWidth + 2, y + drawHeight + 2, 0xFF263544);
        for (int py = 0; py < previewHeight; py++) for (int px = 0; px < 64; px++) {
            int color = previewPixels[py * 64 + px]; if ((color >>> 24) != 0) context.fill(x + px * scale, y + py * scale, x + (px + 1) * scale, y + (py + 1) * scale, color);
        }
    }

    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(null); }

    private static final class ConsentScreen extends Screen {
        private final Screen parent; private final String skinName; private final Runnable consent;
        ConsentScreen(Screen parent, String skinName, Runnable consent) { super(Component.literal("External upload consent")); this.parent = parent; this.skinName = skinName; this.consent = consent; }
        @Override protected void init() {
            int x = width / 2 - 105;
            addRenderableWidget(Button.builder(Component.literal("Upload to MineSkin"), b -> { consent.run(); Minecraft.getInstance().gui.setScreen(parent); }).bounds(x, height / 2 + 32, 130, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> Minecraft.getInstance().gui.setScreen(parent)).bounds(x + 138, height / 2 + 32, 72, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF0B1119);
            context.centeredText(font, "Send this PNG to MineSkin?", width / 2, height / 2 - 40, 0xFFFFFF);
            context.centeredText(font, shortenStatic(skinName, 48), width / 2, height / 2 - 21, 0x8FE0C6);
            context.centeredText(font, "MineSkin receives the image and processes it for a texture URL.", width / 2, height / 2 - 2, 0xC4D0DE);
            context.centeredText(font, "The server decides whether the resulting URL is allowed.", width / 2, height / 2 + 14, 0xAEBBC9);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
        private static String shortenStatic(String value, int n) { return value.length() <= n ? value : value.substring(0, n - 1) + "…"; }
        @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    }
}
