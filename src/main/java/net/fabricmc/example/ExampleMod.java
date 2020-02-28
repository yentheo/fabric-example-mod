package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.registry.CommandRegistry;
import net.minecraft.server.command.CommandManager;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

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
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		CommandRegistry.INSTANCE.register(false, dispatcher -> {
			dispatcher.register(CommandManager.literal("restream")
					.then(CommandManager.argument("code", greedyString()).executes(ctx -> {
						String code = StringArgumentType.getString(ctx, "code");
						String name = ctx.getSource().getName();
						String bodyContent = "client_id=<insert-client-id>&client_secret=<insert-client-secret>&grant_type=authorization_code&redirect_uri=http://localhost&code="
								+ code;
						System.out.println("Getting tokens for " + ctx.getSource().getName());
						try {
							AuthorizeResponse authorizeResponse = getAuthorizeResponse(bodyContent, name);
							System.out.println("Token " + authorizeResponse.access_token);
							ObjectMapper om = new ObjectMapper(new JsonFactory());
							File file = new File("refresh-tokens.json");
							List<RefreshTokenEntry> tokens = new ArrayList<RefreshTokenEntry>();
							if (file.exists()) {
								try {
									tokens = om.readValue(file, new TypeReference<List<RefreshTokenEntry>>() {
									});
								} catch (Exception e) {
									file.createNewFile();
								}
							} else {
								file.createNewFile();
							}

							RefreshTokenEntry current = null;
							for (RefreshTokenEntry refreshTokenEntry : tokens) {
								if (refreshTokenEntry.name == ctx.getSource().getName()) {
									current = refreshTokenEntry;
									current.refreshToken = authorizeResponse.refresh_token;
								}
							}

							if (current == null) {
								current = new RefreshTokenEntry();
								current.refreshToken = authorizeResponse.refresh_token;
								current.name = ctx.getSource().getName();
								tokens.add(current);
							}

							om.writeValue(file, tokens);
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
							ObjectMapper om = new ObjectMapper(new JsonFactory());
							File file = new File("refresh-tokens.json");
							List<RefreshTokenEntry> tokens = new ArrayList<RefreshTokenEntry>();
							if (file.exists()) {
								tokens = om.readValue(file, new TypeReference<List<RefreshTokenEntry>>() {
								});
							}
							String name = ctx.getSource().getName();
							RefreshTokenEntry current = null;
							System.out.println("Searching token for: " + ctx.getSource().getName());
							for (RefreshTokenEntry refreshTokenEntry : tokens) {
								System.out.println(refreshTokenEntry.name);
								if (refreshTokenEntry.name.equals(ctx.getSource().getName())) {
									current = refreshTokenEntry;
								}
							}
							if (current != null) {
								String bodyContent = "client_id=<insert-client-id>&client_secret=<insert-client-secret>&grant_type=refresh_token&refresh_token="
										+ current.refreshToken;
								AuthorizeResponse authorizeResponse = getAuthorizeResponse(bodyContent, name);
								startListen(authorizeResponse.access_token, (author, message) -> {
	
									ctx.getSource().getMinecraftServer().getPlayerManager()
											.broadcastChatMessage(new LiteralText(author + ": " + message), true);
								});
							} else {
								System.out.println("nothing");
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
