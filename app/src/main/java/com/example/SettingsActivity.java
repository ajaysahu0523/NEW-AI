package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings dashboard for Female Voice selection, speed/pitch/volume,
 * Hindi/English language, Wake Word, Memory ON/OFF, Light/Dark theme,
 * Secure Gemini Backend URL, and Privacy controls.
 */
public class SettingsActivity extends AppCompatActivity {

    private MemoryManager memoryManager;
    private TextToSpeechManager ttsManager;
    private GeminiAIService geminiService;

    private TextView tvTtsDiagnostic;
    private Spinner spinnerLanguage;
    private Spinner spinnerVoice;
    private TextView tvSpeedValue;
    private TextView tvPitchValue;
    private TextView tvVolumeValue;
    private EditText etBackendUrl;
    private TextView tvBackendStatus;

    private final List<TextToSpeechManager.VoiceOption> voiceOptions = new ArrayList<>();
    private boolean initializingSpinners = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        AppController app = AppController.getInstance();
        memoryManager = app.getMemoryManager();
        ttsManager = app.getTtsManager();
        geminiService = app.getGeminiService();

        View root = findViewById(R.id.root_settings);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        bindViews();
        setupLanguageSpinner();
        setupVoiceSpinner();
        setupSeekBars();
        setupSwitches();
        setupBackendControls();
        setupPrivacyButtons();
    }

    private void bindViews() {
        findViewById(R.id.btn_settings_back).setOnClickListener(v -> finish());

        tvTtsDiagnostic = findViewById(R.id.tv_tts_diagnostic);
        spinnerLanguage = findViewById(R.id.spinner_language);
        spinnerVoice = findViewById(R.id.spinner_voice);
        tvSpeedValue = findViewById(R.id.tv_speed_value);
        tvPitchValue = findViewById(R.id.tv_pitch_value);
        tvVolumeValue = findViewById(R.id.tv_volume_value);
        etBackendUrl = findViewById(R.id.et_backend_url);
        tvBackendStatus = findViewById(R.id.tv_backend_status);

        tvTtsDiagnostic.setText(ttsManager.getDiagnosticMessage());

        findViewById(R.id.btn_test_voice).setOnClickListener(v -> ttsManager.testFemaleVoice());
        findViewById(R.id.btn_tts_settings).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Install Female Voice")
                        .setMessage(ttsManager.getFemaleVoiceInstallInstructions())
                        .setPositiveButton("Open TTS Settings", (d, w) -> TextToSpeechManager.openTtsSettings(this))
                        .setNegativeButton("Close", null)
                        .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ttsManager != null) {
            ttsManager.refreshAvailableVoices();
            tvTtsDiagnostic.setText(ttsManager.getDiagnosticMessage());
        }
    }

    private void setupLanguageSpinner() {
        String[] labels = new String[]{
                "Auto (Hindi + English + Hinglish)",
                "Hindi (हिन्दी)",
                "English (US)",
                "English (India)"
        };
        String[] codes = new String[]{
                MemoryManager.LANG_AUTO,
                MemoryManager.LANG_HINDI,
                MemoryManager.LANG_ENGLISH,
                MemoryManager.LANG_ENGLISH_IN
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);

        String currentLang = memoryManager.getLanguageMode();
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLang)) {
                spinnerLanguage.setSelection(i, false);
                break;
            }
        }

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initializingSpinners) {
                    memoryManager.setLanguageMode(codes[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupVoiceSpinner() {
        ttsManager.refreshAvailableVoices();
        voiceOptions.clear();
        voiceOptions.addAll(ttsManager.getAvailableVoiceOptions());

        List<String> labels = new ArrayList<>();
        labels.add("Auto-Select Best Female Voice (Recommended)");
        for (TextToSpeechManager.VoiceOption opt : voiceOptions) {
            labels.add(opt.displayLabel);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(adapter);

        String savedVoice = memoryManager.getSelectedVoiceName();
        int selectedIdx = 0;
        if (savedVoice != null && !savedVoice.isEmpty()) {
            for (int i = 0; i < voiceOptions.size(); i++) {
                if (voiceOptions.get(i).voice.getName().equals(savedVoice)) {
                    selectedIdx = i + 1;
                    break;
                }
            }
        }
        spinnerVoice.setSelection(selectedIdx, false);

        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (initializingSpinners) return;
                if (position == 0) {
                    ttsManager.selectVoiceByName("");
                } else {
                    ttsManager.selectVoiceByName(voiceOptions.get(position - 1).voice.getName());
                }
                tvTtsDiagnostic.setText(ttsManager.getDiagnosticMessage());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerVoice.post(() -> initializingSpinners = false);
    }

    private void setupSeekBars() {
        // Speed: 0.60 .. 1.80 -> SeekBar 0..120
        SeekBar seekSpeed = findViewById(R.id.seek_voice_speed);
        float speed = memoryManager.getVoiceSpeed();
        seekSpeed.setProgress(Math.round((speed - 0.6f) * 100f));
        tvSpeedValue.setText(String.format(Locale.US, "%.2fx", speed));
        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.6f + (progress / 100f);
                tvSpeedValue.setText(String.format(Locale.US, "%.2fx", val));
                if (fromUser) {
                    memoryManager.setVoiceSpeed(val);
                    ttsManager.applySavedVoiceParameters();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Pitch: 0.60 .. 1.60 -> SeekBar 0..100
        SeekBar seekPitch = findViewById(R.id.seek_voice_pitch);
        float pitch = memoryManager.getVoicePitch();
        seekPitch.setProgress(Math.round((pitch - 0.6f) * 100f));
        tvPitchValue.setText(String.format(Locale.US, "%.2f", pitch));
        seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.6f + (progress / 100f);
                tvPitchValue.setText(String.format(Locale.US, "%.2f", val));
                if (fromUser) {
                    memoryManager.setVoicePitch(val);
                    ttsManager.applySavedVoiceParameters();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Volume: 10% .. 100% -> SeekBar 0..100
        SeekBar seekVolume = findViewById(R.id.seek_voice_volume);
        int volPct = Math.round(memoryManager.getVoiceVolume() * 100f);
        seekVolume.setProgress(volPct);
        tvVolumeValue.setText(volPct + "%");
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int clamped = Math.max(10, progress);
                tvVolumeValue.setText(clamped + "%");
                if (fromUser) {
                    memoryManager.setVoiceVolume(clamped / 100f);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupSwitches() {
        MaterialSwitch switchConvo = findViewById(R.id.switch_conversation_mode);
        switchConvo.setChecked(memoryManager.isConversationModeEnabled());
        switchConvo.setOnCheckedChangeListener((b, isChecked) ->
                memoryManager.setConversationModeEnabled(isChecked));

        MaterialSwitch switchWake = findViewById(R.id.switch_wake_word);
        switchWake.setChecked(memoryManager.isWakeWordEnabled());
        switchWake.setOnCheckedChangeListener((b, isChecked) -> {
            memoryManager.setWakeWordEnabled(isChecked);
            if (isChecked) {
                Toast.makeText(this,
                        "Wake Phrase \"Hey SUBH\" enabled while app is open on screen.",
                        Toast.LENGTH_LONG).show();
            }
        });

        MaterialSwitch switchMemory = findViewById(R.id.switch_memory_enabled);
        switchMemory.setChecked(memoryManager.isMemoryEnabled());
        switchMemory.setOnCheckedChangeListener((b, isChecked) ->
                memoryManager.setMemoryEnabled(isChecked));

        MaterialSwitch switchDark = findViewById(R.id.switch_dark_theme);
        switchDark.setChecked(memoryManager.isDarkThemeEnabled());
        switchDark.setOnCheckedChangeListener((b, isChecked) -> {
            memoryManager.setDarkThemeEnabled(isChecked);
            AppController.getInstance().applySavedTheme();
        });
    }

    private void setupBackendControls() {
        String existingUrl = memoryManager.getBackendUrl();
        etBackendUrl.setText(existingUrl);
        tvBackendStatus.setText(geminiService.isBackendConfigured()
                ? "Status: HTTPS backend configured."
                : "Status: Not configured yet. Deploy the included backend proxy and paste its HTTPS URL above.");

        findViewById(R.id.btn_save_backend).setOnClickListener(v -> {
            String url = etBackendUrl.getText() != null ? etBackendUrl.getText().toString().trim() : "";
            if (!url.isEmpty() && !url.startsWith("https://")) {
                tvBackendStatus.setText("Error: URL must start with https:// for secure communication.");
                return;
            }
            memoryManager.setBackendUrl(url);
            tvBackendStatus.setText(url.isEmpty()
                    ? "Backend URL cleared."
                    : "Saved HTTPS backend URL.");
            Toast.makeText(this, "Backend URL saved", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_test_backend).setOnClickListener(v -> {
            String url = etBackendUrl.getText() != null ? etBackendUrl.getText().toString().trim() : "";
            if (!url.isEmpty()) {
                memoryManager.setBackendUrl(url);
            }
            tvBackendStatus.setText("Testing connection to secure Gemini backend…");
            geminiService.testBackendConnection(new GeminiAIService.AIResponseCallback() {
                @Override
                public void onSuccess(String replyText) {
                    tvBackendStatus.setText("Connected: " + replyText);
                }

                @Override
                public void onError(String errorMessage) {
                    tvBackendStatus.setText("Connection failed: " + errorMessage);
                }
            });
        });
    }

    private void setupPrivacyButtons() {
        findViewById(R.id.btn_view_memories).setOnClickListener(v ->
                startActivity(new Intent(this, MemoryActivity.class)));

        findViewById(R.id.btn_clear_chat).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Clear Chat History?")
                        .setMessage("This removes all saved conversation messages from local storage.")
                        .setPositiveButton("Clear", (d, w) -> {
                            memoryManager.clearChatHistory();
                            Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        findViewById(R.id.btn_reset_all).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Reset All Local Data?")
                        .setMessage("This erases all memories, chat history, and preferences on this device.")
                        .setPositiveButton("Reset Everything", (d, w) -> {
                            memoryManager.resetAllLocalData();
                            AppController.getInstance().applySavedTheme();
                            Toast.makeText(this, "All local SUBH AI data reset", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
    }
}
