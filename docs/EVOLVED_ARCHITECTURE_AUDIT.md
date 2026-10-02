# EvolveD Architecture Audit

## Overview
This repository is a fork of the existing Android Minecraft launcher codebase built around PojavLauncher-style runtime management, native bridges, and Android UI. The launcher architecture remains centered on a real Minecraft Java Edition launch pipeline rather than a fake demo screen.

## Existing architecture
- App namespace: `net.kdt.pojavlaunch`
- Android application entrypoints: `PojavApplication`, `LauncherActivity`, `MainActivity`, `RuntimeSetupActivity`
- Native launcher components: JNI under `app_pojavlauncher/src/main/jni/`
- Runtime management: multi-runtime Java installation and launch configuration
- Loader support: Fabric, Forge, NeoForge, Quilt, OptiFine, custom profiles, modpacks, and installer flows
- Storage and game directory management: Android storage access + external game directory layout
- Firebase: optional remote sync/notifications path guarded by config

## Minecraft launch pipeline
1. User selects account and profile.
2. Launcher resolves the Minecraft version and loader info.
3. Java runtime is selected from the installed runtime pool.
4. Native libraries and GL/EGL bridge configuration are prepared.
5. Minecraft launch arguments and libraries are assembled.
6. The game process is started through the Android/Java bridge and launcher runtime wrappers.

This pipeline is part of the existing launcher and was intentionally preserved.

## Version management
The project reads and manages Minecraft versions across:
- release
- snapshot
- old alpha/beta
- installed versions
- custom profile versions

The code is structured for version metadata, library resolution, and downloader flows, and future metadata discovery should be treated as an extension of this model rather than a replacement.

## Mod loaders and compatibility
The project retains support for:
- Fabric
- Forge
- NeoForge
- Quilt
- OptiFine and custom install flows
- combined/custom loader handling

The loader system should remain intact and must not be replaced by a simplified custom implementation.

## Mods, modpacks, resource packs, shaders
The launcher continues to support:
- mod discovery and folder management
- mod enable/disable behavior if already implemented
- modpack installation/import/export
- resource packs and shader packs
- custom game directories and profile-specific packaging

These systems remain core Minecraft functionality and were not removed as part of the EvolveD rebrand.

## Rendering, fog, and graphics
Existing renderer-related logic remains in place for:
- GL4ES / Vulkan / Zink / MobileGlues / fallback renderer configuration
- custom renderer settings
- performance tuning
- shaderpack compatibility
- fog and rendering behavior preserved at the engine-level integration

The code should not be rewritten or replaced by a fake UI-only layer.

## Controls and input
The project preserves the existing touch, mouse, keyboard, and controller/gamepad configuration system.

## Runtime management
The project includes:
- Java runtime install flow
- runtime compatibility checks
- version-specific runtime selection
- JVM argument handling
- native processing and library handling

## Authentication and accounts
The launcher preserves Microsoft authentication, offline accounts, Ely.by-style flows, and account switching patterns already present in the original codebase. No replacement auth layer was introduced.

## Storage and networking
- Android storage providers and scoped access remain in use
- downloads and libraries remain handled by the launcher infrastructure
- remote update services are optional and guarded by config

## Logging and crash handling
The project includes crash logging, log sharing, and bootstrap logging flows. These remained in place.

## Firebase and optional features
Firebase was treated as an optional capability rather than a mandatory build dependency.
- Without `google-services.json`, the build continues
- with valid config, optional Firebase features can be enabled
- no original CS backend URL is used as the default endpoint

## Risks and constraints
- Future Minecraft version changes may require native / runtime / loader compatibility work.
- Upgrading Android SDK or Java toolchain aggressively can destabilize the project.
- Optional Firebase should be enabled only when a legitimate project is configured.

## Recommendation
Keep the current launcher architecture intact, keep the EvolveD rebrand limited to user-facing UI and messaging, and preserve all runtime/gameplay systems that are already working.
