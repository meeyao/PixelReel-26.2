# Contributing

## Branch Workflow

- `main` is the release branch.
- Use short-lived feature or fix branches from `main`.
- Open a pull request for every change that affects networking, permissions, persistence, media-provider integration, or playback.
- Require review before merging security-sensitive changes.

## Security-Sensitive Changes

Before opening a PR, audit for credential exposure:

- Clientbound packets must not include API keys, Plex tokens, raw M3U stream URLs, remote artwork/poster URLs, subtitle URLs, or generated playback URLs unless the packet is specifically scoped to active playback.
- World-save data must not store API keys, Plex tokens, raw stream URLs, provider poster URLs, subtitle URLs, or generated playback URLs.
- Commands must enforce the same permissions as equivalent networking actions.
- Logs should show hostnames or high-level status only, not full URLs.

## Testing Expectations

For security-related PRs, include manual test notes or automated tests covering:

- Non-operator command behavior.
- Non-configuring client configuration screens.
- Channel browsing without stream URL exposure.
- Media browsing without tokenized artwork/poster URL exposure.
- World-save data after a display has played Tunarr, Jellyfin, Emby, or Plex content.

## Commit Style

Keep commits focused. Mention security-impacting behavior directly in the commit message, for example:

`Redact stream URLs from channel list packets`
