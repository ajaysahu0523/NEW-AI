package com.example;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Android SpeechRecognizer wrapper supporting Hindi, English, Hinglish,
 * extended silence thresholds for complete questions, RMS waveform callbacks,
 * and foreground wake/sleep phrase detection.
 */
public class SpeechRecognition {

    public interface SpeechCallback {
        void onListeningStarted();
        void onRmsChanged(float normalizedLevel);
        void onPartialTranscript(String partialText);
        void onFinalTranscript(String finalText);
        void onSpeechError(int errorCode, String humanReadableError, boolean isTimeoutOrNoMatch);
    }

    private final Context context;
    private final MemoryManager memoryManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private SpeechCallback callback;
    private boolean listening = false;
    private boolean destroyed = false;

    public SpeechRecognition(Context context, MemoryManager memoryManager) {
        this.context = context.getApplicationContext();
        this.memoryManager = memoryManager;
    }

    public void setCallback(SpeechCallback callback) {
        this.callback = callback;
    }

    public boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isRecognitionAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public boolean isListening() {
        return listening;
    }

    public void startListening() {
        mainHandler.post(() -> {
            if (destroyed) return;

            if (!hasMicrophonePermission()) {
                if (callback != null) {
                    callback.onSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                            "Microphone permission is required to listen.", false);
                }
                return;
            }

            if (!isRecognitionAvailable()) {
                if (callback != null) {
                    callback.onSpeechError(-1,
                            "Speech recognition service is not available on this device. Please install or enable Google Speech Services.",
                            false);
                }
                return;
            }

            stopListeningInternal();
            ensureRecognizer();

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

            // Language selection: Indian English handles Hinglish + English well, Hindi handles Devanagari
            String langMode = memoryManager.getLanguageMode();
            String speechLang = MemoryManager.LANG_HINDI.equals(langMode) ? "hi-IN" : "en-IN";
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLang);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLang);

            // Give the user enough pause time to finish complete multi-clause questions
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L);

            try {
                listening = true;
                recognizer.startListening(intent);
            } catch (Exception ex) {
                listening = false;
                if (callback != null) {
                    callback.onSpeechError(-2, "Could not start microphone: " + ex.getMessage(), false);
                }
            }
        });
    }

    public void stopListening() {
        mainHandler.post(this::stopListeningInternal);
    }

    private void stopListeningInternal() {
        listening = false;
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    public void destroy() {
        destroyed = true;
        mainHandler.post(() -> {
            stopListeningInternal();
            if (recognizer != null) {
                try {
                    recognizer.destroy();
                } catch (Exception ignored) {
                }
                recognizer = null;
            }
        });
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                listening = true;
                if (callback != null) callback.onListeningStarted();
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Convert SpeechRecognizer [-2..10 dB] range to 0..1
                float normalized = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
                if (callback != null) callback.onRmsChanged(normalized);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                listening = false;
            }

            @Override
            public void onError(int error) {
                listening = false;
                boolean isNoMatch = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
                if (callback != null) {
                    callback.onSpeechError(error, mapErrorMessage(error), isNoMatch);
                }
            }

            @Override
            public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results != null
                        ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        : null;
                if (matches != null && !matches.isEmpty() && matches.get(0) != null && !matches.get(0).trim().isEmpty()) {
                    if (callback != null) callback.onFinalTranscript(matches.get(0).trim());
                } else {
                    if (callback != null) {
                        callback.onSpeechError(SpeechRecognizer.ERROR_NO_MATCH,
                                "I didn't catch that clearly. Please try again.", true);
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults != null
                        ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        : null;
                if (partial != null && !partial.isEmpty() && partial.get(0) != null) {
                    if (callback != null) callback.onPartialTranscript(partial.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    public static boolean matchesWakePhrase(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.contains("hey subh") || lower.contains("hey shubh")
                || lower.contains("hi subh") || lower.contains("hi shubh")
                || lower.contains("हे शुभ") || lower.contains("हेलो शुभ")
                || lower.equals("subh") || lower.equals("shubh") || lower.equals("शुभ");
    }

    public static boolean matchesSleepPhrase(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.contains("good night subh") || lower.contains("good night shubh")
                || lower.contains("goodnight subh") || lower.contains("goodnight shubh")
                || lower.contains("गुड नाइट शुभ") || lower.contains("शुभ रात्रि शुभ")
                || lower.contains("go to sleep subh");
    }

    private String mapErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error. Check if another app is using the microphone.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Speech client reset. Tap mic to try again.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission is required.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network error during speech recognition. Check your internet connection.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No speech matched. Please speak a little closer to the mic.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Speech recognizer is busy. Please wait a moment.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Speech recognition server error. Please try again.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech detected before timeout.";
            default:
                return "Speech recognition error (" + error + ").";
        }
    }
}
