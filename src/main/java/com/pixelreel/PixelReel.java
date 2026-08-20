package com.pixelreel;

import com.pixelreel.channels.ChannelService;
import com.pixelreel.commands.TvCommand;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.networking.MediaProxy;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.networking.ServerNetworking;
import com.pixelreel.ondemand.OnDemandCatalog;
import com.pixelreel.registry.ModBlockEntities;
import com.pixelreel.registry.ModBlocks;
import com.pixelreel.registry.ModCreativeTabs;
import com.pixelreel.registry.ModItems;
import com.pixelreel.zones.ZoneManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
		ServerNetworking.register();
		TvCommand.register();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("pixelReel configuration file: {}", ConfigManager.path());
			ZoneManager.INSTANCE.load();
			ChannelService.INSTANCE.channels(false);
			OnDemandCatalog.refreshConfigured(false);
			MediaProxy.INSTANCE.start();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MediaProxy.INSTANCE.stop();
			ChannelService.INSTANCE.invalidateCache();
			OnDemandCatalog.invalidateAll();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			ServerNetworking.sendZoneList(handler.getPlayer())
		);
		LOGGER.info("pixelReel initialised");
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
