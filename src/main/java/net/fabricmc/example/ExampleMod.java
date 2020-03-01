package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.example.restream.*;
import net.fabricmc.example.configuration.*;
import net.fabricmc.fabric.api.registry.CommandRegistry;
import net.minecraft.server.command.CommandManager;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.text.LiteralText;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ExampleMod implements ModInitializer {
	ConfigurationManager configurationManager = new ConfigurationManager();

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
			dispatcher.register(CommandManager.literal("restream")
					.then(CommandManager.argument("code", greedyString()).executes(ctx -> {
						RestreamConfiguration configuration = configurationManager.getConfiguration();
						String code = StringArgumentType.getString(ctx, "code");
						String name = ctx.getSource().getName();
						String bodyContent = "client_id=" + configuration.clientId + "&client_secret="
								+ configuration.clientSecret
								+ "&grant_type=authorization_code&redirect_uri=http://localhost&code=" + code;
						System.out.println("Getting tokens for " + ctx.getSource().getName());
						try {
							AuthorizeResponse authorizeResponse = getAuthorizeResponse(bodyContent, name);
							configurationManager.saveRefreshToken(name, authorizeResponse.refresh_token);
							startListen(authorizeResponse.access_token, (author, message) -> {

								ctx.getSource().getMinecraftServer().getPlayerManager()
										.broadcastChatMessage(new LiteralText(author + ": " + message), true);
							});
						} catch (Exception e) {
							e.printStackTrace();
						}
						return 1;
					})).executes(ctx -> {
						try {
							String name = ctx.getSource().getName();
							RestreamConfiguration configuration = configurationManager.getConfiguration();
							String refreshToken = configurationManager.getRefreshToken(name);
							if (refreshToken != null) {
								String bodyContent = "client_id=" + configuration.clientId + "&client_secret="
										+ configuration.clientSecret + "&grant_type=refresh_token&refresh_token="
										+ refreshToken;
								AuthorizeResponse authorizeResponse = getAuthorizeResponse(bodyContent, name);
								startListen(authorizeResponse.access_token, (author, message) -> {

									ctx.getSource().getMinecraftServer().getPlayerManager()
											.broadcastChatMessage(new LiteralText(author + ": " + message), true);
								});
							} else {
								System.out.println("no refresh token for current user");
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
						return 1;
					}));
		});

	}

	private AuthorizeResponse getAuthorizeResponse(String bodyContent, String name) {
		MediaType formUrl = MediaType.get("application/x-www-form-urlencoded");
		RequestBody body = RequestBody.create(bodyContent, formUrl);
		Request request = new Request.Builder().url("https://api.restream.io/oauth/token").post(body).build();
		OkHttpClient httpClient = new OkHttpClient();
		System.out.println("Getting tokens for " + name);
		try (Response response = httpClient.newCall(request).execute()) {
			String responseBody = response.body().string();
			System.out.println(responseBody);
			Gson gson = new Gson();
			AuthorizeResponse authresponse = (AuthorizeResponse) gson.fromJson(responseBody, AuthorizeResponse.class);
			return authresponse;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private void startListen(String accessToken, OnChatMessageHandler handler) {
		SimpleClient client;
		try {
			client = new SimpleClient(new URI("wss://api.restream.io/v2/ws"));
			client.registerOnOpen(() -> {
				client.send("{ \"action\": \"subscribe\", \"token\":\"" + accessToken
						+ "\", \"subscriptions\":[\"user/stream\", \"user/chat\"] }");
			});

			client.registerOnMessage(message -> {
				System.out.println(message);
				Gson gson = new Gson();
				ChatMessage chatMessage = gson.fromJson(message, ChatMessage.class);
				if (chatMessage.subscription.equals("user/chat")) {
					String content = "";
					for (Content c : chatMessage.payload.contents) {
						content = content + " " + c.content;
					}
					handler.op(chatMessage.payload.author, content);
				}
			});

			client.connect();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
}
