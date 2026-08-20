package com.pixelreel.client.gui.jellyfin;

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

/** Jellyfin configuration */
public class JellyfinConfigScreen extends Screen {
	private final @Nullable Screen parent;
	private EditBox urlBox;
	private EditBox apiKeyBox;
	private EditBox userIdBox;
	private boolean moviesEnabled = true;
	private boolean showsEnabled = true;
	private boolean autoplayEnabled = true;
	private Button moviesButton;
	private Button showsButton;
	private Button autoplayButton;
	private final Set<String> selectedLibraries = new LinkedHashSet<>();
	private final List<Button> libraryButtons = new ArrayList<>();
	private List<JellyfinLibrary> availableLibraries = List.of();

	public JellyfinConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("gui.pixelreel.jellyfin.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientNetworking.requestJellyfinConfig();
		int left = this.width / 2 - 140;
		this.urlBox = new EditBox(this.font, left, 40, 280, 18, Component.translatable("gui.pixelreel.jellyfin.config.url"));
		this.urlBox.setMaxLength(256);
		this.urlBox.setHint(Component.literal("http://192.168.1.100:8096"));
		this.addRenderableWidget(this.urlBox);

		this.apiKeyBox = new EditBox(this.font, left, 72, 280, 18, Component.translatable("gui.pixelreel.jellyfin.config.api_key"));
		this.apiKeyBox.setMaxLength(512);
		this.apiKeyBox.setHint(Component.translatable("gui.pixelreel.jellyfin.config.api_key_hint"));
		this.addRenderableWidget(this.apiKeyBox);

		this.userIdBox = new EditBox(this.font, left, 104, 280, 18, Component.translatable("gui.pixelreel.jellyfin.config.user_id"));
		this.userIdBox.setMaxLength(128);
		this.userIdBox.setHint(Component.literal("Dashboard → Users → user id"));
		this.addRenderableWidget(this.userIdBox);

		this.moviesButton = this.addRenderableWidget(
			Button.builder(this.moviesLabel(), button -> {
				this.moviesEnabled = !this.moviesEnabled;
				button.setMessage(this.moviesLabel());
			}).bounds(left, 128, 136, 20).build()
		);
		this.showsButton = this.addRenderableWidget(
			Button.builder(this.showsLabel(), button -> {
				this.showsEnabled = !this.showsEnabled;
				button.setMessage(this.showsLabel());
			}).bounds(left + 144, 128, 136, 20).build()
		);
		this.autoplayButton = this.addRenderableWidget(
			Button.builder(this.autoplayLabel(), button -> {
				this.autoplayEnabled = !this.autoplayEnabled;
				button.setMessage(this.autoplayLabel());
			}).bounds(left, 152, 280, 20).build()
		);

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.config.save"), button -> this.save())
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
		return Component.translatable(this.moviesEnabled ? "gui.pixelreel.jellyfin.config.movies_on" : "gui.pixelreel.jellyfin.config.movies_off");
	}

	private Component showsLabel() {
		return Component.translatable(this.showsEnabled ? "gui.pixelreel.jellyfin.config.shows_on" : "gui.pixelreel.jellyfin.config.shows_off");
	}

	private Component autoplayLabel() {
		return Component.translatable(this.autoplayEnabled ? "gui.pixelreel.jellyfin.config.autoplay_on" : "gui.pixelreel.jellyfin.config.autoplay_off");
	}

	private void applyConfigData() {
		ModNetworkPayloads.JellyfinConfigData data = ClientMediaCache.INSTANCE.configData();
		if (data == null || this.urlBox == null) {
			return;
		}
		this.urlBox.setValue(data.url());
		this.userIdBox.setValue(data.userId());
		this.moviesEnabled = data.moviesEnabled();
		this.showsEnabled = data.tvShowsEnabled();
		this.autoplayEnabled = data.autoplayNextEpisode();
		this.moviesButton.setMessage(this.moviesLabel());
		this.showsButton.setMessage(this.showsLabel());
		this.autoplayButton.setMessage(this.autoplayLabel());
		this.selectedLibraries.clear();
		this.selectedLibraries.addAll(data.selectedLibraryIds());
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
			178,
			this.height - 50,
			this::rebuildLibraryButtons
		);
	}

	private void save() {
		ClientNetworking.updateJellyfinConfig(
			this.urlBox.getValue().trim(),
			this.apiKeyBox.getValue().trim(),
			this.userIdBox.getValue().trim(),
			this.moviesEnabled,
			this.showsEnabled,
			this.autoplayEnabled,
			List.copyOf(this.selectedLibraries)
		);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, GuiColors.TEXT);
		graphics.drawCenteredString(this.font, Component.translatable("gui.pixelreel.jellyfin.config.subtitle"), this.width / 2, 24, GuiColors.TEXT_DIM);
		ModNetworkPayloads.JellyfinConfigData data = ClientMediaCache.INSTANCE.configData();
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
