package net.fabricmc.example;

import java.net.URI;
import java.nio.ByteBuffer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class SimpleClient extends WebSocketClient {

    private OnConnectHandler _onConnectHandler;
    private OnMessageHandler _onMessageHandler;
    private OnDisconnectHandler _onDisconnectHandler;

    public SimpleClient(URI serverURI) {
        super(serverURI);
    }

    public void registerOnOpen(OnConnectHandler handler) {
        _onConnectHandler = handler;
    }

    public void registerOnMessage(OnMessageHandler handler) {
        _onMessageHandler = handler;
    }

    public void registerOnDisconnect(OnDisconnectHandler handler) {
        _onDisconnectHandler = handler;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        _onConnectHandler.op();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("closed with exit code " + code + " additional info: " + reason);
        _onDisconnectHandler.op();;
    }

    @Override
    public void onMessage(String message) {
        _onMessageHandler.op(message);
    }

    @Override
    public void onMessage(ByteBuffer message) {
        System.out.println("received ByteBuffer");
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("an error occurred:" + ex);
    }

}