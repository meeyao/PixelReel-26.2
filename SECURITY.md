# Security

PixelReel connects Minecraft clients to private media services. Treat Jellyfin API keys, Emby API keys, Plex tokens, M3U URLs, XMLTV URLs, and generated playback URLs as secrets.

## Current Protections

- API keys and tokens are stored in the server config, not in client config.
- Configuration screens send clients only public settings plus `hasApiKey`/`hasToken` booleans.
- Channel list packets redact stream URLs and remote artwork URLs.
- Media browse/detail packets redact provider poster URLs because those URLs can contain API keys or Plex tokens.
- Display block entity saves and normal block entity sync redact stream URLs, subtitle URLs, and media poster URLs.
- Clients request a playback URL only when they are near a playing display. The server validates the display position, epoch, and playback distance before sending the URL.
- On-demand (Jellyfin/Emby/Plex) streams and subtitles are relayed through a server-side media proxy (TCP `28100` by default). The provider API key/token is used only on the server; players receive opaque `/stream/<token>` and `/subtitle/<token>` proxy paths that contain no credentials.
- Proxy tokens are revoked when a display changes title/channel/subtitle and expire after 24 hours, so a re-issued token for a changed screen no longer works. Tokens are stable per screen + URL so VLC seeks and reconnects keep working.
- `/tv` commands use the same permission gates as the packet handlers for browsing, tuning, playback control, refresh, and configuration.
- Active playback URLs are intentionally ephemeral. They are not restored from world data after a server restart.

## Remaining Limitations

Local video playback still requires the client to receive a playable stream URL. Any player authorized by server permissions and close enough to a playing display can extract that URL from their own client while playback is active.

The media proxy keeps provider credentials off the player-facing URL, but the proxy token itself is still a playable URL for the lifetime of the screen's current playback (until playback changes or the 24h TTL expires). Live TV (Tunarr/HLS) streams are not yet proxied and are served directly.

For public or semi-trusted servers:

- Use media-server accounts with limited library access.
- Prefer short-lived or revocable tokens where the provider supports them.
- Keep `permissionPlayTunarr`, on-demand play permissions, and playback-control permissions restricted to trusted players.
- Publish TCP `28100` (the proxy port) only to the networks your players use; set `proxyHost` in the config if players reach the server through a NAT or Cloudflare-routed hostname.
- Assume that any player allowed to watch a private stream may be able to reuse its playback URL until playback changes or the server restarts.
- Restarting the server clears in-memory playback URLs and proxy tokens. Displays retain metadata, but private streams must be selected again.

## Reporting Vulnerabilities

Do not post API keys, Plex tokens, private Jellyfin/Emby URLs, stream URLs, world saves containing secrets, or logs containing secrets in public issues.

Open a private report with:

- PixelReel version or commit.
- Minecraft/Fabric version.
- Whether the issue occurs in single-player, LAN, or dedicated server.
- Exact permissions/configuration needed to reproduce, with secrets redacted.
- Steps to reproduce and expected impact.

## Pre-Release Security Checklist

- Search packet payloads for secret-bearing fields before release.
- Search world-save serialization for stream URLs, API keys, tokens, poster URLs, and subtitle URLs.
- Verify non-operator command use cannot tune, browse, refresh, configure, or control screens unless permissions allow it.
- Verify non-configuring clients never receive API keys, Plex tokens, or provider configuration secrets.
- Verify generated stream/artwork/poster/subtitle URLs are not logged at info level.
- Run `./gradlew check` or `JAVA_HOME=/usr/lib/jvm/java-26-openjdk bash gradlew check` and confirm the `securityAudit` task passes.
