package com.pixelreel.client.gui.tunarr;

import com.pixelreel.channels.LiveStatus;
import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.GuiColors;
import com.pixelreel.networking.ModNetworkPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Tunarr configuration. */
public class TunarrConfigScreen extends Screen {
	private final @Nullable Screen parent;
	private EditBox m3uBox;
	private EditBox xmltvBox;

	public TunarrConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("gui.pixelreel.tunarr.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientNetworking.requestTunarrConfig();
		int left = this.width / 2 - 160;
		this.m3uBox = new EditBox(this.font, left, 56, 320, 20, Component.translatable("gui.pixelreel.tunarr.config.m3u"));
		this.m3uBox.setMaxLength(512);
		this.m3uBox.setHint(Component.literal("http://192.168.1.100:8000"));
		this.addRenderableWidget(this.m3uBox);

		this.xmltvBox = new EditBox(this.font, left, 100, 320, 20, Component.translatable("gui.pixelreel.tunarr.config.xmltv"));
		this.xmltvBox.setMaxLength(512);
		this.xmltvBox.setHint(Component.translatable("gui.pixelreel.tunarr.config.xmltv_hint"));
		this.addRenderableWidget(this.xmltvBox);

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.tunarr.config.save"), button -> this.save())
				.bounds(this.width / 2 - 110, this.height - 36, 100, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(this.parent);
				}
			}).bounds(this.width / 2 + 10, this.height - 36, 100, 20).build()
		);
		this.applyConfigData();
	}

	public void onConfigUpdated() {
		this.applyConfigData();
	}

	private void applyConfigData() {
		ModNetworkPayloads.TunarrConfigData data = ClientMediaCache.INSTANCE.tunarrConfigData();
		if (data == null || this.m3uBox == null) {
			return;
		}
		this.m3uBox.setValue(data.m3uUrl());
		this.xmltvBox.setValue(data.xmltvUrl());
	}

	private void save() {
		ClientNetworking.updateTunarrConfig(this.m3uBox.getValue().trim(), this.xmltvBox.getValue().trim());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, GuiColors.TEXT);
		graphics.drawCenteredString(this.font, Component.translatable("gui.pixelreel.tunarr.config.subtitle"), this.width / 2, 30, GuiColors.TEXT_DIM);
		graphics.drawString(this.font, Component.translatable("gui.pixelreel.tunarr.config.m3u"), this.width / 2 - 160, 44, GuiColors.TEXT_DIM);
		graphics.drawString(this.font, Component.translatable("gui.pixelreel.tunarr.config.xmltv"), this.width / 2 - 160, 88, GuiColors.TEXT_DIM);
		graphics.drawCenteredString(this.font, Component.translatable("gui.pixelreel.tunarr.config.help"), this.width / 2, 130, GuiColors.TEXT_DIM);

		ModNetworkPayloads.TunarrConfigData data = ClientMediaCache.INSTANCE.tunarrConfigData();
		if (data != null) {
			LiveStatus status = data.status();
			Component line;
			int color = GuiColors.TEXT_DIM;
			if (!status.configured()) {
				line = Component.translatable("gui.pixelreel.tunarr.config.not_configured");
			} else if (!status.reachable()) {
				line = Component.translatable("gui.pixelreel.menu.offline", status.detail());
				color = GuiColors.ERROR;
			} else {
				line = Component.translatable("gui.pixelreel.tunarr.config.ready", status.channelCount());
			}
			graphics.drawCenteredString(this.font, line, this.width / 2, this.height - 56, color);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
