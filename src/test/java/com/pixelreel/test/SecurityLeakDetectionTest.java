package com.pixelreel.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pixelreel.channels.Channel;
import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.channels.GuideInfo;
import com.pixelreel.jellyfin.JellyfinItemKind;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests verifying that credential-bearing URLs are redacted before they
 * reach clients. These complement the static {@code securityAudit} Gradle task.
 */
@DisplayName("Security Leak Detection Tests")
public class SecurityLeakDetectionTest {

	private static final String TEST_STREAM_URL = "http://secret-media-server.local:8096/stream?token=abc123xyz";
	private static final String TEST_POSTER_URL = "http://provider.local/poster?apikey=secret";

	@Test
	@DisplayName("Channel.withoutClientSecrets redacts the stream URL")
	void testChannelRedactsStreamUrl() {
		Channel channel = new Channel("ch-1", 1, "Test Channel", "", "", TEST_STREAM_URL);

		Channel clientChannel = channel.withoutClientSecrets();

		assertEquals("", clientChannel.streamUrl(), "Stream URL must be redacted in client channel");
		assertNotEquals(TEST_STREAM_URL, clientChannel.streamUrl(), "Stream URL leaked to client channel!");
		assertEquals(channel.id(), clientChannel.id(), "Channel id must be preserved");
		assertEquals(channel.name(), clientChannel.name(), "Channel name must be preserved");
	}

	@Test
	@DisplayName("ChannelEntry.withoutClientSecrets redacts the stream URL")
	void testChannelEntryRedactsStreamUrl() {
		Channel channel = new Channel("ch-1", 1, "Test Channel", "", "", TEST_STREAM_URL);
		ChannelEntry entry = new ChannelEntry(channel, GuideInfo.EMPTY);

		ChannelEntry clientEntry = entry.withoutClientSecrets();

		assertEquals("", clientEntry.channel().streamUrl(), "Stream URL must be redacted in client packet");
		assertNotEquals(TEST_STREAM_URL, clientEntry.channel().streamUrl(), "Stream URL leaked in client packet!");
	}

	@Test
	@DisplayName("JellyfinItemSummary.forClientPacket redacts the poster image URL")
	void testJellyfinItemSummaryRedactsImageUrl() {
		JellyfinItemSummary item = item(TEST_POSTER_URL, "A Goofy Movie");

		JellyfinItemSummary clientItem = item.forClientPacket();

		assertEquals("", clientItem.imageUrl(), "Poster image URL must be redacted in client packet");
		assertNotEquals(TEST_POSTER_URL, clientItem.imageUrl(), "Provider poster URL leaked in client packet!");
		assertEquals(item.overview(), clientItem.overview(), "Overview is kept for the detail view");
	}

	@Test
	@DisplayName("JellyfinItemSummary.forBrowsePacket redacts the poster image URL and overview")
	void testJellyfinItemSummaryRedactsImageUrlInBrowse() {
		JellyfinItemSummary item = item(TEST_POSTER_URL, "Test Series");

		JellyfinItemSummary browseItem = item.forBrowsePacket();

		assertEquals("", browseItem.imageUrl(), "Poster image URL must be redacted in browse packet");
		assertNotEquals(TEST_POSTER_URL, browseItem.imageUrl(), "Provider poster URL leaked in browse packet!");
		assertEquals("", browseItem.overview(), "Overview must be redacted in browse packet");
	}

	@Test
	@DisplayName("Redacted fields never contain the secret substring")
	void testRedactedFieldsDoNotContainSecrets() {
		JellyfinItemSummary item = item(TEST_POSTER_URL, "Multi Test");

		JellyfinItemSummary clientItem = item.forClientPacket();
		JellyfinItemSummary browseItem = item.forBrowsePacket();

		assertTrue(!clientItem.imageUrl().contains("apikey"), "Client packet must not embed the poster key");
		assertTrue(!browseItem.imageUrl().contains("apikey"), "Browse packet must not embed the poster key");
		assertTrue(!browseItem.overview().contains("sensitive"), "Browse packet must not embed overview text");
	}

	private static JellyfinItemSummary item(String imageUrl, String title) {
		return new JellyfinItemSummary(
			"item-123",
			JellyfinItemKind.MOVIE,
			title,
			"Test overview with sensitive data",
			2024,
			120000000000L,
			imageUrl,
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
	}
}
