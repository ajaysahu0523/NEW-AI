package com.example;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.model.ChatMessage;
import com.example.ui.ChatAdapter;
import com.example.ui.JarvisOrbView;
import com.example.ui.WaveformView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.List;
import java.util.Locale;

/**
 * Main JARVIS-style HUD interface for SUBH AI voice & text assistant,
 * featuring quick female voice testing, live speed/pitch/volume controls,
 * immediate speech interruption, and female voice install guidance.
 */
public class MainActivity extends AppCompatActivity implements
        VoiceAssistant.AssistantUIListener,
        ChatAdapter.OnMessageActionListener {

    private MemoryManager memoryManager;
    private TextToSpeechManager ttsManager;
    private VoiceAssistant voiceAssistant;

    private JarvisOrbView jarvisOrb;
    private WaveformView waveformView;
    private TextView tvAssistantState;
    private TextView tvVoiceBadge;
    private TextView tvLiveTranscript;
    private TextView tvVoiceWarning;
    private Chip chipConvoMode;

    private RecyclerView rvChat;
    private ChatAdapter chatAdapter;
    private EditText etMessage;
    private ImageButton btnMic;
    private ImageButton btnStopSpeaking;
    private ObjectAnimator micPulseAnimator;

    private LinearLayout layoutConfirmation;
    private TextView tvConfirmPrompt;

    private ActivityResultLauncher<String> micPermissionLauncher;
    private boolean installPromptShownThisSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppController app = AppController.getInstance();
        memoryManager = app.getMemoryManager();
        ttsManager = app.getTtsManager();
        voiceAssistant = new VoiceAssistant(this, memoryManager, ttsManager, app.getGeminiService());

        setupEdgeToEdgeInsets();
        bindViews();
        setupPermissionLauncher();
        setupListeners();
        loadHistoryAndInit();
    }

    private void setupEdgeToEdgeInsets() {
        View root = findViewById(R.id.root_main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void bindViews() {
        jarvisOrb = findViewById(R.id.jarvis_orb);
        waveformView = findViewById(R.id.waveform_view);
        tvAssistantState = findViewById(R.id.tv_assistant_state);
        tvVoiceBadge = findViewById(R.id.tv_voice_badge);
        tvLiveTranscript = findViewById(R.id.tv_live_transcript);
        tvVoiceWarning = findViewById(R.id.tv_voice_warning);
        chipConvoMode = findViewById(R.id.chip_convo_mode);

        rvChat = findViewById(R.id.rv_chat);
        etMessage = findViewById(R.id.et_message);
        btnMic = findViewById(R.id.btn_mic);
        btnStopSpeaking = findViewById(R.id.btn_stop_speaking);

        layoutConfirmation = findViewById(R.id.layout_confirmation);
        tvConfirmPrompt = findViewById(R.id.tv_confirm_prompt);

        chatAdapter = new ChatAdapter(this);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvChat.setLayoutManager(lm);
        rvChat.setAdapter(chatAdapter);

        micPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                btnMic,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.12f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.12f)
        );
        micPulseAnimator.setDuration(650);
        micPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        micPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
    }

    private void setupPermissionLauncher() {
        micPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        voiceAssistant.onMicButtonPressed();
                    } else {
                        showMicDeniedDialog();
                    }
                }
        );
    }

    private void setupListeners() {
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.btn_memories).setOnClickListener(v ->
                startActivity(new Intent(this, MemoryActivity.class)));

        findViewById(R.id.btn_quick_test_voice).setOnClickListener(v ->
                voiceAssistant.testFemaleVoice());

        findViewById(R.id.btn_quick_voice_controls).setOnClickListener(v ->
                showQuickVoiceControlsDialog());

        chipConvoMode.setChecked(memoryManager.isConversationModeEnabled());
        chipConvoMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            memoryManager.setConversationModeEnabled(isChecked);
            Toast.makeText(this,
                    isChecked ? "2-Way Voice Conversation ON" : "2-Way Voice Conversation OFF",
                    Toast.LENGTH_SHORT).show();
        });

        jarvisOrb.setOnClickListener(v -> voiceAssistant.onMicButtonPressed());
        btnMic.setOnClickListener(v -> voiceAssistant.onMicButtonPressed());
        btnStopSpeaking.setOnClickListener(v -> voiceAssistant.stopSpeakingOnly());

        findViewById(R.id.btn_send).setOnClickListener(v -> sendTypedMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTypedMessage();
                return true;
            }
            return false;
        });

        tvVoiceWarning.setOnClickListener(v -> showFemaleVoiceInstallGuideDialog());

        MaterialButton btnYes = findViewById(R.id.btn_confirm_yes);
        MaterialButton btnNo = findViewById(R.id.btn_confirm_no);
        btnYes.setOnClickListener(v -> voiceAssistant.confirmPendingAction(true));
        btnNo.setOnClickListener(v -> voiceAssistant.confirmPendingAction(false));

        findViewById(R.id.chip_quick_time).setOnClickListener(v ->
                voiceAssistant.processUserInput("What is the current time and date?"));
        findViewById(R.id.chip_quick_calc).setOnClickListener(v ->
                voiceAssistant.processUserInput("Calculate 18% of 2500"));
        findViewById(R.id.chip_quick_youtube).setOnClickListener(v ->
                voiceAssistant.processUserInput("Open YouTube"));
        findViewById(R.id.chip_quick_camera).setOnClickListener(v ->
                voiceAssistant.processUserInput("Open Camera"));
        findViewById(R.id.chip_quick_google).setOnClickListener(v ->
                voiceAssistant.processUserInput("Search Google for latest space science"));
    }

    private void loadHistoryAndInit() {
        List<ChatMessage> history = memoryManager.getAllMessages();
        chatAdapter.setMessages(history);
        scrollToBottom();

        voiceAssistant.setUIListener(this);
        if (!ttsManager.isSuitableFemaleVoiceUnavailable()) {
            voiceAssistant.greetOnceIfNeeded(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        chipConvoMode.setChecked(memoryManager.isConversationModeEnabled());
        tvVoiceBadge.setText("Female Voice • " + ttsManager.getActiveVoiceDescription());
        List<ChatMessage> dbMessages = memoryManager.getAllMessages();
        if (dbMessages.isEmpty() && chatAdapter.getItemCount() > 0) {
            chatAdapter.clear();
        }
        voiceAssistant.onActivityResumed();
    }

    @Override
    protected void onPause() {
        voiceAssistant.onActivityPaused();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (micPulseAnimator != null) {
            micPulseAnimator.cancel();
        }
        voiceAssistant.destroy();
        super.onDestroy();
    }

    private void sendTypedMessage() {
        String text = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        etMessage.setText("");
        voiceAssistant.processUserInput(text);
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void showQuickVoiceControlsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_voice_controls, null);
        TextView tvStatus = view.findViewById(R.id.tv_dialog_voice_status);
        TextView tvSpeedVal = view.findViewById(R.id.tv_dialog_speed_val);
        TextView tvPitchVal = view.findViewById(R.id.tv_dialog_pitch_val);
        TextView tvVolumeVal = view.findViewById(R.id.tv_dialog_volume_val);

        SeekBar seekSpeed = view.findViewById(R.id.seek_dialog_speed);
        SeekBar seekPitch = view.findViewById(R.id.seek_dialog_pitch);
        SeekBar seekVolume = view.findViewById(R.id.seek_dialog_volume);

        tvStatus.setText(ttsManager.getDiagnosticMessage());

        float speed = memoryManager.getVoiceSpeed();
        seekSpeed.setProgress(Math.round((speed - 0.6f) * 100f));
        tvSpeedVal.setText(String.format(Locale.US, "%.2fx", speed));
        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.6f + (progress / 100f);
                tvSpeedVal.setText(String.format(Locale.US, "%.2fx", val));
                if (fromUser) {
                    memoryManager.setVoiceSpeed(val);
                    ttsManager.applySavedVoiceParameters();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        float pitch = memoryManager.getVoicePitch();
        seekPitch.setProgress(Math.round((pitch - 0.6f) * 100f));
        tvPitchVal.setText(String.format(Locale.US, "%.2f", pitch));
        seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.6f + (progress / 100f);
                tvPitchVal.setText(String.format(Locale.US, "%.2f", val));
                if (fromUser) {
                    memoryManager.setVoicePitch(val);
                    ttsManager.applySavedVoiceParameters();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        int volPct = Math.round(memoryManager.getVoiceVolume() * 100f);
        seekVolume.setProgress(volPct);
        tvVolumeVal.setText(volPct + "%");
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int clamped = Math.max(10, progress);
                tvVolumeVal.setText(clamped + "%");
                if (fromUser) {
                    memoryManager.setVoiceVolume(clamped / 100f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Female Voice Controls")
                .setView(view)
                .setPositiveButton("Test Voice", (d, w) -> voiceAssistant.testFemaleVoice())
                .setNeutralButton("Install Voices", (d, w) -> showFemaleVoiceInstallGuideDialog())
                .setNegativeButton("Done", null)
                .show();
    }

    private void showFemaleVoiceInstallGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Install Natural Female Voice")
                .setMessage(ttsManager.getFemaleVoiceInstallInstructions())
                .setPositiveButton("Open TTS Settings", (d, w) -> TextToSpeechManager.openTtsSettings(this))
                .setNeutralButton("Voice Settings", (d, w) ->
                        startActivity(new Intent(this, SettingsActivity.class)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showMicDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Microphone Access Needed")
                .setMessage(R.string.mic_permission_denied)
                .setPositiveButton("App Settings", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Use Text", null)
                .show();
    }

    @Override
    public void onStateChanged(VoiceAssistant.AssistantState state, String statusText) {
        tvAssistantState.setText(statusText);

        switch (state) {
            case LISTENING:
                jarvisOrb.setState(JarvisOrbView.OrbState.LISTENING);
                waveformView.setWaveformState(true, false);
                btnStopSpeaking.setVisibility(View.GONE);
                if (!micPulseAnimator.isRunning()) micPulseAnimator.start();
                break;
            case THINKING:
                jarvisOrb.setState(JarvisOrbView.OrbState.THINKING);
                waveformView.setWaveformState(false, false);
                btnStopSpeaking.setVisibility(View.GONE);
                stopMicPulse();
                break;
            case SPEAKING:
                jarvisOrb.setState(JarvisOrbView.OrbState.SPEAKING);
                waveformView.setWaveformState(true, true);
                btnStopSpeaking.setVisibility(View.VISIBLE);
                stopMicPulse();
                break;
            case SLEEPING:
                jarvisOrb.setState(JarvisOrbView.OrbState.SLEEPING);
                waveformView.setWaveformState(false, false);
                btnStopSpeaking.setVisibility(View.GONE);
                stopMicPulse();
                break;
            case AWAITING_CONFIRMATION:
                jarvisOrb.setState(JarvisOrbView.OrbState.SPEAKING);
                waveformView.setWaveformState(true, true);
                btnStopSpeaking.setVisibility(View.VISIBLE);
                stopMicPulse();
                break;
            case IDLE:
            default:
                jarvisOrb.setState(JarvisOrbView.OrbState.IDLE);
                waveformView.setWaveformState(false, false);
                btnStopSpeaking.setVisibility(View.GONE);
                stopMicPulse();
                break;
        }
    }

    private void stopMicPulse() {
        if (micPulseAnimator != null && micPulseAnimator.isRunning()) {
            micPulseAnimator.cancel();
            btnMic.setScaleX(1.0f);
            btnMic.setScaleY(1.0f);
        }
    }

    @Override
    public void onPartialTranscript(String partialText) {
        tvLiveTranscript.setText(partialText);
    }

    @Override
    public void onAudioLevelChanged(float normalizedLevel) {
        jarvisOrb.setAudioLevel(normalizedLevel);
        waveformView.pushAmplitude(normalizedLevel);
    }

    @Override
    public void onNewMessage(ChatMessage message) {
        chatAdapter.addMessage(message);
        scrollToBottom();
        if (ChatMessage.ROLE_USER.equals(message.getRole())) {
            tvLiveTranscript.setText("\"" + message.getContent() + "\"");
        }
    }

    @Override
    public void onConfirmationRequested(String promptText) {
        tvConfirmPrompt.setText(promptText);
        layoutConfirmation.setVisibility(View.VISIBLE);
    }

    @Override
    public void onConfirmationCleared() {
        layoutConfirmation.setVisibility(View.GONE);
    }

    @Override
    public void onVoiceDiagnostic(String message, boolean isFemaleVoiceMissing) {
        tvVoiceBadge.setText("Female Voice • " + ttsManager.getActiveVoiceDescription());
        if (isFemaleVoiceMissing && message != null && !message.isEmpty()) {
            tvVoiceWarning.setText(message + " Tap here for step-by-step Female Voice setup instructions.");
            tvVoiceWarning.setVisibility(View.VISIBLE);
            if (!installPromptShownThisSession && !isFinishing()) {
                installPromptShownThisSession = true;
                showFemaleVoiceInstallGuideDialog();
            }
        } else {
            tvVoiceWarning.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestMicPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new AlertDialog.Builder(this)
                    .setTitle("Voice Assistant Permission")
                    .setMessage(R.string.mic_permission_rationale)
                    .setPositiveButton("Allow Mic", (d, w) ->
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO))
                    .setNegativeButton("Not Now", null)
                    .show();
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    @Override
    public void onReplayVoice(ChatMessage message) {
        voiceAssistant.replayText(message.getContent());
    }

    @Override
    public void onSaveToMemory(ChatMessage message) {
        if (!memoryManager.isMemoryEnabled()) {
            Toast.makeText(this, "Enable Memory System in Settings first.", Toast.LENGTH_SHORT).show();
            return;
        }
        memoryManager.saveMemory(message.getContent(), "saved_reply");
        Toast.makeText(this, "Saved to SUBH AI Memory!", Toast.LENGTH_SHORT).show();
    }
}
