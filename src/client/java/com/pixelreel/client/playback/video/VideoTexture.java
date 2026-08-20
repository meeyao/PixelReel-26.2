package com.pixelreel.client.playback.video;

import com.mojang.blaze3d.platform.NativeImage;
import com.pixelreel.PixelReel;
import com.pixelreel.client.playback.subtitle.SubtitleRasterizer;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/** video texture */
public final class VideoTexture implements AutoCloseable {
	public static final String CHROMA = "RGBA";
	private static final int MATTE_SAMPLE_EVERY = 12;
	private static final int MATTE_LOCK_HITS = 5;
	private static int nextId;

	private final ResourceLocation textureId;
	private final Object lock = new Object();
	private @Nullable ByteBuffer staging;
	private int stagingWidth;
	private int stagingHeight;
	private float displayAspect;
	private float contentAspect;
	private float contentU0;
	private float contentV0;
	private float contentU1 = 1.0F;
	private float contentV1 = 1.0F;
	private int framesSinceMatte;
	private int matteStableCount;
	private MatteDetector.@Nullable Bounds pendingMatte;
	private boolean matteLocked;
	private boolean dirty;
	private @Nullable DynamicTexture texture;
	private int textureWidth;
	private int textureHeight;
	private boolean closed;
	private final HdrToneMapper hdrToneMapper = new HdrToneMapper();

	public VideoTexture() {
		this.textureId = PixelReel.id("video/stream_" + nextId++);
	}

	public void forceHdrToneMap() {
		this.hdrToneMapper.forceOn();
	}

	public ResourceLocation textureId() {
		return this.textureId;
	}

	public int frameWidth() {
		synchronized (this.lock) {
			return this.stagingWidth;
		}
	}

	public int frameHeight() {
		synchronized (this.lock) {
			return this.stagingHeight;
		}
	}

	public float displayAspect() {
		synchronized (this.lock) {
			if (this.contentAspect > 0.05F) {
				return this.contentAspect;
			}
			if (this.displayAspect > 0.05F) {
				return this.displayAspect;
			}
			return this.stagingHeight > 0 ? (float)this.stagingWidth / this.stagingHeight : 0.0F;
		}
	}

	public float contentU0() {
		synchronized (this.lock) {
			return this.contentU0;
		}
	}

	public float contentV0() {
		synchronized (this.lock) {
			return this.contentV0;
		}
	}

	public float contentU1() {
		synchronized (this.lock) {
			return this.contentU1;
		}
	}

	public float contentV1() {
		synchronized (this.lock) {
			return this.contentV1;
		}
	}

	public boolean hasFrame() {
		synchronized (this.lock) {
			return this.stagingWidth > 0 && this.stagingHeight > 0;
		}
	}

	public void submitFrame(ByteBuffer source, int width, int height, float aspect) {
		this.submitFrame(source, width, height, aspect, null);
	}

	public void submitFrame(ByteBuffer source, int width, int height, float aspect, @Nullable String subtitleText) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int bytes = width * height * 4;
		synchronized (this.lock) {
			if (this.closed) {
				return;
			}
			// Drop frames while the previous one is still waiting for GPU upload.
			// Stops VLC decode from piling copies / HDR / subtitle work onto native memory.
			if (this.dirty && this.staging != null && this.stagingWidth == width && this.stagingHeight == height) {
				return;
			}
			if (this.staging == null || this.stagingWidth != width || this.stagingHeight != height) {
				if (this.staging != null) {
					MemoryUtil.memFree(this.staging);
				}
				this.staging = MemoryUtil.memAlloc(bytes);
				this.stagingWidth = width;
				this.stagingHeight = height;
				this.resetMatte();
			}
			this.displayAspect = aspect > 0.05F ? aspect : (float)width / height;
			if (source.remaining() >= bytes) {
				MemoryUtil.memCopy(MemoryUtil.memAddress(source), MemoryUtil.memAddress(this.staging), bytes);
				this.staging.clear();
				this.staging.limit(bytes);
				this.hdrToneMapper.processInPlace(this.staging, width, height);
				if (subtitleText != null && !subtitleText.isBlank()) {
					this.staging.clear();
					this.staging.limit(bytes);
					SubtitleRasterizer.burnIn(this.staging, width, height, subtitleText);
				}
				this.staging.clear();
				this.dirty = true;
				this.maybeDetectMatte(width, height);
			}
			this.refreshContentAspect();
		}
	}

	private void maybeDetectMatte(int width, int height) {
		if (this.staging == null || this.matteLocked) {
			return;
		}
		if (++this.framesSinceMatte < MATTE_SAMPLE_EVERY) {
			return;
		}
		this.framesSinceMatte = 0;
		this.staging.clear();
		this.staging.limit(width * height * 4);

		if (MatteDetector.isTooDark(this.staging, width, height)) {
			return;
		}

		MatteDetector.Bounds detected = MatteDetector.detect(this.staging, width, height);
		if (detected.isFull()) {
			this.pendingMatte = null;
			this.matteStableCount = 0;
			return;
		}

		if (this.pendingMatte != null && nearlyEqual(this.pendingMatte, detected)) {
			this.matteStableCount++;
		} else {
			this.pendingMatte = detected;
			this.matteStableCount = 1;
		}
		if (this.matteStableCount < MATTE_LOCK_HITS) {
			return;
		}

		this.contentU0 = detected.u0();
		this.contentV0 = detected.v0();
		this.contentU1 = detected.u1();
		this.contentV1 = detected.v1();
		this.matteLocked = true;
		this.pendingMatte = null;
		this.matteStableCount = 0;
	}

	private void refreshContentAspect() {
		float frameAspect = this.displayAspect > 0.05F
			? this.displayAspect
			: (this.stagingHeight > 0 ? (float)this.stagingWidth / this.stagingHeight : 0.0F);
		float fu = Math.max(0.01F, this.contentU1 - this.contentU0);
		float fv = Math.max(0.01F, this.contentV1 - this.contentV0);
		this.contentAspect = frameAspect * (fu / fv);
	}

	private void resetMatte() {
		this.contentU0 = 0.0F;
		this.contentV0 = 0.0F;
		this.contentU1 = 1.0F;
		this.contentV1 = 1.0F;
		this.contentAspect = 0.0F;
		this.framesSinceMatte = 0;
		this.matteStableCount = 0;
		this.pendingMatte = null;
		this.matteLocked = false;
	}

	private static boolean nearlyEqual(MatteDetector.Bounds a, MatteDetector.Bounds b) {
		return Math.abs(a.u0() - b.u0()) < 0.02F
			&& Math.abs(a.v0() - b.v0()) < 0.02F
			&& Math.abs(a.u1() - b.u1()) < 0.02F
			&& Math.abs(a.v1() - b.v1()) < 0.02F;
	}

	public boolean uploadIfDirty() {
		int width;
		int height;
		synchronized (this.lock) {
			if (this.closed || this.staging == null) {
				return this.texture != null;
			}
			width = this.stagingWidth;
			height = this.stagingHeight;
		}

		if (this.texture == null || this.textureWidth != width || this.textureHeight != height) {
			this.recreate(width, height);
			if (this.texture == null) {
				return false;
			}
		}

		DynamicTexture current = this.texture;
		NativeImage image = current.getPixels();
		if (image == null) {
			return false;
		}
		synchronized (this.lock) {
			if (this.closed || this.staging == null) {
				return true;
			}
			if (!this.dirty) {
				return true;
			}
			try {
				MemoryUtil.memCopy(
					MemoryUtil.memAddress(this.staging),
					NativeImageAccess.pointer(image),
					(long)width * height * 4L
				);
			} catch (Throwable t) {
				PixelReel.LOGGER.error("Failed to copy video frame into NativeImage", t);
				this.dirty = false;
				return false;
			}
			this.dirty = false;
		}
		current.upload();
		return true;
	}

	private void recreate(int width, int height) {
		this.releaseTexture();
		try {
			DynamicTexture created = new DynamicTexture(width, height, true);
			Minecraft.getInstance().getTextureManager().register(this.textureId, created);
			this.texture = created;
			this.textureWidth = width;
			this.textureHeight = height;
		} catch (Exception e) {
			PixelReel.LOGGER.error("Failed to allocate a {}x{} video texture", width, height, e);
			this.texture = null;
		}
	}

	private void releaseTexture() {
		if (this.texture != null) {
			Minecraft.getInstance().getTextureManager().release(this.textureId);
			this.texture = null;
			this.textureWidth = 0;
			this.textureHeight = 0;
		}
	}

	@Override
	public void close() {
		ByteBuffer toFree;
		synchronized (this.lock) {
			if (this.closed) {
				return;
			}
			this.closed = true;
			toFree = this.staging;
			this.staging = null;
			this.stagingWidth = 0;
			this.stagingHeight = 0;
			this.displayAspect = 0.0F;
			this.resetMatte();
			this.dirty = false;
		}
		if (toFree != null) {
			MemoryUtil.memFree(toFree);
		}
		this.releaseTexture();
	}
}
