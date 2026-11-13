package com.example.util;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handles supported Android Intents for launching apps, camera, calculator,
 * settings, maps, YouTube/Google voice search, and telling date/time.
 */
public class PhoneControlManager {

    private static final Map<String, String[]> KNOWN_APPS = new LinkedHashMap<>();

    static {
        KNOWN_APPS.put("youtube", new String[]{"com.google.android.youtube"});
        KNOWN_APPS.put("यूट्यूब", new String[]{"com.google.android.youtube"});
        KNOWN_APPS.put("maps", new String[]{"com.google.android.apps.maps"});
        KNOWN_APPS.put("google maps", new String[]{"com.google.android.apps.maps"});
        KNOWN_APPS.put("मैप्स", new String[]{"com.google.android.apps.maps"});
        KNOWN_APPS.put("chrome", new String[]{"com.android.chrome"});
        KNOWN_APPS.put("whatsapp", new String[]{"com.whatsapp"});
        KNOWN_APPS.put("व्हाट्सएप", new String[]{"com.whatsapp"});
        KNOWN_APPS.put("gmail", new String[]{"com.google.android.gm"});
        KNOWN_APPS.put("clock", new String[]{"com.google.android.deskclock"});
        KNOWN_APPS.put("calendar", new String[]{"com.google.android.calendar"});
        KNOWN_APPS.put("spotify", new String[]{"com.spotify.music"});
        KNOWN_APPS.put("instagram", new String[]{"com.instagram.android"});
        KNOWN_APPS.put("photos", new String[]{"com.google.android.apps.photos"});
        KNOWN_APPS.put("google", new String[]{"com.google.android.googlequicksearchbox", "com.android.chrome"});
        KNOWN_APPS.put("गूगल", new String[]{"com.google.android.googlequicksearchbox", "com.android.chrome"});
        KNOWN_APPS.put("calculator", new String[]{
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator"
        });
        KNOWN_APPS.put("कैलकुलेटर", new String[]{
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator"
        });
    }

    private final Context context;

    public PhoneControlManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean openYouTube() {
        if (launchPackage("com.google.android.youtube")) {
            return true;
        }
        return openUrl("https://m.youtube.com");
    }

    public boolean searchYouTube(String query) {
        String encoded = Uri.encode(query == null ? "" : query.trim());
        Intent appIntent = new Intent(Intent.ACTION_SEARCH);
        appIntent.setPackage("com.google.android.youtube");
        appIntent.putExtra(SearchManager.QUERY, query);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (canHandle(appIntent)) {
            context.startActivity(appIntent);
            return true;
        }
        return openUrl("https://www.youtube.com/results?search_query=" + encoded);
    }

    public boolean openGoogle() {
        if (launchPackage("com.google.android.googlequicksearchbox")) {
            return true;
        }
        return openUrl("https://www.google.com");
    }

    public boolean searchGoogle(String query) {
        String cleanQuery = query == null ? "" : query.trim();
        Intent webSearchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        webSearchIntent.putExtra(SearchManager.QUERY, cleanQuery);
        webSearchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (canHandle(webSearchIntent)) {
            context.startActivity(webSearchIntent);
            return true;
        }
        return openUrl("https://www.google.com/search?q=" + Uri.encode(cleanQuery));
    }

    public boolean openCamera() {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (canHandle(intent)) {
            context.startActivity(intent);
            return true;
        }
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        captureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (canHandle(captureIntent)) {
            context.startActivity(captureIntent);
            return true;
        }
        return false;
    }

    public boolean openCalculator() {
        Intent categoryIntent = new Intent(Intent.ACTION_MAIN);
        categoryIntent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
        categoryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (canHandle(categoryIntent)) {
            context.startActivity(categoryIntent);
            return true;
        }
        String[] pkgs = KNOWN_APPS.get("calculator");
        if (pkgs != null) {
            for (String pkg : pkgs) {
                if (launchPackage(pkg)) return true;
            }
        }
        return false;
    }

    public boolean openSettings() {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (canHandle(intent)) {
            context.startActivity(intent);
            return true;
        }
        return false;
    }

    public boolean openMaps() {
        if (launchPackage("com.google.android.apps.maps")) {
            return true;
        }
        Intent geoIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
        geoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (canHandle(geoIntent)) {
            context.startActivity(geoIntent);
            return true;
        }
        return openUrl("https://maps.google.com");
    }

    public boolean openSupportedApp(String spokenAppName) {
        if (spokenAppName == null) return false;
        String lower = spokenAppName.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : KNOWN_APPS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                for (String pkg : entry.getValue()) {
                    if (launchPackage(pkg)) {
                        return true;
                    }
                }
            }
        }
        if (lower.contains("camera") || lower.contains("कैमरा")) return openCamera();
        if (lower.contains("setting") || lower.contains("सेटिंग")) return openSettings();
        if (lower.contains("dialer") || lower.contains("phone")) {
            Intent dial = new Intent(Intent.ACTION_DIAL);
            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (canHandle(dial)) {
                context.startActivity(dial);
                return true;
            }
        }
        return false;
    }

    public String getFormattedDateTime(boolean preferHindi) {
        Date now = new Date();
        if (preferHindi) {
            SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("hi", "IN"));
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            return "अभी समय " + timeFmt.format(now) + " है, और आज की तारीख " + dateFmt.format(now) + " है।";
        } else {
            SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH);
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            return "The current time is " + timeFmt.format(now) + ", and today is " + dateFmt.format(now) + ".";
        }
    }

    private boolean launchPackage(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (canHandle(intent)) {
                context.startActivity(intent);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean canHandle(Intent intent) {
        PackageManager pm = context.getPackageManager();
        return intent.resolveActivity(pm) != null;
    }
}
