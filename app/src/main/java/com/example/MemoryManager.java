package com.example;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.model.ChatMessage;
import com.example.model.MemoryItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local SQLite + SharedPreferences persistence manager for SUBH AI.
 * Stores user-approved memories, conversation history, voice preferences,
 * theme settings, and backend configuration without Firebase.
 */
public class MemoryManager extends SQLiteOpenHelper {

    private static final String DB_NAME = "subh_ai_local.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String TABLE_MEMORIES = "memories";

    private static final String PREFS_NAME = "subh_ai_prefs";
    private static final String KEY_MEMORY_ENABLED = "memory_enabled";
    private static final String KEY_CONVERSATION_MODE = "conversation_mode_enabled";
    private static final String KEY_WAKE_WORD_ENABLED = "wake_word_enabled";
    private static final String KEY_LANGUAGE_MODE = "language_mode";
    private static final String KEY_VOICE_NAME = "voice_name";
    private static final String KEY_VOICE_SPEED = "voice_speed";
    private static final String KEY_VOICE_PITCH = "voice_pitch";
    private static final String KEY_VOICE_VOLUME = "voice_volume";
    private static final String KEY_DARK_THEME = "dark_theme_enabled";
    private static final String KEY_BACKEND_URL = "secure_backend_url";

    public static final String LANG_AUTO = "auto";
    public static final String LANG_HINDI = "hi-IN";
    public static final String LANG_ENGLISH = "en-US";
    public static final String LANG_ENGLISH_IN = "en-IN";

    private final SharedPreferences prefs;

    public MemoryManager(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "role TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "timestamp INTEGER NOT NULL," +
                "language TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_MEMORIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "fact TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEMORIES);
        onCreate(db);
    }

    // --- Chat History ---

    public synchronized long saveMessage(ChatMessage message) {
        if (message == null || message.getContent() == null) return -1L;
        if (!isMemoryEnabled() && !ChatMessage.ROLE_SYSTEM.equals(message.getRole())) {
            return -1L;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("role", message.getRole());
        cv.put("content", message.getContent());
        cv.put("timestamp", message.getTimestamp());
        cv.put("language", message.getLanguage());
        long id = db.insert(TABLE_MESSAGES, null, cv);
        message.setId(id);
        return id;
    }

    public synchronized List<ChatMessage> getAllMessages() {
        List<ChatMessage> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_MESSAGES,
                new String[]{"id", "role", "content", "timestamp", "language"},
                null, null, null, null, "timestamp ASC, id ASC")) {
            while (c.moveToNext()) {
                list.add(new ChatMessage(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        c.getLong(3),
                        c.getString(4)
                ));
            }
        }
        return list;
    }

    public synchronized List<ChatMessage> getRecentContextMessages(int limit) {
        List<ChatMessage> list = new ArrayList<>();
        if (!isMemoryEnabled()) {
            return list;
        }
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_MESSAGES,
                new String[]{"id", "role", "content", "timestamp", "language"},
                "role != ?",
                new String[]{ChatMessage.ROLE_SYSTEM},
                null, null, "timestamp DESC, id DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                list.add(new ChatMessage(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        c.getLong(3),
                        c.getString(4)
                ));
            }
        }
        Collections.reverse(list);
        return list;
    }

    public synchronized void clearChatHistory() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MESSAGES, null, null);
    }

    // --- Saved Memories ---

    public synchronized long saveMemory(String fact, String category) {
        if (!isMemoryEnabled() || fact == null || fact.trim().isEmpty()) {
            return -1L;
        }
        String trimmed = fact.trim();
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor c = db.query(TABLE_MEMORIES, new String[]{"id"}, "LOWER(fact) = LOWER(?)",
                new String[]{trimmed}, null, null, null)) {
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
        }
        ContentValues cv = new ContentValues();
        cv.put("fact", trimmed);
        cv.put("category", (category == null || category.trim().isEmpty()) ? "preference" : category.trim());
        cv.put("created_at", System.currentTimeMillis());
        return db.insert(TABLE_MEMORIES, null, cv);
    }

    public synchronized List<MemoryItem> getAllMemories() {
        List<MemoryItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_MEMORIES,
                new String[]{"id", "fact", "category", "created_at"},
                null, null, null, null, "created_at DESC, id DESC")) {
            while (c.moveToNext()) {
                list.add(new MemoryItem(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        c.getLong(3)
                ));
            }
        }
        return list;
    }

    public synchronized void deleteMemory(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MEMORIES, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized boolean deleteMemoryMatching(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE_MEMORIES, "LOWER(fact) LIKE ?",
                new String[]{"%" + query.trim().toLowerCase() + "%"});
        return rows > 0;
    }

    public synchronized void clearAllMemories() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MEMORIES, null, null);
    }

    public synchronized void resetAllLocalData() {
        clearChatHistory();
        clearAllMemories();
        prefs.edit().clear().apply();
    }

    // --- Settings & Preferences ---

    public boolean isMemoryEnabled() {
        return prefs.getBoolean(KEY_MEMORY_ENABLED, true);
    }

    public void setMemoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply();
    }

    public boolean isConversationModeEnabled() {
        return prefs.getBoolean(KEY_CONVERSATION_MODE, true);
    }

    public void setConversationModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CONVERSATION_MODE, enabled).apply();
    }

    public boolean isWakeWordEnabled() {
        return prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false);
    }

    public void setWakeWordEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply();
    }

    public String getLanguageMode() {
        return prefs.getString(KEY_LANGUAGE_MODE, LANG_AUTO);
    }

    public void setLanguageMode(String langMode) {
        prefs.edit().putString(KEY_LANGUAGE_MODE, langMode).apply();
    }

    public String getSelectedVoiceName() {
        return prefs.getString(KEY_VOICE_NAME, "");
    }

    public void setSelectedVoiceName(String voiceName) {
        prefs.edit().putString(KEY_VOICE_NAME, voiceName == null ? "" : voiceName).apply();
    }

    public float getVoiceSpeed() {
        return prefs.getFloat(KEY_VOICE_SPEED, 0.94f);
    }

    public void setVoiceSpeed(float speed) {
        prefs.edit().putFloat(KEY_VOICE_SPEED, Math.max(0.5f, Math.min(1.8f, speed))).apply();
    }

    public float getVoicePitch() {
        return prefs.getFloat(KEY_VOICE_PITCH, 1.00f);
    }

    public void setVoicePitch(float pitch) {
        prefs.edit().putFloat(KEY_VOICE_PITCH, Math.max(0.6f, Math.min(1.6f, pitch))).apply();
    }

    public float getVoiceVolume() {
        return prefs.getFloat(KEY_VOICE_VOLUME, 1.0f);
    }

    public void setVoiceVolume(float volume) {
        prefs.edit().putFloat(KEY_VOICE_VOLUME, Math.max(0.1f, Math.min(1.0f, volume))).apply();
    }

    public boolean isDarkThemeEnabled() {
        return prefs.getBoolean(KEY_DARK_THEME, true);
    }

    public void setDarkThemeEnabled(boolean dark) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply();
    }

    public String getBackendUrl() {
        String saved = prefs.getString(KEY_BACKEND_URL, "");
        if (saved == null || saved.trim().isEmpty()) {
            return BuildConfig.DEFAULT_BACKEND_URL;
        }
        return saved.trim();
    }

    public void setBackendUrl(String url) {
        prefs.edit().putString(KEY_BACKEND_URL, url == null ? "" : url.trim()).apply();
    }
}
