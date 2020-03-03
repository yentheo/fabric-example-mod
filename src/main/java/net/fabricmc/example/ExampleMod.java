package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.example.restream.*;
import net.fabricmc.example.configuration.*;
import net.fabricmc.fabric.api.registry.CommandRegistry;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

public class ExampleMod implements ModInitializer {
	ConfigurationManager configurationManager = new ConfigurationManager();
	RestreamClient restreamClient = new RestreamClient(configurationManager);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		CommandRegistry.INSTANCE.register(false, dispatcher -> {
			dispatcher.register(literal("setrestreamcredentials")
					.then(argument("client_id", string()).then(argument("client_secret", string()).executes(context -> {
						String clientId = StringArgumentType.getString(context, "client_id");
						String clientSecret = StringArgumentType.getString(context, "client_secret");
						configurationManager.saveCredentials(clientId, clientSecret);
						return 1;
					}))));
			dispatcher.register(literal("restream").then(argument("code", greedyString()).executes(ctx -> {
				String code = StringArgumentType.getString(ctx, "code");
				String name = ctx.getSource().getName();
				System.out.println("Getting tokens for " + ctx.getSource().getName());
				AuthorizeResponse authorizeResponse = restreamClient.authorize(code);
				if (authorizeResponse != null) {
					if (authorizeResponse.access_token != null) {
						if (authorizeResponse.refresh_token != null) {
							configurationManager.saveRefreshToken(name, authorizeResponse.refresh_token);
							ctx.getSource().sendFeedback(new LiteralText("Saved refresh token.").formatted(Formatting.GRAY), false);
						}
						restreamClient.startListen(authorizeResponse.access_token, () -> {
							ctx.getSource().sendFeedback(new LiteralText("Connected to Restream").formatted(Formatting.DARK_GREEN), false);
						}, (author, message) -> {
							ctx.getSource().getMinecraftServer().getPlayerManager()
									.broadcastChatMessage(new LiteralText(author + ": " + message), true);
						});
					} else {
						ctx.getSource()
								.sendFeedback(new LiteralText("Couldn't authorize, make sure the code is correct.")
										.formatted(Formatting.RED), false);
					}
				} else {
					ctx.getSource().sendFeedback(new LiteralText("Couldn't authorize, make sure the code is correct.")
							.formatted(Formatting.RED), false);
				}
				return 1;
			})).executes(ctx -> {
				String name = ctx.getSource().getName();
				AuthorizeResponse authorizeResponse = restreamClient.refreshAuthorizationFor(name);
				if (authorizeResponse != null) {
					if (authorizeResponse.refresh_token != null) {
						configurationManager.saveRefreshToken(name, authorizeResponse.refresh_token);
						ctx.getSource().sendFeedback(new LiteralText("Saved refresh token.").formatted(Formatting.GRAY), false);
					}
					if (authorizeResponse.access_token != null) {
						restreamClient.startListen(authorizeResponse.access_token, () -> {
							ctx.getSource().sendFeedback(new LiteralText("Connected to Restream").formatted(Formatting.DARK_GREEN), false);
						}, (author, message) -> {
							ctx.getSource().getMinecraftServer().getPlayerManager()
									.broadcastChatMessage(new LiteralText(author + ": " + message), true);
						});

					} else {
						ctx.getSource().sendFeedback(new LiteralText("Couldn't authorize, try re-authorizing with a code")
								.formatted(Formatting.RED), false);
					}
				} else {
					ctx.getSource().sendFeedback(new LiteralText("Couldn't authorize, try re-authorizing with a code")
							.formatted(Formatting.RED), false);
					System.out.println("no refresh token for current user");
				}
				return 1;
			}));
		});
	}
}
