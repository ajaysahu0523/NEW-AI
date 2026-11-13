const express = require('express');

const app = express();
app.use(express.json({ limit: '1mb' }));

const PORT = process.env.PORT || 8080;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-2.5-flash';

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'SUBH AI Secure Backend' });
});

app.post('/api/chat', async (req, res) => {
  if (!GEMINI_API_KEY) {
    return res.status(500).json({
      error: 'GEMINI_API_KEY is not configured on the server.'
    });
  }

  const { systemInstruction, userMessage, history } = req.body || {};
  if (!userMessage || typeof userMessage !== 'string') {
    return res.status(400).json({ error: 'userMessage is required.' });
  }

  const contents = [];
  if (Array.isArray(history)) {
    for (const turn of history) {
      if (turn && turn.text) {
        contents.push({
          role: turn.role === 'model' ? 'model' : 'user',
          parts: [{ text: String(turn.text) }]
        });
      }
    }
  }
  contents.push({
    role: 'user',
    parts: [{ text: userMessage }]
  });

  const payload = {
    contents,
    generationConfig: {
      temperature: 0.7,
      maxOutputTokens: 1024
    }
  };

  if (systemInstruction) {
    payload.systemInstruction = {
      parts: [{ text: String(systemInstruction) }]
    };
  }

  try {
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${encodeURIComponent(GEMINI_API_KEY)}`;
    const upstream = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const data = await upstream.json();
    if (!upstream.ok) {
      return res.status(upstream.status).json({
        error: data?.error?.message || 'Gemini upstream error'
      });
    }

    const reply = data?.candidates?.[0]?.content?.parts?.[0]?.text || '';
    if (!reply) {
      return res.status(502).json({ error: 'Empty response from Gemini.' });
    }

    return res.json({ reply });
  } catch (err) {
    return res.status(500).json({ error: err.message || 'Server error' });
  }
});

app.listen(PORT, () => {
  console.log(`SUBH AI secure backend listening on port ${PORT}`);
});
