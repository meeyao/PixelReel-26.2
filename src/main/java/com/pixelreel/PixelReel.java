package com.pixelreel;

import com.pixelreel.channels.ChannelService;
import com.pixelreel.commands.TvCommand;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.networking.ServerNetworking;
import com.pixelreel.ondemand.OnDemandCatalog;
import com.pixelreel.registry.ModBlockEntities;
import com.pixelreel.registry.ModBlocks;
import com.pixelreel.registry.ModCreativeTabs;
import com.pixelreel.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PixelReel implements ModInitializer {
	public static final String MOD_ID = "pixelreel";
	public static final Logger LOGGER = LoggerFactory.getLogger("pixelreel");

	@Override
	public void onInitialize() {
		ConfigManager.load();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModCreativeTabs.init();
		ModNetworkPayloads.register();
		ServerNetworking.register();
		TvCommand.register();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("pixelReel configuration file: {}", ConfigManager.path());
			ChannelService.INSTANCE.channels(false);
			OnDemandCatalog.refreshConfigured(false);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ChannelService.INSTANCE.invalidateCache();
			OnDemandCatalog.invalidateAll();
		});
		LOGGER.info("pixelReel initialised");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
