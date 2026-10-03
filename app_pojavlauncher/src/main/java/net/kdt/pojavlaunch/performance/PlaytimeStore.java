package net.kdt.pojavlaunch.performance;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Persistent local history of Minecraft sessions that reached a rendered game frame. */
public final class PlaytimeStore {
    private static final String PREFS = "evolved_playtime";
    private static final String KEY_SESSIONS = "sessions_v1";
    private static final String KEY_ACTIVE = "active_v1";
    private static final int MAX_SESSIONS = 1200;

    private PlaytimeStore() {}

    public static synchronized void start(Context context, String profile, String account,
                                          String version) {
        if (context == null) return;
        recoverExistingActive(context);
        long now = System.currentTimeMillis();
        try {
            JSONObject active = new JSONObject();
            active.put("start", now);
            active.put("checkpoint", now);
            active.put("pid", Process.myPid());
            active.put("profile", emptyTo(profile, "Minecraft"));
            active.put("account", emptyTo(account, ""));
            active.put("version", emptyTo(version, ""));
            prefs(context).edit().putString(KEY_ACTIVE, active.toString()).apply();
        } catch (org.json.JSONException e) {
            android.util.Log.e("PlaytimeStore", "Unable to persist active playtime session", e);
        }

        private static void recoverExistingActive(Context context) {
            String raw = prefs(context).getString(KEY_ACTIVE, null);
            if (raw == null) return;
            try {
                JSONObject active = new JSONObject(raw);
                long end = processIsRunning(active.optInt("pid", -1))
                        ? System.currentTimeMillis()
                        : active.optLong("checkpoint", active.optLong("start", 0L));
                closeActive(context, end);
            } catch (org.json.JSONException e) {
                android.util.Log.e("PlaytimeStore", "Unable to recover previous playtime session", e);
                prefs(context).edit().remove(KEY_ACTIVE).apply();
            }
        }
    }

    /** Call periodically while the game process is alive to bound crash recovery overcount. */
    public static synchronized void checkpoint(Context context) {
        if (context == null) return;
        try {
            SharedPreferences preferences = prefs(context);
            String raw = preferences.getString(KEY_ACTIVE, null);
            if (raw == null) return;
            JSONObject active = new JSONObject(raw);
            if (active.optInt("pid", -1) != Process.myPid()) return;
            active.put("checkpoint", System.currentTimeMillis());
            preferences.edit().putString(KEY_ACTIVE, active.toString()).apply();
        } catch (org.json.JSONException e) {
            android.util.Log.e("PlaytimeStore", "Unable to checkpoint playtime session", e);
        }
    }

    public static synchronized void stop(Context context) {
        if (context != null) closeActive(context, System.currentTimeMillis());
    }

    private static void closeActive(Context context, long end) {
        SharedPreferences preferences = prefs(context);
        String raw = preferences.getString(KEY_ACTIVE, null);
        if (raw == null) return;
        try {
            JSONObject active = new JSONObject(raw);
            long start = active.optLong("start", 0L);
            if (start <= 0L || end <= start) {
                preferences.edit().remove(KEY_ACTIVE).apply();
                return;
            }
            JSONArray sessions = load(context);
            sessions.put(toSession(active, Math.min(end, System.currentTimeMillis())));
            preferences.edit()
                    .putString(KEY_SESSIONS, trim(sessions).toString())
                    .remove(KEY_ACTIVE)
                    .apply();
        } catch (org.json.JSONException e) {
            android.util.Log.e("PlaytimeStore", "Unable to save completed playtime session", e);
            preferences.edit().remove(KEY_ACTIVE).apply();
        }
    }

    private static void closeStaleActive(Context context, JSONObject active) {
        SharedPreferences preferences = prefs(context);
        long start = active.optLong("start", 0L);
        long checkpoint = active.optLong("checkpoint", start);
        if (start <= 0L || checkpoint <= start) {
            preferences.edit().remove(KEY_ACTIVE).apply();
            return;
        }
        try {
            JSONArray sessions = load(context);
            sessions.put(toSession(active, checkpoint));
            preferences.edit()
                    .putString(KEY_SESSIONS, trim(sessions).toString())
                    .remove(KEY_ACTIVE)
                    .apply();
        } catch (org.json.JSONException e) {
            android.util.Log.e("PlaytimeStore", "Unable to recover interrupted playtime session", e);
            preferences.edit().remove(KEY_ACTIVE).apply();
        }
    }

    private static JSONObject toSession(JSONObject active, long end) throws org.json.JSONException {
        long start = active.optLong("start", 0L);
        JSONObject session = new JSONObject();
        session.put("start", start);
        session.put("end", end);
        session.put("duration", Math.max(0L, end - start));
        session.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                .format(new Date(start)));
        session.put("profile", emptyTo(active.optString("profile", null), "Minecraft"));
        session.put("account", emptyTo(active.optString("account", null), ""));
        session.put("version", emptyTo(active.optString("version", null), ""));
        return session;
    }

    private static JSONArray trim(JSONArray sessions) throws org.json.JSONException {
        int first = Math.max(0, sessions.length() - MAX_SESSIONS);
        JSONArray result = new JSONArray();
        for (int i = first; i < sessions.length(); i++) result.put(sessions.getJSONObject(i));
        return result;
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray load(Context context) {
        String raw = prefs(context).getString(KEY_SESSIONS, "[]");
        try {
            return new JSONArray(raw);
        } catch (org.json.JSONException e) {
            android.util.Log.e("PlaytimeStore", "Stored playtime history is invalid", e);
            return new JSONArray();
        }
    }

    public static synchronized Stats stats(Context context) {
        long now = System.currentTimeMillis();
        JSONArray sessions = load(context);
        List<JSONObject> records = new ArrayList<>();
        long earliest = Long.MAX_VALUE;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            if (session == null) continue;
            long start = session.optLong("start", 0L);
            long end = session.optLong("end", 0L);
            if (start <= 0L || end <= start || end > now) continue;
            records.add(session);
            earliest = Math.min(earliest, start);
        }

        SharedPreferences preferences = prefs(context);
        String activeRaw = preferences.getString(KEY_ACTIVE, null);
        if (activeRaw != null) {
            try {
                JSONObject active = new JSONObject(activeRaw);
                long start = active.optLong("start", 0L);
                if (start > 0L && start < now) {
                    if (processIsRunning(active.optInt("pid", -1))) {
                        JSONObject live = new JSONObject(active);
                        live.put("end", now);
                        records.add(live);
                        earliest = Math.min(earliest, start);
                    } else {
                        closeStaleActive(context, active);
                        JSONObject recovered = toSession(active,
                                active.optLong("checkpoint", start));
                        long recoveredEnd = recovered.optLong("end", 0L);
                        if (recoveredEnd > start) {
                            records.add(recovered);
                            earliest = Math.min(earliest, start);
                        }
                    }
                }
            } catch (org.json.JSONException e) {
                android.util.Log.e("PlaytimeStore", "Unable to read active playtime session", e);
                preferences.edit().remove(KEY_ACTIVE).apply();
            }
        }

        Calendar today = Calendar.getInstance();
        Calendar todayStart = startOfDay(today);
        Calendar weekStart = startOfWeek(today);
        long todayMs = 0L, weekMs = 0L, longestMs = 0L, totalMs = 0L;
        long[] daily = new long[7];
        Calendar day = (Calendar) weekStart.clone();
        for (int i = 0; i < daily.length; i++) {
            long dayStart = day.getTimeInMillis();
            day.add(Calendar.DAY_OF_YEAR, 1);
            long dayEnd = Math.min(day.getTimeInMillis(), now);
            if (dayStart < now) {
                for (JSONObject record : records) {
                    long start = record.optLong("start", 0L);
                    long end = Math.min(record.optLong("end", now), now);
                    daily[i] += overlap(start, end, dayStart, dayEnd);
                }
            }
        }

        for (JSONObject record : records) {
            long start = record.optLong("start", 0L);
            long end = Math.min(record.optLong("end", now), now);
            if (start <= 0L || end <= start) continue;
            long duration = end - start;
            totalMs += duration;
            longestMs = Math.max(longestMs, duration);
            todayMs += overlap(start, end, todayStart.getTimeInMillis(), now);
            weekMs += overlap(start, end, weekStart.getTimeInMillis(), now);
        }

        int trackedDays = 0;
        if (earliest != Long.MAX_VALUE) {
            Calendar firstDay = Calendar.getInstance();
            firstDay.setTimeInMillis(earliest);
            firstDay = startOfDay(firstDay);
            Calendar cursor = (Calendar) firstDay.clone();
            while (cursor.before(todayStart)) {
                trackedDays++;
                cursor.add(Calendar.DAY_OF_YEAR, 1);
            }
            trackedDays++;
        }
        return new Stats(records.size() > 0, todayMs,
                trackedDays == 0 ? 0L : totalMs / trackedDays,
                longestMs, weekMs, daily, trackedDays);
    }

    private static long overlap(long start, long end, long rangeStart, long rangeEnd) {
        return Math.max(0L, Math.min(end, rangeEnd) - Math.max(start, rangeStart));
    }

    private static Calendar startOfDay(Calendar calendar) {
        Calendar day = (Calendar) calendar.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    private static Calendar startOfWeek(Calendar calendar) {
        Calendar week = startOfDay(calendar);
        int offset = (week.get(Calendar.DAY_OF_WEEK) - week.getFirstDayOfWeek() + 7) % 7;
        week.add(Calendar.DAY_OF_YEAR, -offset);
        return week;
    }

    private static boolean processIsRunning(int pid) {
        if (pid <= 0) return false;
        File commandLine = new File("/proc/" + pid + "/cmdline");
        if (!commandLine.isFile()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(commandLine))) {
            String process = reader.readLine();
            return process != null && process.contains(":game");
        } catch (java.io.IOException e) {
            return false;
        }
    }

    public static String formatDuration(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        long hours = minutes / 60L;
        long mins = minutes % 60L;
        if (hours > 0L) return String.format(Locale.US, "%dh %02dm", hours, mins);
        return String.format(Locale.US, "%dm", mins);
    }

    public static String shortDuration(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 60L) return minutes + "m";
        return (minutes / 60L) + "h " + (minutes % 60L) + "m";
    }

    public static final class Stats {
        public final boolean hasData;
        public final long todayMs;
        public final long averageDailyMs;
        public final long longestMs;
        public final long weekMs;
        public final long[] dailyMs;
        public final int trackedDays;

        Stats(boolean hasData, long todayMs, long averageDailyMs, long longestMs,
              long weekMs, long[] dailyMs, int trackedDays) {
            this.hasData = hasData;
            this.todayMs = todayMs;
            this.averageDailyMs = averageDailyMs;
            this.longestMs = longestMs;
            this.weekMs = weekMs;
            this.dailyMs = dailyMs;
            this.trackedDays = trackedDays;
        }
    }
}
