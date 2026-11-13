package com.example;

import android.content.Context;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.example.model.ChatMessage;
import com.example.model.MemoryItem;
import com.example.util.PhoneControlManager;
import com.google.android.material.button.MaterialButton;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SubhAiRobolectricTest {

    @Test
    public void testAppNameAndGreetingResources() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("SUBH AI", context.getString(R.string.app_name));
        assertTrue(context.getString(R.string.default_greeting).contains("SUBH AI"));
    }

    @Test
    public void testMemoryManager_defaultsAndCrud() {
        Context context = ApplicationProvider.getApplicationContext();
        MemoryManager mm = new MemoryManager(context);
        mm.resetAllLocalData();

        // Natural default pitch (1.00f) and gentle speaking speed (0.94f)
        assertEquals(1.00f, mm.getVoicePitch(), 0.001f);
        assertEquals(0.94f, mm.getVoiceSpeed(), 0.001f);
        assertEquals(1.00f, mm.getVoiceVolume(), 0.001f);

        assertTrue(mm.isMemoryEnabled());
        long id = mm.saveMemory("User prefers Hindi responses", "preference");
        assertTrue(id > 0);

        List<MemoryItem> items = mm.getAllMemories();
        assertEquals(1, items.size());
        assertEquals("User prefers Hindi responses", items.get(0).getFact());

        mm.saveMessage(new ChatMessage(ChatMessage.ROLE_USER, "Namaste", 1000L, "hi-IN"));
        mm.saveMessage(new ChatMessage(ChatMessage.ROLE_ASSISTANT, "नमस्ते!", 2000L, "hi-IN"));
        assertEquals(2, mm.getAllMessages().size());

        mm.deleteMemory(id);
        assertTrue(mm.getAllMemories().isEmpty());

        mm.clearChatHistory();
        assertTrue(mm.getAllMessages().isEmpty());

        mm.setVoiceSpeed(1.15f);
        mm.setVoicePitch(1.05f);
        assertEquals(1.15f, mm.getVoiceSpeed(), 0.01f);
        assertEquals(1.05f, mm.getVoicePitch(), 0.01f);
    }

    @Test
    public void testPhoneControl_dateTimeHindiAndEnglish() {
        Context context = ApplicationProvider.getApplicationContext();
        PhoneControlManager pcm = new PhoneControlManager(context);

        String hi = pcm.getFormattedDateTime(true);
        String en = pcm.getFormattedDateTime(false);
        assertTrue(hi.contains("अभी समय"));
        assertTrue(en.contains("current time"));
    }

    @Test
    public void testMainActivity_voiceControlsAndQuickCalculation() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity);

        TextView stateView = activity.findViewById(R.id.tv_assistant_state);
        assertNotNull(stateView);

        ImageButton micBtn = activity.findViewById(R.id.btn_mic);
        assertNotNull(micBtn);

        MaterialButton testVoiceBtn = activity.findViewById(R.id.btn_quick_test_voice);
        MaterialButton voiceControlsBtn = activity.findViewById(R.id.btn_quick_voice_controls);
        assertNotNull(testVoiceBtn);
        assertNotNull(voiceControlsBtn);
        testVoiceBtn.performClick();

        TextView calcChip = activity.findViewById(R.id.chip_quick_calc);
        calcChip.performClick();

        MemoryManager mm = AppController.getInstance().getMemoryManager();
        List<ChatMessage> msgs = mm.getAllMessages();
        boolean found450 = false;
        for (ChatMessage m : msgs) {
            if (m.getContent() != null && m.getContent().contains("450")) {
                found450 = true;
                break;
            }
        }
        assertTrue("Offline quick calculation should produce 450 in chat history", found450);
    }

    @Test
    public void testSettingsAndMemoryActivitiesLaunch() {
        SettingsActivity settings = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        assertNotNull(settings);
        assertNotNull(settings.findViewById(R.id.btn_test_voice));
        assertNotNull(settings.findViewById(R.id.spinner_voice));

        MemoryActivity memoryActivity = Robolectric.buildActivity(MemoryActivity.class).setup().get();
        assertNotNull(memoryActivity);
        assertNotNull(memoryActivity.findViewById(R.id.rv_memories));
        assertFalse(memoryActivity.isFinishing());
    }
}
