package com.pixelreel.client.gui.ondemand;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.GuiColors;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** pagination for seasons of a tvshow  */
public class OnDemandSeriesScreen extends Screen {
	private static final int FOOTER_HEIGHT = 36;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 4;
	private static final int OVERVIEW_LINES = 3;

	private final DisplayBlockEntity display;
	private JellyfinItemSummary series;
	private final OnDemandProvider provider;
	private final @Nullable Screen parent;
	private final List<Button> seasonButtons = new ArrayList<>();
	private double scroll;
	private boolean requestedSeasons;

	public OnDemandSeriesScreen(
		DisplayBlockEntity display,
		JellyfinItemSummary series,
		OnDemandProvider provider,
		@Nullable Screen parent
	) {
		super(Component.literal(series.title()));
		this.display = display;
		this.series = series;
		this.provider = provider;
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.seasonButtons.clear();
		this.scroll = 0.0;
		this.requestedSeasons = false;
		// Browse rows strip overview — fetch full series first, then seasons.
		this.applyFetchedSeries();
		if (this.series.overview().isEmpty()) {
			ClientNetworking.requestJellyfinChildren(
				this.provider,
				ModNetworkPayloads.ChildrenKind.ITEM,
				this.series.id(),
				false
			);
		} else {
			this.requestSeasons();
		}
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(this.parent);
				}
			}).bounds(this.width / 2 - 40, this.height - 28, 80, 20).build()
		);
		this.rebuildSeasons();
	}

	public void onChildrenUpdated() {
		if (this.applyFetchedSeries() && !this.requestedSeasons) {
			this.requestSeasons();
			return;
		}
		this.rebuildSeasons();
		this.clampScroll();
		this.applyVisibility();
	}

	private boolean applyFetchedSeries() {
		List<JellyfinItemSummary> items = ClientMediaCache.INSTANCE.children(
			this.provider,
			ModNetworkPayloads.ChildrenKind.ITEM,
			this.series.id()
		);
		if (items.isEmpty()) {
			return false;
		}
		this.series = items.getFirst();
		return true;
	}

	private void requestSeasons() {
		this.requestedSeasons = true;
		ClientNetworking.requestJellyfinChildren(
			this.provider,
			ModNetworkPayloads.ChildrenKind.SEASONS,
			this.series.id(),
			false
		);
	}

	private int headerHeight() {
		int base = 40;
		if (this.series.overview().isEmpty()) {
			return base;
		}
		return base + OVERVIEW_LINES * 10 + 4;
	}

	private void rebuildSeasons() {
		for (Button button : this.seasonButtons) {
			this.removeWidget(button);
		}
		this.seasonButtons.clear();
		List<JellyfinItemSummary> seasons = ClientMediaCache.INSTANCE.children(
			this.provider, ModNetworkPayloads.ChildrenKind.SEASONS, this.series.id()
		);
		int y = this.headerHeight();
		for (JellyfinItemSummary season : seasons) {
			JellyfinItemSummary seasonRef = season;
			Button button = Button.builder(
				Component.literal(seasonRef.seasonLabel()),
				b -> {
					if (this.minecraft != null) {
						this.minecraft.setScreen(
							new OnDemandEpisodeScreen(this.display, this.series, seasonRef, this.provider, this)
						);
					}
				}
			).bounds(this.width / 2 - 120, y - (int)this.scroll, 240, ROW_HEIGHT).build();
			this.seasonButtons.add(button);
			this.addRenderableWidget(button);
			y += ROW_HEIGHT + ROW_GAP;
		}
		this.applyVisibility();
	}

	private void applyVisibility() {
		int y = this.headerHeight();
		int top = this.headerHeight() - 2;
		int bottom = this.height - FOOTER_HEIGHT;
		for (Button button : this.seasonButtons) {
			int rowY = y - (int)this.scroll;
			button.setY(rowY);
			boolean visible = rowY + ROW_HEIGHT >= top && rowY <= bottom;
			button.visible = visible;
			button.active = visible;
			y += ROW_HEIGHT + ROW_GAP;
		}
	}

	private int contentHeight() {
		int size = this.seasonButtons.size();
		if (size <= 0) {
			return 0;
		}
		return size * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
	}

	private void clampScroll() {
		int viewHeight = this.height - FOOTER_HEIGHT - this.headerHeight();
		this.scroll = Math.min(Math.max(this.scroll, 0.0), Math.max(0, this.contentHeight() - viewHeight));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, GuiColors.TEXT);
		graphics.drawCenteredString(this.font, Component.translatable("gui.pixelreel.jellyfin.seasons"), this.width / 2, 26, GuiColors.TEXT_DIM);
		if (!this.series.overview().isEmpty()) {
			int lineY = 38;
			int maxWidth = Math.max(80, this.width - 48);
			int start = 0;
			String overview = this.series.overview();
			for (int i = 0; i < OVERVIEW_LINES && start < overview.length(); i++) {
				String chunk = this.font.plainSubstrByWidth(overview.substring(start), maxWidth);
				if (chunk.isEmpty()) {
					break;
				}
				graphics.drawCenteredString(this.font, Component.literal(chunk), this.width / 2, lineY, GuiColors.TEXT_DIM);
				lineY += 10;
				start += chunk.length();
				while (start < overview.length() && Character.isWhitespace(overview.charAt(start))) {
					start++;
				}
			}
		}
		if (this.seasonButtons.isEmpty()) {
			graphics.drawCenteredString(
				this.font,
				Component.translatable("gui.pixelreel.menu.loading"),
				this.width / 2,
				this.headerHeight() + 20,
				GuiColors.TEXT_DIM
			);
		}
		super.render(graphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalDelta, verticalDelta)) {
			return true;
		}
		this.scroll -= verticalDelta * 24.0;
		this.clampScroll();
		this.applyVisibility();
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
