package com.pixelreel.client.gui.ondemand;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientMediaCache;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.ClientPosterUrlCache;
import com.pixelreel.client.gui.GuiColors;
import com.pixelreel.client.gui.shared.TimeFormat;
import com.pixelreel.client.texture.PosterCache;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** this handles the detials of the content like movies and tv shows, also adds play and resume buttons. */
public class OnDemandDetailScreen extends Screen {
	private final DisplayBlockEntity display;
	private JellyfinItemSummary item;
	private final OnDemandProvider provider;
	private final @Nullable Screen parent;
	private Button resumeButton;

	public OnDemandDetailScreen(
		DisplayBlockEntity display,
		JellyfinItemSummary item,
		OnDemandProvider provider,
		@Nullable Screen parent
	) {
		super(Component.literal(item.title()));
		this.display = display;
		this.item = item;
		this.provider = provider;
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.applyFetchedItem();
		ClientNetworking.requestJellyfinChildren(
			this.provider,
			ModNetworkPayloads.ChildrenKind.ITEM,
			this.item.id(),
			false
		);

		int centre = this.width / 2;
		int y = this.height - 40;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.play"), button -> {
				ClientNetworking.playJellyfin(this.provider, this.display.getBlockPos(), this.item.id(), 0L);
				this.onClose();
			}).bounds(centre - 110, y, 100, 20).build()
		);
		this.resumeButton = this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.resume"), button -> {
				ClientNetworking.playJellyfin(this.provider, this.display.getBlockPos(), this.item.id(), this.item.resumePositionMs());
				this.onClose();
			}).bounds(centre + 10, y, 100, 20).build()
		);
		this.resumeButton.active = this.item.hasResume();
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.pixelreel.jellyfin.back"), button -> {
				if (this.minecraft != null) {
					this.minecraft.gui.setScreen(this.parent);
				}
			}).bounds(centre - 40, y - 24, 80, 20).build()
		);
	}

	public void onItemUpdated() {
		if (!this.applyFetchedItem()) {
			return;
		}
		if (this.resumeButton != null) {
			this.resumeButton.active = this.item.hasResume();
		}
	}

	private boolean applyFetchedItem() {
		List<JellyfinItemSummary> items = ClientMediaCache.INSTANCE.children(
			this.provider,
			ModNetworkPayloads.ChildrenKind.ITEM,
			this.item.id()
		);
		if (items.isEmpty()) {
			return false;
		}
		this.item = items.getFirst();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		int posterX = this.width / 2 - 160;
		int posterY = 28;
		int posterW = 96;
		int posterH = 144;
		graphics.fill(posterX, posterY, posterX + posterW, posterY + posterH, 0xFF06080A);
		String posterUrl = ClientPosterUrlCache.INSTANCE.url(this.provider, this.item.id());
		PosterCache.Poster poster = PosterCache.INSTANCE.getByUrl(this.item.id(), posterUrl);
		if (poster.state() == PosterCache.State.READY && poster.texture() != null) {
			Identifier texture = poster.texture();
			graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture,
				posterX,
				posterY,
				0.0F,
				0.0F,
				posterW,
				posterH,
				poster.width(),
				poster.height(),
				poster.width(),
				poster.height()
			);
		}

		int textX = posterX + posterW + 16;
		graphics.text(this.font, Component.literal(this.item.title()), textX, posterY, GuiColors.TEXT);
		int lineY = posterY + 14;
		if (this.item.productionYear() > 0) {
			graphics.text(this.font, Component.translatable("gui.pixelreel.jellyfin.year", this.item.productionYear()), textX, lineY, GuiColors.TEXT_DIM);
			lineY += 12;
		}
		long runtimeMin = this.item.runtimeMs() / 60_000L;
		if (runtimeMin > 0L) {
			graphics.text(this.font, Component.translatable("gui.pixelreel.jellyfin.runtime", runtimeMin), textX, lineY, GuiColors.TEXT_DIM);
			lineY += 12;
		}
		if (this.item.hasResume()) {
			graphics.text(
				this.font,
				Component.translatable("gui.pixelreel.jellyfin.resume_at", TimeFormat.format(this.item.resumePositionMs())),
				textX,
				lineY,
				GuiColors.TEXT_DIM
			);
			lineY += 14;
		}
		String overview = this.item.overview().isEmpty()
			? Component.translatable("gui.pixelreel.jellyfin.no_overview").getString()
			: this.item.overview();
		int maxWidth = Math.max(80, this.width - textX - 24);
		int maxLines = Math.max(1, (this.height - 70 - lineY) / 10);
		int start = 0;
		for (int i = 0; i < maxLines && start < overview.length(); i++) {
			String chunk = this.font.plainSubstrByWidth(overview.substring(start), maxWidth);
			if (chunk.isEmpty()) {
				break;
			}
			graphics.text(this.font, Component.literal(chunk), textX, lineY, GuiColors.TEXT_DIM);
			lineY += 10;
			start += chunk.length();
			while (start < overview.length() && Character.isWhitespace(overview.charAt(start))) {
				start++;
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
