package com.pixelreel.client.playback.video;

import com.mojang.blaze3d.platform.NativeImage;


public final class NativeImageAccess {
	private NativeImageAccess() {
	}

	public static long pointer(NativeImage image) {
		long address = image.pixels;
		if (address == 0L) {
			throw new IllegalStateException("NativeImage pixel pointer is null");
		}
		return address;
	}
}
