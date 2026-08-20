package com.pixelreel.client.gui.tunarr;

import com.pixelreel.PixelReel;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.channels.LiveStatus;
import com.pixelreel.client.ClientChannelCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.GuiColors;
import com.pixelreel.client.gui.MediaSourceScreen;
import com.pixelreel.client.playback.PlaybackManager;
import com.pixelreel.networking.ScreenAction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/* The guide: a scroll poster grid of every channel  */
public class ChannelMenuScreen extends Screen {
	private static final int HEADER_HEIGHT = 42;
	private static final int FOOTER_HEIGHT = 42;
	private static final int CARD_GAP = 8;
	private static final int PREWARM_EXTRA = 3;
		private final DisplayBlockEntity display;
	private List<ChannelEntry> channels = List.of();
	private final List<ChannelCardWidget> cards = new ArrayList<>();
	private final List<Button> footerButtons = new ArrayList<>();
	private double scroll;
	private int columns = 1;
	private int gridLeft;
	private boolean requested;
	private Button powerButton;
	private Button volumeDownButton;
	private Button volumeUpButton;
	private String pendingChannelId = "";

	public ChannelMenuScreen(DisplayBlockEntity display) {
		super(Component.translatable("gui.pixelreel.menu.title"));
		this.display = display;
		this.pendingChannelId = display.getChannelId();
	}

	@Override
	protected void init() {
		this.cards.clear();
		this.footerButtons.clear();
		this.reflow();
		if (!ClientChannelCache.INSTANCE.hasFreshData() && !this.requested) {
			this.requested = true;
			ClientNetworking.requestChannels(false);
		}
		this.onChannelsUpdated();
	}

	private void rebuildFooter() {
		for (Button button : this.footerButtons) {
			this.removeWidget(button);
		}
		this.footerButtons.clear();
		int centre = this.width / 2;
		int y = this.height - 28;
		this.powerButton = this.addRenderableWidget(
			Button.builder(this.powerLabel(), button -> {
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.POWER_TOGGLE, 0.0F);
				button.setMessage(this.powerLabel(!this.display.isPowered()));
			}).bounds(centre - 190, y, 70, 20).build()
		);
		this.footerButtons.add(this.powerButton);
		this.footerButtons.add(this.addRenderableWidget(
			Button.builder(Component.literal("<"), button -> ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.CHANNEL_PREVIOUS, 0.0F))
				.bounds(centre - 114, y, 20, 20).build()
		));
		this.footerButtons.add(this.addRenderableWidget(
			Button.builder(Component.literal(">"), button -> ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.CHANNEL_NEXT, 0.0F))
				.bounds(centre - 90, y, 20, 20).build()
		));
		this.volumeDownButton = this.addRenderableWidget(
			Button.builder(Component.literal("-"), button ->
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.VOLUME_SET, Math.max(0.0F, this.display.getVolume() - 0.1F))
			).bounds(centre - 62, y, 20, 20).build()
		);
		this.footerButtons.add(this.volumeDownButton);
		this.volumeUpButton = this.addRenderableWidget(
			Button.builder(Component.literal("+"), button ->
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.VOLUME_SET, Math.min(1.0F, this.display.getVolume() + 0.1F))
			).bounds(centre + 2, y, 20, 20).build()
		);
		this.footerButtons.add(this.volumeUpButton);
		this.footerButtons.add(this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.menu.refresh"), button -> {
				this.requested = true;
				ClientNetworking.requestChannels(true);
			}).bounds(centre + 30, y, 70, 20).build()
		));
		this.footerButtons.add(this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new MediaSourceScreen(this.display));
				}
			}).bounds(centre + 106, y, 64, 20).build()
		));
	}

	public void tuneTo(ChannelEntry entry) {
		this.pendingChannelId = entry.channel().id();
		PixelReel.LOGGER.info("Tuning display at {} to channel {} ({})", this.display.getBlockPos().toShortString(), entry.channel().number(), entry.channel().name());
		ClientNetworking.sendTune(this.display.getBlockPos(), entry.channel().id());
		PlaybackManager.INSTANCE.prewarm(List.of(entry.channel().streamUrl()));
		this.refreshCardSelection();
	}

	private Component powerLabel() {
		return this.powerLabel(this.display.isPowered());
	}

	private Component powerLabel(boolean powered) {
		return Component.translatable(powered ? "gui.pixelreel.menu.power_off" : "gui.pixelreel.menu.power_on");
	}

	public void onChannelsUpdated() {
		this.channels = ClientChannelCache.INSTANCE.channels();
		this.reflow();
		this.rebuildCards();
		this.clampScroll();
		this.rebuildFooter();
		this.prewarmLikelyChannels();
	}

	private void reflow() {
		int available = this.width - 24;
		this.columns = Math.max(1, (available + CARD_GAP) / (ChannelCardWidget.CARD_WIDTH + CARD_GAP));
		int gridWidth = this.columns * (ChannelCardWidget.CARD_WIDTH + CARD_GAP) - CARD_GAP;
		this.gridLeft = (this.width - gridWidth) / 2;
	}

	private void rebuildCards() {
		for (ChannelCardWidget card : this.cards) {
			this.removeWidget(card);
		}
		this.cards.clear();
		String selectedId = this.selectedChannelId();
		for (int i = 0; i < this.channels.size(); i++) {
			ChannelEntry entry = this.channels.get(i);
			int cardX = this.gridLeft + (i % this.columns) * (ChannelCardWidget.CARD_WIDTH + CARD_GAP);
			int cardY = HEADER_HEIGHT + (i / this.columns) * (ChannelCardWidget.CARD_HEIGHT + CARD_GAP) - (int)this.scroll;
			ChannelCardWidget card = new ChannelCardWidget(this, cardX, cardY, entry, entry.channel().id().equals(selectedId));
			this.cards.add(card);
			this.addRenderableWidget(card);
		}
		this.applyCardVisibility();
	}

	private void refreshCardSelection() {
		String selectedId = this.selectedChannelId();
		for (int i = 0; i < this.cards.size(); i++) {
			ChannelEntry entry = this.channels.get(i);
			this.cards.get(i).update(entry, entry.channel().id().equals(selectedId));
		}
	}

	private String selectedChannelId() {
		if (!this.pendingChannelId.isEmpty()) {
			return this.pendingChannelId;
		}
		return this.display.getChannelId();
	}

	private void applyCardVisibility() {
		int top = HEADER_HEIGHT;
		int bottom = this.height - FOOTER_HEIGHT;
		for (int i = 0; i < this.cards.size(); i++) {
			ChannelCardWidget card = this.cards.get(i);
			int cardY = HEADER_HEIGHT + (i / this.columns) * (ChannelCardWidget.CARD_HEIGHT + CARD_GAP) - (int)this.scroll;
			card.setX(this.gridLeft + (i % this.columns) * (ChannelCardWidget.CARD_WIDTH + CARD_GAP));
			card.setY(cardY);
			boolean visible = cardY + ChannelCardWidget.CARD_HEIGHT >= top && cardY + 8 <= bottom;
			card.visible = visible;
			card.active = visible;
		}
	}

	private void prewarmLikelyChannels() {
		int size = this.channels.size();
		if (size == 0) {
			return;
		}
		int selected = 0;
		String selectedId = this.selectedChannelId();
		for (int i = 0; i < size; i++) {
			if (this.channels.get(i).channel().id().equals(selectedId)) {
				selected = i;
				break;
			}
		}
		LinkedHashSet<String> urls = new LinkedHashSet<>();
		for (int offset = 0; offset <= PREWARM_EXTRA && urls.size() < size; offset++) {
			urls.add(this.channels.get(Math.floorMod(selected + offset, size)).channel().streamUrl());
			urls.add(this.channels.get(Math.floorMod(selected - offset, size)).channel().streamUrl());
		}
		urls.remove("");
		PlaybackManager.INSTANCE.prewarm(List.copyOf(urls));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		this.renderHeader(graphics);
		graphics.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height, GuiColors.FOOTER);
		this.renderFooterLabels(graphics);
		super.render(graphics, mouseX, mouseY, partialTicks);
	}

	private void renderHeader(GuiGraphics graphics) {
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, GuiColors.TEXT);
		LiveStatus status = ClientChannelCache.INSTANCE.status();
		Component line;
		int color = GuiColors.TEXT_DIM;
		if (!this.requested && !ClientChannelCache.INSTANCE.hasFreshData()) {
			line = Component.translatable("gui.pixelreel.menu.loading");
		} else if (!status.configured()) {
			line = Component.translatable("gui.pixelreel.menu.not_configured");
			color = GuiColors.ERROR;
		} else if (!status.reachable()) {
			line = Component.translatable("gui.pixelreel.menu.offline", status.detail());
			color = GuiColors.ERROR;
		} else if (this.channels.isEmpty()) {
			line = Component.translatable("gui.pixelreel.menu.no_channels");
			color = GuiColors.ERROR;
		} else {
			line = Component.translatable(
				"gui.pixelreel.menu.subtitle",
				this.channels.size(),
				Component.translatable(this.display.type().translationKey())
			);
		}
		graphics.drawCenteredString(this.font, line, this.width / 2, 24, color);
	}

	private void renderFooterLabels(GuiGraphics graphics) {
		int centre = this.width / 2;
		int y = this.height - FOOTER_HEIGHT + 12;
		int volume = Math.round(this.display.getVolume() * 100.0F);
		graphics.drawCenteredString(this.font, Component.literal(volume + "%"), centre - 20, y, GuiColors.TEXT);
	}

	private int contentHeight() {
		int rows = (this.channels.size() + this.columns - 1) / this.columns;
		return Math.max(0, rows * (ChannelCardWidget.CARD_HEIGHT + CARD_GAP) - CARD_GAP);
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
		if (this.display.isRemoved()) {
			this.onClose();
			return;
		}
		if (!this.display.getChannelId().isEmpty()) {
			this.pendingChannelId = this.display.getChannelId();
		}
		this.refreshCardSelection();
		this.powerButton.setMessage(this.powerLabel());
		boolean powered = this.display.isPowered();
		this.volumeDownButton.active = powered;
		this.volumeUpButton.active = powered;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public @Nullable DisplayBlockEntity displayEntity() {
		return this.display;
	}
}
