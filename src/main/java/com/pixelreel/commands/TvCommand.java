package com.pixelreel.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.channels.Channel;
import com.pixelreel.channels.ChannelService;
import com.pixelreel.channels.GuideInfo;
import com.pixelreel.jellyfin.JellyfinService;
import com.pixelreel.jellyfin.JellyfinStatus;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.networking.ScreenAction;
import com.pixelreel.networking.ServerNetworking;
import com.pixelreel.permissions.CinemaPermissions;
import com.pixelreel.server.ScreenControllerLogic;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public final class TvCommand {
	private static final int CHANNEL_LIST_LIMIT = 40;
	private static final DateTimeFormatter GUIDE_TIME = DateTimeFormatter.ofPattern("HH:mm");

	private TvCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> tv = Commands.literal("tv")
			.then(Commands.literal("menu").executes(TvCommand::menu))
			.then(Commands.literal("channels").executes(TvCommand::channels))
			.then(Commands.literal("channel")
				.then(Commands.argument("channel", StringArgumentType.greedyString()).executes(TvCommand::channel)))
			.then(Commands.literal("next").executes(context -> control(context, ScreenAction.CHANNEL_NEXT)))
			.then(Commands.literal("previous").executes(context -> control(context, ScreenAction.CHANNEL_PREVIOUS)))
			.then(Commands.literal("guide").executes(TvCommand::guide))
			.then(Commands.literal("status").executes(TvCommand::status))
			.then(Commands.literal("retry").executes(TvCommand::retry))
			.then(Commands.literal("reload").executes(TvCommand::reload))
			.then(Commands.literal("stop").executes(context -> control(context, ScreenAction.STOP)))
			.then(Commands.literal("resume").executes(context -> control(context, ScreenAction.RESUME)))
			.then(Commands.literal("power")
				.then(Commands.literal("on").executes(context -> control(context, ScreenAction.POWER_ON)))
				.then(Commands.literal("off").executes(context -> control(context, ScreenAction.POWER_OFF)))
				.then(Commands.literal("toggle").executes(context -> control(context, ScreenAction.POWER_TOGGLE))))
			.then(Commands.literal("volume")
				.then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(TvCommand::volume)))
			.then(Commands.literal("rebuild").executes(TvCommand::rebuild))
			.then(Commands.literal("jellyfin")
				.then(Commands.literal("status").executes(TvCommand::jellyfinStatus))
				.then(Commands.literal("refresh").executes(TvCommand::jellyfinRefresh))
				.then(Commands.literal("configure").executes(TvCommand::jellyfinConfigure)))
			.executes(TvCommand::status);
		dispatcher.register(tv);
	}

	private static int menu(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("command.pixelreel.players_only"));
			return 0;
		}
		DisplayBlockEntity display = lookedAtDisplay(source);
		if (display == null) {
			source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
			return 0;
		}
		ServerPlayNetworking.send(player, new ModNetworkPayloads.OpenMenu(display.getBlockPos()));
		return 1;
	}

	private static int channels(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		ChannelService.INSTANCE.channels(false).whenComplete((channels, error) -> server.execute(() -> {
			if (channels == null || channels.isEmpty()) {
				source.sendFailure(Component.translatable("command.pixelreel.channels.none", ChannelService.INSTANCE.lastStatus().detail()));
				return;
			}
			source.sendSuccess(() -> Component.translatable("command.pixelreel.channels.header", channels.size()).withStyle(ChatFormatting.AQUA), false);
			int shown = Math.min(channels.size(), CHANNEL_LIST_LIMIT);
			for (int i = 0; i < shown; i++) {
				source.sendSuccess(channelLine(channels.get(i)), false);
			}
			if (channels.size() > shown) {
				int remaining = channels.size() - shown;
				source.sendSuccess(() -> Component.translatable("command.pixelreel.channels.more", remaining).withStyle(ChatFormatting.GRAY), false);
			}
		}));
		return 1;
	}

	private static Supplier<Component> channelLine(Channel channel) {
		return () -> {
			MutableComponent line = Component.literal(" ")
				.append(Component.literal(String.valueOf(channel.number())).withStyle(ChatFormatting.GOLD))
				.append(Component.literal("  "))
				.append(Component.literal(channel.name()).withStyle(ChatFormatting.WHITE));
			String command = "/tv channel " + channel.number();
			return line.withStyle(
				style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("command.pixelreel.channels.click", channel.name())))
			);
		};
	}

	private static int channel(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String query = StringArgumentType.getString(context, "channel").trim();
		DisplayBlockEntity display = lookedAtDisplay(source);
		if (display == null) {
			source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
			return 0;
		}

		Optional<Channel> direct = ChannelService.INSTANCE.resolve(query);
		if (direct.isPresent()) {
			return tune(source, display, direct.get());
		}

		MinecraftServer server = source.getServer();
		var pos = display.getBlockPos();
		var level = display.getLevel();
		ChannelService.INSTANCE.channels(false).whenComplete((channels, error) -> server.execute(() -> {
			DisplayBlockEntity current = DisplayBlock.displayAt(level, pos);
			if (current == null) {
				source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
				return;
			}
			Optional<Channel> resolved = ChannelService.INSTANCE.resolve(query);
			if (resolved.isEmpty()) {
				source.sendFailure(Component.translatable("command.pixelreel.unknown_channel", query));
			} else {
				tune(source, current, resolved.get());
			}
		}));
		return 1;
	}

	private static int tune(CommandSourceStack source, DisplayBlockEntity display, Channel channel) {
		display.tuneTo(channel);
		display.setPowered(true);
		source.sendSuccess(
			() -> Component.translatable("command.pixelreel.playing", channel.number(), channel.name()).withStyle(ChatFormatting.GREEN), false
		);
		return 1;
	}

	private static int control(CommandContext<CommandSourceStack> context, ScreenAction action) {
		CommandSourceStack source = context.getSource();
		DisplayBlockEntity display = lookedAtDisplay(source);
		if (display == null) {
			source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
			return 0;
		}
		ScreenControllerLogic.Outcome outcome = ScreenControllerLogic.apply(display, action, 0.0F);
		switch (outcome) {
			case OK -> source.sendSuccess(() -> feedbackFor(action, display), false);
			case NO_CHANNELS -> source.sendFailure(Component.translatable("message.pixelreel.no_channels"));
			case UNKNOWN_CHANNEL -> source.sendFailure(Component.translatable("message.pixelreel.unknown_channel"));
			case NO_SCREEN -> source.sendFailure(Component.translatable("message.pixelreel.no_screen"));
		}
		return outcome == ScreenControllerLogic.Outcome.OK ? 1 : 0;
	}

	private static Component feedbackFor(ScreenAction action, DisplayBlockEntity display) {
		return switch (action) {
			case POWER_ON, POWER_OFF, POWER_TOGGLE -> Component.translatable(
				display.isPowered() ? "command.pixelreel.power.now_on" : "command.pixelreel.power.now_off"
			).withStyle(ChatFormatting.GREEN);
			case STOP -> Component.translatable("command.pixelreel.stopped").withStyle(ChatFormatting.GREEN);
			case RESUME -> Component.translatable("command.pixelreel.resumed").withStyle(ChatFormatting.GREEN);
			case CHANNEL_NEXT, CHANNEL_PREVIOUS -> Component.translatable(
				"command.pixelreel.playing", display.getChannelNumber(), display.getChannelName()
			).withStyle(ChatFormatting.GREEN);
			default -> Component.translatable("command.pixelreel.done").withStyle(ChatFormatting.GREEN);
		};
	}

	private static int volume(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		int percent = IntegerArgumentType.getInteger(context, "percent");
		DisplayBlockEntity display = lookedAtDisplay(source);
		if (display == null) {
			source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
			return 0;
		}
		display.setVolume(percent / 100.0F);
		source.sendSuccess(() -> Component.translatable("command.pixelreel.volume", percent).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int guide(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		ChannelService.INSTANCE.channels(false).whenComplete((channels, error) -> server.execute(() -> {
			if (channels == null || channels.isEmpty()) {
				source.sendFailure(Component.translatable("command.pixelreel.channels.none", ChannelService.INSTANCE.lastStatus().detail()));
				return;
			}
			source.sendSuccess(() -> Component.translatable("command.pixelreel.guide.header").withStyle(ChatFormatting.AQUA), false);
			int shown = Math.min(channels.size(), CHANNEL_LIST_LIMIT);
			long now = System.currentTimeMillis() / 1000L;
			for (int i = 0; i < shown; i++) {
				Channel channel = channels.get(i);
				GuideInfo guide = ChannelService.INSTANCE.guideFor(channel.id());
				source.sendSuccess(() -> guideLine(channel, guide, now), false);
			}
		}));
		return 1;
	}

	private static Component guideLine(Channel channel, GuideInfo guide, long now) {
		MutableComponent line = Component.literal(" ")
			.append(Component.literal(String.valueOf(channel.number())).withStyle(ChatFormatting.GOLD))
			.append(Component.literal("  "))
			.append(Component.literal(channel.name()).withStyle(ChatFormatting.WHITE));
		if (guide.hasNow()) {
			String window = formatTime(guide.nowStart()) + "-" + formatTime(guide.nowEnd());
			line.append(Component.literal("  "))
				.append(Component.translatable("command.pixelreel.guide.now", guide.nowTitle(), window).withStyle(ChatFormatting.GREEN));
			float progress = guide.progressAt(now);
			if (progress >= 0.0F) {
				line.append(Component.literal(" (" + Math.round(progress * 100.0F) + "%)").withStyle(ChatFormatting.DARK_GREEN));
			}
			if (!guide.nowEpisode().isEmpty()) {
				line.append(Component.literal(" - " + guide.nowEpisode()).withStyle(ChatFormatting.GRAY));
			}
		}
		if (guide.hasNext()) {
			line.append(Component.literal("  "))
				.append(Component.translatable("command.pixelreel.guide.next", guide.nextTitle(), formatTime(guide.nextStart())).withStyle(ChatFormatting.GRAY));
		}
		return line;
	}

	private static String formatTime(long epochSeconds) {
		return GUIDE_TIME.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()));
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		DisplayBlockEntity display = lookedAtDisplay(source);
		if (display == null) {
			var status = ChannelService.INSTANCE.lastStatus();
			if (status.reachable()) {
				source.sendSuccess(() -> Component.translatable("command.pixelreel.status.source_online", status.channelCount()).withStyle(ChatFormatting.GREEN), false);
			} else {
				source.sendSuccess(() -> Component.translatable("command.pixelreel.status.source_offline", status.detail()).withStyle(ChatFormatting.RED), false);
			}
			source.sendSuccess(() -> Component.translatable("command.pixelreel.no_screen_in_sight").withStyle(ChatFormatting.GRAY), false);
			return 1;
		}

		source.sendSuccess(() -> Component.translatable("command.pixelreel.status.header").withStyle(ChatFormatting.AQUA), false);
		source.sendSuccess(() -> statusLine("command.pixelreel.status.display", Component.translatable(display.getBlockState().getBlock().getDescriptionId())), false);
		source.sendSuccess(
			() -> statusLine("command.pixelreel.status.power", Component.translatable(display.isPowered()
				? (display.isSuspended() ? "command.pixelreel.status.power.suspended" : "command.pixelreel.status.power.on")
				: "command.pixelreel.status.power.off")),
			false
		);
		if (display.hasChannel()) {
			source.sendSuccess(
				() -> statusLine("command.pixelreel.status.channel", Component.literal(display.getChannelNumber() + " - " + display.getChannelName())), false
			);
			source.sendSuccess(
				() -> statusLine("command.pixelreel.status.stream_host", Component.literal(ChannelService.hostOnly(display.getStreamUrl()))), false
			);
			GuideInfo guide = ChannelService.INSTANCE.guideFor(display.getChannelId());
			if (guide.hasNow()) {
				source.sendSuccess(() -> statusLine("command.pixelreel.status.programme", Component.literal(guide.nowTitle())), false);
			}
		} else {
			source.sendSuccess(() -> statusLine("command.pixelreel.status.channel", Component.translatable("command.pixelreel.status.no_channel")), false);
		}
		source.sendSuccess(() -> statusLine("command.pixelreel.status.volume", Component.literal(Math.round(display.getVolume() * 100.0F) + "%")), false);

		ServerPlayer player = source.getPlayer();
		if (player != null) {
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ShowClientStatus(display.getBlockPos()));
		}
		return 1;
	}

	private static Component statusLine(String key, Component value) {
		return Component.literal(" ").append(Component.translatable(key).withStyle(ChatFormatting.GRAY)).append(" ").append(value.copy().withStyle(ChatFormatting.WHITE));
	}

	private static int retry(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("command.pixelreel.players_only"));
			return 0;
		}
		DisplayBlockEntity display = lookedAtDisplay(source);
		if (display == null) {
			source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
			return 0;
		}
		ServerPlayNetworking.send(player, new ModNetworkPayloads.RetryDisplay(display.getBlockPos()));
		source.sendSuccess(() -> Component.translatable("command.pixelreel.retrying").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> Component.translatable("command.pixelreel.reload.started"), false);
		ChannelService.INSTANCE.channels(true).whenComplete((channels, error) -> server.execute(() -> {
			if (error == null && channels != null && !channels.isEmpty()) {
				source.sendSuccess(() -> Component.translatable("command.pixelreel.reload.done", channels.size()).withStyle(ChatFormatting.GREEN), false);
			} else {
				source.sendFailure(Component.translatable("command.pixelreel.reload.failed", ChannelService.INSTANCE.lastStatus().detail()));
			}
			for (ServerPlayer online : server.getPlayerList().getPlayers()) {
				ServerNetworking.sendChannelListNow(online);
			}
		}));
		return 1;
	}

	private static int jellyfinStatus(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		JellyfinStatus status = JellyfinService.INSTANCE.lastStatus();
		source.sendSuccess(
			() -> Component.translatable(
				"command.pixelreel.jellyfin.status",
				status.configured()
					? (status.authenticated()
						? status.movieCount() + " movies / " + status.seriesCount() + " shows — " + status.detail()
						: status.detail())
					: "not configured"
			).withStyle(status.authenticated() ? ChatFormatting.GREEN : ChatFormatting.RED),
			false
		);
		return 1;
	}

	private static int jellyfinRefresh(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("command.pixelreel.players_only"));
			return 0;
		}
		if (!CinemaPermissions.canRefreshLibrary(player)) {
			source.sendFailure(Component.translatable("command.pixelreel.no_permission"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.pixelreel.jellyfin.refresh.started"), false);
		MinecraftServer server = source.getServer();
		JellyfinService.INSTANCE.refresh(true).whenComplete((status, error) -> server.execute(() -> {
			if (status != null && status.authenticated()) {
				source.sendSuccess(
					() -> Component.translatable("command.pixelreel.jellyfin.refresh.done", status.movieCount(), status.seriesCount())
						.withStyle(ChatFormatting.GREEN),
					false
				);
			} else {
				source.sendFailure(Component.translatable(
					"command.pixelreel.jellyfin.refresh.failed",
					status == null ? "unknown error" : status.detail()
				));
			}
		}));
		return 1;
	}

	private static int jellyfinConfigure(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("command.pixelreel.players_only"));
			return 0;
		}
		if (!CinemaPermissions.canConfigureJellyfin(player)) {
			source.sendFailure(Component.translatable("command.pixelreel.no_permission"));
			return 0;
		}
		DisplayBlockEntity display = lookedAtDisplay(source);
		BlockPos pos = display == null ? player.blockPosition() : display.getBlockPos();
		ServerPlayNetworking.send(player, new ModNetworkPayloads.OpenMenu(pos));
		ServerNetworking.sendMediaFeatures(player);
		return 1;
	}

	private static int rebuild(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		DisplayBlockEntity display = lookedAtDisplay(source);
		if (display == null) {
			source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
			return 0;
		}
		DisplayBlock block = display.displayBlock();
		if (block == null) {
			source.sendFailure(Component.translatable("command.pixelreel.no_screen_in_sight"));
			return 0;
		}
		int placed = DisplayBlock.placePanels(display.getLevel(), display.getBlockPos(), block.type(), display.facing(), false);
		source.sendSuccess(() -> Component.translatable("command.pixelreel.rebuild.done", placed).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static @Nullable DisplayBlockEntity lookedAtDisplay(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return null;
		}
		HitResult hit = player.pick(48.0, 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return DisplayBlock.controllerAt(player.level(), blockHit.getBlockPos());
		}
		return null;
	}
}
