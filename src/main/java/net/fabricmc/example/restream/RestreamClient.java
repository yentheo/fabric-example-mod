package net.fabricmc.example.restream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;

import net.fabricmc.example.OnChatMessageHandler;
import net.fabricmc.example.OnConnectHandler;
import net.fabricmc.example.SimpleClient;
import net.fabricmc.example.configuration.ConfigurationManager;
import net.fabricmc.example.configuration.RestreamConfiguration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.stream.Collectors;

public class RestreamClient {
    private ConfigurationManager _configurationManager;

    public RestreamClient(ConfigurationManager configurationManager) {
        _configurationManager = configurationManager;
    }

    public AuthorizeResponse authorize(String code) {
        System.out.println("Authorizing with code");
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("grant_type", "authorization_code");
        parameters.put("redirect_uri", "http://localhost");
        parameters.put("code", code);
        return authorize(parameters);
    }

    public AuthorizeResponse refreshAuthorizationFor(String name) {
        System.out.println("Authorizing with refresh token");
        String refreshToken = _configurationManager.getRefreshToken(name);
        if (refreshToken != null) {
            HashMap<String, String> parameters = new HashMap<String, String>();
            parameters.put("grant_type", "refresh_token");
            parameters.put("refresh_token", refreshToken);
            return authorize(parameters);
        }
        return null;
    }

    public void startListen(String accessToken, OnConnectHandler onConnectHandler, OnChatMessageHandler handler) {
        SimpleClient client;
        try {
            client = new SimpleClient(new URI("wss://api.restream.io/v2/ws"));
            client.registerOnOpen(() -> {
                onConnectHandler.op();
                client.send("{ \"action\": \"subscribe\", \"token\":\"" + accessToken
                        + "\", \"subscriptions\":[\"user/stream\", \"user/chat\"] }");
            });

            client.registerOnMessage(message -> {
                System.out.println(message);
                Gson gson = new Gson();
                if (message != null) {
                    ChatMessage chatMessage = gson.fromJson(message, ChatMessage.class);
                    if (chatMessage.subscription != null && chatMessage.subscription.equals("user/chat")) {
                        String content = "";
                        if (chatMessage.payload != null) {
                            for (Content c : chatMessage.payload.contents) {
                                if ("img".equals(c.type) && "link".equals(c.subtype)) {
                                    content = content + " {img}";
                                } else if ("text".equals(c.type)) {
                                    content = content + " " + c.content;
                                } else if ("link".equals(c.type)) {
                                    content = content + " {" + c.content + "}";
                                } else {
                                    content = content + " {unknown}";
                                }
                            }
                            handler.op(chatMessage.payload.author, content);
                        }
                    }
                }
            });

            client.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private AuthorizeResponse authorize(HashMap<String, String> parameters) {
        RestreamConfiguration configuration = _configurationManager.getConfiguration();
        HashMap<String, String> allParameters = new HashMap<String, String>(parameters);
        allParameters.put("client_id", configuration.clientId);
        allParameters.put("client_secret", configuration.clientSecret);
        System.out.println("Authorizing with client id {" + configuration.clientId + "} and secret {"
                + configuration.clientSecret + "}");
        List<String> keyValues = allParameters.keySet().stream()
                .map(key -> String.format("%s=%s", key, allParameters.get(key))).collect(Collectors.toList());
        String bodyContent = String.join("&", keyValues);
        MediaType formUrl = MediaType.get("application/x-www-form-urlencoded");
        System.out.println("Authorizing with body {" + bodyContent + "}");
        RequestBody body = RequestBody.create(bodyContent, formUrl);
        Request request = new Request.Builder().url("https://api.restream.io/oauth/token").post(body).build();
        OkHttpClient httpClient = new OkHttpClient();
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
}