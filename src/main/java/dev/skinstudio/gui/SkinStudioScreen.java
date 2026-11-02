package dev.skinstudio.gui;

import dev.skinstudio.SkinStudioClient;
import dev.skinstudio.model.SkinEntry;
import dev.skinstudio.model.SkinModel;
import dev.skinstudio.service.ServerProfiles;
import dev.skinstudio.service.SkinsRestorerAdapter;
import dev.skinstudio.service.SkinResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SkinStudioScreen extends Screen {
    private enum Page { IMPORT, LIBRARY, SETTINGS }
    private Page page = Page.IMPORT;
    private SkinEntry selected;
    private SkinResolver.Source source = SkinResolver.Source.MOJANG;
    private TextFieldWidget input;
    private TextFieldWidget template;
    private String status = "Choose an input, then import a skin.";
    private boolean busy;
    private boolean favoriteOnly;
    private int[] previewPixels;
    private int previewHeight;

    public SkinStudioScreen(SkinEntry selected) { super(Text.literal("Skin Studio")); this.selected = selected; }

    @Override protected void init() {
        if (selected != null && previewPixels == null) loadPreview();
        int left = width / 2 - 205;
        addDrawableChild(ButtonWidget.builder(Text.literal("Import"), b -> changePage(Page.IMPORT)).dimensions(left, 45, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Library"), b -> changePage(Page.LIBRARY)).dimensions(left + 96, 45, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> changePage(Page.SETTINGS)).dimensions(left + 192, 45, 90, 20).build());
        if (page == Page.IMPORT) initImport(left);
        if (page == Page.LIBRARY) initLibrary(left);
        if (page == Page.SETTINGS) initSettings(left);
    }

    private void initImport(int left) {
        input = new TextFieldWidget(textRenderer, left, 92, 300, 20, Text.literal("Username, URL, or file path"));
        input.setMaxLength(512); addDrawableChild(input);
        addDrawableChild(ButtonWidget.builder(Text.literal(sourceLabel()), b -> {
            source = SkinResolver.Source.values()[(source.ordinal() + 1) % SkinResolver.Source.values().length]; b.setMessage(Text.literal(sourceLabel()));
        }).dimensions(left + 306, 92, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(busy ? "Importing…" : "Import skin"), b -> resolve())
                .dimensions(left, 118, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Local PNG path"), b -> { source = SkinResolver.Source.LOCAL; input.setText(""); status = "Enter the full path to a PNG file."; })
                .dimensions(left + 126, 118, 120, 20).build());
        if (selected != null) addSelectedControls(left, 184);
    }

    private void addSelectedControls(int left, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Model: " + modelLabel()), b -> {
            selected.model = selected.skinModel() == SkinModel.CLASSIC ? SkinModel.SLIM.name() : SkinModel.CLASSIC.name();
            uploadValue = null; uploadedSkinId = null;
            SkinStudioClient.LIBRARY.add(selected); loadPreview(); b.setMessage(Text.literal("Model: " + modelLabel()));
        }).dimensions(left, y, 132, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Upload to MineSkin"), b -> confirmUpload())
                .dimensions(left + 138, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply uploaded skin"), b -> applyUploaded())
                .dimensions(left + 294, y, 112, 20).build());
    }

    private void initLibrary(int left) {
        addDrawableChild(ButtonWidget.builder(Text.literal(favoriteOnly ? "Show all" : "Favorites"), b -> {
            favoriteOnly = !favoriteOnly; rebuildWidgets();
        }).dimensions(left + 310, 78, 96, 20).build());
        List<SkinEntry> entries = SkinStudioClient.LIBRARY.entries().stream().filter(e -> !favoriteOnly || e.favorite).limit(7).toList();
        int y = 106;
        for (SkinEntry entry : entries) {
            int rowY = y;
            addDrawableChild(ButtonWidget.builder(Text.literal((entry.favorite ? "★ " : "☆ ") + shorten(entry.displayName, 21)), b -> {
                SkinStudioClient.LIBRARY.setFavorite(entry.id, !entry.favorite); rebuildWidgets();
            }).dimensions(left, rowY, 200, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Select"), b -> { selected = entry; page = Page.IMPORT; previewPixels = null; uploadValue = null; uploadedSkinId = null; rebuildWidgets(); })
                    .dimensions(left + 206, rowY, 70, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), b -> { SkinStudioClient.LIBRARY.remove(entry.id); if (selected == entry) selected = null; rebuildWidgets(); })
                    .dimensions(left + 282, rowY, 75, 20).build());
            y += 24;
        }
        if (entries.isEmpty()) status = favoriteOnly ? "No favorites yet." : "Your library is empty. Import a skin to get started.";
    }

    private void initSettings(int left) {
        ServerProfiles.Profile profile = SkinStudioClient.SERVER_PROFILES.get(SkinStudioClient.serverAddress());
        template = new TextFieldWidget(textRenderer, left, 110, 400, 20, Text.literal("/skin url \"{value}\" {model}"));
        template.setMaxLength(256); template.setText(profile.commandTemplate); addDrawableChild(template);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save server profile"), b -> saveSettings())
                .dimensions(left, 138, 140, 20).build());
    }

    private String sourceLabel() { return switch (source) { case MOJANG -> "Minecraft name"; case ELY_BY -> "Ely.by name"; case URL -> "Direct URL"; case LOCAL -> "Local PNG path"; }; }
    private String modelLabel() { return selected == null ? "Unknown" : selected.skinModel().name().toLowerCase(); }

    private void resolve() {
        if (busy || input == null || input.getText().isBlank()) { status = "Enter a username, URL, or local PNG path first."; return; }
        busy = true; status = "Resolving and validating skin…";
        SkinResolver.Source selectedSource = source;
        SkinStudioClient.RESOLVER.resolve(selectedSource, input.getText()).whenComplete((entry, error) -> client.execute(() -> {
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
        }, SkinStudioClient.WORKER).thenAccept(data -> client.execute(() -> {
            if (selected == entry && data != null) { previewPixels = (int[]) data[0]; previewHeight = (int) data[1]; }
        }));
    }

    private void confirmUpload() {
        if (selected == null) { status = "Select a skin first."; return; }
        if (selected.skinModel() == SkinModel.UNKNOWN) { status = "Choose Classic or Slim before uploading this 64×64 skin."; return; }
        if (busy) return;
        // Consent is deliberate each time because the PNG is sent to an external provider.
        client.setScreen(new ConsentScreen(this, selected.displayName, () -> {
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
        client.setScreen(new ApplyConfirmScreen(this, "/" + ready, () -> {
            if (MinecraftClient.getInstance().getNetworkHandler() == null) { status = "Connect to a multiplayer server before applying."; return; }
            MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(ready);
            selected.lastUsedAt = System.currentTimeMillis(); SkinStudioClient.LIBRARY.add(selected);
            status = "Command sent. The server has not confirmed that the skin was applied.";
            SkinStudioClient.toast("Command sent; check the server response for success or permission errors.");
        }));
    }

    private void saveSettings() {
        String value = template.getText().trim();
        if (!value.contains("{value}") || value.contains("\n") || value.contains("\r")) { status = "Command template must include {value} and fit on one line."; return; }
        SkinStudioClient.SERVER_PROFILES.put(SkinStudioClient.serverAddress(), "skinsrestorer", value);
        status = "Saved command profile for " + SkinStudioClient.serverAddress() + ".";
    }

    private void changePage(Page next) { page = next; rebuildWidgets(); }
    private void rebuildWidgets() { clearChildren(); init(); }
    private String rootMessage(Throwable error) { Throwable value = error; while (value.getCause() != null) value = value.getCause(); return value.getMessage() == null ? "Import failed." : value.getMessage(); }
    private String shorten(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = width / 2 - 205;
        context.fill(left - 12, 18, left + 418, height - 18, 0xEF111923);
        context.drawTextWithShadow(textRenderer, "SKIN STUDIO", left, 24, 0x8FE0C6);
        context.drawTextWithShadow(textRenderer, "Client-side skin library", left + 116, 24, 0xAFBAC8);
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWrapped(textRenderer, Text.literal(status), left, height - 54, 410, 0xC4D0DE);
        if (page == Page.IMPORT && selected != null) {
            context.drawTextWithShadow(textRenderer, selected.displayName + " · " + selected.width + "×" + selected.height + " · " + selected.sourceType,
                    left, 156, 0xFFFFFF);
            renderSkinSheet(context, left + 270, 180);
        }
        if (page == Page.SETTINGS) {
            context.drawTextWithShadow(textRenderer, "Server: " + SkinStudioClient.serverAddress(), left, 78, 0xDDE6F1);
            context.drawTextWithShadow(textRenderer, "SkinsRestorer command template. Use {value} and {model}.", left, 94, 0xAEBBC9);
            context.drawTextWithShadow(textRenderer, "MineSkin API key comes from SKINSTUDIO_MINESKIN_API_KEY.", left, 174, 0xAEBBC9);
            context.drawTextWithShadow(textRenderer, "The key is not written to the config directory.", left, 190, 0xAEBBC9);
        }
    }

    private void renderSkinSheet(DrawContext context, int x, int y) {
        if (previewPixels == null) { context.drawTextWithShadow(textRenderer, "2D skin sheet", x, y, 0xB7C4D0); return; }
        int drawWidth = 128; int scale = drawWidth / 64; int drawHeight = previewHeight * scale;
        context.fill(x - 2, y - 2, x + drawWidth + 2, y + drawHeight + 2, 0xFF263544);
        for (int py = 0; py < previewHeight; py++) for (int px = 0; px < 64; px++) {
            int color = previewPixels[py * 64 + px]; if ((color >>> 24) != 0) context.fill(x + px * scale, y + py * scale, x + (px + 1) * scale, y + (py + 1) * scale, color);
        }
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void close() { client.setScreen(null); }

    private static final class ConsentScreen extends Screen {
        private final Screen parent; private final String skinName; private final Runnable consent;
        ConsentScreen(Screen parent, String skinName, Runnable consent) { super(Text.literal("External upload consent")); this.parent = parent; this.skinName = skinName; this.consent = consent; }
        @Override protected void init() {
            int x = width / 2 - 105;
            addDrawableChild(ButtonWidget.builder(Text.literal("Upload to MineSkin"), b -> { consent.run(); client.setScreen(parent); }).dimensions(x, height / 2 + 32, 130, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> client.setScreen(parent)).dimensions(x + 138, height / 2 + 32, 72, 20).build());
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(textRenderer, "Send this PNG to MineSkin?", width / 2, height / 2 - 40, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, shortenStatic(skinName, 48), width / 2, height / 2 - 21, 0x8FE0C6);
            context.drawCenteredTextWithShadow(textRenderer, "MineSkin receives the image and processes it for a texture URL.", width / 2, height / 2 - 2, 0xC4D0DE);
            context.drawCenteredTextWithShadow(textRenderer, "The server decides whether the resulting URL is allowed.", width / 2, height / 2 + 14, 0xAEBBC9);
            super.render(context, mouseX, mouseY, delta);
        }
        private static String shortenStatic(String value, int n) { return value.length() <= n ? value : value.substring(0, n - 1) + "…"; }
        @Override public void close() { client.setScreen(parent); }
    }
}
