package com.pixelreel.client.gui;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.gui.ondemand.OnDemandBrowseScreen;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** pick either Jellyfin / Emby / Plex. */
public class ProviderPickerScreen extends Screen {
		private final DisplayBlockEntity display;
	private final ModNetworkPayloads.BrowseKind kind;
	private final List<OnDemandProvider> providers;

	public ProviderPickerScreen(DisplayBlockEntity display, ModNetworkPayloads.BrowseKind kind, List<OnDemandProvider> providers) {
		super(Component.translatable(
			kind == ModNetworkPayloads.BrowseKind.MOVIES
				? "gui.pixelreel.provider.movies.title"
				: "gui.pixelreel.provider.shows.title"
		));
		this.display = display;
		this.kind = kind;
		this.providers = List.copyOf(providers);
	}

	public static void open(DisplayBlockEntity display, ModNetworkPayloads.BrowseKind kind) {
		Minecraft minecraft = Minecraft.getInstance();
		List<OnDemandProvider> providers = ClientMediaCache.INSTANCE.features().providersFor(kind);
		if (providers.isEmpty()) {
			return;
		}
		if (providers.size() == 1) {
			minecraft.setScreen(new OnDemandBrowseScreen(display, kind, providers.get(0)));
			return;
		}
		minecraft.setScreen(new ProviderPickerScreen(display, kind, providers));
	}

	@Override
	protected void init() {
		int centreX = this.width / 2;
		int y = this.height / 2 - (this.providers.size() * 28) / 2;
		for (OnDemandProvider provider : this.providers) {
			this.addRenderableWidget(
				Button.builder(Component.literal(provider.displayName()), button -> {
					if (this.minecraft != null) {
						this.minecraft.setScreen(new OnDemandBrowseScreen(this.display, this.kind, provider));
					}
				}).bounds(centreX - 100, y, 200, 24).build()
			);
			y += 28;
		}
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new MediaSourceScreen(this.display));
				}
			}).bounds(centreX - 40, this.height / 2 + 80, 80, 20).build()
		);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 70, GuiColors.TEXT);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("gui.pixelreel.provider.subtitle"),
			this.width / 2,
			this.height / 2 - 56,
			GuiColors.TEXT_DIM
		);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
