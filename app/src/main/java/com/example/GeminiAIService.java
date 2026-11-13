package com.example;

import android.os.Handler;
import android.os.Looper;

import com.example.model.ChatMessage;
import com.example.model.MemoryItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Secure backend client for SUBH AI's Gemini brain.
 * Sends conversation context, approved memories, and user queries over HTTPS
 * to a server-side proxy so private Gemini API keys are NEVER packaged in the APK.
 */
public class GeminiAIService {

    public interface AIResponseCallback {
        void onSuccess(String replyText);
        void onError(String errorMessage);
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final MemoryManager memoryManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GeminiAIService(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public boolean isBackendConfigured() {
        String url = memoryManager.getBackendUrl();
        return url != null && url.startsWith("https://") && url.length() > 10;
    }

    public void askGemini(String userMessage,
                          List<ChatMessage> recentHistory,
                          List<MemoryItem> memories,
                          AIResponseCallback callback) {

        String backendUrl = memoryManager.getBackendUrl();
        if (backendUrl == null || backendUrl.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError(
                    "Secure Gemini backend URL is not configured yet. Please open Settings → Secure Gemini AI Backend and enter your HTTPS backend proxy URL. (No private API keys are stored inside the APK.)"));
            return;
        }

        if (!backendUrl.startsWith("https://")) {
            mainHandler.post(() -> callback.onError(
                    "For your security, SUBH AI requires an HTTPS backend URL (starting with https://). Please update it in Settings."));
            return;
        }

        try {
            JSONObject payload = buildRequestPayload(userMessage, recentHistory, memories);
            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(backendUrl)
                    .post(body)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError(
                            "Unable to reach the SUBH AI secure backend: " + e.getMessage() + ". Please check your internet connection or backend URL in Settings."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String rawBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError(
                                "Backend server returned HTTP " + response.code() + ". Please verify your backend deployment and server-side GEMINI_API_KEY."));
                        return;
                    }
                    String parsedReply = extractReplyText(rawBody);
                    if (parsedReply == null || parsedReply.trim().isEmpty()) {
                        mainHandler.post(() -> callback.onError(
                                "Received an empty or unrecognized response from the backend server."));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(parsedReply.trim()));
                    }
                }
            });
        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Failed to build request JSON: " + e.getMessage()));
        }
    }

    public void testBackendConnection(AIResponseCallback callback) {
        String backendUrl = memoryManager.getBackendUrl();
        if (backendUrl == null || !backendUrl.startsWith("https://")) {
            mainHandler.post(() -> callback.onError("Please enter a valid https:// backend URL first."));
            return;
        }
        askGemini("Reply in one short friendly sentence confirming SUBH AI backend is connected.",
                null, null, callback);
    }

    private JSONObject buildRequestPayload(String userMessage,
                                           List<ChatMessage> recentHistory,
                                           List<MemoryItem> memories) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("systemInstruction", buildSystemInstruction(memories));
        root.put("languageMode", memoryManager.getLanguageMode());
        root.put("userMessage", userMessage);

        JSONArray historyArr = new JSONArray();
        if (recentHistory != null) {
            for (ChatMessage msg : recentHistory) {
                if (ChatMessage.ROLE_SYSTEM.equals(msg.getRole())) continue;
                JSONObject item = new JSONObject();
                item.put("role", ChatMessage.ROLE_USER.equals(msg.getRole()) ? "user" : "model");
                item.put("text", msg.getContent());
                historyArr.put(item);
            }
        }
        root.put("history", historyArr);
        return root;
    }

    private String buildSystemInstruction(List<MemoryItem> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are SUBH AI, a warm, articulate, intelligent female personal AI assistant with a calm, helpful JARVIS-like personality. ");
        sb.append("Speak naturally as a female assistant (e.g., in Hindi use feminine grammar such as 'मैं कर सकती हूँ', 'मैं आपकी मदद करूँगी'). ");
        sb.append("Language rules: Match the user's language naturally — respond in clear natural Hindi (Devanagari) for Hindi queries, fluent English for English queries, and natural Hinglish when appropriate. ");
        sb.append("Quality rules: Give thorough, accurate, well-structured, genuinely useful answers for general knowledge, science & tech, mathematics, exam preparation, coding & programming, Hindi/English translation, explanations, summaries, creative ideas, and problem solving. ");
        sb.append("Use the provided conversation history so follow-up questions make seamless sense. ");
        sb.append("Do not repeat the welcome greeting if conversation is already underway. ");
        sb.append("If a question is ambiguous or incomplete, politely ask a brief clarifying question. ");
        sb.append("Never invent facts or fabricate real-time/current-affairs updates when uncertain — clearly state if live post-cutoff data is unavailable. ");
        sb.append("Keep spoken readability in mind: avoid noisy markdown symbols when a clean explanation works better.");

        String langPref = memoryManager.getLanguageMode();
        if (MemoryManager.LANG_HINDI.equals(langPref)) {
            sb.append(" Preferred response language: Hindi (हिन्दी).");
        } else if (MemoryManager.LANG_ENGLISH.equals(langPref)) {
            sb.append(" Preferred response language: English.");
        }

        if (memories != null && !memories.isEmpty() && memoryManager.isMemoryEnabled()) {
            sb.append("\n\nUser-approved saved memories to personalize responses:\n");
            for (MemoryItem item : memories) {
                sb.append("- ").append(item.getFact()).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractReplyText(String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(rawBody);
            if (obj.has("reply")) return obj.optString("reply");
            if (obj.has("text")) return obj.optString("text");
            if (obj.has("answer")) return obj.optString("answer");
            if (obj.has("message")) return obj.optString("message");
            // Also support standard Gemini generateContent response shape if proxied transparently
            if (obj.has("candidates")) {
                JSONArray candidates = obj.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                    if (content != null) {
                        JSONArray parts = content.optJSONArray("parts");
                        if (parts != null && parts.length() > 0) {
                            return parts.getJSONObject(0).optString("text");
                        }
                    }
                }
            }
        } catch (JSONException ignored) {
            return rawBody;
        }
        return null;
    }
}
