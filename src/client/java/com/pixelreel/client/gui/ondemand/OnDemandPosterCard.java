package com.pixelreel.client.gui.ondemand;

import com.pixelreel.client.gui.shared.SharedPoster;
import com.pixelreel.client.texture.PosterCache;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** getting Posters card from selected provider like Jellyfin, Emby, Plex. */
public class OnDemandPosterCard extends AbstractWidget {
	static final int CARD_WIDTH = 78;
	static final int POSTER_HEIGHT = 110;
	static final int CARD_HEIGHT = POSTER_HEIGHT + 28;

	private static final int COLOR_CARD = 0xE0101418;
	private static final int COLOR_CARD_HOVER = 0xE01C242C;
	private static final int COLOR_POSTER_BACK = 0xFF06080A;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_TEXT_DIM = 0xFFB0B8C0;
	private static final int COLOR_TEXT_FAINT = 0xFF6E7880;
	private static final int COLOR_PROGRESS = 0xFF46C878;

	private JellyfinItemSummary item;
	private final Consumer<JellyfinItemSummary> onSelect;

	public OnDemandPosterCard(int x, int y, JellyfinItemSummary item, Consumer<JellyfinItemSummary> onSelect) {
		super(x, y, CARD_WIDTH, CARD_HEIGHT, Component.literal(item.title()));
		this.item = item;
		this.onSelect = onSelect;
	}

	public void update(JellyfinItemSummary item) {
		this.item = item;
		this.setMessage(Component.literal(item.title()));
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		this.onSelect.accept(this.item);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		int x = this.getX();
		int y = this.getY();
		boolean hovered = this.isHovered();
		graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, hovered ? COLOR_CARD_HOVER : COLOR_CARD);

		int thumbX = x + 3;
		int thumbY = y + 3;
		int thumbWidth = CARD_WIDTH - 6;
		int thumbHeight = POSTER_HEIGHT - 6;
		graphics.fill(thumbX, thumbY, thumbX + thumbWidth, thumbY + thumbHeight, COLOR_POSTER_BACK);

		var font = Minecraft.getInstance().font;
		PosterCache.Poster thumb = PosterCache.INSTANCE.getByUrl(this.item.id(), this.item.imageUrl());
		if (thumb.state() == PosterCache.State.READY && thumb.texture() != null) {
			SharedPoster.blitCover(graphics, thumb, thumbX, thumbY, thumbWidth, thumbHeight);
		} else if (thumb.state() == PosterCache.State.LOADING) {
			SharedPoster.blitPlaceholder(graphics, PosterCache.PLACEHOLDER, thumbX, thumbY, thumbWidth, thumbHeight);
			graphics.drawCenteredString(font, Component.translatable("gui.pixelreel.menu.loading_art"), x + CARD_WIDTH / 2, thumbY + thumbHeight / 2 - 4, COLOR_TEXT_FAINT);
		} else {
			SharedPoster.blitPlaceholder(graphics, PosterCache.PLACEHOLDER, thumbX, thumbY, thumbWidth, thumbHeight);
			graphics.drawCenteredString(
				font,
				Component.literal(font.plainSubstrByWidth(this.item.title(), thumbWidth - 4)),
				x + CARD_WIDTH / 2,
				thumbY + thumbHeight / 2 - 4,
				COLOR_TEXT
			);
		}

		if (this.item.hasResume()) {
			long runtime = Math.max(1L, this.item.runtimeMs());
			float progress = Math.min(Math.max(this.item.resumePositionMs() / (float)runtime, 0.0F), 1.0F);
			int barY = thumbY + thumbHeight - 3;
			graphics.fill(thumbX, barY, thumbX + thumbWidth, barY + 3, 0xFF2A3238);
			graphics.fill(thumbX, barY, thumbX + (int)(thumbWidth * progress), barY + 3, COLOR_PROGRESS);
		}

		graphics.drawString(
			font,
			Component.literal(font.plainSubstrByWidth(this.item.title(), CARD_WIDTH - 8)),
			x + 4,
			y + POSTER_HEIGHT + 6,
			COLOR_TEXT
		);
		if (this.item.productionYear() > 0) {
			graphics.drawString(font, Component.literal(String.valueOf(this.item.productionYear())), x + 4, y + POSTER_HEIGHT + 16, COLOR_TEXT_DIM);
		}

		if (hovered) {
			graphics.renderComponentTooltip(font, List.of(
				Component.literal(this.item.title()).withStyle(ChatFormatting.WHITE),
				Component.translatable("gui.pixelreel.tooltip.click_media").withStyle(ChatFormatting.BLUE)
			), mouseX, mouseY);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
