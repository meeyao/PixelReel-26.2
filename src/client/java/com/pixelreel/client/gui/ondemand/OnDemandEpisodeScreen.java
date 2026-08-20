package com.pixelreel.client.gui.ondemand;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.GuiColors;
import com.pixelreel.client.gui.shared.SharedPoster;
import com.pixelreel.client.gui.shared.TimeFormat;
import com.pixelreel.client.texture.PosterCache;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Episode list */
public class OnDemandEpisodeScreen extends Screen {
	private static final int ROW_HEIGHT = 54;

	private final DisplayBlockEntity display;
	private final JellyfinItemSummary series;
	private final JellyfinItemSummary season;
	private final OnDemandProvider provider;
	private final @Nullable Screen parent;
	private final List<EpisodeRow> rows = new ArrayList<>();
	private double scroll;

	public OnDemandEpisodeScreen(
		DisplayBlockEntity display,
		JellyfinItemSummary series,
		JellyfinItemSummary season,
		OnDemandProvider provider,
		@Nullable Screen parent
	) {
		super(Component.literal(series.title() + " - " + season.seasonLabel()));
		this.display = display;
		this.series = series;
		this.season = season;
		this.provider = provider;
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.rows.clear();
		ClientNetworking.requestJellyfinChildren(this.provider, ModNetworkPayloads.ChildrenKind.EPISODES, this.season.id(), false);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(this.parent);
				}
			}).bounds(this.width / 2 - 40, this.height - 28, 80, 20).build()
		);
		this.rebuildRows();
	}

	public void onChildrenUpdated() {
		this.rebuildRows();
	}

	private void rebuildRows() {
		for (EpisodeRow row : this.rows) {
			this.removeWidget(row);
		}
		this.rows.clear();
		List<JellyfinItemSummary> episodes = ClientMediaCache.INSTANCE.children(
			this.provider, ModNetworkPayloads.ChildrenKind.EPISODES, this.season.id()
		);
		int seasonNumber = this.season.resolvedSeasonNumber();
		int y = 36;
		for (JellyfinItemSummary episode : episodes) {
			EpisodeRow row = new EpisodeRow(
				20,
				y - (int)this.scroll,
				this.width - 40,
				episode,
				seasonNumber,
				this::playEpisode
			);
			this.rows.add(row);
			this.addRenderableWidget(row);
			y += ROW_HEIGHT + 4;
		}
		this.applyVisibility();
	}

	private void playEpisode(JellyfinItemSummary episode) {
		long start = episode.hasResume() ? episode.resumePositionMs() : 0L;
		ClientNetworking.playJellyfin(this.provider, this.display.getBlockPos(), episode.id(), start);
		this.onClose();
	}

	private void applyVisibility() {
		int y = 36;
		for (EpisodeRow row : this.rows) {
			int rowY = y - (int)this.scroll;
			row.setY(rowY);
			boolean visible = rowY + ROW_HEIGHT >= 32 && rowY <= this.height - 36;
			row.visible = visible;
			row.active = visible;
			y += ROW_HEIGHT + 4;
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, GuiColors.TEXT);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("gui.pixelreel.jellyfin.episodes_of", this.series.title()),
			this.width / 2,
			22,
			GuiColors.TEXT_DIM
		);
		super.render(graphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (super.mouseScrolled(mouseX, mouseY, delta)) {
			return true;
		}
		int content = this.rows.size() * (ROW_HEIGHT + 4);
		this.scroll = Math.min(Math.max(this.scroll - delta * 24.0, 0.0), Math.max(0, content - (this.height - 70)));
		this.applyVisibility();
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static final class EpisodeRow extends AbstractWidget {
		private final JellyfinItemSummary episode;
		private final int seasonNumber;
		private final java.util.function.Consumer<JellyfinItemSummary> onPlay;

		private EpisodeRow(
			int x,
			int y,
			int width,
			JellyfinItemSummary episode,
			int seasonNumber,
			java.util.function.Consumer<JellyfinItemSummary> onPlay
		) {
			super(x, y, width, ROW_HEIGHT, Component.literal(episode.title()));
			this.episode = episode;
			this.seasonNumber = seasonNumber;
			this.onPlay = onPlay;
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			this.onPlay.accept(this.episode);
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			int x = this.getX();
			int y = this.getY();
			graphics.fill(x, y, x + this.width, y + ROW_HEIGHT, this.isHovered() ? 0xE01C242C : 0xE0101418);
			int thumbW = 80;
			int thumbH = ROW_HEIGHT - 8;
			graphics.fill(x + 4, y + 4, x + 4 + thumbW, y + 4 + thumbH, 0xFF06080A);
			PosterCache.Poster thumb = PosterCache.INSTANCE.getByUrl(this.episode.id(), this.episode.imageUrl());
			if (thumb.state() == PosterCache.State.READY && thumb.texture() != null) {
				SharedPoster.blitCover(graphics, thumb, x + 4, y + 4, thumbW, thumbH);
			} else {
				SharedPoster.blitPlaceholder(graphics, PosterCache.PLACEHOLDER, x + 4, y + 4, thumbW, thumbH);
			}
			var font = net.minecraft.client.Minecraft.getInstance().font;
			int season = this.seasonNumber > 0 ? this.seasonNumber : this.episode.parentIndexNumber();
			String label = this.episode.episodeLabel(season);
			graphics.drawString(font, Component.literal(font.plainSubstrByWidth(label, this.width - thumbW - 20)), x + thumbW + 12, y + 8, GuiColors.TEXT);
			String meta = TimeFormat.format(this.episode.runtimeMs());
			if (this.episode.played()) {
				meta += " · " + Component.translatable("gui.pixelreel.jellyfin.watched").getString();
			} else if (this.episode.hasResume()) {
				meta += " · " + Component.translatable("gui.pixelreel.jellyfin.resume_at", TimeFormat.format(this.episode.resumePositionMs())).getString();
			}
			graphics.drawString(font, Component.literal(meta), x + thumbW + 12, y + 20, GuiColors.TEXT_DIM);
			if (!this.episode.overview().isEmpty()) {
				graphics.drawString(
					font,
					Component.literal(font.plainSubstrByWidth(this.episode.overview(), this.width - thumbW - 20)),
					x + thumbW + 12,
					y + 34,
					0xFF6E7880
				);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
