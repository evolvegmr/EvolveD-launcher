package net.kdt.pojavlaunch.launch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
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
            } catch (Exception t) {
                Log.w(TAG, "GitHub release check failed", t);
            } finally {
                CHECKING.set(false);
            }
        }, "EvolveD-Update-Check").start();
    }

    public static void checkManually(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (!CHECKING.compareAndSet(false, true)) {
            new AlertDialog.Builder(activity)
                    .setTitle("Update check in progress")
                    .setMessage("An EvolveD Launcher update check is already running.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        new Thread(() -> {
            try {
                Release release = fetchLatestRelease();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (release == null) {
                        showCheckMessage(activity, "No stable EvolveD Launcher release is currently available.");
                    } else if (isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
                        showUpdateDialog(activity, release);
                    } else {
                        showCheckMessage(activity, "EvolveD Launcher is up to date ("
                                + BuildConfig.VERSION_NAME + ").");
                    }
                });
            } catch (Exception t) {
                Log.w(TAG, "Manual GitHub release check failed", t);
                String detail = t.getMessage();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    showCheckMessage(activity, "Unable to check for EvolveD Launcher updates."
                            + (TextUtils.isEmpty(detail) ? "" : "\n\n" + detail));
                });
            } finally {
                CHECKING.set(false);
            }
        }, "EvolveD-Manual-Update-Check").start();
    }

    private static void showCheckMessage(@NonNull Activity activity, @NonNull String message) {
        new AlertDialog.Builder(activity)
                .setTitle("EvolveD Launcher Updates")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static boolean isCheckDue(Context ctx) {
        long last = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0L);
        return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS;
    }

    @Nullable
    private static Release fetchLatestRelease() throws Exception {
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
                throw new IOException("GitHub returned HTTP " + code);
            }

            String json = readAll(conn.getInputStream(), 512 * 1024);
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) return null;

            String tag = root.optString("tag_name", "").trim();
            if (tag.isEmpty()) throw new IOException("GitHub release metadata did not include a tag.");

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
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void showUpdateDialog(@NonNull Activity act, @NonNull Release release) {
        try {
            final android.app.Dialog dialog = new android.app.Dialog(
                    act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(R.layout.dialog_launcher_update);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

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
                    dialog.dismiss();
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
                    if (progressText != null) progressText.setText("Verifying downloaded APK...");
                });
                verifyDownloadedApk(act, finalApk);
                act.runOnUiThread(() -> {
                    if (progressText != null) progressText.setText("APK verified. Opening Android installer...");
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

    private static void verifyDownloadedApk(Context context, File apk) throws Exception {
        if (apk == null || !apk.isFile() || apk.length() < 1024) {
            throw new IOException("The downloaded update file is missing or invalid.");
        }

        PackageManager packageManager = context.getPackageManager();
        PackageInfo candidate = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)) {
            throw new IOException("The downloaded APK is not an EvolveD Launcher package.");
        }

        long candidateVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? candidate.getLongVersionCode() : candidate.versionCode;
        if (candidateVersion <= BuildConfig.VERSION_CODE) {
            throw new IOException("The downloaded APK does not have a newer version code.");
        }

        PackageInfo installed = packageManager.getPackageInfo(context.getPackageName(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        if (!hasCompatibleSigningIdentity(installed, candidate)) {
            throw new IOException("The downloaded APK is not signed with a compatible launcher signing key.");
        }
    }

    private static boolean hasCompatibleSigningIdentity(PackageInfo installed, PackageInfo candidate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (installed.signingInfo == null || candidate.signingInfo == null) return false;
            Signature[] installedSigners = installed.signingInfo.getApkContentsSigners();
            if (installedSigners == null || installedSigners.length == 0) return false;
            for (Signature signer : installedSigners) {
                boolean found = false;
            Signature[] candidateSigners = candidate.signingInfo.getApkContentsSigners();
            if (candidateSigners == null || candidateSigners.length == 0) return false;

            for (Signature candidateSigner : candidateSigners) {
                if (Arrays.equals(signer.toByteArray(), candidateSigner.toByteArray())) {
                    found = true;
                    break;
                }
            }

            if (!found) return false;
            }
            return true;
        }

        Signature[] installedSigners = installed.signatures;
        Signature[] candidateSigners = candidate.signatures;
        return installedSigners != null && candidateSigners != null
                && installedSigners.length > 0
                && Arrays.equals(installedSigners, candidateSigners);
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
        String[] remoteVersion = r.split("\\+", 2)[0].split("-", 2);
        String[] localVersion = l.split("\\+", 2)[0].split("-", 2);
        String[] remoteNumbers = remoteVersion[0].split("\\.", -1);
        String[] localNumbers = localVersion[0].split("\\.", -1);
        int coreLength = Math.max(remoteNumbers.length, localNumbers.length);

        for (int i = 0; i < coreLength; i++) {
            BigInteger remotePart = versionNumber(remoteNumbers, i);
            BigInteger localPart = versionNumber(localNumbers, i);
            if (remotePart == null || localPart == null) return false;
            int comparison = remotePart.compareTo(localPart);
            if (comparison != 0) return comparison > 0;
        }

        boolean remotePrerelease = remoteVersion.length > 1;
        boolean localPrerelease = localVersion.length > 1;
        if (remotePrerelease != localPrerelease) return !remotePrerelease;
        if (!remotePrerelease) return false;

        String[] remoteIdentifiers = remoteVersion[1].split("\\.", -1);
        String[] localIdentifiers = localVersion[1].split("\\.", -1);
        int identifierCount = Math.max(remoteIdentifiers.length, localIdentifiers.length);
        for (int i = 0; i < identifierCount; i++) {
            if (i >= remoteIdentifiers.length) return false;
            if (i >= localIdentifiers.length) return true;

            String remoteIdentifier = remoteIdentifiers[i];
            String localIdentifier = localIdentifiers[i];
            boolean remoteNumeric = remoteIdentifier.matches("\\d+");
            boolean localNumeric = localIdentifier.matches("\\d+");
            if (remoteNumeric && localNumeric) {
                int comparison = new BigInteger(remoteIdentifier).compareTo(new BigInteger(localIdentifier));
                if (comparison != 0) return comparison > 0;
            } else if (remoteNumeric != localNumeric) {
                return !remoteNumeric;
            } else {
                int comparison = remoteIdentifier.compareTo(localIdentifier);
                if (comparison != 0) return comparison > 0;
            }
        }
        return false;
    }

    @Nullable
    private static BigInteger versionNumber(String[] parts, int index) {
        if (index >= parts.length) return BigInteger.ZERO;
        if (!parts[index].matches("\\d+")) return null;
        return new BigInteger(parts[index]);
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
