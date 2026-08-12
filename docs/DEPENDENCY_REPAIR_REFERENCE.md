# SQLite Android Dependency Repair

The Android CI build could not resolve the repository-specific JitPack coordinate `com.github.nastech:sqlite-android:80cedc8888df2fe1d22d7f9bf8d9287f621624be`; JitPack returned HTTP 401 during dependency resolution.

Nastech now uses the public `com.github.requery:sqlite-android:3.45.0` artifact. The verified POM is available from Liferay Public at `https://repository.liferay.com/nexus/content/repositories/public/`, so `settings.gradle.kts` includes that repository with content restricted to `com.github.requery`.

## Sources

- [requery/sqlite-android project README](https://github.com/requery/sqlite-android)
- [sqlite-android 3.45.0 package metadata](https://mvnrepository.com/artifact/com.github.requery/sqlite-android/3.45.0)
