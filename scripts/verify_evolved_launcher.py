#!/usr/bin/env python3
"""Static release gate for EvolveD Launcher 1.0.0."""
from pathlib import Path
import sys
import hashlib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

FILES = {
    "build": read("app_pojavlauncher/build.gradle"),
    "activity": read("app_pojavlauncher/src/main/res/layout/activity_pojav_launcher.xml"),
    "land_activity": read("app_pojavlauncher/src/main/res/layout-land/activity_pojav_launcher.xml"),
    "bottom_nav": read("app_pojavlauncher/src/main/res/layout/include_lh_bottom_nav.xml"),
    "dock": read("app_pojavlauncher/src/main/res/layout/include_lh_dock.xml"),
    "home": read("app_pojavlauncher/src/main/res/layout/fragment_launcher_home.xml"),
    "home_land": read("app_pojavlauncher/src/main/res/layout-land/fragment_launcher_home.xml"),
    "manifest": read("app_pojavlauncher/src/main/AndroidManifest.xml"),
    "memory": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MemoryFragment.java"),
    "home_fragment": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LauncherHomeFragment.java"),
    "sdl": read("app_pojavlauncher/src/main/java/org/libsdl/app/SDLActivity.java"),
    "updater": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/launch/GitHubReleaseUpdateManager.java"),
    "cleaner": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/CleanerFragment.java"),
    "playtime": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/PlaytimeFragment.java"),
    "playtime_store": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/performance/PlaytimeStore.java"),
    "game_activity": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MainActivity.java"),
    "mods": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/ModsSearchFragment.java"),
    "launch_home": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LauncherHomeFragment.java"),
    "launcher_activity": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java"),
    "play_button": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/ui/PremiumPlayButtonView.java"),
    "version_create": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/VersionCreateFragment.java"),
    "modrinth": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModrinthApi.java"),
    "curseforge": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/CurseforgeApi.java"),
    "prefs": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java"),
    "settings": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceFragment.java"),
    "sidebar": read("app_pojavlauncher/src/main/res/drawable/bg_hd_sidebar_item.xml"),
}

XML_FILES = [
    ROOT / "app_pojavlauncher/src/main/res/layout/activity_pojav_launcher.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout-land/activity_pojav_launcher.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/include_lh_bottom_nav.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/include_lh_dock.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/dialog_launcher_update.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/fragment_launcher_home.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout-land/fragment_launcher_home.xml",
    ROOT / "app_pojavlauncher/src/main/res/drawable/bg_hd_sidebar_item.xml",
    ROOT / "app_pojavlauncher/src/main/res/drawable/bg_lh_scrim.xml",
    ROOT / "app_pojavlauncher/src/main/res/values/dimens.xml",
    ROOT / "app_pojavlauncher/src/main/res/values/styles.xml",
]

def assertions():
    b, a, d, h = FILES["build"], FILES["activity"], FILES["dock"], FILES["home"]
    u, c, m, lh = FILES["updater"], FILES["cleaner"], FILES["mods"], FILES["launch_home"]
    assert 'return "1.0.0"' in b and 'versionCode 1000000' in b
    assert 'return "DUMMY"' not in b and 'cslauncher2026' not in b
    assert 'releaseSigningConfigured' in b
    assert 'app:layout_constraintEnd_toEndOf="parent"' in a
    assert 'app:layout_constraintEnd_toStartOf="@+id/lh_dock"' in a
    assert 'app:layout_constraintStart_toStartOf="parent"' in a
    assert 'app:layout_constraintStart_toEndOf="@+id/lh_dock"' not in a
    assert '#FF9D00' in d and '#FFB000' in FILES["sidebar"] and 'EvolveD' in d
    assert 'android:text="EvolveD"' in h and '@drawable/evolved_icon' in h
    assert 'android:id="@+id/lh_search_bar"' in h
    assert 'android:icon="@drawable/evolved_icon"' in FILES["manifest"]
    assert 'android:roundIcon="@drawable/evolved_icon"' in FILES["manifest"]
    logo = ROOT / "EvolveD.png"
    icon = ROOT / "app_pojavlauncher/src/main/res/drawable/evolved_icon.png"
    assert hashlib.sha256(logo.read_bytes()).digest() == hashlib.sha256(icon.read_bytes()).digest()
    android_ns = "{http://schemas.android.com/apk/res/android}"
    manifest_root = ET.fromstring(FILES["manifest"])
    activities = manifest_root.find("application").findall("activity")
    assert activities and all(
        activity.get(android_ns + "screenOrientation") == "landscape"
        for activity in activities
    )
    assert manifest_root.find("application").get(android_ns + "resizeableActivity") == "true"
    assert not list((ROOT / "app_pojavlauncher/src/main/res/layout-port").glob("*.xml"))
    assert 'ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE' in FILES["sdl"]
    assert 'SCREEN_ORIENTATION_PORTRAIT' not in FILES["sdl"]
    assert 'lh_nav_home' in FILES["bottom_nav"] and 'lh_nav_versions' in FILES["bottom_nav"]
    assert 'layout="@layout/include_lh_bottom_nav"' in FILES["land_activity"]
    assert 'app:layout_constraintBottom_toTopOf="@+id/lh_dock"' in FILES["land_activity"]
    assert 'include_lh_dock' not in FILES["land_activity"]
    assert 'app:layout_constraintGuide_percent="0.43"' in h
    feature_cards = ("lh_quick_memory", "lh_quick_cleaner", "lh_quick_playtime")
    assert 'layout="@layout/fragment_launcher_home"' in FILES["home_land"]
    for home in (h,):
        assert all(action in home for action in feature_cards)
        assert '<androidx.core.widget.NestedScrollView' in home[:1000]
        assert 'android:fillViewport="true"' in home
        assert 'android:scrollbars="vertical"' in home
        assert 'android:overScrollMode="ifContentScrolls"' in home
        assert 'android:minHeight="@dimen/lh_home_min_content_height"' in home
        assert not any(old in home for old in (
            "lh_quick_mods", "lh_quick_modpacks", "lh_quick_accounts", "lh_quick_settings"))
        assert not any(social in home for social in (
            "lh_btn_youtube", "lh_btn_discord", "YouTube", "Discord"))
    assert 'lh_instance_version' in h and 'android:layout_height="62dp"' in h
    assert '<dimen name="lh_home_min_content_height">320dp</dimen>' in read(
        "app_pojavlauncher/src/main/res/values/dimens.xml")
    assert 'android:layout_width="0dp"' in h and 'android:layout_weight="1"' in h
    root = ET.parse(ROOT / "app_pojavlauncher/src/main/res/layout/fragment_launcher_home.xml").getroot()
    quick_actions = root.find(
        ".//*[@android:id='@+id/lh_quick_actions']",
        {"android": "http://schemas.android.com/apk/res/android"})
    assert quick_actions is not None
    assert [
        child.get(android_ns + "id") for child in list(quick_actions)
    ] == [f"@+id/{card}" for card in feature_cards]
    assert "MemoryFragment.class" in FILES["home_fragment"]
    assert "ActivityManager.MemoryInfo" in FILES["memory"]
    assert "getMemoryInfo(info)" in FILES["memory"]
    assert "totalMem" in FILES["memory"] and "availMem" in FILES["memory"]
    assert "Nothing to clean." in c and "Cleaned: " in c
    assert 'HomeTutorial.maybeStart' not in lh and 'PlusWelcomeDialog.maybeShow' not in lh
    assert 'https://api.github.com/repos/evolvegmr/EvolveD-launcher/releases/latest' in u
    assert 'dialog.setCancelable(true)' in u and 'dialog.dismiss()' in u
    assert 'verifyDownloadedApk(act, finalApk)' in u
    assert 'hasCompatibleSigningIdentity' in u and 'BuildConfig.VERSION_CODE' in u
    assert 'GitHubReleaseUpdateManager' in FILES["settings"] and 'checkManually(requireActivity())' in FILES["settings"]
    assert 'refresh_minecraft_versions' in FILES["settings"] and 'refreshNow' in FILES["settings"]
    assert 'FirebaseSyncManager.checkForUpdateManual' not in FILES["settings"]
    assert 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' in FILES["prefs"]
    assert 'scanSafeCache' in c and 'getCacheDir()' in c and 'getExternalCacheDir()' in c
    assert 'getCodeCacheDir()' in c and 'getCanonicalPath()' in c
    assert 'Tools.DIR_GAME_NEW' not in c and 'mods/' not in c
    assert all(label in FILES["playtime"] for label in ('"TODAY"', '"AVERAGE"', '"MAXIMUM"', '"WEEKLY"'))
    assert "NestedScrollView" in FILES["playtime"] and "setFillViewport(true)" in FILES["playtime"]
    assert "dp(960)" in FILES["playtime"]
    assert "WindowInsetsCompat.Type.statusBars()" in FILES["launcher_activity"]
    assert "maximumDailyMs" in FILES["playtime"]
    assert 'text(context, "0m", 42' in FILES["playtime"]
    assert 'text(context, "0m", 21' in FILES["playtime"]
    assert "dateKey(dayStart.getTimeInMillis())" in FILES["playtime_store"]
    assert "recordedDays == 0 ? 0L" in FILES["playtime_store"]
    assert "maximumDailyMs" in FILES["playtime_store"]
    assert 'PlaytimeStore.start(' in FILES["game_activity"]
    assert 'frames > mFirstFrameBaseline' in FILES["game_activity"]
    assert 'checkpoint(Context context)' in FILES["playtime_store"]
    assert 'applyProfileFiltersFromSelection();' in m
    assert 'String version = profile.lastVersionId;' in m and 'String loader = profile.lastVersionId;' in m
    for loader in ("Fabric", "Forge", "NeoForge", "Quilt"):
        assert loader in FILES["version_create"]
    assert 'ModrinthApi' in FILES["modrinth"] and 'CurseforgeApi' in FILES["curseforge"]
    assert 'it.setTranslationX(dx);' in FILES["launcher_activity"]
    assert '0xFFFF9800' in FILES["play_button"] or '0xFFFFD54A' in FILES["play_button"]
    for rel in [
        "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java",
        "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LauncherHomeFragment.java",
        "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/ModsSearchFragment.java",
        "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/launch/GitHubReleaseUpdateManager.java",
        "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/ui/PremiumPlayButtonView.java",
    ]:
        s = read(rel)
        assert s.count("{") == s.count("}"), rel
        assert s.count("(") == s.count(")"), rel

for p in XML_FILES:
    ET.parse(p)

repeat = 1
full = "--full" in sys.argv
if "--repeat" in sys.argv:
    repeat = int(sys.argv[sys.argv.index("--repeat") + 1])
for _ in range(repeat):
    assertions()

if full:
    all_xml = list((ROOT / "app_pojavlauncher/src/main/res").rglob("*.xml"))
    for p in all_xml:
        ET.parse(p)
    print(f"PASS: {len(all_xml)} XML files parsed; {repeat} verification pass(es) completed.")
else:
    print(f"PASS: {len(XML_FILES)} changed XML files parsed; {repeat} verification pass(es) completed.")
