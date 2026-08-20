package com.pixelreel.client.playback.subtitle;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Burns subtitles into a video frame.
 * Uses a small bottom strip (not a full-frame BufferedImage) and caches the strip
 * so we do not allocate megabytes of heap every decoded frame.
 */
public final class SubtitleRasterizer {
	private static final Object LOCK = new Object();
	private static String cachedText = "";
	private static int cachedWidth;
	private static int cachedStripHeight;
	private static int @Nullable [] cachedStrip;

	private SubtitleRasterizer() {
	}

	public static void burnIn(ByteBuffer rgba, int width, int height, String text) {
		if (rgba == null || !rgba.isDirect() || width < 32 || height < 32 || text == null || text.isBlank()) {
			return;
		}
		int bytes = width * height * 4;
		if (rgba.remaining() < bytes) {
			return;
		}
		try {
			int fontSize = Math.max(16, Math.min(48, height / 14));
			int stripHeight = Math.min(height, blockBottom(height, fontSize, text));
			int[] strip = stripPixels(text, width, stripHeight, fontSize);
			if (strip == null) {
				return;
			}
			long base = MemoryUtil.memAddress(rgba);
			int top = height - stripHeight;
			for (int row = 0; row < stripHeight; row++) {
				int frameRow = top + row;
				int rowOffset = row * width;
				boolean any = false;
				for (int col = 0; col < width; col++) {
					if (((strip[rowOffset + col] >>> 24) & 0xFF) > 8) {
						any = true;
						break;
					}
				}
				if (!any) {
					continue;
				}
				for (int col = 0; col < width; col++) {
					int argb = strip[rowOffset + col];
					int a = (argb >>> 24) & 0xFF;
					if (a < 8) {
						continue;
					}
					int sr = (argb >>> 16) & 0xFF;
					int sg = (argb >>> 8) & 0xFF;
					int sb = argb & 0xFF;
					long p = base + ((long) frameRow * width + col) * 4L;
					if (a >= 250) {
						MemoryUtil.memPutByte(p, (byte) sr);
						MemoryUtil.memPutByte(p + 1, (byte) sg);
						MemoryUtil.memPutByte(p + 2, (byte) sb);
					} else {
						int dr = MemoryUtil.memGetByte(p) & 0xFF;
						int dg = MemoryUtil.memGetByte(p + 1) & 0xFF;
						int db = MemoryUtil.memGetByte(p + 2) & 0xFF;
						MemoryUtil.memPutByte(p, (byte) ((sr * a + dr * (255 - a)) / 255));
						MemoryUtil.memPutByte(p + 1, (byte) ((sg * a + dg * (255 - a)) / 255));
						MemoryUtil.memPutByte(p + 2, (byte) ((sb * a + db * (255 - a)) / 255));
					}
				}
			}
		} catch (Throwable ignored) {
			// Keep video playing even if subtitle burn-in fails.
		}
	}

	private static int @Nullable [] stripPixels(String text, int width, int stripHeight, int fontSize) {
		synchronized (LOCK) {
			if (cachedStrip != null
				&& cachedWidth == width
				&& cachedStripHeight == stripHeight
				&& text.equals(cachedText)) {
				return cachedStrip;
			}
			BufferedImage image = new BufferedImage(width, stripHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			try {
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
				FontMetrics metrics = g.getFontMetrics();
				String[] lines = text.split("\\n");
				int lineHeight = metrics.getHeight();
				int y = Math.max(metrics.getAscent(), stripHeight - 8 - lineHeight * (lines.length - 1));
				for (String line : lines) {
					String trimmed = line.strip();
					if (trimmed.isEmpty()) {
						y += lineHeight;
						continue;
					}
					int x = Math.max(4, (width - metrics.stringWidth(trimmed)) / 2);
					g.setColor(Color.BLACK);
					g.drawString(trimmed, x - 2, y);
					g.drawString(trimmed, x + 2, y);
					g.drawString(trimmed, x, y - 2);
					g.drawString(trimmed, x, y + 2);
					g.setColor(Color.WHITE);
					g.drawString(trimmed, x, y);
					y += lineHeight;
				}
			} finally {
				g.dispose();
			}
			int[] pixels = new int[width * stripHeight];
			image.getRGB(0, 0, width, stripHeight, pixels, 0, width);
			cachedText = text;
			cachedWidth = width;
			cachedStripHeight = stripHeight;
			cachedStrip = pixels;
			return pixels;
		}
	}

	private static int blockBottom(int height, int fontSize, String text) {
		int lines = Math.max(1, text.split("\\n").length);
		return Math.max(height / 12, 16) + (fontSize + 8) * lines;
	}
}
