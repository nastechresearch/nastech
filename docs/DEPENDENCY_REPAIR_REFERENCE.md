# SQLite Android Dependency Repair

The Android CI build could not resolve the repository-specific JitPack coordinate `com.github.nastech:sqlite-android:80cedc8888df2fe1d22d7f9bf8d9287f621624be`; JitPack returned HTTP 401 during dependency resolution.

Nastech now uses the public `com.github.requery:sqlite-android:3.45.0` artifact. The verified POM is available from Liferay Public at `https://repository.liferay.com/nexus/content/repositories/public/`, so `settings.gradle.kts` includes that repository with content restricted to `com.github.requery`.

## Sources

- [requery/sqlite-android project README](https://github.com/requery/sqlite-android)
- [sqlite-android 3.45.0 package metadata](https://mvnrepository.com/artifact/com.github.requery/sqlite-android/3.45.0)

## Rebrand-safe external coordinates

The product package, Android application ID, and user-facing materials are branded Nastech. Third-party Maven coordinates are not product branding and must retain the publisher namespace that resolves the artifact. The JLaTeXMath Android, Markdown, and HugeIcons dependencies therefore use their verified public upstream `com.github.rikkahub` coordinates; changing their group identifiers to Nastech made CI request artifacts that do not exist or are not publicly accessible.

For JLaTeXMath Android, the maintained public `ru.noties` artifacts are also available from Maven Central, but Nastech retains the existing compatible upstream fork coordinates to avoid an unreviewed API or behavior change. JetBrains’ public `org.jetbrains:markdown` artifact exists on Maven Central; the project continues to use the pinned upstream fork commit because it is the dependency version already used by this source tree.

- [JLaTeXMath Android installation guidance](https://github.com/noties/jlatexmath-android)
- [JetBrains Markdown installation guidance](https://github.com/JetBrains/markdown)
- [Public upstream JLaTeXMath fork](https://github.com/rikkahub/jlatexmath-android)
- [Public upstream HugeIcons fork](https://github.com/rikkahub/hugeicons-compose)
