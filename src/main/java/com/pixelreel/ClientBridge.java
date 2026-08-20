package com.pixelreel;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class ClientBridge {
	private static volatile ClientBridge.@Nullable Handler handler;

	private ClientBridge() {
	}

	public static void setHandler(Handler newHandler) {
		handler = newHandler;
	}

	public static void openChannelMenu(BlockPos controllerPos) {
		Handler current = handler;
		if (current != null) {
			current.openChannelMenu(controllerPos);
		}
	}

	public static void releasePlayback(BlockPos controllerPos) {
		Handler current = handler;
		if (current != null) {
			current.releasePlayback(controllerPos);
		}
	}

	public interface Handler {
		void openChannelMenu(BlockPos controllerPos);

		void releasePlayback(BlockPos controllerPos);
	}
}
