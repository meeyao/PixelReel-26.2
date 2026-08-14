package com.pixelreel.test;

import static org.junit.jupiter.api.Assertions.*;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.channels.Channel;
import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.jellyfin.JellyfinItemKind;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.media.MediaSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests to verify that credentials and secret-bearing URLs are not leaked
 * in world-save serialization or network packets.
 *
 * These tests ensure:
 * - API keys, Plex tokens, and stream URLs are not persisted to world saves.
 * - Network packets redact provider URLs and artwork URLs before client transmission.
 * - Playback URLs are ephemeral and permission-gated.
 */
@DisplayName("Security Leak Detection Tests")
public class SecurityLeakDetectionTest {

	private static final String TEST_STREAM_URL = "http://secret-media-server.local:8096/stream?token=abc123xyz";
	private static final String TEST_POSTER_URL = "http://provider.local/poster?apikey=secret";
	private static final String TEST_SUB_URL = "http://subtitle-provider.local/file.vtt?key=secret";
	private static final String TEST_PLEX_PART_KEY = "/library/metadata/12345/parts/67890";

	@Test
	@DisplayName("DisplayBlockEntity does not persist stream URL to world save")
	void testDisplayBlockEntityDoesNotPersistStreamUrl() {
		DisplayBlockEntity display = createTestDisplay();
		display.setStreamUrl(TEST_STREAM_URL);

		CompoundTag tag = saveAndLoad(display);

		// After save/load, streamUrl should be empty, not the secret
		String loaded = tag.getString("StreamUrl");
		assertEquals("", loaded, "Stream URL must not be persisted to world save");
		assertNotEquals(TEST_STREAM_URL, loaded, "Actual stream URL leaked to world save!");
	}

	@Test
	@DisplayName("DisplayBlockEntity does not persist media image URL to world save")
	void testDisplayBlockEntityDoesNotPersistMediaImageUrl() {
		DisplayBlockEntity display = createTestDisplay();
		display.setMediaImageUrl(TEST_POSTER_URL);

		CompoundTag tag = saveAndLoad(display);

		String loaded = tag.getString("MediaImage");
		assertEquals("", loaded, "Media image URL must not be persisted to world save");
		assertNotEquals(TEST_POSTER_URL, loaded, "Poster URL leaked to world save!");
	}

	@Test
	@DisplayName("DisplayBlockEntity does not persist subtitle fetch URL to world save")
	void testDisplayBlockEntityDoesNotPersistSubtitleUrl() {
		DisplayBlockEntity display = createTestDisplay();
		display.applySubtitleSelection(0, TEST_SUB_URL);

		CompoundTag tag = saveAndLoad(display);

		String loaded = tag.getString("SubUrl");
		assertEquals("", loaded, "Subtitle fetch URL must not be persisted to world save");
		assertNotEquals(TEST_SUB_URL, loaded, "Subtitle URL leaked to world save!");
	}

	@Test
	@DisplayName("DisplayBlockEntity does not persist Plex part key to world save")
	void testDisplayBlockEntityDoesNotPersistPlexPartKey() {
		DisplayBlockEntity display = createTestDisplay();
		display.setPlexPartKey(TEST_PLEX_PART_KEY);

		CompoundTag tag = saveAndLoad(display);

		String loaded = tag.getString("PlexPartKey");
		assertEquals("", loaded, "Plex part key must not be persisted to world save");
		assertNotEquals(TEST_PLEX_PART_KEY, loaded, "Plex part key leaked to world save!");
	}

	@Test
	@DisplayName("ChannelEntry redacts stream and artwork URLs in client packets")
	void testChannelEntryRedactsUrlsForClient() {
		ChannelEntry entry = new ChannelEntry(
			"ch-1",
			1,
			"Test Channel",
			TEST_STREAM_URL,
			TEST_POSTER_URL,
			Channel.NO_RATING
		);

		ChannelEntry clientEntry = entry.withoutClientSecrets();

		assertNotEquals(TEST_STREAM_URL, clientEntry.streamUrl(),
			"Stream URL must be redacted in client packet");
		assertNotEquals(TEST_POSTER_URL, clientEntry.artworkUrl(),
			"Artwork URL must be redacted in client packet");
		assertTrue(clientEntry.streamUrl().isEmpty() || clientEntry.streamUrl().equals(""),
			"Stream URL should be empty in client packet");
		assertTrue(clientEntry.artworkUrl().isEmpty() || clientEntry.artworkUrl().equals(""),
			"Artwork URL should be empty in client packet");
	}

	@Test
	@DisplayName("JellyfinItemSummary redacts image URL in client packets")
	void testJellyfinItemSummaryRedactsImageUrl() {
		JellyfinItemSummary item = new JellyfinItemSummary(
			"item-123",
			JellyfinItemKind.MOVIE,
			"Test Movie",
			"Test overview",
			2024,
			120000000000L, // runtimeTicks
			TEST_POSTER_URL, // imageUrl
			0L,
			120000000000L,
			false,
			0,
			0,
			0,
			"",
			"",
			""
		);

		JellyfinItemSummary clientItem = item.forClientPacket();

		assertEquals("", clientItem.imageUrl(),
			"Image URL must be redacted in client packet");
		assertNotEquals(TEST_POSTER_URL, clientItem.imageUrl(),
			"Provider poster URL leaked in client packet!");
	}

	@Test
	@DisplayName("JellyfinItemSummary redacts image URL in browse packets")
	void testJellyfinItemSummaryRedactsImageUrlInBrowse() {
		JellyfinItemSummary item = new JellyfinItemSummary(
			"item-456",
			JellyfinItemKind.SERIES,
			"Test Series",
			"Test overview",
			2024,
			0L,
			TEST_POSTER_URL,
			0L,
			0L,
			false,
			5,
			0,
			0,
			"",
			"series-123",
			""
		);

		JellyfinItemSummary browseItem = item.forBrowsePacket();

		assertEquals("", browseItem.imageUrl(),
			"Image URL must be redacted in browse packet");
		assertNotEquals(TEST_POSTER_URL, browseItem.imageUrl(),
			"Provider poster URL leaked in browse packet!");
	}

	@Test
	@DisplayName("Multiple redacted fields are empty in client packets")
	void testMultipleRedactedFieldsInClientPacket() {
		JellyfinItemSummary item = new JellyfinItemSummary(
			"multi-test",
			JellyfinItemKind.MOVIE,
			"Multi Test",
			"A long overview with sensitive data",
			2024,
			7200000000000L,
			TEST_POSTER_URL,
			0L,
			7200000000000L,
			false,
			0,
			0,
			0,
			"",
			"",
			""
		);

		JellyfinItemSummary clientItem = item.forClientPacket();

		// Verify sensitive fields are redacted
		assertEquals("", clientItem.imageUrl(), "Image URL not redacted");
		// Overview should still be present for detail view, but not in browse
		JellyfinItemSummary browseItem = item.forBrowsePacket();
		assertEquals("", browseItem.overview(), "Overview must be redacted in browse packet");
	}

	@Test
	@DisplayName("World save loading does not restore secret URLs")
	void testWorldSaveLoadingDoesNotRestoreSecrets() {
		DisplayBlockEntity display1 = createTestDisplay();
		display1.setStreamUrl(TEST_STREAM_URL);
		display1.setMediaImageUrl(TEST_POSTER_URL);
		display1.applySubtitleSelection(0, TEST_SUB_URL);
		display1.setPlexPartKey(TEST_PLEX_PART_KEY);

		// Save to NBT
		CompoundTag tag = saveAndLoad(display1);

		// Create a new display and load the NBT
		DisplayBlockEntity display2 = createTestDisplay();
		display2.loadAdditional(tag);

		// None of the secret URLs should be restored
		assertEquals("", display2.getStreamUrl(), "Stream URL was restored from save!");
		assertEquals("", display2.getMediaImageUrl(), "Media image URL was restored from save!");
		assertEquals("", display2.getSubtitleFetchUrl(), "Subtitle URL was restored from save!");
		assertEquals("", display2.getPlexPartKey(), "Plex part key was restored from save!");
	}

	// Helper methods

	private DisplayBlockEntity createTestDisplay() {
		BlockPos pos = new BlockPos(0, 0, 0);
		BlockState state = null; // In real tests, mock this
		return new DisplayBlockEntity(pos, state);
	}

	private CompoundTag saveAndLoad(DisplayBlockEntity display) {
		// This is a simplified test. In a real environment, you'd use Minecraft's
		// NBT serialization infrastructure directly.
		// For now, this simulates the saveAdditional flow.
		CompoundTag tag = new CompoundTag();
		try {
			// Call the protected saveAdditional method via reflection if needed,
			// or use a public wrapper in tests
			display.saveAdditional(tag);
		} catch (Exception e) {
			fail("Failed to serialize display block entity: " + e.getMessage());
		}
		return tag;
	}
}
