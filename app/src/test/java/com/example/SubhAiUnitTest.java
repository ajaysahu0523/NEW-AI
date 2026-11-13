package com.example;

import com.example.util.MathEvaluator;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SubhAiUnitTest {

    @Test
    public void testMathEvaluator_arithmeticAndPercentages() {
        Double r1 = MathEvaluator.tryEvaluate("18% of 2500");
        assertNotNull(r1);
        assertEquals(450.0, r1, 0.0001);

        Double r2 = MathEvaluator.tryEvaluate("2500 ka 18 percent");
        assertNotNull(r2);
        assertEquals(450.0, r2, 0.0001);

        Double r3 = MathEvaluator.tryEvaluate("square root of 144");
        assertNotNull(r3);
        assertEquals(12.0, r3, 0.0001);

        Double r4 = MathEvaluator.tryEvaluate("(12 + 8) * 5 / 2");
        assertNotNull(r4);
        assertEquals("50", MathEvaluator.formatNumber(r4));

        assertNull(MathEvaluator.tryEvaluate("What is the capital of India?"));
    }

    @Test
    public void testWakeAndSleepPhraseMatchers() {
        assertTrue(SpeechRecognition.matchesWakePhrase("Hey SUBH"));
        assertTrue(SpeechRecognition.matchesWakePhrase("hey shubh please listen"));
        assertTrue(SpeechRecognition.matchesWakePhrase("हे शुभ"));
        assertFalse(SpeechRecognition.matchesWakePhrase("Hello world"));

        assertTrue(SpeechRecognition.matchesSleepPhrase("Good Night SUBH"));
        assertTrue(SpeechRecognition.matchesSleepPhrase("goodnight shubh"));
        assertTrue(SpeechRecognition.matchesSleepPhrase("गुड नाइट शुभ"));
        assertFalse(SpeechRecognition.matchesSleepPhrase("Good morning"));
    }

    @Test
    public void testGenuineFemaleVoiceDetection_andMaleRejection() {
        assertTrue(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("hi-in-x-hia-local", null));
        assertTrue(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("hi-in-x-hic-network", null));
        assertTrue(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("en-us-x-sfg-local", null));
        assertTrue(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("en-us-x-tpf-local", null));
        assertTrue(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("en-in-x-ena-local", null));
        assertTrue(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("custom-voice", Collections.singleton("gender=female")));

        assertFalse(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("hi-in-x-hid-local", null));
        assertFalse(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("en-us-x-iom-local", null));
        assertFalse(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("en-in-x-end-local", null));
        assertFalse(TextToSpeechManager.isGenuineFemaleVoiceNameAndFeatures("generic-voice", Collections.singleton("gender=male")));
    }

    @Test
    public void testHindiEnglishHinglishDetection_andSpeechChunking() {
        assertEquals(TextToSpeechManager.SpeechLanguage.HINDI,
                TextToSpeechManager.detectSpeechLanguage("नमस्ते, मैं शुभ एआई हूँ।", MemoryManager.LANG_AUTO));
        assertEquals(TextToSpeechManager.SpeechLanguage.HINGLISH,
                TextToSpeechManager.detectSpeechLanguage("Namaste aap kaise hain?", MemoryManager.LANG_AUTO));
        assertEquals(TextToSpeechManager.SpeechLanguage.ENGLISH_US,
                TextToSpeechManager.detectSpeechLanguage("Quantum computing uses qubits to solve complex problems.", MemoryManager.LANG_AUTO));

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longText.append("This is sentence number ").append(i).append(" in a detailed explanation. ");
        }
        List<String> chunks = TextToSpeechManager.splitIntoSpeakableChunks(longText.toString(), 400);
        assertTrue(chunks.size() > 2);
        for (String c : chunks) {
            assertTrue(c.length() <= 405);
        }
    }
}
