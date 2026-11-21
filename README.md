<p align="center"><img src="src/main/resources/icon.png" width="128" alt="IRL Tunes icon"></p>

# 🎵 IRL Tunes

A client-side **NeoForge 26.3** mod that shows the music playing on your computer as a small HUD widget in Minecraft: title, artist, cover art, source app and a live progress bar.

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A)
![Loader](https://img.shields.io/badge/Loader-NeoForge-D7742F)
![Side](https://img.shields.io/badge/Side-Client-5B8DEF)
![License](https://img.shields.io/badge/License-MIT-blue)
![Mod](https://img.shields.io/badge/Mod-1.0.0-E07B39)

It's an open-source (MIT) take on the idea behind the Fabric mod *Now Playing IRL*, written from scratch for NeoForge. No code from that project is used.

## Features

- **Works with almost any player.** On Windows it reads the system media session, the same info the Windows volume flyout shows. That covers Spotify, Apple Music, TIDAL, Deezer, Amazon Music, VLC, foobar2000, MusicBee, AIMP and Media Player, plus YouTube, YouTube Music, SoundCloud and Twitch in Chrome, Edge, Firefox, Brave and Opera.
- **Real cover art** straight from the player. If the player doesn't provide any, it can optionally look the cover up on the iTunes Search API.
- **Live progress bar** with elapsed and total time.
- **12 themes**, six screen positions, 50–250% size, background opacity, compact one-line mode, scrolling titles for long names, and an optional **spinning vinyl** cover.
- **Hides itself** a few seconds after you pause, and respects F1.
- **Light on resources.** Detection runs on a background thread, never the render thread, and polling slows down to every 5 seconds when nothing is playing.
- **Client-only.** Works in singleplayer and on any server, and the server doesn't need it.

## Controls

| Key | Action |
| --- | --- |
| `N` | Show / hide the widget |
| `M` | Open settings |
| `,` | Next theme |

All keys can be rebound in *Options → Controls → Key Binds → IRL Tunes*. Settings are also under *Mods → IRL Tunes → Config*, and saved to `config/irltunes-client.toml`.

## How detection works

| OS | Method | Notes |
| --- | --- | --- |
| Windows 10/11 | System media session (`GlobalSystemMediaTransportControls`) through a small bundled PowerShell script that runs for the whole session ([`gsmtc.ps1`](src/main/resources/assets/irltunes/scripts/gsmtc.ps1)) | Gives title, artist, album, progress and cover. If it's unavailable (for example, PowerShell is blocked by policy), it falls back to reading player window titles through JNA. |
| Linux | MPRIS via `playerctl` | Install `playerctl` from your package manager. |
| macOS | AppleScript (`osascript`) for Spotify and Apple Music | Only asks apps that are already running, so it never launches them. |

**Privacy:** nothing leaves your computer, except that when a player gives no cover art and *Look up missing covers online* is on, the artist and title are sent to `itunes.apple.com` to find one. Turn that option off to stay fully offline.

## Adding an app

Friendly app names and the window-title fallback list live in [`AppNames.java`](src/main/java/dev/irltunes/media/AppNames.java). Each app is one line.

## Building

You need JDK 25.

```sh
./gradlew build        # jar ends up in build/libs/
./gradlew runClient    # start a dev client with the mod
```

> **Windows tip:** if Gradle fails with `Unable to establish loopback connection`, point Java's socket temp folder somewhere writable:
> `set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\temp` (create the folder first).

## Project layout

```
src/main/java/dev/irltunes/
  IrlTunes.java            mod entry: config, keybinds, HUD layer registration
  Config.java              all settings
  hud/NowPlayingHud.java   drawing, animation, cover texture upload
  hud/Theme.java, Anchor.java
  media/MediaService.java  background polling and cover loading
  media/*Source.java       one class per detection method
  media/CoverArt.java      cover decode, resize and online lookup
```

## License

MIT. See [LICENSE](LICENSE).
