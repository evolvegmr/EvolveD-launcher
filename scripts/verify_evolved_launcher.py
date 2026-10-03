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
    "dock": read("app_pojavlauncher/src/main/res/layout/include_lh_dock.xml"),
    "home": read("app_pojavlauncher/src/main/res/layout/fragment_launcher_home.xml"),
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
    "sidebar": read("app_pojavlauncher/src/main/res/drawable/bg_hd_sidebar_item.xml"),
}

XML_FILES = [
    ROOT / "app_pojavlauncher/src/main/res/layout/activity_pojav_launcher.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/include_lh_dock.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/dialog_launcher_update.xml",
    ROOT / "app_pojavlauncher/src/main/res/layout/fragment_launcher_home.xml",
    ROOT / "app_pojavlauncher/src/main/res/drawable/evolved_mark.xml",
    ROOT / "app_pojavlauncher/src/main/res/drawable/bg_hd_sidebar_item.xml",
    ROOT / "app_pojavlauncher/src/main/res/drawable/bg_lh_scrim.xml",
]

def assertions():
    b, a, d, h = FILES["build"], FILES["activity"], FILES["dock"], FILES["home"]
    u, c, m, lh = FILES["updater"], FILES["cleaner"], FILES["mods"], FILES["launch_home"]
    assert 'return "1.0.0"' in b and 'versionCode 1000000' in b
    assert 'app:layout_constraintEnd_toEndOf="parent"' in a
    assert 'app:layout_constraintEnd_toStartOf="@+id/lh_dock"' in a
    assert 'app:layout_constraintStart_toStartOf="parent"' in a
    assert 'app:layout_constraintStart_toEndOf="@+id/lh_dock"' not in a
    assert '#FF9800' in d and '#FFB000' in FILES["sidebar"] and 'EvolveD' in d
    assert 'EVOLVED LAUNCHER' in h and 'evolved_mark' in h
    assert 'HomeTutorial.maybeStart' not in lh and 'PlusWelcomeDialog.maybeShow' not in lh
    assert 'https://api.github.com/repos/evolvegmr/EvolveD-launcher/releases/latest' in u
    assert 'dialog.setCancelable(false)' in u and 'finishAffinity()' in u
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
