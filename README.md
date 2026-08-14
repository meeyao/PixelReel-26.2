# pixelReel

Fabric mod for **Minecraft Java Edition 26.2** that lets you place televisions and cinema screens in your world and watch **live TV** and **on-demand media** together with friends.

Live channels come from a Tunarr (or any M3U + XMLTV) playlist. Movies and shows can come from **Jellyfin**, **Emby**, or **Plex**. Each display keeps its own channel or title, multiple screens can play at once, and audio is positional.

<h2><font color="red">SECURITY NOTICE: THIS FORK IS A SECURITY RESET</font></h2>

This repository is a fork/rework of PixelReel after serious multiplayer credential-leak issues were identified in the original code path. Earlier builds could expose media-server URLs, stream URLs, API keys, Plex tokens, or tokenized artwork URLs to players who should not receive them.

This fork treats that as a release-blocking bug class:

- API keys and Plex tokens stay in server config.
- World saves do not persist stream URLs, subtitle URLs, tokenized artwork/poster URLs, or Plex part keys.
- Normal display sync and channel/media browse packets redact secret-bearing URLs.
- Active playback URLs are temporary and are only sent to permitted nearby clients that need them for local VLC playback.
- `./gradlew check` runs a `securityAudit` task that fails on known leak-pattern regressions.

Important limitation: local VLC playback means any player allowed to watch a screen must receive a playable URL while that screen is active. Do not give watch/play permissions to untrusted players, and use limited media-server accounts/tokens for multiplayer servers.

AI-assisted development was used on this fork. Security-sensitive changes are expected to be reviewable in code, covered by the audit checklist, and documented in [SECURITY.md](SECURITY.md). Do not trust claims; inspect the code and run the build checks.

## Requirements


| Requirement                   | Notes                                                                                             |
| ----------------------------- | ------------------------------------------------------------------------------------------------- |
| **Java 25+**                  | Required by this snapshot toolchain.                                                              |
| **Minecraft 26.2** | Built against this stable release.                                                          |
| **Fabric Loader** `>=0.19.3`  | Pinned in `gradle.properties`.                                                                    |
| **Fabric API**                | Use the build matching `26.2` (see `gradle.properties`).                                          |
| **VLC (64-bit)**              | Needed on each **client** for video/audio. Install from [videolan.org](https://www.videolan.org). |


Without VLC, menus, guide, commands, and multiplayer sync still work, but screens show a “video player unavailable” state instead of video.

If VLC is installed in a non-standard location, launch the game with:

```text
-Dpixelreel.vlc.path=C:\Path\To\VLC
```

## Building

Windows:

```bat
build-mod.bat
```

or:

```bat
gradlew.bat clean build
```

The installable JAR is:

```text
build\libs\pixelreel-1.1.1.jar
```

Use the JAR **without** `-sources`.

Dev client:

```bat
run-client.bat
```

## Installing

1. Install Fabric Loader for Minecraft **26.2**.
2. Put **Fabric API** in your `mods` folder.
3. Put `pixelreel-1.1.1.jar` in `mods`.
4. Install **64-bit VLC** on every client that should see video.

The mod runs on clients and servers (`environment: *`). For multiplayer, **both sides** need the mod. VLC is only required on clients.

## Configuration

Created on first launch at `config/pixelreel.json`.

### Tunarr (Live TV / M3U / XMLTV)


| Key        | Default | Meaning                                                                                            |
| ---------- | ------- | -------------------------------------------------------------------------------------------------- |
| `m3uUrl`   | `""`    | Tunarr base URL or full M3U URL. A base like `http://1.1.1.1:8000` expands to `/api/channels.m3u`. |
| `xmltvUrl` | `""`    | XMLTV guide URL. Auto-filled from a Tunarr base as `/api/xmltv.xml` when empty.                    |


![Tunarr Config](src/main/resources/assets/screenshots/Tunarr%20Config.png)

### Jellyfin

**Tip:** create a dedicated Jellyfin user with access only to **Movies** and **TV Shows**, or **Anime** then use that user’s API key here. I feel it’s simpler and safer than pointing the mod at your admin account.

| Key                                   | Default | Meaning                                   |
| ------------------------------------- | ------- | ----------------------------------------- |
| `jellyfinUrl`                         | `""`    | Server URL (often port `8096`).           |
| `jellyfinApiKey`                      | `""`    | API key.                                  |
| `jellyfinUserId`                      | `""`    | Optional user id                          |
| `jellyfinMoviesEnabled`               | `true`  | Show movies.                              |
| `jellyfinTvShowsEnabled`              | `true`  | Show TV series.                           |
| `jellyfinLibraryIds`                  | `[]`    | Limit to specific libraries; empty = all. |
| `jellyfinAutoplayNextEpisode`         | `true`  | Autoplay next episode.                    |
| `jellyfinLibraryCacheSeconds`         | `600`   | Library cache lifetime.                   |
| `jellyfinProgressReportSeconds`       | `30`    | Playback progress report interval.        |
| `jellyfinNextEpisodeCountdownSeconds` | `10`    | Countdown before next episode.            |


![Jellyfin config](src/main/resources/assets/screenshots/jellyfin%20config.png)

### Emby
**Tip:** create a dedicated Emby user with access only to **Movies** and **TV Shows**, or **Anime** then use that user’s API key here. I feel it’s simpler and safer than pointing the mod at your admin account.


| Key                       | Default | Meaning                                   |
| ------------------------- | ------- | ----------------------------------------- |
| `embyUrl`                 | `""`    | Server URL.                               |
| `embyApiKey`              | `""`    | API key.                                  |
| `embyUserId`              | `""`    | Optional user id.                         |
| `embyMoviesEnabled`       | `true`  | Show movies.                              |
| `embyTvShowsEnabled`      | `true`  | Show TV series.                           |
| `embyLibraryIds`          | `[]`    | Limit to specific libraries; empty = all. |
| `embyLibraryCacheSeconds` | `600`   | Library cache lifetime.                   |


![Emby config](src/main/resources/assets/screenshots/Emby%20config.png)

### Plex

**Tip:** prefer a Plex account/user that only has access to **Movies**, **TV Shows**, and/or **Anime**, then use that account’s token here instead of a full admin setup.

#### How to get `plexToken`

1. Open your Plex web app and sign in.
2. Click any movie or TV show (for example *American Dad*).
3. Click the **three dots** → **Get Info** → **View XML**.
4. In the address bar of the XML page, find `X-Plex-Token=` — the value after it is your Plex token.
5. Paste that value into `plexToken` in in game GUI.

| Key                       | Default | Meaning                                   |
| ------------------------- | ------- | ----------------------------------------- |
| `plexUrl`                 | `""`    | Server URL (often port `32400`).          |
| `plexToken`               | `""`    | Plex token (see steps above).             |
| `plexMoviesEnabled`       | `true`  | Show movies.                              |
| `plexTvShowsEnabled`      | `true`  | Show TV series.                           |
| `plexLibraryKeys`         | `[]`    | Limit to specific libraries; empty = all. |
| `plexLibraryCacheSeconds` | `600`   | Library cache lifetime.                   |


![Plex config](src/main/resources/assets/screenshots/Plex%20config.png)

**API keys and tokens stay server-side. Clients only receive non-secret public config.** Active playback URLs are treated as ephemeral secrets and are only sent to permitted nearby clients that need them for local VLC playback.

## Displays


| Block                | Size   | Audio range |
| -------------------- | ------ | ----------- |
| Compact Television   | 3 × 2  | 16          |
| Wall Television      | 6 × 4  | 24          |
| Ultrawide Monitor    | 8 × 4  | 24          |
| Cinema Screen        | 14 × 8 | 80          |
| Curved Cinema Screen | 16 × 7 | 80          |

**Using a curved cinema screen is the best way to watch 4k HDR content when creating your own home movie theater. (just my opinion)**

![Curved Screen with glasses](src/main/resources/assets/screenshots/Curved%20Screen%20with%20glasses.png)

All are craftable, appear in the **pixelReel** creative tab, and are findable by searching *television*, *TV*, *screen*, *cinema*, or *monitor*. (need more work on this but for now)

**Pixel Glasses** are also craftable: wear them for a fullscreen overlay of a nearby playing screen.

Only the active screen area has collision — bezels stay buildable so you can frame screens with your own blocks. Breaking the Center of the controller removes the whole display; \
player build is never touched.

- **Right-click** a display: open the media menu.
- **Sneak + right-click**: toggle power.
- Video is letterboxed/pillarboxed to the screen aspect (never stretched), it plays its orginial aspect ratio

![Choose your source](src/main/resources/assets/screenshots/choose%20your%20source.png)

![Media selector](src/main/resources/assets/screenshots/media%20selector.png)

![Posters Movies](src/main/resources/assets/screenshots/Posters%20Movies.png)

![Posters TV Shows](src/main/resources/assets/screenshots/Posters%20Tvshows.png) 

## Commands

Most screen actions use these commands

```text
/tv menu                 open the media menu
/tv channels             list live channels
/tv channel <n or name>  tune a live channel
/tv next | previous      change channel
/tv guide                now/next programme overview
/tv status               power, channel, and volume diagnostics
/tv retry                restart playback
/tv reload               re-download playlist/guide
/tv stop | resume        pause/resume playback
/tv power on|off|toggle  power control
/tv volume <0-100>       per-display volume
/tv rebuild              rebuild screen collision without touching builds
/tv jellyfin status|refresh|configure
```

## Multiplayer

Display state (type, power, channel/media, facing, volume, playback position) is **server-auth** and synced to every client, including players who join later.

Each client decodes video locally with its own VLC. API keys, Plex tokens, stream URLs, subtitle URLs, and remote artwork/poster URLs are not stored in world data or normal display sync packets.

Security limitation: a player who has permission to watch a screen and is close enough to it must receive a playable URL while playback is active. Use restricted media-server accounts/tokens for multiplayer servers, and keep play/configuration permissions limited to trusted players.

After a server restart, active playback URLs are intentionally not restored from world data. Displays keep their metadata, but private streams must be selected again.

## Roadmap

### Current Scope

| Area | Status | Notes |
| ---- | ------ | ----- |
| Fabric mod targeting Minecraft **26.2** | Done | Current supported target for this fork. |
| Display blocks | Done | Compact TV, Wall TV, Ultrawide Monitor, Cinema Screen, Curved Cinema Screen. |
| Live TV | Done | Tunarr or direct M3U + XMLTV guide. |
| On-demand providers | Done | Jellyfin, Emby, and Plex. |
| Provider config GUIs | Done | Tunarr, Jellyfin, Emby, Plex. |
| Browse UI | Done | Movies, shows, seasons, episodes. Remote tokenized artwork is redacted in multiplayer packets. |
| Playback controls | Done | Power, pause/resume, stop, volume, channel/media select, subtitles, seek. |
| `/tv` command suite | Done | Commands are permission-gated. |
| Multiplayer sync | Done | Server-authoritative display state with late-join sync. Playback URLs are ephemeral. |
| VLC playback | Done | Client-side decode with positional audio. |
| Visual playback handling | Done | Letterboxing, subtitle overlay, HDR tone mapping. |
| Pixel Glasses | Done | Fullscreen overlay for a nearby playing screen. |
| Security hardening | In progress | Known leak paths are blocked and checked by `securityAudit`; broader review is still welcome. |

### Planned Work

| Feature | Status | Priority | Notes |
| ------- | ------ | -------- | ----- |
| **Security review and tests** | Active | Critical | Expand automated tests/audits around packets, world saves, permissions, and logs. |
| **Server-side media proxy / short-lived playback URLs** | Research | Critical | Needed to avoid giving clients long-lived playable URLs during local VLC playback. |
| **Admin remote control** | Planned | High | Item or GUI usable from anywhere (not only at the screen). Admins can start, pause, rewind/seek, pick content, and open config for any display. |
| **Personalized screens** | Planned | High | Per-player private viewing — each player can have their own channel/title on a shared or personal display without forcing everyone onto the same stream. |
| **Movie scheduler** | Planned | Medium | Queue showtimes (date/time + title or channel). Auto power-on, start playback, and optional lobby announcements for cinema nights. |
| **YouTube / streaming integration** | Deferred | Medium | Must not ship until provider URL/security behavior is understood. |
| **Posters as paintings** | Deferred | Medium | Needs a safe artwork proxy/cache model before remote provider artwork is exposed. |
| **Fabric on stable versions** | Planned | High | Port from `26.2` to older/stable Fabric target **1.20.1**. |
| **NeoForge support** | Planned | High | First-class NeoForge build so servers/clients on NeoForge can run pixelReel. |
| **Forge support** | Planned | Medium | Forge port after (or alongside) NeoForge, depending on version demand. |

DISCORD: https://discord.gg/RSWQuEnMj
Minecraft Mod 1.21.1 upstream/related repo: https://github.com/Samarth-programming/PixelReel_1.21.1

Ideas and PRs welcome — especially for loaders, version ports, and the admin remote.
