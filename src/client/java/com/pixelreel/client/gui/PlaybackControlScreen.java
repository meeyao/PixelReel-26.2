package com.pixelreel.client.gui;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.gui.shared.TimeFormat;
import com.pixelreel.networking.ScreenAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** controls: pause, restart, next-episode */
public class PlaybackControlScreen extends Screen {
	private static final int COLOR_BAR_BACK = 0xFF2A3238;
	private static final int COLOR_BAR = 0xFF46C878;

	private final DisplayBlockEntity display;
	private Button pauseButton;
	private Button subtitleButton;

	public PlaybackControlScreen(DisplayBlockEntity display) {
		super(Component.translatable("gui.pixelreel.playback.title"));
		this.display = display;
	}

	@Override
	protected void init() {
		int centre = this.width / 2;
		int y = this.height - 70;
		this.addRenderableWidget(
			Button.builder(Component.literal("<<"), button ->
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.SEEK_BACKWARD, 0.0F)
			).bounds(centre - 150, y, 40, 20).build()
		);
		this.pauseButton = this.addRenderableWidget(
			Button.builder(this.pauseLabel(), button -> {
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.PAUSE_TOGGLE, 0.0F);
				button.setMessage(this.pauseLabel(!this.display.isPlaybackPaused()));
			}).bounds(centre - 100, y, 70, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.literal(">>"), button ->
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.SEEK_FORWARD, 0.0F)
			).bounds(centre - 20, y, 40, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.playback.restart"), button ->
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.RESTART, 0.0F)
			).bounds(centre + 30, y, 70, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.playback.stop"), button ->
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.STOP, 0.0F)
			).bounds(centre + 110, y, 50, 20).build()
		);
		this.subtitleButton = this.addRenderableWidget(
			Button.builder(this.subtitleLabel(), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new SubtitlePickerScreen(this.display, this));
				}
			}).bounds(centre - 140, y - 28, 280, 20).build()
		);
		this.subtitleButton.active = this.display.isOnDemand();
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.menu.close"), button -> this.onClose())
				.bounds(centre - 40, y + 28, 80, 20).build()
		);
		if (this.display.hasAutoplayPending()) {
			this.addRenderableWidget(
				Button.builder(Component.translatable("gui.pixelreel.playback.play_now"), button ->
					ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.PLAY_NEXT_NOW, 0.0F)
				).bounds(centre - 110, 100, 100, 20).build()
			);
			this.addRenderableWidget(
				Button.builder(Component.translatable("gui.pixelreel.playback.cancel_next"), button ->
					ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.CANCEL_NEXT, 0.0F)
				).bounds(centre + 10, 100, 100, 20).build()
			);
		}
	}

	private Component pauseLabel() {
		return this.pauseLabel(this.display.isPlaybackPaused());
	}

	private Component pauseLabel(boolean paused) {
		return Component.translatable(paused ? "gui.pixelreel.playback.resume" : "gui.pixelreel.playback.pause");
	}

	private Component subtitleLabel() {
		if (!this.display.isOnDemand()) {
			return Component.translatable("gui.pixelreel.playback.subtitles_none");
		}
		if (!this.display.hasSubtitleTracks()) {
			return Component.translatable("gui.pixelreel.playback.subtitles_pick_empty");
		}
		return Component.translatable("gui.pixelreel.playback.subtitles", this.display.getSubtitleLabel());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);
		graphics.drawCenteredString(this.font, this.display.getMediaTitle(), this.width / 2, 20, GuiColors.TEXT);

		long pos = this.display.currentPlaybackPositionMs();
		long dur = Math.max(1L, this.display.getPlaybackDurationMs());
		float progress = Math.min(Math.max(pos / (float)dur, 0.0F), 1.0F);
		int barLeft = this.width / 2 - 140;
		int barTop = 40;
		int barWidth = 280;
		graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 8, COLOR_BAR_BACK);
		graphics.fill(barLeft, barTop, barLeft + (int)(barWidth * progress), barTop + 8, COLOR_BAR);
		graphics.drawCenteredString(
			this.font,
			Component.literal(TimeFormat.format(pos) + " / " + TimeFormat.format(this.display.getPlaybackDurationMs())),
			this.width / 2,
			54,
			GuiColors.TEXT_DIM
		);

		if (this.display.hasAutoplayPending()) {
			long remaining = Math.max(0L, (this.display.getAutoplayAtMillis() - System.currentTimeMillis() + 999L) / 1000L);
			graphics.drawCenteredString(
				this.font,
				Component.translatable("gui.pixelreel.playback.next_in", remaining, this.display.getNextEpisodeTitle()),
				this.width / 2,
				80,
				GuiColors.TEXT
			);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		long dur = this.display.getPlaybackDurationMs();
		if (dur > 0L && button == 0) {
			int barLeft = this.width / 2 - 140;
			int barTop = 40;
			int barWidth = 280;
			if (mouseX >= barLeft && mouseX <= barLeft + barWidth && mouseY >= barTop && mouseY <= barTop + 8) {
				float ratio = (float)((mouseX - barLeft) / barWidth);
				ClientNetworking.sendControl(this.display.getBlockPos(), ScreenAction.SEEK, ratio * dur);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.display.isRemoved()) {
			this.onClose();
			return;
		}
		if (this.pauseButton != null) {
			this.pauseButton.setMessage(this.pauseLabel());
		}
		if (this.subtitleButton != null) {
			this.subtitleButton.active = this.display.isOnDemand();
			this.subtitleButton.setMessage(this.subtitleLabel());
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
