package com.example.model;

public class MemoryItem {
    private final long id;
    private final String fact;
    private final String category;
    private final long createdAt;

    public MemoryItem(long id, String fact, String category, long createdAt) {
        this.id = id;
        this.fact = fact;
        this.category = category;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getFact() {
        return fact;
    }

    public String getCategory() {
        return category;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
