package com.pixelreel.permissions;

import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.Locale;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** simple permission gates for display actions */
public final class CinemaPermissions {
	public static final String EVERYONE = "everyone";
	public static final String OP = "op";

	private CinemaPermissions() {
	}

	public static boolean canBrowse(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionBrowse);
	}

	public static boolean canPlayTunarr(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionPlayTunarr);
	}

	public static boolean canPlayJellyfinMovies(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionPlayJellyfinMovies);
	}

	public static boolean canPlayJellyfinShows(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionPlayJellyfinShows);
	}

	public static boolean canPlayOnDemandMovies(ServerPlayer player) {
		return canPlayJellyfinMovies(player);
	}

	public static boolean canPlayOnDemandShows(ServerPlayer player) {
		return canPlayJellyfinShows(player);
	}

	public static boolean canControlPlayback(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionControlPlayback);
	}

	public static boolean canPause(ServerPlayer player) {
		return canControlPlayback(player);
	}

	public static boolean canSeek(ServerPlayer player) {
		return canControlPlayback(player);
	}

	public static boolean canStop(ServerPlayer player) {
		return canControlPlayback(player);
	}

	public static boolean canChangeContent(ServerPlayer player) {
		return canControlPlayback(player);
	}

	public static boolean canConfigureTunarr(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionConfigureTunarr);
	}

	public static boolean canConfigureJellyfin(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionConfigureJellyfin);
	}

	public static boolean canConfigureEmby(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionConfigureEmby);
	}

	public static boolean canConfigurePlex(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionConfigurePlex);
	}

	public static boolean canConfigureProvider(ServerPlayer player, OnDemandProvider provider) {
		return switch (provider) {
			case JELLYFIN -> canConfigureJellyfin(player);
			case EMBY -> canConfigureEmby(player);
			case PLEX -> canConfigurePlex(player);
		};
	}

	public static boolean canRefreshLibrary(ServerPlayer player) {
		return allows(player, ConfigManager.get().permissionRefreshLibrary);
	}

	public static boolean allows(ServerPlayer player, String rule) {
		if (rule == null || rule.isBlank()) {
			return true;
		}
		String normalized = rule.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals(EVERYONE) || normalized.equals("*") || normalized.equals("true")) {
			return true;
		}
		if (normalized.equals(OP) || normalized.equals("ops") || normalized.equals("operator")) {
			return isOperator(player);
		}
		if (normalized.equals("false") || normalized.equals("none") || normalized.equals("deny")) {
			return false;
		}
		String name = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
		for (String part : normalized.split(",")) {
			String token = part.trim();
			if (token.isEmpty()) {
				continue;
			}
			if (token.equals(OP) && isOperator(player)) {
				return true;
			}
			if (token.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOperator(ServerPlayer player) {
		return player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
	}

	public static PixelReelConfig.FeatureFlags featureFlags(ServerPlayer player) {
		PixelReelConfig config = ConfigManager.get();
		boolean moviesPerm = canPlayOnDemandMovies(player);
		boolean showsPerm = canPlayOnDemandShows(player);
		boolean jellyfinMovies = config.isJellyfinConfigured() && config.jellyfinMoviesEnabled && moviesPerm;
		boolean jellyfinShows = config.isJellyfinConfigured() && config.jellyfinTvShowsEnabled && showsPerm;
		boolean embyMovies = config.isEmbyConfigured() && config.embyMoviesEnabled && moviesPerm;
		boolean embyShows = config.isEmbyConfigured() && config.embyTvShowsEnabled && showsPerm;
		boolean plexMovies = config.isPlexConfigured() && config.plexMoviesEnabled && moviesPerm;
		boolean plexShows = config.isPlexConfigured() && config.plexTvShowsEnabled && showsPerm;
		return new PixelReelConfig.FeatureFlags(
			canBrowse(player),
			canPlayTunarr(player),
			jellyfinMovies || embyMovies || plexMovies,
			jellyfinShows || embyShows || plexShows,
			canControlPlayback(player),
			canConfigureTunarr(player),
			canConfigureJellyfin(player),
			canConfigureEmby(player),
			canConfigurePlex(player),
			canRefreshLibrary(player),
			config.onDemandAutoplayNextEpisode,
			jellyfinMovies,
			jellyfinShows,
			embyMovies,
			embyShows,
			plexMovies,
			plexShows
		);
	}
}
