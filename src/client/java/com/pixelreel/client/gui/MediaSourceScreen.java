package com.pixelreel.client.gui;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.emby.EmbyConfigScreen;
import com.pixelreel.client.gui.jellyfin.JellyfinConfigScreen;
import com.pixelreel.client.gui.plex.PlexConfigScreen;
import com.pixelreel.client.gui.tunarr.ChannelMenuScreen;
import com.pixelreel.client.gui.tunarr.TunarrConfigScreen;
import com.pixelreel.jellyfin.JellyfinStatus;
import com.pixelreel.networking.ModNetworkPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** menu: you can choose Live TV, Movies or TV Shows. */
public class MediaSourceScreen extends Screen {
	private static final int PANEL_WIDTH = 240;

	private final DisplayBlockEntity display;
	private Button liveButton;
	private Button moviesButton;
	private Button showsButton;
	private Button tunarrConfigButton;
	private Button jellyfinConfigButton;
	private Button embyConfigButton;
	private Button plexConfigButton;
	private Button refreshButton;

	public MediaSourceScreen(DisplayBlockEntity display) {
		super(Component.translatable("gui.pixelreel.source.title"));
		this.display = display;
	}

	@Override
	protected void init() {
		ClientNetworking.requestMediaFeatures();
		int centreX = this.width / 2;
		boolean showPlayback = this.display.isOnDemand() && this.display.hasChannel();
		int y = showPlayback ? 48 : 40;
		int gap = 3;
		int mainH = 20;
		int smallH = 18;

		this.liveButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.live"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new ChannelMenuScreen(this.display));
				}
			}).bounds(centreX - PANEL_WIDTH / 2, y, PANEL_WIDTH, mainH).build()
		);
		y += mainH + gap;
		this.moviesButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.movies"), button ->
				ProviderPickerScreen.open(this.display, ModNetworkPayloads.BrowseKind.MOVIES)
			).bounds(centreX - PANEL_WIDTH / 2, y, PANEL_WIDTH, mainH).build()
		);
		y += mainH + gap;
		this.showsButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.shows"), button ->
				ProviderPickerScreen.open(this.display, ModNetworkPayloads.BrowseKind.SERIES)
			).bounds(centreX - PANEL_WIDTH / 2, y, PANEL_WIDTH, mainH).build()
		);
		y += mainH + gap + 2;

		int half = (PANEL_WIDTH - gap) / 2;
		this.tunarrConfigButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.configure_tunarr"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new TunarrConfigScreen(this));
				}
			}).bounds(centreX - PANEL_WIDTH / 2, y, half, smallH).build()
		);
		this.jellyfinConfigButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.configure_jellyfin"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new JellyfinConfigScreen(this));
				}
			}).bounds(centreX - PANEL_WIDTH / 2 + half + gap, y, half, smallH).build()
		);
		y += smallH + gap;
		this.embyConfigButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.configure_emby"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new EmbyConfigScreen(this));
				}
			}).bounds(centreX - PANEL_WIDTH / 2, y, half, smallH).build()
		);
		this.plexConfigButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.configure_plex"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new PlexConfigScreen(this));
				}
			}).bounds(centreX - PANEL_WIDTH / 2 + half + gap, y, half, smallH).build()
		);
		y += smallH + gap + 2;

		this.refreshButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.source.refresh"), button -> ClientNetworking.refreshJellyfinLibrary())
				.bounds(centreX - PANEL_WIDTH / 2, y, PANEL_WIDTH, smallH).build()
		);
		y += smallH + gap;

		if (showPlayback) {
			this.addRenderableWidget(
				Button.builder(Component.translatable("gui.pixelreel.playback.open"), button -> {
					if (this.minecraft != null) {
						this.minecraft.setScreen(new PlaybackControlScreen(this.display));
					}
				}).bounds(centreX - PANEL_WIDTH / 2, y, PANEL_WIDTH, smallH).build()
			);
			y += smallH + gap;
		}

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.menu.close"), button -> this.onClose())
				.bounds(centreX - 40, y, 80, smallH).build()
		);

		int bottom = y + smallH + 6;
		if (bottom > this.height - 2) {
			int shift = bottom - (this.height - 2);
			for (var child : this.children()) {
				if (child instanceof AbstractWidget widget) {
					widget.setY(Math.max(38, widget.getY() - shift));
				}
			}
		}

		this.applyFeatures();
	}

	public void onFeaturesUpdated() {
		this.applyFeatures();
	}

	private void applyFeatures() {
		ClientMediaCache.Features features = ClientMediaCache.INSTANCE.features();
		if (this.liveButton != null) {
			this.liveButton.active = features.canPlayTunarr();
		}
		if (this.moviesButton != null) {
			this.moviesButton.active = features.canPlayMovies();
		}
		if (this.showsButton != null) {
			this.showsButton.active = features.canPlayShows();
		}
		if (this.tunarrConfigButton != null) {
			this.tunarrConfigButton.active = features.canConfigureTunarr();
			this.tunarrConfigButton.visible = features.canConfigureTunarr();
		}
		if (this.jellyfinConfigButton != null) {
			this.jellyfinConfigButton.active = features.canConfigureJellyfin();
			this.jellyfinConfigButton.visible = features.canConfigureJellyfin();
		}
		if (this.embyConfigButton != null) {
			this.embyConfigButton.active = features.canConfigureEmby();
			this.embyConfigButton.visible = features.canConfigureEmby();
		}
		if (this.plexConfigButton != null) {
			this.plexConfigButton.active = features.canConfigurePlex();
			this.plexConfigButton.visible = features.canConfigurePlex();
		}
		if (this.refreshButton != null) {
			this.refreshButton.active = features.canRefreshLibrary();
			this.refreshButton.visible = features.canRefreshLibrary();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, GuiColors.TEXT);
		ClientMediaCache.Features features = ClientMediaCache.INSTANCE.features();
		Component status;
		int color = GuiColors.TEXT_DIM;
		if (!features.canBrowse()) {
			status = Component.translatable("message.pixelreel.no_permission");
			color = GuiColors.ERROR;
		} else {
			status = Component.literal(buildStatusLine(features));
			if (hasBackendError(features)) {
				color = GuiColors.ERROR;
			}
		}
		graphics.drawCenteredString(this.font, status, this.width / 2, 22, color);
		if (this.display.isOnDemand() && this.display.hasChannel()) {
			graphics.drawCenteredString(
				this.font,
				Component.translatable("gui.pixelreel.source.now_playing", truncate(this.display.getMediaTitle(), 40)),
				this.width / 2,
				34,
				GuiColors.TEXT_DIM
			);
		}
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

	private static boolean hasBackendError(ClientMediaCache.Features features) {
		return errorIfConfigured(features.tunarrStatus().configured(), features.tunarrStatus().reachable())
			|| errorIfConfigured(features.jellyfinStatus().configured(), features.jellyfinStatus().authenticated())
			|| errorIfConfigured(features.embyStatus().configured(), features.embyStatus().authenticated())
			|| errorIfConfigured(features.plexStatus().configured(), features.plexStatus().authenticated());
	}

	private static boolean errorIfConfigured(boolean configured, boolean ok) {
		return configured && !ok;
	}

	private static String buildStatusLine(ClientMediaCache.Features features) {
		StringBuilder sb = new StringBuilder();
		appendTunarr(sb, features);
		appendOnDemand(sb, "JF", features.jellyfinStatus());
		appendOnDemand(sb, "Emby", features.embyStatus());
		appendOnDemand(sb, "Plex", features.plexStatus());
		if (sb.isEmpty()) {
			return Component.translatable("gui.pixelreel.source.tunarr_not_configured").getString();
		}
		return sb.toString();
	}

	private static void appendTunarr(StringBuilder sb, ClientMediaCache.Features features) {
		if (!features.tunarrStatus().configured()) {
			return;
		}
		if (!sb.isEmpty()) {
			sb.append(" · ");
		}
		if (features.tunarrStatus().reachable()) {
			sb.append("TV ").append(features.tunarrStatus().channelCount());
		} else {
			sb.append("TV err");
		}
	}

	private static void appendOnDemand(StringBuilder sb, String name, JellyfinStatus status) {
		if (!status.configured()) {
			return;
		}
		if (!sb.isEmpty()) {
			sb.append(" · ");
		}
		if (status.authenticated()) {
			sb.append(name).append(' ').append(status.movieCount()).append('/').append(status.seriesCount());
		} else {
			sb.append(name).append(" err");
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.display.isRemoved()) {
			this.onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public DisplayBlockEntity display() {
		return this.display;
	}
}
