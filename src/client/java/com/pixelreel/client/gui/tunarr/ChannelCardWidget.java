package com.pixelreel.client.gui.tunarr;

import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.client.gui.shared.SharedPoster;
import com.pixelreel.client.texture.PosterCache;
import java.time.Instant;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** showing posters for live tv channels */
public class ChannelCardWidget extends AbstractWidget {
	static final int CARD_WIDTH = 78;
	static final int POSTER_HEIGHT = 110;
	static final int CARD_HEIGHT = POSTER_HEIGHT + 38;

	private static final int COLOR_CARD = 0xE0101418;
	private static final int COLOR_CARD_HOVER = 0xE01C242C;
	private static final int COLOR_SELECTED = 0xFF46C878;
	private static final int COLOR_POSTER_BACK = 0xFF06080A;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_TEXT_DIM = 0xFFB0B8C0;
	private static final int COLOR_TEXT_FAINT = 0xFF6E7880;
	private static final int COLOR_PROGRESS_BACK = 0xFF2A3238;
	private static final int COLOR_PROGRESS = 0xFF46C878;
	private static final int COLOR_ERROR = 0xFFFF6060;

	private final ChannelMenuScreen menu;
	private ChannelEntry entry;
	private boolean selected;

	public ChannelCardWidget(ChannelMenuScreen menu, int x, int y, ChannelEntry entry, boolean selected) {
		super(x, y, CARD_WIDTH, CARD_HEIGHT, Component.literal(entry.channel().name()));
		this.menu = menu;
		this.entry = entry;
		this.selected = selected;
	}

	public void update(ChannelEntry entry, boolean selected) {
		this.entry = entry;
		this.selected = selected;
		this.setMessage(Component.literal(entry.channel().name()));
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		if (!this.entry.channel().isPlayable()) {
			return;
		}
		this.menu.tuneTo(this.entry);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		int x = this.getX();
		int y = this.getY();
		boolean hovered = this.isHovered();
		graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, hovered ? COLOR_CARD_HOVER : COLOR_CARD);
		if (this.selected) {
			graphics.fill(x - 1, y - 1, x + CARD_WIDTH + 1, y, COLOR_SELECTED);
			graphics.fill(x - 1, y + CARD_HEIGHT, x + CARD_WIDTH + 1, y + CARD_HEIGHT + 1, COLOR_SELECTED);
			graphics.fill(x - 1, y, x, y + CARD_HEIGHT, COLOR_SELECTED);
			graphics.fill(x + CARD_WIDTH, y, x + CARD_WIDTH + 1, y + CARD_HEIGHT, COLOR_SELECTED);
		}

		int thumbX = x + 3;
		int thumbY = y + 3;
		int thumbWidth = CARD_WIDTH - 6;
		int thumbHeight = POSTER_HEIGHT - 6;
		graphics.fill(thumbX, thumbY, thumbX + thumbWidth, thumbY + thumbHeight, COLOR_POSTER_BACK);

		var font = Minecraft.getInstance().font;
		PosterCache.Poster thumb = PosterCache.INSTANCE.get(this.entry);
		if (thumb.state() == PosterCache.State.READY && thumb.texture() != null) {
			SharedPoster.blitCover(graphics, thumb, thumbX, thumbY, thumbWidth, thumbHeight);
		} else if (thumb.state() == PosterCache.State.LOADING) {
			SharedPoster.blitPlaceholder(graphics, PosterCache.PLACEHOLDER, thumbX, thumbY, thumbWidth, thumbHeight);
			graphics.drawCenteredString(font, Component.translatable("gui.pixelreel.menu.loading_art"), x + CARD_WIDTH / 2, thumbY + thumbHeight / 2 - 4, COLOR_TEXT_FAINT);
		} else {
			SharedPoster.blitPlaceholder(graphics, PosterCache.PLACEHOLDER, thumbX, thumbY, thumbWidth, thumbHeight);
			graphics.drawCenteredString(
				font,
				Component.literal(font.plainSubstrByWidth(this.entry.channel().name(), thumbWidth - 4)),
				x + CARD_WIDTH / 2,
				thumbY + thumbHeight / 2 - 4,
				COLOR_TEXT
			);
		}
		if (this.selected) {
			graphics.drawString(font, Component.translatable("gui.pixelreel.menu.watching"), thumbX + 2, thumbY + 2, COLOR_SELECTED);
		}

		long nowEpoch = Instant.now().getEpochSecond();
		float progress = this.entry.guide().progressAt(nowEpoch);
		int barY = thumbY + thumbHeight + 1;
		if (progress >= 0.0F) {
			graphics.fill(thumbX, barY, thumbX + thumbWidth, barY + 2, COLOR_PROGRESS_BACK);
			graphics.fill(thumbX, barY, thumbX + (int)(thumbWidth * progress), barY + 2, COLOR_PROGRESS);
		}

		int textX = x + 4;
		int textY = y + POSTER_HEIGHT + 4;
		String nameLine = this.entry.channel().number() + "  " + this.entry.channel().name();
		graphics.drawString(font, Component.literal(font.plainSubstrByWidth(nameLine, CARD_WIDTH - 8)), textX, textY, COLOR_TEXT);
		if (!this.entry.channel().isPlayable()) {
			graphics.drawString(font, Component.translatable("gui.pixelreel.menu.channel_offline"), textX, textY + 11, COLOR_ERROR);
		} else if (this.entry.guide().hasNow()) {
			graphics.drawString(
				font,
				Component.literal(font.plainSubstrByWidth("\u25B6 " + this.entry.guide().nowTitle(), CARD_WIDTH - 8)),
				textX, textY + 11, COLOR_TEXT_DIM
			);
		} else {
			graphics.drawString(font, Component.translatable("gui.pixelreel.menu.no_guide"), textX, textY + 11, COLOR_TEXT_FAINT);
		}
		if (this.entry.guide().hasNext()) {
			graphics.drawString(
				font,
				Component.literal(font.plainSubstrByWidth(
					Component.translatable("gui.pixelreel.menu.next_prefix").getString() + " " + this.entry.guide().nextTitle(), CARD_WIDTH - 8
				)),
				textX, textY + 22, COLOR_TEXT_FAINT
			);
		}

		if (hovered) {
			graphics.renderComponentTooltip(font, List.of(
				Component.literal(this.entry.channel().number() + " - " + this.entry.channel().name()).withStyle(ChatFormatting.WHITE),
				Component.translatable("gui.pixelreel.tooltip.click").withStyle(ChatFormatting.BLUE)
			), mouseX, mouseY);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
