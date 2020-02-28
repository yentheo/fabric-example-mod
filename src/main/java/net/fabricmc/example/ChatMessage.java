package net.fabricmc.example;

import java.util.List;

class Payload {
    public String author;
    public List<Content> contents;
}

public class ChatMessage {
    public String subscription;
    public Payload payload;
}