package com.pixelreel.client.gui;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.media.SubtitleTrack;
import com.pixelreel.networking.ScreenAction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** subtitle picker this one kinda sucks needs work*/
public class SubtitlePickerScreen extends Screen {
	private static final int HEADER_HEIGHT = 40;
	private static final int FOOTER_HEIGHT = 36;
	private static final int ROW_HEIGHT = 36;
	private static final int ROW_GAP = 4;

	private final DisplayBlockEntity display;
	private final Screen parent;
	private final List<TrackRow> rows = new ArrayList<>();
	private double scroll;

	public SubtitlePickerScreen(DisplayBlockEntity display, Screen parent) {
		super(Component.translatable("gui.pixelreel.playback.subtitles_title"));
		this.display = display;
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.rows.clear();
		this.scroll = 0.0;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(this.parent);
				}
			}).bounds(this.width / 2 - 40, this.height - 28, 80, 20).build()
		);
		this.rebuildRows();
	}

	private void rebuildRows() {
		for (TrackRow row : this.rows) {
			this.removeWidget(row);
		}
		this.rows.clear();

		List<SubtitleTrack> options = new ArrayList<>();
		options.add(SubtitleTrack.OFF);
		options.addAll(this.display.getSubtitleTracks());

		int y = HEADER_HEIGHT;
		int rowWidth = Math.min(360, this.width - 40);
		int rowX = (this.width - rowWidth) / 2;
		for (SubtitleTrack track : options) {
			TrackRow row = new TrackRow(
				rowX,
				y - (int)this.scroll,
				rowWidth,
				track,
				this.display.getSelectedSubtitleIndex() == track.index(),
				this::selectTrack
			);
			this.rows.add(row);
			this.addRenderableWidget(row);
			y += ROW_HEIGHT + ROW_GAP;
		}
		this.applyVisibility();
	}

	private void selectTrack(SubtitleTrack track) {
		ClientNetworking.sendControl(
			this.display.getBlockPos(),
			ScreenAction.SELECT_SUBTITLE,
			(float)track.index()
		);
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}

	private void applyVisibility() {
		int y = HEADER_HEIGHT;
		int top = HEADER_HEIGHT - 2;
		int bottom = this.height - FOOTER_HEIGHT;
		int selected = this.display.getSelectedSubtitleIndex();
		for (TrackRow row : this.rows) {
			int rowY = y - (int)this.scroll;
			row.setY(rowY);
			row.setSelected(selected == row.track.index());
			boolean visible = rowY + ROW_HEIGHT >= top && rowY <= bottom;
			row.visible = visible;
			row.active = visible;
			y += ROW_HEIGHT + ROW_GAP;
		}
	}

	private int contentHeight() {
		int size = this.rows.size();
		if (size <= 0) {
			return 0;
		}
		return size * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
	}

	private void clampScroll() {
		int viewHeight = this.height - FOOTER_HEIGHT - HEADER_HEIGHT;
		this.scroll = Math.clamp(this.scroll, 0.0, Math.max(0, this.contentHeight() - viewHeight));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, GuiColors.TEXT);
		String current = this.display.getSubtitleLabel();
		graphics.drawCenteredString(
			this.font,
			Component.translatable("gui.pixelreel.playback.subtitles_current", current),
			this.width / 2,
			26,
			GuiColors.TEXT_DIM
		);
		if (this.display.getSubtitleTracks().isEmpty()) {
			graphics.drawCenteredString(
				this.font,
				Component.translatable("gui.pixelreel.playback.subtitles_none"),
				this.width / 2,
				HEADER_HEIGHT + 20,
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
	public void tick() {
		super.tick();
		if (this.display.isRemoved()) {
			this.onClose();
			return;
		}
		this.applyVisibility();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static final class TrackRow extends AbstractWidget {
		private final SubtitleTrack track;
		private final java.util.function.Consumer<SubtitleTrack> onSelect;
		private boolean selected;

		private TrackRow(
			int x,
			int y,
			int width,
			SubtitleTrack track,
			boolean selected,
			java.util.function.Consumer<SubtitleTrack> onSelect
		) {
			super(x, y, width, ROW_HEIGHT, Component.literal(track.displayLabel()));
			this.track = track;
			this.selected = selected;
			this.onSelect = onSelect;
		}

		private void setSelected(boolean selected) {
			this.selected = selected;
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			this.onSelect.accept(this.track);
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			int x = this.getX();
			int y = this.getY();
			int fill = this.selected ? GuiColors.ROW_ACTIVE : (this.isHovered() ? GuiColors.ROW_HOVER : GuiColors.ROW);
			graphics.fill(x, y, x + this.width, y + ROW_HEIGHT, fill);
			var font = net.minecraft.client.Minecraft.getInstance().font;
			int titleColor = this.selected ? GuiColors.ACCENT_WARM : GuiColors.TEXT;
			graphics.drawString(font, Component.literal(this.track.displayLabel()), x + 10, y + 6, titleColor);
			String detail = this.track.detailLabel();
			if (!detail.isEmpty()) {
				graphics.drawString(font, Component.literal(detail), x + 10, y + 20, GuiColors.TEXT_DIM);
			}
			if (this.selected) {
				graphics.drawString(font, Component.literal("✓"), x + this.width - 18, y + 12, GuiColors.ACCENT_WARM);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
