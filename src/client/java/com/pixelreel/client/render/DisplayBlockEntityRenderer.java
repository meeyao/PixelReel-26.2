package com.pixelreel.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pixelreel.PixelReel;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.blocks.DisplayType;
import com.pixelreel.client.playback.PlaybackManager;
import com.pixelreel.client.playback.PlaybackStatus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** this is the what the screen looks like this is pretty cool i know */

public class DisplayBlockEntityRenderer implements BlockEntityRenderer<DisplayBlockEntity> {
	private static final ResourceLocation SCREEN_OFF = PixelReel.id("textures/block/screen_off.png");
	private static final ResourceLocation CONNECTING = PixelReel.id("textures/block/screen_connecting.png");
	private static final ResourceLocation ERROR = PixelReel.id("textures/block/screen_error.png");
	private static final ResourceLocation NO_SIGNAL = PixelReel.id("textures/block/screen_no_signal.png");
	private static final ResourceLocation NO_PLAYER = PixelReel.id("textures/block/screen_no_player.png");
	private static final ResourceLocation BACKING = PixelReel.id("textures/block/tv_body.png");

	private static final float STATUS_ASPECT = 2.0F;
	private static final int PICTURE_LIGHT = 0xF000F0;
	private static final float PIXEL = 0.0625F;
	private static final float BACKING_BIAS = 0.0F;
	private static final float BACK_FACE_BIAS = -0.15F;
	private static final float SCREEN_BIAS = 0.12F;
	private static final float PICTURE_BIAS = 0.22F;

	public DisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(
		DisplayBlockEntity blockEntity,
		float partialTicks,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int packedLight,
		int packedOverlay
	) {
		DisplayBlock block = blockEntity.displayBlock();
		DisplayType type = block == null ? null : block.type();
		if (type == null) {
			return;
		}
		Direction facing = blockEntity.facing();

		ResourceLocation pictureTexture = null;
		float pictureAspect = 0.0F;
		float contentU0 = 0.0F;
		float contentV0 = 0.0F;
		float contentU1 = 1.0F;
		float contentV1 = 1.0F;
		if (blockEntity.isPowered() && !blockEntity.isSuspended()) {
			if (!blockEntity.hasChannel()) {
				pictureTexture = NO_SIGNAL;
				pictureAspect = STATUS_ASPECT;
			} else {
				PlaybackManager.PictureHandle picture = PlaybackManager.INSTANCE.pictureFor(blockEntity);
				if (picture != null) {
					pictureTexture = picture.textureId();
					pictureAspect = picture.aspect();
					contentU0 = picture.u0();
					contentV0 = picture.v0();
					contentU1 = picture.u1();
					contentV1 = picture.v1();
				} else {
					PlaybackStatus status = PlaybackManager.INSTANCE.statusFor(blockEntity);
					pictureAspect = STATUS_ASPECT;
					pictureTexture = switch (status == null ? PlaybackStatus.CONNECTING : status) {
						case UNAVAILABLE -> NO_PLAYER;
						case ERROR, ENDED -> ERROR;
						case CONNECTING, BUFFERING, PLAYING, IDLE -> CONNECTING;
					};
				}
			}
		}

		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
		poseStack.translate(-0.5F, -0.5F, -0.5F);

		Sheet screen = new Sheet(type.screenLeft(), type.screenBottom(), type.screenRight(), type.screenTop());
		submitSheet(type, screen, BACKING, BACKING_BIAS, false, packedLight, poseStack, bufferSource);
		submitSheet(type, screen, BACKING, BACK_FACE_BIAS, true, packedLight, poseStack, bufferSource);
		submitSheet(type, screen, SCREEN_OFF, SCREEN_BIAS, false, packedLight, poseStack, bufferSource);

		if (pictureTexture != null) {
			boolean statusGraphic = Math.abs(pictureAspect - STATUS_ASPECT) < 0.001F;
			FittedPicture fitted = statusGraphic
				? FittedPicture.full(screen)
				: containContent(type, screen, pictureAspect);
			float u0 = lerp(contentU0, contentU1, fitted.uMin());
			float u1 = lerp(contentU0, contentU1, fitted.uMax());
			float v0 = lerp(contentV0, contentV1, fitted.vMin());
			float v1 = lerp(contentV0, contentV1, fitted.vMax());
			submitSheet(
				type,
				fitted.sheet(),
				pictureTexture,
				PICTURE_BIAS,
				false,
				PICTURE_LIGHT,
				poseStack,
				bufferSource,
				u0,
				u1,
				v0,
				v1
			);
		}

		poseStack.popPose();
	}

	private static FittedPicture containContent(DisplayType type, Sheet screen, float pictureAspect) {
		if (pictureAspect <= 0.05F || screen.height() <= 0.0F) {
			return FittedPicture.full(screen);
		}
		float screenWidth = type.isCurved() ? type.arcLengthPx(screen.left(), screen.right()) : screen.width();
		float screenAspect = screenWidth / screen.height();
		if (Math.abs(screenAspect - pictureAspect) < 0.001F) {
			return FittedPicture.full(screen);
		}
		if (pictureAspect > screenAspect) {
			float fittedHeight = screenWidth / pictureAspect;
			float inset = (screen.height() - fittedHeight) * 0.5F;
			return FittedPicture.full(new Sheet(screen.left(), screen.bottom() + inset, screen.right(), screen.top() - inset));
		}
		float inset = type.isCurved()
			? type.pillarboxInsetForAspect(screen.left(), screen.right(), screen.height(), pictureAspect)
			: (screen.width() - screen.height() * pictureAspect) * 0.5F;
		return FittedPicture.full(new Sheet(screen.left() + inset, screen.bottom(), screen.right() - inset, screen.top()));
	}

	private record FittedPicture(Sheet sheet, float uMin, float uMax, float vMin, float vMax) {
		static FittedPicture full(Sheet sheet) {
			return new FittedPicture(sheet, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	private static void submitSheet(
		DisplayType type,
		Sheet sheet,
		ResourceLocation texture,
		float bias,
		boolean backFace,
		int packedLight,
		PoseStack poseStack,
		MultiBufferSource bufferSource
	) {
		submitSheet(type, sheet, texture, bias, backFace, packedLight, poseStack, bufferSource, 0.0F, 1.0F, 0.0F, 1.0F);
	}

	private static void submitSheet(
		DisplayType type,
		Sheet sheet,
		ResourceLocation texture,
		float bias,
		boolean backFace,
		int packedLight,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		float uMin,
		float uMax,
		float vMin,
		float vMax
	) {
		if (sheet.width() <= 0.0F || sheet.height() <= 0.0F) {
			return;
		}
		VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutout(texture));
		emit(poseStack.last(), buffer, type, sheet, bias, backFace, packedLight, uMin, uMax, vMin, vMax);
	}

	private static void emit(
		PoseStack.Pose pose,
		VertexConsumer buffer,
		DisplayType type,
		Sheet sheet,
		float bias,
		boolean backFace,
		int packedLight,
		float uMin,
		float uMax,
		float vMin,
		float vMax
	) {
		float displayWidth = type.displayWidthPx();
		float controllerRight = (type.controllerColumn() + 1) * 16.0F;
		int segments = type.isCurved() ? Math.max(1, Math.round((float)type.curveSegments() * sheet.width() / displayWidth)) : 1;
		float vTop = vMin;
		float vBottom = vMax;
		float yBottom = sheet.bottom();
		float yTop = sheet.top();

		float[] arcAt = new float[segments + 1];
		arcAt[0] = 0.0F;
		for (int i = 1; i <= segments; i++) {
			float d0 = lerp(sheet.left(), sheet.right(), (float)(i - 1) / segments);
			float d1 = lerp(sheet.left(), sheet.right(), (float)i / segments);
			arcAt[i] = arcAt[i - 1] + type.arcLengthPx(d0, d1);
		}
		float totalArc = Math.max(arcAt[segments], 1.0E-4F);

		for (int i = 0; i < segments; i++) {
			float d0 = lerp(sheet.left(), sheet.right(), (float)i / segments);
			float d1 = lerp(sheet.left(), sheet.right(), (float)(i + 1) / segments);
			float x0 = controllerRight - d0;
			float x1 = controllerRight - d1;
			float z0 = type.depthAt(d0 / displayWidth) - bias;
			float z1 = type.depthAt(d1 / displayWidth) - bias;
			float u0 = uMin + (uMax - uMin) * (arcAt[i] / totalArc);
			float u1 = uMin + (uMax - uMin) * (arcAt[i + 1] / totalArc);
			float slope = Math.abs(x1 - x0) < 1.0E-5F ? 0.0F : (z1 - z0) / (x1 - x0);
			float length = (float)Math.sqrt(slope * slope + 1.0F);
			float normalX = slope / length;
			float normalZ = -1.0F / length;
			if (backFace) {
				vertex(buffer, pose, x0, yBottom, z0, u0, vBottom, -normalX, -normalZ, packedLight);
				vertex(buffer, pose, x0, yTop, z0, u0, vTop, -normalX, -normalZ, packedLight);
				vertex(buffer, pose, x1, yTop, z1, u1, vTop, -normalX, -normalZ, packedLight);
				vertex(buffer, pose, x1, yBottom, z1, u1, vBottom, -normalX, -normalZ, packedLight);
			} else {
				vertex(buffer, pose, x0, yBottom, z0, u0, vBottom, normalX, normalZ, packedLight);
				vertex(buffer, pose, x1, yBottom, z1, u1, vBottom, normalX, normalZ, packedLight);
				vertex(buffer, pose, x1, yTop, z1, u1, vTop, normalX, normalZ, packedLight);
				vertex(buffer, pose, x0, yTop, z0, u0, vTop, normalX, normalZ, packedLight);
			}
		}
	}

	private static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}

	private static void vertex(
		VertexConsumer buffer,
		PoseStack.Pose pose,
		float xPixels,
		float yPixels,
		float zPixels,
		float u,
		float v,
		float normalX,
		float normalZ,
		int packedLight
	) {
		buffer.addVertex(pose, xPixels * PIXEL, yPixels * PIXEL, zPixels * PIXEL)
			.setColor(255, 255, 255, 255)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(packedLight)
			.setNormal(pose, normalX, 0.0F, normalZ);
	}

	@Override
	public boolean shouldRenderOffScreen(DisplayBlockEntity blockEntity) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}

	private record Sheet(float left, float bottom, float right, float top) {
		float width() {
			return this.right - this.left;
		}

		float height() {
			return this.top - this.bottom;
		}
	}
}
