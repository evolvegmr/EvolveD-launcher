# EvolveD Launcher Updates

## Minecraft versions

EvolveD reads Mojang's official `version_manifest_v2.json` at runtime. The launcher refreshes this manifest when the launcher opens and falls back to its cached copy if the network is unavailable. Settings also provides **Refresh Minecraft versions** to force an independent Mojang metadata refresh. This means a newly released Minecraft version can appear without shipping a new EvolveD Launcher APK.

The version still has to be compatible with the launcher/runtime/loader implementation. A launcher APK is not required merely for a normal Minecraft release to appear in the version list.

## Automatic update checks

While the launcher is in the foreground, it runs one metadata-only update check every 25 seconds. Checks cover stable EvolveD releases, Mojang's latest stable Minecraft release, and compatible stable Fabric, Forge, NeoForge, Quilt, and OptiFine versions for installed profiles. Loader versions are compared only against metadata for the profile's Minecraft version. HTTPS requests are restricted to the configured official hosts; no game or loader artifact is downloaded by the periodic check.

Minecraft and loader update offers are stored internally and never shown as dialogs, banners, toasts, or badges. Vanilla, Fabric, Forge, NeoForge, Quilt, and OptiFine checks do not download artifacts or change profiles. The checker does not replace existing profiles or remove saves, mods, settings, or other user data. Custom JAR profiles and legacy LiteLoader profiles do not have a managed official update source in the launcher and are not auto-updated.

## EvolveD Launcher self-updates

Launcher updates use GitHub Releases for `evolvegmr/EvolveD-launcher`. EvolveD checks the public GitHub Releases API immediately on launcher resume and every 25 seconds while the launcher is in the foreground. An unchanged release is offered only once. Settings also provides a manual launcher update check. It only offers an update when the latest compatible stable release version is newer than the installed version; a failed manual request is reported as a failure, not as an up-to-date result. Downloaded packages are kept in temporary cache storage and checked for integrity, package identity, version, signing identity, and supported native ABI before Android's package installer is opened.

To publish an update:

1. Build a signed release APK.
2. Set the launcher `versionName`/git tag to the new version, for example `v3.2.0`, and increment `versionCode` so Android accepts the update.
3. Create a GitHub Release with that tag.
4. Upload the release APK as an `.apk` asset.
5. Add release notes/changelog.

Users with an older version will see the EvolveD update dialog when they open the launcher. `Later` dismisses the dialog without closing the launcher. `Update Now` downloads the APK, verifies it before opening Android's package installer, and leaves the installed launcher untouched if a download or verification fails. Android remains responsible for the final installation decision.

Debug builds intentionally open the GitHub release page instead of attempting to install a release APK, because debug builds use a different application ID/signing identity.

## Important signing requirement

Every release APK intended as an in-place update must be signed with the same Android signing key as the installed production EvolveD Launcher. Changing the signing key requires a different installation/migration strategy and cannot be silently installed over the existing app.

The signing key is not stored in this repository. Configure `signingKeystore`,
`signingStorePassword`, `signingKeyAlias`, and `signingKeyPassword` as private
Gradle properties or CI secrets for signed release builds. Without all four
properties, the release APK is unsigned and cannot update an installed build.
