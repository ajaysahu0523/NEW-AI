package com.example;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Upgraded Female Voice TextToSpeech manager for SUBH AI.
 *
 * Key upgrades:
 * - Prefers Google Speech Services engine (com.google.android.tts) when installed.
 * - Strictly detects genuine installed female voices for Hindi (hi-IN), US English (en-US),
 *   and Indian English (en-IN) without faking a female voice by raising pitch.
 * - Automatically detects Hindi, English, and Hinglish text and switches to the best matching
 *   female voice.
 * - Splits long AI answers into natural sentence chunks so complete answers are spoken aloud.
 * - Provides clear installation instructions when a suitable female voice is unavailable.
 */
public class TextToSpeechManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "SubhTTS";
    private static final String GOOGLE_TTS_ENGINE = "com.google.android.tts";

    private static final Set<String> HINGLISH_KEYWORDS = new HashSet<>(Arrays.asList(
            "namaste", "namaskar", "kaise", "kaisa", "kaisi", "kya", "kyu", "kyon",
            "aap", "tum", "main", "hum", "hoon", "hain", "hai", "nahi", "haan",
            "shukriya", "dhanyavaad", "dhanyawad", "achha", "accha", "theek", "thik",
            "batao", "bataiye", "karo", "kijiye", "madad", "sahayata", "subh", "shubh",
            "raat", "subah", "shaam", "kal", "aaj", "abhi", "samay", "tareekh",
            "yaar", "bhai", "dost", "samajh", "zaroor", "bilkul", "chalo", "kholo"
    ));

    public enum SpeechLanguage {
        HINDI,
        ENGLISH_US,
        HINGLISH
    }

    public static class VoiceOption {
        public final Voice voice;
        public final String displayLabel;
        public final boolean genuineFemale;
        public final int score;

        public VoiceOption(Voice voice, String displayLabel, boolean genuineFemale, int score) {
            this.voice = voice;
            this.displayLabel = displayLabel;
            this.genuineFemale = genuineFemale;
            this.score = score;
        }

        @Override
        public String toString() {
            return displayLabel;
        }
    }

    public interface TtsListener {
        void onTtsReady(boolean success, String statusMessage);
        void onSpeechStart(String utteranceId);
        void onSpeechDone(String utteranceId);
        void onSpeechError(String errorMessage);
    }

    private final Context context;
    private final MemoryManager memoryManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<TtsListener> listeners = new ArrayList<>();

    private TextToSpeech tts;
    private boolean initialized = false;
    private boolean speaking = false;
    private boolean hindiSupported = false;
    private boolean englishSupported = false;

    private Voice bestHindiFemaleVoice;
    private Voice bestUsEnglishFemaleVoice;
    private Voice bestIndianEnglishFemaleVoice;

    private final List<VoiceOption> availableVoiceOptions = new ArrayList<>();
    private String lastDiagnosticMessage = "Detecting installed female voices…";
    private String activeFinalUtteranceId = null;

    public TextToSpeechManager(Context context, MemoryManager memoryManager) {
        this.context = context.getApplicationContext();
        this.memoryManager = memoryManager;
        initEngine();
    }

    public void initEngine() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
            initialized = false;
            if (isPackageInstalled(GOOGLE_TTS_ENGINE)) {
                tts = new TextToSpeech(context, this, GOOGLE_TTS_ENGINE);
            } else {
                tts = new TextToSpeech(context, this);
            }
        } catch (Exception ex) {
            try {
                tts = new TextToSpeech(context, this);
            } catch (Exception inner) {
                initialized = false;
                lastDiagnosticMessage = "Unable to start Android Text-to-Speech engine: " + inner.getMessage();
                notifyError(lastDiagnosticMessage);
            }
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public void addListener(TtsListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(TtsListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            initialized = false;
            lastDiagnosticMessage = "Text-to-Speech engine unavailable (status " + status
                    + "). Open Android TTS Settings and select Speech Services by Google.";
            notifyReady(false, lastDiagnosticMessage);
            return;
        }

        initialized = true;
        setupProgressListener();
        refreshAvailableVoices();
        applySavedVoiceParameters();

        notifyReady(true, lastDiagnosticMessage);
    }

    private void setupProgressListener() {
        if (tts == null) return;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                speaking = true;
                mainHandler.post(() -> {
                    for (TtsListener l : new ArrayList<>(listeners)) {
                        l.onSpeechStart(utteranceId);
                    }
                });
            }

            @Override
            public void onDone(String utteranceId) {
                // Only signal completion after the final chunk of a multi-chunk answer finishes
                if (activeFinalUtteranceId != null && !activeFinalUtteranceId.equals(utteranceId)) {
                    return;
                }
                speaking = false;
                activeFinalUtteranceId = null;
                mainHandler.post(() -> {
                    for (TtsListener l : new ArrayList<>(listeners)) {
                        l.onSpeechDone(utteranceId);
                    }
                });
            }

            @Override
            @Deprecated
            public void onError(String utteranceId) {
                handleUtteranceError("Voice playback failed for utterance.");
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                handleUtteranceError("Voice synthesis error (code " + errorCode
                        + "). Please download Hindi & English female voice data in Android Text-to-Speech Settings.");
            }
        });
    }

    private void handleUtteranceError(String msg) {
        speaking = false;
        activeFinalUtteranceId = null;
        mainHandler.post(() -> {
            for (TtsListener l : new ArrayList<>(listeners)) {
                l.onSpeechError(msg);
            }
        });
    }

    public synchronized void refreshAvailableVoices() {
        availableVoiceOptions.clear();
        bestHindiFemaleVoice = null;
        bestUsEnglishFemaleVoice = null;
        bestIndianEnglishFemaleVoice = null;

        if (tts == null || !initialized) return;

        Locale hiLocale = new Locale("hi", "IN");
        int hiAvail = tts.isLanguageAvailable(hiLocale);
        int enUsAvail = tts.isLanguageAvailable(Locale.US);
        int enInAvail = tts.isLanguageAvailable(new Locale("en", "IN"));

        hindiSupported = hiAvail >= TextToSpeech.LANG_AVAILABLE;
        englishSupported = enUsAvail >= TextToSpeech.LANG_AVAILABLE || enInAvail >= TextToSpeech.LANG_AVAILABLE;

        Set<Voice> voices = null;
        try {
            voices = tts.getVoices();
        } catch (Exception e) {
            Log.w(TAG, "Unable to enumerate TTS voices", e);
        }

        if (voices != null && !voices.isEmpty()) {
            int bestHiScore = Integer.MIN_VALUE;
            int bestUsScore = Integer.MIN_VALUE;
            int bestInScore = Integer.MIN_VALUE;

            for (Voice v : voices) {
                if (v == null || v.getLocale() == null) continue;
                String lang = v.getLocale().getLanguage();
                if (!"hi".equalsIgnoreCase(lang) && !"en".equalsIgnoreCase(lang)) {
                    continue;
                }

                boolean isFemale = isGenuineFemaleVoice(v);
                int score = scoreVoice(v, isFemale);
                String tag = isFemale ? "★ Female" : (isKnownMaleVoice(v) ? "Male" : "Voice");
                String quality = v.getQuality() >= Voice.QUALITY_HIGH ? "HQ" : "Std";
                String net = v.isNetworkConnectionRequired() ? "Online" : "Offline";
                String label = tag + " (" + v.getLocale().toLanguageTag() + " • " + quality + "/" + net
                        + ") — " + simplifyVoiceName(v.getName());

                availableVoiceOptions.add(new VoiceOption(v, label, isFemale, score));

                if (!isFemale) {
                    continue;
                }

                if ("hi".equalsIgnoreCase(lang) && score > bestHiScore) {
                    bestHiScore = score;
                    bestHindiFemaleVoice = v;
                } else if ("en".equalsIgnoreCase(lang)) {
                    String country = v.getLocale().getCountry();
                    if ("US".equalsIgnoreCase(country) && score > bestUsScore) {
                        bestUsScore = score;
                        bestUsEnglishFemaleVoice = v;
                    } else if ("IN".equalsIgnoreCase(country) && score > bestInScore) {
                        bestInScore = score;
                        bestIndianEnglishFemaleVoice = v;
                    } else if (bestUsEnglishFemaleVoice == null && score > bestUsScore) {
                        bestUsScore = score;
                        bestUsEnglishFemaleVoice = v;
                    }
                }
            }

            Collections.sort(availableVoiceOptions, new Comparator<VoiceOption>() {
                @Override
                public int compare(VoiceOption a, VoiceOption b) {
                    return Integer.compare(b.score, a.score);
                }
            });
        }

        applyPreferredVoiceToEngine();
        buildDiagnosticSummary();
    }

    private void applyPreferredVoiceToEngine() {
        if (tts == null || !initialized) return;
        String savedVoice = memoryManager.getSelectedVoiceName();
        if (savedVoice != null && !savedVoice.isEmpty()) {
            for (VoiceOption opt : availableVoiceOptions) {
                if (opt.voice.getName().equals(savedVoice)) {
                    tts.setVoice(opt.voice);
                    return;
                }
            }
        }
        if (bestHindiFemaleVoice != null) {
            tts.setVoice(bestHindiFemaleVoice);
        } else if (bestUsEnglishFemaleVoice != null) {
            tts.setVoice(bestUsEnglishFemaleVoice);
        } else if (bestIndianEnglishFemaleVoice != null) {
            tts.setVoice(bestIndianEnglishFemaleVoice);
        }
    }

    /**
     * Strictly identifies genuine female voices using engine metadata and known voice IDs.
     * Never mislabels a male or unknown voice as female.
     */
    public static boolean isGenuineFemaleVoice(Voice v) {
        if (v == null || v.getName() == null) return false;
        return isGenuineFemaleVoiceNameAndFeatures(v.getName(), v.getFeatures());
    }

    public static boolean isGenuineFemaleVoiceNameAndFeatures(String rawName, Set<String> features) {
        if (rawName == null) return false;
        String name = rawName.toLowerCase(Locale.ROOT);

        if (isKnownMaleVoiceName(name, features)) {
            return false;
        }

        if (features != null) {
            for (String f : features) {
                if (f != null && f.toLowerCase(Locale.ROOT).contains("female")) {
                    return true;
                }
            }
        }

        // Explicit female names across Google, Samsung, Apple/OEM TTS packs
        if (name.contains("female") || name.contains("woman") || name.contains("girl")
                || name.contains("lekha") || name.contains("neerja") || name.contains("swara")
                || name.contains("aditi") || name.contains("kajal") || name.contains("samantha")
                || name.contains("aria") || name.contains("jenny") || name.contains("sara")
                || name.contains("karen") || name.contains("moira") || name.contains("tessa")) {
            return true;
        }

        // Known Google TTS female Hindi (hi-IN), US English (en-US), Indian English (en-IN), UK English (en-GB) voices
        return name.contains("hi-in-x-hia") || name.contains("hi-in-x-hic") || name.contains("hi-in-x-hie")
                || name.contains("en-us-x-sfg") || name.contains("en-us-x-tpf")
                || name.contains("en-us-x-iob") || name.contains("en-us-x-iog")
                || name.contains("en-us-x-iol")
                || name.contains("en-in-x-ena") || name.contains("en-in-x-enc") || name.contains("en-in-x-ene")
                || name.contains("en-gb-x-gba") || name.contains("en-gb-x-gbc") || name.contains("en-gb-x-rjs");
    }

    private static boolean isKnownMaleVoice(Voice v) {
        return v != null && isKnownMaleVoiceName(
                v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT),
                v.getFeatures()
        );
    }

    private static boolean isKnownMaleVoiceName(String name, Set<String> features) {
        if (features != null) {
            for (String f : features) {
                if (f != null) {
                    String fl = f.toLowerCase(Locale.ROOT);
                    if (fl.contains("male") && !fl.contains("female")) {
                        return true;
                    }
                }
            }
        }
        return name.contains("-male") || name.contains("_male") || name.contains("#male")
                || name.contains("rishi") || name.contains("daniel") || name.contains("madhur")
                || name.contains("prabhat") || name.contains("hi-in-x-hid") || name.contains("hi-in-x-hib")
                || name.contains("en-in-x-end") || name.contains("en-in-x-enb")
                || name.contains("en-us-x-iom") || name.contains("en-us-x-tpd")
                || name.contains("en-us-x-sfb") || name.contains("en-gb-x-gbd");
    }

    private int scoreVoice(Voice v, boolean genuineFemale) {
        String name = v.getName().toLowerCase(Locale.ROOT);
        Locale loc = v.getLocale();
        int score = genuineFemale ? 120 : 0;

        if (isKnownMaleVoice(v)) {
            score -= 120;
        }

        // Prefer natural local/offline high-quality neural voices
        if (v.getQuality() >= Voice.QUALITY_VERY_HIGH) score += 35;
        else if (v.getQuality() >= Voice.QUALITY_HIGH) score += 25;
        else if (v.getQuality() >= Voice.QUALITY_NORMAL) score += 15;

        if (!v.isNetworkConnectionRequired()) score += 18;

        if ("hi".equalsIgnoreCase(loc.getLanguage()) && "IN".equalsIgnoreCase(loc.getCountry())) {
            score += 25;
        } else if ("en".equalsIgnoreCase(loc.getLanguage()) && "US".equalsIgnoreCase(loc.getCountry())) {
            score += 22;
        } else if ("en".equalsIgnoreCase(loc.getLanguage()) && "IN".equalsIgnoreCase(loc.getCountry())) {
            score += 18;
        }

        // Slight priority for Google's sweetest natural voices
        if (name.contains("hi-in-x-hia") || name.contains("en-us-x-sfg") || name.contains("en-us-x-tpf")) {
            score += 15;
        }

        return score;
    }

    private String simplifyVoiceName(String rawName) {
        if (rawName == null) return "Default";
        return rawName.replace("-local", "").replace("-network", "");
    }

    private void buildDiagnosticSummary() {
        boolean hasHiFemale = bestHindiFemaleVoice != null;
        boolean hasEnFemale = bestUsEnglishFemaleVoice != null || bestIndianEnglishFemaleVoice != null;

        if (!initialized) {
            lastDiagnosticMessage = "Android Text-to-Speech is not initialized.";
        } else if (!hasHiFemale && !hasEnFemale) {
            lastDiagnosticMessage = "No genuine Hindi or English female voice detected on this device. Install a female voice in Android Text-to-Speech Settings.";
        } else if (!hasHiFemale) {
            lastDiagnosticMessage = "English female voice ready, but no genuine Hindi (hi-IN) female voice was found. Tap System Voices to install Hindi female voice data.";
        } else if (!hasEnFemale) {
            lastDiagnosticMessage = "Hindi female voice ready, but no genuine English (en-US) female voice was found. Tap System Voices to install English female voice data.";
        } else {
            lastDiagnosticMessage = "Female Voices Ready: Hindi ("
                    + simplifyVoiceName(bestHindiFemaleVoice.getName())
                    + ") & English ("
                    + simplifyVoiceName((bestUsEnglishFemaleVoice != null ? bestUsEnglishFemaleVoice : bestIndianEnglishFemaleVoice).getName())
                    + ")";
        }
    }

    public boolean hasGenuineHindiFemaleVoice() {
        return bestHindiFemaleVoice != null;
    }

    public boolean hasGenuineEnglishFemaleVoice() {
        return bestUsEnglishFemaleVoice != null || bestIndianEnglishFemaleVoice != null;
    }

    public boolean isSuitableFemaleVoiceUnavailable() {
        return !initialized || (!hasGenuineHindiFemaleVoice() && !hasGenuineEnglishFemaleVoice());
    }

    public boolean isVoiceDataMissing() {
        return isSuitableFemaleVoiceUnavailable() || (!hindiSupported && !englishSupported);
    }

    public String getFemaleVoiceInstallInstructions() {
        return "To enable a sweet, natural female voice on your device:\n\n"
                + "1. Tap \"Open TTS Settings\" below.\n"
                + "2. Choose \"Speech Services by Google\" as your Preferred Engine.\n"
                + "3. Tap the Settings gear icon → \"Install voice data\".\n"
                + "4. Download \"Hindi (India)\" and \"English (United States)\" female voices.\n"
                + "5. Return to SUBH AI and tap \"Test Female Voice\".\n\n"
                + "हिन्दी: Android TTS Settings में जाकर Google Speech Services चुनें और Hindi (India) तथा English (US) female voice data डाउनलोड करें।";
    }

    public String getActiveVoiceDescription() {
        if (tts == null || !initialized) return "Voice engine loading…";
        String saved = memoryManager.getSelectedVoiceName();
        if (saved != null && !saved.isEmpty()) {
            return simplifyVoiceName(saved);
        }
        if (bestHindiFemaleVoice != null && bestUsEnglishFemaleVoice != null) {
            return "HI + EN Female Active";
        }
        if (bestHindiFemaleVoice != null) {
            return "Hindi Female (" + simplifyVoiceName(bestHindiFemaleVoice.getName()) + ")";
        }
        if (bestUsEnglishFemaleVoice != null) {
            return "EN-US Female (" + simplifyVoiceName(bestUsEnglishFemaleVoice.getName()) + ")";
        }
        if (bestIndianEnglishFemaleVoice != null) {
            return "EN-IN Female (" + simplifyVoiceName(bestIndianEnglishFemaleVoice.getName()) + ")";
        }
        return "System Default (No Female Voice Detected)";
    }

    public String getDiagnosticMessage() {
        return lastDiagnosticMessage;
    }

    public List<VoiceOption> getAvailableVoiceOptions() {
        return new ArrayList<>(availableVoiceOptions);
    }

    public void applySavedVoiceParameters() {
        if (tts == null || !initialized) return;
        tts.setSpeechRate(memoryManager.getVoiceSpeed());
        tts.setPitch(memoryManager.getVoicePitch());
    }

    public void selectVoiceByName(String voiceName) {
        memoryManager.setSelectedVoiceName(voiceName);
        if (tts == null || !initialized) return;
        if (voiceName == null || voiceName.isEmpty()) {
            refreshAvailableVoices();
            return;
        }
        for (VoiceOption opt : availableVoiceOptions) {
            if (opt.voice.getName().equals(voiceName)) {
                tts.setVoice(opt.voice);
                break;
            }
        }
        buildDiagnosticSummary();
    }

    public boolean isSpeaking() {
        return speaking;
    }

    /**
     * Speaks the given AI answer or prompt aloud completely.
     * Automatically routes Hindi, Hinglish, and English to the best matching female voice,
     * and splits long texts on sentence boundaries so nothing gets truncated.
     */
    public void speak(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return;

        if (tts == null || !initialized) {
            notifyError("Voice engine is not ready yet: " + lastDiagnosticMessage);
            return;
        }

        String cleanText = cleanTextForSpeech(rawText);
        if (cleanText.isEmpty()) return;

        applySavedVoiceParameters();
        selectBestVoiceForText(cleanText);

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, memoryManager.getVoiceVolume());

        List<String> chunks = splitIntoSpeakableChunks(cleanText, 1200);
        if (chunks.isEmpty()) return;

        // Interrupt any active speech immediately before starting the new utterance
        tts.stop();
        String finalId = "subh_final_" + UUID.randomUUID();
        activeFinalUtteranceId = finalId;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            int queueMode = (i == 0) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            String uid = (i == chunks.size() - 1) ? finalId : ("subh_part_" + i + "_" + UUID.randomUUID());
            int result = tts.speak(chunk, queueMode, params, uid);
            if (result == TextToSpeech.ERROR) {
                speaking = false;
                activeFinalUtteranceId = null;
                notifyError("Unable to play spoken voice. Open TTS Settings to install or enable female voice data.");
                return;
            }
        }
    }

    private void selectBestVoiceForText(String cleanText) {
        String pinnedVoice = memoryManager.getSelectedVoiceName();
        if (pinnedVoice != null && !pinnedVoice.isEmpty()) {
            for (VoiceOption opt : availableVoiceOptions) {
                if (opt.voice.getName().equals(pinnedVoice)) {
                    tts.setVoice(opt.voice);
                    return;
                }
            }
        }

        SpeechLanguage detected = detectSpeechLanguage(cleanText, memoryManager.getLanguageMode());
        if (detected == SpeechLanguage.HINDI || detected == SpeechLanguage.HINGLISH) {
            if (bestHindiFemaleVoice != null) {
                tts.setVoice(bestHindiFemaleVoice);
            } else if (detected == SpeechLanguage.HINGLISH && bestIndianEnglishFemaleVoice != null) {
                tts.setVoice(bestIndianEnglishFemaleVoice);
            } else if (hindiSupported) {
                tts.setLanguage(new Locale("hi", "IN"));
            } else if (bestUsEnglishFemaleVoice != null) {
                tts.setVoice(bestUsEnglishFemaleVoice);
            }
        } else {
            if (bestUsEnglishFemaleVoice != null) {
                tts.setVoice(bestUsEnglishFemaleVoice);
            } else if (bestIndianEnglishFemaleVoice != null) {
                tts.setVoice(bestIndianEnglishFemaleVoice);
            } else if (englishSupported) {
                tts.setLanguage(Locale.US);
            }
        }
    }

    public static SpeechLanguage detectSpeechLanguage(String text, String userLangPreference) {
        if (containsDevanagari(text)) {
            return SpeechLanguage.HINDI;
        }
        if (MemoryManager.LANG_HINDI.equals(userLangPreference)) {
            return SpeechLanguage.HINDI;
        }
        if (isLikelyHinglish(text)) {
            return SpeechLanguage.HINGLISH;
        }
        return SpeechLanguage.ENGLISH_US;
    }

    public static boolean isLikelyHinglish(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String[] tokens = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").split("\\s+");
        int hits = 0;
        for (String t : tokens) {
            if (HINGLISH_KEYWORDS.contains(t)) {
                hits++;
                if (hits >= 2) return true;
            }
        }
        return hits == 1 && tokens.length <= 5;
    }

    public static List<String> splitIntoSpeakableChunks(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return chunks;
        String remaining = text.trim();
        while (remaining.length() > maxChunkSize) {
            int splitIdx = -1;
            for (int i = maxChunkSize; i >= maxChunkSize / 2; i--) {
                char c = remaining.charAt(i);
                if (c == '।' || c == '.' || c == '!' || c == '?' || c == '\n') {
                    splitIdx = i + 1;
                    break;
                }
            }
            if (splitIdx <= 0) {
                splitIdx = remaining.lastIndexOf(' ', maxChunkSize);
            }
            if (splitIdx <= 0) {
                splitIdx = maxChunkSize;
            }
            chunks.add(remaining.substring(0, splitIdx).trim());
            remaining = remaining.substring(splitIdx).trim();
        }
        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }
        return chunks;
    }

    public void testFemaleVoice() {
        String langMode = memoryManager.getLanguageMode();
        String sample;
        if (MemoryManager.LANG_HINDI.equals(langMode)) {
            sample = "नमस्ते! मैं शुभ एआई हूँ। मेरी आवाज़ अब स्पष्ट, मधुर और प्राकृतिक है। बताइए, मैं आपकी क्या मदद कर सकती हूँ?";
        } else if (MemoryManager.LANG_ENGLISH.equals(langMode) || MemoryManager.LANG_ENGLISH_IN.equals(langMode)) {
            sample = "Hello! I am SUBH AI, your personal assistant. My natural female voice is calibrated and ready to assist you.";
        } else {
            sample = "नमस्ते! मैं SUBH AI हूँ। Hello, my natural female voice speaks Hindi, English, and Hinglish clearly and gently.";
        }
        speak(sample);
    }

    public void stopSpeaking() {
        speaking = false;
        activeFinalUtteranceId = null;
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    public void shutdown() {
        stopSpeaking();
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
        initialized = false;
    }

    public static void openTtsSettings(Context ctx) {
        try {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(fallback);
            } catch (Exception ignored) {
            }
        }
    }

    private String cleanTextForSpeech(String text) {
        return text
                .replaceAll("```[\\s\\S]*?```", " code snippet shown on screen. ")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("#", "")
                .replace("•", ",")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static boolean containsDevanagari(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0900' && c <= '\u097F') {
                return true;
            }
        }
        return false;
    }

    private void notifyReady(boolean success, String message) {
        mainHandler.post(() -> {
            for (TtsListener l : new ArrayList<>(listeners)) {
                l.onTtsReady(success, message);
            }
        });
    }

    private void notifyError(String message) {
        mainHandler.post(() -> {
            for (TtsListener l : new ArrayList<>(listeners)) {
                l.onSpeechError(message);
            }
        });
    }
}
