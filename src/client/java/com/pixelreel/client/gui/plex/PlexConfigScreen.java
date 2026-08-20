package com.pixelreel.client.gui.plex;

import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.GuiColors;
import com.pixelreel.client.gui.shared.LibraryToggleList;
import com.pixelreel.jellyfin.JellyfinLibrary;
import com.pixelreel.networking.ModNetworkPayloads;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Plex configuration */
public class PlexConfigScreen extends Screen {
	private final @Nullable Screen parent;
	private EditBox urlBox;
	private EditBox tokenBox;
	private boolean moviesEnabled = true;
	private boolean showsEnabled = true;
	private Button moviesButton;
	private Button showsButton;
	private final Set<String> selectedLibraries = new LinkedHashSet<>();
	private final List<Button> libraryButtons = new ArrayList<>();
	private List<JellyfinLibrary> availableLibraries = List.of();

	public PlexConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("gui.pixelreel.plex.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientNetworking.requestPlexConfig();
		int left = this.width / 2 - 140;
		this.urlBox = new EditBox(this.font, left, 40, 280, 18, Component.translatable("gui.pixelreel.plex.config.url"));
		this.urlBox.setMaxLength(256);
		this.urlBox.setHint(Component.literal("http://192.168.1.100:32400"));
		this.addRenderableWidget(this.urlBox);

		this.tokenBox = new EditBox(this.font, left, 72, 280, 18, Component.translatable("gui.pixelreel.plex.config.token"));
		this.tokenBox.setMaxLength(512);
		this.tokenBox.setHint(Component.translatable("gui.pixelreel.plex.config.token_hint"));
		this.addRenderableWidget(this.tokenBox);

		this.moviesButton = this.addRenderableWidget(
			Button.builder(this.moviesLabel(), button -> {
				this.moviesEnabled = !this.moviesEnabled;
				button.setMessage(this.moviesLabel());
			}).bounds(left, 104, 136, 20).build()
		);
		this.showsButton = this.addRenderableWidget(
			Button.builder(this.showsLabel(), button -> {
				this.showsEnabled = !this.showsEnabled;
				button.setMessage(this.showsLabel());
			}).bounds(left + 144, 104, 136, 20).build()
		);

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.plex.config.save"), button -> this.save())
				.bounds(this.width / 2 - 110, this.height - 28, 100, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(this.parent);
				}
			}).bounds(this.width / 2 + 10, this.height - 28, 100, 20).build()
		);
		this.applyConfigData();
	}

	public void onConfigUpdated() {
		this.applyConfigData();
	}

	private Component moviesLabel() {
		return Component.translatable(this.moviesEnabled ? "gui.pixelreel.plex.config.movies_on" : "gui.pixelreel.plex.config.movies_off");
	}

	private Component showsLabel() {
		return Component.translatable(this.showsEnabled ? "gui.pixelreel.plex.config.shows_on" : "gui.pixelreel.plex.config.shows_off");
	}

	private void applyConfigData() {
		ModNetworkPayloads.PlexConfigData data = ClientMediaCache.INSTANCE.plexConfigData();
		if (data == null || this.urlBox == null) {
			return;
		}
		this.urlBox.setValue(data.url());
		this.moviesEnabled = data.moviesEnabled();
		this.showsEnabled = data.tvShowsEnabled();
		this.moviesButton.setMessage(this.moviesLabel());
		this.showsButton.setMessage(this.showsLabel());
		this.selectedLibraries.clear();
		this.selectedLibraries.addAll(data.selectedLibraryKeys());
		this.availableLibraries = data.availableLibraries();
		this.rebuildLibraryButtons();
	}

	private void rebuildLibraryButtons() {
		LibraryToggleList.rebuild(
			this.libraryButtons,
			this::removeWidget,
			this::addRenderableWidget,
			this.availableLibraries,
			this.selectedLibraries,
			this.width / 2 - 140,
			132,
			this.height - 50,
			this::rebuildLibraryButtons
		);
	}

	private void save() {
		ClientNetworking.updatePlexConfig(
			this.urlBox.getValue().trim(),
			this.tokenBox.getValue().trim(),
			this.moviesEnabled,
			this.showsEnabled,
			List.copyOf(this.selectedLibraries)
		);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, GuiColors.TEXT);
		graphics.drawCenteredString(this.font, Component.translatable("gui.pixelreel.plex.config.subtitle"), this.width / 2, 24, GuiColors.TEXT_DIM);
		ModNetworkPayloads.PlexConfigData data = ClientMediaCache.INSTANCE.plexConfigData();
		if (data != null) {
			int color = data.status().authenticated() ? GuiColors.TEXT_DIM : GuiColors.ERROR;
			graphics.drawCenteredString(
				this.font,
				Component.literal(data.status().detail()),
				this.width / 2,
				this.height - 42,
				color
			);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
