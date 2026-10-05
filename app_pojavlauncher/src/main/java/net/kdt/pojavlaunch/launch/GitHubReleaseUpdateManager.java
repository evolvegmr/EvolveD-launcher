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
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
            "https://api.github.com/repos/evolvegmr/EvolveD-launcher/releases";
    private static final String PREFS = "evolved_github_updater";
    private static final String KEY_LAST_NOTIFIED_VERSION = "last_notified_version";

    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);

    private GitHubReleaseUpdateManager() {}

    public static void checkOnLauncherOpen(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (!CHECKING.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                Release release = fetchLatestRelease();
                if (release == null || !isNewerVersion(release.version, BuildConfig.VERSION_NAME)) return;

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    android.content.SharedPreferences preferences =
                            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    String lastNotified = preferences.getString(KEY_LAST_NOTIFIED_VERSION, "");
                    if (!TextUtils.isEmpty(lastNotified)
                            && !isNewerVersion(release.version, lastNotified)) return;
                    preferences.edit().putString(KEY_LAST_NOTIFIED_VERSION,
                            release.version.replaceFirst("(?i)^v", "").split("\\+", 2)[0]).apply();
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
            JSONArray releases = new JSONArray(json);
            Release newest = null;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject releaseObject = releases.optJSONObject(i);
                if (releaseObject == null) continue;
                Release release = parseRelease(releaseObject);
                if (release != null && !TextUtils.isEmpty(release.apkUrl)
                        && (newest == null || isNewerVersion(release.version, newest.version))) {
                    newest = release;
                }
            }
            return newest;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Nullable
    private static Release parseRelease(@NonNull JSONObject root) {
        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) return null;

        String tag = root.optString("tag_name", "").trim();
        if (!isStableReleaseVersion(tag)) return null;

        String apkUrl = "";
        String apkDigest = "";
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            JSONObject bestAsset = chooseCompatibleAsset(assets);
            if (bestAsset != null) {
                apkUrl = bestAsset.optString("browser_download_url", "");
                apkDigest = normalizeDigest(bestAsset.optString("digest", ""));
                if (TextUtils.isEmpty(apkDigest)) {
                    apkDigest = normalizeDigest(bestAsset.optString("checksum", ""));
                }
            }
        }

        return new Release(
                tag,
                root.optString("name", tag),
                root.optString("body", ""),
                root.optString("html_url", "https://github.com/evolvegmr/EvolveD-launcher/releases"),
                apkUrl,
                apkDigest
        );
    }

    @Nullable
    private static JSONObject chooseCompatibleAsset(@NonNull JSONArray assets) {
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        String[] supported = Build.SUPPORTED_ABIS;

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;

            String name = asset.optString("name", "").trim();
            if (TextUtils.isEmpty(name)) continue;
            String lowerName = name.toLowerCase(Locale.US);
            String browserUrl = asset.optString("browser_download_url", "");

            if (!lowerName.endsWith(".apk") || !isOfficialReleaseAssetUrl(browserUrl)) continue;
            if (lowerName.contains("debug") || lowerName.contains("symbol")
                    || lowerName.contains("source") || lowerName.contains("mapping")
                    || lowerName.contains("unsigned")) {
                continue;
            }

            int score = platformAssetScore(lowerName, supported);
            if (score < 0) continue;

            if (best == null || score > bestScore) {
                best = asset;
                bestScore = score;
            }
        }

        return best;
    }

    private static boolean isOfficialReleaseAssetUrl(String value) {
        try {
            URL url = new URL(value);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && "github.com".equalsIgnoreCase(url.getHost())
                    && url.getUserInfo() == null
                    && url.getPath().startsWith("/evolvegmr/EvolveD-launcher/releases/download/");
        } catch (Exception e) {
            return false;
        }
    }

    private static int platformAssetScore(String name, String[] supportedAbis) {
        String normalizedName = name.replace('_', '-');
        int best = -1;
        for (int i = 0; i < supportedAbis.length; i++) {
            String abi = supportedAbis[i].toLowerCase(Locale.US).replace('_', '-');
            if (normalizedName.contains(abi)) best = Math.max(best, 100 - i);
            if ("arm64-v8a".equals(abi) && normalizedName.contains("aarch64")) best = Math.max(best, 100 - i);
            if ("armeabi-v7a".equals(abi)
                    && (normalizedName.contains("armv7") || normalizedName.contains("armeabi"))) {
                best = Math.max(best, 100 - i);
            }
            if ("x86-64".equals(abi) && normalizedName.contains("x64")) best = Math.max(best, 100 - i);
        }
        if (best >= 0) return best;

        boolean namesArchitecture = normalizedName.contains("arm64") || normalizedName.contains("aarch64")
                || normalizedName.contains("armeabi") || normalizedName.contains("armv7")
                || normalizedName.contains("x86") || normalizedName.contains("x64")
                || normalizedName.contains("riscv");
        if (namesArchitecture) return -1;
        return name.contains("universal") ? 10 : 1;
    }

    private static String normalizeDigest(@NonNull String digest) {
        String cleaned = digest.trim();
        if (cleaned.isEmpty()) return "";
        if (cleaned.regionMatches(true, 0, "sha256:", 0, "sha256:".length())) {
            cleaned = cleaned.substring("sha256:".length());
        }
        return cleaned.trim();
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
            if (version != null) {
                version.setText("Current version: " + BuildConfig.VERSION_NAME
                        + "\nNew version: " + release.version);
            }
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
            File partialApk = null;
            try {
                File dir = new File(act.getCacheDir(), "launcher-updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create download directory");
                partialApk = File.createTempFile("evolved-update-", ".part", dir);

                if (!isOfficialReleaseAssetUrl(release.apkUrl)) {
                    throw new IOException("The release APK URL is not an official EvolveD GitHub asset.");
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(release.apkUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "EvolveD-Launcher-Updater");
                conn.connect();
                int responseCode;
                try {
                    responseCode = conn.getResponseCode();
                } catch (IOException e) {
                    conn.disconnect();
                    throw e;
                }
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect();
                    throw new IllegalStateException("Download failed: HTTP " + responseCode);
                }
                String downloadHost = conn.getURL().getHost();
                if (!"github.com".equalsIgnoreCase(downloadHost)
                        && !"release-assets.githubusercontent.com".equalsIgnoreCase(downloadHost)
                        && !"objects.githubusercontent.com".equalsIgnoreCase(downloadHost)) {
                    conn.disconnect();
                    throw new IOException("The launcher update was redirected to an untrusted host.");
                }

                int total = conn.getContentLength();
                long read = 0;
                int lastPercent = -1;
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(partialApk)) {
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
                if (total > 0 && read != total) {
                    throw new IOException("The launcher update download did not complete.");
                }

                File downloadedApk = partialApk;
                act.runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setProgress(100);
                    if (progressText != null) progressText.setText("Verifying downloaded APK...");
                });
                verifyDownloadedApk(act, downloadedApk, release.apkDigest, release.version);
                File finalApk = new File(dir,
                        "EvolveD-Launcher-" + safeFilePart(release.version) + ".apk");
                if (finalApk.exists() && !finalApk.delete()) {
                    throw new IOException("Could not replace the temporary launcher update file.");
                }
                if (!downloadedApk.renameTo(finalApk)) {
                    throw new IOException("Could not finalize the verified launcher update.");
                }
                partialApk = null;
                act.runOnUiThread(() -> {
                    if (progressText != null) progressText.setText("APK verified. Opening Android installer...");
                    installApk(act, finalApk);

                });
            } catch (Throwable t) {
                if (partialApk != null && partialApk.exists() && !partialApk.delete()) {
                    Log.w(TAG, "Could not remove incomplete launcher update file " + partialApk);
                }
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

    private static void verifyDownloadedApk(Context context, File apk,
                                             @Nullable String expectedDigest,
                                             String expectedVersion) throws Exception {
        if (apk == null || !apk.isFile() || apk.length() < 1024) {
            throw new IOException("The downloaded update file is missing or invalid.");
        }

        if (!TextUtils.isEmpty(expectedDigest)) {
            String actualDigest = sha256(apk);
            if (!expectedDigest.equalsIgnoreCase(actualDigest)) {
                if (apk.exists()) apk.delete();
                throw new IOException("The downloaded APK failed SHA-256 verification.");
            }
        }

        PackageManager packageManager = context.getPackageManager();
        PackageInfo candidate = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)) {
            throw new IOException("The downloaded APK is not an EvolveD Launcher package.");
        }
        if (!normalizeVersion(candidate.versionName).equals(normalizeVersion(expectedVersion))) {
            throw new IOException("The downloaded APK version does not match the selected GitHub release.");
        }

        long candidateVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? candidate.getLongVersionCode() : candidate.versionCode;
        if (candidateVersion <= BuildConfig.VERSION_CODE) {
            throw new IOException("The downloaded APK does not have a newer version code.");
        }

        Set<String> installedAbis = apkAbis(new File(context.getApplicationInfo().sourceDir));
        Set<String> candidateAbis = apkAbis(apk);
        candidateAbis.retainAll(installedAbis);
        if (candidateAbis.isEmpty()) {
            throw new IOException("The downloaded APK does not support this installed launcher platform.");
        }

        PackageInfo installed = packageManager.getPackageInfo(context.getPackageName(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        if (!hasCompatibleSigningIdentity(installed, candidate)) {
            throw new IOException("The downloaded APK is not signed with a compatible launcher signing key.");
        }
    }

    private static Set<String> apkAbis(File apk) throws IOException {
        Set<String> abis = new HashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("lib/")) continue;
                int end = name.indexOf('/', 4);
                if (end > 4) abis.add(name.substring(4, end));
            }
        }
        return abis;
    }

    @NonNull
    private static String sha256(@NonNull File file) throws Exception {
        MessageDigest digester = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream in = new java.io.FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digester.update(buffer, 0, read);
            }
        }
        byte[] digest = digester.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            sb.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return sb.toString();
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

    private static String normalizeVersion(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst("(?i)^v", "").split("\\+", 2)[0];
    }

    private static boolean isStableReleaseVersion(String version) {
        return version != null
                && version.matches("(?i)^v?\\d+(?:\\.\\d+){1,3}(?:\\+[0-9a-z.-]+)?$");
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
        final String apkDigest;

        Release(String version, String name, String body, String htmlUrl, String apkUrl, String apkDigest) {
            this.version = version;
            this.name = name;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.apkUrl = apkUrl;
            this.apkDigest = apkDigest;
        }
    }
}
