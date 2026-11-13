package com.example;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.model.ChatMessage;
import com.example.model.MemoryItem;
import com.example.util.MathEvaluator;
import com.example.util.PhoneControlManager;

import java.util.List;
import java.util.Locale;

/**
 * Central voice assistant orchestrator connecting SpeechRecognition,
 * TextToSpeechManager, GeminiAIService, MemoryManager, MathEvaluator,
 * and PhoneControlManager.
 */
public class VoiceAssistant implements
        SpeechRecognition.SpeechCallback,
        TextToSpeechManager.TtsListener {

    public enum AssistantState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        SLEEPING,
        AWAITING_CONFIRMATION
    }

    public interface AssistantUIListener {
        void onStateChanged(AssistantState state, String statusText);
        void onPartialTranscript(String partialText);
        void onAudioLevelChanged(float normalizedLevel);
        void onNewMessage(ChatMessage message);
        void onConfirmationRequested(String promptText);
        void onConfirmationCleared();
        void onVoiceDiagnostic(String message, boolean isFemaleVoiceMissing);
        void onRequestMicPermission();
    }

    public static final String DEFAULT_GREETING =
            "नमस्ते! आपका स्वागत है। मैं SUBH AI हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?";

    private final Context context;
    private final MemoryManager memoryManager;
    private final TextToSpeechManager ttsManager;
    private final SpeechRecognition speechRecognition;
    private final GeminiAIService geminiService;
    private final PhoneControlManager phoneControl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AssistantUIListener uiListener;
    private AssistantState currentState = AssistantState.IDLE;
    private boolean sessionGreeted = false;
    private boolean autoListenAfterSpeech = false;
    private boolean wakeListeningCycle = false;

    private Runnable pendingConfirmedAction;
    private String pendingActionLabel = "";

    public VoiceAssistant(Context context,
                          MemoryManager memoryManager,
                          TextToSpeechManager ttsManager,
                          GeminiAIService geminiService) {
        this.context = context.getApplicationContext();
        this.memoryManager = memoryManager;
        this.ttsManager = ttsManager;
        this.geminiService = geminiService;
        this.phoneControl = new PhoneControlManager(this.context);
        this.speechRecognition = new SpeechRecognition(this.context, memoryManager);
        this.speechRecognition.setCallback(this);
        this.ttsManager.addListener(this);
    }

    public void setUIListener(AssistantUIListener listener) {
        this.uiListener = listener;
        if (listener != null) {
            listener.onStateChanged(currentState, getStateStatusText(currentState));
            listener.onVoiceDiagnostic(ttsManager.getDiagnosticMessage(), ttsManager.isSuitableFemaleVoiceUnavailable());
        }
    }

    public AssistantState getCurrentState() {
        return currentState;
    }

    public void onActivityResumed() {
        ttsManager.refreshAvailableVoices();
        ttsManager.applySavedVoiceParameters();
        if (uiListener != null) {
            uiListener.onVoiceDiagnostic(ttsManager.getDiagnosticMessage(), ttsManager.isSuitableFemaleVoiceUnavailable());
        }
        if (memoryManager.isWakeWordEnabled()
                && currentState == AssistantState.IDLE
                && speechRecognition.hasMicrophonePermission()) {
            startWakeWordListening();
        }
    }

    public void onActivityPaused() {
        speechRecognition.stopListening();
        wakeListeningCycle = false;
        if (currentState == AssistantState.LISTENING) {
            updateState(AssistantState.IDLE);
        }
    }

    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        ttsManager.removeListener(this);
        speechRecognition.destroy();
    }

    public void greetOnceIfNeeded(boolean speakGreeting) {
        if (sessionGreeted) return;
        sessionGreeted = true;
        List<ChatMessage> existing = memoryManager.getAllMessages();
        boolean alreadyInHistory = !existing.isEmpty()
                && DEFAULT_GREETING.equals(existing.get(existing.size() - 1).getContent());
        if (!alreadyInHistory) {
            ChatMessage greetingMsg = new ChatMessage(
                    ChatMessage.ROLE_ASSISTANT,
                    DEFAULT_GREETING,
                    System.currentTimeMillis(),
                    "hi-IN"
            );
            memoryManager.saveMessage(greetingMsg);
            if (uiListener != null) {
                uiListener.onNewMessage(greetingMsg);
            }
        }
        if (speakGreeting) {
            autoListenAfterSpeech = false;
            updateState(AssistantState.SPEAKING);
            ttsManager.speak(DEFAULT_GREETING);
        }
    }

    public void onMicButtonPressed() {
        // Immediate barge-in: stop speaking when user interrupts
        if (currentState == AssistantState.SPEAKING || ttsManager.isSpeaking()) {
            interruptAndListen();
            return;
        }
        if (currentState == AssistantState.LISTENING) {
            wakeListeningCycle = false;
            speechRecognition.stopListening();
            updateState(AssistantState.IDLE);
            return;
        }
        if (!speechRecognition.hasMicrophonePermission()) {
            if (uiListener != null) {
                uiListener.onRequestMicPermission();
            }
            return;
        }
        wakeListeningCycle = false;
        if (currentState == AssistantState.SLEEPING) {
            updateState(AssistantState.IDLE);
        }
        speechRecognition.startListening();
    }

    public void interruptAndListen() {
        autoListenAfterSpeech = false;
        wakeListeningCycle = false;
        ttsManager.stopSpeaking();
        if (!speechRecognition.hasMicrophonePermission()) {
            updateState(AssistantState.IDLE);
            if (uiListener != null) uiListener.onRequestMicPermission();
            return;
        }
        speechRecognition.startListening();
    }

    public void stopSpeakingOnly() {
        autoListenAfterSpeech = false;
        ttsManager.stopSpeaking();
        updateState(AssistantState.IDLE);
    }

    public void testFemaleVoice() {
        autoListenAfterSpeech = false;
        speechRecognition.stopListening();
        updateState(AssistantState.SPEAKING);
        ttsManager.testFemaleVoice();
    }

    public void replayText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        autoListenAfterSpeech = false;
        speechRecognition.stopListening();
        updateState(AssistantState.SPEAKING);
        ttsManager.speak(text);
    }

    public void confirmPendingAction(boolean approved) {
        if (uiListener != null) {
            uiListener.onConfirmationCleared();
        }
        Runnable action = pendingConfirmedAction;
        String label = pendingActionLabel;
        pendingConfirmedAction = null;
        pendingActionLabel = "";

        if (approved && action != null) {
            action.run();
        } else {
            String msg = "ठीक है, मैंने " + (label.isEmpty() ? "यह क्रिया" : label) + " रद्द कर दी है।";
            sendAssistantReply(msg, false);
        }
    }

    public void processUserInput(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return;
        String text = rawText.trim();

        // Interrupt any ongoing speech immediately whenever the user submits a new query
        if (ttsManager.isSpeaking()) {
            ttsManager.stopSpeaking();
        }

        if (currentState == AssistantState.AWAITING_CONFIRMATION) {
            addAndNotifyMessage(new ChatMessage(ChatMessage.ROLE_USER, text, System.currentTimeMillis(), detectLangTag(text)));
            if (isAffirmative(text)) {
                confirmPendingAction(true);
            } else if (isNegative(text)) {
                confirmPendingAction(false);
            } else {
                if (uiListener != null) uiListener.onConfirmationCleared();
                pendingConfirmedAction = null;
                routeCommandOrAI(text);
            }
            return;
        }

        if (SpeechRecognition.matchesSleepPhrase(text)) {
            addAndNotifyMessage(new ChatMessage(ChatMessage.ROLE_USER, text, System.currentTimeMillis(), detectLangTag(text)));
            autoListenAfterSpeech = false;
            wakeListeningCycle = memoryManager.isWakeWordEnabled();
            String reply = "शुभ रात्रि! जब भी आवश्यकता हो, बस 'Hey SUBH' बोलें या माइक दबाएँ।";
            sendAssistantReplyAndSleep(reply);
            return;
        }

        if (SpeechRecognition.matchesWakePhrase(text)) {
            if (currentState == AssistantState.SLEEPING || wakeListeningCycle) {
                wakeListeningCycle = false;
                updateState(AssistantState.IDLE);
                String reply = "जी, मैं सुन रही हूँ। बताइए मैं आपकी क्या मदद कर सकती हूँ?";
                sendAssistantReply(reply, true);
                return;
            }
        }

        addAndNotifyMessage(new ChatMessage(ChatMessage.ROLE_USER, text, System.currentTimeMillis(), detectLangTag(text)));
        routeCommandOrAI(text);
    }

    private void routeCommandOrAI(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        boolean preferHindi = TextToSpeechManager.containsDevanagari(text)
                || TextToSpeechManager.isLikelyHinglish(text)
                || MemoryManager.LANG_HINDI.equals(memoryManager.getLanguageMode());

        // 1. Voice Search on YouTube
        if (lower.startsWith("search youtube for ") || lower.startsWith("youtube search ")
                || lower.startsWith("play on youtube ") || lower.contains("यूट्यूब पर ")) {
            String query = extractSearchQuery(text,
                    new String[]{"search youtube for", "youtube search", "play on youtube", "यूट्यूब पर", "खोजो", "चलाओ"});
            if (!query.isEmpty()) {
                boolean ok = phoneControl.searchYouTube(query);
                String reply = ok
                        ? (preferHindi ? "मैं यूट्यूब पर \"" + query + "\" खोज रही हूँ।" : "Searching YouTube for \"" + query + "\".")
                        : "Unable to open YouTube search on this device.";
                sendAssistantReply(reply, false);
                return;
            }
        }

        // 2. Voice Search on Google
        if (lower.startsWith("search google for ") || lower.startsWith("google search ")
                || lower.startsWith("search for ") || lower.contains("गूगल पर ")) {
            String query = extractSearchQuery(text,
                    new String[]{"search google for", "google search", "search for", "गूगल पर", "खोजो", "सर्च करो"});
            if (!query.isEmpty()) {
                boolean ok = phoneControl.searchGoogle(query);
                String reply = ok
                        ? (preferHindi ? "मैं गूगल पर \"" + query + "\" खोज रही हूँ।" : "Searching Google for \"" + query + "\".")
                        : "Unable to open Google search on this device.";
                sendAssistantReply(reply, false);
                return;
            }
        }

        // 3. Direct Phone Controls
        if (matchesOpenCommand(lower, "youtube", "यूट्यूब")) {
            boolean ok = phoneControl.openYouTube();
            sendAssistantReply(ok
                    ? (preferHindi ? "यूट्यूब खोल रही हूँ।" : "Opening YouTube.")
                    : "YouTube could not be opened on this device.", false);
            return;
        }
        if (matchesOpenCommand(lower, "google", "गूगल") && !lower.contains("map")) {
            boolean ok = phoneControl.openGoogle();
            sendAssistantReply(ok
                    ? (preferHindi ? "गूगल खोल रही हूँ।" : "Opening Google.")
                    : "Google could not be opened on this device.", false);
            return;
        }
        if (matchesOpenCommand(lower, "camera", "कैमरा")) {
            boolean ok = phoneControl.openCamera();
            sendAssistantReply(ok
                    ? (preferHindi ? "कैमरा खोल रही हूँ।" : "Opening Camera.")
                    : "Camera app is not available on this device.", false);
            return;
        }
        if (matchesOpenCommand(lower, "calculator", "कैलकुलेटर")) {
            boolean ok = phoneControl.openCalculator();
            sendAssistantReply(ok
                    ? (preferHindi ? "कैलकुलेटर खोल रही हूँ।" : "Opening Calculator.")
                    : "Calculator app could not be found on this device.", false);
            return;
        }
        if (matchesOpenCommand(lower, "maps", "मैप्स") || matchesOpenCommand(lower, "google maps", "गूगल मैप्स")) {
            boolean ok = phoneControl.openMaps();
            sendAssistantReply(ok
                    ? (preferHindi ? "गूगल मैप्स खोल रही हूँ।" : "Opening Google Maps.")
                    : "Maps could not be opened on this device.", false);
            return;
        }

        if (matchesOpenCommand(lower, "settings", "सेटिंग", "सेटिंग्स")) {
            requestConfirmation(
                    preferHindi ? "क्या आप चाहते हैं कि मैं डिवाइस की Settings खोलूँ?"
                            : "Would you like me to open your device Settings?",
                    "Device Settings",
                    () -> {
                        boolean ok = phoneControl.openSettings();
                        sendAssistantReply(ok
                                ? (preferHindi ? "डिवाइस सेटिंग्स खोल रही हूँ।" : "Opening device Settings.")
                                : "Unable to open Settings.", false);
                    });
            return;
        }

        if (lower.contains("clear chat") || lower.contains("delete chat") || lower.contains("चैट साफ")) {
            requestConfirmation(
                    preferHindi ? "क्या आप वाकई पूरी चैट हिस्ट्री मिटाना चाहते हैं?"
                            : "Are you sure you want to clear all chat history?",
                    "Clear Chat",
                    () -> {
                        memoryManager.clearChatHistory();
                        sendAssistantReply(preferHindi ? "चैट हिस्ट्री साफ कर दी गई है।" : "Chat history has been cleared.", false);
                    });
            return;
        }

        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.contains("खोलो")) {
            boolean isSensitiveApp = lower.contains("whatsapp") || lower.contains("gmail")
                    || lower.contains("phone") || lower.contains("dialer");
            if (isSensitiveApp) {
                requestConfirmation(
                        preferHindi ? "क्या आप यह ऐप खोलने की अनुमति देते हैं: " + text + "?"
                                : "Confirm opening external app for: \"" + text + "\"?",
                        text,
                        () -> launchGenericApp(text, preferHindi));
                return;
            }
            if (phoneControl.openSupportedApp(text)) {
                sendAssistantReply(preferHindi ? "ऐप खोल रही हूँ।" : "Opening the app now.", false);
                return;
            }
        }

        // 4. Date & Time
        if (isDateTimeQuery(lower)) {
            String dateTimeReply = phoneControl.getFormattedDateTime(preferHindi);
            sendAssistantReply(dateTimeReply, memoryManager.isConversationModeEnabled());
            return;
        }

        // 5. Offline Math Evaluator
        Double calcResult = MathEvaluator.tryEvaluate(text);
        if (calcResult != null) {
            String formatted = MathEvaluator.formatNumber(calcResult);
            String reply = preferHindi
                    ? "इसका उत्तर " + formatted + " है।"
                    : "The answer is " + formatted + ".";
            sendAssistantReply(reply, memoryManager.isConversationModeEnabled());
            return;
        }

        // 6. Local Memory Commands
        if (lower.startsWith("remember that ") || lower.startsWith("remember ")
                || lower.startsWith("याद रखो ") || lower.startsWith("याद रखना ")) {
            String fact = text.replaceFirst("(?i)^(remember that|remember|याद रखो कि|याद रखो|याद रखना)\\s+", "").trim();
            if (!fact.isEmpty()) {
                if (!memoryManager.isMemoryEnabled()) {
                    sendAssistantReply(preferHindi
                            ? "मेमोरी सिस्टम अभी बंद है। इसे चालू करने के लिए Settings में Memory ON करें।"
                            : "Memory is currently turned OFF. Please enable Memory in Settings so I can remember that.",
                            memoryManager.isConversationModeEnabled());
                } else {
                    memoryManager.saveMemory(fact, "user_note");
                    sendAssistantReply(preferHindi
                            ? "जी, मैंने याद रख लिया है: " + fact
                            : "Got it, I will remember that: " + fact,
                            memoryManager.isConversationModeEnabled());
                }
                return;
            }
        }

        if (lower.contains("what do you remember") || lower.contains("show my memories")
                || lower.contains("मेरी यादें") || lower.contains("क्या याद है")) {
            List<MemoryItem> memories = memoryManager.getAllMemories();
            if (memories.isEmpty()) {
                sendAssistantReply(preferHindi
                        ? "अभी कोई सेव की गई मेमोरी नहीं है।"
                        : "I don't have any saved memories yet.",
                        memoryManager.isConversationModeEnabled());
            } else {
                StringBuilder sb = new StringBuilder(preferHindi ? "आपकी सेव की गई यादें:\n" : "Here is what I remember:\n");
                int max = Math.min(memories.size(), 6);
                for (int i = 0; i < max; i++) {
                    sb.append("• ").append(memories.get(i).getFact()).append("\n");
                }
                sendAssistantReply(sb.toString().trim(), memoryManager.isConversationModeEnabled());
            }
            return;
        }

        // 7. Intelligent Gemini AI Brain via Secure Backend (spoken aloud automatically)
        updateState(AssistantState.THINKING);
        List<ChatMessage> history = memoryManager.getRecentContextMessages(12);
        List<MemoryItem> memories = memoryManager.getAllMemories();

        geminiService.askGemini(text, history, memories, new GeminiAIService.AIResponseCallback() {
            @Override
            public void onSuccess(String replyText) {
                sendAssistantReply(replyText, memoryManager.isConversationModeEnabled());
            }

            @Override
            public void onError(String errorMessage) {
                // Speak error explanation aloud too so the assistant never fails silently
                sendAssistantReply(errorMessage, false);
            }
        });
    }

    private void launchGenericApp(String text, boolean preferHindi) {
        boolean ok = phoneControl.openSupportedApp(text);
        sendAssistantReply(ok
                ? (preferHindi ? "ऐप खोल रही हूँ।" : "Opening the requested app.")
                : (preferHindi ? "क्षमा करें, यह ऐप इस डिवाइस पर इंस्टॉल नहीं मिला।"
                        : "Sorry, I couldn't find that supported app installed on this device."),
                false);
    }

    private void requestConfirmation(String prompt, String label, Runnable onConfirm) {
        this.pendingConfirmedAction = onConfirm;
        this.pendingActionLabel = label;
        updateState(AssistantState.AWAITING_CONFIRMATION);
        if (uiListener != null) {
            uiListener.onConfirmationRequested(prompt);
        }
        addAndNotifyMessage(new ChatMessage(ChatMessage.ROLE_ASSISTANT, prompt, System.currentTimeMillis(), detectLangTag(prompt)));
        autoListenAfterSpeech = true;
        ttsManager.speak(prompt);
    }

    private void sendAssistantReply(String replyText, boolean continueListeningAfter) {
        ChatMessage assistantMsg = new ChatMessage(
                ChatMessage.ROLE_ASSISTANT,
                replyText,
                System.currentTimeMillis(),
                detectLangTag(replyText)
        );
        memoryManager.saveMessage(assistantMsg);
        if (uiListener != null) {
            uiListener.onNewMessage(assistantMsg);
        }
        autoListenAfterSpeech = continueListeningAfter;
        updateState(AssistantState.SPEAKING);
        ttsManager.speak(replyText);
    }

    private void sendAssistantReplyAndSleep(String replyText) {
        ChatMessage assistantMsg = new ChatMessage(
                ChatMessage.ROLE_ASSISTANT,
                replyText,
                System.currentTimeMillis(),
                detectLangTag(replyText)
        );
        memoryManager.saveMessage(assistantMsg);
        if (uiListener != null) {
            uiListener.onNewMessage(assistantMsg);
        }
        autoListenAfterSpeech = false;
        updateState(AssistantState.SLEEPING);
        ttsManager.speak(replyText);
    }

    private void addAndNotifyMessage(ChatMessage message) {
        memoryManager.saveMessage(message);
        if (uiListener != null) {
            uiListener.onNewMessage(message);
        }
    }

    private void startWakeWordListening() {
        if (!memoryManager.isWakeWordEnabled() || !speechRecognition.hasMicrophonePermission()) {
            return;
        }
        wakeListeningCycle = true;
        speechRecognition.startListening();
    }

    private void updateState(AssistantState newState) {
        this.currentState = newState;
        if (uiListener != null) {
            uiListener.onStateChanged(newState, getStateStatusText(newState));
        }
    }

    private String getStateStatusText(AssistantState state) {
        switch (state) {
            case LISTENING:
                return wakeListeningCycle
                        ? context.getString(R.string.state_wake_listening)
                        : context.getString(R.string.state_listening);
            case THINKING:
                return context.getString(R.string.state_thinking);
            case SPEAKING:
                return context.getString(R.string.state_speaking);
            case SLEEPING:
                return context.getString(R.string.state_sleeping);
            case AWAITING_CONFIRMATION:
                return context.getString(R.string.state_confirming);
            case IDLE:
            default:
                return context.getString(R.string.state_idle);
        }
    }

    @Override
    public void onListeningStarted() {
        updateState(AssistantState.LISTENING);
    }

    @Override
    public void onRmsChanged(float normalizedLevel) {
        if (uiListener != null) {
            uiListener.onAudioLevelChanged(normalizedLevel);
        }
    }

    @Override
    public void onPartialTranscript(String partialText) {
        if (uiListener != null && !wakeListeningCycle) {
            uiListener.onPartialTranscript(partialText);
        }
    }

    @Override
    public void onFinalTranscript(String finalText) {
        if (wakeListeningCycle) {
            if (SpeechRecognition.matchesWakePhrase(finalText)) {
                wakeListeningCycle = false;
                updateState(AssistantState.IDLE);
                sendAssistantReply("जी, मैं सुन रही हूँ। बताइए क्या मदद करूँ?", true);
            } else {
                mainHandler.postDelayed(this::startWakeWordListening, 500L);
            }
            return;
        }
        processUserInput(finalText);
    }

    @Override
    public void onSpeechError(int errorCode, String humanReadableError, boolean isTimeoutOrNoMatch) {
        if (wakeListeningCycle) {
            if (isTimeoutOrNoMatch && memoryManager.isWakeWordEnabled()) {
                mainHandler.postDelayed(this::startWakeWordListening, 650L);
                return;
            }
            wakeListeningCycle = false;
        }

        updateState(currentState == AssistantState.SLEEPING ? AssistantState.SLEEPING : AssistantState.IDLE);
        if (!isTimeoutOrNoMatch) {
            ChatMessage errMsg = new ChatMessage(ChatMessage.ROLE_SYSTEM, humanReadableError,
                    System.currentTimeMillis(), "en-US");
            if (uiListener != null) {
                uiListener.onNewMessage(errMsg);
            }
        } else if (uiListener != null) {
            uiListener.onPartialTranscript(humanReadableError);
        }
    }

    @Override
    public void onTtsReady(boolean success, String statusMessage) {
        if (uiListener != null) {
            uiListener.onVoiceDiagnostic(statusMessage, !success || ttsManager.isSuitableFemaleVoiceUnavailable());
        }
        if (success) {
            greetOnceIfNeeded(true);
        } else {
            greetOnceIfNeeded(false);
            ChatMessage notice = new ChatMessage(ChatMessage.ROLE_SYSTEM,
                    "Voice output warning: " + statusMessage, System.currentTimeMillis(), "en-US");
            if (uiListener != null) uiListener.onNewMessage(notice);
        }
    }

    @Override
    public void onSpeechStart(String utteranceId) {
        if (currentState != AssistantState.AWAITING_CONFIRMATION && currentState != AssistantState.SLEEPING) {
            updateState(AssistantState.SPEAKING);
        }
    }

    @Override
    public void onSpeechDone(String utteranceId) {
        if (currentState == AssistantState.SLEEPING) {
            updateState(AssistantState.SLEEPING);
            if (memoryManager.isWakeWordEnabled() && speechRecognition.hasMicrophonePermission()) {
                mainHandler.postDelayed(this::startWakeWordListening, 500L);
            }
            return;
        }

        if (autoListenAfterSpeech && speechRecognition.hasMicrophonePermission()) {
            autoListenAfterSpeech = false;
            mainHandler.postDelayed(() -> {
                if (currentState != AssistantState.SLEEPING) {
                    speechRecognition.startListening();
                }
            }, 350L);
        } else {
            if (currentState != AssistantState.AWAITING_CONFIRMATION) {
                updateState(AssistantState.IDLE);
            }
            if (memoryManager.isWakeWordEnabled() && speechRecognition.hasMicrophonePermission()) {
                mainHandler.postDelayed(this::startWakeWordListening, 600L);
            }
        }
    }

    @Override
    public void onSpeechError(String errorMessage) {
        autoListenAfterSpeech = false;
        updateState(AssistantState.IDLE);
        if (uiListener != null) {
            uiListener.onVoiceDiagnostic(errorMessage, true);
            uiListener.onNewMessage(new ChatMessage(ChatMessage.ROLE_SYSTEM,
                    "Voice output issue: " + errorMessage, System.currentTimeMillis(), "en-US"));
        }
    }

    private boolean matchesOpenCommand(String lower, String... keywords) {
        boolean hasVerb = lower.contains("open") || lower.contains("launch")
                || lower.contains("start") || lower.contains("खोलो") || lower.contains("चलाओ");
        if (!hasVerb && lower.split("\\s+").length > 2) return false;
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String extractSearchQuery(String original, String[] prefixes) {
        String result = original.trim();
        for (String p : prefixes) {
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(p), "").trim();
        }
        return result;
    }

    private boolean isDateTimeQuery(String lower) {
        return lower.contains("what time") || lower.contains("current time")
                || lower.contains("what is the time") || lower.contains("tell me the time")
                || lower.contains("time and date") || lower.contains("what is the date")
                || lower.contains("today's date") || lower.equals("time") || lower.equals("date")
                || lower.contains("कितने बजे") || lower.contains("क्या समय")
                || lower.contains("आज की तारीख") || lower.contains("टाइम क्या");
    }

    private boolean isAffirmative(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.equals("yes") || lower.equals("yeah") || lower.equals("ok") || lower.equals("okay")
                || lower.equals("sure") || lower.equals("proceed") || lower.equals("confirm")
                || lower.equals("हाँ") || lower.equals("जी") || lower.equals("जी हाँ") || lower.equals("ठीक है");
    }

    private boolean isNegative(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.equals("no") || lower.equals("cancel") || lower.equals("stop") || lower.equals("don't")
                || lower.equals("नहीं") || lower.equals("रद्द") || lower.equals("मत करो");
    }

    private String detectLangTag(String text) {
        if (TextToSpeechManager.containsDevanagari(text) || TextToSpeechManager.isLikelyHinglish(text)) {
            return "hi-IN";
        }
        return "en-US";
    }
}
