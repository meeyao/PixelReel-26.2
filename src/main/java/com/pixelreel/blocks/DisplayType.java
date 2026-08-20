package com.pixelreel.blocks;

/** each display */
public enum DisplayType {
	COMPACT_TELEVISION("compact_television", 3, 2, 1, 0.0F, 0.0F, 48.0F, 32.0F, 1.0F, 0.0F, 1, 16.0F),
	WALL_TELEVISION("wall_television", 6, 4, 2, 0.0F, 0.0F, 96.0F, 64.0F, 1.0F, 0.0F, 1, 24.0F),
	ULTRAWIDE_MONITOR("ultrawide_monitor", 8, 4, 3, 0.0F, 0.0F, 128.0F, 64.0F, 1.0F, 0.0F, 1, 24.0F),
	CINEMA_SCREEN("cinema_screen", 14, 8, 6, 0.0F, 0.0F, 224.0F, 128.0F, 1.0F, 0.0F, 1, 80.0F),
	CURVED_CINEMA_SCREEN("curved_cinema_screen", 16, 7, 7, 0.0F, 0.0F, 256.0F, 112.0F, 15.2F, 22.0F, 32, 80.0F);

	private final String id;
	private final int widthBlocks;
	private final int heightBlocks;
	private final int controllerColumn;
	private final float screenLeft;
	private final float screenBottom;
	private final float screenRight;
	private final float screenTop;
	private final float baseZ;
	private final float curveDepth;
	private final int curveSegments;
	private final float audioRange;

	DisplayType(
		String id,
		int widthBlocks,
		int heightBlocks,
		int controllerColumn,
		float screenLeft,
		float screenBottom,
		float screenRight,
		float screenTop,
		float baseZ,
		float curveDepth,
		int curveSegments,
		float audioRange
	) {
		this.id = id;
		this.widthBlocks = widthBlocks;
		this.heightBlocks = heightBlocks;
		this.controllerColumn = controllerColumn;
		this.screenLeft = screenLeft;
		this.screenBottom = screenBottom;
		this.screenRight = screenRight;
		this.screenTop = screenTop;
		this.baseZ = baseZ;
		this.curveDepth = curveDepth;
		this.curveSegments = curveSegments;
		this.audioRange = audioRange;
	}

	public String id() {
		return this.id;
	}

	public String translationKey() {
		return "block.pixelreel." + this.id;
	}

	public int widthBlocks() {
		return this.widthBlocks;
	}

	public int heightBlocks() {
		return this.heightBlocks;
	}

	public int controllerColumn() {
		return this.controllerColumn;
	}

	public float screenLeft() {
		return this.screenLeft;
	}

	public float screenBottom() {
		return this.screenBottom;
	}

	public float screenRight() {
		return this.screenRight;
	}

	public float screenTop() {
		return this.screenTop;
	}

	public float displayWidthPx() {
		return this.widthBlocks * 16.0F;
	}

	public float displayHeightPx() {
		return this.heightBlocks * 16.0F;
	}

	public float baseZ() {
		return this.baseZ;
	}

	public float curveDepth() {
		return this.curveDepth;
	}

	public int curveSegments() {
		return this.curveSegments;
	}

	public boolean isCurved() {
		return this.curveDepth > 0.0F;
	}

	public boolean isCinema() {
		return this == CINEMA_SCREEN || this == CURVED_CINEMA_SCREEN;
	}

	public float audioRange() {
		return this.audioRange;
	}

	public float screenAspect() {
		float height = this.screenTop - this.screenBottom;
		if (height <= 0.0F) {
			return 1.0F;
		}
		return this.arcLengthPx(this.screenLeft, this.screenRight) / height;
	}

	public float depthAt(float u) {
		if (!this.isCurved()) {
			return this.baseZ;
		}
		float t = u * 2.0F - 1.0F;
		return this.baseZ - this.curveDepth * t * t;
	}

	public float arcLengthPx(float fromD, float toD) {
		float start = Math.min(fromD, toD);
		float end = Math.max(fromD, toD);
		if (end <= start) {
			return 0.0F;
		}
		if (!this.isCurved()) {
			return end - start;
		}
		int samples = Math.max(8, Math.round((end - start) / 2.0F));
		float width = this.displayWidthPx();
		float length = 0.0F;
		float prevD = start;
		float prevZ = this.depthAt(start / width);
		for (int i = 1; i <= samples; i++) {
			float d = start + (end - start) * i / samples;
			float z = this.depthAt(d / width);
			float dx = d - prevD;
			float dz = z - prevZ;
			length += (float)Math.sqrt(dx * dx + dz * dz);
			prevD = d;
			prevZ = z;
		}
		return length;
	}

	public float pillarboxInsetForAspect(float left, float right, float height, float targetAspect) {
		if (height <= 0.0F || targetAspect <= 0.0F || right <= left) {
			return 0.0F;
		}
		float fullArc = this.arcLengthPx(left, right);
		float targetWidth = height * targetAspect;
		if (targetWidth >= fullArc) {
			return 0.0F;
		}
		float lo = 0.0F;
		float hi = (right - left) * 0.5F;
		for (int i = 0; i < 24; i++) {
			float mid = (lo + hi) * 0.5F;
			float arc = this.arcLengthPx(left + mid, right - mid);
			if (arc > targetWidth) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) * 0.5F;
	}
}
