# EvolveD Launcher Updates

## Minecraft versions

EvolveD reads Mojang's official `version_manifest_v2.json` at runtime. The launcher refreshes this manifest when the launcher opens and falls back to its cached copy if the network is unavailable. This means a newly released Minecraft version can appear without shipping a new EvolveD Launcher APK.

The version still has to be compatible with the launcher/runtime/loader implementation. A launcher APK is not required merely for a normal Minecraft release to appear in the version list.

## EvolveD Launcher self-updates

Launcher updates use GitHub Releases for `evolvegmr/EvolveD-launcher`. On launcher open, EvolveD checks the public GitHub Releases API. It only offers an update when the latest stable release version is newer than the installed version.

To publish an update:

1. Build a signed release APK.
2. Set the launcher `versionName`/git tag to the new version, for example `v3.2.0`.
3. Create a GitHub Release with that tag.
4. Upload the release APK as an `.apk` asset.
5. Add release notes/changelog.

Users with an older version will see the EvolveD update dialog when they open the launcher. `Later` dismisses that specific version; the dialog will return for a newer release. `Update Now` downloads the APK and invokes Android's package installer.

Debug builds intentionally open the GitHub release page instead of attempting to install a release APK, because debug builds use a different application ID/signing identity.

## Important signing requirement

Every release APK intended as an in-place update must be signed with the same Android signing key as the installed production EvolveD Launcher. Changing the signing key requires a different installation/migration strategy and cannot be silently installed over the existing app.
