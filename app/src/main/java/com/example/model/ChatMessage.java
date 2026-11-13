package com.example.model;

public class ChatMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    private long id;
    private final String role;
    private final String content;
    private final long timestamp;
    private final String language;

    public ChatMessage(long id, String role, String content, long timestamp, String language) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.language = language;
    }

    public ChatMessage(String role, String content, long timestamp, String language) {
        this(-1L, role, content, timestamp, language);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getLanguage() {
        return language;
    }
}
