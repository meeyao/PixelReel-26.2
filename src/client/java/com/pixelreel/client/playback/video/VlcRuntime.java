package com.pixelreel.client.playback.video;

import com.pixelreel.PixelReel;
import com.pixelreel.config.ConfigManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

/** player's content runtime. */
public final class VlcRuntime {
	public static final String PATH_PROPERTY = "pixelreel.vlc.path";
	private static final AtomicBoolean INITIALISED = new AtomicBoolean();
	private static volatile @Nullable MediaPlayerFactory factory;
	private static volatile String unavailableReason = "";

	private VlcRuntime() {
	}

	public static boolean isAvailable() {
		ensureInitialised();
		return factory != null;
	}

	public static String unavailableReason() {
		ensureInitialised();
		return unavailableReason;
	}

	public static @Nullable MediaPlayerFactory factory() {
		ensureInitialised();
		return factory;
	}

	public static void ensureInitialised() {
		if (!INITIALISED.compareAndSet(false, true)) {
			return;
		}
		try {
			applyExplicitPath();
			if (!new NativeDiscovery().discover()) {
				unavailableReason = "libVLC was not found. Install VLC (64-bit) or set -D" + PATH_PROPERTY + "=<folder containing libvlc>";
				PixelReel.LOGGER.warn("pixelReel: {}", unavailableReason);
				return;
			}
			int caching = Math.clamp(ConfigManager.get().streamCachingMillis, 0, 10000);
			List<String> args = List.of(
				"--quiet",
				"--intf=dummy",
				"--vout=dummy",
				"--no-video-title-show",
				"--no-snapshot-preview",
				"--no-sub-autodetect-file",
				"--network-caching=" + caching,
				"--live-caching=" + caching,
				"--file-caching=" + caching,
				"--clock-jitter=0",
				"--clock-synchro=0",
				"--no-audio-time-stretch"
			);
			factory = new MediaPlayerFactory(args);
			PixelReel.LOGGER.info("pixelReel: libVLC initialised (native audio, {} ms cache)", caching);
		} catch (Throwable t) {
			unavailableReason = "libVLC failed to load: " + t;
			PixelReel.LOGGER.error("pixelReel: could not initialise libVLC", t);
			factory = null;
		}
	}

	private static void applyExplicitPath() {
		String configured = System.getProperty(PATH_PROPERTY, "").trim();
		if (!configured.isEmpty()) {
			Path path = Path.of(configured);
			if (!Files.isDirectory(path)) {
				PixelReel.LOGGER.warn("pixelReel: {} points at {}, which is not a directory", PATH_PROPERTY, configured);
			} else {
				String existing = System.getProperty("jna.library.path", "");
				System.setProperty("jna.library.path", existing.isEmpty() ? configured : configured + File.pathSeparator + existing);
			}
		}
	}

	public static void shutdown() {
		MediaPlayerFactory current = factory;
		factory = null;
		if (current != null) {
			try {
				current.release();
			} catch (Throwable t) {
				PixelReel.LOGGER.warn("pixelReel: error releasing libVLC factory", t);
			}
		}
	}
}
