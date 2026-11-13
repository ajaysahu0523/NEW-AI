package com.example;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Application controller initializing local memory, female TTS voice engine,
 * secure Gemini backend service, and saved Light/Dark theme.
 */
public class AppController extends Application {

    private static AppController instance;

    private MemoryManager memoryManager;
    private TextToSpeechManager ttsManager;
    private GeminiAIService geminiService;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        memoryManager = new MemoryManager(this);
        applySavedTheme();

        ttsManager = new TextToSpeechManager(this, memoryManager);
        geminiService = new GeminiAIService(memoryManager);
    }

    public static AppController getInstance() {
        return instance;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public TextToSpeechManager getTtsManager() {
        return ttsManager;
    }

    public GeminiAIService getGeminiService() {
        return geminiService;
    }

    public void applySavedTheme() {
        boolean dark = memoryManager != null && memoryManager.isDarkThemeEnabled();
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    @Override
    public void onTerminate() {
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
        super.onTerminate();
    }
}
