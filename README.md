# Skin Studio

A client-side Fabric mod for Minecraft Java Edition 26.3. Import a skin, validate and cache it locally, keep favorites, upload it to MineSkin after explicit consent, then review and deliberately send a SkinsRestorer URL command.

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API 0.165.0+26.3
- Java 25

## Build

Install JDK 25 and run `gradlew.bat build` on Windows (or `./gradlew build` on macOS/Linux). The project compiles to Java 25 bytecode for Minecraft 26.3. The release JAR is written to `build/libs/skin-studio-0.1.0.jar`. Put that JAR and Fabric API in the Minecraft `mods` folder.

### Build for another Minecraft version

Builds are version-specific. To make a build for a different game version, update `minecraft_version`, `loader_version`, and `fabric_api_version` in `gradle.properties` to compatible releases, use the Java version required by that Minecraft release, and update the source code for any changed Minecraft or Fabric APIs. Minecraft 26.1 and newer use Mojang's unobfuscated names; older releases use Yarn mappings and need a separate source migration. Run the Gradle wrapper after making the changes and install the resulting JAR only in a matching Fabric instance.

## Use

1. Join a server and press **K** to open Skin Studio.
2. Choose an import type and enter a Minecraft name, Ely.by name or supported skin URL, a direct PNG URL, or a local PNG path.
3. Import and review the validated skin. Standard 64×64 and legacy 64×32 PNGs are accepted. The library stores image files by SHA-256 under `.minecraft/config/skin-studio/cache/skins` and metadata in `library.json`.
4. Configure a MineSkin API key as the environment variable `SKINSTUDIO_MINESKIN_API_KEY`, then fully restart the launcher. The mod does not store the key in its JSON files. MineSkin requires an account and API key for API requests.
5. Click **Upload to MineSkin** and approve the upload. The image is sent to MineSkin as an unlisted skin-generation request. Uploads cannot currently be canceled after submission.
6. After the provider returns a texture URL, click **Apply uploaded skin**. Inspect the exact command and select **Send command**.

The default command template is `/skin url "{value}" {model}`. Settings are saved for the current server address. Custom templates must include `{value}`; `{model}` is replaced with `classic` or `slim`.

## Privacy and server behavior

- Skin imports from usernames and URLs contact Mojang, Ely.by, or the host in the URL. Direct URL requests accept HTTP/HTTPS on standard ports, block local/private destinations, limit redirects, and cap downloads at 2 MB.
- An upload sends the selected PNG to MineSkin. The key is read from the process environment and is never written by this mod to disk or sent to Minecraft chat.
- The mod does not include a server plugin, bypass permissions, or infer that a command succeeded. SkinsRestorer must support `/skin url` on the server, and the player needs the server's `skinsrestorer.command.set.url` permission. Server URL restrictions can reject MineSkin texture URLs.
- A “command sent” status only means the client sent a normal chat command. Read the server reply and ask another player to verify the skin.
- Favorites, server profiles, imported PNGs, and history metadata are local. No Minecraft account credentials or session tokens are used.

## Current scope

The initial client milestone includes username/URL/local imports, validation, a local favorites library, MineSkin upload with per-upload consent, server-specific command templates, and explicit command confirmation. Legacy 64×32 skins are assigned Classic; 64×64 skins ask you to choose Classic or Slim because their layout alone does not reliably encode the player model. It shows the 2D skin texture sheet; the 3D player preview, public skin marketplace, native file picker, config screen for key management, upload-result cache, upload cancellation, and automated server-reply classification remain follow-up work. Test against a controlled server before release; no compatible test server or provider credentials are included here.

## References

- [Fabric documentation](https://docs.fabricmc.net/)
- [Ely.by skins system](https://docs.ely.by/en/skins-system.html)
- [MineSkin API](https://docs.mineskin.org/docs/mineskin-api/mineskin-api/)
- [MineSkin terms](https://legal.inventivetalent.org/terms/mineskin)
- [SkinsRestorer command permissions](https://skinsrestorer.net/docs/configuration/commands-permissions)
- [SkinsRestorer URL upload guide](https://skinsrestorer.net/docs/features/skin-upload)
