# SUBH AI Secure Gemini Backend

This lightweight Express proxy keeps your `GEMINI_API_KEY` safely on the server so no private API key is ever bundled inside the Android APK.

## Deploy
1. Set environment variable `GEMINI_API_KEY` on your hosting provider (Cloud Run, Render, Railway, Fly.io, etc.).
2. Install & start:
   ```bash
   cd backend
   npm install
   npm start
   ```
3. Copy your deployed HTTPS endpoint URL (e.g. `https://your-service.run.app/api/chat`) into **SUBH AI → Settings → Secure Gemini AI Backend** and tap **Test Backend**.
