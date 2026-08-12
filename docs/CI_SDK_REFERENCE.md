# Android CI SDK Reference

The Android build workflow targets `compileSdk = 37` and `targetSdk = 37`.

Google’s Android 17 SDK setup guidance states that Android 17 uses API level 37 and requires Android SDK Platform Cinnamon Bun plus Build Tools 37.x.x. The command-line package metadata currently lists the platform package as `platforms;android-37.0`, with `build-tools;37.0.0` also available. The workflow therefore installs `platforms;android-37.0`, `build-tools;37.0.0`, CMake 3.22.1, and NDK 29.0.14206865 via `sdkmanager --channel=3`.

## Sources

- [Set up the Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk)
- [sdkmanager command-line documentation](https://developer.android.com/tools/sdkmanager)
- [Google Android SDK repository metadata](https://dl.google.com/android/repository/repository2-3.xml)
