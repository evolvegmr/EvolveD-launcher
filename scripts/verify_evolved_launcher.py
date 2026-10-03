#!/usr/bin/env python3
"""Static release gate for EvolveD Launcher 1.0.0."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

FILES = {
    "build": read("app_pojavlauncher/build.gradle"),
    "activity": read("app_pojavlauncher/src/main/res/layout/activity_pojav_launcher.xml"),
    "port_activity": read("app_pojavlauncher/src/main/res/layout-port/activity_pojav_launcher.xml"),
    "land_activity": read("app_pojavlauncher/src/main/res/layout-land/activity_pojav_launcher.xml"),
    "bottom_nav": read("app_pojavlauncher/src/main/res/layout/include_lh_bottom_nav.xml"),
    "dock": read("app_pojavlauncher/src/main/res/layout/include_lh_dock.xml"),
    "home": read("app_pojavlauncher/src/main/res/layout/fragment_launcher_home.xml"),
    "manifest": read("app_pojavlauncher/src/main/AndroidManifest.xml"),
    "updater": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/launch/GitHubReleaseUpdateManager.java"),
    "cleaner": read("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/CleanerFragment.java"),
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
    ROOT / "app_pojavlauncher/src/main/res/layout-port/activity_pojav_launcher.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout-land/activity_pojav_launcher.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/include_lh_bottom_nav.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/include_lh_dock.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/dialog_launcher_update.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/fragment_launcher_home.xml",
    ROOT / "app_pojavlauncher/src/main/res/drawable/evolved_mark.xml",
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
    assert 'android:text="EvolveD"' in h and 'evolved_mark' in h
    android_ns = "{http://schemas.android.com/apk/res/android}"
    activities = {
        item.get(android_ns + "name"): item
        for item in ET.fromstring(FILES["manifest"]).findall("application/activity")
    }
    for name in (".TestStorageActivity", ".LauncherActivity"):
        assert android_ns + "screenOrientation" not in activities[name].attrib
    assert 'setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)' not in FILES["launcher_activity"]
    assert 'lh_nav_home' in FILES["bottom_nav"] and 'lh_nav_versions' in FILES["bottom_nav"]
    assert 'layout="@layout/include_lh_bottom_nav"' in FILES["land_activity"]
    assert 'app:layout_constraintBottom_toTopOf="@+id/lh_dock"' in FILES["land_activity"]
    assert 'include_lh_dock' not in FILES["land_activity"]
    assert 'app:layout_constraintGuide_percent="0.43"' in h
    assert all(action in h for action in (
        'lh_instance_version', 'lh_quick_mods', 'lh_quick_playtime',
        'lh_quick_cleaner', 'lh_quick_settings'))
    assert 'android:layout_height="48dp"' in h
    assert 'android:layout_width="0dp"' in h and 'android:layout_weight="1"' in h
    assert 'HomeTutorial.maybeStart' not in lh and 'PlusWelcomeDialog.maybeShow' not in lh
    assert 'https://api.github.com/repos/evolvegmr/EvolveD-launcher/releases/latest' in u
    assert 'dialog.setCancelable(true)' in u and 'dialog.dismiss()' in u
    assert 'verifyDownloadedApk(act, finalApk)' in u
    assert 'hasCompatibleSigningIdentity' in u and 'BuildConfig.VERSION_CODE' in u
    assert 'GitHubReleaseUpdateManager' in FILES["settings"] and 'checkManually(requireActivity())' in FILES["settings"]
    assert 'refresh_minecraft_versions' in FILES["settings"] and 'refreshNow' in FILES["settings"]
    assert 'FirebaseSyncManager.checkForUpdateManual' not in FILES["settings"]
    assert 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' in FILES["prefs"]
    assert 'safeCacheSize' in c and 'getCacheDir()' in c and 'getExternalCacheDir()' in c
    assert 'Tools.DIR_GAME_NEW' not in c and 'mods/' not in c
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
