# Build Status

## Build environment
- OS: Ubuntu 24.04.5 LTS
- JDK in use for verified build: 17
- Gradle version: 8.13
- Android Gradle Plugin: 8.7.2
- compileSdk / targetSdk: 34
- minSdk: 21
- buildTools: 34.0.0
- NDK: 27.3.13750724

## Verified build command
The project was verified using:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app_pojavlauncher:assembleDebug --console=plain
```

## Result
Build result: successful.

Evidence from the final run:

```text
BUILD SUCCESSFUL in 2m 38s
```

## APK output
APK generated at:
- `app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk`

## Warnings
The build still emits non-fatal C/C++ warnings from native sources, but no blocking Java or Gradle compile errors remained after the Firebase fix and JDK-17 configuration.

## Remaining blocker
The default shell environment is using Java 25, which fails with `Unsupported class file major version 69`. The project requires a supported Java version to compile, and JDK 17 is the verified working toolchain.

## Documentation status
Build, architecture, branding, and attribution notes have been added under the `docs/` folder.
