package net.kdt.pojavlaunch.launch;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.OptiFineUtils;
import net.kdt.pojavlaunch.modloaders.OptiFineScraper;
import net.kdt.pojavlaunch.utils.ProfileDetection;
import net.kdt.pojavlaunch.value.DependentLibrary;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight metadata-only checks for installed Minecraft profiles. Downloads
 * are deliberately left to the launcher's existing, user-initiated install flow.
 */
public final class MinecraftUpdateManager {
    private static final String TAG = "MinecraftUpdater";
    private static final long CHECK_INTERVAL_MS = 25_000L;
    private static final String PREFS = "evolved_minecraft_updater";
    private static final String KEY_AVAILABLE_OFFERS = "available_offers";
    private static final String MOJANG_MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final int MAX_METADATA_BYTES = 16 * 1024 * 1024;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static volatile Activity activeActivity;

    private static final Runnable PERIODIC_CHECK = new Runnable() {
        @Override public void run() {
            Activity activity = activeActivity;
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            MAIN.postDelayed(this, CHECK_INTERVAL_MS);
            GitHubReleaseUpdateManager.checkOnLauncherOpen(activity);
            if (!CHECKING.compareAndSet(false, true)) return;
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    List<Offer> offers = findOffers(activity.getApplicationContext());
                    persistOffers(activity.getApplicationContext(), offers);
                } catch (Exception e) {
                    Log.w(TAG, "Minecraft metadata check failed", e);
                } finally {
                    CHECKING.set(false);
                }
            });
        }
    };

    private MinecraftUpdateManager() {}

    public static void startPeriodicChecks(@NonNull Activity activity) {
        activeActivity = activity;
        MAIN.removeCallbacks(PERIODIC_CHECK);
        PERIODIC_CHECK.run();
    }

    public static void stopPeriodicChecks(@NonNull Activity activity) {
        if (activeActivity != activity) return;
        activeActivity = null;
        MAIN.removeCallbacks(PERIODIC_CHECK);
    }

    private static List<Offer> findOffers(Context context) throws Exception {
        LauncherProfiles.load();
        Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson.profiles;
        if (profiles == null || profiles.isEmpty()) return Collections.emptyList();

        String latestRelease = null;
        try {
            JSONObject manifest = new JSONObject(get(MOJANG_MANIFEST, "piston-meta.mojang.com"));
            JSONObject latest = manifest.optJSONObject("latest");
            latestRelease = latest == null ? null : latest.optString("release", null);
            JSONArray manifestVersions = manifest.optJSONArray("versions");
            if (TextUtils.isEmpty(latestRelease) || manifestVersions == null) {
                throw new IOException("Mojang returned an incomplete version manifest.");
            }

            String latestReleaseUrl = null;
            for (int i = 0; i < manifestVersions.length(); i++) {
                JSONObject version = manifestVersions.optJSONObject(i);
                if (version == null || !latestRelease.equals(version.optString("id"))) continue;
                if (!"release".equals(version.optString("type"))) break;
                latestReleaseUrl = version.optString("url", null);
                break;
            }
            if (TextUtils.isEmpty(latestReleaseUrl)
                    || !isOfficialUrl(latestReleaseUrl, "piston-meta.mojang.com")) {
                throw new IOException("Mojang's latest stable release metadata is invalid.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Mojang version manifest check failed", e);
            latestRelease = null;
        }

        List<Offer> offers = new ArrayList<>();
        TreeSet<String> minecraftVersions = new TreeSet<>();
        for (Map.Entry<String, MinecraftProfile> entry : profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null || TextUtils.isEmpty(profile.lastVersionId)) continue;
            String mcVersion = ProfileDetection.getMcVersion(profile);
            if (TextUtils.isEmpty(mcVersion) || !isStableMinecraftRelease(mcVersion)) continue;
            minecraftVersions.add(mcVersion);
            String profileName = TextUtils.isEmpty(profile.name) ? profile.lastVersionId : profile.name;
            String loader = loaderType(profile.lastVersionId);

            if (latestRelease != null) {
                if (compareVersions(latestRelease, mcVersion) > 0) {
                    offers.add(new Offer(entry.getKey(), profileName, "Minecraft", mcVersion, latestRelease));
                }
            }
        }

        try {
            readFabricLike("https://meta.fabricmc.net/v2", "fabric", minecraftVersions, offers, profiles);
        } catch (Exception e) {
            Log.w(TAG, "Fabric metadata check failed", e);
        }
        try {
            readFabricLike("https://meta.quiltmc.org/v3", "quilt", minecraftVersions, offers, profiles);
        } catch (Exception e) {
            Log.w(TAG, "Quilt metadata check failed", e);
        }
        List<String> forgeVersions = Collections.emptyList();
        List<String> neoForgeVersions = Collections.emptyList();
        try {
            forgeVersions = readMavenVersions(
                    "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
                    "maven.minecraftforge.net");
        } catch (Exception e) {
            Log.w(TAG, "Forge metadata check failed", e);
        }
        try {
            neoForgeVersions = readMavenVersions(
                    "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
                    "maven.neoforged.net");
        } catch (Exception e) {
            Log.w(TAG, "NeoForge metadata check failed", e);
        }

        OptiFineUtils.OptiFineVersions optiFineVersions = null;
        boolean optiFineChecked = false;
        for (Map.Entry<String, MinecraftProfile> entry : profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null || TextUtils.isEmpty(profile.lastVersionId)) continue;
            String loader = loaderType(profile.lastVersionId);
            if (loader == null) continue;
            String mcVersion = ProfileDetection.getMcVersion(profile);
            if (TextUtils.isEmpty(mcVersion) || !isStableMinecraftRelease(mcVersion)) continue;
            String installed = installedLoaderVersion(profile.lastVersionId, loader);
            if (TextUtils.isEmpty(installed)) continue;
            String available = null;
            if ("forge".equals(loader)) {
                available = newestForgeFor(forgeVersions, mcVersion);
            } else if ("neoforge".equals(loader)) {
                available = newestNeoForgeFor(neoForgeVersions, mcVersion);
            } else if ("optifine".equals(loader)) {
                if (!optiFineChecked) {
                    optiFineChecked = true;
                    try {
                        optiFineVersions = new OptiFineScraper().process(
                                get("https://optifine.net/downloads", "optifine.net"));
                    } catch (Exception e) {
                        Log.w(TAG, "OptiFine metadata check failed", e);
                    }
                }
                available = newestOptiFineFor(mcVersion, optiFineVersions);
            }
            if (!TextUtils.isEmpty(available) && compareVersions(available, installed) > 0) {
                String name = TextUtils.isEmpty(profile.name) ? profile.lastVersionId : profile.name;
                offers.add(new Offer(entry.getKey(), name, loaderLabel(loader), installed, available));
            }
        }
        return offers;
    }

    private static void readFabricLike(String api, String loader, TreeSet<String> minecraftVersions,
                                       List<Offer> offers,
                                       Map<String, MinecraftProfile> profiles) throws Exception {
        String metaHost = "fabric".equals(loader) ? "meta.fabricmc.net" : "meta.quiltmc.org";
        for (String mcVersion : minecraftVersions) {
            String url = api + "/versions/loader/" + URLEncoder.encode(mcVersion, "UTF-8");
            JSONArray versions;
            try {
                versions = new JSONArray(get(url, metaHost));
            } catch (Exception e) {
                Log.w(TAG, loaderLabel(loader) + " metadata failed for Minecraft " + mcVersion, e);
                continue;
            }
            String newest = null;
            for (int i = 0; i < versions.length(); i++) {
                JSONObject item = versions.optJSONObject(i);
                JSONObject loaderObj = item == null ? null : item.optJSONObject("loader");
                if (loaderObj == null) continue;
                String version = loaderObj.optString("version", "");
                if (version.isEmpty() || !isStableLoaderVersion(version)) continue;
                if (item.has("stable") && !item.optBoolean("stable")) continue;
                if (loaderObj.has("stable") && !loaderObj.optBoolean("stable")) continue;
                if (newest == null || compareVersions(version, newest) > 0) newest = version;
            }
            if (newest == null) continue;
            for (Map.Entry<String, MinecraftProfile> profileEntry : profiles.entrySet()) {
                MinecraftProfile profile = profileEntry.getValue();
                if (profile == null || !loader.equals(loaderType(profile.lastVersionId))) continue;
                if (!mcVersion.equals(ProfileDetection.getMcVersion(profile))) continue;
                String installed = installedLoaderVersion(profile.lastVersionId, loader);
                if (!TextUtils.isEmpty(installed) && compareVersions(newest, installed) > 0) {
                    String name = TextUtils.isEmpty(profile.name) ? profile.lastVersionId : profile.name;
                    offers.add(new Offer(profileEntry.getKey(), name, loaderLabel(loader), installed, newest));
                }
            }
        }
    }

    private static List<String> readMavenVersions(String url, String host) throws Exception {
        String xml = get(url, host);
        Matcher matcher = Pattern.compile("<version>\\s*([^<]+?)\\s*</version>").matcher(xml);
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            String version = matcher.group(1).trim();
            if (isStableLoaderVersion(version)) versions.add(version);
        }
        return versions;
    }

    private static String newestForgeFor(List<String> versions, String mcVersion) {
        String prefix = mcVersion + "-";
        String newest = null;
        for (String version : versions) {
            if (!version.startsWith(prefix)) continue;
            String build = version.substring(prefix.length());
            if (!isStableLoaderVersion(build)) continue;
            if (newest == null || compareVersions(build, newest.substring(prefix.length())) > 0) {
                newest = version;
            }
        }
        return newest == null ? null : newest.substring(prefix.length());
    }

    private static String newestNeoForgeFor(List<String> versions, String mcVersion) {
        String newest = null;
        for (String version : versions) {
            if (!isNeoForgeCompatible(version, mcVersion)) continue;
            if (newest == null || compareVersions(version, newest) > 0) newest = version;
        }
        return newest;
    }

    private static boolean isNeoForgeCompatible(String version, String mcVersion) {
        if (version.equals(mcVersion) || version.startsWith(mcVersion + "-")) return true;
        String[] parts = version.split("[.-]");
        if (parts.length < 2 || !parts[0].matches("\\d+") || !parts[1].matches("\\d+")) return false;
        int major;
        int minor;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        String compatibleMc = major == 1 ? "1." + minor
                : major >= 26 ? major + "." + minor : "1." + major + "." + minor;
        return mcVersion.equals(compatibleMc);
    }

    private static String newestOptiFineFor(String mcVersion, OptiFineUtils.OptiFineVersions versions) {
        if (versions == null || versions.minecraftVersions == null || versions.optifineVersions == null) return null;
        String newest = null;
        for (int group = 0; group < versions.minecraftVersions.size()
                && group < versions.optifineVersions.size(); group++) {
            String heading = versions.minecraftVersions.get(group);
            if (heading == null) continue;
            heading = heading.replaceFirst("^Minecraft\\s+", "").trim();
            if (!mcVersion.equals(heading)) continue;
            List<OptiFineUtils.OptiFineVersion> builds = versions.optifineVersions.get(group);
            if (builds == null) continue;
            for (OptiFineUtils.OptiFineVersion build : builds) {
                if (build == null || TextUtils.isEmpty(build.versionName)
                        || !isStableLoaderVersion(build.versionName)) continue;
                String normalized = build.versionName.replaceFirst("(?i)^OptiFine\\s+", "").trim();
                if (newest == null || compareVersions(normalized, newest) > 0) {
                    newest = normalized;
                }
            }
        }
        return newest;
    }

    private static String installedLoaderVersion(String versionId, String loader) {
        if ("optifine".equals(loader)) return parseLoaderVersionFromId(versionId, loader);
        JMinecraftVersionList.Version version = Tools.getVersionInfo(versionId);
        if (version != null && version.libraries != null) {
            for (DependentLibrary library : version.libraries) {
                if (library == null || TextUtils.isEmpty(library.name)) continue;
                String[] coordinate = library.name.split(":");
                if (coordinate.length < 3) continue;
                String group = coordinate[0];
                String artifact = coordinate[1];
                String value = coordinate[2];
                if ("fabric".equals(loader) && "net.fabricmc".equals(group)
                        && "fabric-loader".equals(artifact)) return value;
                if ("quilt".equals(loader) && "org.quiltmc".equals(group)
                        && "quilt-loader".equals(artifact)) return value;
                if ("forge".equals(loader) && "net.minecraftforge".equals(group)
                        && "forge".equals(artifact)) {
                    String mcVersion = ProfileDetection.extractMcFromVersionId(versionId);
                    String prefix = mcVersion + "-";
                    return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
                }
                if ("neoforge".equals(loader) && "net.neoforged".equals(group)
                        && "neoforge".equals(artifact)) return value;
                if ("optifine".equals(loader) && group.toLowerCase(Locale.ROOT).contains("optifine")) {
                    return value;
                }
            }
        }
        return parseLoaderVersionFromId(versionId, loader);
    }

    private static String parseLoaderVersionFromId(String id, String loader) {
        String lower = id.toLowerCase(Locale.ROOT);
        if ("optifine".equals(loader)) {
            Matcher matcher = Pattern.compile("(?i)optifine[_ -](.+)$").matcher(id);
            return matcher.find() ? matcher.group(1).replace('_', ' ') : null;
        }
        String prefix = loader + "-";
        int at = lower.indexOf(prefix);
        if (at < 0) return null;
        String value = id.substring(at + prefix.length());
        String mcVersion = ProfileDetection.extractMcFromVersionId(id);
        if (!TextUtils.isEmpty(mcVersion) && value.endsWith("-" + mcVersion)) {
            value = value.substring(0, value.length() - mcVersion.length() - 1);
        } else if ("forge".equals(loader) && value.startsWith(mcVersion + "-")) {
            value = value.substring(mcVersion.length() + 1);
        }
        return value.isEmpty() ? null : value;
    }

    private static String loaderType(String versionId) {
        if (versionId == null) return null;
        String id = versionId.toLowerCase(Locale.ROOT);
        if (id.contains("neoforge")) return "neoforge";
        if (id.contains("fabric")) return "fabric";
        if (id.contains("quilt")) return "quilt";
        if (id.contains("optifine")) return "optifine";
        if (id.contains("forge")) return "forge";
        return null;
    }

    private static String loaderLabel(String loader) {
        switch (loader) {
            case "fabric": return "Fabric";
            case "forge": return "Forge";
            case "neoforge": return "NeoForge";
            case "quilt": return "Quilt";
            case "optifine": return "OptiFine";
            default: return loader;
        }
    }

    private static boolean isStableMinecraftRelease(String version) {
        return version != null && version.matches("\\d+\\.\\d+(?:\\.\\d+)?");
    }

    private static boolean isStableLoaderVersion(String version) {
        if (TextUtils.isEmpty(version)) return false;
        String lower = version.toLowerCase(Locale.ROOT);
        return !(lower.contains("alpha") || lower.contains("beta") || lower.contains("snapshot")
                || lower.contains("experimental") || lower.contains("pre-release")
                || lower.contains("preview") || lower.matches(".*(?:^|[-.])rc\\d*.*"));
    }

    private static int compareVersions(String left, String right) {
        if (left == null || right == null) return 0;
        String[] a = left.replaceFirst("(?i)^v", "").split("[^A-Za-z0-9]+");
        String[] b = right.replaceFirst("(?i)^v", "").split("[^A-Za-z0-9]+");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            if (av.matches("\\d+") && bv.matches("\\d+")) {
                int c = new BigInteger(av).compareTo(new BigInteger(bv));
                if (c != 0) return c;
            } else {
                int c = av.compareToIgnoreCase(bv);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    private static String get(String address, String expectedHost) throws Exception {
        if (!isOfficialUrl(address, expectedHost)) throw new IOException("Untrusted metadata URL.");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "EvolveD-Launcher-Updater");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Official metadata source returned HTTP " + status + ".");
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_METADATA_BYTES) throw new IOException("Metadata response is too large.");
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isOfficialUrl(String value, String expectedHost) {
        try {
            URL url = new URL(value);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && expectedHost.equalsIgnoreCase(url.getHost())
                    && url.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void persistOffers(Context context, List<Offer> offers) {
        Set<String> available = new HashSet<>();
        for (Offer offer : offers) {
            available.add(offer.key());
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_AVAILABLE_OFFERS, available).apply();
    }

    private static final class Offer {
        final String profileKey;
        final String profile;
        final String loader;
        final String installed;
        final String available;

        Offer(String profileKey, String profile, String loader, String installed, String available) {
            this.profileKey = profileKey;
            this.profile = profile;
            this.loader = loader;
            this.installed = installed;
            this.available = available;
        }

        String key() {
            return profileKey + "|" + loader + "|" + installed + "|" + available;
        }
    }
}
