package com.pixelreel.client.gui.ondemand;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.GuiColors;
import com.pixelreel.client.gui.MediaSourceScreen;
import com.pixelreel.client.texture.PosterCache;
import com.pixelreel.jellyfin.JellyfinItemKind;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.jellyfin.JellyfinStatus;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** this handles pagination for content if you have more than 48 items. i know 48 it just looked better ok*/
public class OnDemandBrowseScreen extends Screen {
	private static final int HEADER_HEIGHT = 52;
	private static final int FOOTER_HEIGHT = 48;
	private static final int CARD_GAP = 8;
	private static final int SEARCH_DEBOUNCE_TICKS = 12;
		private final DisplayBlockEntity display;
	private final ModNetworkPayloads.BrowseKind kind;
	private final OnDemandProvider provider;
	private final List<OnDemandPosterCard> cards = new ArrayList<>();
	private final List<Button> footerButtons = new ArrayList<>();
	private EditBox searchBox;
	private int columns = 1;
	private int gridLeft;
	private double scroll;
	private int page;
	private boolean requested;
	private boolean loadingPage;
	private int searchDebounce;
	private String lastRequestedSearch = "";

	public OnDemandBrowseScreen(DisplayBlockEntity display, ModNetworkPayloads.BrowseKind kind, OnDemandProvider provider) {
		super(Component.translatable(
			kind == ModNetworkPayloads.BrowseKind.MOVIES
				? "gui.pixelreel.jellyfin.movies.title"
				: "gui.pixelreel.jellyfin.shows.title",
			provider.displayName()
		));
		this.display = display;
		this.kind = kind;
		this.provider = provider;
	}

	public OnDemandProvider provider() {
		return this.provider;
	}

	@Override
	protected void init() {
		this.cards.clear();
		this.footerButtons.clear();
		int centre = this.width / 2;
		this.searchBox = new EditBox(this.font, centre - 120, 28, 180, 18, Component.translatable("gui.pixelreel.jellyfin.search"));
		this.searchBox.setMaxLength(64);
		this.searchBox.setResponder(value -> {
			String next = value == null ? "" : value;
			if (next.equals(this.lastRequestedSearch) && this.searchDebounce == 0) {
				return;
			}
			this.page = 0;
			this.scroll = 0;
			this.searchDebounce = SEARCH_DEBOUNCE_TICKS;
		});
		this.addRenderableWidget(this.searchBox);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.menu.refresh"), button -> this.request(true))
				.bounds(centre + 66, 27, 70, 20).build()
		);
		this.reflow();
		this.request(false);
		this.rebuildFooter();
	}

	private void rebuildFooter() {
		for (Button button : this.footerButtons) {
			this.removeWidget(button);
		}
		this.footerButtons.clear();
		int centre = this.width / 2;
		int y = this.height - 28;
		this.footerButtons.add(this.addRenderableWidget(
			Button.builder(Component.literal("<"), button -> {
				if (this.page > 0) {
					this.page--;
					this.scroll = 0;
					this.request(false);
				}
			}).bounds(centre - 90, y, 20, 20).build()
		));
		this.footerButtons.add(this.addRenderableWidget(
			Button.builder(Component.literal(">"), button -> {
				int totalPages = Math.max(1, (ClientMediaCache.INSTANCE.browseTotal() + 47) / 48);
				if (this.page + 1 < totalPages) {
					this.page++;
					this.scroll = 0;
					this.request(false);
				}
			}).bounds(centre + 70, y, 20, 20).build()
		));
		this.footerButtons.add(this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new MediaSourceScreen(this.display));
				}
			}).bounds(centre - 40, y, 80, 20).build()
		));
	}

	private void request(boolean force) {
		this.requested = true;
		this.loadingPage = true;
		this.searchDebounce = 0;
		this.clearCards();
		ClientMediaCache.INSTANCE.beginBrowse();
		String search = this.searchBox == null ? "" : this.searchBox.getValue();
		this.lastRequestedSearch = search == null ? "" : search;
		// Clear previous page posters only when the new page payload arrives.
		ClientNetworking.requestJellyfinBrowse(this.provider, this.kind, this.lastRequestedSearch, this.page, force);
	}

	public void onBrowseUpdated() {
		if (ClientMediaCache.INSTANCE.browseKind() != this.kind
			|| ClientMediaCache.INSTANCE.browseProvider() != this.provider) {
			return;
		}
		this.loadingPage = false;
		this.page = ClientMediaCache.INSTANCE.browsePage();
		PosterCache.INSTANCE.clearPage();
		this.rebuildCards();
		this.clampScroll();
		this.rebuildFooter();
	}

	private void clearCards() {
		for (OnDemandPosterCard card : this.cards) {
			this.removeWidget(card);
		}
		this.cards.clear();
	}

	private void reflow() {
		int available = this.width - 24;
		this.columns = Math.max(1, (available + CARD_GAP) / (OnDemandPosterCard.CARD_WIDTH + CARD_GAP));
		int gridWidth = this.columns * (OnDemandPosterCard.CARD_WIDTH + CARD_GAP) - CARD_GAP;
		this.gridLeft = (this.width - gridWidth) / 2;
	}

	private void rebuildCards() {
		this.clearCards();
		List<JellyfinItemSummary> items = ClientMediaCache.INSTANCE.browseItems();
		for (int i = 0; i < items.size(); i++) {
			JellyfinItemSummary item = items.get(i);
			int cardX = this.gridLeft + (i % this.columns) * (OnDemandPosterCard.CARD_WIDTH + CARD_GAP);
			int cardY = HEADER_HEIGHT + (i / this.columns) * (OnDemandPosterCard.CARD_HEIGHT + CARD_GAP) - (int)this.scroll;
			OnDemandPosterCard card = new OnDemandPosterCard(cardX, cardY, item, this::openItem);
			this.cards.add(card);
			this.addRenderableWidget(card);
			// Prefetch this page immediately so off-screen cards still warm the cache.
			PosterCache.INSTANCE.getByUrl(item.id(), item.imageUrl());
		}
		this.applyCardVisibility();
	}

	private void openItem(JellyfinItemSummary item) {
		if (this.minecraft == null) {
			return;
		}
		if (item.kind() == JellyfinItemKind.SERIES || this.kind == ModNetworkPayloads.BrowseKind.SERIES) {
			this.minecraft.setScreen(new OnDemandSeriesScreen(this.display, item, this.provider, this));
		} else {
			this.minecraft.setScreen(new OnDemandDetailScreen(this.display, item, this.provider, this));
		}
	}

	private void applyCardVisibility() {
		int top = HEADER_HEIGHT;
		int bottom = this.height - FOOTER_HEIGHT;
		List<JellyfinItemSummary> items = ClientMediaCache.INSTANCE.browseItems();
		for (int i = 0; i < this.cards.size(); i++) {
			OnDemandPosterCard card = this.cards.get(i);
			int cardY = HEADER_HEIGHT + (i / this.columns) * (OnDemandPosterCard.CARD_HEIGHT + CARD_GAP) - (int)this.scroll;
			card.setX(this.gridLeft + (i % this.columns) * (OnDemandPosterCard.CARD_WIDTH + CARD_GAP));
			card.setY(cardY);
			boolean visible = cardY + OnDemandPosterCard.CARD_HEIGHT >= top && cardY + 8 <= bottom;
			card.visible = visible;
			card.active = visible;
			if (i < items.size()) {
				card.update(items.get(i));
			}
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, GuiColors.TEXT);
		JellyfinStatus status = ClientMediaCache.INSTANCE.browseStatus();
		Component line;
		int color = GuiColors.TEXT_DIM;
		if (!this.requested || this.loadingPage) {
			line = Component.translatable("gui.pixelreel.menu.loading");
		} else if (!status.configured()) {
			line = Component.translatable("gui.pixelreel.source.ondemand_not_configured", this.provider.displayName());
			color = GuiColors.ERROR;
		} else if (!status.authenticated()) {
			line = Component.translatable("gui.pixelreel.source.ondemand_error", this.provider.displayName(), status.detail());
			color = GuiColors.ERROR;
		} else if (ClientMediaCache.INSTANCE.browseItems().isEmpty()) {
			line = Component.translatable("gui.pixelreel.jellyfin.empty");
			color = GuiColors.ERROR;
		} else {
			line = Component.translatable(
				"gui.pixelreel.jellyfin.page",
				ClientMediaCache.INSTANCE.browseTotal(),
				this.page + 1,
				Math.max(1, (ClientMediaCache.INSTANCE.browseTotal() + 47) / 48)
			);
		}
		// Opaque footer so cards never cover Back / page controls.
		graphics.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height, GuiColors.FOOTER);
		graphics.drawCenteredString(this.font, line, this.width / 2, this.height - FOOTER_HEIGHT + 6, color);
		super.render(graphics, mouseX, mouseY, partialTicks);
	}

	private int contentHeight() {
		int size = ClientMediaCache.INSTANCE.browseItems().size();
		int rows = (size + this.columns - 1) / this.columns;
		return Math.max(0, rows * (OnDemandPosterCard.CARD_HEIGHT + CARD_GAP) - CARD_GAP);
	}

	private void clampScroll() {
		int viewHeight = this.height - FOOTER_HEIGHT - HEADER_HEIGHT;
		this.scroll = Math.clamp(this.scroll, 0.0, Math.max(0, this.contentHeight() - viewHeight));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalDelta, verticalDelta)) {
			return true;
		}
		this.scroll -= verticalDelta * 24.0;
		this.clampScroll();
		this.applyCardVisibility();
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.searchDebounce > 0 && --this.searchDebounce == 0) {
			this.request(false);
		}
		if (this.display.isRemoved()) {
			this.onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
