package net.kdt.pojavlaunch.performance;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Persistent local Minecraft session history used by EvolveD Launcher's Playtime screen.
 *
 * Only real game-process sessions call start()/stop(). A dangling session is closed when the
 * next real game session starts, so an app/process kill does not permanently lose the previous
 * session. No cloud account or external service is involved.
 */
public final class PlaytimeStore {
    private static final String PREFS = "evolved_playtime";
    private static final String KEY_SESSIONS = "sessions_v1";
    private static final String KEY_ACTIVE = "active_v1";
    private static final int MAX_SESSIONS = 1200;

    private PlaytimeStore() {}

    public static synchronized void start(Context context, String profile, String account) {
        if (context == null) return;
        closeActive(context, System.currentTimeMillis());
        try {
            JSONObject active = new JSONObject();
            active.put("start", System.currentTimeMillis());
            active.put("profile", profile == null ? "Minecraft" : profile);
            active.put("account", account == null ? "" : account);
            prefs(context).edit().putString(KEY_ACTIVE, active.toString()).apply();
        } catch (Throwable ignored) {}
    }

    public static synchronized void stop(Context context) {
        if (context == null) return;
        closeActive(context, System.currentTimeMillis());
    }

    private static void closeActive(Context context, long end) {
        try {
            String raw = prefs(context).getString(KEY_ACTIVE, null);
            if (raw == null) return;
            JSONObject active = new JSONObject(raw);
            long start = active.optLong("start", 0);
            if (start <= 0 || end <= start) {
                prefs(context).edit().remove(KEY_ACTIVE).apply();
                return;
            }
            JSONArray sessions = load(context);
            JSONObject session = new JSONObject();
            session.put("start", start);
            session.put("end", end);
            session.put("duration", end - start);
            session.put("profile", active.optString("profile", "Minecraft"));
            session.put("account", active.optString("account", ""));
            sessions.put(session);

            // Keep the newest sessions only.
            int first = Math.max(0, sessions.length() - MAX_SESSIONS);
            JSONArray trimmed = new JSONArray();
            for (int i = first; i < sessions.length(); i++) trimmed.put(sessions.getJSONObject(i));
            prefs(context).edit()
                    .putString(KEY_SESSIONS, trimmed.toString())
                    .remove(KEY_ACTIVE)
                    .apply();
        } catch (Throwable ignored) {
            try { prefs(context).edit().remove(KEY_ACTIVE).apply(); } catch (Throwable ignored2) {}
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray load(Context context) {
        try { return new JSONArray(prefs(context).getString(KEY_SESSIONS, "[]")); }
        catch (Throwable ignored) { return new JSONArray(); }
    }

    public static Stats stats(Context context) {
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance();
        Calendar month = Calendar.getInstance();
        long todayStart = startOfDay(today);
        long monthStart = startOfMonth(month);
        long sevenStart = todayStart - 6L * 24L * 60L * 60L * 1000L;

        long todayMs = 0, monthMs = 0, weekMs = 0, longest = 0;
        int todayCount = 0, daysPlayed = 0;
        long[] daily = new long[7];
        boolean[] played = new boolean[7];

        JSONArray a = load(context);
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null) continue;
            long start = s.optLong("start", 0);
            long end = s.optLong("end", 0);
            long duration = Math.max(0, s.optLong("duration", end - start));
            if (start <= 0 || duration <= 0) continue;

            if (duration > longest) longest = duration;
            if (start >= monthStart) monthMs += duration;
            if (start >= todayStart) {
                todayMs += duration;
                todayCount++;
            }
            if (start >= sevenStart && start < now) {
                weekMs += duration;
                int index = (int)((start - sevenStart) / (24L * 60L * 60L * 1000L));
                if (index >= 0 && index < 7) {
                    daily[index] += duration;
                    played[index] = true;
                }
            }
        }
        for (boolean p : played) if (p) daysPlayed++;

        long avgToday = todayCount == 0 ? 0 : todayMs / todayCount;
        int elapsedDays = Math.max(1, month.get(Calendar.DAY_OF_MONTH));
        long avgMonth = monthMs / elapsedDays;
        return new Stats(todayMs, avgToday, avgMonth, longest, daysPlayed, weekMs, daily);
    }

    private static long startOfDay(Calendar c) {
        Calendar x = (Calendar)c.clone();
        x.set(Calendar.HOUR_OF_DAY, 0);
        x.set(Calendar.MINUTE, 0);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    private static long startOfMonth(Calendar c) {
        Calendar x = (Calendar)c.clone();
        x.set(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(x);
    }

    public static String formatDuration(long ms) {
        long minutes = Math.max(0, ms / 60000L);
        long hours = minutes / 60L;
        long mins = minutes % 60L;
        if (hours > 0) return String.format(Locale.US, "%dh %02dm", hours, mins);
        return String.format(Locale.US, "%dm", mins);
    }

    public static String shortDuration(long ms) {
        long minutes = Math.max(0, ms / 60000L);
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    public static final class Stats {
        public final long todayMs, avgTodayMs, avgMonthMs, longestMs, weekMs;
        public final int daysPlayed;
        public final long[] dailyMs;

        Stats(long todayMs, long avgTodayMs, long avgMonthMs, long longestMs,
              int daysPlayed, long weekMs, long[] dailyMs) {
            this.todayMs = todayMs;
            this.avgTodayMs = avgTodayMs;
            this.avgMonthMs = avgMonthMs;
            this.longestMs = longestMs;
            this.daysPlayed = daysPlayed;
            this.weekMs = weekMs;
            this.dailyMs = dailyMs;
        }
    }
}
