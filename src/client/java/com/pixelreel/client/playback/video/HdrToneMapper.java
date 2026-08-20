package com.pixelreel.client.playback.video;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/* HDR tone mapper */
final class HdrToneMapper {
	private enum State {
		DETECTING,
		OFF,
		ON
	}

	private static final int SAMPLE_STRIDE = 12;
	private static final int DECIDE_AFTER = 8;
	private static final float HDR_MEAN_MAX = 0.13F;
	private static final float HDR_P95_MAX = 0.55F;
	private static final float SDR_MEAN_MIN = 0.20F;
	private static final float BLACK_MEAN = 0.025F;
	private static final float TARGET_MEAN = 0.34F;
	private static final float MAX_EXPOSURE = 12.0F;
	private static final float MIN_EXPOSURE = 2.2F;
	private static final float HABLE_WHITE = 11.2F;

	private State state = State.DETECTING;
	private int goodSamples;
	private int hdrVotes;
	private int sdrVotes;
	private float meanAccum;
	private float exposure = 1.0F;
	private final byte[] lut = new byte[256];
	private float lutExposure = Float.NaN;

	void forceOn() {
		this.state = State.ON;
		if (this.exposure < MIN_EXPOSURE) {
			this.exposure = 5.5F;
		}
		this.rebuildLut();
	}

	void forceOff() {
		this.state = State.OFF;
	}

	void processInPlace(ByteBuffer rgba, int width, int height) {
		if (rgba == null || !rgba.isDirect() || width < 8 || height < 8) {
			return;
		}
		int bytes = width * height * 4;
		if (rgba.remaining() < bytes) {
			return;
		}
		if (this.state == State.OFF) {
			return;
		}
		if (this.state == State.DETECTING) {
			this.sampleAndMaybeDecide(rgba, width, height);
		}
		if (this.state == State.ON) {
			this.apply(rgba, width, height);
		} else if (this.state == State.DETECTING && this.hdrVotes > this.sdrVotes && this.goodSamples > 0) {
			float provisional = this.exposure >= MIN_EXPOSURE ? this.exposure : 5.0F;
			this.applyWithExposure(rgba, width, height, provisional);
		}
	}

	private void sampleAndMaybeDecide(ByteBuffer rgba, int width, int height) {
		FrameStats stats = sample(rgba, width, height);
		if (stats.mean <= BLACK_MEAN) {
			return;
		}
		this.goodSamples++;
		this.meanAccum += stats.mean;
		if (stats.mean <= HDR_MEAN_MAX && stats.p95 <= HDR_P95_MAX) {
			this.hdrVotes++;
			float needed = TARGET_MEAN / Math.max(0.02F, stats.mean);
			this.exposure = clamp(needed, MIN_EXPOSURE, MAX_EXPOSURE);
		} else if (stats.mean >= SDR_MEAN_MIN) {
			this.sdrVotes++;
		}

		if (this.goodSamples == 1) {
			if (stats.mean <= 0.09F && stats.p95 <= 0.42F) {
				this.state = State.ON;
				this.exposure = clamp(TARGET_MEAN / Math.max(0.02F, stats.mean), MIN_EXPOSURE, MAX_EXPOSURE);
				this.rebuildLut();
				return;
			}
			if (stats.mean >= 0.24F) {
				this.state = State.OFF;
				return;
			}
		}

		if (this.goodSamples < DECIDE_AFTER) {
			return;
		}
		if (this.hdrVotes > this.sdrVotes && this.hdrVotes >= DECIDE_AFTER / 2) {
			float avgMean = this.meanAccum / this.goodSamples;
			this.exposure = clamp(TARGET_MEAN / Math.max(0.02F, avgMean), MIN_EXPOSURE, MAX_EXPOSURE);
			this.state = State.ON;
			this.rebuildLut();
		} else {
			this.state = State.OFF;
		}
	}

	private void apply(ByteBuffer rgba, int width, int height) {
		this.applyWithExposure(rgba, width, height, this.exposure);
	}

	private void applyWithExposure(ByteBuffer rgba, int width, int height, float useExposure) {
		if (Float.isNaN(this.lutExposure) || Math.abs(this.lutExposure - useExposure) > 0.01F) {
			this.buildLut(useExposure);
		}
		long base = MemoryUtil.memAddress(rgba);
		int pixels = width * height;
		for (int i = 0; i < pixels; i++) {
			long p = base + (long)i * 4L;
			int r = MemoryUtil.memGetByte(p) & 0xFF;
			int g = MemoryUtil.memGetByte(p + 1) & 0xFF;
			int b = MemoryUtil.memGetByte(p + 2) & 0xFF;
			MemoryUtil.memPutByte(p, this.lut[r]);
			MemoryUtil.memPutByte(p + 1, this.lut[g]);
			MemoryUtil.memPutByte(p + 2, this.lut[b]);
		}
	}

	private void rebuildLut() {
		this.buildLut(this.exposure);
	}

	private void buildLut(float useExposure) {
		float white = hable(HABLE_WHITE);
		for (int i = 0; i < 256; i++) {
			float c = i / 255.0F;
			float mapped = hable(c * useExposure) / white;
			mapped = (float)Math.pow(Math.max(0.0F, Math.min(1.0F, mapped)), 0.92);
			this.lut[i] = (byte)Math.round(mapped * 255.0F);
		}
		this.lutExposure = useExposure;
	}

	private static float hable(float x) {
		float a = 0.15F;
		float b = 0.50F;
		float c = 0.10F;
		float d = 0.20F;
		float e = 0.02F;
		float f = 0.30F;
		return ((x * (a * x + c * b) + d * e) / (x * (a * x + b) + d * f)) - e / f;
	}

	private static FrameStats sample(ByteBuffer rgba, int width, int height) {
		long base = MemoryUtil.memAddress(rgba);
		long sum = 0L;
		int count = 0;
		int[] hist = new int[256];
		for (int y = 0; y < height; y += SAMPLE_STRIDE) {
			for (int x = 0; x < width; x += SAMPLE_STRIDE) {
				long p = base + ((long)y * width + x) * 4L;
				int r = MemoryUtil.memGetByte(p) & 0xFF;
				int g = MemoryUtil.memGetByte(p + 1) & 0xFF;
				int b = MemoryUtil.memGetByte(p + 2) & 0xFF;
				int y601 = (r * 77 + g * 150 + b * 29) >> 8;
				sum += y601;
				hist[y601]++;
				count++;
			}
		}
		if (count == 0) {
			return new FrameStats(0.0F, 0.0F);
		}
		float mean = (float)sum / (count * 255.0F);
		int target = Math.max(1, (int)(count * 0.95F));
		int seen = 0;
		int p95 = 0;
		for (int i = 0; i < 256; i++) {
			seen += hist[i];
			if (seen >= target) {
				p95 = i;
				break;
			}
		}
		return new FrameStats(mean, p95 / 255.0F);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private record FrameStats(float mean, float p95) {
	}
}
