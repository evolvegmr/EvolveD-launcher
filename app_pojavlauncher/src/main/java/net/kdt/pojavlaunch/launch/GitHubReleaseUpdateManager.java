package net.kdt.pojavlaunch.launch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EvolveD Launcher self-updater backed by GitHub Releases.
 *
 * A new launcher release can be published to:
 * https://github.com/evolvegmr/EvolveD-launcher/releases
 * without requiring a launcher-code update. The launcher checks the public
 * GitHub Releases API and only offers an update when the release version is
 * newer than the installed version.
 */
public final class GitHubReleaseUpdateManager {
    private static final String TAG = "EvolveDUpdater";
    private static final String API_URL =
            "https://api.github.com/repos/evolvegmr/EvolveD-launcher/releases/latest";
    private static final String PREFS = "evolved_github_updater";
    private static final String KEY_LAST_CHECK = "last_check_ms";
    private static final long CHECK_INTERVAL_MS = 30L * 60L * 1000L;

    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);

    private GitHubReleaseUpdateManager() {}

    public static void checkOnLauncherOpen(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (!isCheckDue(activity)) return;
        if (!CHECKING.compareAndSet(false, true)) return;

        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

        new Thread(() -> {
            try {
                Release release = fetchLatestRelease();
                if (release == null || !isNewerVersion(release.version, BuildConfig.VERSION_NAME)) return;

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    showUpdateDialog(activity, release);
                });
            } catch (Throwable t) {
                Log.w(TAG, "GitHub release check failed", t);
            } finally {
                CHECKING.set(false);
            }
        }, "EvolveD-Update-Check").start();
    }

    private static boolean isCheckDue(Context ctx) {
        long last = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0L);
        return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS;
    }

    @Nullable
    private static Release fetchLatestRelease() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "EvolveD-Launcher-Updater");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned HTTP " + code);
                return null;
            }

            String json = readAll(conn.getInputStream(), 512 * 1024);
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) return null;

            String tag = root.optString("tag_name", "").trim();
            if (tag.isEmpty()) return null;

            String apkUrl = "";
            JSONArray assets = root.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    String browserUrl = asset.optString("browser_download_url", "");
                    if (name.toLowerCase().endsWith(".apk") && !browserUrl.isEmpty()) {
                        apkUrl = browserUrl;
                        break;
                    }
                }
            }

            return new Release(
                    tag,
                    root.optString("name", tag),
                    root.optString("body", ""),
                    root.optString("html_url", "https://github.com/evolvegmr/EvolveD-launcher/releases"),
                    apkUrl
            );
        } catch (Throwable t) {
            Log.w(TAG, "Could not parse GitHub latest release", t);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void showUpdateDialog(@NonNull Activity act, @NonNull Release release) {
        try {
            final android.app.Dialog dialog = new android.app.Dialog(
                    act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(R.layout.dialog_launcher_update);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            TextView title = dialog.findViewById(R.id.tv_update_title);
            TextView version = dialog.findViewById(R.id.tv_update_version);
            TextView changelog = dialog.findViewById(R.id.tv_update_changelog);
            View progressContainer = dialog.findViewById(R.id.update_progress_container);
            TextView progressText = dialog.findViewById(R.id.tv_update_progress_text);
            ProgressBar progressBar = dialog.findViewById(R.id.update_progress_bar);
            TextView later = dialog.findViewById(R.id.btn_update_later);
            TextView update = dialog.findViewById(R.id.btn_update_now);

            if (title != null) title.setText("EVOLVED LAUNCHER UPDATE");
            if (version != null) version.setText("New version available: " + release.version);
            if (changelog != null) {
                String body = release.body == null ? "" : release.body.trim();
                changelog.setText(TextUtils.isEmpty(body)
                        ? "A new EvolveD Launcher release is available."
                        : body);
            }

            if (later != null) {
                later.setOnClickListener(v -> {
                    act.finishAffinity();
                });
            }

            if (update != null) {
                update.setText(release.apkUrl.isEmpty() ? "VIEW RELEASE" : "UPDATE NOW");
                update.setOnClickListener(v -> {
                    if (release.apkUrl.isEmpty() || BuildConfig.APPLICATION_ID.endsWith(".debug")) {
                        openUrl(act, release.htmlUrl);
                        return;
                    }
                    downloadAndInstall(act, release, dialog, progressContainer,
                            progressText, progressBar, update, later);
                });
            }

            dialog.show();
        } catch (Throwable t) {
            Log.w(TAG, "Could not show EvolveD update dialog", t);
        }
    }

    private static void downloadAndInstall(Activity act, Release release,
                                           android.app.Dialog dialog, View progressContainer,
                                           TextView progressText, ProgressBar progressBar,
                                           TextView update, TextView later) {
        if (progressContainer != null) progressContainer.setVisibility(View.VISIBLE);
        if (update != null) {
            update.setEnabled(false);
            update.setText("DOWNLOADING...");
        }
        if (later != null) later.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setProgress(0);

        new Thread(() -> {
            File apk = null;
            try {
                File dir = act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = act.getCacheDir();
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create download directory");
                apk = new File(dir, "EvolveD-Launcher-" + safeFilePart(release.version) + ".apk");
                if (apk.exists()) apk.delete();

                HttpURLConnection conn = (HttpURLConnection) new URL(release.apkUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "EvolveD-Launcher-Updater");
                conn.connect();
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("Download failed: HTTP " + conn.getResponseCode());
                }

                int total = conn.getContentLength();
                long read = 0;
                int lastPercent = -1;
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        read += n;
                        if (total > 0) {
                            int p = (int) ((read * 100) / total);
                            if (p != lastPercent) {
                                lastPercent = p;
                                int finalP = p;
                                act.runOnUiThread(() -> {
                                    if (progressBar != null) progressBar.setProgress(finalP);
                                    if (progressText != null) progressText.setText("Downloading update... " + finalP + "%");
                                });
                            }
                        }
                    }
                } finally {
                    conn.disconnect();
                }

                File finalApk = apk;
                act.runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setProgress(100);
                    if (progressText != null) progressText.setText("Update downloaded. Installing...");
                    installApk(act, finalApk);

                });
            } catch (Throwable t) {
                Log.e(TAG, "Update download failed", t);
                String message = t.getMessage() == null ? "Download failed" : t.getMessage();
                act.runOnUiThread(() -> {
                    if (progressText != null) progressText.setText(message);
                    if (update != null) {
                        update.setText("RETRY");
                        update.setEnabled(true);
                        update.setOnClickListener(v -> downloadAndInstall(act, release, dialog,
                                progressContainer, progressText, progressBar, update, later));
                    }
                    if (later != null) later.setVisibility(View.VISIBLE);
                });
            }
        }, "EvolveD-Update-Download").start();
    }

    private static void installApk(Activity act, File apk) {
        if (apk == null || !apk.exists() || apk.length() < 1024) {
            new AlertDialog.Builder(act).setTitle("Update Error")
                    .setMessage("The downloaded update file is missing or invalid.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(act, act.getPackageName() + ".provider", apk);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "Could not start package installer", t);
            new AlertDialog.Builder(act).setTitle("Update Error")
                    .setMessage("Could not start the Android package installer.\n\n" + t.getMessage())
                    .setPositiveButton("OK", null).show();
        }
    }

    private static void openUrl(Context ctx, String url) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Log.w(TAG, "Could not open release URL", t);
        }
    }

    private static String readAll(InputStream in, int maxBytes) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) throw new IllegalStateException("Response too large");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }

    private static String safeFilePart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static boolean isNewerVersion(String remote, String local) {
        if (remote == null || local == null) return false;
        String r = remote.trim().replaceFirst("(?i)^v", "");
        String l = local.trim().replaceFirst("(?i)^v", "");
        try {
            String[] rp = r.split("[^0-9]+");
            String[] lp = l.split("[^0-9]+");
            int max = Math.max(rp.length, lp.length);
            for (int i = 0; i < max; i++) {
                int rv = i < rp.length && !rp[i].isEmpty() ? Integer.parseInt(rp[i]) : 0;
                int lv = i < lp.length && !lp[i].isEmpty() ? Integer.parseInt(lp[i]) : 0;
                if (rv != lv) return rv > lv;
            }
        } catch (Throwable ignored) {
            return !r.equalsIgnoreCase(l);
        }
        return false;
    }

    private static final class Release {
        final String version;
        final String name;
        final String body;
        final String htmlUrl;
        final String apkUrl;

        Release(String version, String name, String body, String htmlUrl, String apkUrl) {
            this.version = version;
            this.name = name;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.apkUrl = apkUrl;
        }
    }
}
